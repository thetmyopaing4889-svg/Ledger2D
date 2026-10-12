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

    private val morningRetrySignal = Channel<Unit>(Channel.CONFLATED)
    @Volatile
    private var morningReferenceWaitingForRetry = false

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

    private suspend fun fetchAndPublishLiveRequest(sequence: Long): LiveFeedData? {
        val startedAt = monotonicClockMs()
        val feed = try {
            fetcher()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val finishedAt = monotonicClockMs()

        if (feed != null) {
            onLiveResult(
                LiveRequestResultEvent(
                    sequence = sequence,
                    feed = feed,
                    requestStartedAtElapsedMs = startedAt,
                    requestFinishedAtElapsedMs = finishedAt,
                )
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
        if (referenceComplete(true, today)) return

        if (reference930Cycle?.isActive == true) {
            if (reference930CycleDate == today) {
                if (forceNow && morningReferenceWaitingForRetry) {
                    morningRetrySignal.trySend(Unit)
                }
                return
            }
            reference930Cycle?.cancel()
            reference930Cycle = null
        }

        reference930CycleDate = today
        reference930Cycle = scope.launch {
            while (morningRetrySignal.tryReceive().isSuccess) {
                // Discard a wake-up left over from a previous cycle.
            }

            val cycleDate = today

            // A request started just before 09:30 may still return a valid pair.
            // Let those normal requests finish and reuse their responses before
            // starting another request for the same reference cycle.
            val existingFeeds = awaitExistingLiveRequests()
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

            // Before 11:30 there is no normal LIVE polling window, so make the
            // initial Daily Flow request here. Once normal LIVE polling is active,
            // its request stream is already the primary path; only the fallback
            // retry lane is started when reference data is still missing.
            if (clock().isBefore(MORNING_LIVE)) {
                val initialFeed = fetchAndPublishLiveRequest(requestSequence.incrementAndGet())
                if (!isActive) return@launch
                if (!referenceRetryWindowOpen(cycleDate, dateProvider(), clock())) {
                    return@launch
                }

                // Normal acceptance may already have promoted a valid pair.
                // If not, evaluate this same response under the reference-specific
                // rules before making another HTTP request.
                if (
                    initialFeed != null &&
                    !referenceComplete(true, cycleDate) &&
                    acceptReferenceFeed(true, cycleDate, initialFeed)
                ) {
                    return@launch
                }
            }

            while (isActive) {
                val now = clock()
                val nowDate = dateProvider()
                if (!referenceRetryWindowOpen(cycleDate, nowDate, now)) return@launch
                if (referenceComplete(true, cycleDate)) return@launch

                // First fallback follows immediately after the failed primary
                // attempt. The interval applies only between fallback attempts.
                if (fetchReferencePair(true, cycleDate)) return@launch
                if (referenceComplete(true, cycleDate)) return@launch

                morningReferenceWaitingForRetry = true
                try {
                    withTimeoutOrNull(LIVE_REFERENCE_FETCH_INTERVAL_MS) {
                        morningRetrySignal.receive()
                    }
                } finally {
                    morningReferenceWaitingForRetry = false
                }
            }
        }
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
        if (referenceComplete(false, today)) return
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
                if (!referenceRetryWindowOpen(cycleDate, nowDate, now)) return@launch
                if (nowDate == cycleDate && now.isBefore(AFTERNOON_REFERENCE)) return@launch
                if (fetchReferencePair(false, cycleDate)) return@launch
                delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
            }
        }
    }
}
