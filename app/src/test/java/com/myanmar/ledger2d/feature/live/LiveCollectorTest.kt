package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCollectorTest {
    private val yangon = ZoneId.of("Asia/Yangon")

    private fun feed(
        value: String,
        time: String,
        morning: LiveSessionData = LiveSessionData("--", "--", "--", false),
        evening: LiveSessionData = LiveSessionData("--", "--", "--", false),
        modern930: String = "98",
        internet930: String = "15",
        modern200: String = "40",
        internet200: String = "04",
    ) = LiveFeedData(
        date = currentYangonDate().toString(),
        currentTime = time,
        live = value,
        liveSet = "1600",
        liveVal = "20000",
        morning = morning,
        evening = evening,
        modern930 = modern930,
        internet930 = internet930,
        modern200 = modern200,
        internet200 = internet200,
        sourceTag = "LUKE",
        serverTimeEpochMs = currentYangonDate()
            .atTime(LocalTime.parse(time))
            .atZone(yangon)
            .toInstant()
            .toEpochMilli(),
    )

    private fun finalMorning(value: String) = LiveSessionData(
        result = value, set = "1600", value = "20000", finalized = true, providerOpenTime = "12:01:00"
    )

    private fun finalEvening(value: String) = LiveSessionData(
        result = value, set = "1600", value = "20000", finalized = true, providerOpenTime = "16:30:00"
    )

    @Test fun schedule_0930_is_reference() {
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(9, 30)))
    }

    @Test fun schedule_1129_is_reference() {
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(11, 29)))
    }

    @Test fun schedule_1130_starts_live() {
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(11, 30)))
    }

    @Test fun schedule_1200_is_still_live() {
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(12, 0)))
    }

    @Test fun schedule_1201_starts_final_catchup() {
        assertEquals(LiveWindowAction.FINALIZING, liveWindowAction(LocalTime.of(12, 1)))
    }

    @Test fun schedule_1400_is_reference() {
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(14, 0)))
    }

    @Test fun schedule_1600_starts_evening_live() {
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(16, 0)))
    }

    @Test fun schedule_1630_starts_evening_final_catchup() {
        assertEquals(LiveWindowAction.FINALIZING, liveWindowAction(LocalTime.of(16, 30)))
    }

    @Test fun previous_day_morning_response_shows_yesterdays_final_before_0930() {
        val yesterday = currentYangonDate().minusDays(1)
        val old = feed(
            "--",
            "09:22:11",
            evening = finalEvening("25"),
        ).copy(date = yesterday.toString(), serverTimeEpochMs = null)
        val oldObs = SourceObservation(old, 0L, 0L, 10L)

        val result = resolveLiveState(
            p = oldObs,
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(9, 22),
        )

        assertFalse(result.heroLive)
        assertEquals("25", result.hero?.result)
        assertEquals(yesterday.toString(), result.hero?.date)
        assertEquals(yesterday.toString(), result.displayFeed?.date)
    }

    @Test fun previous_day_feed_is_kept_until_0930() {
        val yesterday = currentYangonDate().minusDays(1)
        val old = feed(
            "38",
            "17:00:00",
            evening = finalEvening("77"),
        ).copy(date = yesterday.toString())
        val oldObs = SourceObservation(old, 0L, 0L, 10L)

        val result = resolveLiveState(
            p = oldObs,
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(7, 46),
        )

        assertFalse(result.heroLive)
        assertEquals("77", result.hero?.result)
        assertEquals(yesterday.toString(), result.displayFeed?.date)
    }

    @Test fun previous_day_feed_remains_visible_after_0930_until_live_boundary() {
        val yesterday = currentYangonDate().minusDays(1)
        val old = feed(
            "38",
            "17:00:00",
            evening = finalEvening("77"),
        ).copy(date = yesterday.toString())
        val oldObs = SourceObservation(old, 0L, 0L, 10L)

        val result = resolveLiveState(
            p = oldObs,
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(9, 34),
        )

        assertFalse(result.heroLive)
        assertEquals("77", result.hero?.result)
        assertEquals(yesterday.toString(), result.hero?.date)
        assertEquals(yesterday.toString(), result.displayFeed?.date)
    }

    @Test fun yesterday_live_and_today_reference_can_coexist() {
        val yesterday = currentYangonDate().minusDays(1)
        val old = feed(
            "38",
            "17:00:00",
            evening = finalEvening("77"),
            modern930 = "80",
            internet930 = "33",
        ).copy(date = yesterday.toString())

        val result = resolveLiveState(
            p = SourceObservation(old, 0L, 0L, 10L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(9, 34),
        )

        assertFalse(result.heroLive)
        assertEquals("77", result.hero?.result)
        assertEquals("80", result.displayFeed?.modern930)
        assertEquals("33", result.displayFeed?.internet930)
        assertEquals(yesterday.toString(), result.displayFeed?.date)
    }

    @Test fun morning_final_is_shown_immediately_after_final_response() {
        val f = feed("36", "12:03:00", morning = finalMorning("36"))
        val result = resolveLiveState(
            SourceObservation(f, 100L, 0L, 100L),
            null,
            java.time.Instant.now(),
            null,
            null,
            LocalTime.of(12, 3),
        )

        assertEquals("36", result.hero?.result)
        assertFalse(result.heroLive)
        assertEquals("36", result.displayFeed?.morning?.result)
    }

    @Test fun evening_final_is_shown_immediately_after_final_response() {
        val f = feed("77", "17:00:00", evening = finalEvening("77"))
        val result = resolveLiveState(
            SourceObservation(f, 100L, 0L, 100L),
            null,
            java.time.Instant.now(),
            null,
            null,
            LocalTime.of(17, 0),
        )

        assertEquals("77", result.hero?.result)
        assertFalse(result.heroLive)
    }

    @Test fun request_failure_keeps_last_successful_snapshot() = runTest {
        var calls = 0
        val collector = LiveCollector(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            fetcher = {
                calls++
                if (calls == 1) feed("38", "11:40:00") else null
            },
            clock = { LocalTime.of(11, 40) },
        )

        collector.fetchCycle()
        advanceUntilIdle()
        collector.fetchCycle()
        advanceUntilIdle()

        assertEquals("38", (collector.state.value as LiveUiState.Data).hero?.result)
    }

    @Test fun slow_request_does_not_block_next_scheduled_request() = runTest {
        var calls = 0
        val firstGate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                if (calls == 1) firstGate.await()
                feed(if (calls == 1) "38" else "39", "11:40:00")
            },
            clock = { LocalTime.of(11, 40) },
        )

        collector.start()
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS + 100L)

        assertTrue(calls >= 2)
        firstGate.complete(Unit)
        runCurrent()
        scope.cancel()
    }

    @Test fun late_older_response_cannot_overwrite_newer_response() = runTest {
        val first = CompletableDeferred<LiveFeedData?>()
        val second = CompletableDeferred<LiveFeedData?>()
        var calls = 0

        val collector = LiveCollector(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            fetcher = {
                calls++
                if (calls == 1) first.await() else second.await()
            },
            clock = { LocalTime.of(11, 40) },
        )

        collector.fetchCycle()
        collector.fetchCycle()

        second.complete(feed("39", "11:40:02"))
        advanceUntilIdle()
        assertEquals("39", (collector.state.value as LiveUiState.Data).hero?.result)

        first.complete(feed("38", "11:40:01"))
        advanceUntilIdle()
        assertEquals("39", (collector.state.value as LiveUiState.Data).hero?.result)
    }

    @Test fun final_result_cannot_regress_to_pending() = runTest {
        var calls = 0
        val collector = LiveCollector(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            fetcher = {
                calls++
                if (calls == 1) feed("36", "12:03:00", morning = finalMorning("36"))
                else feed("37", "12:04:00")
            },
            clock = { LocalTime.of(12, 4) },
        )

        collector.fetchCycle()
        advanceUntilIdle()
        collector.fetchCycle()
        advanceUntilIdle()

        val state = collector.state.value as LiveUiState.Data
        assertEquals("36", state.feed?.morning?.result)
        assertEquals("36", state.hero?.result)
    }

    @Test fun background_polling_works_without_opening_live_screen() = runTest {
        var calls = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                feed("38", "11:40:00")
            },
            clock = { LocalTime.of(11, 40) },
        )

        collector.start()
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 3)
        assertTrue(calls >= 4)
        scope.cancel()
    }
}
