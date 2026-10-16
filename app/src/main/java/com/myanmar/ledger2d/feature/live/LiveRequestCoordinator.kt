package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns ordinary LIVE requests and current-cycle reference retry lifecycles.
 *
 * The morning reference cycle gives the normal Daily Flow request first chance
 * to supply the 09:30 pair. It waits for ordinary requests already in flight,
 * then performs one normal request while the 09:30-to-11:30 reference window is
 * active. The same response is offered to reference validation if normal LIVE
 * acceptance did not already complete the pair. Only then can dedicated
 * fallback requests begin.
 *
 * Requests outside the morning reference flow intentionally remain concurrent:
 * the existing LIVE pipeline relies on late/out-of-order snapshot acceptance.
 * This coordinator does not mutate Daily Flow state or write persistence.
 */
internal class LiveRequestCoordinator(
    private val scope: CoroutineScope,
    private val fetcher: suspend () -> LiveFeedData?,
    private val monotonicClockMs: () -> Long,
    private val clock: () -> LocalTime,
    private val dateProvider: () -> LocalDate,
    private val referenceRetryWindowOpen: (
        cycleDate: LocalDate,
        currentDate: LocalDate,
        now: LocalTime,
    ) -> Boolean,
    private val referenceComplete: (isMorning: Boolean, cycleDate: LocalDate) -> Boolean,
    private val fetchReferencePair: suspend (isMorning: Boolean, cycleDate: LocalDate) -> Boolean,
    private val onLiveResult: (LiveRequestResultEvent) -> Unit,
    private val acceptReferenceFeed: suspend (
        isMorning: Boolean,
        cycleDate: LocalDate,
        feed: LiveFeedData,
    ) -> Boolean = { _, _, _ -> false },
) {
    private val requestSequence = AtomicLong(0L)

    private val liveRequestLock = Any()
    private val liveRequestJobs = mutableSetOf<Deferred<LiveFeedData?>>()
    private val referenceCycleLock = Any()

    private val morningRetrySignal = Channel<Unit>(Channel.CONFLATED)
    @Volatile
    private var morningReferenceWaitingForRetryDate: LocalDate? = null

    private var reference930Cycle: Job? = null
    private var reference930CycleDate: LocalDate? = null
    private var reference200Cycle: Job? = null
    private var reference200CycleDate: LocalDate? = null

    fun launchLiveRequest() {
        val sequence = requestSequence.incrementAndGet()
        val request = scope.async(start = CoroutineStart.LAZY) {
            fetchAndPublishLiveRequest(sequence)
        }
        synchronized(liveRequestLock) {
            liveRequestJobs += request
        }
        request.invokeOnCompletion {
            synchronized(liveRequestLock) {
                liveRequestJobs -= request
            }
        }
        request.start()
    }

    /** Wait for requests already in flight before an explicit cold-start retry. */
    suspend fun awaitLiveRequests() {
        awaitExistingLiveRequests()
    }

    /** Make one ordinary LIVE request through the same acceptance pipeline. */
    suspend fun requestLiveNow(): LiveFeedData? =
        fetchAndPublishLiveRequest(requestSequence.incrementAndGet())

    private suspend fun fetchAndPublishLiveRequest(sequence: Long): LiveFeedData? {
        val startedAt = monotonicClockMs()
        val startedAtWallMs = System.currentTimeMillis()
        val feed = try {
            fetcher()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LiveDiagnostics.warn(
                "Luke fetcher threw seq=$sequence type=${error.javaClass.simpleName} startedAtMs=$startedAtWallMs",
                error,
            )
            null
        }
        val finishedAt = monotonicClockMs()
        val finishedAtWallMs = System.currentTimeMillis()
        val latencyMs = (finishedAt - startedAt).coerceAtLeast(0L)

        if (feed != null) {
            LiveDiagnostics.info(
                "LIVE request seq=$sequence startedAtMs=$startedAtWallMs finishedAtMs=$finishedAtWallMs " +
                    "latencyMs=$latencyMs date=${feed.date} current_time=${feed.currentTime} " +
                    "live=${feed.live} result_1200=${feed.morning.result} result_430=${feed.evening.result}",
            )
            onLiveResult(
                LiveRequestResultEvent(
                    sequence = sequence,
                    feed = feed,
                    requestStartedAtElapsedMs = startedAt,
                    requestFinishedAtElapsedMs = finishedAt,
                )
            )
        } else {
            LiveDiagnostics.warn(
                "LIVE request returned null seq=$sequence startedAtMs=$startedAtWallMs " +
                    "finishedAtMs=$finishedAtWallMs latencyMs=$latencyMs",
            )
        }
        return feed
    }


    /**
     * Start one coordinated morning cycle. Repeated scheduler/UI calls join the
     * existing cycle rather than starting a parallel 09:30 reference request.
     * A UI retry can wake a retry that is currently waiting for its next interval.
     */
    fun startMorningReferenceCycle(forceNow: Boolean = false) {
        val today = dateProvider()
        var jobToStart: Job? = null
        var shouldWakeCurrentCycle = false

        // Scheduler ticks and LiveScreen entry can happen on different threads.
        // Reserve the 09:30 cycle under one lock so they cannot launch two
        // primary requests for the same date at the same time.
        synchronized(referenceCycleLock) {
            if (referenceComplete(true, today)) return@synchronized

            val active = reference930Cycle
            if (active != null && !active.isCompleted && reference930CycleDate == today) {
                shouldWakeCurrentCycle =
                    forceNow && morningReferenceWaitingForRetryDate == today
            } else {
                active?.cancel()
                while (morningRetrySignal.tryReceive().isSuccess) {
                    // Remove a wake-up left over from an earlier cycle.
                }
                morningReferenceWaitingForRetryDate = null
                reference930CycleDate = today

                val created = scope.launch(start = CoroutineStart.LAZY) {
                    val cycleDate = today

                    // The normal-first sequencing applies only to the 09:30
                    // reference window. After 11:30, preserve the existing
                    // independent catch-up behavior for reference slots.
                    val normalFirstWindow = clock().isBefore(MORNING_LIVE)
                    val existingFeeds = if (normalFirstWindow) {
                        // Reuse any normal request that began just before 09:30;
                        // do not issue a competing request while it is in flight.
                        awaitExistingLiveRequests()
                    } else {
                        emptyList()
                    }
                    if (!isActive) return@launch
                    if (!referenceRetryWindowOpen(cycleDate, dateProvider(), clock())) return@launch
                    if (referenceComplete(true, cycleDate)) return@launch

                    for (feed in existingFeeds) {
                        if (!referenceRetryWindowOpen(cycleDate, dateProvider(), clock())) return@launch
                        if (referenceComplete(true, cycleDate)) return@launch
                        if (
                            feed != null &&
                            acceptReferenceFeed(true, cycleDate, feed)
                        ) {
                            return@launch
                        }
                    }
                    if (referenceComplete(true, cycleDate)) return@launch

                    // Before 11:30 there is no normal LIVE polling stream yet.
                    // Make one normal Daily Flow request first, pass that same
                    // response through normal acceptance and reference validation,
                    // and only use the dedicated path if the pair is still absent.
                    if (normalFirstWindow) {
                        val primaryFeed = fetchAndPublishLiveRequest(requestSequence.incrementAndGet())
                        if (!isActive) return@launch
                        if (!referenceRetryWindowOpen(cycleDate, dateProvider(), clock())) {
                            return@launch
                        }
                        if (referenceComplete(true, cycleDate)) return@launch
                        if (
                            primaryFeed != null &&
                            acceptReferenceFeed(true, cycleDate, primaryFeed)
                        ) {
                            return@launch
                        }
                    }

                    while (isActive) {
                        val now = clock()
                        val nowDate = dateProvider()
                        if (!referenceRetryWindowOpen(cycleDate, nowDate, now)) return@launch
                        if (referenceComplete(true, cycleDate)) return@launch

                        // First fallback follows immediately after primary failure;
                        // the retry interval applies only between fallback attempts.
                        if (fetchReferencePair(true, cycleDate)) return@launch
                        if (referenceComplete(true, cycleDate)) return@launch

                        morningReferenceWaitingForRetryDate = cycleDate
                        try {
                            withTimeoutOrNull(LIVE_REFERENCE_FETCH_INTERVAL_MS) {
                                morningRetrySignal.receive()
                            }
                        } finally {
                            if (morningReferenceWaitingForRetryDate == cycleDate) {
                                morningReferenceWaitingForRetryDate = null
                            }
                        }
                    }
                }
                reference930Cycle = created
                jobToStart = created
            }
        }

        if (shouldWakeCurrentCycle) {
            morningRetrySignal.trySend(Unit)
        }
        jobToStart?.start()
    }

    private suspend fun awaitExistingLiveRequests(): List<LiveFeedData?> {
        val observed = mutableSetOf<Deferred<LiveFeedData?>>()
        val feeds = mutableListOf<LiveFeedData?>()
        while (true) {
            val pending = synchronized(liveRequestLock) {
                liveRequestJobs.filter { it.isActive && it !in observed }
            }
            if (pending.isEmpty()) return feeds

            for (request in pending) {
                observed += request
                try {
                    feeds += request.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    feeds += null
                }
            }
        }
    }

    fun startAfternoonReferenceCycle() {
        val today = dateProvider()
        var jobToStart: Job? = null

        synchronized(referenceCycleLock) {
            if (referenceComplete(false, today)) return@synchronized

            val active = reference200Cycle
            if (active != null && !active.isCompleted && reference200CycleDate == today) {
                return@synchronized
            }

            active?.cancel()
            reference200CycleDate = today
            val created = scope.launch(start = CoroutineStart.LAZY) {
                val cycleDate = today
                while (isActive) {
                    val now = clock()
                    val nowDate = dateProvider()
                    if (!referenceRetryWindowOpen(cycleDate, nowDate, now)) return@launch
                    if (nowDate == cycleDate && now.isBefore(AFTERNOON_REFERENCE)) return@launch
                    if (fetchReferencePair(false, cycleDate)) return@launch
                    delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
                }
            }
            reference200Cycle = created
            jobToStart = created
        }
        jobToStart?.start()
    }
}
