package com.myanmar.ledger2d.feature.live

import android.content.Context
import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
import com.myanmar.ledger2d.core.repository.HistorySync
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val LIVE_HISTORY_RECOVERY_ATTEMPTS = 2
private const val LIVE_HISTORY_RECOVERY_RETRY_DELAY_MS = 1_000L

internal fun monotonicMs(): Long = runCatching {
    SystemClock.elapsedRealtime()
}.getOrElse {
    System.nanoTime() / 1_000_000L
}

internal suspend fun <T> runSuspendCatchingCancellable(block: suspend () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Match the previous runCatching contract while allowing cancellation to propagate.
        null
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

// Bounded cold-start recovery only; this does not reopen the normal after-hours polling window.
internal const val LATE_COLD_START_RETRY_ATTEMPTS = 4
internal const val LATE_COLD_START_RETRY_DELAY_MS = 5_000L

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
    private val sideEffects = LiveSideEffectQueue { Thread.holdsLock(stateLock) }

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
    private val closedDayProcessingDates = mutableSetOf<LocalDate>()
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
            isReferenceComplete(isMorning, cycleDate)
        },
        fetchReferencePair = { isMorning, cycleDate ->
            fetchReferencePair(isMorning, cycleDate)
        },
        onLiveResult = ::applyLiveRequestResult,
        acceptReferenceFeed = { isMorning, cycleDate, feed ->
            applyReferenceFeed(isMorning, cycleDate, feed)
        },
    )

    /**
     * Read the stored LIVE-only Closed Day date and resolve its display hold.
     * The date/time rule itself is pure and owned by LiveSchedulePolicy.
     */
    private fun closedHoldDate(today: LocalDate, now: LocalTime): LocalDate? =
        synchronized(stateLock) {
            liveClosedDayHoldDate(
                closedDate = closedDayDate,
                today = today,
                now = now,
            )
        }

    private fun isReferenceComplete(isMorning: Boolean, cycleDate: LocalDate): Boolean =
        synchronized(stateLock) {
            if (isMorning) reference930CompleteDate == cycleDate
            else reference200CompleteDate == cycleDate
        }

    private fun isMorningResetMarked(cycleDate: LocalDate): Boolean =
        synchronized(stateLock) { referenceResetDate == cycleDate }

    private fun clearExpiredClosedDayBeforeMorningBoundary(
        today: LocalDate,
        now: LocalTime,
    ) {
        val closed = synchronized(stateLock) { closedDayDate } ?: return
        if (
            !now.isBefore(MORNING_REFERENCE) &&
            today == nextWorkingDayAfter(closed)
        ) {
            synchronized(stateLock) {
                if (closedDayDate == closed) {
                    closedDayDate = null
                    sideEffects.dispatch { closedDayDateClearer() }
                }
            }
            sideEffects.drain()
        }
    }

    private fun currentClosedDayForNotice(today: LocalDate): Boolean =
        synchronized(stateLock) { closedDayDate == today }

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

    private fun sanitizeCachedFeedForFrozenLive(
        feed: LiveFeedData,
        scheduleTime: LocalTime,
    ): LiveFeedData {
        val frozenSession = liveSessionWhoseLiveIsFrozenAt(scheduleTime) ?: return feed
        val boundary = if (frozenSession == LiveSession.MORNING) MORNING_CLOSE else EVENING_CLOSE
        val sourceTime = parseDecisionInstant(feed)?.atZone(YANGON)?.toLocalTime()
        if (sourceTime != null && sourceTime.isBefore(boundary)) return feed

        LiveDiagnostics.warn(
            "Cached LIVE fields masked after cutoff session=$frozenSession " +
                "feedDate=${feed.date} current_time=${feed.currentTime} appTime=$scheduleTime",
        )
        return feed.copy(
            live = LIVE_PENDING,
            liveSet = LIVE_PENDING,
            liveVal = LIVE_PENDING,
        )
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
                sideEffects.dispatch { closedDayDateClearer() }
            }
        }
        val cachedFeed = cacheFeedLoader()
            ?.let { sanitizeCachedFeedForFrozenLive(it, scheduleTime) }
            ?.takeIf { isLiveDisplayableFeed(it, scheduleTime, today) }

        if (cachedFeed != null) {
            val startedAt = monotonicMs()
            primary = SourceObservation(cachedFeed, startedAt, startedAt, 0L)
            if (hasValidLive(cachedFeed, today)) {
                primaryLiveSession = LiveStateReducer.inferLiveSessionFromSourceTime(cachedFeed)
            }
            lastFinal = latestFinalFor(cachedFeed)
            publishLocked()
            sideEffects.dispatch { syncLiveRoom(cachedFeed, today, scheduleTime) }
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
        sideEffects.drain()
    }

    fun fetchCycle() {
        val today = dateProvider()
        val now = clock()
        val cycleDate = dailyCycleDate(today, now)
        val inMorningReferenceWindow =
            isWorkingDay(today) &&
                !now.isBefore(MORNING_REFERENCE) &&
                now.isBefore(MORNING_LIVE) &&
                closedHoldDate(today, now) == null

        if (
            inMorningReferenceWindow &&
            !isReferenceComplete(true, cycleDate)
        ) {
            // UI entry joins the app-scoped 09:30 cycle instead of launching
            // a second Luke request beside its normal-first attempt.
            requestCoordinator.startMorningReferenceCycle(forceNow = true)
        } else {
            launchRequest()
        }
    }

    private fun launchRequest() {
        requestCoordinator.launchLiveRequest()
    }

    private fun applyLiveRequestResult(event: LiveRequestResultEvent) {
        val sequence = event.sequence
        val feed = event.feed
        val startedAt = event.requestStartedAtElapsedMs
        val finishedAt = event.requestFinishedAtElapsedMs

        // A normal LIVE response may be the first Luke response we receive on
        // a Closed Day, including cold starts after the 09:30 retry window.
        // Resolve that signal before ordinary LIVE reduction can replace the
        // held previous-working-day display.
        val observedDate = dateProvider()
        val observedTime = clock()
        if (
            feed.isCloseDay &&
            isWorkingDay(observedDate) &&
            observedDate == dailyCycleDate(observedDate, observedTime) &&
            !observedTime.isBefore(MORNING_REFERENCE) &&
            isCurrentCycleReferenceObservation(feed, observedDate)
        ) {
            val shouldRecover = synchronized(stateLock) {
                if (closedDayDate == observedDate || !closedDayProcessingDates.add(observedDate)) {
                    false
                } else {
                    true
                }
            }
            if (shouldRecover) {
                scope.launch {
                    val holdDate = previousWorkingDay(observedDate)
                    val heldFeed = runSuspendCatchingCancellable {
                        closedDayFeedFetcher?.invoke(holdDate)
                    }?.takeIf { canonicalDate(it.date) == holdDate }
                    try {
                        applyReferenceResult(
                            LiveReferenceResultEvent(
                                isMorning = true,
                                cycleDate = observedDate,
                                feed = feed,
                                closedDayObservation = true,
                                heldFeed = heldFeed,
                            )
                        )
                    } finally {
                        synchronized(stateLock) {
                            closedDayProcessingDates.remove(observedDate)
                        }
                    }
                }
            }
            return
        }

        // Room has its own current-day/checkpoint policy. Run it before UI
        // display/ordering rejection so eligible early Luke fields are not lost.
        // This does not alter the feed or the Daily Flow acceptance decision.
        syncLiveRoom(feed, dateProvider(), clock())

        synchronized(stateLock) {
            if (!isUsableLukeSnapshot(feed)) {
                LiveDiagnostics.warn(
                    "LIVE request rejected seq=$sequence reason=unusable date=${feed.date} current_time=${feed.currentTime}",
                )
                return@synchronized
            }

            val previous = primary?.feed
            val incomingTime = parseDecisionInstant(feed)
            val previousTime = previous?.let(::parseDecisionInstant)
            val appliedSequence = latestAppliedSequence.get()
            val scheduleTime = clock()
            val today = dateProvider()
            val hasUnresolvedCandidate =
                hasUnresolvedFinalCandidate(previous, feed, today) ||
                    hasUnresolvedReferenceCandidate(feed, today, scheduleTime)

            // Provider timestamps remain authoritative; sequence is a tie-breaker only.
            // Requests intentionally overlap, so do not replace this with a single in-flight guard.
            if (
                !shouldAcceptLiveSnapshotByOrdering(
                    incomingTime = incomingTime,
                    previousTime = previousTime,
                    sequence = sequence,
                    latestAppliedSequence = appliedSequence,
                ) && !hasUnresolvedCandidate
            ) {
                LiveDiagnostics.warn(
                    "LIVE request rejected seq=$sequence reason=older-provider-snapshot " +
                        "incomingTime=$incomingTime previousTime=$previousTime latestAppliedSequence=$appliedSequence",
                )
                return@synchronized
            }

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
                previousLastFinal = lastFinal,
            )
            val protected = reduction.feed
            if (
                feed.live != protected.live ||
                feed.liveSet != protected.liveSet ||
                feed.liveVal != protected.liveVal
            ) {
                LiveDiagnostics.info(
                    "LIVE fields frozen seq=$sequence appTime=$scheduleTime " +
                        "session=${liveSessionWhoseLiveIsFrozenAt(scheduleTime)} " +
                        "incomingLive=${feed.live} retainedLive=${protected.live} " +
                        "incomingSet=${feed.liveSet} retainedSet=${protected.liveSet}",
                )
            }
            latestAppliedSequence.set(sequence)
            primary = SourceObservation(
                protected,
                finishedAt,
                startedAt,
                finishedAt - startedAt,
            )

            primaryLiveSession = reduction.primaryLiveSession
            lastFinal = reduction.lastFinal

            sideEffects.dispatch { cacheFeedSaver(protected) }
            publishLocked()
            val displayed = _state.value as? LiveUiState.Data
            LiveDiagnostics.info(
                "LIVE UI published seq=$sequence acceptedAtMs=${System.currentTimeMillis()} " +
                    "appDate=$today appTime=$scheduleTime hero=${displayed?.hero?.result} " +
                    "heroLive=${displayed?.heroLive} status=${displayed?.status}",
            )
            sideEffects.dispatch { syncLiveRoom(protected, today, scheduleTime) }
        }
        sideEffects.drain()
    }
    /**
     * Persist a Luke observation through the Room-specific acceptance path.
     * The Builder independently validates provider/app dates and each checkpoint;
     * this method blocks weekends and the LIVE-only Closed Day hold. It must not
     * mutate or relax UI/Daily Flow acceptance.
     */
    private fun syncLiveRoom(
        feed: LiveFeedData,
        today: LocalDate = dateProvider(),
        now: LocalTime = clock(),
    ) {
        val saver = liveRoomSaver ?: return
        if (
            today != dateProvider() ||
            !isWorkingDay(today) ||
            feed.isCloseDay ||
            closedHoldDate(today, now) != null
        ) {
            LiveDiagnostics.info(
                "Room sync skipped appDate=$today feedDate=${feed.date} " +
                    "closeDay=${feed.isCloseDay} appTime=$now",
            )
            return
        }

        val patches = buildLiveDailyResultPatches(feed, today, now)
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
                p.reference930SourceAt,
                p.reference200SourceAt,
                p.morningSourceAt,
                p.eveningSourceAt,
            ).joinToString("|")
        }

        synchronized(stateLock) {
            if (signature == lastRoomSyncSignature) return
            lastRoomSyncSignature = signature
        }

        // Patch contents are captured synchronously here. Only persistence
        // launch is queued, so callback delays cannot change the target date.
        sideEffects.dispatch {
            scope.launch {
                try {
                    saver(patches)
                } catch (cancelled: CancellationException) {
                    synchronized(stateLock) {
                        if (lastRoomSyncSignature == signature) {
                            lastRoomSyncSignature = null
                        }
                    }
                    throw cancelled
                } catch (_: Exception) {
                    synchronized(stateLock) {
                        if (lastRoomSyncSignature == signature) {
                            lastRoomSyncSignature = null
                        }
                    }
                }
            }
        }
    }

    private fun isUsableLukeSnapshot(feed: LiveFeedData): Boolean {
        val scheduleTime = clock()
        val today = dateProvider()

        val isCurrentCycleCandidate =
            hasCurrentCycleFinalCandidate(feed, today) ||
                hasCurrentCycleReferenceCandidate(feed, today, scheduleTime)
        if (!isLiveDisplayableFeed(feed, scheduleTime, today) && !isCurrentCycleCandidate) {
            LiveDiagnostics.warn(
                "LIVE snapshot rejected reason=not-displayable appDate=$today appTime=$scheduleTime " +
                    "feedDate=${feed.date} current_time=${feed.currentTime}",
            )
            return false
        }
        if (feed.currentTime.isBlank() || parseDecisionInstant(feed) == null) {
            LiveDiagnostics.warn(
                "LIVE snapshot rejected reason=invalid-provider-time appTime=$scheduleTime " +
                    "feedDate=${feed.date} current_time=${feed.currentTime}",
            )
            return false
        }

        // Display fallback and provider acceptance are deliberately different
        // contracts. From the 09:30 boundary onward, only today's Luke
        // snapshot may mutate primary/current-cycle state. A previous-day
        // snapshot may still be shown as the temporary held display before
        // 11:30, but it must never race with or overwrite today's cycle.
        if (
            isWorkingDay(today) &&
            !scheduleTime.isBefore(MORNING_REFERENCE) &&
            canonicalDate(feed.date) != today &&
            !isCurrentCycleCandidate
        ) {
            LiveDiagnostics.warn(
                "LIVE snapshot rejected reason=date-mismatch appDate=$today appTime=$scheduleTime " +
                    "feedDate=${feed.date} current_time=${feed.currentTime}",
            )
            return false
        }

        val hasLive = hasValidLive(feed, today)
        val hasMorningFinal = finalValid(feed.morning, feed, today)
        val hasEveningFinal = finalValid(feed.evening, feed, today)

        val usable = hasLive ||
            hasMorningFinal ||
            hasEveningFinal ||
            feed.modern930 != LIVE_PENDING ||
            feed.internet930 != LIVE_PENDING ||
            feed.modern200 != LIVE_PENDING ||
            feed.internet200 != LIVE_PENDING
        if (!usable) {
            LiveDiagnostics.warn(
                "LIVE snapshot rejected reason=no-live-final-reference-field appDate=$today appTime=$scheduleTime " +
                    "feedDate=${feed.date} current_time=${feed.currentTime}",
            )
        }
        return usable
    }

    private fun hasCurrentCycleFinalCandidate(
        feed: LiveFeedData,
        today: LocalDate,
    ): Boolean =
        isCurrentCycleObservation(feed, today) &&
            (isValidLive2d(feed.morning.result) || isValidLive2d(feed.evening.result))

    private fun hasCurrentCycleReferenceCandidate(
        feed: LiveFeedData,
        today: LocalDate,
        scheduleTime: LocalTime,
    ): Boolean {
        if (!isWorkingDay(today) || dailyCycleDate(today, scheduleTime) != today) return false
        if (!isCurrentCycleReferenceObservation(feed, today)) return false
        val morning = !scheduleTime.isBefore(MORNING_REFERENCE) &&
            isValidLive2d(feed.modern930) && isValidLive2d(feed.internet930)
        val afternoon = !scheduleTime.isBefore(AFTERNOON_REFERENCE) &&
            isValidLive2d(feed.modern200) && isValidLive2d(feed.internet200)
        return morning || afternoon
    }

    private fun hasUnresolvedFinalCandidate(
        previous: LiveFeedData?,
        incoming: LiveFeedData,
        today: LocalDate,
    ): Boolean {
        if (!hasCurrentCycleFinalCandidate(incoming, today)) return false
        val morningNew = isValidLive2d(incoming.morning.result) &&
            !finalValid(previous?.morning, previous, today)
        val eveningNew = isValidLive2d(incoming.evening.result) &&
            !finalValid(previous?.evening, previous, today)
        return morningNew || eveningNew
    }

    private fun hasUnresolvedReferenceCandidate(
        incoming: LiveFeedData,
        today: LocalDate,
        scheduleTime: LocalTime,
    ): Boolean {
        if (!hasCurrentCycleReferenceCandidate(incoming, today, scheduleTime)) return false
        val morningPending = !scheduleTime.isBefore(MORNING_REFERENCE) &&
            !isReferenceComplete(true, today)
        val afternoonPending = !scheduleTime.isBefore(AFTERNOON_REFERENCE) &&
            !isReferenceComplete(false, today)
        return morningPending || afternoonPending
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return applyReferenceFeed(isMorning, cycleDate, feed)
    }

    /**
     * Evaluate a response already obtained by the normal LIVE route or by a
     * fallback request. This keeps the first 09:30 response reusable instead
     * of immediately requesting the same Luke payload a second time.
     */
    private suspend fun applyReferenceFeed(
        isMorning: Boolean,
        cycleDate: LocalDate,
        feed: LiveFeedData?,
    ): Boolean {
        // Dedicated reference fetches also feed Room independently of whether
        // the reference reducer accepts them for the visible UI state.
        if (feed != null) syncLiveRoom(feed, dateProvider(), clock())

        // Closed Day is a calendar-day signal, not a reference-pair
        // acceptance decision. Luke may publish the current day's close-day
        // flag a few seconds before 09:30, so do not apply the reference
        // source-time boundary gate here.
        val closedSnapshot = isMorning &&
            feed != null &&
            feed.isCloseDay &&
            isCurrentCycleReferenceObservation(feed, cycleDate)

        // Keep the previous-working-day lookup outside the state lock. The
        // completed I/O is carried with the reference result event.
        val heldFeed = if (closedSnapshot) {
            val holdDate = previousWorkingDay(cycleDate)
            runSuspendCatchingCancellable {
                closedDayFeedFetcher?.invoke(holdDate)
            }
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
                // A confirmed Closed Day must be applied even if the 09:30
                // reference retry window has already ended (e.g. cold start
                // late in the afternoon). This is independent of reference
                // pair retry scheduling.
                if (
                    !isWorkingDay(dateProvider()) ||
                    dateProvider() != cycleDate ||
                    dailyCycleDate(dateProvider(), clock()) != cycleDate
                ) {
                    LiveDiagnostics.warn(
                        "Closed Day observation rejected due to stale cycle " +
                            "cycleDate=$cycleDate appDate=${dateProvider()} appTime=${clock()}",
                    )
                    return true
                }

                val transition = LiveStateReducer.reduceConfirmedClosedDay(
                    cycleDate = cycleDate,
                    heldFeed = heldFeed,
                    elapsedRealtimeMs = monotonicMs(),
                    previousPrimary = primary,
                    previousLastLive = lastLive,
                    previousLastFinal = lastFinal,
                    references = referenceStateLocked(),
                )

                closedDayDate = transition.closedDayDate
                sideEffects.dispatch { closedDayDateSaver(cycleDate) }
                applyReferenceStateLocked(transition.references)
                lastLive = transition.lastLive

                if (heldFeed != null) {
                    primary = transition.primary
                    lastFinal = transition.lastFinal
                    sideEffects.dispatch { cacheFeedSaver(heldFeed) }
                }

                publishLocked()
            }
            sideEffects.drain()
            return true
        }

        val cycleComplete = synchronized(stateLock) {
            val acceptedDate = dateProvider()
            val acceptedTime = clock()
            val transition = LiveReferenceStateReducer.reduce(
                state = referenceStateLocked(),
                event = event,
                retryWindowOpen = referenceRetryWindowOpen(
                    cycleDate,
                    acceptedDate,
                    acceptedTime,
                ),
                appTime = acceptedTime,
            )
            if (!transition.accepted) {
                LiveDiagnostics.warn(
                    "Reference response rejected reason=retry-window-or-closed-state " +
                        "session=${if (event.isMorning) "morning" else "afternoon"} " +
                        "cycleDate=$cycleDate appDate=$acceptedDate appTime=$acceptedTime " +
                        "feedDate=${feed?.date} current_time=${feed?.currentTime}",
                )
                return false
            }

            applyReferenceStateLocked(transition.state)

            publishLocked()
            val acceptedReferenceFeed =
                feed != null &&
                    transition.cycleComplete &&
                    transition.state.referenceFeed == feed &&
                    transition.state.referenceFeedDate == cycleDate
            val sessionName = if (event.isMorning) "morning" else "afternoon"
            val boundary = if (event.isMorning) MORNING_REFERENCE else AFTERNOON_REFERENCE
            if (transition.cycleComplete && feed != null) {
                LiveDiagnostics.info(
                    "Reference pair accepted session=$sessionName cycleDate=$cycleDate " +
                        "appDate=$acceptedDate appTime=$acceptedTime feedDate=${feed.date} " +
                        "current_time=${feed.currentTime} modern=${if (event.isMorning) feed.modern930 else feed.modern200} " +
                        "internet=${if (event.isMorning) feed.internet930 else feed.internet200}",
                )
            } else if (feed != null) {
                val modern = if (event.isMorning) feed.modern930 else feed.modern200
                val internet = if (event.isMorning) feed.internet930 else feed.internet200
                val reason = when {
                    acceptedTime.isBefore(boundary) -> "app-clock-before-boundary"
                    !isValidLive2d(modern) || !isValidLive2d(internet) -> "reference-pair-invalid"
                    !isCurrentCycleReferenceObservation(feed, cycleDate) -> "stale-source-cycle"
                    else -> "reference-policy-pending"
                }
                LiveDiagnostics.warn(
                    "Reference pair not accepted reason=$reason session=$sessionName " +
                        "cycleDate=$cycleDate appDate=$acceptedDate appTime=$acceptedTime " +
                        "feedDate=${feed.date} current_time=${feed.currentTime} " +
                        "modern=$modern internet=$internet",
                )
            }
            if (acceptedReferenceFeed && feed != null) {
                sideEffects.dispatch { syncLiveRoom(feed, acceptedDate, acceptedTime) }
            } else if (feed != null) {
                LiveDiagnostics.info(
                    "Reference response not synced to Room because pair was not accepted " +
                        "session=$sessionName cycleDate=$cycleDate feedDate=${feed.date} " +
                        "current_time=${feed.currentTime}",
                )
            }
            transition.cycleComplete
        }
        sideEffects.drain()

        return cycleComplete
    }
    private fun maybeReferenceFetch(t: LocalTime) {
        val today = dateProvider()
        clearExpiredClosedDayBeforeMorningBoundary(today, t)

        if (closedHoldDate(today, t) != null) return
        if (!isWorkingDay(today)) return

        val cycleDate = dailyCycleDate(today, t)

        val referenceRetryOpen = referenceRetryWindowOpen(cycleDate, today, t)

        if (referenceRetryOpen && t >= MORNING_REFERENCE && !isReferenceComplete(true, cycleDate)) {
            synchronized(stateLock) {
                applyReferenceStateLocked(
                    LiveReferenceStateReducer.beginWorkingDayCycle(
                        state = referenceStateLocked(),
                        cycleDate = cycleDate,
                    )
                )
                publishLocked()
            }
            sideEffects.drain()

            if (!isReferenceComplete(true, cycleDate)) {
                requestCoordinator.startMorningReferenceCycle()
            }
        }

        if (
            referenceRetryOpen &&
            t >= AFTERNOON_REFERENCE &&
            !isReferenceComplete(false, cycleDate)
        ) {
            requestCoordinator.startAfternoonReferenceCycle()
        }

        // If 09:30 has not succeeded by 11:30, force the session-card reset
        // exactly once. This only changes the display projection; the raw
        // provider snapshot and existing LIVE/final engine remain untouched.
        if (
            t >= MORNING_LIVE &&
            !isReferenceComplete(true, cycleDate) &&
            !isMorningResetMarked(cycleDate)
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
            sideEffects.drain()
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
            morningFinalized = f?.let { finalValid(it.morning, it, today) },
            eveningFinalized = f?.let { finalValid(it.evening, it, today) },
        )
    }

    private fun isExplicitClosedDayHistoryMarker(
        feed: LiveFeedData?,
        today: LocalDate,
        now: LocalTime,
    ): Boolean {
        val snapshot = feed ?: return false
        if (!snapshot.sourceTag.equals("HISTORY", ignoreCase = true)) return false
        if (canonicalDate(snapshot.date) != today) return false
        if (!isWorkingDay(today) || dailyCycleDate(today, now) != today) return false
        if (now.isBefore(MORNING_REFERENCE)) return false

        // The history source uses the literal "off" only for an explicit
        // non-trading day; empty/pending fields alone are not enough to infer closure.
        val bothSessionsOff =
            snapshot.morning.result.equals("off", ignoreCase = true) &&
                snapshot.evening.result.equals("off", ignoreCase = true)
        val referencesAbsent = listOf(
            snapshot.modern930,
            snapshot.internet930,
            snapshot.modern200,
            snapshot.internet200,
        ).all { it.isBlank() || it == LIVE_PENDING }
        return bothSessionsOff && referencesAbsent
    }

    private suspend fun recoverPreviousWorkingDayFinal() {
        val recoveryStartedElapsed = monotonicMs()
        val today = dateProvider()
        val now = clock()
        val bootstrap = freshInstallBootstrapPlan(today, now)
        val closedHold = closedHoldDate(today, now)
        val cycleDate = closedHold?.let(::previousWorkingDay) ?: bootstrap.cycleDate
        val fallbackFeedDate = closedHold?.let(::previousWorkingDay) ?: bootstrap.fallbackDate

        // First recover the exact cycle row represented by the app clock.
        // Fresh-install policy owns only the date selection; the Daily Flow
        // still decides what portions of that row are displayable.
        suspend fun fetchHistoricalFeed(
            targetDate: LocalDate,
            fetcher: (suspend (LocalDate) -> LiveFeedData?)?,
            retryOnMiss: Boolean,
        ): LiveFeedData? {
            val request = fetcher ?: return null
            // Retry only when recovering a held/previous-day cycle (the fresh
            // install 08:30 case, weekends, or a confirmed Closed Day). Normal
            // current-day phases keep their original single-attempt timing.
            val maxAttempts = if (retryOnMiss) LIVE_HISTORY_RECOVERY_ATTEMPTS else 1
            for (attempt in 0 until maxAttempts) {
                val candidate = runSuspendCatchingCancellable {
                    request(targetDate)
                }?.takeIf {
                    canonicalDate(it.date) == targetDate
                }
                if (candidate != null) return candidate
                if (attempt + 1 < maxAttempts) {
                    delay(LIVE_HISTORY_RECOVERY_RETRY_DELAY_MS)
                }
            }
            LiveDiagnostics.warn(
                "History recovery feed unavailable date=$targetDate attempts=$maxAttempts",
            )
            return null
        }

        // First recover the exact cycle row represented by the app clock.
        // Fresh-install policy owns only the date selection; Daily Flow still
        // decides which portions of that row are displayable.
        val currentFeed = if (closedHold != null) {
            fetchHistoricalFeed(cycleDate, closedDayFeedFetcher, retryOnMiss = cycleDate != today)
        } else {
            fetchHistoricalFeed(cycleDate, historicalFeedFetcher, retryOnMiss = cycleDate != today)
        }

        // Some holidays are explicitly represented in the history source as
        // result_1200="off" / result_430="off" while Luke's close-day flag may
        // be absent or encoded in a JSON type that older parsing did not accept.
        // Translate only that explicit current-day marker into the same Closed
        // Day transition used by Luke; do not treat ordinary pending data as closed.
        if (closedHold == null && isExplicitClosedDayHistoryMarker(currentFeed, today, now)) {
            if (!currentClosedDayForNotice(today)) {
                val holdDate = previousWorkingDay(today)
                val heldFeed = fetchHistoricalFeed(
                    holdDate,
                    closedDayFeedFetcher ?: historicalFeedFetcher,
                    retryOnMiss = true,
                )
                if (!currentClosedDayForNotice(today)) {
                    val closedObservation = currentFeed!!.copy(isCloseDay = true)
                    LiveDiagnostics.warn(
                        "Closed Day confirmed from explicit history off marker date=$today holdDate=$holdDate",
                    )
                    applyReferenceResult(
                        LiveReferenceResultEvent(
                            isMorning = true,
                            cycleDate = today,
                            feed = closedObservation,
                            closedDayObservation = true,
                            heldFeed = heldFeed,
                        )
                    )
                }
            }
            return
        }

        val fallbackFeed = if (currentFeed == null && fallbackFeedDate != null) {
            fetchHistoricalFeed(fallbackFeedDate, historicalFeedFetcher, retryOnMiss = false)
        } else {
            null
        }

        // Before today's morning session has finalized, the current-day history
        // row is not an eligible final source yet. Keep the previous working-day
        // hero as the fallback instead of probing a result that cannot exist.
        // Once the morning catch-up window has ended, current-cycle final recovery
        // is valid again. A held cycle (weekend / pre-09:30 / Luke Closed Day) is
        // always eligible because its cycleDate differs from today's app date.
        // A recovered feed already contains the session result fields. Derive
        // its final hero directly instead of issuing a second full-history HTTP
        // request before publishing the cold-start state. This is especially
        // important before 09:30: a fresh install must not remain visually blank
        // while a redundant final-only lookup is still loading or has failed.
        val currentFinal = if (
            cycleDate != today || !now.isBefore(MORNING_CATCHUP_END)
        ) {
            currentFeed?.let(::latestFinalFor)?.takeIf {
                canonicalDate(it.date) == cycleDate
            } ?: runSuspendCatchingCancellable {
                historicalFinalFetcher?.invoke(cycleDate)
            }?.takeIf {
                canonicalDate(it.date) == cycleDate
            }
        } else {
            null
        }

        val fallbackFinal = if (
            currentFinal == null &&
            fallbackFeedDate != null &&
            fallbackFeedDate != cycleDate
        ) {
            fallbackFeed?.let(::latestFinalFor)?.takeIf {
                canonicalDate(it.date) == fallbackFeedDate
            } ?: runSuspendCatchingCancellable {
                historicalFinalFetcher?.invoke(fallbackFeedDate)
            }?.takeIf {
                canonicalDate(it.date) == fallbackFeedDate
            }
        } else {
            null
        }

        val recoveredFeed = currentFeed ?: fallbackFeed

        synchronized(stateLock) {
            // Historical I/O can finish after a current-day Luke request when
            // the 09:30 flow runs independently. Preserve that newer primary
            // observation, while still using recovered history to seed lastFinal.
            val primaryLukeArrivedDuringRecovery = primary?.let { observation ->
                observation.feed.sourceTag.equals("LUKE", ignoreCase = true) &&
                    observation.fetchedAtElapsedMs > recoveryStartedElapsed
            } == true
            val currentDayMorningLukePrimary = primary?.feed?.let { existing ->
                isWorkingDay(today) &&
                    !now.isBefore(MORNING_REFERENCE) &&
                    now.isBefore(MORNING_LIVE) &&
                    canonicalDate(existing.date) == today &&
                    existing.sourceTag.equals("LUKE", ignoreCase = true)
            } == true
            val preserveNewerLukePrimary =
                primaryLukeArrivedDuringRecovery || currentDayMorningLukePrimary

            val transition = LiveStateReducer.reduceStartupRecovery(
                today = today,
                scheduleTime = now,
                cycleDate = cycleDate,
                recoveredFeed = recoveredFeed,
                currentFinal = currentFinal,
                fallbackFinal = fallbackFinal,
                previousPrimary = primary,
                previousLastFinal = lastFinal,
                references = referenceStateLocked(),
                elapsedRealtimeMs = monotonicMs(),
            )

            lastFinal = transition.lastFinal
            if (recoveredFeed != null && !preserveNewerLukePrimary) {
                primary = transition.primary
                applyReferenceStateLocked(transition.references)
            } else if (
                recoveredFeed != null &&
                preserveNewerLukePrimary &&
                cycleDate == today &&
                !now.isBefore(EVENING_CLOSE) &&
                canonicalDate(recoveredFeed.date) == cycleDate
            ) {
                // At/after the evening cutoff, History supplies completed
                // finals and reference pairs that Luke's LIVE endpoint can
                // still leave pending. Preserve the newer Luke timestamp/live
                // fields, but merge in completed historical fields rather than
                // dropping the history result along with the replaced primary.
                val current = primary
                val currentFeed = current?.feed
                if (
                    current != null &&
                    currentFeed != null &&
                    currentFeed.sourceTag.equals("LUKE", ignoreCase = true) &&
                    canonicalDate(currentFeed.date) == cycleDate
                ) {
                    primary = current.copy(
                        feed = mergeCompletedHistoryIntoLukeFeed(
                            lukeFeed = currentFeed,
                            historyFeed = recoveredFeed,
                        ),
                    )
                    applyReferenceStateLocked(transition.references)
                }
            }

            publishLocked()
        }
        sideEffects.drain()
    }

    /**
     * Fill missing completed-day result/reference fields from History without
     * replacing the fresher Luke observation or its LIVE timestamp.
     */
    private fun mergeCompletedHistoryIntoLukeFeed(
        lukeFeed: LiveFeedData,
        historyFeed: LiveFeedData,
    ): LiveFeedData {
        fun resolvedReferencePair(
            currentModern: String,
            currentInternet: String,
            historicalModern: String,
            historicalInternet: String,
        ): Pair<String, String>? = when {
            isValidLive2d(currentModern) && isValidLive2d(currentInternet) ->
                currentModern to currentInternet
            isValidLive2d(historicalModern) && isValidLive2d(historicalInternet) ->
                historicalModern to historicalInternet
            else -> null
        }

        val reference930 = resolvedReferencePair(
            lukeFeed.modern930,
            lukeFeed.internet930,
            historyFeed.modern930,
            historyFeed.internet930,
        )
        val reference200 = resolvedReferencePair(
            lukeFeed.modern200,
            lukeFeed.internet200,
            historyFeed.modern200,
            historyFeed.internet200,
        )

        return lukeFeed.copy(
            morning = if (
                isValidLive2d(lukeFeed.morning.result) ||
                !isValidLive2d(historyFeed.morning.result)
            ) lukeFeed.morning else historyFeed.morning,
            evening = if (
                isValidLive2d(lukeFeed.evening.result) ||
                !isValidLive2d(historyFeed.evening.result)
            ) lukeFeed.evening else historyFeed.evening,
            modern930 = reference930?.first ?: lukeFeed.modern930,
            internet930 = reference930?.second ?: lukeFeed.internet930,
            modern200 = reference200?.first ?: lukeFeed.modern200,
            internet200 = reference200?.second ?: lukeFeed.internet200,
        )
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
            sideEffects.dispatch { cacheSaver(hero) }
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

    private fun hasCompleteLateColdStartSnapshot(today: LocalDate): Boolean {
        val data = _state.value as? LiveUiState.Data ?: return false
        // Once Luke or an explicit History off marker confirms a Closed Day,
        // ordinary after-hours recovery must not issue more LIVE requests.
        if (data.closedDay || currentClosedDayForNotice(today)) return true

        val feed = data.feed ?: return false
        if (canonicalDate(feed.date) != today) return false

        // The retry exists only to recover an entirely blank late cold start.
        // Preserve the established schedule contract: once any current-day
        // final result or a complete reference pair is usable, do not resume
        // polling/retrying merely because another field is still pending.
        val anyFinalReady =
            isValidLive2d(feed.morning.result) || isValidLive2d(feed.evening.result)
        val morningReferenceReady =
            isValidLive2d(feed.modern930) && isValidLive2d(feed.internet930)
        val afternoonReferenceReady =
            isValidLive2d(feed.modern200) && isValidLive2d(feed.internet200)
        return anyFinalReady || morningReferenceReady || afternoonReferenceReady
    }

    /**
     * A late cold start normally does not poll after the final catch-up window.
     * If its first Luke snapshot and history recovery both provide no usable
     * current-day data, run a small bounded recovery sequence instead of leaving
     * the UI permanently blank. Every request still goes through normal acceptance.
     */
    private fun launchLateColdStartRecoveryRetries(startupDate: LocalDate) {
        scope.launch {
            repeat(LATE_COLD_START_RETRY_ATTEMPTS) { index ->
                delay(LATE_COLD_START_RETRY_DELAY_MS)

                var now = clock()
                if (
                    dateProvider() != startupDate ||
                    !isWorkingDay(startupDate) ||
                    now.isBefore(EVENING_CATCHUP_END) ||
                    closedHoldDate(startupDate, now) != null
                ) {
                    return@launch
                }

                // Never overlap a retry with the startup request still in flight.
                requestCoordinator.awaitLiveRequests()

                now = clock()
                if (
                    dateProvider() != startupDate ||
                    !isWorkingDay(startupDate) ||
                    now.isBefore(EVENING_CATCHUP_END) ||
                    closedHoldDate(startupDate, now) != null
                ) {
                    return@launch
                }
                if (hasCompleteLateColdStartSnapshot(startupDate)) return@launch

                LiveDiagnostics.warn(
                    "Late cold-start data incomplete; retrying Luke " +
                        "attempt=${index + 1}/$LATE_COLD_START_RETRY_ATTEMPTS date=$startupDate",
                )
                requestCoordinator.requestLiveNow()
                if (hasCompleteLateColdStartSnapshot(startupDate)) return@launch
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return

        schedulerJob = scope.launch {
            val startupToday = dateProvider()
            val startupTime = clock()
            val startupClosed = closedHoldDate(startupToday, startupTime)
            val startupLiveSession = liveSessionForTime(startupTime)

            if (startupLiveSession != null && startupClosed == null) {
                // Active LIVE/finalization cold-start remains latency-sensitive.
                fetchCycle()
            } else if (
                isWorkingDay(startupToday) &&
                startupTime.isBefore(MORNING_LIVE)
            ) {
                // On a working-day morning, let the scheduler and 09:30 request
                // run while historical recovery is in flight. History must not
                // hold the clock boundary hostage.
                val startupCycleDate = dailyCycleDate(startupToday, startupTime)
                val morningReferenceOwnsStartup =
                    startupClosed == null &&
                        !startupTime.isBefore(MORNING_REFERENCE) &&
                        !isReferenceComplete(true, startupCycleDate)

                if (startupClosed == null && morningReferenceOwnsStartup) {
                    // Start the normal-first 09:30 flow before any History I/O.
                    maybeReferenceFetch(startupTime)
                    launch { recoverPreviousWorkingDayFinal() }
                } else {
                    // Keep the historical recovery first when it completes quickly,
                    // as before, but do not await it before the scheduler can reach 09:30.
                    launch { recoverPreviousWorkingDayFinal() }
                    if (startupClosed == null) {
                        fetchCycle()
                    }
                }
            } else {
                // Do not put the first Luke request behind historical I/O after
                // the reference windows have closed (including a cold start at
                // 16:30+). A slow/unavailable History endpoint must not leave the
                // LIVE screen blank while the current provider can answer now.
                // Recovery already preserves a newer accepted Luke primary when
                // its history request completes later.
                launch { recoverPreviousWorkingDayFinal() }
                maybeReferenceFetch(startupTime)
                if (startupClosed == null) {
                    fetchCycle()
                }
            }

            if (
                startupClosed == null &&
                isWorkingDay(startupToday) &&
                !startupTime.isBefore(EVENING_CATCHUP_END)
            ) {
                launchLateColdStartRecoveryRetries(startupToday)
            }

            var lastSchedulerDate = startupToday

            while (isActive) {
                val currentDate = dateProvider()
                val t = clock()

                // Publish once at the calendar boundary so a current-day-only
                // Closed Day notice disappears as soon as the new date starts.
                if (currentDate != lastSchedulerDate) {
                    lastSchedulerDate = currentDate
                    synchronized(stateLock) {
                        publishLocked()
                    }
                    sideEffects.drain()
                }

                synchronized(stateLock) {
                    val phaseMarker = liveWindowAction(t) to liveSessionForTime(t)
                    if (phaseMarker != lastPhaseMarker) {
                        publishLocked()
                    }
                }
                sideEffects.drain()

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
                                    currentYangonDate(),
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
