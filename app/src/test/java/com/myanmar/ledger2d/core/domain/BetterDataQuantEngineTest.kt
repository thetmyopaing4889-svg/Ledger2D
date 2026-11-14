package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BetterDataQuantEngineTest {
    private fun draw(date: String, session: DrawSession, value: String) =
        HistoricDraw(LocalDate.parse(date), session, value, HistoricSource.WINNER)

    @Test fun onlyEarlierVerifiedSlotsMayFeedPrediction() {
        val target = DrawIdentity(LocalDate.parse("2026-11-10"), DrawSession.EVENING)
        val data = listOf(
            draw("2026-11-09", DrawSession.MORNING, "23"),
            draw("2026-11-09", DrawSession.EVENING, "14"),
            draw("2026-11-10", DrawSession.MORNING, "17"),
            draw("2026-11-10", DrawSession.EVENING, "99"),
            draw("2026-11-11", DrawSession.MORNING, "78")
        )
        val report = BetterDataQuantEngine.calculate(data, target)
        assertEquals(3, report.observedDraws)
        assertFalse(report.current.any { it.traces.any { t -> t.id == "s6-78" } })
    }

    @Test fun ruleAndFormulaModesRemainIndependent() {
        val target = DrawIdentity(LocalDate.parse("2026-11-10"), DrawSession.MORNING)
        val report = BetterDataQuantEngine.calculate(listOf(
            draw("2026-11-07", DrawSession.MORNING, "54"),
            draw("2026-11-09", DrawSession.EVENING, "14")
        ), target)
        val byRule = report.select(BetterStrategy.CURRENT, BetterMode.RULES)
        val byFormula = report.select(BetterStrategy.CURRENT, BetterMode.FORMULAS)
        assertTrue(byRule.any { it.number == "17" })
        assertTrue(byFormula.isNotEmpty())
        assertTrue(byRule.all { it.hasRule })
        assertTrue(byFormula.all { it.hasFormula })
        assertTrue(report.select(BetterStrategy.CURRENT, BetterMode.OVERLAP)
            .all { it.hasFormula && it.hasRule })
    }

    @Test fun sectionTwoCutFlagsConflictInsteadOfErasingEvidence() {
        val target = DrawIdentity(LocalDate.parse("2026-11-10"), DrawSession.MORNING)
        val report = BetterDataQuantEngine.calculate(listOf(
            draw("2026-11-09", DrawSession.MORNING, "14"),
            draw("2026-11-09", DrawSession.EVENING, "01")
        ), target)
        val cut = report.current.single { it.number == "68" }
        assertTrue(cut.cuts.any { it.contains("+7") })
        assertTrue(report.cuts().contains(cut))
        assertFalse(report.select(BetterStrategy.CURRENT, BetterMode.RULES).contains(cut))
    }

    @Test fun noInventedPairsWithoutPreviousData() {
        val target = DrawIdentity(LocalDate.parse("2026-11-10"), DrawSession.MORNING)
        val result = BetterDataQuantEngine.calculate(emptyList(), target)
        assertEquals(0, result.observedDraws)
        assertTrue(result.current.isEmpty())
        assertTrue(result.alive.isEmpty())
        assertTrue(result.select(BetterStrategy.CURRENT, BetterMode.OVERLAP).isEmpty())
    }

    @Test fun morningSourceNotBorrowedFromFutureEvening() {
        val day = LocalDate.parse("2026-11-10")
        val target = DrawIdentity(day, DrawSession.MORNING)
        val result = BetterDataQuantEngine.calculate(listOf(
            draw("2026-11-09", DrawSession.EVENING, "54"),
            draw("2026-11-10", DrawSession.EVENING, "05")
        ), target)
        assertEquals(1, result.observedDraws)
        assertFalse(result.current.any { it.traces.any { trace -> trace.id == "s7-am05" } })
    }
}
