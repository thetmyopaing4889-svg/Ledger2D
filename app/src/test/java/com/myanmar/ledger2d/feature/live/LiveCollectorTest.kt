package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCollectorTest {

    /** Eager dispatch; pass runTest's scheduler to share virtual time. */
    private fun testScope(scheduler: TestScheduler? = null): CoroutineScope =
        if (scheduler == null) CoroutineScope(UnconfinedTestDispatcher()) else CoroutineScope(UnconfinedTestDispatcher(scheduler))

    // ------------------------------------------------------------------
    // Window schedule (pure schedule function)
    // ------------------------------------------------------------------

    @Test fun `morning Modern Internet collection starts at 0930`() {
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(9, 30)))
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(10, 0)))
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(11, 29)))
    }

    @Test fun `morning LIVE polling starts at 1130 not at 0930`() {
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(11, 30)))
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(11, 59)))
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(9, 30)))
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(11, 29)))
    }

    @Test fun `morning stop and final at 1201 inclusive`() {
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(12, 1)))
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(12, 1, 1)))
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(13, 0)))
    }

    @Test fun `evening Modern Internet collection starts at 1400`() {
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(14, 0)))
        assertEquals(LiveWindowAction.REFERENCE_ONLY, liveWindowAction(LocalTime.of(15, 59)))
    }

    @Test fun `evening LIVE polling starts at 1600`() {
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(16, 0)))
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(16, 29)))
    }

    @Test fun `evening stop and final at 1630 inclusive`() {
        assertEquals(LiveWindowAction.LIVE_POLLING, liveWindowAction(LocalTime.of(16, 30)))
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(16, 31)))
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(18, 0)))
    }

    @Test fun `outside windows no polling at all`() {
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(0, 0)))
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(9, 29)))
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(12, 30)))
        assertEquals(LiveWindowAction.NONE, liveWindowAction(LocalTime.of(23, 59)))
    }

    // ------------------------------------------------------------------
    // Hero lifecycle (pure deriveHero state machine)
    // ------------------------------------------------------------------

    @Test fun `next day 0930 reference data arrives while results stay pending`() {
        val feed = sampleFeed(live = "--", modern200 = "--", internet200 = "--")
        val derived = deriveHero(feed, LocalTime.of(9, 30), cachedFinal = yesterdayFinal())
        // Reference section gets today's 09:30 values…
        assertEquals("98", feed.modern930)
        assertEquals("15", feed.internet930)
        // …while both result sessions are still pending.
        assertFalse(feed.morning.finalized)
        assertFalse(feed.evening.finalized)
        assertEquals(LIVE_PENDING, feed.morning.result)
        assertEquals(LIVE_PENDING, feed.evening.result)
        // Hero is NOT blanked: the previous final stays.
        assertFalse(derived.isLive)
        assertEquals(yesterdayFinal(), derived.hero)
    }

    @Test fun `before 1130 hero remains the latest known final state`() {
        val feed = sampleFeed(live = "--")
        val derived = deriveHero(feed, LocalTime.of(10, 0), cachedFinal = yesterdayFinal())
        assertFalse(derived.isLive)
        assertEquals(yesterdayFinal(), derived.hero)
    }

    @Test fun `1130 morning window with live data switches hero to LIVE`() {
        val feed = sampleFeed(live = "36")
        val derived = deriveHero(feed, LocalTime.of(11, 30), cachedFinal = yesterdayFinal())
        assertTrue(derived.isLive)
        assertEquals("36", derived.hero?.result)
    }

    @Test fun `1201 final replaces morning LIVE and stops LIVE presentation`() {
        val finalMorning = sampleFeed(live = "36").copy(
            morning = LiveSessionData(result = "08", set = "1,599.50", value = "29,608.15", finalized = true),
        )
        val derived = deriveHero(finalMorning, LocalTime.of(12, 1), cachedFinal = yesterdayFinal())
        assertFalse(derived.isLive)
        assertEquals("08", derived.hero?.result)
        assertEquals(LIVE_SESSION_MORNING_LABEL, derived.hero?.sessionLabel)
    }

    @Test fun `final result wins over still-streaming live value`() {
        val feed = sampleFeed(live = "99").copy(
            morning = LiveSessionData(result = "08", set = "1,599.50", value = "29,608.15", finalized = true),
        )
        val derived = deriveHero(feed, LocalTime.of(12, 0), cachedFinal = null)
        assertFalse(derived.isLive)
        assertEquals("08", derived.hero?.result)
    }

    @Test fun `1400 afternoon reference updates while 430 stays pending`() {
        val feed = sampleFeed(live = "--", modern200 = "40", internet200 = "04")
        val derived = deriveHero(feed, LocalTime.of(14, 0), cachedFinal = yesterdayFinal())
        assertEquals("40", feed.modern200)
        assertEquals("04", feed.internet200)
        assertFalse(feed.evening.finalized)
        assertFalse(derived.isLive)
        // Hero keeps the morning final captured earlier today, not the cache.
        val expected = LiveHeroSnapshot("08", "1,599.50", "29,608.15", LIVE_SESSION_MORNING_LABEL, feed.date)
        val withMorningFinal = feed.copy(morning = LiveSessionData("08", "1,599.50", "29,608.15", finalized = true))
        assertEquals(expected, deriveHero(withMorningFinal, LocalTime.of(14, 0), cachedFinal = null).hero)
    }

    @Test fun `1600 evening window with live data switches hero to LIVE`() {
        val feed = sampleFeed(live = "12")
        val derived = deriveHero(feed, LocalTime.of(16, 0), cachedFinal = null)
        assertTrue(derived.isLive)
        assertEquals("12", derived.hero?.result)
    }

    @Test fun `1630 final replaces evening LIVE and stops LIVE presentation`() {
        val feed = sampleFeed(live = "12").copy(
            evening = LiveSessionData(result = "77", set = "1,602.37", value = "49,707.75", finalized = true),
        )
        val derived = deriveHero(feed, LocalTime.of(16, 30), cachedFinal = null)
        assertFalse(derived.isLive)
        assertEquals("77", derived.hero?.result)
        assertEquals(LIVE_SESSION_EVENING_LABEL, derived.hero?.sessionLabel)
    }

    @Test fun `outside windows hero falls back to latest known final`() {
        val feed = sampleFeed(live = "--")
        assertEquals(yesterdayFinal(), deriveHero(feed, LocalTime.of(20, 0), yesterdayFinal()).hero)
        assertNull(deriveHero(feed, LocalTime.of(20, 0), null).hero)
    }

    // ------------------------------------------------------------------
    // Collector behavior
    // ------------------------------------------------------------------

    @Test fun `cached final hero survives app restart`() {
        val persisted = yesterdayFinal()
        // A fresh process creates a new collector; the cache loader restores
        // the hero before any network request happens.
        val restarted = LiveCollector(
            scope = testScope(),
            fetcher = { null },
            cacheLoader = { persisted },
        )
        val initial = restarted.state.value
        assertTrue(initial is LiveUiState.Data)
        initial as LiveUiState.Data
        assertNull(initial.feed)
        assertFalse(initial.heroLive)
        assertEquals(persisted, initial.hero)
    }

    @Test fun `finals are persisted and live values are never saved`() = runTest {
        var saved: LiveHeroSnapshot? = null
        val liveFeed = sampleFeed(live = "36")
        val finalFeed = liveFeed.copy(morning = LiveSessionData("08", "1,599.50", "29,608.15", finalized = true))
        val responses = listOf(liveFeed, finalFeed).iterator()
        val collector = LiveCollector(
            scope = testScope(testScheduler),
            fetcher = { responses.next() },
            clock = { LocalTime.of(11, 45) },
            cacheSaver = { saved = it },
        )
        collector.fetchCycle(); advanceUntilIdle() // LIVE value — must NOT be saved
        assertNull(saved)
        collector.fetchCycle(); advanceUntilIdle() // 12:01 final — must be saved
        assertEquals(LiveHeroSnapshot("08", "1,599.50", "29,608.15", LIVE_SESSION_MORNING_LABEL, finalFeed.date), saved)
    }

    @Test fun `failed fetch retains previous data and marks stale`() = runTest {
        val feed1 = sampleFeed(live = "35")
        val responses = listOf(feed1, null, null).iterator()
        val collector = LiveCollector(scope = testScope(testScheduler), fetcher = { responses.next() })

        collector.fetchCycle(); advanceUntilIdle()
        val fresh = collector.state.value as LiveUiState.Data
        assertFalse(fresh.stale)

        collector.fetchCycle(); advanceUntilIdle() // fails
        val stale1 = collector.state.value as LiveUiState.Data
        assertEquals(feed1, stale1.feed)
        assertTrue(stale1.stale)

        collector.fetchCycle(); advanceUntilIdle() // fails again
        val stale2 = collector.state.value as LiveUiState.Data
        assertEquals(feed1, stale2.feed)
        assertTrue(stale2.stale)
    }

    @Test fun `successful fetch replaces cached data and clears stale`() = runTest {
        val feed1 = sampleFeed(live = "35")
        val feed2 = sampleFeed(live = "36")
        val responses = listOf(feed1, null, feed2).iterator()
        val collector = LiveCollector(scope = testScope(testScheduler), fetcher = { responses.next() })

        collector.fetchCycle(); advanceUntilIdle()
        collector.fetchCycle(); advanceUntilIdle() // failure -> stale retained
        assertTrue((collector.state.value as LiveUiState.Data).stale)

        collector.fetchCycle(); advanceUntilIdle() // success
        val s = collector.state.value as LiveUiState.Data
        assertFalse(s.stale)
        assertEquals(feed2, s.feed)
    }

    @Test fun `first fetch failure with no previous data surfaces error state`() = runTest {
        val collector = LiveCollector(scope = testScope(testScheduler), fetcher = { null })
        collector.fetchCycle(); advanceUntilIdle()
        assertTrue(collector.state.value is LiveUiState.Error)
    }

    @Test fun `overlapping fetch requests are prevented`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = 0
        val collector = LiveCollector(scope = testScope(testScheduler), fetcher = { started++; gate.await(); sampleFeed() })

        collector.fetchCycle() // cycle 1 queued, suspended on the gate
        collector.fetchCycle() // must be ignored while cycle 1 is active
        advanceUntilIdle()     // would execute a second cycle if not guarded
        assertEquals(1, started)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(collector.state.value is LiveUiState.Data)
    }

    @Test fun `intermediate live values are applied immediately without waiting`() = runTest {
        val responses = listOf(sampleFeed(live = "35"), sampleFeed(live = "36"), sampleFeed(live = "37")).iterator()
        val collector = LiveCollector(scope = testScope(testScheduler), fetcher = { responses.next() }, clock = { LocalTime.of(11, 45) })

        collector.fetchCycle(); advanceUntilIdle()
        assertEquals("35", (collector.state.value as LiveUiState.Data).hero?.result)
        collector.fetchCycle(); advanceUntilIdle()
        assertEquals("36", (collector.state.value as LiveUiState.Data).hero?.result)
        collector.fetchCycle(); advanceUntilIdle()
        assertEquals("37", (collector.state.value as LiveUiState.Data).hero?.result)
        // The blink is presentation-only (Compose layer); the data state here
        // moved 35 -> 36 -> 37 with no gating whatsoever.
    }

    @Test fun `closing the screen is irrelevant - collector keeps polling inside a window`() = runTest {
        var fetchCount = 0
        val scope = testScope(testScheduler)
        val collector = LiveCollector(scope = scope, fetcher = { fetchCount++; sampleFeed() }, clock = { LocalTime.of(16, 5) })
        // No screen/composable is involved anywhere in this test.
        collector.start()
        advanceTimeBy(11_000) // > two 5s ticks
        scope.cancel() // bound the infinite loop so runTest can finish
        assertTrue(fetchCount >= 2)
    }

    @Test fun `loop polls only during live windows and idles outside`() = runTest {
        var clock = LocalTime.of(13, 0) // outside all windows
        var fetchCount = 0
        val scope = testScope(testScheduler)
        val collector = LiveCollector(scope = scope, fetcher = { fetchCount++; sampleFeed() }, clock = { clock })
        collector.start()

        advanceTimeBy(11_000) // two ticks outside the window
        assertEquals(0, fetchCount)

        clock = LocalTime.of(16, 5) // evening LIVE window
        advanceTimeBy(6_000) // one tick
        assertEquals(1, fetchCount)

        clock = LocalTime.of(16, 31) // window over again
        advanceTimeBy(11_000)
        assertEquals(1, fetchCount)
        scope.cancel() // bound the infinite loop so runTest can finish
    }

    @Test fun `reopening the screen surfaces the latest cached state immediately`() = runTest {
        val collector = LiveCollector(scope = testScope(testScheduler), fetcher = { sampleFeed(live = "37") }, clock = { LocalTime.of(11, 45) })
        collector.fetchCycle(); advanceUntilIdle()
        // Re-entering the screen collects the shared StateFlow, whose current
        // value is the latest cache — no new fetch is required to display it.
        val latest = collector.state.value
        assertTrue(latest is LiveUiState.Data)
        latest as LiveUiState.Data
        assertEquals("37", latest.hero?.result)
        assertTrue(latest.heroLive)
    }

    private fun yesterdayFinal() = LiveHeroSnapshot("77", "1,602.37", "49,707.75", LIVE_SESSION_EVENING_LABEL, "28/09/2026")

    private fun sampleFeed(
        live: String = "35",
        modern200: String = "--",
        internet200: String = "--",
    ) = LiveFeedData(
        date = "29/09/2026",
        currentTime = "11:45:00",
        live = live,
        liveSet = "1,594.88",
        liveVal = "32,581.50",
        morning = LiveSessionData(result = LIVE_PENDING, set = LIVE_PENDING, value = LIVE_PENDING, finalized = false),
        evening = LiveSessionData(result = LIVE_PENDING, set = LIVE_PENDING, value = LIVE_PENDING, finalized = false),
        modern930 = "98",
        internet930 = "15",
        modern200 = modern200,
        internet200 = internet200,
    )
}
