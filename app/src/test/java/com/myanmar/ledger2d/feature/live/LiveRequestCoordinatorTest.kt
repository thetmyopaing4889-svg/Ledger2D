package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        isReferenceComplete: (Boolean, LocalDate) -> Boolean = { _, _ -> false },
        acceptReferenceFeed: suspend (Boolean, LocalDate, LiveFeedData) -> Boolean = { _, _, _ -> false },
    ) = LiveRequestCoordinator(
        scope = scope,
        fetcher = fetcher,
        monotonicClockMs = { System.nanoTime() / 1_000_000L },
        clock = clock,
        dateProvider = dateProvider,
        referenceRetryWindowOpen = { _, _, _ -> true },
        referenceComplete = isReferenceComplete,
        fetchReferencePair = fetchReferencePair,
        onLiveResult = onLiveResult,
        acceptReferenceFeed = acceptReferenceFeed,
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
    fun morning_primary_success_does_not_start_fallback_request() = runTest {
        var calls = 0
        var fallbackCalls = 0
        var referenceComplete = false
        val result = feed("09:34:00").copy(
            modern930 = "35",
            internet930 = "12",
        )
        val requestCoordinator = coordinator(
            scope = this,
            fetcher = {
                calls++
                result
            },
            onLiveResult = { event ->
                if (
                    event.feed.modern930 == "35" &&
                    event.feed.internet930 == "12"
                ) {
                    referenceComplete = true
                }
            },
            clock = { LocalTime.of(9, 34) },
            isReferenceComplete = { isMorning, date ->
                isMorning && date == today && referenceComplete
            },
            fetchReferencePair = { _, _ ->
                fallbackCalls++
                false
            },
            acceptReferenceFeed = { _, _, _ ->
                fallbackCalls++
                false
            },
        )

        requestCoordinator.startMorningReferenceCycle()
        runCurrent()

        assertEquals("The normal Daily Flow request must be the only request when it supplies a valid pair", 1, calls)
        assertEquals("Fallback must not run after normal acceptance completes the pair", 0, fallbackCalls)
    }

    @Test
    fun morning_fallback_starts_only_after_normal_request_fails() = runTest {
        val primaryGate = CompletableDeferred<LiveFeedData?>()
        var calls = 0
        var fallbackCalls = 0
        val requestCoordinator = coordinator(
            scope = this,
            fetcher = {
                calls++
                if (calls == 1) primaryGate.await() else feed("09:35:00")
            },
            onLiveResult = {},
            clock = { LocalTime.of(9, 34) },
            fetchReferencePair = { isMorning, date ->
                assertTrue(isMorning)
                assertEquals(today, date)
                fallbackCalls++
                false
            },
        )

        requestCoordinator.startMorningReferenceCycle()
        runCurrent()

        assertEquals(1, calls)
        assertEquals("Fallback must wait while the normal request is still in flight", 0, fallbackCalls)

        primaryGate.complete(null)
        runCurrent()

        assertEquals("The failed primary attempt must be followed by the fallback", 1, fallbackCalls)
        assertEquals("The fallback must not start while the primary attempt is still running", 1, calls)
    }

    @Test
    fun morning_cycle_waits_for_existing_normal_request_before_starting_another() = runTest {
        val primaryGate = CompletableDeferred<LiveFeedData?>()
        var calls = 0
        var fallbackCalls = 0
        var referenceComplete = false
        val validPair = feed("09:34:30").copy(
            modern930 = "36",
            internet930 = "14",
        )
        val requestCoordinator = coordinator(
            scope = this,
            fetcher = {
                calls++
                primaryGate.await()
            },
            onLiveResult = { event ->
                if (event.feed.modern930 == "36" && event.feed.internet930 == "14") {
                    referenceComplete = true
                }
            },
            clock = { LocalTime.of(9, 34) },
            isReferenceComplete = { isMorning, _ -> isMorning && referenceComplete },
            fetchReferencePair = { _, _ ->
                fallbackCalls++
                false
            },
        )

        requestCoordinator.launchLiveRequest()
        runCurrent()
        requestCoordinator.startMorningReferenceCycle()
        runCurrent()

        assertEquals("The in-flight normal request should be reused, not duplicated", 1, calls)
        assertEquals(0, fallbackCalls)

        primaryGate.complete(validPair)
        runCurrent()

        assertEquals("No second primary or fallback request is needed after the in-flight response supplies the pair", 1, calls)
        assertEquals(0, fallbackCalls)
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
    @Test
    fun date_change_cancels_the_old_reference_cycle_and_starts_the_new_cycle() = runTest {
        val monday = today
        val tuesday = today.plusDays(1)
        var currentDate = monday
        val oldCycleGate = CompletableDeferred<Unit>()
        val oldCycleCancelled = CompletableDeferred<Unit>()
        val attemptedDates = mutableListOf<LocalDate>()

        val requestCoordinator = coordinator(
            scope = backgroundScope,
            fetcher = { null },
            onLiveResult = {},
            dateProvider = { currentDate },
            fetchReferencePair = { _, cycleDate ->
                attemptedDates += cycleDate
                if (cycleDate == monday) {
                    try {
                        oldCycleGate.await()
                    } finally {
                        oldCycleCancelled.complete(Unit)
                    }
                    false
                } else {
                    true
                }
            },
        )

        requestCoordinator.startMorningReferenceCycle()
        runCurrent()
        assertEquals(listOf(monday), attemptedDates)

        currentDate = tuesday
        requestCoordinator.startMorningReferenceCycle()
        runCurrent()

        assertEquals(listOf(monday, tuesday), attemptedDates)
        assertTrue(oldCycleCancelled.isCompleted)
    }

}
