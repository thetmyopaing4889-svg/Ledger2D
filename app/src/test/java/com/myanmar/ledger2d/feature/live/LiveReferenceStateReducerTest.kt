package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LiveReferenceStateReducerTest {
    private val cycleDate = LocalDate.of(2026, 10, 5)

    private fun feed(
        date: LocalDate = cycleDate,
        time: String = "09:30:00",
        modern930: String = LIVE_PENDING,
        internet930: String = LIVE_PENDING,
        modern200: String = LIVE_PENDING,
        internet200: String = LIVE_PENDING,
        epoch: Long? = null,
    ) = LiveFeedData(
        date = date.toString(),
        currentTime = time,
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false),
        evening = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false),
        modern930 = modern930,
        internet930 = internet930,
        modern200 = modern200,
        internet200 = internet200,
        serverTimeEpochMs = epoch,
    )

    private fun state(
        reference930: Pair<String, String>? = null,
        reference930Date: LocalDate? = null,
        reference200: Pair<String, String>? = null,
        reference200Date: LocalDate? = null,
        reference930PendingDate: LocalDate? = cycleDate,
        reference200PendingDate: LocalDate? = cycleDate,
        reference930CompleteDate: LocalDate? = null,
        reference200CompleteDate: LocalDate? = null,
        referenceResetDate: LocalDate? = null,
        referenceFeed: LiveFeedData? = null,
        referenceFeedDate: LocalDate? = null,
    ) = LiveReferenceState(
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

    @Test
    fun valid_morning_reference_completes_cycle_and_requests_card_reset() {
        val referenceFeed = feed(modern930 = "12", internet930 = "34")
        val result = LiveReferenceStateReducer.reduce(
            state = state(),
            event = LiveReferenceResultEvent(true, cycleDate, referenceFeed, false, null),
            retryWindowOpen = true,
        )

        assertTrue(result.accepted)
        assertTrue(result.cycleComplete)
        assertEquals("12" to "34", result.state.reference930)
        assertEquals(cycleDate, result.state.reference930Date)
        assertEquals(null, result.state.reference930PendingDate)
        assertEquals(cycleDate, result.state.reference930CompleteDate)
        assertEquals(cycleDate, result.state.referenceResetDate)
        assertEquals(referenceFeed, result.state.referenceFeed)
        assertEquals(cycleDate, result.state.referenceFeedDate)
    }

    @Test
    fun failed_retry_does_not_regress_reference_already_accepted_this_cycle() {
        val acceptedFeed = feed(modern930 = "12", internet930 = "34")
        val initial = state(
            reference930 = "12" to "34",
            reference930Date = cycleDate,
            reference930PendingDate = null,
            reference930CompleteDate = cycleDate,
            referenceResetDate = cycleDate,
            referenceFeed = acceptedFeed,
            referenceFeedDate = cycleDate,
        )
        val result = LiveReferenceStateReducer.reduce(
            state = initial,
            event = LiveReferenceResultEvent(true, cycleDate, null, false, null),
            retryWindowOpen = true,
        )

        assertTrue(result.accepted)
        assertTrue(result.cycleComplete)
        assertEquals(initial, result.state)
    }

    @Test
    fun stale_provider_reference_does_not_complete_current_cycle() {
        val yesterday = cycleDate.minusDays(1)
        val staleEpoch = yesterday.atTime(9, 30)
            .atZone(ZoneId.of("Asia/Yangon"))
            .toInstant()
            .toEpochMilli()
        val staleFeed = feed(
            modern930 = "12",
            internet930 = "34",
            epoch = staleEpoch,
        )
        val result = LiveReferenceStateReducer.reduce(
            state = state(),
            event = LiveReferenceResultEvent(true, cycleDate, staleFeed, false, null),
            retryWindowOpen = true,
        )

        assertTrue(result.accepted)
        assertFalse(result.cycleComplete)
        assertEquals(null, result.state.reference930)
        assertEquals(null, result.state.reference930CompleteDate)
        assertEquals(cycleDate, result.state.reference930PendingDate)
    }

    @Test
    fun closed_retry_window_leaves_reference_state_unchanged() {
        val initial = state(reference930 = "12" to "34", reference930CompleteDate = cycleDate)
        val result = LiveReferenceStateReducer.reduce(
            state = initial,
            event = LiveReferenceResultEvent(true, cycleDate, null, false, null),
            retryWindowOpen = false,
        )

        assertFalse(result.accepted)
        assertFalse(result.cycleComplete)
        assertEquals(initial, result.state)
    }

    @Test
    fun failed_afternoon_reference_keeps_the_existing_pending_date_semantics() {
        val result = LiveReferenceStateReducer.reduce(
            state = state(),
            event = LiveReferenceResultEvent(false, cycleDate, null, false, null),
            retryWindowOpen = true,
        )

        assertTrue(result.accepted)
        assertFalse(result.cycleComplete)
        assertEquals(null, result.state.reference200)
        assertEquals(cycleDate, result.state.reference200Date)
        assertEquals(cycleDate, result.state.reference200PendingDate)
    }
}
