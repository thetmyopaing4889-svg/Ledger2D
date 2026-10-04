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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCollectorTest {
    private val yangon = ZoneId.of("Asia/Yangon")
    private val friday = java.time.LocalDate.of(2026, 10, 2)
    private val thursday = friday.minusDays(1)
    private val saturday = friday.plusDays(1)
    private val sunday = friday.plusDays(2)
    private val monday = friday.plusDays(3)

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
        date = friday.toString(),
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
        serverTimeEpochMs = friday
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


    @Test fun daily_cycle_date_switches_at_0930_and_skips_weekend() {
        assertEquals(thursday, dailyCycleDate(friday, LocalTime.of(9, 29, 59)))
        assertEquals(friday, dailyCycleDate(friday, LocalTime.of(23, 59)))
        assertEquals(friday, dailyCycleDate(saturday, LocalTime.of(11, 23)))
        assertEquals(friday, dailyCycleDate(sunday, LocalTime.of(15, 0)))
        assertEquals(friday, dailyCycleDate(monday, LocalTime.of(9, 29, 59)))
        assertEquals(monday, dailyCycleDate(monday, LocalTime.of(9, 30)))
    }

    @Test fun app_clock_controls_phase_even_when_luke_reports_previous_day_time() {
        val yesterday = thursday
        val old = feed(
            "--",
            "17:00:00",
            evening = finalEvening("25"),
        ).copy(date = yesterday.toString(), serverTimeEpochMs = null)

        val result = resolveLiveState(
            p = SourceObservation(old, 0L, 0L, 10L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(11, 40),
            scheduleDate = friday,
        )

        assertFalse(result.heroLive)
        assertEquals("25", result.hero?.result)
        assertEquals(yesterday.toString(), result.hero?.date)
    }

    @Test fun current_day_live_replaces_previous_day_fallback_without_rewriting_live_engine() {
        val yesterday = thursday
        val previous = feed(
            "--",
            "17:00:00",
            evening = finalEvening("25"),
        ).copy(date = yesterday.toString(), serverTimeEpochMs = null)

        val todayLive = feed(
            "36",
            "11:40:00",
        )

        val result = resolveLiveState(
            p = SourceObservation(todayLive, 0L, 0L, 10L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = LiveHeroSnapshot(
                result = previous.evening.result,
                set = previous.evening.set,
                value = previous.evening.value,
                sessionLabel = LIVE_SESSION_EVENING_LABEL,
                date = previous.date,
            ),
            scheduleTime = LocalTime.of(11, 40),
            scheduleDate = friday,
        )

        assertTrue(result.heroLive)
        assertEquals("36", result.hero?.result)
        assertEquals(todayLive.date, result.hero?.date)
    }

    @Test fun successful_0930_reference_starts_new_cycle_and_resets_cards() = runTest {
        val yesterday = thursday
        val old = feed(
            "--",
            "17:00:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        ).copy(date = yesterday.toString(), serverTimeEpochMs = null)

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = { old },
            clock = { LocalTime.of(9, 34) },
            dateProvider = { friday },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)

        scope.cancel()
    }

    @Test fun failed_0930_reference_keeps_old_cards_until_1130_then_resets() = runTest {
        var now = LocalTime.of(9, 34)
        val yesterday = thursday
        val old = feed(
            "--",
            "17:00:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "--",
            internet930 = "--",
            modern200 = "98",
            internet200 = "78",
        ).copy(date = yesterday.toString(), serverTimeEpochMs = null)

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = { old },
            clock = { now },
            dateProvider = { friday },
        )

        collector.start()
        runCurrent()

        var state = collector.state.value as LiveUiState.Data
        assertEquals("22", state.feed?.morning?.result)
        assertEquals("25", state.feed?.evening?.result)
        assertEquals("--", state.feed?.modern930)
        assertEquals("--", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)

        now = LocalTime.of(11, 30)
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS)
        runCurrent()

        state = collector.state.value as LiveUiState.Data
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun late_0930_success_after_1130_fills_reference_without_resetting_cards_again() = runTest {
        var now = LocalTime.of(9, 34)
        var referenceReady = false
        val yesterday = thursday
        val old = feed(
            "--",
            "17:00:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "--",
            internet930 = "--",
            modern200 = "98",
            internet200 = "78",
        ).copy(date = yesterday.toString(), serverTimeEpochMs = null)

        val late = old.copy(modern930 = "80", internet930 = "33")

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = { if (referenceReady) late else old },
            clock = { now },
            dateProvider = { friday },
        )

        collector.start()
        runCurrent()

        now = LocalTime.of(11, 30)
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS)
        runCurrent()

        referenceReady = true
        advanceTimeBy(LIVE_REFERENCE_FETCH_INTERVAL_MS)
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)

        scope.cancel()
    }

    @Test fun weekend_holds_friday_state_without_entering_live_phase() {
        val fridayFeed = feed(
            "--",
            "16:30:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        val result = resolveLiveState(
            p = SourceObservation(fridayFeed, 0L, 0L, 10L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(11, 23),
            scheduleDate = saturday,
        )

        assertFalse(result.heroLive)
        assertEquals("25", result.hero?.result)
        assertEquals(friday.toString(), result.displayFeed?.date)
        assertEquals("22", result.displayFeed?.morning?.result)
        assertEquals("25", result.displayFeed?.evening?.result)
        assertEquals("80", result.displayFeed?.modern930)
        assertEquals("33", result.displayFeed?.internet930)
        assertEquals("98", result.displayFeed?.modern200)
        assertEquals("78", result.displayFeed?.internet200)
    }

    @Test fun sunday_also_holds_friday_state() {
        val fridayFeed = feed(
            "--",
            "16:30:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
        )

        val result = resolveLiveState(
            p = SourceObservation(fridayFeed, 0L, 0L, 10L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(16, 0),
            scheduleDate = sunday,
        )

        assertFalse(result.heroLive)
        assertEquals("25", result.hero?.result)
        assertEquals(friday.toString(), result.displayFeed?.date)
    }

    @Test fun monday_before_0930_still_holds_friday_state() {
        val fridayFeed = feed(
            "--",
            "16:30:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
        )

        val result = resolveLiveState(
            p = SourceObservation(fridayFeed, 0L, 0L, 10L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(8, 30),
            scheduleDate = monday,
        )

        assertFalse(result.heroLive)
        assertEquals("25", result.hero?.result)
        assertEquals(friday.toString(), result.displayFeed?.date)
    }

    @Test fun weekend_collector_does_not_poll_live_or_references() = runTest {
        var calls = 0
        val fridayFeed = feed(
            "--",
            "16:30:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                fridayFeed
            },
            clock = { LocalTime.of(11, 40) },
            dateProvider = { saturday },
        )

        collector.start()
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 3)
        runCurrent()

        assertEquals(1, calls)
        val state = collector.state.value as LiveUiState.Data
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)
        assertEquals("22", state.feed?.morning?.result)
        assertEquals("25", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)

        scope.cancel()
    }

    @Test fun friday_after_evening_final_stops_reference_retries() = runTest {
        var calls = 0
        val fridayFeed = feed(
            "--",
            "17:00:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "--",
            internet930 = "--",
            modern200 = "--",
            internet200 = "--",
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                fridayFeed
            },
            clock = { LocalTime.of(17, 0) },
            dateProvider = { friday },
        )

        collector.start()
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 3)
        runCurrent()

        // Only the app-start fetch is allowed; Friday's post-final period
        // is a hold state, so no new reference cycle begins.
        assertEquals(1, calls)
        val state = collector.state.value as LiveUiState.Data
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)
        assertEquals("--", state.feed?.modern930)
        assertEquals("--", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)

        scope.cancel()
    }

    @Test fun monday_0930_starts_new_working_day_cycle() = runTest {
        val fridayFeed = feed(
            "--",
            "16:30:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "--",
            internet930 = "--",
            modern200 = "98",
            internet200 = "78",
        )
        val mondayFeed = fridayFeed.copy(
            modern930 = "80",
            internet930 = "33",
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = { mondayFeed },
            clock = { LocalTime.of(9, 34) },
            dateProvider = { monday },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)

        scope.cancel()
    }

    @Test fun eleven_thirty_current_day_live_keeps_live_engine_data_but_projection_resets_cards() = runTest {
        val todayFeed = feed(
            "36",
            "11:40:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "--",
            internet930 = "--",
            modern200 = "--",
            internet200 = "--",
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = { todayFeed },
            clock = { LocalTime.of(11, 40) },
            dateProvider = { friday },
        )

        collector.fetchCycle()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertTrue(state.heroLive)
        assertEquals("36", state.hero?.result)
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)

        scope.cancel()
    }

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
        val yesterday = thursday
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
            scheduleDate = friday,
        )

        assertFalse(result.heroLive)
        assertEquals("25", result.hero?.result)
        assertEquals(yesterday.toString(), result.hero?.date)
        assertEquals(yesterday.toString(), result.displayFeed?.date)
    }

    @Test fun previous_day_feed_is_kept_until_0930() {
        val yesterday = thursday
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
            scheduleDate = friday,
        )

        assertFalse(result.heroLive)
        assertEquals("77", result.hero?.result)
        assertEquals(yesterday.toString(), result.displayFeed?.date)
    }

    @Test fun previous_day_feed_remains_visible_after_0930_until_live_boundary() {
        val yesterday = thursday
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
            scheduleDate = friday,
        )

        assertFalse(result.heroLive)
        assertEquals("77", result.hero?.result)
        assertEquals(yesterday.toString(), result.hero?.date)
        assertEquals(yesterday.toString(), result.displayFeed?.date)
    }

    @Test fun yesterday_live_and_today_reference_can_coexist() {
        val yesterday = thursday
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
            scheduleDate = friday,
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
            friday,
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
            friday,
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
            dateProvider = { friday },
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
            dateProvider = { friday },
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
            dateProvider = { friday },
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
            dateProvider = { friday },
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
            dateProvider = { friday },
        )

        collector.start()
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 3)
        assertTrue(calls >= 4)
        scope.cancel()
    }

    @Test fun live_room_patches_split_previous_result_date_from_current_cycle_references() {
        val f = feed(
            "--",
            "09:34:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        ).copy(date = thursday.toString(), serverTimeEpochMs = 1_000L)

        val patches = buildLiveDailyResultPatches(
            feed = f,
            today = friday,
            now = LocalTime.of(9, 34),
        )

        val yesterday = patches.single { it.date == thursday }
        val todayPatch = patches.single { it.date == friday }

        assertEquals("22", yesterday.morning2d)
        assertEquals("25", yesterday.evening2d)
        assertNull(yesterday.modern930)
        assertEquals("80", todayPatch.modern930)
        assertEquals("33", todayPatch.internet930)
        assertNull(todayPatch.modern200)
        assertNull(todayPatch.internet200)
        assertNull(todayPatch.morning2d)
        assertNull(todayPatch.evening2d)
    }

    @Test fun live_room_does_not_persist_current_day_references_before_0930() {
        val f = feed(
            "--",
            "09:22:11",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        ).copy(date = thursday.toString(), serverTimeEpochMs = 900L)

        val patches = buildLiveDailyResultPatches(
            feed = f,
            today = friday,
            now = LocalTime.of(9, 29, 59),
        )

        val yesterday = patches.single { it.date == thursday }
        assertEquals("22", yesterday.morning2d)
        assertNull(patches.firstOrNull { it.date == friday })
    }

    @Test fun live_room_full_evening_snapshot_is_one_current_day_patch() {
        val f = feed(
            "25",
            "18:00:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        ).copy(date = friday.toString(), serverTimeEpochMs = 10_000L)

        val patches = buildLiveDailyResultPatches(
            feed = f,
            today = friday,
            now = LocalTime.of(18, 0),
        )

        assertEquals(1, patches.size)
        val p = patches.single()
        assertEquals(friday, p.date)
        assertEquals("80", p.modern930)
        assertEquals("33", p.internet930)
        assertEquals("98", p.modern200)
        assertEquals("78", p.internet200)
        assertEquals("22", p.morning2d)
        assertEquals("25", p.evening2d)
    }

    @Test fun live_room_pending_patch_never_erases_existing_valid_values() {
        val old = com.myanmar.ledger2d.core.database.LiveDailyResultEntity(
            id = 7L,
            date = friday,
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
            morning2d = "22",
            morningSet = "1,000",
            morningValue = "2,000",
            evening2d = "25",
            eveningSet = "3,000",
            eveningValue = "4,000",
            reference930SourceAt = 100L,
            reference200SourceAt = 200L,
            morningSourceAt = 300L,
            eveningSourceAt = 400L,
            updatedAt = 500L,
        )

        val pending = com.myanmar.ledger2d.core.database.LiveDailyResultPatch(
            date = friday,
        )

        val merged = com.myanmar.ledger2d.core.repository.LiveDailyResultMerger.merge(
            old = old,
            patch = pending,
            updatedAt = 600L,
        )

        assertEquals(old, merged)
    }

    @Test fun live_room_morning_correction_can_update_morning_without_blocking_later_reference_retry() {
        val old = com.myanmar.ledger2d.core.database.LiveDailyResultEntity(
            id = 1L,
            date = friday,
            modern930 = null,
            internet930 = null,
            modern200 = null,
            internet200 = null,
            morning2d = "35",
            morningSet = "1,000",
            morningValue = "2,000",
            evening2d = null,
            eveningSet = null,
            eveningValue = null,
            reference930SourceAt = null,
            reference200SourceAt = null,
            morningSourceAt = 120L,
            eveningSourceAt = null,
            updatedAt = 120L,
        )

        val lateReference = com.myanmar.ledger2d.core.database.LiveDailyResultPatch(
            date = friday,
            modern930 = "80",
            internet930 = "33",
            reference930SourceAt = 100L,
        )

        val merged = com.myanmar.ledger2d.core.repository.LiveDailyResultMerger.merge(
            old = old,
            patch = lateReference,
            updatedAt = 200L,
        )

        assertEquals("35", merged.morning2d)
        assertEquals("80", merged.modern930)
        assertEquals("33", merged.internet930)
    }

    @Test fun live_room_newer_provider_correction_updates_final_morning_result() {
        val old = com.myanmar.ledger2d.core.database.LiveDailyResultEntity(
            id = 1L,
            date = friday,
            modern930 = null,
            internet930 = null,
            modern200 = null,
            internet200 = null,
            morning2d = "35",
            morningSet = "1,000",
            morningValue = "2,000",
            evening2d = null,
            eveningSet = null,
            eveningValue = null,
            reference930SourceAt = null,
            reference200SourceAt = null,
            morningSourceAt = 120L,
            eveningSourceAt = null,
            updatedAt = 120L,
        )

        val corrected = com.myanmar.ledger2d.core.database.LiveDailyResultPatch(
            date = friday,
            morning2d = "36",
            morningSet = "1,100",
            morningValue = "2,100",
            morningSourceAt = 130L,
        )

        val merged = com.myanmar.ledger2d.core.repository.LiveDailyResultMerger.merge(
            old = old,
            patch = corrected,
            updatedAt = 130L,
        )

        assertEquals("36", merged.morning2d)
        assertEquals("1,100", merged.morningSet)
        assertEquals("2,100", merged.morningValue)
    }

    @Test fun live_room_older_morning_observation_cannot_regress_newer_morning_result() {
        val old = com.myanmar.ledger2d.core.database.LiveDailyResultEntity(
            id = 1L,
            date = friday,
            modern930 = null,
            internet930 = null,
            modern200 = null,
            internet200 = null,
            morning2d = "36",
            morningSet = "1,100",
            morningValue = "2,100",
            evening2d = null,
            eveningSet = null,
            eveningValue = null,
            reference930SourceAt = null,
            reference200SourceAt = null,
            morningSourceAt = 130L,
            eveningSourceAt = null,
            updatedAt = 130L,
        )

        val older = com.myanmar.ledger2d.core.database.LiveDailyResultPatch(
            date = friday,
            morning2d = "35",
            morningSet = "1,000",
            morningValue = "2,000",
            morningSourceAt = 120L,
        )

        val merged = com.myanmar.ledger2d.core.repository.LiveDailyResultMerger.merge(
            old = old,
            patch = older,
            updatedAt = 140L,
        )

        assertEquals("36", merged.morning2d)
        assertEquals("1,100", merged.morningSet)
        assertEquals("2,100", merged.morningValue)
    }

    @Test fun collector_persists_reference_snapshot_through_room_saver_without_changing_ui_flow() = runTest {
        val saved = mutableListOf<com.myanmar.ledger2d.core.database.LiveDailyResultPatch>()
        val f = feed(
            "--",
            "09:34:00",
            modern930 = "80",
            internet930 = "33",
            modern200 = "--",
            internet200 = "--",
        ).copy(date = thursday.toString(), serverTimeEpochMs = 1_000L)

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { f },
            clock = { LocalTime.of(9, 34) },
            dateProvider = { friday },
            liveRoomSaver = { patches -> saved += patches },
        )

        collector.fetchCycle()
        runCurrent()

        assertTrue(saved.any { it.date == friday && it.modern930 == "80" && it.internet930 == "33" })
        val state = collector.state.value as LiveUiState.Data
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)

        scope.cancel()
    }
}
