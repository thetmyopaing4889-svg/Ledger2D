package com.myanmar.ledger2d.feature.live

import android.content.Context
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SET UI-only coordinator. It does not start until the LIVE screen is opened,
 * and suspends while LIVE is not visible. Never writes Luke/Room/history.
 * Outside 11:59–12:04 it sleeps until the next business-day window.
 */
internal object SetMorningUiCoordinator {
    private const val POLL_INTERVAL_MS = 3_000L
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val liveVisible = MutableStateFlow(false)
    private val mutableState = MutableStateFlow<SetMorningUiState?>(null)
    val state: StateFlow<SetMorningUiState?> = mutableState
    private lateinit var applicationContext: Context

    fun enterLiveScreen(context: Context) {
        applicationContext = context.applicationContext
        if (started.compareAndSet(false, true)) {
            scope.launch { morningLoop() }
            scope.launch {
                // Existing Luke in-memory data only: never create another Luke request.
                LiveCollector.instance.state.collect { state ->
                    if (state is LiveUiState.Data) {
                        val feed = state.feed
                        val today = currentYangonDate()
                        if (finalValid(feed?.morning, feed, today)) {
                            SetMorningAuditStore.recordLukeFinal(
                                applicationContext, today, feed!!.morning.result,
                            )
                        }
                    }
                }
            }
        }
        liveVisible.value = true
    }

    fun leaveLiveScreen() {
        liveVisible.value = false
    }

    private suspend fun morningLoop() {
        var activeDate: LocalDate? = null
        var selector: SetMorningSnapshotSelector? = null
        var stoppedForDate: LocalDate? = null

        while (currentCoroutineContext().isActive) {
            // No busy loop at all after navigating away from LIVE.
            liveVisible.first { it }

            val now = ZonedDateTime.now(YANGON)
            val date = now.toLocalDate()
            val time = now.toLocalTime()
            if (activeDate != date) {
                activeDate = date
                selector = SetMorningSnapshotSelector(date)
                val saved = SetMorningAuditStore.restoreUiResult(applicationContext, date)
                stoppedForDate = if (saved != null) date else null
                mutableState.value = saved
            }

            val start = LocalTime.of(11, 59)
            val end = LocalTime.of(12, 4)
            val inWindow = !time.isBefore(start) && time.isBefore(end)
            if (!isWorkingDay(date) || !inWindow) {
                // A single suspension until the next window; no daytime 3s timer.
                val nextDate = if (time.isBefore(start) && isWorkingDay(date)) date
                    else generateSequence(date.plusDays(1)) { it.plusDays(1) }
                        .first(::isWorkingDay)
                val nextStart = nextDate.atTime(start).atZone(YANGON)
                val sleepMs = Duration.between(now, nextStart).toMillis().coerceAtLeast(300L)
                delay(sleepMs)
                continue
            }

            if (stoppedForDate == date) {
                // Already sealed or explicitly rejected by SET; no requests today.
                val nextStart = date.plusDays(1).atTime(start).atZone(YANGON)
                delay(Duration.between(now, nextStart).toMillis().coerceAtLeast(3_000L))
                continue
            }
            if (!LiveAppVisibility.isVisible) {
                delay(1_000L)
                continue
            }
            if (!liveVisible.value) continue

            val requestStarted = System.nanoTime()
            when (val reply = SetSnapshotFetcher.probeOnce()) {
                is SetProbeOutcome.Success -> {
                    val selection = selector!!.observe(reply.observation.snapshot)
                    SetMorningAuditStore.recordObservation(
                        applicationContext, date, reply.observation, selection,
                    )
                    mutableState.value = if (selection.snapshot != null &&
                        selection.phase != SetMorningSelectionPhase.CONFLICT
                    ) {
                        SetMorningUiState(date, selection, reply.observation.fetchedAt)
                    } else null
                    if (selection.phase == SetMorningSelectionPhase.UI_RESULT_READY ||
                        selection.phase == SetMorningSelectionPhase.CONFLICT ||
                        (reply.observation.snapshot.sourceUpdateYangon.toLocalDate() == date &&
                            reply.observation.snapshot.sourceUpdateYangon.toLocalTime()
                                .isAfter(LocalTime.of(12, 1)))
                    ) stoppedForDate = date
                }
                is SetProbeOutcome.Failure -> {
                    if (reply.httpStatus in listOf(401, 403, 429)) {
                        // Do not retry blocked or rate-limited website requests.
                        stoppedForDate = date
                        mutableState.value = null
                    } else if (shouldDiscardStaleSetLive(
                            mutableState.value, java.time.Instant.now()
                        )) {
                        mutableState.value = null
                    }
                }
            }
            val elapsedMs = (System.nanoTime() - requestStarted) / 1_000_000L
            delay((POLL_INTERVAL_MS - elapsedMs).coerceAtLeast(500L))
        }
    }
}
