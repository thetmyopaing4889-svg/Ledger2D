package com.myanmar.ledger2d.feature.live

import java.time.LocalDate

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
 * Pure transition logic for dedicated 09:30/14:00 reference completions.
 * Closed Day observations are deliberately handled by LiveCollector's
 * integration path because it must retrieve/save the held-day feed.
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
            return LiveReferenceStateReduction(
                state = state,
                accepted = false,
                cycleComplete = false,
            )
        }

        val feed = event.feed
        val modern = if (isMorning) feed?.modern930 else feed?.modern200
        val internet = if (isMorning) feed?.internet930 else feed?.internet200
        val valid = feed != null &&
            isCurrentCycleReferenceObservation(feed, cycleDate) &&
            isValidLive2d(modern.orEmpty()) &&
            isValidLive2d(internet.orEmpty())

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

        return LiveReferenceStateReduction(
            state = next,
            accepted = true,
            cycleComplete = cycleComplete,
        )
    }
}
