package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CherryFormulaDiscoveryEngineTest {
    private fun history(vararg digits: String): Pair<List<HistoricDraw>, DrawIdentity> {
        val dates = (0L..30L).map { LocalDate.parse("2026-11-02").plusDays(it) }
            .filter { DrawSchedule.isWeekday(it) }
        val draws = digits.mapIndexed { i, digit ->
            HistoricDraw(
                dates[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                digit,
                HistoricSource.WINNER,
            )
        }
        val last = draws.last()
        val next = if (last.session == DrawSession.MORNING)
            DrawIdentity(last.date, DrawSession.EVENING)
        else {
            val day = dates.first { it > last.date }
            DrawIdentity(day, DrawSession.MORNING)
        }
        return draws to next
    }

    @Test fun discoversNonconstantLaggedArithmeticRuleWithoutRetuningEveryDraw() {
        // Synthetic, nonconstant progression: each new top digit is
        // (previous top + previous tail) % 10. The tail differs by +3.
        // This checks the engine can find a real fixed relation instead of
        // only succeeding on the degenerate all-zero fixture.
        val sequence = mutableListOf("12", "34")
        repeat(22) {
            val previous = sequence.last()
            val top = (previous[0].digitToInt() +
                previous[1].digitToInt()) % 10
            sequence.add("" + top + ((top + 3) % 10))
        }
        val (rows, target) = history(*sequence.toTypedArray())
        val expectedTop = (rows.last().digit[0].digitToInt() +
            rows.last().digit[1].digitToInt()) % 10
        val first = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        val champion = first.longest(CherryDiscoveryKind.LONE)
        assertNotNull("Nonconstant recurrence should be discovered", champion)
        assertTrue("One unchanged formula must fit a long suffix",
            champion!!.historicalStreak >= 20)
        assertEquals(expectedTop, champion.currentDigit)
        assertEquals(champion.historicalStreak, champion.matchedDraws.size)
        assertEquals(1, first.completedStages)
    }

    @Test fun discoversResultsDerivedStreaksForAllThreeKindsWithoutFutureResults() {
        val (rows, target) = history(*Array(12) { "00" })
        val result = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        assertEquals(1, result.completedStages)
        assertTrue(result.hasImmediatePreviousDraw)
        for (kind in CherryDiscoveryKind.entries) {
            val winner = result.longest(kind)
            assertNotNull(winner)
            assertEquals(11, winner!!.historicalStreak)
            assertEquals(11, winner.matchedDraws.size)
            assertEquals(0, winner.currentDigit)
            assertEquals("HISTORICAL_FIT", winner.evidenceLabel)
        }
        assertTrue(result.formulas.size <= 3 * 8)
        assertTrue(result.formulas.all { it.historicalStreak >= 3 })
    }

    @Test fun subsequentRoundsUseNewGrammarAndPreserveEarlierFits() {
        val (rows, target) = history(*Array(12) { "00" })
        val first = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        var current = first
        repeat(CherryFormulaDiscoveryEngine.STAGE_COUNT - 1) {
            val advanced = CherryFormulaDiscoveryEngine.nextRound(rows, target, current)
            assertEquals(current.completedStages + 1, advanced.completedStages)
            assertTrue(advanced.inspectedExpressions > current.inspectedExpressions)
            assertTrue(advanced.formulas.size <= 3 * 8)
            assertEquals(first.longest(CherryDiscoveryKind.LONE)?.id,
                advanced.longest(CherryDiscoveryKind.LONE)?.id)
            current = advanced
        }
        assertTrue(current.isComplete)
        assertEquals(current, CherryFormulaDiscoveryEngine.nextRound(rows, target, current))
    }

    @Test fun thousandIdenticalResultsFinishAllFiniteStagesWithoutGrowingFormulaList() {
        // Deliberately degenerate input: nearly every candidate expression
        // matches every prior draw, maximizing full-length streak evaluation.
        val weekdays = generateSequence(LocalDate.parse("2024-01-01")) { it.plusDays(1) }
            .filter { DrawSchedule.isWeekday(it) }
            .take(501).toList()
        val rows = (0 until 1000).map { index ->
            HistoricDraw(
                weekdays[index / 2],
                if (index % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00",
                HistoricSource.WINNER,
            )
        }
        val target = DrawIdentity(weekdays[500], DrawSession.MORNING)
        var session: CherryDiscoverySession? = null
        val start = System.nanoTime()
        repeat(CherryFormulaDiscoveryEngine.STAGE_COUNT) {
            session = CherryFormulaDiscoveryEngine.nextRound(rows, target, session)
        }
        val result = requireNotNull(session)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        assertTrue(result.isComplete)
        assertEquals(207, result.inspectedExpressions)
        assertTrue(result.formulas.size <= 24)
        for (kind in CherryDiscoveryKind.entries) {
            assertEquals(999, result.longest(kind)?.historicalStreak)
        }
        assertEquals(result, CherryFormulaDiscoveryEngine.nextRound(rows, target, result))
        println("CHERRY_DISCOVERY_STRESS draws=1000 expressions=207 " +
            "stages=4 elapsedMs=" + elapsedMs)
    }

    @Test fun closedOrWeekendTargetNeverStartsDiscovery() {
        // Ten draws occupy Mon 2 Nov through Fri 6 Nov 2026.
        val (rows, _) = history(*Array(10) { "00" })
        val saturday = DrawIdentity(LocalDate.parse("2026-11-07"), DrawSession.MORNING)
        val closedMonday = DrawIdentity(LocalDate.parse("2026-11-09"), DrawSession.MORNING)
        val weekendState = CherryFormulaDiscoveryEngine.nextRound(rows, saturday)
        val holidayState = CherryFormulaDiscoveryEngine.nextRound(
            rows, closedMonday, closedDates = setOf(closedMonday.date))
        assertEquals(0, weekendState.completedStages)
        assertEquals(0, holidayState.completedStages)
        assertFalse(weekendState.hasImmediatePreviousDraw)
        assertFalse(holidayState.hasImmediatePreviousDraw)
        assertTrue(weekendState.formulas.isEmpty())
        assertTrue(holidayState.formulas.isEmpty())
    }

    @Test fun callerCancellationStopsLongSearchWithoutReturningPartialState() {
        val (rows, target) = history(*Array(12) { "00" })
        var calls = 0
        try {
            CherryFormulaDiscoveryEngine.nextRound(rows, target,
                checkCancellation = {
                    calls++
                    if (calls >= 15) throw java.util.concurrent.CancellationException()
                })
            fail("Expected cooperative cancellation")
        } catch (_: java.util.concurrent.CancellationException) {
            assertTrue(calls >= 15)
        }
        // Re-running after cancellation starts from a clean immutable state.
        val clean = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        assertEquals(1, clean.completedStages)
        assertEquals(11, clean.longest(CherryDiscoveryKind.LONE)?.historicalStreak)
    }

    @Test fun cancellingContinueSearchNeverDiscardsAlreadyFoundFormula() {
        val (rows, target) = history(*Array(12) { "00" })
        val first = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        assertEquals(1, first.completedStages)
        var checks = 0
        try {
            CherryFormulaDiscoveryEngine.nextRound(rows, target, first,
                checkCancellation = {
                    if (++checks >= 5) throw java.util.concurrent.CancellationException()
                })
            fail("Expected cancellation during second stage")
        } catch (_: java.util.concurrent.CancellationException) {
            assertEquals(1, first.completedStages)
            assertEquals(11,
                first.longest(CherryDiscoveryKind.WAM)?.historicalStreak)
        }
        val resumed = CherryFormulaDiscoveryEngine.nextRound(rows, target, first)
        assertEquals(2, resumed.completedStages)
        assertEquals(first.longest(CherryDiscoveryKind.WAM)?.id,
            resumed.longest(CherryDiscoveryKind.WAM)?.id)
    }

    @Test fun anomalousWeekendHistoryCannotChangeWorkingDayDiscovery() {
        val (rows, monday) = history(*Array(10) { "00" })
        val saturday = LocalDate.parse("2026-11-07")
        val badWeekendRows = listOf(
            HistoricDraw(saturday, DrawSession.MORNING, "91", HistoricSource.HISTORY),
            HistoricDraw(saturday, DrawSession.EVENING, "18", HistoricSource.HISTORY),
        )
        val expected = CherryFormulaDiscoveryEngine.nextRound(rows, monday)
        val contaminated = CherryFormulaDiscoveryEngine.nextRound(
            rows + badWeekendRows, monday)
        assertEquals(expected, contaminated)
        val asOf = rows + HistoricDraw(monday.date,
            DrawSession.MORNING, "38", HistoricSource.WINNER)
        val walk1 = CherryDiscoveryWalkForward.replay(asOf, lastTargets = 1)
        val walk2 = CherryDiscoveryWalkForward.replay(
            asOf + badWeekendRows, lastTargets = 1)
        assertEquals(walk1, walk2)
    }

    @Test fun neverReadsTargetOrFutureDrawsWhenSearching() {
        val (rows, target) = history(*Array(10) { "12" })
        val withoutFuture = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        val future = listOf(
            HistoricDraw(target.date, target.session, "99", HistoricSource.WINNER),
            HistoricDraw(target.date.plusDays(1), DrawSession.MORNING, "88",
                HistoricSource.WINNER),
        )
        val withFuture = CherryFormulaDiscoveryEngine.nextRound(rows + future, target)
        assertEquals(withoutFuture, withFuture)
    }

    @Test fun changedHistoryResetsProgressRatherThanReusingOldDiscovery() {
        val (rows, target) = history(*Array(12) { "00" })
        val first = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        val second = CherryFormulaDiscoveryEngine.nextRound(rows, target, first)
        assertEquals(2, second.completedStages)
        val changed = rows.toMutableList()
        changed[5] = changed[5].copy(digit = "99")
        val restarted = CherryFormulaDiscoveryEngine.nextRound(changed, target, second)
        assertEquals(1, restarted.completedStages)
        assertNotEquals(second.historySignature, restarted.historySignature)
    }

    @Test fun missingImmediateSlotDoesNotFabricateAStreak() {
        val (rows, target) = history(*Array(10) { "00" })
        val missing = rows.dropLast(1)
        val result = CherryFormulaDiscoveryEngine.nextRound(missing, target)
        assertEquals(0, result.completedStages)
        assertFalse(result.hasImmediatePreviousDraw)
        assertTrue(result.formulas.isEmpty())
    }

    @Test fun genuineSingleDigitHitsAreNotReportedAsExactPairWins() {
        val (rows, target) = history(*Array(10) { "12" })
        val result = CherryFormulaDiscoveryEngine.nextRound(rows, target)
        assertTrue(result.longest(CherryDiscoveryKind.LONE)!!.historicalStreak >= 3)
        // Discovery returns one digit and its matching category, not a
        // fabricated exact-pair winning forecast or prospectively verified hit.
        assertTrue(result.formulas.all { it.currentDigit in 0..9 })
        assertTrue(result.formulas.all { it.evidenceLabel == "HISTORICAL_FIT" })
    }
}
