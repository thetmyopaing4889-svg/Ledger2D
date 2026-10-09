package com.myanmar.ledger2d.feature.live

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveDisplayProjectorTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = monday.plusDays(1)

    private fun session(result: String, finalized: Boolean = true) = LiveSessionData(
        result = result,
        set = if (finalized) "1600" else LIVE_PENDING,
        value = if (finalized) "20000" else LIVE_PENDING,
        finalized = finalized,
    )

    private fun feed(
        date: LocalDate = monday,
        morning: LiveSessionData = session("77"),
        evening: LiveSessionData = session("35"),
        modern930: String = "98",
        internet930: String = "15",
        modern200: String = "40",
        internet200: String = "04",
    ) = LiveFeedData(
        date = date.toString(),
        currentTime = "16:00:00",
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = morning,
        evening = evening,
        modern930 = modern930,
        internet930 = internet930,
        modern200 = modern200,
        internet200 = internet200,
    )

    private fun snapshot(
        today: LocalDate = monday,
        scheduleTime: LocalTime = LocalTime.of(9, 45),
        cycleDate: LocalDate = today,
        referenceFeed: LiveFeedData? = null,
        referenceFeedDate: LocalDate? = null,
        closedHold: Boolean = false,
        reference930: Pair<String, String>? = null,
        reference930Date: LocalDate? = null,
        reference930PendingDate: LocalDate? = null,
        reference200: Pair<String, String>? = null,
        reference200Date: LocalDate? = null,
        reference200PendingDate: LocalDate? = null,
        referenceResetDate: LocalDate? = null,
    ) = LiveReferenceProjectionSnapshot(
        today = today,
        scheduleTime = scheduleTime,
        cycleDate = cycleDate,
        referenceFeed = referenceFeed,
        referenceFeedDate = referenceFeedDate,
        closedHold = closedHold,
        reference930 = reference930,
        reference930Date = reference930Date,
        reference930PendingDate = reference930PendingDate,
        reference200 = reference200,
        reference200Date = reference200Date,
        reference200PendingDate = reference200PendingDate,
        referenceResetDate = referenceResetDate,
    )

    @Test
    fun pending_feed_without_source_uses_today_and_only_pending_values() {
        val projected = projectPendingDisplayFeed(
            sourceFeed = null,
            scheduleTime = LocalTime.of(9, 30),
            today = monday,
        )

        assertNotNull(projected)
        assertEquals(monday.toString(), projected!!.date)
        assertEquals("09:30", projected.currentTime)
        assertEquals(LIVE_PENDING, projected.live)
        assertEquals(LIVE_PENDING, projected.liveSet)
        assertEquals(LIVE_PENDING, projected.liveVal)
        assertEquals(LIVE_PENDING, projected.morning.result)
        assertEquals(LIVE_PENDING, projected.evening.result)
        assertEquals(LIVE_PENDING, projected.modern930)
        assertEquals(LIVE_PENDING, projected.internet930)
        assertEquals(LIVE_PENDING, projected.modern200)
        assertEquals(LIVE_PENDING, projected.internet200)
        assertEquals("LUKE", projected.sourceTag)
    }

    @Test
    fun pending_feed_relabels_previous_day_source_without_mutating_it() {
        val previousDay = feed(date = monday.minusDays(3))
        val projected = projectPendingDisplayFeed(
            sourceFeed = previousDay,
            scheduleTime = LocalTime.of(11, 30),
            today = monday,
        )

        assertNotNull(projected)
        assertEquals(monday.toString(), projected!!.date)
        assertEquals("11:30", projected.currentTime)
        assertEquals(LIVE_PENDING, projected.live)
        assertEquals(LIVE_PENDING, projected.morning.result)
        assertEquals(LIVE_PENDING, projected.evening.result)
        assertEquals("98", projected.modern930)
        assertEquals(monday.minusDays(3).toString(), previousDay.date)
        assertEquals("77", previousDay.morning.result)
    }

    @Test
    fun pending_feed_is_not_projected_outside_windows_or_on_weekends() {
        assertEquals(
            null,
            projectPendingDisplayFeed(
                sourceFeed = null,
                scheduleTime = LocalTime.of(12, 45),
                today = monday,
            ),
        )
        assertEquals(
            null,
            projectPendingDisplayFeed(
                sourceFeed = null,
                scheduleTime = LocalTime.of(10, 0),
                today = monday.plusDays(5),
            ),
        )
    }

    @Test
    fun closed_day_display_eligibility_accepts_only_the_held_previous_working_day() {
        val heldFeed = feed(date = monday)
        val currentDayFeed = feed(date = tuesday)

        assertEquals(
            true,
            isLiveFeedDisplayableForSchedule(
                feed = heldFeed,
                scheduleTime = LocalTime.of(10, 0),
                today = tuesday,
                liveClosedDayDate = tuesday,
            ),
        )
        assertEquals(
            false,
            isLiveFeedDisplayableForSchedule(
                feed = currentDayFeed,
                scheduleTime = LocalTime.of(10, 0),
                today = tuesday,
                liveClosedDayDate = tuesday,
            ),
        )
    }

    @Test
    fun normal_display_eligibility_still_accepts_previous_working_day_before_morning_live() {
        val fridayFeed = feed(date = monday.minusDays(3))

        assertEquals(
            true,
            isLiveFeedDisplayableForSchedule(
                feed = fridayFeed,
                scheduleTime = LocalTime.of(10, 0),
                today = monday,
                liveClosedDayDate = null,
            ),
        )
        assertEquals(
            false,
            isLiveFeedDisplayableForSchedule(
                feed = fridayFeed,
                scheduleTime = LocalTime.of(11, 30),
                today = monday,
                liveClosedDayDate = null,
            ),
        )
    }

    @Test
    fun successful_reference_feed_projects_current_values_and_pending_session_cards() {
        val rawFeed = feed()
        val projected = projectReferenceFeed(
            base = null,
            snapshot = snapshot(
                referenceFeed = rawFeed,
                referenceFeedDate = monday,
                reference930 = "12" to "34",
                reference930Date = monday,
                referenceResetDate = monday,
            ),
        )

        assertNotNull(projected)
        assertEquals("12", projected!!.modern930)
        assertEquals("34", projected.internet930)
        assertEquals(LIVE_PENDING, projected.modern200)
        assertEquals(LIVE_PENDING, projected.morning.result)
        assertEquals(LIVE_PENDING, projected.evening.result)

        // Projection copies the feed; it never mutates the baseline snapshot.
        assertEquals("98", rawFeed.modern930)
        assertEquals("77", rawFeed.morning.result)
        assertEquals("35", rawFeed.evening.result)
    }

    @Test
    fun closed_day_hold_returns_the_held_feed_without_reference_overlay() {
        val heldFeed = feed(date = monday)
        val projected = projectReferenceFeed(
            base = heldFeed,
            snapshot = snapshot(
                today = tuesday,
                cycleDate = tuesday,
                scheduleTime = LocalTime.of(10, 0),
                closedHold = true,
                reference930 = "12" to "34",
                reference930Date = tuesday,
                referenceResetDate = tuesday,
            ),
        )

        assertEquals(heldFeed, projected)
    }

    @Test
    fun morning_live_boundary_projects_both_sessions_to_pending_until_finalization() {
        val currentFeed = feed()
        val projected = projectReferenceFeed(
            base = currentFeed,
            snapshot = snapshot(
                scheduleTime = LocalTime.of(11, 30),
                reference930PendingDate = monday,
                reference200 = "52" to "11",
                reference200Date = monday,
            ),
        )

        assertNotNull(projected)
        assertEquals(LIVE_PENDING, projected!!.morning.result)
        assertEquals(LIVE_PENDING, projected.evening.result)
    }

    @Test
    fun evening_live_window_preserves_morning_final_and_resets_only_evening_card() {
        val currentFeed = feed(
            morning = session("77"),
            evening = session("35"),
        )
        val projected = projectReferenceFeed(
            base = currentFeed,
            snapshot = snapshot(
                scheduleTime = LocalTime.of(16, 5),
                reference200 = "52" to "11",
                reference200Date = monday,
            ),
        )

        assertNotNull(projected)
        assertEquals("77", projected!!.morning.result)
        assertEquals(LIVE_PENDING, projected.evening.result)
    }
}
