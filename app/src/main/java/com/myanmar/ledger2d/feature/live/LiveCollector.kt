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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val YANGON = ZoneId.of("Asia/Yangon")

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

private val MORNING_REFERENCE = LocalTime.of(9, 30)
private val MORNING_LIVE = LocalTime.of(11, 30)
private val MORNING_CLOSE = LocalTime.of(12, 1)
private val MORNING_CATCHUP_END = LocalTime.of(12, 31)
private val AFTERNOON_REFERENCE = LocalTime.of(14, 0)
private val EVENING_LIVE = LocalTime.of(16, 0)
private val EVENING_CLOSE = LocalTime.of(16, 30)
private val EVENING_CATCHUP_END = LocalTime.of(17, 0)

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

internal enum class LiveWindowAction {
    NONE,
    REFERENCE_ONLY,
    LIVE_POLLING,
    FINALIZING,
}

internal enum class LiveSession {
    MORNING,
    EVENING,
}

internal fun liveSessionForTime(t: LocalTime): LiveSession? = when {
    // The session remains "live" through the finalization/catch-up window.
    // At the exact final boundary the provider may still be carrying the
    // last streaming value while it is preparing the verified final result.
    // Keeping the same session owner lets preserveActiveLive() retain that
    // last valid LIVE observation until the final result arrives.
    t >= MORNING_LIVE && t < MORNING_CATCHUP_END -> LiveSession.MORNING
    t >= EVENING_LIVE && t < EVENING_CATCHUP_END -> LiveSession.EVENING
    else -> null
}

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

internal fun liveWindowAction(t: LocalTime): LiveWindowAction = when {
    t.isBefore(MORNING_REFERENCE) -> LiveWindowAction.NONE
    t.isBefore(MORNING_LIVE) -> LiveWindowAction.REFERENCE_ONLY
    t.isBefore(MORNING_CLOSE) -> LiveWindowAction.LIVE_POLLING
    t.isBefore(MORNING_CATCHUP_END) -> LiveWindowAction.FINALIZING
    t.isBefore(AFTERNOON_REFERENCE) -> LiveWindowAction.NONE
    t.isBefore(AFTERNOON_REFERENCE.plusMinutes(1)) -> LiveWindowAction.REFERENCE_ONLY
    t.isBefore(EVENING_LIVE) -> LiveWindowAction.NONE
    t.isBefore(EVENING_CLOSE) -> LiveWindowAction.LIVE_POLLING
    t.isBefore(EVENING_CATCHUP_END) -> LiveWindowAction.FINALIZING
    else -> LiveWindowAction.NONE
}

internal fun currentYangonDate(): LocalDate = LocalDate.now(YANGON)

internal fun isWorkingDay(date: LocalDate): Boolean =
    date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

internal fun previousWorkingDay(date: LocalDate): LocalDate {
    var d = date.minusDays(1)
    while (!isWorkingDay(d)) {
        d = d.minusDays(1)
    }
    return d
}

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

internal fun dailyCycleDate(
    date: LocalDate = currentYangonDate(),
    time: LocalTime = LocalTime.now(YANGON),
): LocalDate =
    when {
        !isWorkingDay(date) -> previousWorkingDay(date)
        time.isBefore(MORNING_REFERENCE) -> previousWorkingDay(date)
        else -> date
    }

private fun isDisplayableFeedForSchedule(
    feed: LiveFeedData,
    scheduleTime: LocalTime,
    today: LocalDate = currentYangonDate(),
): Boolean {
    val feedDate = canonicalDate(feed.date) ?: return false
    val cycleDate = dailyCycleDate(today, scheduleTime)

    // dailyCycleDate() already moves a working-day pre-09:30 start onto the
    // previous working day. Do not step back a second time there: that would
    // turn Tuesday morning's Monday hold into Friday, Wednesday morning's
    // Tuesday hold into Monday, and so on.
    //
    // From 09:30 until 11:30, the previous working day may still seed the
    // held display while today's LIVE cycle has not started. Once the
    // morning LIVE session starts, only today's feed may remain displayable.
    // Weekend hold is cycleDate (Friday) only.
    return when {
        !isWorkingDay(today) -> feedDate == cycleDate
        scheduleTime.isBefore(MORNING_REFERENCE) -> feedDate == cycleDate
        scheduleTime.isBefore(MORNING_LIVE) ->
            feedDate == cycleDate || feedDate == previousWorkingDay(cycleDate)
        else -> feedDate == cycleDate
    }
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

private fun age(o: SourceObservation?): Long =
    if (o == null) Long.MAX_VALUE
    else maxOf(0L, monotonicMs() - o.fetchedAtElapsedMs)

private fun hasValidLive(
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

private fun liveValid(
    o: SourceObservation?,
    date: LocalDate = currentYangonDate(),
): Boolean = hasValidLive(o?.feed, date)

private fun finalValid(
    session: LiveSessionData?,
    feed: LiveFeedData?,
    date: LocalDate = currentYangonDate(),
): Boolean {
    if (session == null || feed == null || !currentDay(feed, date)) return false
    // Luke's result_1200 / result_430 is the final-result trigger.
    // SET/VALUE are supplementary metadata and may arrive slightly later.
    return isValidLive2d(session.result)
}

private fun latestFinalFor(feed: LiveFeedData): LiveHeroSnapshot? = when {
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

internal fun resolveLiveState(
    p: SourceObservation?,
    s: SourceObservation?,
    now: Instant,
    lastLive: LiveHeroSnapshot?,
    cachedFinal: LiveHeroSnapshot?,
    scheduleTime: LocalTime? = null,
    scheduleDate: LocalDate? = null,
    liveClosedDayDate: LocalDate? = null,
    primaryLiveSession: LiveSession? = null,
): LiveResolution {
    val effectiveScheduleTime = scheduleTime ?: now.atZone(YANGON).toLocalTime()
    val today = scheduleDate ?: currentYangonDate()
    val closedHoldDate = liveClosedDayDate
    val cycleDate = if (closedHoldDate != null) {
        previousWorkingDay(closedHoldDate)
    } else {
        dailyCycleDate(today, effectiveScheduleTime)
    }
    val previousWorking = previousWorkingDay(cycleDate)
    val feed = p?.feed?.takeIf {
        if (closedHoldDate != null) {
            canonicalDate(it.date) == cycleDate
        } else {
            isDisplayableFeedForSchedule(it, effectiveScheduleTime, today)
        }
    }
    val cycleFinal =
        feed?.takeIf { canonicalDate(it.date) == cycleDate }?.let(::latestFinalFor)
    val previousWorkingFinal =
        feed?.takeIf { canonicalDate(it.date) == previousWorking }?.let(::latestFinalFor)
    val cachedRelevantFinal = cachedFinal?.takeIf {
        val d = canonicalDate(it.date)
        when {
            closedHoldDate != null -> d == cycleDate
            !isWorkingDay(today) -> d == cycleDate
            effectiveScheduleTime.isBefore(MORNING_REFERENCE) -> d == cycleDate
            else -> d == cycleDate || d == previousWorking
        }
    }
    val heldFinal = cycleFinal ?: previousWorkingFinal ?: cachedRelevantFinal

    if (feed == null) {
        val activeSession = liveSessionForTime(effectiveScheduleTime)
        val currentSessionLive = lastLive?.takeIf {
            activeSession != null &&
                primaryLiveSession == activeSession &&
                canonicalDate(it.date) == cycleDate
        }
        if (currentSessionLive != null) {
            return LiveResolution(
                null,
                currentSessionLive,
                true,
                LiveStatus.LIVE_CONFIRMED,
                "",
                0,
                age(p),
            )
        }
        // Never substitute a previous-day final while an active LIVE/finalization
        // session is waiting for its first current-day Luke response.
        if (activeSession != null) {
            return LiveResolution(
                null,
                null,
                false,
                LiveStatus.WAITING,
                "",
                0,
                age(p),
            )
        }
        return LiveResolution(null, heldFinal, false, LiveStatus.WAITING, "", 0, age(p))
    }

    val activeLiveSession = liveSessionForTime(effectiveScheduleTime)
    val canShowLive =
        activeLiveSession != null &&
            liveValid(p, today) &&
            primaryLiveSession == activeLiveSession
    val morningFinal = finalValid(feed.morning, feed, today)
    val eveningFinal = finalValid(feed.evening, feed, today)

    fun liveHero(): LiveHeroSnapshot? =
        if (canShowLive) {
            LiveHeroSnapshot(
                result = feed.live,
                set = feed.liveSet,
                value = feed.liveVal,
                sessionLabel = feed.currentTime,
                date = feed.date,
            )
        } else {
            null
        }

    fun finalHero(): LiveHeroSnapshot? = latestFinalFor(feed)

    // The app clock controls the daily phase. Luke's date/current_time are
    // provider data only and must never advance the app into another phase.
    val phaseTime = effectiveScheduleTime

    // Saturday/Sunday and a Luke-confirmed Live-only closed day hold the
    // last working-day snapshot for the whole closed interval.
    if (closedHoldDate != null || !isWorkingDay(today)) {
        return LiveResolution(
            feed,
            heldFinal,
            false,
            LiveStatus.WAITING,
            "",
            1,
            age(p),
        )
    }

    return when {
        phaseTime.isBefore(MORNING_LIVE) -> {
            LiveResolution(
                feed,
                heldFinal,
                false,
                LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(MORNING_CLOSE) -> {
            // 11:30–12:01 is one continuous MORNING session. A current-day
            // final, when it appears early, wins immediately; otherwise only
            // this session's validated LIVE value may occupy the hero.
            val live = liveHero()
            val hero = if (morningFinal) finalHero() else live
            LiveResolution(
                feed,
                hero,
                !morningFinal && live != null,
                when {
                    morningFinal -> LiveStatus.FINAL_CONFIRMED
                    live != null -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(LocalTime.of(13, 0)) -> {
            // 12:01 onward remains the same morning LIVE session while Luke
            // is still finalizing. Never fall back to yesterday's/morning-held
            // final before today's 12:01 result is actually present.
            val live = liveHero()
            val hero = if (morningFinal) finalHero() else live
            LiveResolution(
                feed,
                hero,
                !morningFinal && live != null,
                when {
                    morningFinal -> LiveStatus.FINAL_CONFIRMED
                    live != null -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(EVENING_LIVE) -> {
            // 13:00–16:00 is a frozen/reference phase. A one-time Luke
            // startup/reference response may still contain a live value, but
            // that value must never become the active LIVE hero outside the
            // scheduled evening LIVE window.
            val hero = finalHero() ?: heldFinal
            LiveResolution(
                feed,
                hero,
                false,
                if (hero != null) LiveStatus.FINAL_CONFIRMED else LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        else -> {
            // 16:00–4:30 is one continuous EVENING session. Until today's
            // evening final exists, only this session's validated LIVE value
            // may occupy the hero; do not substitute the morning final or an
            // older held final on a cold start.
            val live = liveHero()
            val hero = if (eveningFinal) finalHero() else live
            LiveResolution(
                feed,
                hero,
                !eveningFinal && live != null,
                when {
                    eveningFinal -> LiveStatus.FINAL_CONFIRMED
                    live != null -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }
    }
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
    private val requestSequence = AtomicLong(0L)
    private val latestAppliedSequence = AtomicLong(0L)
    private val stateLock = Any()

    private var schedulerJob: Job? = null
    private var reference930Cycle: Job? = null
    private var reference930CycleDate: LocalDate? = null
    private var reference200Cycle: Job? = null
    private var reference200CycleDate: LocalDate? = null
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

    private fun nextWorkingDayAfter(date: LocalDate): LocalDate {
        var next = date.plusDays(1)
        while (!isWorkingDay(next)) next = next.plusDays(1)
        return next
    }

    /**
     * Luke-confirmed closed days behave like weekends for Live display only.
     * The previous working-day hold spans intervening weekend dates until the
     * next working day's 09:30 boundary.
     */
    private fun closedHoldDate(today: LocalDate, now: LocalTime): LocalDate? {
        val closed = closedDayDate ?: return null
        if (closed == today) return closed

        if (today.isAfter(closed)) {
            val nextWorking = nextWorkingDayAfter(closed)
            if (
                today.isBefore(nextWorking) ||
                (today == nextWorking && now.isBefore(MORNING_REFERENCE))
            ) {
                return closed
            }
        }
        return null
    }

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
    ): Boolean {
        val holdDate = closedHoldDate(today, scheduleTime)
        if (holdDate != null) {
            val feedDate = canonicalDate(feed.date) ?: return false
            return feedDate == previousWorkingDay(holdDate)
        }
        return isDisplayableFeedForSchedule(feed, scheduleTime, today)
    }

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
                primaryLiveSession = inferLiveSessionFromSourceTime(cachedFeed)
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
        val sequence = requestSequence.incrementAndGet()

        scope.launch {
            val startedAt = monotonicMs()
            val feed = try {
                fetcher()
            } catch (_: Exception) {
                null
            }
            val finishedAt = monotonicMs()

            if (feed == null) return@launch

            synchronized(stateLock) {
                if (!isUsableLukeSnapshot(feed)) {
                    feed?.let(::capturePreviousWorkingDayFinalLocked)
                    return@synchronized
                }

                val previous = primary?.feed
                val incomingTime = parseDecisionInstant(feed)
                val previousTime = previous?.let(::parseDecisionInstant)
                val appliedSequence = latestAppliedSequence.get()

                // Provider time is the source-of-truth ordering signal when it
                // exists. Request sequence is only a tie-breaker for equal or
                // unavailable provider times. This matters because start() and
                // LiveScreen can issue overlapping immediate requests: an older
                // request can legitimately finish after a newer request but
                // carry a newer Luke timestamp/final result.
                if (incomingTime != null && previousTime != null) {
                    when {
                        incomingTime.isBefore(previousTime) -> { capturePreviousWorkingDayFinalLocked(feed); return@synchronized }
                        incomingTime == previousTime && sequence <= appliedSequence -> return@synchronized
                    }
                } else if (incomingTime == null && previousTime != null) {
                    // Never replace a timestamped snapshot with one whose
                    // provider time cannot be established.
                    return@synchronized
                } else if (incomingTime == null && sequence <= appliedSequence) {
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

                val protectedFinals = protectFinalSessions(previous, feed)
                val protected = preserveActiveLive(
                    previous = previous,
                    incoming = protectedFinals,
                    scheduleTime = scheduleTime,
                    scheduleDate = today,
                )
                latestAppliedSequence.set(sequence)
                primary = SourceObservation(
                    protected,
                    finishedAt,
                    startedAt,
                    finishedAt - startedAt,
                )

                val activeSession = liveSessionForTime(scheduleTime)
                primaryLiveSession = when {
                    activeSession != null && hasValidLive(protected, today) -> activeSession
                    activeSession != null -> null
                    else -> primaryLiveSession
                }

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
    }

    private fun preserveActiveLive(
        previous: LiveFeedData?,
        incoming: LiveFeedData,
        scheduleTime: LocalTime,
        scheduleDate: LocalDate,
    ): LiveFeedData {
        val activeSession = liveSessionForTime(scheduleTime) ?: return incoming
        if (primaryLiveSession != activeSession || !hasValidLive(previous, scheduleDate)) {
            return incoming
        }
        if (hasValidLive(incoming, scheduleDate)) {
            return incoming
        }

        return incoming.copy(
            live = previous!!.live,
            liveSet = previous.liveSet,
            liveVal = previous.liveVal,
        )
    }

    private fun inferLiveSessionFromSourceTime(feed: LiveFeedData): LiveSession? {
        val sourceTime = parseDecisionInstant(feed)
            ?.atZone(YANGON)
            ?.toLocalTime()
            ?: return null

        return when {
            sourceTime >= MORNING_LIVE && sourceTime < MORNING_CATCHUP_END ->
                LiveSession.MORNING
            sourceTime >= EVENING_LIVE && sourceTime < EVENING_CATCHUP_END ->
                LiveSession.EVENING
            else -> null
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

    private fun capturePreviousWorkingDayFinalLocked(feed: LiveFeedData) {
        val final = latestFinalFor(feed) ?: return
        val finalDate = canonicalDate(final.date) ?: return
        val today = dateProvider()
        val cycleDate = dailyCycleDate(today, clock())
        val previousWorking = previousWorkingDay(cycleDate)
        if (finalDate != previousWorking) return
        val existingDate = canonicalDate(lastFinal?.date.orEmpty())
        if (existingDate == null || existingDate == previousWorking) {
            lastFinal = final
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

    private fun protectFinalSessions(
        previous: LiveFeedData?,
        incoming: LiveFeedData,
    ): LiveFeedData {
        val today = dateProvider()
        if (previous == null || !currentDay(previous, today) || !currentDay(incoming, today)) {
            return incoming
        }

        return incoming.copy(
            morning = if (previous.morning.finalized && !incoming.morning.finalized) {
                previous.morning
            } else {
                incoming.morning
            },
            evening = if (previous.evening.finalized && !incoming.evening.finalized) {
                previous.evening
            } else {
                incoming.evening
            },
        )
    }

    private fun isUsableLukeSnapshot(feed: LiveFeedData): Boolean {
        if (!isLiveDisplayableFeed(feed, clock(), dateProvider())) return false
        if (feed.currentTime.isBlank() || parseDecisionInstant(feed) == null) return false

        val today = dateProvider()
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

    private fun validReferencePair(
        feed: LiveFeedData?,
        modern: String,
        internet: String,
    ): Boolean =
        feed != null &&
            isValidLive2d(modern) &&
            isValidLive2d(internet)

    private fun promoteCurrentCycleReferencesLocked(
        feed: LiveFeedData,
        today: LocalDate,
        scheduleTime: LocalTime,
    ) {
        if (!isWorkingDay(today)) return
        val cycleDate = dailyCycleDate(today, scheduleTime)
        if (cycleDate != today) return
        if (!isCurrentCycleReferenceObservation(feed, cycleDate)) return

        if (
            !scheduleTime.isBefore(MORNING_REFERENCE) &&
            validReferencePair(feed, feed.modern930, feed.internet930)
        ) {
            reference930 = feed.modern930 to feed.internet930
            reference930Date = cycleDate
            reference930PendingDate = null
            reference930CompleteDate = cycleDate
            referenceResetDate = cycleDate
            referenceFeed = feed
            referenceFeedDate = cycleDate
        }

        if (
            !scheduleTime.isBefore(AFTERNOON_REFERENCE) &&
            validReferencePair(feed, feed.modern200, feed.internet200)
        ) {
            reference200 = feed.modern200 to feed.internet200
            reference200Date = cycleDate
            reference200PendingDate = null
            reference200CompleteDate = cycleDate
            referenceFeed = feed
            referenceFeedDate = cycleDate
        }
    }

    private fun mergeReferenceIntoFeed(base: LiveFeedData?): LiveFeedData? {
        val today = dateProvider()
        val t = clock()
        val cycleDate = dailyCycleDate(today, t)

        // The reference fetch has its own lifecycle. When it succeeds before
        // the main poll returns a usable snapshot, use that successful feed as
        // the display baseline instead of waiting for primary.
        val effectiveBase = base ?: referenceFeed?.takeIf {
            referenceFeedDate == cycleDate
        }

        if (effectiveBase == null) return null

        if (closedHoldDate(today, t) != null) return effectiveBase

        var out = effectiveBase

        // 09:30 starts the new cycle. A successful pair is applied directly;
        // after a failed attempt, pending masks the provider's previous-day
        // values. The 14:00 slot is also reset at 09:30.
        when {
            reference930Date == cycleDate && reference930 != null -> {
                val pair = reference930!!
                out = out.copy(
                    modern930 = pair.first,
                    internet930 = pair.second,
                )
            }
            reference930PendingDate == cycleDate -> {
                out = out.copy(
                    modern930 = LIVE_PENDING,
                    internet930 = LIVE_PENDING,
                )
            }
        }

        when {
            reference200Date == cycleDate && reference200 != null -> {
                val pair = reference200!!
                out = out.copy(
                    modern200 = pair.first,
                    internet200 = pair.second,
                )
            }
            reference200PendingDate == cycleDate -> {
                out = out.copy(
                    modern200 = LIVE_PENDING,
                    internet200 = LIVE_PENDING,
                )
            }
            isWorkingDay(today) &&
                !t.isBefore(MORNING_REFERENCE) &&
                reference200Date != cycleDate -> {
                // The 14:00 slot belongs to the new working-day cycle from
                // 09:30 onward, even before its independent retry cycle starts.
                out = out.copy(
                    modern200 = LIVE_PENDING,
                    internet200 = LIVE_PENDING,
                )
            }
        }

        val pendingSessions = LiveSessionData(
            LIVE_PENDING,
            LIVE_PENDING,
            LIVE_PENDING,
            false,
        )

        val workingToday = isWorkingDay(today)

        // A successful 09:30 reference resets the session cards immediately.
        // When 09:30 has not succeeded yet, old cards may remain temporarily.
        if (
            workingToday &&
            referenceResetDate == cycleDate &&
            !t.isBefore(MORNING_REFERENCE) &&
            t.isBefore(MORNING_LIVE)
        ) {
            out = out.copy(
                morning = pendingSessions,
                evening = pendingSessions,
            )
        } else if (
            workingToday &&
            !t.isBefore(MORNING_LIVE) &&
            (
                t.isBefore(MORNING_CLOSE) ||
                    !currentDay(out, today)
            )
        ) {
            // On a working day, 11:30 is a hard display reset. This does not
            // mutate the raw provider snapshot, so the existing LIVE/final
            // engine and its protections remain untouched. After 12:01 a
            // verified current-day morning final may render normally.
            out = out.copy(
                morning = pendingSessions,
                evening = pendingSessions,
            )
        }

        if (
            workingToday &&
            !t.isBefore(EVENING_LIVE) &&
            t.isBefore(EVENING_CLOSE) &&
            currentDay(out, today)
        ) {
            // Evening starts a new session at 16:00. Preserve the verified
            // morning final, but project the evening card to Pending until
            // the 16:30 final is confirmed.
            out = out.copy(evening = pendingSessions)
        }

        return out
    }

    private fun referenceRetryWindowOpen(
        cycleDate: LocalDate,
        currentDate: LocalDate,
        now: LocalTime,
    ): Boolean {
        if (closedHoldDate(currentDate, now) != null) return false
        if (currentDate == cycleDate) {
            // Friday's state becomes the weekend-held snapshot after the
            // evening final; no late reference fetch may mutate it.
            return !(cycleDate.dayOfWeek == DayOfWeek.FRIDAY && !now.isBefore(EVENING_CLOSE))
        }

        val nextDate = cycleDate.plusDays(1)
        return currentDate == nextDate &&
            isWorkingDay(nextDate) &&
            now.isBefore(MORNING_REFERENCE)
    }

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

        if (closedSnapshot) {
            val holdDate = previousWorkingDay(cycleDate)
            val heldFeed = runCatching {
                closedDayFeedFetcher?.invoke(holdDate)
            }.getOrNull()

            synchronized(stateLock) {
                if (!referenceRetryWindowOpen(cycleDate, dateProvider(), clock())) return true

                closedDayDate = cycleDate
                closedDayDateSaver(cycleDate)

                reference930 = null
                reference930Date = null
                reference930PendingDate = null
                reference930CompleteDate = null
                reference200 = null
                reference200Date = null
                reference200PendingDate = null
                reference200CompleteDate = null
                referenceResetDate = null
                referenceFeed = null
                referenceFeedDate = null
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

        synchronized(stateLock) {
            if (!referenceRetryWindowOpen(cycleDate, dateProvider(), clock())) return false

            if (isMorning) {
                val valid =
                    feed != null &&
                        isCurrentCycleReferenceObservation(feed, cycleDate) &&
                        validReferencePair(
                            feed,
                            feed.modern930,
                            feed.internet930,
                        )

                if (valid) {
                    reference930 =
                        feed!!.modern930 to feed.internet930
                    reference930Date = cycleDate
                    reference930PendingDate = null
                    reference930CompleteDate = cycleDate
                    referenceResetDate = cycleDate
                    referenceFeed = feed
                    referenceFeedDate = cycleDate
                } else if (reference930CompleteDate != cycleDate) {
                    capturePreviousWorkingDayFinalLocked(feed)
                    // A failed/stale dedicated retry must never regress a
                    // reference that was already accepted from another valid
                    // Luke observation.
                    reference930 = null
                    reference930Date = null
                    reference930PendingDate = cycleDate
                }
            } else {
                val valid =
                    feed != null &&
                        isCurrentCycleReferenceObservation(feed, cycleDate) &&
                        validReferencePair(
                            feed,
                            feed.modern200,
                            feed.internet200,
                        )

                if (valid) {
                    reference200 =
                        feed!!.modern200 to feed.internet200
                    reference200Date = cycleDate
                    reference200PendingDate = null
                    reference200CompleteDate = cycleDate
                    referenceFeed = feed
                    referenceFeedDate = cycleDate
                } else if (reference200CompleteDate != cycleDate) {
                    capturePreviousWorkingDayFinalLocked(feed)
                    // A failed/stale dedicated retry must never regress a
                    // reference that was already accepted from another valid
                    // Luke observation.
                    reference200 = null
                    reference200Date = cycleDate
                    reference200PendingDate = cycleDate
                }
            }

            publishLocked()
            if (feed != null) {
                syncLiveRoom(feed)
            }
        }

        return if (isMorning) {
            reference930CompleteDate == cycleDate
        } else {
            reference200CompleteDate == cycleDate
        }
    }

    private fun fetchReference930Cycle() {
        val today = dateProvider()
        if (reference930CompleteDate == today) {
            return
        }
        if (reference930Cycle?.isActive == true) {
            if (reference930CycleDate == today) return
            reference930Cycle?.cancel()
            reference930Cycle = null
        }

        reference930CycleDate = today
        reference930Cycle = scope.launch {
            val cycleDate = today

            while (isActive) {
                val now = clock()
                val nowDate = dateProvider()

                if (!referenceRetryWindowOpen(cycleDate, nowDate, now)) {
                    return@launch
                }

                if (fetchReferencePair(true, cycleDate)) return@launch

                delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
            }
        }
    }

    private fun fetchReference200Cycle() {
        val today = dateProvider()
        if (reference200CompleteDate == today) {
            return
        }
        if (reference200Cycle?.isActive == true) {
            if (reference200CycleDate == today) return
            reference200Cycle?.cancel()
            reference200Cycle = null
        }

        reference200CycleDate = today
        reference200Cycle = scope.launch {
            val cycleDate = today

            while (isActive) {
                val now = clock()
                val nowDate = dateProvider()

                if (!referenceRetryWindowOpen(cycleDate, nowDate, now)) {
                    return@launch
                }

                if (nowDate == cycleDate && now.isBefore(AFTERNOON_REFERENCE)) {
                    return@launch
                }

                if (fetchReferencePair(false, cycleDate)) return@launch

                delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
            }
        }
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
                // From 09:30 onward, previous-day 09:30/14:00 values are no
                // longer valid for the new cycle. Mask both slots immediately
                // while their independent current-day fetches are running.
                // A successful fetch clears the pending marker and supplies
                // today's value.
                if (
                    reference930CompleteDate != cycleDate &&
                    reference930PendingDate != cycleDate
                ) {
                    reference930 = null
                    reference930Date = null
                    reference930PendingDate = cycleDate
                }

                if (
                    reference200CompleteDate != cycleDate &&
                    reference200PendingDate != cycleDate
                ) {
                    // The new working-day cycle owns the 14:00 reference slot
                    // from 09:30 onward.
                    reference200 = null
                    reference200Date = null
                    reference200PendingDate = cycleDate
                }
                publishLocked()
            }

            if (reference930CompleteDate != cycleDate) {
                fetchReference930Cycle()
            }
        }

        if (
            referenceRetryOpen &&
            t >= AFTERNOON_REFERENCE &&
            reference200CompleteDate != cycleDate
        ) {
            fetchReference200Cycle()
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
                if (
                    reference930CompleteDate != cycleDate &&
                    referenceResetDate != cycleDate
                ) {
                    referenceResetDate = cycleDate
                    publishLocked()
                }
            }
        }
    }

    private fun shouldPoll(t: LocalTime): Boolean {
        val today = dateProvider()
        if (closedHoldDate(today, t) != null) return false
        if (!isWorkingDay(today)) return false

        val f = synchronized(stateLock) {
            primary?.feed?.takeIf { currentDay(it, today) }
        }

        return when {
            t >= MORNING_LIVE && t < MORNING_CLOSE -> true
            t >= MORNING_CLOSE && t < MORNING_CATCHUP_END ->
                f?.morning?.finalized != true
            t >= EVENING_LIVE && t < EVENING_CLOSE -> true
            t >= EVENING_CLOSE && t < EVENING_CATCHUP_END ->
                f?.evening?.finalized != true
            else -> false
        }
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
                    val pair930 = projected.modern930 to projected.internet930
                    if (validReferencePair(projected, pair930.first, pair930.second)) {
                        reference930 = pair930
                        reference930Date = cycleDate
                        reference930CompleteDate = cycleDate
                        reference930PendingDate = null
                    }

                    val pair200 = projected.modern200 to projected.internet200
                    if (validReferencePair(projected, pair200.first, pair200.second)) {
                        reference200 = pair200
                        reference200Date = cycleDate
                        reference200CompleteDate = cycleDate
                        reference200PendingDate = null
                    }
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
        val resolution = resolveLiveState(
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

        _state.value = LiveUiState.Data(
            feed = resolution.displayFeed,
            hero = resolution.hero,
            heroLive = resolution.heroLive,
            stale = false,
            secondaryFeed = null,
            sourceMessage = "",
            status = resolution.status,
            staleAgeMs = resolution.staleAgeMs,
            closedDay = currentClosedDayForNotice(today),
        )
    }

    private fun pendingDisplayFeed(
        sourceFeed: LiveFeedData?,
        scheduleTime: LocalTime,
        today: LocalDate,
    ): LiveFeedData? {
        val inPendingWindow = scheduleTime >= MORNING_REFERENCE && (scheduleTime.isBefore(MORNING_LIVE) || (scheduleTime >= MORNING_LIVE && scheduleTime < MORNING_CATCHUP_END) || (scheduleTime >= AFTERNOON_REFERENCE && scheduleTime < EVENING_CATCHUP_END))
        if (!isWorkingDay(today) || !inPendingWindow) return null

        val pendingSession = LiveSessionData(
            LIVE_PENDING,
            LIVE_PENDING,
            LIVE_PENDING,
            false,
        )

        val source = sourceFeed ?: LiveFeedData(
            date = today.toString(),
            currentTime = scheduleTime.toString(),
            live = LIVE_PENDING,
            liveSet = LIVE_PENDING,
            liveVal = LIVE_PENDING,
            morning = pendingSession,
            evening = pendingSession,
            modern930 = LIVE_PENDING,
            internet930 = LIVE_PENDING,
            modern200 = LIVE_PENDING,
            internet200 = LIVE_PENDING,
            sourceTag = "LUKE",
            serverTimeEpochMs = null,
            isCloseDay = false,
        )

        // This is a display-only projection for the gap where the stored/raw
        // provider feed belongs to a previous day or is unavailable. It never
        // replaces primary, cache, Room history, or any financial state.
        val projected = source.copy(
            date = today.toString(),
            currentTime = scheduleTime.toString(),
            live = LIVE_PENDING,
            liveSet = LIVE_PENDING,
            liveVal = LIVE_PENDING,
            morning = pendingSession,
            evening = pendingSession,
        )

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
