package com.myanmar.ledger2d.feature.live

import java.time.LocalDate

/**
 * Completed result of one independent Luke reference request.
 *
 * The optional held feed is fetched for a confirmed Closed Day before the
 * event is applied. No network or history I/O belongs in the state-apply path.
 */
internal data class LiveReferenceResultEvent(
    val isMorning: Boolean,
    val cycleDate: LocalDate,
    val feed: LiveFeedData?,
    val closedDayObservation: Boolean,
    val heldFeed: LiveFeedData?,
)
