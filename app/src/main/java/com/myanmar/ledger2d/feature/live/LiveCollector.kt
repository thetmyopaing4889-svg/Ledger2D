package com.myanmar.ledger2d.feature.live

import android.content.Context
import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.repository.HistorySync
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private fun monotonicMs(): Long = runCatching {
    SystemClock.elapsedRealtime()
}.getOrElse {
    System.nanoTime() / 1_000_000L
}

/**
 * Luke-only Phase 1:
 * - one scheduled request every 3 seconds while a live/final result is expected
 * - request completion never controls the next request
 * - failed requests never erase the last successful snapshot
 * - late/out-of-order responses cannot overwrite a newer successful snapshot
 */
internal const val NORMAL_POLL_INTERVAL_MS = 3_000L
internal const val CLOSING_POLL_INTERVAL_MS = NORMAL_POLL_INTERVAL_MS
internal const val CYCLE_DEADLINE_MS = 0L
internal const val FINAL_GRACE_MS = 30_000L
internal const val SOURCE_FRESHNESS_MS = 5_000L
internal const val SOURCE_TIME_SKEW_MS = 2_000L
internal const val LIVE_REFERENCE_FETCH_INTERVAL_MS = 60_000L

internal const val LIVE_PENDING = "--"
internal const val LIVE_SESSION_MORNING_LABEL = "12:01 PM"
internal const val LIVE_SESSION_EVENING_LABEL = "4:30 PM"

data class LiveHeroSnapshot(
    val result: String,
    val set: String,
    val value: String,
    val sessionLabel: String,
    val date: String,
)

enum class LiveStatus {
    WAITING,
    LIVE_CONFIRMED,
    LIVE_DEGRADED,
    WAITING_FOR_ALIGNMENT,
    LIVE_CONFLICT,
    STALE,
    FINALIZING,
    WAITING_FOR_PRIMARY,
    DRAW_FREEZE,
    RESULT_AVAILABLE,
    FINAL_CONFIRMED,
    DEGRADED_FINAL,
    FINAL_CONFLICT,
    STALE_PRIMARY,
}

internal fun buildLiveDailyResultPatches(
    feed: LiveFeedData,
    today: LocalDate,
    now: LocalTime,
): List<LiveDailyResultPatch> {
    val cycleDate = dailyCycleDate(today, now)
    val providerDate = canonicalDate(feed.date)
    val sourceAt = feed.serverTimeEpochMs
        ?: parseDecisionInstant(feed)?.toEpochMilli()
        ?: Instant.now().toEpochMilli()

    val byDate = linkedMapOf<LocalDate, LiveDailyResultPatch>()

    fun mergePatch(
        date: LocalDate,
        update: LiveDailyResultPatch.() -> LiveDailyResultPatch,
    ) {
        val old = byDate[date]
        val base = old ?: LiveDailyResultPatch(date = date)
        byDate[date] = base.update()
    }

    if (
        providerDate != null &&
        isWorkingDay(providerDate) &&
        (providerDate == cycleDate || providerDate == previousWorkingDay(cycleDate))
    ) {
        val morning2d = feed.morning.result.takeIf(::isValidLive2d)
        val morningSet = feed.morning.set.takeIf(::validMoney)
        val morningValue = feed.morning.value.takeIf(::validMoney)
        val evening2d = feed.evening.result.takeIf(::isValidLive2d)
        val eveningSet = feed.evening.set.takeIf(::validMoney)
        val eveningValue = feed.evening.value.takeIf(::validMoney)

        if (morning2d != null || morningSet != null || morningValue != null) {
            mergePatch(providerDate) {
                copy(
                    morning2d = morning2d,
                    morningSet = morningSet,
                    morningValue = morningValue,
                    morningSourceAt = sourceAt,
                )
            }
        }
        if (evening2d != null || eveningSet != null || eveningValue != null) {
            mergePatch(providerDate) {
                copy(
                    evening2d = evening2d,
                    eveningSet = eveningSet,
                    eveningValue = eveningValue,
                    eveningSourceAt = sourceAt,
                )
            }
        }
    }

    // References belong to the app's current working-day cycle, not Luke's
    // completed-result date. Never persist them before their display boundary.
    if (
        isWorkingDay(today) &&
        cycleDate == today &&
        isCurrentCycleReferenceObservation(feed, cycleDate)
    ) {
        val modern930 = if (!now.isBefore(MORNING_REFERENCE)) {
            feed.modern930.takeIf(::isValidLive2d)
        } else null
        val internet930 = if (!now.isBefore(MORNING_REFERENCE)) {
            feed.internet930.takeIf(::isValidLive2d)
        } else null

        val modern200 = if (!now.isBefore(AFTERNOON_REFERENCE)) {
            feed.modern200.takeIf(::isValidLive2d)
        } else null
        val internet200 = if (!now.isBefore(AFTERNOON_REFERENCE)) {
            feed.internet200.takeIf(::isValidLive2d)
        } else null

        if (modern930 != null || internet930 != null || modern200 != null || internet200 != null) {
            mergePatch(today) {
                copy(
                    modern930 = modern930,
                    internet930 = internet930,
                    modern200 = modern200,
                    internet200 = internet200,
                    reference930SourceAt = if (modern930 != null || internet930 != null) sourceAt else null,
                    reference200SourceAt = if (modern200 != null || internet200 != null) sourceAt else null,
                )
            }
        }
    }

    return byDate.values.filter {
        it.modern930 != null ||
            it.internet930 != null ||
            it.modern200 != null ||
            it.internet200 != null ||
            it.morning2d != null ||
            it.morningSet != null ||
            it.morningValue != null ||
            it.evening2d != null ||
            it.eveningSet != null ||
            it.eveningValue != null
    }
}

internal data class SourceObservation(
    val feed: LiveFeedData,
    val fetchedAtElapsedMs: Long,
    val requestStartedElapsedMs: Long,
    val roundTripMs: Long,
)

internal data class LiveResolution(
    val displayFeed: LiveFeedData?,
    val hero: LiveHeroSnapshot?,
    val heroLive: Boolean,
    val status: LiveStatus,
    val message: String,
    val sourceCount: Int,
    val staleAgeMs: Long,
)

private fun historySession(
    result: String,
    set: String,
    value: String,
): LiveSessionData {
    val normalizedResult = result.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    val normalizedSet = set.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    val normalizedValue = value.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    return LiveSessionData(
        result = normalizedResult,
        set = normalizedSet,
        value = normalizedValue,
        finalized = isValidLive2d(normalizedResult),
    )
}

/**
 * Reconstruct the complete held working-day display from the same historical
 * 2D source already used by the Room Keeper. This is display-only cold-start
 * state before the next working-day 09:30 boundary; it does not fabricate a
 * LIVE value.
 */
internal fun historyRowToFeed(row: HistoryResultEntity): LiveFeedData {
    val morning = historySession(row.morning2d, row.morningSet, row.morningValue)
    val evening = historySession(row.evening2d, row.eveningSet, row.eveningValue)
    val lastFinalTime = when {
        evening.finalized -> LocalTime.of(16, 30)
        morning.finalized -> LocalTime.of(12, 1)
        else -> LocalTime.MIDNIGHT
    }

    return LiveFeedData(
        date = row.date.toString(),
        currentTime = lastFinalTime.toString(),
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = morning,
        evening = evening,
        modern930 = row.modern930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        internet930 = row.internet930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        modern200 = row.modern200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        internet200 = row.internet200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        sourceTag = "HISTORY",
        serverTimeEpochMs = row.date.atTime(lastFinalTime).atZone(YANGON).toInstant().toEpochMilli(),
    )
}

internal fun historyRowToFeed(
    row: HistoryResultEntity,
    today: LocalDate = currentYangonDate(),
    now: LocalTime = LocalTime.now(YANGON),
): LiveFeedData {
    val cycleDate = dailyCycleDate(today, now)
    val isCurrentCycleRow = row.date == cycleDate
    val isHeldFallbackRow =
        row.date != today &&
            (now.isBefore(MORNING_LIVE) || !isWorkingDay(today))

    fun session(
        result: String,
        set: String,
        value: String,
        allowed: Boolean,
    ): LiveSessionData {
        if (!allowed) return LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false)
        val normalizedResult = result.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
        val normalizedSet = set.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
        val normalizedValue = value.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
        return LiveSessionData(
            result = normalizedResult,
            set = normalizedSet,
            value = normalizedValue,
            finalized = isValidLive2d(normalizedResult),
        )
    }

    // When the historical row is the held cycleDate itself (Monday-Friday
    // pre-09:30, or the weekend-held Friday row), the whole completed row is
    // valid because that cycle has already finished. For today's cycle,
    // future session/reference values must stay masked until their boundaries.
    val heldCompletedCycle = cycleDate != today

    val showMorningFinal = heldCompletedCycle || !now.isBefore(MORNING_CLOSE)
    val showEveningFinal = heldCompletedCycle || !now.isBefore(EVENING_CLOSE)
    val morning = session(
        row.morning2d,
        row.morningSet,
        row.morningValue,
        (isCurrentCycleRow && showMorningFinal) || isHeldFallbackRow,
    )
    val evening = session(
        row.evening2d,
        row.eveningSet,
        row.eveningValue,
        (isCurrentCycleRow && showEveningFinal) || isHeldFallbackRow,
    )

    val show930Reference =
        heldCompletedCycle || !now.isBefore(MORNING_REFERENCE)
    val show200Reference =
        heldCompletedCycle || !now.isBefore(AFTERNOON_REFERENCE)

    val modern930 = if ((isCurrentCycleRow || isHeldFallbackRow) && show930Reference) {
        row.modern930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }
    val internet930 = if ((isCurrentCycleRow || isHeldFallbackRow) && show930Reference) {
        row.internet930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }
    val modern200 = if ((isCurrentCycleRow || isHeldFallbackRow) && show200Reference) {
        row.modern200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }
    val internet200 = if ((isCurrentCycleRow || isHeldFallbackRow) && show200Reference) {
        row.internet200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }

    val projectedTime = if (!isCurrentCycleRow) {
        EVENING_CLOSE
    } else {
        when {
            now.isBefore(MORNING_REFERENCE) -> MORNING_REFERENCE
            now.isBefore(MORNING_LIVE) -> MORNING_REFERENCE
            now.isBefore(MORNING_CLOSE) -> MORNING_LIVE
            now.isBefore(AFTERNOON_REFERENCE) -> MORNING_CLOSE
            now.isBefore(EVENING_LIVE) -> AFTERNOON_REFERENCE
            now.isBefore(EVENING_CLOSE) -> EVENING_LIVE
            else -> EVENING_CLOSE
        }
    }

    return LiveFeedData(
        date = row.date.toString(),
        currentTime = projectedTime.toString(),
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = morning,
        evening = evening,
        modern930 = modern930,
        internet930 = internet930,
        modern200 = modern200,
        internet200 = internet200,
        sourceTag = "HISTORY",
        serverTimeEpochMs = row.date.atTime(projectedTime).atZone(YANGON).toInstant().toEpochMilli(),
    )
}

internal fun historyRowToFinal(row: HistoryResultEntity): LiveHeroSnapshot? =
    historyRowToFinal(row, LocalTime.of(23, 59, 59))

internal fun historyRowToFinal(
    row: HistoryResultEntity,
    now: LocalTime,
): LiveHeroSnapshot? {
    val result: String
    val set: String
    val value: String
    val label: String

    when {
        !now.isBefore(EVENING_CLOSE) && isValidLive2d(row.evening2d) -> {
            result = row.evening2d
            set = row.eveningSet
            value = row.eveningValue
            label = LIVE_SESSION_EVENING_LABEL
        }

        !now.isBefore(MORNING_CLOSE) && isValidLive2d(row.morning2d) -> {
            result = row.morning2d
            set = row.morningSet
            value = row.morningValue
            label = LIVE_SESSION_MORNING_LABEL
        }

        else -> return null
    }

    return LiveHeroSnapshot(
        result = result,
        set = set.takeUnless { it == "-" }.orEmpty().ifBlank { LIVE_PENDING },
        value = value.takeUnless { it == "-" }.orEmpty().ifBlank { LIVE_PENDING },
        sessionLabel = label,
        date = row.date.toString(),
    )
}

internal fun canonicalDate(raw: String): LocalDate? = runCatching {
    LocalDate.parse(raw.trim())
}.getOrElse {
    runCatching {
        LocalDate.parse(
            raw.trim(),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        )
    }.getOrNull()
}

internal fun parseDecisionInstant(f: LiveFeedData): Instant? {
    f.serverTimeEpochMs
        ?.takeIf { it >= 946684800000L }
        ?.let { return Instant.ofEpochMilli(it) }
    val raw = f.currentTime.trim()
    if (raw.isBlank() || raw == LIVE_PENDING) return null

    return try {
        when {
            raw.contains("T") -> Instant.parse(raw)
            raw.contains(" ") -> java.time.LocalDateTime
                .parse(raw.replace(' ', 'T'))
                .atZone(YANGON)
                .toInstant()
            else -> {
                val d = canonicalDate(f.date) ?: return null
                java.time.LocalDateTime
                    .of(d, LocalTime.parse(raw.take(8)))
                    .atZone(YANGON)
                    .toInstant()
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun isValidLive2d(v: String): Boolean =
    v.matches(Regex("^[0-9]{2}$"))

private fun validMoney(v: String): Boolean =
    v.isNotBlank() && v != LIVE_PENDING

private fun currentDay(
    f: LiveFeedData,
    date: LocalDate = currentYangonDate(),
): Boolean =
    canonicalDate(f.date) == date

internal fun age(o: SourceObservation?): Long =
    if (o == null) Long.MAX_VALUE
    else maxOf(0L, monotonicMs() - o.fetchedAtElapsedMs)

internal fun hasValidLive(
    feed: LiveFeedData?,
    date: LocalDate = currentYangonDate(),
): Boolean {
    val f = feed ?: return false
    return currentDay(f, date) &&
        isValidLive2d(f.live) &&
        validMoney(f.liveSet) &&
        validMoney(f.liveVal) &&
        parseDecisionInstant(f) != null
}

internal fun liveValid(
    o: SourceObservation?,
    date: LocalDate = currentYangonDate(),
): Boolean = hasValidLive(o?.feed, date)

internal fun finalValid(
    session: LiveSessionData?,
    feed: LiveFeedData?,
    date: LocalDate = currentYangonDate(),
): Boolean {
    if (session == null || feed == null || !currentDay(feed, date)) return false
    // Luke's result_1200 / result_430 is the final-result trigger.
    // SET/VALUE are supplementary metadata and may arrive slightly later.
    return isValidLive2d(session.result)
}

internal fun latestFinalFor(feed: LiveFeedData): LiveHeroSnapshot? = when {
    feed.evening.finalized -> LiveHeroSnapshot(
        feed.evening.result,
        feed.evening.set,
        feed.evening.value,
        LIVE_SESSION_EVENING_LABEL,
        feed.date,
    )
    feed.morning.finalized -> LiveHeroSnapshot(
        feed.morning.result,
        feed.morning.set,
        feed.morning.value,
        LIVE_SESSION_MORNING_LABEL,
        feed.date,
    )
    else -> null
}

internal fun isCurrentCycleReferenceObservation(
    feed: LiveFeedData,
    cycleDate: LocalDate,
): Boolean {
    val epoch = feed.serverTimeEpochMs
    // Unit fixtures and some legacy provider responses may omit a real epoch
    // and use a tiny sentinel. Treat those as "no absolute server date" so
    // #403's split history-date/current-reference behavior stays valid.
    if (epoch == null || epoch < 946684800000L) return true

    val serverDate = Instant.ofEpochMilli(epoch)
        .atZone(YANGON)
        .toLocalDate()

    // Some Luke responses carry the current reference values while the
    // provider's result-date field still names the previous completed day.
    // When an absolute server timestamp is available, require that timestamp
    // to belong to the current working-day cycle so stale snapshots cannot
    // complete a reference retry.
    return serverDate == cycleDate
}

internal class LiveCollector(
    private val scope: CoroutineScope,
    private val fetcher: suspend () -> LiveFeedData?,
    private val secondaryFetcher: (suspend () -> LiveFeedData?)? = null,
    private val clock: () -> LocalTime = { LocalTime.now(YANGON) },
    private val dateProvider: () -> LocalDate = { currentYangonDate() },
    cacheLoader: () -> LiveHeroSnapshot? = { null },
    private val cacheSaver: (LiveHeroSnapshot) -> Unit = {},
    private val cacheFeedLoader: () -> LiveFeedData? = { null },
    private val cacheFeedSaver: (LiveFeedData) -> Unit = {},
    private val liveRoomSaver: (suspend (List<LiveDailyResultPatch>) -> Unit)? = null,
    private val closedDayFeedFetcher: (suspend (LocalDate) -> LiveFeedData?)? = null,
    private val closedDayDateLoader: () -> LocalDate? = { null },
    private val closedDayDateSaver: (LocalDate) -> Unit = {},
    private val closedDayDateClearer: () -> Unit = {},
    private val historicalFinalFetcher: (suspend (LocalDate) -> LiveHeroSnapshot?)? = null,
    private val historicalFeedFetcher: (suspend (LocalDate) -> LiveFeedData?)? = null,
) {
    private val _state = MutableStateFlow<LiveUiState>(
        LiveUiState.Data(null, null, false, false)
    )
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val latestAppliedSequence = AtomicLong(0L)
    private val stateLock = Any()

    private var schedulerJob: Job? = null
    private var reference930CompleteDate: LocalDate? = null
    private var reference200CompleteDate: LocalDate? = null
    private var referenceResetDate: LocalDate? = null
    private var reference930: Pair<String, String>? = null
    private var reference930Date: LocalDate? = null
    private var reference200: Pair<String, String>? = null
    private var reference200Date: LocalDate? = null
    private var reference930PendingDate: LocalDate? = null
    private var reference200PendingDate: LocalDate? = null

    // A successful reference request is also a valid current-cycle display
    // baseline even when the normal LIVE fetch is empty. Keep that feed
    // separate from the raw primary so 09:30/14:00 can render deterministically.
    private var referenceFeed: LiveFeedData? = null
    private var referenceFeedDate: LocalDate? = null

    private var primary: SourceObservation? = null
    private var primaryLiveSession: LiveSession? = null
    private var lastLive: LiveHeroSnapshot? = null
    private var lastPhaseMarker: Pair<LiveWindowAction, LiveSession?>? = null
    /**
     * Last Luke-confirmed closed calendar date. Live-only state; never written
     * to the user's Room ClosedDayRepository.
     */
    private var closedDayDate: LocalDate? = null
    private var lastFinal: LiveHeroSnapshot? = null
    private var lastRoomSyncSignature: String? = null

    private val requestCoordinator = LiveRequestCoordinator(
        scope = scope,
        fetcher = fetcher,
        monotonicClockMs = { monotonicMs() },
        clock = clock,
        dateProvider = dateProvider,
        referenceRetryWindowOpen = { cycleDate, currentDate, now ->
            referenceRetryWindowOpen(cycleDate, currentDate, now)
        },
        referenceComplete = { isMorning, cycleDate ->
            if (isMorning) {
                reference930CompleteDate == cycleDate
            } else {
                reference200CompleteDate == cycleDate
            }
        },
        fetchReferencePair = { isMorning, cycleDate ->
            fetchReferencePair(isMorning, cycleDate)
        },
        onLiveResult = ::applyLiveRequestResult,
    )

    /**
     * Read the stored LIVE-only Closed Day date and resolve its display hold.
     * The date/time rule itself is pure and owned by LiveSchedulePolicy.
     */
    private fun closedHoldDate(today: LocalDate, now: LocalTime): LocalDate? =
        liveClosedDayHoldDate(
            closedDate = closedDayDate,
            today = today,
            now = now,
        )

    private fun clearExpiredClosedDayBeforeMorningBoundary(
        today: LocalDate,
        now: LocalTime,
    ) {
        val closed = closedDayDate ?: return
        if (
            !now.isBefore(MORNING_REFERENCE) &&
            today == nextWorkingDayAfter(closed)
        ) {
            synchronized(stateLock) {
                if (closedDayDate == closed) {
                    closedDayDate = null
                    closedDayDateClearer()
                }
            }
        }
    }

    private fun currentClosedDayForNotice(today: LocalDate): Boolean =
        closedDayDate == today

    private fun isLiveDisplayableFeed(
        feed: LiveFeedData,
        scheduleTime: LocalTime,
        today: LocalDate,
    ): Boolean = isLiveFeedDisplayableForSchedule(
        feed = feed,
        scheduleTime = scheduleTime,
        today = today,
        liveClosedDayDate = closedHoldDate(today, scheduleTime),
    )

    init {
        val scheduleTime = clock()
        val today = dateProvider()

        val persistedClosed = closedDayDateLoader()
        if (persistedClosed != null) {
            val nextWorking = nextWorkingDayAfter(persistedClosed)
            val stillRelevant =
                persistedClosed == today ||
                    today.isBefore(nextWorking) ||
                    (today == nextWorking && scheduleTime.isBefore(MORNING_REFERENCE))
            if (stillRelevant) {
                closedDayDate = persistedClosed
            } else {
                closedDayDateClearer()
            }
        }
        val cachedFeed = cacheFeedLoader()
            ?.takeIf { isLiveDisplayableFeed(it, scheduleTime, today) }

        if (cachedFeed != null) {
            val startedAt = monotonicMs()
            primary = SourceObservation(cachedFeed, startedAt, startedAt, 0L)
            if (hasValidLive(cachedFeed, today)) {
                primaryLiveSession = LiveStateReducer.inferLiveSessionFromSourceTime(cachedFeed)
            }
            lastFinal = latestFinalFor(cachedFeed)
            publishLocked()
            syncLiveRoom(cachedFeed)
        } else {
            val bootstrap = freshInstallBootstrapPlan(today, scheduleTime)
            val activeSession = liveSessionForTime(scheduleTime)
            val cached = cacheLoader()?.takeIf { snapshot ->
                val cachedDate = canonicalDate(snapshot.date)
                if (activeSession == null) {
                    cachedDate in bootstrap.allowedCacheDates
                } else {
                    // During an active session, only a current-day cached final
                    // for that same session may seed the initial hero. A prior
                    // day's final, or the morning final during the evening
                    // session, must never flash as today's active result.
                    cachedDate == today &&
                        when (activeSession) {
                            LiveSession.MORNING ->
                                snapshot.sessionLabel == LIVE_SESSION_MORNING_LABEL
                            LiveSession.EVENING ->
                                snapshot.sessionLabel == LIVE_SESSION_EVENING_LABEL
                        }
                }
            }

            if (cached != null) {
                lastFinal = cached
                _state.value = LiveUiState.Data(null, cached, false, false)
            }
        }
    }

    fun fetchCycle() {
        launchRequest()
    }

    private fun launchRequest() {
        requestCoordinator.launchLiveRequest()
    }

    private fun applyLiveRequestResult(event: LiveRequestResultEvent) {
        val sequence = event.sequence
        val feed = event.feed
        val startedAt = event.requestStartedAtElapsedMs
        val finishedAt = event.requestFinishedAtElapsedMs

        synchronized(stateLock) {
            if (!isUsableLukeSnapshot(feed)) {
                return@synchronized
            }

            val previous = primary?.feed
            val incomingTime = parseDecisionInstant(feed)
            val previousTime = previous?.let(::parseDecisionInstant)
            val appliedSequence = latestAppliedSequence.get()

            // Provider timestamps remain authoritative; sequence is a tie-breaker only.
            // Requests intentionally overlap, so do not replace this with a single in-flight guard.
            if (
                !shouldAcceptLiveSnapshotByOrdering(
                    incomingTime = incomingTime,
                    previousTime = previousTime,
                    sequence = sequence,
                    latestAppliedSequence = appliedSequence,
                )
            ) {
                return@synchronized
            }

            // Preserve the last completed working-day final before a
            // newer provider snapshot replaces the primary feed. A
            // Monday reference response can be today's feed while LIVE
            // is still pending; it must not erase Friday's held hero.
            previous?.let(::latestFinalFor)?.let { previousFinal ->
                val cycleDate = dailyCycleDate(dateProvider(), clock())
                val previousWorking = previousWorkingDay(cycleDate)
                if (canonicalDate(previousFinal.date) == previousWorking) {
                    lastFinal = previousFinal
                }
            }

            // Only an accepted, in-order snapshot may update the held-final
            // memory. A stale response must never mutate display fallback state.
            captureHeldFinalLocked(feed)

            val scheduleTime = clock()
            val today = dateProvider()

            // A normal Luke snapshot can contain an already-valid
            // current-cycle reference even when the dedicated reference
            // request is still pending or previously failed. Promote those
            // reference fields from the accepted snapshot immediately.
            promoteCurrentCycleReferencesLocked(feed, today, scheduleTime)

            val reduction = LiveStateReducer.reduceAcceptedLiveSnapshot(
                previous = previous,
                incoming = feed,
                scheduleDate = today,
                scheduleTime = scheduleTime,
                previousPrimaryLiveSession = primaryLiveSession,
            )
            val protected = reduction.feed
            latestAppliedSequence.set(sequence)
            primary = SourceObservation(
                protected,
                finishedAt,
                startedAt,
                finishedAt - startedAt,
            )

            primaryLiveSession = reduction.primaryLiveSession

            // Keep the latest completed result from the previous day in
            // memory as a hero fallback when today's Luke feed arrives
            // without today's LIVE value yet. This is display state only;
            // it never participates in betting or ledger calculations.
            val incomingFinal = latestFinalFor(protected)
            if (
                incomingFinal != null &&
                canonicalDate(incomingFinal.date) != today
            ) {
                lastFinal = incomingFinal
            }

            cacheFeedSaver(protected)
            publishLocked()
            syncLiveRoom(protected)
        }
    }
    private fun captureHeldFinalLocked(feed: LiveFeedData) {
        val final = latestFinalFor(feed) ?: return
        val finalDate = canonicalDate(final.date) ?: return

        val today = dateProvider()
        val now = clock()
        val cycleDate = dailyCycleDate(today, now)
        val previousWorking = previousWorkingDay(cycleDate)

        // A previous-working-day completed result is valid as the temporary
        // hero while the new cycle is still at reference/pending/live stages.
        // It must not become the primary feed or affect financial data.
        if (finalDate == cycleDate || finalDate == previousWorking) {
            val existingDate = canonicalDate(lastFinal?.date.orEmpty())
            if (
                existingDate == null ||
                existingDate == previousWorking ||
                finalDate == cycleDate
            ) {
                lastFinal = final
            }
        }
    }

    private fun syncLiveRoom(feed: LiveFeedData) {
        val saver = liveRoomSaver ?: return
        val patches = buildLiveDailyResultPatches(feed, dateProvider(), clock())
        if (patches.isEmpty()) return

        // Skip repeated 3-second observations when persisted facts did not
        // change. The Room repository remains idempotent and independently
        // protects each field group by source time.
        val signature = patches.joinToString("||") { p ->
            listOf(
                p.date,
                p.modern930,
                p.internet930,
                p.modern200,
                p.internet200,
                p.morning2d,
                p.morningSet,
                p.morningValue,
                p.evening2d,
                p.eveningSet,
                p.eveningValue,
            ).joinToString("|")
        }

        synchronized(stateLock) {
            if (signature == lastRoomSyncSignature) return
            lastRoomSyncSignature = signature
        }

        scope.launch {
            try {
                saver(patches)
            } catch (_: Exception) {
                synchronized(stateLock) {
                    if (lastRoomSyncSignature == signature) {
                        lastRoomSyncSignature = null
                    }
                }
            }
        }
    }

    private fun isUsableLukeSnapshot(feed: LiveFeedData): Boolean {
        val scheduleTime = clock()
        val today = dateProvider()

        if (!isLiveDisplayableFeed(feed, scheduleTime, today)) return false
        if (feed.currentTime.isBlank() || parseDecisionInstant(feed) == null) return false

        // Display fallback and provider acceptance are deliberately different
        // contracts. From the 09:30 boundary onward, only today's Luke
        // snapshot may mutate primary/current-cycle state. A previous-day
        // snapshot may still be shown as the temporary held display before
        // 11:30, but it must never race with or overwrite today's cycle.
        if (
            isWorkingDay(today) &&
            !scheduleTime.isBefore(MORNING_REFERENCE) &&
            canonicalDate(feed.date) != today
        ) {
            return false
        }

        val hasLive = hasValidLive(feed, today)
        val hasMorningFinal = finalValid(feed.morning, feed, today)
        val hasEveningFinal = finalValid(feed.evening, feed, today)

        return hasLive ||
            hasMorningFinal ||
            hasEveningFinal ||
            feed.modern930 != LIVE_PENDING ||
            feed.internet930 != LIVE_PENDING ||
            feed.modern200 != LIVE_PENDING ||
            feed.internet200 != LIVE_PENDING
    }

    private fun promoteCurrentCycleReferencesLocked(
        feed: LiveFeedData,
        today: LocalDate,
        scheduleTime: LocalTime,
    ) {
        applyReferenceStateLocked(
            LiveReferenceStateReducer.promoteFromLiveSnapshot(
                state = referenceStateLocked(),
                feed = feed,
                today = today,
                scheduleTime = scheduleTime,
            )
        )
    }

    private fun mergeReferenceIntoFeed(base: LiveFeedData?): LiveFeedData? {
        val today = dateProvider()
        val t = clock()
        val cycleDate = dailyCycleDate(today, t)

        return projectReferenceFeed(
            base = base,
            snapshot = LiveReferenceProjectionSnapshot(
                today = today,
                scheduleTime = t,
                cycleDate = cycleDate,
                referenceFeed = referenceFeed,
                referenceFeedDate = referenceFeedDate,
                closedHold = closedHoldDate(today, t) != null,
                reference930 = reference930,
                reference930Date = reference930Date,
                reference930PendingDate = reference930PendingDate,
                reference200 = reference200,
                reference200Date = reference200Date,
                reference200PendingDate = reference200PendingDate,
                referenceResetDate = referenceResetDate,
            ),
        )
    }

    private fun referenceRetryWindowOpen(
        cycleDate: LocalDate,
        currentDate: LocalDate,
        now: LocalTime,
    ): Boolean = isReferenceRetryWindowOpenAt(
        cycleDate = cycleDate,
        currentDate = currentDate,
        now = now,
        hasClosedHold = closedHoldDate(currentDate, now) != null,
    )

    private suspend fun fetchReferencePair(
        isMorning: Boolean,
        cycleDate: LocalDate,
    ): Boolean {
        val feed = try {
            fetcher()
        } catch (_: Exception) {
            null
        }

        val closedSnapshot = isMorning &&
            feed != null &&
            feed.isCloseDay &&
            isCurrentCycleReferenceObservation(feed, cycleDate)

        // Keep the previous-working-day lookup outside the state lock. The
        // completed I/O is carried with the reference result event.
        val heldFeed = if (closedSnapshot) {
            val holdDate = previousWorkingDay(cycleDate)
            runCatching {
                closedDayFeedFetcher?.invoke(holdDate)
            }.getOrNull()
        } else {
            null
        }

        return applyReferenceResult(
            LiveReferenceResultEvent(
                isMorning = isMorning,
                cycleDate = cycleDate,
                feed = feed,
                closedDayObservation = closedSnapshot,
                heldFeed = heldFeed,
            )
        )
    }

    private fun referenceStateLocked(): LiveReferenceState = LiveReferenceState(
        reference930 = reference930,
        reference930Date = reference930Date,
        reference200 = reference200,
        reference200Date = reference200Date,
        reference930PendingDate = reference930PendingDate,
        reference200PendingDate = reference200PendingDate,
        reference930CompleteDate = reference930CompleteDate,
        reference200CompleteDate = reference200CompleteDate,
        referenceResetDate = referenceResetDate,
        referenceFeed = referenceFeed,
        referenceFeedDate = referenceFeedDate,
    )

    /** Commit a pure reference-state reduction. Call only within stateLock. */
    private fun applyReferenceStateLocked(state: LiveReferenceState) {
        reference930 = state.reference930
        reference930Date = state.reference930Date
        reference200 = state.reference200
        reference200Date = state.reference200Date
        reference930PendingDate = state.reference930PendingDate
        reference200PendingDate = state.reference200PendingDate
        reference930CompleteDate = state.reference930CompleteDate
        reference200CompleteDate = state.reference200CompleteDate
        referenceResetDate = state.referenceResetDate
        referenceFeed = state.referenceFeed
        referenceFeedDate = state.referenceFeedDate
    }

    private fun applyReferenceResult(event: LiveReferenceResultEvent): Boolean {
        val cycleDate = event.cycleDate
        val feed = event.feed
        val closedSnapshot = event.closedDayObservation
        val heldFeed = event.heldFeed

        if (closedSnapshot) {
            synchronized(stateLock) {
                if (!referenceRetryWindowOpen(cycleDate, dateProvider(), clock())) return true

                closedDayDate = cycleDate
                closedDayDateSaver(cycleDate)

                applyReferenceStateLocked(
                    LiveReferenceStateReducer.clearForClosedDay(referenceStateLocked())
                )
                lastLive = null

                if (heldFeed != null) {
                    val startedAt = monotonicMs()
                    primary = SourceObservation(heldFeed, startedAt, startedAt, 0L)
                    latestFinalFor(heldFeed)?.let { lastFinal = it }
                    cacheFeedSaver(heldFeed)
                }

                publishLocked()
            }
            return true
        }

        val cycleComplete = synchronized(stateLock) {
            val transition = LiveReferenceStateReducer.reduce(
                state = referenceStateLocked(),
                event = event,
                retryWindowOpen = referenceRetryWindowOpen(
                    cycleDate,
                    dateProvider(),
                    clock(),
                ),
            )
            if (!transition.accepted) return false

            applyReferenceStateLocked(transition.state)

            publishLocked()
            if (feed != null) {
                syncLiveRoom(feed)
            }
            transition.cycleComplete
        }

        return cycleComplete
    }
    private fun maybeReferenceFetch(t: LocalTime) {
        val today = dateProvider()
        clearExpiredClosedDayBeforeMorningBoundary(today, t)

        if (closedHoldDate(today, t) != null) return
        if (!isWorkingDay(today)) return

        val cycleDate = dailyCycleDate(today, t)

        val referenceRetryOpen = referenceRetryWindowOpen(cycleDate, today, t)

        if (referenceRetryOpen && t >= MORNING_REFERENCE && reference930CompleteDate != cycleDate) {
            synchronized(stateLock) {
                applyReferenceStateLocked(
                    LiveReferenceStateReducer.beginWorkingDayCycle(
                        state = referenceStateLocked(),
                        cycleDate = cycleDate,
                    )
                )
                publishLocked()
            }

            if (reference930CompleteDate != cycleDate) {
                requestCoordinator.startMorningReferenceCycle()
            }
        }

        if (
            referenceRetryOpen &&
            t >= AFTERNOON_REFERENCE &&
            reference200CompleteDate != cycleDate
        ) {
            requestCoordinator.startAfternoonReferenceCycle()
        }

        // If 09:30 has not succeeded by 11:30, force the session-card reset
        // exactly once. This only changes the display projection; the raw
        // provider snapshot and existing LIVE/final engine remain untouched.
        if (
            t >= MORNING_LIVE &&
            reference930CompleteDate != cycleDate &&
            referenceResetDate != cycleDate
        ) {
            synchronized(stateLock) {
                val current = referenceStateLocked()
                val next = LiveReferenceStateReducer.markMorningResetIfNeeded(
                    state = current,
                    cycleDate = cycleDate,
                )
                if (next != current) {
                    applyReferenceStateLocked(next)
                    publishLocked()
                }
            }
        }
    }

    private fun shouldPoll(t: LocalTime): Boolean {
        val today = dateProvider()
        val hasClosedHold = closedHoldDate(today, t) != null
        val isWorkingDayToday = isWorkingDay(today)

        // Preserve the previous short-circuit: don't inspect feed state when
        // the market is closed or the current day is a weekend.
        val f = if (hasClosedHold || !isWorkingDayToday) {
            null
        } else {
            synchronized(stateLock) {
                primary?.feed?.takeIf { currentDay(it, today) }
            }
        }

        return shouldPollLiveAt(
            t = t,
            isWorkingDay = isWorkingDayToday,
            hasClosedHold = hasClosedHold,
            morningFinalized = f?.morning?.finalized,
            eveningFinalized = f?.evening?.finalized,
        )
    }

    private suspend fun recoverPreviousWorkingDayFinal() {
        val today = dateProvider()
        val now = clock()
        val bootstrap = freshInstallBootstrapPlan(today, now)
        val closedHold = closedHoldDate(today, now)
        val cycleDate = closedHold?.let(::previousWorkingDay) ?: bootstrap.cycleDate
        val fallbackFeedDate = closedHold?.let(::previousWorkingDay) ?: bootstrap.fallbackDate

        // First recover the exact cycle row represented by the app clock.
        // Fresh-install policy owns only the date selection; the Daily Flow
        // still decides what portions of that row are displayable.
        val currentFeed = if (closedHold != null) {
            runCatching {
                closedDayFeedFetcher?.invoke(cycleDate)
            }.getOrNull()?.takeIf {
                canonicalDate(it.date) == cycleDate
            }
        } else {
            runCatching {
                historicalFeedFetcher?.invoke(cycleDate)
            }.getOrNull()?.takeIf {
                canonicalDate(it.date) == cycleDate
            }
        }

        val fallbackFeed = if (currentFeed == null && fallbackFeedDate != null) {
            runCatching {
                historicalFeedFetcher?.invoke(fallbackFeedDate)
            }.getOrNull()?.takeIf {
                canonicalDate(it.date) == fallbackFeedDate
            }
        } else {
            null
        }

        val currentFinal = runCatching {
            historicalFinalFetcher?.invoke(cycleDate)
        }.getOrNull()?.takeIf {
            canonicalDate(it.date) == cycleDate
        }

        val fallbackFinal = if (
            currentFinal == null &&
            fallbackFeedDate != null &&
            fallbackFeedDate != cycleDate
        ) {
            runCatching {
                historicalFinalFetcher?.invoke(fallbackFeedDate)
            }.getOrNull()?.takeIf {
                canonicalDate(it.date) == fallbackFeedDate
            }
        } else {
            null
        }

        val recoveredFeed = currentFeed ?: fallbackFeed

        synchronized(stateLock) {
            // A current-cycle final has priority over the previous working
            // day's hero. When the current cycle is still pending, keep the
            // previous working-day final as the temporary hero hold.
            when {
                currentFinal != null -> lastFinal = currentFinal
                fallbackFinal != null -> lastFinal = fallbackFinal
            }

            if (recoveredFeed != null) {
                val projected = recoveredFeed
                val startedAt = monotonicMs()
                primary = SourceObservation(projected, startedAt, startedAt, 0L)

                // Historical data may fully reconstruct a completed held day:
                // - any pre-09:30 start (cycleDate is already the held day), or
                // - a same-day cold start after the evening final.
                //
                // Do not seed a working day's references during the active
                // 09:30/14:00 retry windows. Those slots must still be fetched
                // from Luke for the current cycle. Once the day is complete,
                // its historical row is an authoritative display baseline.
                val recoveredDate = canonicalDate(projected.date)
                val completedHeldCycle =
                    recoveredDate == cycleDate &&
                        (
                            cycleDate != today ||
                                !now.isBefore(EVENING_CLOSE)
                            )

                if (completedHeldCycle) {
                    applyReferenceStateLocked(
                        LiveReferenceStateReducer.restoreCompletedHeldCycle(
                            state = referenceStateLocked(),
                            feed = projected,
                            cycleDate = cycleDate,
                        )
                    )
                }

                latestFinalFor(projected)?.let { historicalFinal ->
                    if (
                        currentFinal == null &&
                        canonicalDate(historicalFinal.date) == recoveredDate
                    ) {
                        lastFinal = historicalFinal
                    }
                }

                publishLocked()
                return
            }

            publishLocked()
        }
    }

    private fun publishLocked() {
        val today = dateProvider()
        lastPhaseMarker = liveWindowAction(clock()) to liveSessionForTime(clock())
        val scheduleTime = clock()
        val resolution = resolveDisplayLocked(today, scheduleTime)
        applyResolutionEffectsLocked(resolution, today)

        _state.value = projectLiveUiState(
            resolution = resolution,
            closedDay = currentClosedDayForNotice(today),
        )
    }

    /**
     * Resolve the current display without remembering a new hero/final or writing cache.
     * This stays under the existing state-lock call path so the observed state is consistent.
     */
    private fun resolveDisplayLocked(
        today: LocalDate,
        scheduleTime: LocalTime,
    ): LiveResolution {
        val displayPrimary = primary?.let { observation ->
            mergeReferenceIntoFeed(observation.feed)?.let { merged ->
                observation.copy(feed = merged)
            } ?: observation
        }?.takeIf { isLiveDisplayableFeed(it.feed, scheduleTime, today) }
            ?: mergeReferenceIntoFeed(null)?.let { feed ->
                val now = monotonicMs()
                SourceObservation(feed, now, now, 0L)
            }?.takeIf { isLiveDisplayableFeed(it.feed, scheduleTime, today) }
            ?: pendingDisplayFeed(primary?.feed, scheduleTime, today)?.let { feed ->
                val now = monotonicMs()
                SourceObservation(feed, now, now, 0L)
            }

        val closedHold = closedHoldDate(today, scheduleTime)
        return resolveLiveState(
            p = displayPrimary,
            s = null,
            now = Instant.now(),
            lastLive = lastLive,
            cachedFinal = lastFinal,
            scheduleTime = scheduleTime,
            scheduleDate = today,
            liveClosedDayDate = closedHold,
            primaryLiveSession = primaryLiveSession,
        )
    }

    /**
     * Apply the state/cache effects of an already-computed display resolution.
     * Keep this ordering aligned with the baseline: update lastLive first, then
     * remember/cache a newly resolved current-day final.
     */
    private fun applyResolutionEffectsLocked(
        resolution: LiveResolution,
        today: LocalDate,
    ) {
        if (resolution.heroLive) {
            lastLive = resolution.hero
        }

        val hero = resolution.hero
        if (
            !resolution.heroLive &&
            hero != null &&
            canonicalDate(hero.date) == today &&
            hero != lastFinal
        ) {
            lastFinal = hero
            cacheSaver(hero)
        }
    }


    private fun pendingDisplayFeed(
        sourceFeed: LiveFeedData?,
        scheduleTime: LocalTime,
        today: LocalDate,
    ): LiveFeedData? {
        val projected = projectPendingDisplayFeed(
            sourceFeed = sourceFeed,
            scheduleTime = scheduleTime,
            today = today,
        ) ?: return null

        return mergeReferenceIntoFeed(projected)
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return

        schedulerJob = scope.launch {
            val startupToday = dateProvider()
            val startupTime = clock()
            val startupClosed = closedHoldDate(startupToday, startupTime)
            val startupLiveSession = liveSessionForTime(startupTime)

            if (startupLiveSession != null && startupClosed == null) {
                // LIVE/finalization cold-start is latency-sensitive. Do not let
                // historical reconstruction block the first Luke request, and
                // do not allow a historical task to race and overwrite the
                // current LIVE feed. The existing cached same-session LIVE, when
                // available, is already restored by init().
                fetchCycle()
            } else {
                // Outside an active LIVE session, historical recovery remains
                // the source of held/fresh-start display state.
                recoverPreviousWorkingDayFinal()

                // Reference catch-up remains separate from normal LIVE polling.
                // When a closed-day hold is already known, skip the startup fetch
                // and keep the previous working-day snapshot.
                maybeReferenceFetch(startupTime)
                if (startupClosed == null) {
                    fetchCycle()
                }
            }

            var lastSchedulerDate = startupToday

            while (isActive) {
                val currentDate = dateProvider()
                val t = clock()

                // Publish once at the calendar boundary so a current-day-only
                // Closed Day notice disappears as soon as the new date starts.
                if (currentDate != lastSchedulerDate) {
                    lastSchedulerDate = currentDate
                    publishLocked()
                }

                synchronized(stateLock) {
                    val phaseMarker = liveWindowAction(t) to liveSessionForTime(t)
                    if (phaseMarker != lastPhaseMarker) {
                        publishLocked()
                    }
                }

                maybeReferenceFetch(t)

                if (shouldPoll(t)) {
                    launchRequest()
                }

                delay(NORMAL_POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        @Volatile
        private var shared: LiveCollector? = null

        val instance: LiveCollector
            get() = shared ?: error("LiveCollector not started")

        fun startOnce(
            context: Context,
            liveRoomSaver: (suspend (List<LiveDailyResultPatch>) -> Unit)? = null,
        ) {
            if (shared != null) return

            synchronized(this) {
                if (shared != null) return

                val collector = LiveCollector(
                    scope = CoroutineScope(
                        kotlinx.coroutines.SupervisorJob() + Dispatchers.Default
                    ),
                    fetcher = {
                        withContext(Dispatchers.IO) {
                            LiveApi.fetch()
                        }
                    },
                    secondaryFetcher = null,
                    clock = { LocalTime.now(YANGON) },
                    cacheLoader = { LiveCacheStore.load(context) },
                    cacheSaver = { LiveCacheStore.save(context, it) },
                    cacheFeedLoader = { LiveCacheStore.loadFeed(context) },
                    cacheFeedSaver = { LiveCacheStore.saveFeed(context, it) },
                    liveRoomSaver = liveRoomSaver,
                    closedDayFeedFetcher = { date ->
                        HistorySync.fetch2DHistory(date, date)
                            .firstOrNull()
                            ?.let(::historyRowToFeed)
                    },
                    closedDayDateLoader = {
                        LiveClosedDayStore.load(context)
                    },
                    closedDayDateSaver = { date ->
                        LiveClosedDayStore.save(context, date)
                    },
                    closedDayDateClearer = {
                        LiveClosedDayStore.clear(context)
                    },
                    historicalFinalFetcher = { date ->
                        HistorySync.fetch2DHistory(date, date)
                            .firstOrNull()
                            ?.let { row ->
                                historyRowToFinal(
                                    row,
                                    LocalTime.now(YANGON),
                                )
                            }
                    },
                    historicalFeedFetcher = { date ->
                        HistorySync.fetch2DHistory(date, date)
                            .firstOrNull()
                            ?.let { row ->
                                historyRowToFeed(
                                    row,
                                    currentYangonDate(),
                                    LocalTime.now(YANGON),
                                )
                            }
                    },
                )

                shared = collector
                collector.start()
            }
        }
    }
}

internal object LiveClosedDayStore {
    private const val PREFS_NAME = "live_display_cache"
    private const val KEY = "observed_closed_day"

    fun load(context: Context): LocalDate? = runCatching {
        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return null
        LocalDate.parse(raw)
    }.getOrNull()

    fun save(context: Context, date: LocalDate) {
        runCatching {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, date.toString())
                .apply()
        }
    }

    fun clear(context: Context) {
        runCatching {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply()
        }
    }
}

internal object LiveCacheStore {
    private const val PREFS_NAME = "live_display_cache"
    private const val KEY = "latest_final"
    private const val FEED_KEY = "latest_feed"

    fun load(context: Context): LiveHeroSnapshot? {
        return try {
            val raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, null)
                ?: return null

            val o = JSONObject(raw)
            if (o.optString("state") != "FINAL_CONFIRMED") return null

            val snapshot = LiveHeroSnapshot(
                result = o.getString("result"),
                set = o.getString("set"),
                value = o.getString("value"),
                sessionLabel = o.getString("sessionLabel"),
                date = o.getString("date"),
            )

            val today = currentYangonDate()
            val cycleDate = dailyCycleDate(today, LocalTime.now(YANGON))
            val previousWorking = previousWorkingDay(cycleDate)
            snapshot.takeIf {
                val d = canonicalDate(it.date)
                d == cycleDate || d == previousWorking
            }
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, snapshot: LiveHeroSnapshot) {
        runCatching {
            val o = JSONObject()
                .put("result", snapshot.result)
                .put("set", snapshot.set)
                .put("value", snapshot.value)
                .put("sessionLabel", snapshot.sessionLabel)
                .put("date", snapshot.date)
                .put("state", "FINAL_CONFIRMED")

            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, o.toString())
                .apply()
        }
    }

    fun loadFeed(context: Context): LiveFeedData? {
        return runCatching {
            val raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(FEED_KEY, null)
                ?: return null

            val o = JSONObject(raw)
            LiveFeedData(
                date = o.optString("date", ""),
                currentTime = o.optString("currentTime", ""),
                live = o.optString("live", LIVE_PENDING),
                liveSet = o.optString("liveSet", LIVE_PENDING),
                liveVal = o.optString("liveVal", LIVE_PENDING),
                morning = loadSession(o.optJSONObject("morning")),
                evening = loadSession(o.optJSONObject("evening")),
                modern930 = o.optString("modern930", LIVE_PENDING),
                internet930 = o.optString("internet930", LIVE_PENDING),
                modern200 = o.optString("modern200", LIVE_PENDING),
                internet200 = o.optString("internet200", LIVE_PENDING),
                sourceTag = o.optString("sourceTag", "LUKE"),
                serverTimeEpochMs = if (o.has("serverTimeEpochMs") && !o.isNull("serverTimeEpochMs")) {
                    o.optLong("serverTimeEpochMs")
                } else {
                    null
                },
                isCloseDay = o.optBoolean("isCloseDay", false),
            )
        }.getOrNull()
    }

    private fun loadSession(o: JSONObject?): LiveSessionData {
        if (o == null) return LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false)
        return LiveSessionData(
            result = o.optString("result", LIVE_PENDING),
            set = o.optString("set", LIVE_PENDING),
            value = o.optString("value", LIVE_PENDING),
            finalized = o.optBoolean("finalized", false),
            historyId = o.optString("historyId").ifBlank { null },
            providerOpenTime = o.optString("providerOpenTime").ifBlank { null },
        )
    }

    fun saveFeed(context: Context, feed: LiveFeedData) {
        runCatching {
            val o = JSONObject()
                .put("date", feed.date)
                .put("currentTime", feed.currentTime)
                .put("live", feed.live)
                .put("liveSet", feed.liveSet)
                .put("liveVal", feed.liveVal)
                .put("morning", saveSession(feed.morning))
                .put("evening", saveSession(feed.evening))
                .put("modern930", feed.modern930)
                .put("internet930", feed.internet930)
                .put("modern200", feed.modern200)
                .put("internet200", feed.internet200)
                .put("sourceTag", feed.sourceTag)
                .put("isCloseDay", feed.isCloseDay)
                .apply {
                    feed.serverTimeEpochMs?.let { put("serverTimeEpochMs", it) }
                }

            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(FEED_KEY, o.toString())
                .apply()
        }
    }

    private fun saveSession(session: LiveSessionData): JSONObject =
        JSONObject()
            .put("result", session.result)
            .put("set", session.set)
            .put("value", session.value)
            .put("finalized", session.finalized)
            .apply {
                session.historyId?.let { put("historyId", it) }
                session.providerOpenTime?.let { put("providerOpenTime", it) }
            }
}
