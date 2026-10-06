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
    private val tuesday = monday.plusDays(1)

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

    @Test fun fresh_start_tuesday_before_0930_recovers_monday_not_friday() = runTest {
        val mondayFinal = LiveHeroSnapshot(
            result = "77",
            set = "1600",
            value = "20000",
            sessionLabel = LIVE_SESSION_EVENING_LABEL,
            date = monday.toString(),
        )
        val fridayFinal = mondayFinal.copy(result = "25", date = friday.toString())

        var recoveredDate: java.time.LocalDate? = null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(7, 0) },
            dateProvider = { tuesday },
            historicalFinalFetcher = { date ->
                recoveredDate = date
                when (date) {
                    monday -> mondayFinal
                    friday -> fridayFinal
                    else -> null
                }
            },
        )

        collector.start()
        runCurrent()

        assertEquals(monday, recoveredDate)
        val state = collector.state.value as LiveUiState.Data
        assertEquals("77", state.hero?.result)
        assertEquals(monday.toString(), state.hero?.date)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_tuesday_before_0930_rejects_friday_only_cached_hero() = runTest {
        val fridayFinal = LiveHeroSnapshot(
            result = "25",
            set = "1600",
            value = "20000",
            sessionLabel = LIVE_SESSION_EVENING_LABEL,
            date = friday.toString(),
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(7, 0) },
            dateProvider = { tuesday },
            cacheLoader = { fridayFinal },
            historicalFinalFetcher = { null },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertNull(state.hero)

        scope.cancel()
    }

    @Test fun history_row_reconstructs_complete_held_feed() {
        val row = com.myanmar.ledger2d.core.database.HistoryResultEntity(
            date = monday,
            morning2d = "36",
            morningSet = "1,500",
            morningValue = "30,000",
            evening2d = "57",
            eveningSet = "1,576.45",
            eveningValue = "55,047.95",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        val feed = historyRowToFeed(row)

        assertEquals(monday.toString(), feed.date)
        assertEquals("36", feed.morning.result)
        assertTrue(feed.morning.finalized)
        assertEquals("57", feed.evening.result)
        assertTrue(feed.evening.finalized)
        assertEquals("80", feed.modern930)
        assertEquals("33", feed.internet930)
        assertEquals("98", feed.modern200)
        assertEquals("78", feed.internet200)
        assertEquals("--", feed.live)
        assertEquals("HISTORY", feed.sourceTag)
    }

    @Test fun fresh_start_tuesday_before_0930_reconstructs_monday_full_held_state() = runTest {
        val mondayRow = com.myanmar.ledger2d.core.database.HistoryResultEntity(
            date = monday,
            morning2d = "36",
            morningSet = "1,500",
            morningValue = "30,000",
            evening2d = "57",
            eveningSet = "1,576.45",
            eveningValue = "55,047.95",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        var historyCalls = 0
        var lukeCalls = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = {
                lukeCalls++
                null
            },
            clock = { LocalTime.of(8, 24) },
            dateProvider = { tuesday },
            historicalFeedFetcher = { date ->
                historyCalls++
                assertEquals(monday, date)
                historyRowToFeed(mondayRow)
            },
            historicalFinalFetcher = { null },
        )

        collector.start()
        runCurrent()

        assertEquals(1, historyCalls)
        assertEquals(1, lukeCalls)

        val state = collector.state.value as LiveUiState.Data
        assertEquals(monday.toString(), state.feed?.date)
        assertEquals("36", state.feed?.morning?.result)
        assertEquals("57", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("57", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_monday_before_0930_reconstructs_friday_full_held_state() = runTest {
        val fridayRow = com.myanmar.ledger2d.core.database.HistoryResultEntity(
            date = friday,
            morning2d = "22",
            morningSet = "1,200",
            morningValue = "20,000",
            evening2d = "25",
            eveningSet = "1,300",
            eveningValue = "21,000",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        var recoveredDate: java.time.LocalDate? = null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(8, 30) },
            dateProvider = { monday },
            historicalFeedFetcher = { date ->
                recoveredDate = date
                historyRowToFeed(fridayRow)
            },
        )

        collector.start()
        runCurrent()

        assertEquals(friday, recoveredDate)
        val state = collector.state.value as LiveUiState.Data
        assertEquals(friday.toString(), state.feed?.date)
        assertEquals("22", state.feed?.morning?.result)
        assertEquals("25", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun weekend_cold_start_reconstructs_friday_full_held_state() = runTest {
        val fridayRow = com.myanmar.ledger2d.core.database.HistoryResultEntity(
            date = friday,
            morning2d = "22",
            morningSet = "1,200",
            morningValue = "20,000",
            evening2d = "25",
            eveningSet = "1,300",
            eveningValue = "21,000",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(18, 0) },
            dateProvider = { saturday },
            historicalFeedFetcher = {
                historyRowToFeed(fridayRow)
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals(friday.toString(), state.feed?.date)
        assertEquals("25", state.hero?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("98", state.feed?.modern200)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    private fun historyRow(
        date: java.time.LocalDate,
        morning: String = "36",
        evening: String = "57",
        modern930: String = "80",
        internet930: String = "33",
        modern200: String = "98",
        internet200: String = "78",
    ) = com.myanmar.ledger2d.core.database.HistoryResultEntity(
        date = date,
        morning2d = morning,
        morningSet = "1,500",
        morningValue = "30,000",
        evening2d = evening,
        eveningSet = "1,576.45",
        eveningValue = "55,047.95",
        modern930 = modern930,
        internet930 = internet930,
        modern200 = modern200,
        internet200 = internet200,
    )

    @Test fun fresh_start_monday_0830_enters_friday_held_phase_completely() = runTest {
        val fridayRow = historyRow(friday, morning = "22", evening = "25")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(8, 30) },
            dateProvider = { monday },
            historicalFeedFetcher = { date ->
                if (date == friday) historyRowToFeed(fridayRow, monday, LocalTime.of(8, 30)) else null
            },
            historicalFinalFetcher = { date ->
                if (date == friday) historyRowToFinal(fridayRow) else null
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals(friday.toString(), state.feed?.date)
        assertEquals("22", state.feed?.morning?.result)
        assertEquals("25", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_monday_1830_enters_monday_completed_phase_completely() = runTest {
        val mondayRow = historyRow(monday)
        val fridayRow = historyRow(friday, morning = "22", evening = "25")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(18, 30) },
            dateProvider = { monday },
            historicalFeedFetcher = { date ->
                when (date) {
                    monday -> historyRowToFeed(mondayRow, monday, LocalTime.of(18, 30))
                    else -> null
                }
            },
            historicalFinalFetcher = { date ->
                if (date == friday) historyRowToFinal(fridayRow) else null
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals(monday.toString(), state.feed?.date)
        assertEquals("36", state.feed?.morning?.result)
        assertEquals("57", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("57", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_tuesday_0830_enters_monday_held_phase_completely() = runTest {
        val mondayRow = historyRow(monday)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(8, 30) },
            dateProvider = { tuesday },
            historicalFeedFetcher = { date ->
                if (date == monday) historyRowToFeed(mondayRow, tuesday, LocalTime.of(8, 30)) else null
            },
            historicalFinalFetcher = { date ->
                if (date == monday) historyRowToFinal(mondayRow) else null
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals(monday.toString(), state.feed?.date)
        assertEquals("36", state.feed?.morning?.result)
        assertEquals("57", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("57", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_tuesday_1130_enters_new_cycle_with_pending_sessions_and_live_gate() = runTest {
        val mondayRow = historyRow(monday, morning = "36", evening = "57")
        val tuesdayRow = historyRow(tuesday, morning = "44", evening = "66")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val tuesdayFeed = historyRowToFeed(tuesdayRow, tuesday, LocalTime.of(11, 30)).copy(
            sourceTag = "LUKE",
            live = LIVE_PENDING,
            liveSet = LIVE_PENDING,
            liveVal = LIVE_PENDING,
        )
        val collector = LiveCollector(
            scope = scope,
            fetcher = { tuesdayFeed },
            clock = { LocalTime.of(11, 30) },
            dateProvider = { tuesday },
            historicalFeedFetcher = { date ->
                if (date == tuesday) historyRowToFeed(tuesdayRow, tuesday, LocalTime.of(11, 30)) else null
            },
            historicalFinalFetcher = { date ->
                if (date == monday) historyRowToFinal(mondayRow) else null
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals(tuesday.toString(), state.feed?.date)
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)
        assertEquals("57", state.hero?.result)
        assertEquals(tuesday.toString(), state.feed?.date)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_tuesday_1730_enters_tuesday_completed_phase_completely() = runTest {
        val mondayRow = historyRow(monday, morning = "36", evening = "57")
        val tuesdayRow = historyRow(tuesday, morning = "44", evening = "66")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(17, 30) },
            dateProvider = { tuesday },
            historicalFeedFetcher = { date ->
                if (date == tuesday) historyRowToFeed(tuesdayRow, tuesday, LocalTime.of(17, 30)) else null
            },
            historicalFinalFetcher = { date ->
                if (date == monday) historyRowToFinal(mondayRow) else null
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals(tuesday.toString(), state.feed?.date)
        assertEquals("44", state.feed?.morning?.result)
        assertEquals("66", state.feed?.evening?.result)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("66", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
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

    @Test fun monday_reference_response_keeps_friday_hero_until_morning_live() = runTest {
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
        val mondayFeed = LiveFeedData(
            date = monday.toString(),
            currentTime = "09:41:00",
            live = "--",
            liveSet = "--",
            liveVal = "--",
            morning = LiveSessionData("--", "--", "--", false),
            evening = LiveSessionData("--", "--", "--", false),
            modern930 = "81",
            internet930 = "17",
            modern200 = "--",
            internet200 = "--",
            sourceTag = "LUKE",
            serverTimeEpochMs = monday.atTime(9, 41).atZone(yangon).toInstant().toEpochMilli(),
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { mondayFeed },
            clock = { LocalTime.of(9, 41) },
            dateProvider = { monday },
            cacheFeedLoader = { fridayFeed },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)
        assertEquals("81", state.feed?.modern930)
        assertEquals("17", state.feed?.internet930)
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)

        scope.cancel()
    }

    @Test fun fresh_install_monday_before_morning_live_recovers_previous_final_without_local_cache() = runTest {
        val mondayFeed = LiveFeedData(
            date = monday.toString(),
            currentTime = "10:27:00",
            live = "--",
            liveSet = "--",
            liveVal = "--",
            morning = LiveSessionData("--", "--", "--", false),
            evening = LiveSessionData("--", "--", "--", false),
            modern930 = "81",
            internet930 = "17",
            modern200 = "--",
            internet200 = "--",
            sourceTag = "LUKE",
            serverTimeEpochMs = monday.atTime(10, 27).atZone(yangon).toInstant().toEpochMilli(),
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { mondayFeed },
            clock = { LocalTime.of(10, 27) },
            dateProvider = { monday },
            historicalFinalFetcher = { date ->
                assertEquals(friday, date)
                LiveHeroSnapshot("25", "1,571.62", "71,085.86", LIVE_SESSION_EVENING_LABEL, friday.toString())
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)
        assertEquals("81", state.feed?.modern930)
        assertEquals("17", state.feed?.internet930)

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

    @Test fun afternoon_reference_phase_does_not_promote_startup_luke_live_to_hero() {
        val todayFeed = feed(
            "35",
            "15:10:00",
            morning = finalMorning("36"),
            evening = LiveSessionData("--", "--", "--", false),
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        val result = resolveLiveState(
            p = SourceObservation(todayFeed, 100L, 0L, 100L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(15, 10),
            scheduleDate = friday,
        )

        assertFalse(result.heroLive)
        assertEquals("36", result.hero?.result)
        assertEquals("15:10:00", result.displayFeed?.currentTime)
    }

    @Test fun afternoon_hold_starts_at_1400_and_stays_non_live() {
        val todayFeed = feed(
            "35",
            "14:00:00",
            morning = finalMorning("36"),
            evening = LiveSessionData("--", "--", "--", false),
        )

        val result = resolveLiveState(
            p = SourceObservation(todayFeed, 100L, 0L, 100L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(14, 0),
            scheduleDate = friday,
        )

        assertFalse(result.heroLive)
        assertEquals("36", result.hero?.result)
    }

    @Test fun evening_live_resumes_exactly_at_1600() {
        val todayFeed = feed(
            "44",
            "16:00:00",
            morning = finalMorning("36"),
            evening = LiveSessionData("--", "--", "--", false),
        )

        val result = resolveLiveState(
            p = SourceObservation(todayFeed, 100L, 0L, 100L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = null,
            scheduleTime = LocalTime.of(16, 0),
            scheduleDate = friday,
        )

        assertTrue(result.heroLive)
        assertEquals("44", result.hero?.result)
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

    @Test fun fresh_start_0830_does_not_start_reference_or_live_polling() = runTest {
        var calls = 0
        val yesterday = thursday
        val old = feed(
            "--",
            "17:00:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
        ).copy(date = yesterday.toString(), serverTimeEpochMs = null)

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                old
            },
            clock = { LocalTime.of(8, 30) },
            dateProvider = { friday },
        )

        collector.start()
        runCurrent()
        assertEquals(1, calls)

        val state = collector.state.value as LiveUiState.Data
        assertEquals("25", state.hero?.result)
        assertFalse(state.heroLive)
        assertEquals(yesterday.toString(), state.feed?.date)

        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 3)
        runCurrent()
        assertEquals(1, calls)

        scope.cancel()
    }

    @Test fun fresh_start_0935_reference_failure_keeps_yesterday_cards_until_1130() = runTest {
        val mondayRow = historyRow(monday)
        var now = LocalTime.of(9, 35)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { now },
            dateProvider = { tuesday },
            historicalFeedFetcher = { date ->
                if (date == monday) historyRowToFeed(mondayRow, tuesday, now) else null
            },
            historicalFinalFetcher = { date ->
                if (date == monday) historyRowToFinal(mondayRow) else null
            },
        )

        collector.start()
        runCurrent()

        var state = collector.state.value as LiveUiState.Data
        assertEquals(monday.toString(), state.feed?.date)
        assertEquals("36", state.feed?.morning?.result)
        assertEquals("57", state.feed?.evening?.result)
        assertEquals("--", state.feed?.modern930)
        assertEquals("--", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)
        assertEquals("57", state.hero?.result)
        assertFalse(state.heroLive)

        now = LocalTime.of(11, 30)
        advanceTimeBy(NORMAL_POLL_INTERVAL_MS)
        runCurrent()

        state = collector.state.value as LiveUiState.Data
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)
        assertEquals("57", state.hero?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_0935_fetches_todays_reference_instead_of_historical_reference() = runTest {
        val mondayRow = historyRow(
            monday,
            morning = "36",
            evening = "57",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )
        val tuesdayFeed = feed(
            "--",
            "09:35:00",
            morning = LiveSessionData("--", "--", "--", false),
            evening = LiveSessionData("--", "--", "--", false),
            modern930 = "91",
            internet930 = "17",
            modern200 = "--",
            internet200 = "--",
        ).copy(
            date = tuesday.toString(),
            serverTimeEpochMs = tuesday
                .atTime(9, 35)
                .atZone(yangon)
                .toInstant()
                .toEpochMilli(),
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { tuesdayFeed },
            clock = { LocalTime.of(9, 35) },
            dateProvider = { tuesday },
            historicalFeedFetcher = { date ->
                if (date == monday) historyRowToFeed(mondayRow, tuesday, LocalTime.of(9, 35)) else null
            },
            historicalFinalFetcher = { date ->
                if (date == monday) historyRowToFinal(mondayRow) else null
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertEquals("91", state.feed?.modern930)
        assertEquals("17", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)
        assertEquals("--", state.feed?.morning?.result)
        assertEquals("--", state.feed?.evening?.result)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun fresh_start_1000_catches_up_0930_reference_without_live_polling() = runTest {
        var calls = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                feed(
                    "--",
                    "10:00:00",
                    morning = LiveSessionData("--", "--", "--", false),
                    evening = LiveSessionData("--", "--", "--", false),
                    modern930 = "80",
                    internet930 = "33",
                    modern200 = "98",
                    internet200 = "78",
                )
            },
            clock = { LocalTime.of(10, 0) },
            dateProvider = { friday },
        )

        collector.start()
        runCurrent()

        assertEquals(2, calls)
        val state = collector.state.value as LiveUiState.Data
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)
        assertFalse(state.heroLive)

        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 2)
        runCurrent()
        assertEquals(2, calls)

        scope.cancel()
    }

    @Test fun fresh_start_1500_catches_up_both_reference_slots_without_live_polling() = runTest {
        var calls = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                feed(
                    "--",
                    "15:00:00",
                    morning = finalMorning("36"),
                    evening = LiveSessionData("--", "--", "--", false),
                    modern930 = "80",
                    internet930 = "33",
                    modern200 = "98",
                    internet200 = "78",
                )
            },
            clock = { LocalTime.of(15, 0) },
            dateProvider = { friday },
        )

        collector.start()
        runCurrent()

        assertEquals(3, calls)
        val state = collector.state.value as LiveUiState.Data
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("36", state.hero?.result)
        assertFalse(state.heroLive)

        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 2)
        runCurrent()
        assertEquals(3, calls)

        scope.cancel()
    }

    @Test fun fresh_start_1800_catches_up_reference_slots_without_starting_evening_live_polling() = runTest {
        var calls = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope,
            fetcher = {
                calls++
                feed(
                    "--",
                    "18:00:00",
                    morning = finalMorning("36"),
                    evening = finalEvening("77"),
                    modern930 = "80",
                    internet930 = "33",
                    modern200 = "98",
                    internet200 = "78",
                ).copy(date = monday.toString(), serverTimeEpochMs = null)
            },
            clock = { LocalTime.of(18, 0) },
            dateProvider = { monday },
        )

        collector.start()
        runCurrent()

        assertEquals(3, calls)
        val state = collector.state.value as LiveUiState.Data
        assertEquals(monday.toString(), state.feed?.date)
        assertEquals("80", state.feed?.modern930)
        assertEquals("33", state.feed?.internet930)
        assertEquals("98", state.feed?.modern200)
        assertEquals("78", state.feed?.internet200)
        assertEquals("77", state.hero?.result)
        assertFalse(state.heroLive)

        advanceTimeBy(NORMAL_POLL_INTERVAL_MS * 3)
        runCurrent()
        assertEquals(3, calls)

        scope.cancel()
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

    @Test fun stale_provider_reference_does_not_complete_current_cycle() = runTest {
        val stale = feed(
            "--",
            "09:35:00",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        ).copy(
            date = monday.toString(),
            serverTimeEpochMs = monday
                .atTime(9, 35)
                .atZone(yangon)
                .toInstant()
                .toEpochMilli(),
        )

        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { stale },
            clock = { LocalTime.of(9, 35) },
            dateProvider = { tuesday },
        )

        collector.start()
        runCurrent()

        var state = collector.state.value as LiveUiState.Data
        assertEquals("--", state.feed?.modern930)
        assertEquals("--", state.feed?.internet930)

        scope.cancel()
    }

    @Test fun main_poll_cannot_erase_successful_current_day_reference() = runTest {
        val currentReference = feed(
            "--",
            "09:35:00",
            modern930 = "81",
            internet930 = "17",
            modern200 = "--",
            internet200 = "--",
        ).copy(
            date = tuesday.toString(),
            serverTimeEpochMs = tuesday
                .atTime(9, 35)
                .atZone(yangon)
                .toInstant()
                .toEpochMilli(),
        )
        val mainSnapshot = currentReference.copy(
            modern930 = "--",
            internet930 = "--",
        )
        var calls = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = {
                calls++
                if (calls == 1) currentReference else mainSnapshot
            },
            clock = { LocalTime.of(9, 35) },
            dateProvider = { tuesday },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertTrue(calls >= 2)
        assertEquals("81", state.feed?.modern930)
        assertEquals("17", state.feed?.internet930)
        assertEquals("--", state.feed?.modern200)
        assertEquals("--", state.feed?.internet200)

        scope.cancel()
    }

    @Test fun friday_after_1700_rejects_previous_working_day_primary_feed() {
        val thursdayFeed = feed(
            "--",
            "17:00:00",
            morning = finalMorning("22"),
            evening = finalEvening("25"),
        ).copy(date = thursday.toString())
        val fridayFinal = LiveHeroSnapshot(
            result = "36",
            set = "1600",
            value = "20000",
            sessionLabel = LIVE_SESSION_MORNING_LABEL,
            date = friday.toString(),
        )

        val result = resolveLiveState(
            p = SourceObservation(thursdayFeed, 0L, 0L, 10L),
            s = null,
            now = java.time.Instant.now(),
            lastLive = null,
            cachedFinal = fridayFinal,
            scheduleTime = LocalTime.of(17, 30),
            scheduleDate = friday,
        )

        assertNull(result.displayFeed)
        assertEquals("36", result.hero?.result)
        assertEquals(friday.toString(), result.hero?.date)
        assertFalse(result.heroLive)
    }

    @Test fun friday_after_1700_does_not_fallback_to_thursday_when_friday_feed_is_unavailable() = runTest {
        val fridayFinal = LiveHeroSnapshot(
            result = "36",
            set = "1600",
            value = "20000",
            sessionLabel = LIVE_SESSION_MORNING_LABEL,
            date = friday.toString(),
        )
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = LiveCollector(
            scope = scope,
            fetcher = { null },
            clock = { LocalTime.of(17, 30) },
            dateProvider = { friday },
            historicalFeedFetcher = { null },
            historicalFinalFetcher = { date ->
                if (date == friday) fridayFinal else null
            },
        )

        collector.start()
        runCurrent()

        val state = collector.state.value as LiveUiState.Data
        assertNull(state.feed)
        assertEquals("36", state.hero?.result)
        assertEquals(friday.toString(), state.hero?.date)
        assertFalse(state.heroLive)

        scope.cancel()
    }

    @Test fun schedule_aware_history_projection_hides_future_evening_result() {
        val row = historyRow(
            tuesday,
            morning = "44",
            evening = "66",
            modern930 = "80",
            internet930 = "33",
            modern200 = "98",
            internet200 = "78",
        )

        val projected = historyRowToFeed(
            row,
            tuesday,
            LocalTime.of(12, 30),
        )

        assertEquals(tuesday.toString(), projected.date)
        assertEquals("44", projected.morning.result)
        assertTrue(projected.morning.finalized)
        assertEquals("--", projected.evening.result)
        assertFalse(projected.evening.finalized)
        assertEquals("80", projected.modern930)
        assertEquals("33", projected.internet930)
        assertEquals("--", projected.modern200)
        assertEquals("--", projected.internet200)
    }

}
