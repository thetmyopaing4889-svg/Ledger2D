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
