package com.myanmar.ledger2d.feature.live

import android.content.Context
import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal fun monotonicMs(): Long = runCatching {
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
                previousLastFinal = lastFinal,
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
            lastFinal = reduction.lastFinal

            cacheFeedSaver(protected)
            publishLocked()
            syncLiveRoom(protected)
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
                closedDayDateSaver(cycleDate)
                applyReferenceStateLocked(transition.references)
                lastLive = transition.lastLive

                if (heldFeed != null) {
                    primary = transition.primary
                    lastFinal = transition.lastFinal
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
            // The reducer preserves the original priority: a current-cycle
            // final wins over fallback history; if the recovered row is a
            // completed held cycle, its valid reference pairs may be restored.
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
            if (recoveredFeed != null) {
                primary = transition.primary
                applyReferenceStateLocked(transition.references)
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

