package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate
import java.util.concurrent.CancellationException

class CherryV2HistoricalEvaluatorTest {
    private val dates = generateSequence(LocalDate.parse("2026-09-01")) { it.plusDays(1) }
        .filter { DrawSchedule.isWeekday(it) }.take(24).toList()
    private val target = DrawIdentity(dates[20], DrawSession.MORNING)
    private val index = BigInteger.ZERO

    private fun ascendingHistory(): List<HistoricDraw> =
        (0 until 40).map { position ->
            val top = position % 10
            HistoricDraw(
                dates[position / 2],
                if (position % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "$top${(top + 4) % 10}",
                HistoricSource.WINNER,
            )
        }

    @Test fun nonconstantRecentResultsPermitDirectTenPlusWithoutTuningAnOffsetPerDraw() {
        val context = CherryV2HistoricalEvaluator.prepare(ascendingHistory(), target)
        assertFalse(context.blocked)
        val formula = CherryV2ExpandableGrammar.expressionAt(index)
        val matches = CherryV2HistoricalEvaluator.evaluate(
            context, CherryDiscoveryKind.LONE, index, formula)
        val offsetOne = matches.single { it.offset == 1 }
        assertTrue(offsetOne.historicalStreak >= 10)
        assertEquals(0, offsetOne.predictedDigit) // most recent top 9, +1
        assertEquals("HISTORICAL_FIT", offsetOne.evidenceLabel)
        assertEquals("v2:0+1@LONE", offsetOne.formulaId)
        val saved = CherryV2StreakRegistry(
            target, CherryDiscoveryKind.LONE, context.historySignature)
            .inspectedBatch(index, matches)
        assertTrue(saved.recommended()!!.historicalStreak >= 10)
        assertEquals(BigInteger.ONE, saved.nextIndex)
    }

    @Test fun futureResultAndTargetResultAreNeverUsedToDiscoverOrFit() {
        val old = ascendingHistory()
        val before = CherryV2HistoricalEvaluator.prepare(old, target)
        val withFuture = CherryV2HistoricalEvaluator.prepare(old + listOf(
            HistoricDraw(target.date, target.session, "99", HistoricSource.WINNER),
            HistoricDraw(target.date.plusDays(1), DrawSession.MORNING,
                "88", HistoricSource.WINNER),
        ), target)
        assertEquals(before.historySignature, withFuture.historySignature)
        assertEquals(before.prior, withFuture.prior)
        assertEquals(
            CherryV2HistoricalEvaluator.evaluate(before, CherryDiscoveryKind.LONE,
                index, CherryV2ExpandableGrammar.expressionAt(index)),
            CherryV2HistoricalEvaluator.evaluate(withFuture, CherryDiscoveryKind.LONE,
                index, CherryV2ExpandableGrammar.expressionAt(index)),
        )
    }

    @Test fun missingImmediateSlotBlocksAndMissingMiddleSlotEndsStreak() {
        val original = ascendingHistory()
        val missingLast = CherryV2HistoricalEvaluator.prepare(original.dropLast(1), target)
        assertTrue(missingLast.blocked)
        assertTrue(CherryV2HistoricalEvaluator.evaluate(missingLast,
            CherryDiscoveryKind.LONE, index,
            CherryV2ExpandableGrammar.expressionAt(index)).isEmpty())

        val middleMissing = CherryV2HistoricalEvaluator.prepare(
            original.filterIndexed { position, _ -> position != 35 }, target)
        assertEquals(4, middleMissing.prior.size)
        val scored = CherryV2HistoricalEvaluator.evaluate(middleMissing,
            CherryDiscoveryKind.LONE, index,
            CherryV2ExpandableGrammar.expressionAt(index))
        assertTrue(scored.all { it.historicalStreak <= 3 })
    }

    @Test fun onlyPrioritizedConfirmedRowsInfluenceTheInputs() {
        val original = ascendingHistory()
        val last = original.last()
        val weaker = last.copy(digit = "00", source = HistoricSource.HISTORY)
        val updated = CherryV2HistoricalEvaluator.prepare(
            listOf(weaker) + original.reversed(), target)
        assertEquals(last, updated.prior.first())
    }

    @Test fun deepLagNeedsContextAndSearchRemainsBounded() {
        val original = ascendingHistory()
        val context = CherryV2HistoricalEvaluator.prepare(original, target)
        val tooDeep = BigInteger.valueOf(219)
        val formula = CherryV2ExpandableGrammar.expressionAt(tooDeep)
        assertEquals("top14", formula.structuralKey)
        assertTrue(CherryV2HistoricalEvaluator.evaluate(context,
            CherryDiscoveryKind.LONE, tooDeep, formula).isEmpty())
        assertTrue(context.prior.size <= CherryV2HistoricalEvaluator.MAX_SCORED +
            CherryV2HistoricalEvaluator.MAX_LAG)
        val shallow = CherryV2ExpandableGrammar.expressionAt(index)
        assertTrue(CherryV2HistoricalEvaluator.evaluate(context,
            CherryDiscoveryKind.WAM, index, shallow).all {
                it.kind == CherryDiscoveryKind.WAM })
        assertTrue(CherryV2HistoricalEvaluator.evaluate(context,
            CherryDiscoveryKind.BREAK, index, shallow).all {
                it.kind == CherryDiscoveryKind.BREAK })
    }

    @Test fun oldCorrectedDrawInvalidatesOnlyRelevantHistorySnapshot() {
        val old = ascendingHistory()
        val context = CherryV2HistoricalEvaluator.prepare(old, target)
        val changed = old.toMutableList()
        changed[36] = changed[36].copy(digit = "99")
        val latest = CherryV2HistoricalEvaluator.prepare(changed, target)
        assertNotEquals(context.historySignature, latest.historySignature)
    }

    @Test fun cancelledNormalizationAndScoringPropagateWithoutMutatingHistory() {
        val draws = ascendingHistory()
        try {
            CherryV2HistoricalEvaluator.prepare(draws, target,
                checkCancellation = { throw CancellationException("stop") })
            fail("Must cancel")
        } catch (_: CancellationException) {}
        val context = CherryV2HistoricalEvaluator.prepare(draws, target)
        try {
            CherryV2HistoricalEvaluator.evaluate(context,
                CherryDiscoveryKind.LONE, index,
                CherryV2ExpandableGrammar.expressionAt(index),
                checkCancellation = { throw CancellationException("stop") })
            fail("Must cancel")
        } catch (_: CancellationException) {}
        assertEquals(40, draws.size)
    }
}
