package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCollectorTest {

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
        // 09:30-11:29 must NOT be live polling
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
    // Collector behavior
    // ------------------------------------------------------------------

    @Test fun `failed fetch retains previous data and marks stale`() = runTest {
        val feed1 = sampleFeed(live = "35")
        val responses = listOf(feed1, null, null).iterator()
        val collector = LiveCollector(scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), fetcher = { responses.next() })

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

    @Test fun `successful fetch replaces cache and clears stale`() = runTest {
        val feed1 = sampleFeed(live = "35")
        val feed2 = sampleFeed(live = "36")
        val responses = listOf(feed1, null, feed2).iterator()
        val collector = LiveCollector(scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), fetcher = { responses.next() })

        collector.fetchCycle(); advanceUntilIdle()
        collector.fetchCycle(); advanceUntilIdle() // failure -> stale retained
        assertTrue((collector.state.value as LiveUiState.Data).stale)

        collector.fetchCycle(); advanceUntilIdle() // success
        val s = collector.state.value as LiveUiState.Data
        assertFalse(s.stale)
        assertEquals(feed2, s.feed)
    }

    @Test fun `first fetch failure with no previous data surfaces error state`() = runTest {
        val collector = LiveCollector(scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), fetcher = { null })
        collector.fetchCycle(); advanceUntilIdle()
        assertTrue(collector.state.value is LiveUiState.Error)
    }

    @Test fun `overlapping fetch requests are prevented`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = 0
        val collector = LiveCollector(scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), fetcher = { started++; gate.await(); sampleFeed() })

        collector.fetchCycle() // cycle 1 queued, suspended on the gate
        collector.fetchCycle() // must be ignored while cycle 1 is active
        advanceUntilIdle()     // would execute a second cycle if not guarded
        assertEquals(1, started)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(collector.state.value is LiveUiState.Data)
    }

    @Test fun `screen is never needed - collector keeps polling inside a window`() = runTest {
        var fetchCount = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
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
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
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
        val collector = LiveCollector(scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)), fetcher = { sampleFeed(live = "37") })
        collector.fetchCycle(); advanceUntilIdle()
        // Re-entering the screen collects the shared StateFlow, whose current
        // value is the latest cache — no new fetch is required to display it.
        val latest = collector.state.value
        assertTrue(latest is LiveUiState.Data)
        assertEquals("37", (latest as LiveUiState.Data).feed.live)
    }

    private fun sampleFeed(live: String = "35") = LiveFeedData(
        date = "29/09/2026",
        currentTime = "11:45:00",
        live = live,
        liveSet = "1,594.88",
        liveVal = "32,581.50",
        morning = LiveSessionData(result = "--", set = "--", value = "--", finalized = false),
        evening = LiveSessionData(result = "--", set = "--", value = "--", finalized = false),
        modern930 = "98",
        internet930 = "15",
        modern200 = "--",
        internet200 = "--",
    )
}
