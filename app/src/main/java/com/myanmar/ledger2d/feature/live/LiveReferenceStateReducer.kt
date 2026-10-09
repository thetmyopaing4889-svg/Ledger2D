package com.myanmar.ledger2d.feature.live

import java.time.LocalDate
import java.time.LocalTime

/**
 * The reference-related portion of canonical Daily Flow state.
 * All values are immutable; the collector applies a reduction while holding
 * its existing state lock, then performs publication and persistence callbacks.
 */
internal data class LiveReferenceState(
    val reference930: Pair<String, String>?,
    val reference930Date: LocalDate?,
    val reference200: Pair<String, String>?,
    val reference200Date: LocalDate?,
    val reference930PendingDate: LocalDate?,
    val reference200PendingDate: LocalDate?,
    val reference930CompleteDate: LocalDate?,
    val reference200CompleteDate: LocalDate?,
    val referenceResetDate: LocalDate?,
    val referenceFeed: LiveFeedData?,
    val referenceFeedDate: LocalDate?,
)

internal data class LiveReferenceStateReduction(
    val state: LiveReferenceState,
    val accepted: Boolean,
    val cycleComplete: Boolean,
)

/**
 * Pure transition logic for 09:30/14:00 reference state. This class never
 * writes cache/Room, publishes UI, fetches history, or performs network I/O.
 */
internal object LiveReferenceStateReducer {
    fun reduce(
        state: LiveReferenceState,
        event: LiveReferenceResultEvent,
        retryWindowOpen: Boolean,
    ): LiveReferenceStateReduction {
        val cycleDate = event.cycleDate
        val isMorning = event.isMorning

        if (event.closedDayObservation || !retryWindowOpen) {
            return LiveReferenceStateReduction(state, accepted = false, cycleComplete = false)
        }

        val feed = event.feed
        val modern = if (isMorning) feed?.modern930 else feed?.modern200
        val internet = if (isMorning) feed?.internet930 else feed?.internet200
        val valid = isValidReferencePair(feed, modern.orEmpty(), internet.orEmpty()) &&
            feed != null &&
            isCurrentCycleReferenceObservation(feed, cycleDate)

        val next = if (isMorning) {
            when {
                valid -> state.copy(
                    reference930 = modern!! to internet!!,
                    reference930Date = cycleDate,
                    reference930PendingDate = null,
                    reference930CompleteDate = cycleDate,
                    referenceResetDate = cycleDate,
                    referenceFeed = feed,
                    referenceFeedDate = cycleDate,
                )
                state.reference930CompleteDate != cycleDate -> state.copy(
                    reference930 = null,
                    reference930Date = null,
                    reference930PendingDate = cycleDate,
                )
                else -> state
            }
        } else {
            when {
                valid -> state.copy(
                    reference200 = modern!! to internet!!,
                    reference200Date = cycleDate,
                    reference200PendingDate = null,
                    reference200CompleteDate = cycleDate,
                    referenceFeed = feed,
                    referenceFeedDate = cycleDate,
                )
                state.reference200CompleteDate != cycleDate -> state.copy(
                    reference200 = null,
                    reference200Date = cycleDate,
                    reference200PendingDate = cycleDate,
                )
                else -> state
            }
        }

        val cycleComplete = if (isMorning) {
            next.reference930CompleteDate == cycleDate
        } else {
            next.reference200CompleteDate == cycleDate
        }

        return LiveReferenceStateReduction(next, accepted = true, cycleComplete = cycleComplete)
    }

    /** Begin a working-day cycle by masking only reference slots not yet completed. */
    fun beginWorkingDayCycle(
        state: LiveReferenceState,
        cycleDate: LocalDate,
    ): LiveReferenceState {
        var next = state
        if (
            next.reference930CompleteDate != cycleDate &&
            next.reference930PendingDate != cycleDate
        ) {
            next = next.copy(
                reference930 = null,
                reference930Date = null,
                reference930PendingDate = cycleDate,
            )
        }
        if (
            next.reference200CompleteDate != cycleDate &&
            next.reference200PendingDate != cycleDate
        ) {
            next = next.copy(
                reference200 = null,
                reference200Date = null,
                reference200PendingDate = cycleDate,
            )
        }
        return next
    }

    /** Promote current-cycle references from an otherwise accepted Luke snapshot. */
    fun promoteFromLiveSnapshot(
        state: LiveReferenceState,
        feed: LiveFeedData,
        today: LocalDate,
        scheduleTime: LocalTime,
    ): LiveReferenceState {
        if (!isWorkingDay(today)) return state
        val cycleDate = dailyCycleDate(today, scheduleTime)
        if (cycleDate != today || !isCurrentCycleReferenceObservation(feed, cycleDate)) return state

        var next = state
        if (
            !scheduleTime.isBefore(MORNING_REFERENCE) &&
            isValidReferencePair(feed, feed.modern930, feed.internet930)
        ) {
            next = next.copy(
                reference930 = feed.modern930 to feed.internet930,
                reference930Date = cycleDate,
                reference930PendingDate = null,
                reference930CompleteDate = cycleDate,
                referenceResetDate = cycleDate,
                referenceFeed = feed,
                referenceFeedDate = cycleDate,
            )
        }
        if (
            !scheduleTime.isBefore(AFTERNOON_REFERENCE) &&
            isValidReferencePair(feed, feed.modern200, feed.internet200)
        ) {
            next = next.copy(
                reference200 = feed.modern200 to feed.internet200,
                reference200Date = cycleDate,
                reference200PendingDate = null,
                reference200CompleteDate = cycleDate,
                referenceFeed = feed,
                referenceFeedDate = cycleDate,
            )
        }
        return next
    }

    /** Clear reference state when Luke has confirmed that the current day is closed. */
    fun clearForClosedDay(state: LiveReferenceState): LiveReferenceState = state.copy(
        reference930 = null,
        reference930Date = null,
        reference200 = null,
        reference200Date = null,
        reference930PendingDate = null,
        reference200PendingDate = null,
        reference930CompleteDate = null,
        reference200CompleteDate = null,
        referenceResetDate = null,
        referenceFeed = null,
        referenceFeedDate = null,
    )

    /** Restore reference values only for a completed historical held-day row. */
    fun restoreCompletedHeldCycle(
        state: LiveReferenceState,
        feed: LiveFeedData,
        cycleDate: LocalDate,
    ): LiveReferenceState {
        var next = state
        if (isValidReferencePair(feed, feed.modern930, feed.internet930)) {
            next = next.copy(
                reference930 = feed.modern930 to feed.internet930,
                reference930Date = cycleDate,
                reference930CompleteDate = cycleDate,
                reference930PendingDate = null,
            )
        }
        if (isValidReferencePair(feed, feed.modern200, feed.internet200)) {
            next = next.copy(
                reference200 = feed.modern200 to feed.internet200,
                reference200Date = cycleDate,
                reference200CompleteDate = cycleDate,
                reference200PendingDate = null,
            )
        }
        return next
    }

    fun markMorningResetIfNeeded(
        state: LiveReferenceState,
        cycleDate: LocalDate,
    ): LiveReferenceState =
        if (
            state.reference930CompleteDate != cycleDate &&
            state.referenceResetDate != cycleDate
        ) {
            state.copy(referenceResetDate = cycleDate)
        } else {
            state
        }

    private fun isValidReferencePair(
        feed: LiveFeedData?,
        modern: String,
        internet: String,
    ): Boolean =
        feed != null && isValidLive2d(modern) && isValidLive2d(internet)
}
