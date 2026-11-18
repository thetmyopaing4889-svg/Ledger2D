package com.myanmar.ledger2d.feature.live

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Separate, bounded SET display-only morning reader. Starts when the existing
 * app UI process starts (never from a WorkManager worker).
 *
 * Luke's collector, final writes, history, Room and auto-save remain untouched.
 * The 3-second cadence has no overlapping SET requests, runs only briefly on
 * weekday mornings while an Activity is visible, and stops after the cutoff.
 */
internal object SetMorningUiCoordinator {
    private const val SET_POLL_INTERVAL_MS = 3_000L
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<SetMorningUiState?>(null)
    val state: StateFlow<SetMorningUiState?> = mutableState

    fun startOnce() {
        if (started.compareAndSet(false, true)) {
            scope.launch { morningLoop() }
        }
    }

    private suspend fun morningLoop() {
        var activeDate: LocalDate? = null
        var selector: SetMorningSnapshotSelector? = null
        var stoppedForDate: LocalDate? = null

        while (scope.isActive) {
            val now = ZonedDateTime.now(YANGON)
            val date = now.toLocalDate()
            val time = now.toLocalTime()
            if (activeDate != date) {
                activeDate = date
                selector = SetMorningSnapshotSelector(date)
                stoppedForDate = null
                mutableState.value = null
            }

            val inWindow = !time.isBefore(LocalTime.of(11, 59)) &&
                time.isBefore(LocalTime.of(12, 4))
            if (!isWorkingDay(date) || !inWindow) {
                // No SET traffic all day outside the short requested period.
                delay(if (time.isBefore(LocalTime.of(11, 55))) 60_000L else 3_000L)
                continue
            }
            if (!LiveAppVisibility.isVisible || stoppedForDate == date) {
                delay(3_000L)
                continue
            }

            val startedAt = System.nanoTime()
            val reply = SetSnapshotFetcher.probeOnce()
            when (reply) {
                is SetProbeOutcome.Success -> {
                    val selection = selector!!.observe(reply.observation.snapshot)
                    val projected = if (selection.snapshot != null &&
                        selection.phase != SetMorningSelectionPhase.CONFLICT
                    ) {
                        SetMorningUiState(date, selection, reply.observation.fetchedAt)
                    } else null
                    mutableState.value = projected
                    if (selection.phase == SetMorningSelectionPhase.UI_RESULT_READY ||
                        selection.phase == SetMorningSelectionPhase.CONFLICT ||
                        (reply.observation.snapshot.sourceUpdateYangon.toLocalDate() == date &&
                            reply.observation.snapshot.sourceUpdateYangon.toLocalTime()
                                .isAfter(LocalTime.of(12, 1)))
                    ) {
                        // A crossed source timestamp seals the chosen cutoff snapshot.
                        stoppedForDate = date
                    }
                }
                is SetProbeOutcome.Failure -> {
                    if (reply.httpStatus in listOf(401, 403, 429)) {
                        // Obey access rejection/rate limits: no further requests today.
                        stoppedForDate = date
                        mutableState.value = null
                    }
                    // Other failures cannot make any snapshot final; source-time
                    // freshness at presentation time automatically falls back to Luke.
                }
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            delay((SET_POLL_INTERVAL_MS - elapsedMs).coerceAtLeast(500L))
        }
    }
}
