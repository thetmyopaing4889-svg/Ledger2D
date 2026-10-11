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
    fun older_same_cycle_reference_cannot_regress_newer_accepted_feed() {
        val yangon = ZoneId.of("Asia/Yangon")
        val newerTime = LocalTime.of(9, 35)
        val olderTime = LocalTime.of(9, 34)
        val newerEpoch = cycleDate.atTime(newerTime).atZone(yangon).toInstant().toEpochMilli()
        val olderEpoch = cycleDate.atTime(olderTime).atZone(yangon).toInstant().toEpochMilli()
        val newerFeed = feed(
            time = newerTime.toString(),
            modern930 = "80",
            internet930 = "33",
            epoch = newerEpoch,
        )
        val olderFeed = feed(
            time = olderTime.toString(),
            modern930 = "12",
            internet930 = "34",
            epoch = olderEpoch,
        )
        val initial = state(
            reference930 = "80" to "33",
            reference930Date = cycleDate,
            reference930PendingDate = null,
            reference930CompleteDate = cycleDate,
            referenceResetDate = cycleDate,
            referenceFeed = newerFeed,
            referenceFeedDate = cycleDate,
        )

        val result = LiveReferenceStateReducer.reduce(
            state = initial,
            event = LiveReferenceResultEvent(true, cycleDate, olderFeed, false, null),
            retryWindowOpen = true,
        )

        assertTrue(result.accepted)
        assertTrue(result.cycleComplete)
        assertEquals(initial, result.state)
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

    @Test
    fun closed_day_clears_all_reference_cycle_memory() {
        val populated = state(
            reference930 = "12" to "34",
            reference930Date = cycleDate,
            reference200 = "56" to "78",
            reference200Date = cycleDate,
            reference930PendingDate = cycleDate,
            reference200PendingDate = cycleDate,
            reference930CompleteDate = cycleDate,
            reference200CompleteDate = cycleDate,
            referenceResetDate = cycleDate,
            referenceFeed = feed(modern930 = "12", internet930 = "34"),
            referenceFeedDate = cycleDate,
        )

        val cleared = LiveReferenceStateReducer.clearForClosedDay(populated)

        assertEquals(null, cleared.reference930)
        assertEquals(null, cleared.reference930Date)
        assertEquals(null, cleared.reference200)
        assertEquals(null, cleared.reference200Date)
        assertEquals(null, cleared.reference930PendingDate)
        assertEquals(null, cleared.reference200PendingDate)
        assertEquals(null, cleared.reference930CompleteDate)
        assertEquals(null, cleared.reference200CompleteDate)
        assertEquals(null, cleared.referenceResetDate)
        assertEquals(null, cleared.referenceFeed)
        assertEquals(null, cleared.referenceFeedDate)
    }

    @Test
    fun new_cycle_keeps_completed_reference_and_masks_uncompleted_slot() {
        val yesterday = cycleDate.minusDays(1)
        val initial = state(
            reference930 = "12" to "34",
            reference930Date = cycleDate,
            reference930PendingDate = null,
            reference930CompleteDate = cycleDate,
            reference200 = "56" to "78",
            reference200Date = yesterday,
            reference200PendingDate = yesterday,
            reference200CompleteDate = yesterday,
        )

        val next = LiveReferenceStateReducer.beginWorkingDayCycle(initial, cycleDate)

        assertEquals("12" to "34", next.reference930)
        assertEquals(cycleDate, next.reference930CompleteDate)
        assertEquals(null, next.reference200)
        assertEquals(null, next.reference200Date)
        assertEquals(cycleDate, next.reference200PendingDate)
        assertEquals(yesterday, next.reference200CompleteDate)
    }

    @Test
    fun normal_luke_snapshot_promotes_both_references_at_their_boundaries() {
        val acceptedFeed = feed(
            time = "14:01:00",
            modern930 = "12",
            internet930 = "34",
            modern200 = "56",
            internet200 = "78",
        )
        val next = LiveReferenceStateReducer.promoteFromLiveSnapshot(
            state = state(),
            feed = acceptedFeed,
            today = cycleDate,
            scheduleTime = LocalTime.of(14, 1),
        )

        assertEquals("12" to "34", next.reference930)
        assertEquals(cycleDate, next.reference930CompleteDate)
        assertEquals("56" to "78", next.reference200)
        assertEquals(cycleDate, next.reference200CompleteDate)
        assertEquals(acceptedFeed, next.referenceFeed)
    }

    @Test
    fun normal_luke_snapshot_before_reference_boundary_does_not_promote_reference() {
        val initial = state()
        val acceptedFeed = feed(
            time = "09:29:00",
            modern930 = "12",
            internet930 = "34",
            modern200 = "56",
            internet200 = "78",
        )
        val next = LiveReferenceStateReducer.promoteFromLiveSnapshot(
            state = initial,
            feed = acceptedFeed,
            today = cycleDate,
            scheduleTime = LocalTime.of(9, 29),
        )

        assertEquals(initial, next)
    }

    @Test
    fun completed_held_history_restores_only_valid_reference_pairs() {
        val heldFeed = feed(
            date = cycleDate.minusDays(1),
            time = "16:30:00",
            modern930 = "12",
            internet930 = "34",
            modern200 = LIVE_PENDING,
            internet200 = "78",
        )
        val next = LiveReferenceStateReducer.restoreCompletedHeldCycle(
            state = state(),
            feed = heldFeed,
            cycleDate = cycleDate.minusDays(1),
        )

        assertEquals("12" to "34", next.reference930)
        assertEquals(cycleDate.minusDays(1), next.reference930CompleteDate)
        assertEquals(null, next.reference200)
        assertEquals(null, next.reference200CompleteDate)
    }

    @Test
    fun morning_card_reset_is_marked_only_once_until_cycle_changes() {
        val first = LiveReferenceStateReducer.markMorningResetIfNeeded(state(), cycleDate)
        val second = LiveReferenceStateReducer.markMorningResetIfNeeded(first, cycleDate)
        val completed = LiveReferenceStateReducer.markMorningResetIfNeeded(
            state(reference930CompleteDate = cycleDate),
            cycleDate,
        )

        assertEquals(cycleDate, first.referenceResetDate)
        assertEquals(first, second)
        assertEquals(null, completed.referenceResetDate)
    }
}
