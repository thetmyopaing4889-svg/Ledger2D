package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns request execution and the two independent current-cycle reference retry
 * lifecycles. Network waits happen in child coroutines; each completion is
 * delivered as an immutable event for LiveCollector to accept under its state
 * lock. This coordinator never mutates Daily Flow state or writes persistence.
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
) {
    private val requestSequence = AtomicLong(0L)

    private var reference930Cycle: Job? = null
    private var reference930CycleDate: LocalDate? = null
    private var reference200Cycle: Job? = null
    private var reference200CycleDate: LocalDate? = null

    fun launchLiveRequest() {
        val sequence = requestSequence.incrementAndGet()

        scope.launch {
            val startedAt = monotonicClockMs()
            val feed = try {
                fetcher()
            } catch (_: Exception) {
                null
            }
            val finishedAt = monotonicClockMs()

            if (feed == null) return@launch

            onLiveResult(
                LiveRequestResultEvent(
                    sequence = sequence,
                    feed = feed,
                    requestStartedAtElapsedMs = startedAt,
                    requestFinishedAtElapsedMs = finishedAt,
                )
            )
        }
    }

    fun startMorningReferenceCycle() {
        val today = dateProvider()
        if (referenceComplete(true, today)) return
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
                if (!referenceRetryWindowOpen(cycleDate, nowDate, now)) return@launch
                if (fetchReferencePair(true, cycleDate)) return@launch
                delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
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
