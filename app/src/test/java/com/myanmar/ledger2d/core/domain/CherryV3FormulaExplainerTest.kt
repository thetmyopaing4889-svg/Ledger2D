package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigInteger
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryV3FormulaExplainerTest {
    private val weekdays = listOf("2026-11-11", "2026-11-12", "2026-11-13")
        .map(LocalDate::parse)
    private val target = DrawIdentity(LocalDate.parse("2026-11-16"),
        DrawSession.MORNING)

    private fun context(pair: String): CherryV2HistoricalEvaluator.Context {
        val past = weekdays.flatMap { date ->
            listOf(DrawSession.MORNING, DrawSession.EVENING).map { session ->
                HistoricDraw(date, session, pair, HistoricSource.WINNER)
            }
        }
        return CherryV2HistoricalEvaluator.prepare(past, target)
    }

    @Test fun simpleFormulaShowsPreviousVerifiedHitAndUpcomingDigitWithoutTargetLeakage() {
        val ctx = context("42")
        val index = BigInteger.ZERO // top1
        val formula = CherryV2StreakMatch(
            target, CherryDiscoveryKind.LONE, ctx.historySignature,
            index, "top1", 2, 3, offset = 8)
        val explained = requireNotNull(
            CherryV3FormulaExplainer.explain(ctx, formula))
        assertEquals(2, explained.current.digit)
        assertNull("Upcoming result must never be invented", explained.current.actualPair)
        assertEquals(2, explained.previous.digit)
        assertEquals("42", explained.previous.actualPair)
        assertEquals(
            DrawIdentity(LocalDate.parse("2026-11-13"), DrawSession.EVENING),
            explained.previous.draw)
        assertEquals(target, explained.current.draw)
        assertTrue(explained.current.inputs.any {
            it.contains("2026-11-13") && it.contains("42") && it.contains("ထိပ်လုံး 4")
        })
        assertTrue(explained.current.steps.any {
            it.contains("4 + 8 = 12") && it.contains("2")
        })
        assertTrue("Previous replay must not use Friday evening (its own result)",
            explained.previous.inputs.none { it.contains("ညနေ") })
    }

    @Test fun compoundFormulaExplainsModuloAtIntermediateNode() {
        val ctx = context("74")
        val index = BigInteger.TEN // top1 + top2 (guided CROSS_LAG)
        val expression = CherryV2GuidedSchedule.expressionAt(index)
        assertEquals("(top1+top2)", expression.structuralKey)
        val formula = CherryV2StreakMatch(
            target, CherryDiscoveryKind.LONE, ctx.historySignature,
            index, expression.structuralKey, 4, 3)
        val explained = requireNotNull(
            CherryV3FormulaExplainer.explain(ctx, formula))
        for (sample in listOf(explained.previous, explained.current)) {
            assertEquals(4, sample.digit)
            assertEquals(2, sample.inputs.size)
            assertTrue(sample.steps.any {
                it.contains("7 + 7 = 14") &&
                    it.contains("နောက်ဆုံးလုံး 4")
            })
        }
        assertEquals("74", explained.previous.actualPair)
    }

    @Test fun rejectStaleHistoryWrongFormulaAndIncorrectPrediction() {
        val ctx = context("42")
        val matching = CherryV2StreakMatch(
            target, CherryDiscoveryKind.LONE, ctx.historySignature,
            BigInteger.ZERO, "top1", 2, 3, offset = 8)
        assertNotNull(CherryV3FormulaExplainer.explain(ctx, matching))
        assertNull(CherryV3FormulaExplainer.explain(
            ctx, matching.copy(predictedDigit = 9)))
        assertNull(CherryV3FormulaExplainer.explain(
            ctx, matching.copy(expression = "top2")))
        assertNull(CherryV3FormulaExplainer.explain(
            ctx, matching.copy(historySignature = "changed")))
        assertNull(CherryV3FormulaExplainer.explain(
            ctx, matching.copy(offset = 0, predictedDigit = 4)))
        // Last case: the current output looks right, but the previous hit
        // could be wrong for another fixture. Here offset 0 also matches 42.
        // So the preview is only gated by verified replay, not invented wins.
    }
}
