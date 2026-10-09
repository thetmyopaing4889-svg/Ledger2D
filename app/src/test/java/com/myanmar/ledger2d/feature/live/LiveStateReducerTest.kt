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

    @Test
    fun confirmed_closed_day_clears_references_and_keeps_previous_primary_when_no_held_feed_exists() {
        val oldFeed = feed(time = "16:30:00", live = "38")
        val oldPrimary = SourceObservation(oldFeed, 100L, 50L, 50L)
        val oldHero = LiveHeroSnapshot("38", "1600", "20000", LIVE_SESSION_EVENING_LABEL, today.toString())
        val oldReferences = LiveReferenceState(
            reference930 = "12" to "34",
            reference930Date = today,
            reference200 = "56" to "78",
            reference200Date = today,
            reference930PendingDate = null,
            reference200PendingDate = null,
            reference930CompleteDate = today,
            reference200CompleteDate = today,
            referenceResetDate = today,
            referenceFeed = oldFeed,
            referenceFeedDate = today,
        )

        val reduced = LiveStateReducer.reduceConfirmedClosedDay(
            cycleDate = today.plusDays(1),
            heldFeed = null,
            elapsedRealtimeMs = 300L,
            previousPrimary = oldPrimary,
            previousLastLive = oldHero,
            previousLastFinal = oldHero,
            references = oldReferences,
        )

        assertEquals(today.plusDays(1), reduced.closedDayDate)
        assertEquals(oldPrimary, reduced.primary)
        assertNull(reduced.lastLive)
        assertEquals(oldHero, reduced.lastFinal)
        assertNull(reduced.references.reference930)
        assertNull(reduced.references.reference200)
        assertNull(reduced.references.referenceFeed)
        assertNull(reduced.references.reference930CompleteDate)
        assertNull(reduced.references.reference200CompleteDate)
    }

    @Test
    fun confirmed_closed_day_uses_held_feed_and_its_final_without_mutating_input() {
        val heldDate = previousWorkingDay(today.plusDays(1))
        val heldFeed = feed(
            time = "16:30:00",
            morning = session("47"),
            evening = session("62"),
        ).copy(date = heldDate.toString())
        val oldHero = LiveHeroSnapshot("38", "1600", "20000", LIVE_SESSION_EVENING_LABEL, today.toString())
        val refs = LiveReferenceState(
            reference930 = "12" to "34",
            reference930Date = today,
            reference200 = null,
            reference200Date = null,
            reference930PendingDate = null,
            reference200PendingDate = today,
            reference930CompleteDate = today,
            reference200CompleteDate = null,
            referenceResetDate = today,
            referenceFeed = heldFeed,
            referenceFeedDate = heldDate,
        )

        val reduced = LiveStateReducer.reduceConfirmedClosedDay(
            cycleDate = today.plusDays(1),
            heldFeed = heldFeed,
            elapsedRealtimeMs = 500L,
            previousPrimary = null,
            previousLastLive = oldHero,
            previousLastFinal = oldHero,
            references = refs,
        )

        assertEquals(heldFeed, reduced.primary?.feed)
        assertEquals(500L, reduced.primary?.fetchedAtElapsedMs)
        assertNull(reduced.lastLive)
        assertEquals("62", reduced.lastFinal?.result)
        assertEquals(heldDate.toString(), reduced.lastFinal?.date)
        assertEquals("12" to "34", refs.reference930)
        assertNull(reduced.references.reference930)
    }

    @Test
    fun closed_day_without_final_in_held_feed_preserves_last_known_final() {
        val heldFeed = feed(time = "16:30:00").copy(date = previousWorkingDay(today.plusDays(1)).toString())
        val previousFinal = LiveHeroSnapshot("74", "1600", "20000", LIVE_SESSION_EVENING_LABEL, today.toString())
        val reduced = LiveStateReducer.reduceConfirmedClosedDay(
            cycleDate = today.plusDays(1),
            heldFeed = heldFeed,
            elapsedRealtimeMs = 700L,
            previousPrimary = null,
            previousLastLive = null,
            previousLastFinal = previousFinal,
            references = LiveReferenceState(
                reference930 = null,
                reference930Date = null,
                reference200 = null,
                reference200Date = null,
                reference930PendingDate = null,
                reference200PendingDate = null,
                reference930CompleteDate = null,
                reference200CompleteDate = null,
                referenceResetDate = null,
                referenceFeed = null,
                referenceFeedDate = null,
            ),
        )

        assertEquals(previousFinal, reduced.lastFinal)
    }

    @Test
    fun startup_recovery_restores_references_only_for_a_completed_held_cycle() {
        val heldDate = previousWorkingDay(today)
        val heldFeed = feed(
            time = "16:30:00",
            morning = session("47"),
            evening = session("62"),
        ).copy(
            date = heldDate.toString(),
            modern930 = "12",
            internet930 = "34",
            modern200 = "56",
            internet200 = "78",
        )
        val initialRefs = LiveReferenceState(
            reference930 = null,
            reference930Date = null,
            reference200 = null,
            reference200Date = null,
            reference930PendingDate = today,
            reference200PendingDate = today,
            reference930CompleteDate = null,
            reference200CompleteDate = null,
            referenceResetDate = null,
            referenceFeed = null,
            referenceFeedDate = null,
        )

        val held = LiveStateReducer.reduceStartupRecovery(
            today = today,
            scheduleTime = LocalTime.of(8, 30),
            cycleDate = heldDate,
            recoveredFeed = heldFeed,
            currentFinal = null,
            fallbackFinal = null,
            previousPrimary = null,
            previousLastFinal = null,
            references = initialRefs,
            elapsedRealtimeMs = 1000L,
        )
        assertEquals("12" to "34", held.references.reference930)
        assertEquals("56" to "78", held.references.reference200)
        assertEquals(heldFeed, held.primary?.feed)

        val currentCycle = LiveStateReducer.reduceStartupRecovery(
            today = today,
            scheduleTime = LocalTime.of(14, 0),
            cycleDate = today,
            recoveredFeed = feed(time = "14:00:00").copy(
                modern930 = "12",
                internet930 = "34",
                modern200 = "56",
                internet200 = "78",
            ),
            currentFinal = null,
            fallbackFinal = null,
            previousPrimary = null,
            previousLastFinal = null,
            references = initialRefs,
            elapsedRealtimeMs = 2000L,
        )
        assertEquals(initialRefs, currentCycle.references)
    }

    @Test
    fun startup_recovery_keeps_current_cycle_final_ahead_of_historical_held_final() {
        val currentFinal = LiveHeroSnapshot(
            "47", "1600", "20000", LIVE_SESSION_EVENING_LABEL, today.toString()
        )
        val fallbackFinal = LiveHeroSnapshot(
            "28", "1600", "20000", LIVE_SESSION_EVENING_LABEL, previousWorkingDay(today).toString()
        )
        val recoveredHeld = feed(
            time = "16:30:00",
            evening = session("62"),
        ).copy(date = previousWorkingDay(today).toString())

        val result = LiveStateReducer.reduceStartupRecovery(
            today = today,
            scheduleTime = LocalTime.of(8, 30),
            cycleDate = previousWorkingDay(today),
            recoveredFeed = recoveredHeld,
            currentFinal = currentFinal,
            fallbackFinal = fallbackFinal,
            previousPrimary = null,
            previousLastFinal = null,
            references = LiveReferenceState(
                reference930 = null,
                reference930Date = null,
                reference200 = null,
                reference200Date = null,
                reference930PendingDate = null,
                reference200PendingDate = null,
                reference930CompleteDate = null,
                reference200CompleteDate = null,
                referenceResetDate = null,
                referenceFeed = null,
                referenceFeedDate = null,
            ),
            elapsedRealtimeMs = 3000L,
        )

        assertEquals(currentFinal, result.lastFinal)
    }

    @Test
    fun startup_recovery_uses_recovered_final_when_no_current_final_exists() {
        val recovered = feed(
            time = "16:30:00",
            evening = session("62"),
        ).copy(date = previousWorkingDay(today).toString())
        val fallbackFinal = LiveHeroSnapshot(
            "28", "1600", "20000", LIVE_SESSION_EVENING_LABEL, previousWorkingDay(today).toString()
        )
        val result = LiveStateReducer.reduceStartupRecovery(
            today = today,
            scheduleTime = LocalTime.of(8, 30),
            cycleDate = previousWorkingDay(today),
            recoveredFeed = recovered,
            currentFinal = null,
            fallbackFinal = fallbackFinal,
            previousPrimary = null,
            previousLastFinal = null,
            references = LiveReferenceState(
                reference930 = null,
                reference930Date = null,
                reference200 = null,
                reference200Date = null,
                reference930PendingDate = null,
                reference200PendingDate = null,
                reference930CompleteDate = null,
                reference200CompleteDate = null,
                referenceResetDate = null,
                referenceFeed = null,
                referenceFeedDate = null,
            ),
            elapsedRealtimeMs = 4000L,
        )

        assertEquals("62", result.lastFinal?.result)
    }

    @Test
    fun startup_recovery_without_feed_preserves_existing_primary_and_hero() {
        val currentFeed = feed(time = "09:29:00")
        val primary = SourceObservation(currentFeed, 100L, 100L, 0L)
        val hero = LiveHeroSnapshot("35", "1200", "19000", LIVE_SESSION_MORNING_LABEL, today.toString())
        val references = LiveReferenceState(
            reference930 = null,
            reference930Date = null,
            reference200 = null,
            reference200Date = null,
            reference930PendingDate = today,
            reference200PendingDate = today,
            reference930CompleteDate = null,
            reference200CompleteDate = null,
            referenceResetDate = null,
            referenceFeed = null,
            referenceFeedDate = null,
        )

        val reduced = LiveStateReducer.reduceStartupRecovery(
            today = today,
            scheduleTime = LocalTime.of(9, 29),
            cycleDate = previousWorkingDay(today),
            recoveredFeed = null,
            currentFinal = null,
            fallbackFinal = null,
            previousPrimary = primary,
            previousLastFinal = hero,
            references = references,
            elapsedRealtimeMs = 5000L,
        )

        assertEquals(primary, reduced.primary)
        assertEquals(hero, reduced.lastFinal)
        assertEquals(references, reduced.references)
    }
}
