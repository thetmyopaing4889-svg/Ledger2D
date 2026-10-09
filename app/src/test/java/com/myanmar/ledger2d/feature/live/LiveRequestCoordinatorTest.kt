package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRequestCoordinatorTest {
    private val today = LocalDate.of(2026, 10, 5)

    private fun feed(time: String, live: String = "38") = LiveFeedData(
        date = today.toString(),
        currentTime = time,
        live = live,
        liveSet = if (isValidLive2d(live)) "1600" else LIVE_PENDING,
        liveVal = if (isValidLive2d(live)) "20000" else LIVE_PENDING,
        morning = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false),
        evening = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false),
        modern930 = LIVE_PENDING,
        internet930 = LIVE_PENDING,
        modern200 = LIVE_PENDING,
        internet200 = LIVE_PENDING,
    )

    private fun coordinator(
        scope: kotlinx.coroutines.CoroutineScope,
        fetcher: suspend () -> LiveFeedData?,
        onLiveResult: (LiveRequestResultEvent) -> Unit,
        clock: () -> LocalTime = { LocalTime.of(14, 0) },
        dateProvider: () -> LocalDate = { today },
        fetchReferencePair: suspend (Boolean, LocalDate) -> Boolean = { _, _ -> false },
    ) = LiveRequestCoordinator(
        scope = scope,
        fetcher = fetcher,
        monotonicClockMs = { System.nanoTime() / 1_000_000L },
        clock = clock,
        dateProvider = dateProvider,
        referenceRetryWindowOpen = { _, _, _ -> true },
        referenceComplete = { _, _ -> false },
        fetchReferencePair = fetchReferencePair,
        onLiveResult = onLiveResult,
    )

    @Test
    fun slow_live_request_does_not_block_another_request() = runTest {
        val firstGate = CompletableDeferred<LiveFeedData?>()
        var calls = 0
        val events = mutableListOf<LiveRequestResultEvent>()
        val requestCoordinator = coordinator(
            scope = this,
            fetcher = {
                calls++
                if (calls == 1) firstGate.await() else feed("14:01:00")
            },
            onLiveResult = events::add,
        )

        requestCoordinator.launchLiveRequest()
        runCurrent()
        requestCoordinator.launchLiveRequest()
        runCurrent()

        assertEquals(1, events.size)
        assertEquals(2L, events.single().sequence)

        firstGate.complete(feed("14:00:00"))
        runCurrent()

        assertEquals(listOf(2L, 1L), events.map { it.sequence })
    }

    @Test
    fun morning_and_afternoon_reference_retries_run_independently() = runTest {
        val attempts = mutableListOf<Boolean>()
        val requestCoordinator = coordinator(
            scope = backgroundScope,
            fetcher = { null },
            onLiveResult = {},
            fetchReferencePair = { isMorning, _ ->
                attempts += isMorning
                false
            },
        )

        requestCoordinator.startMorningReferenceCycle()
        requestCoordinator.startAfternoonReferenceCycle()
        runCurrent()

        assertEquals(2, attempts.size)
        assertEquals(1, attempts.count { it })
        assertEquals(1, attempts.count { !it })

        advanceTimeBy(LIVE_REFERENCE_FETCH_INTERVAL_MS)
        runCurrent()

        assertEquals(4, attempts.size)
        assertEquals(2, attempts.count { it })
        assertEquals(2, attempts.count { !it })
    }

    @Test
    fun failed_live_request_does_not_emit_state_event() = runTest {
        val events = mutableListOf<LiveRequestResultEvent>()
        val requestCoordinator = coordinator(
            scope = this,
            fetcher = { throw IllegalStateException("temporary failure") },
            onLiveResult = events::add,
        )

        requestCoordinator.launchLiveRequest()
        runCurrent()

        assertEquals(emptyList<LiveRequestResultEvent>(), events)
    }
}
