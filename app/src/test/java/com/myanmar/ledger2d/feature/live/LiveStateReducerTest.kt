package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LiveStateReducerTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun session(result: String = LIVE_PENDING) = LiveSessionData(
        result = result,
        set = if (isValidLive2d(result)) "1600" else LIVE_PENDING,
        value = if (isValidLive2d(result)) "20000" else LIVE_PENDING,
        finalized = isValidLive2d(result),
    )

    private fun feed(
        time: String,
        live: String = LIVE_PENDING,
        liveSet: String = LIVE_PENDING,
        liveValue: String = LIVE_PENDING,
        morning: LiveSessionData = session(),
        evening: LiveSessionData = session(),
    ) = LiveFeedData(
        date = today.toString(),
        currentTime = time,
        live = live,
        liveSet = liveSet,
        liveVal = liveValue,
        morning = morning,
        evening = evening,
        modern930 = LIVE_PENDING,
        internet930 = LIVE_PENDING,
        modern200 = LIVE_PENDING,
        internet200 = LIVE_PENDING,
    )

    @Test
    fun finalized_morning_result_is_not_replaced_by_pending_snapshot() {
        val previous = feed(
            time = "11:59:00",
            live = "38",
            liveSet = "1600",
            liveValue = "20000",
            morning = session("47"),
        )
        val incoming = feed(time = "12:00:00")

        val reduced = LiveStateReducer.reduceAcceptedLiveSnapshot(
            previous = previous,
            incoming = incoming,
            scheduleDate = today,
            scheduleTime = LocalTime.of(11, 59),
            previousPrimaryLiveSession = LiveSession.MORNING,
        )

        assertEquals("47", reduced.feed.morning.result)
        assertTrue(reduced.feed.morning.finalized)
    }

    @Test
    fun partial_response_preserves_last_valid_live_values_within_same_session() {
        val previous = feed(
            time = "11:40:00",
            live = "38",
            liveSet = "1600",
            liveValue = "20000",
        )
        val incoming = feed(time = "11:41:00")

        val reduced = LiveStateReducer.reduceAcceptedLiveSnapshot(
            previous = previous,
            incoming = incoming,
            scheduleDate = today,
            scheduleTime = LocalTime.of(11, 41),
            previousPrimaryLiveSession = LiveSession.MORNING,
        )

        assertEquals("38", reduced.feed.live)
        assertEquals("1600", reduced.feed.liveSet)
        assertEquals("20000", reduced.feed.liveVal)
        assertEquals(LiveSession.MORNING, reduced.primaryLiveSession)
    }

    @Test
    fun morning_live_value_does_not_leak_into_evening_session() {
        val previous = feed(
            time = "11:40:00",
            live = "38",
            liveSet = "1600",
            liveValue = "20000",
        )
        val incoming = feed(time = "16:05:00")

        val reduced = LiveStateReducer.reduceAcceptedLiveSnapshot(
            previous = previous,
            incoming = incoming,
            scheduleDate = today,
            scheduleTime = LocalTime.of(16, 5),
            previousPrimaryLiveSession = LiveSession.MORNING,
        )

        assertEquals(LIVE_PENDING, reduced.feed.live)
        assertNull(reduced.primaryLiveSession)
    }

    @Test
    fun source_time_infers_morning_and_evening_catchup_ownership() {
        assertEquals(
            LiveSession.MORNING,
            LiveStateReducer.inferLiveSessionFromSourceTime(feed(time = "12:15:00")),
        )
        assertEquals(
            LiveSession.EVENING,
            LiveStateReducer.inferLiveSessionFromSourceTime(feed(time = "16:45:00")),
        )
        assertNull(
            LiveStateReducer.inferLiveSessionFromSourceTime(feed(time = LIVE_PENDING)),
        )
    }
}
