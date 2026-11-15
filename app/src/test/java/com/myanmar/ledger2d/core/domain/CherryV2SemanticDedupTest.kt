package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

class CherryV2SemanticDedupTest {
    private val days = generateSequence(LocalDate.parse("2026-09-01")) {
        it.plusDays(1)
    }.filter { DrawSchedule.isWeekday(it) }.take(17).toList()
    private val target = DrawIdentity(days[12], DrawSession.MORNING)
    private val history = (0 until 24).map { number ->
        val top = number % 10
        HistoricDraw(
            days[number / 2],
            if (number % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
            "${top}4", HistoricSource.WINNER,
        )
    }
    private val one = BigInteger.ONE

    @Test fun constantShiftedExpressionsHaveSameBehaviorOverExactlySameHistory() {
        val context = CherryV2HistoricalEvaluator.prepare(history, target)
        val top = CherryV2Expression.Feature(one, CherryV2Expression.Part.TOP)
        val tail = CherryV2Expression.Feature(one, CherryV2Expression.Part.TAIL)
        val shifted = CherryV2Expression.Binary(
            CherryV2Expression.Operator.ADD, top, tail)
        val base = requireNotNull(CherryV2HistoricalEvaluator.trace(context, top))
        val variant = requireNotNull(
            CherryV2HistoricalEvaluator.trace(context, shifted))
        assertEquals(base.behaviorKey, variant.behaviorKey)
        assertNotEquals(base.currentBase, variant.currentBase)
        assertEquals(base.historicalBases.size, variant.historicalBases.size)
        val kind = CherryDiscoveryKind.LONE
        val initial = CherryV2HistoricalEvaluator.evaluateTrace(
            context, kind, BigInteger.ZERO, base)
        val shiftedResults = CherryV2HistoricalEvaluator.evaluateTrace(
            context, kind, one, variant)
        val byStreak = initial.map { it.historicalStreak }.toSet()
        assertEquals(byStreak, shiftedResults.map { it.historicalStreak }.toSet())
    }

    @Test fun differentOutputTracesDoNotCollapseAndChangesInvalidateBehavior() {
        val context = CherryV2HistoricalEvaluator.prepare(history, target)
        val top = CherryV2Expression.Feature(one, CherryV2Expression.Part.TOP)
        val repeated = CherryV2Expression.Binary(
            CherryV2Expression.Operator.MULTIPLY, top, top)
        val a = requireNotNull(CherryV2HistoricalEvaluator.trace(context, top))
        val b = requireNotNull(CherryV2HistoricalEvaluator.trace(context, repeated))
        assertNotEquals(a.behaviorKey, b.behaviorKey)

        val changed = history.toMutableList()
        changed[20] = changed[20].copy(digit = "84")
        val revised = CherryV2HistoricalEvaluator.prepare(changed, target)
        assertNotEquals(context.historySignature, revised.historySignature)
    }

    @Test fun searchSkipsEquivalentBehaviorAndKeepsRecentWindowBounded() {
        val context = CherryV2HistoricalEvaluator.prepare(history, target)
        var now = 0L
        fun clock(): Long { now += 1_000L; return now }
        val first = CherryV2TimedSearch.run(
            context, CherryDiscoveryKind.LONE,
            timeBudgetNanos = 2_000_000L,
            monotonicNanos = ::clock,
            onBatchBoundary = {},
        )
        assertEquals(CherryV2TimedSearch.StopReason.TIME_LIMIT, first.stopReason)
        assertTrue(first.inspectedThisBatch >= 3)
        assertTrue("top1/tail1 are shifts on this dataset",
            first.equivalentBehaviorsSkipped > 0)
        assertTrue(first.cursor.recentBehaviorKeys.size <=
            CherryV2StreakRegistry.BEHAVIOR_WINDOW)
        assertEquals(first.cursor.recentBehaviorKeys.size,
            first.cursor.recentBehaviorKeys.toSet().size)

        now = 0L
        val continued = CherryV2TimedSearch.run(
            context, CherryDiscoveryKind.LONE, previous = first.cursor,
            timeBudgetNanos = 2_000_000L,
            monotonicNanos = ::clock,
            onBatchBoundary = {},
        )
        assertTrue(continued.cursor.nextIndex > first.cursor.nextIndex)
        assertTrue(continued.cursor.firstByStreak.keys.containsAll(
            first.cursor.firstByStreak.keys))
    }

    @Test fun checkpointRejectsUnboundedOrRepeatedEquivalenceSignatures() {
        val context = CherryV2HistoricalEvaluator.prepare(history, target)
        val cursor = CherryV2StreakRegistry(
            target, CherryDiscoveryKind.BREAK, context.historySignature)
        try {
            cursor.withRecentBehaviors(listOf("abc", "abc"))
            fail("Cannot persist duplicate behavior signatures")
        } catch (_: IllegalArgumentException) {}
        try {
            cursor.withRecentBehaviors(List(
                CherryV2StreakRegistry.BEHAVIOR_WINDOW + 1) { "trace$it" })
            fail("Cannot persist unlimited behavior signatures")
        } catch (_: IllegalArgumentException) {}
        assertTrue(cursor.recentBehaviorKeys.isEmpty())
    }
    @Test fun threeStreakPrefilterPrunesImpossibleBreakButRetainsDirectTen() {
        val context = CherryV2HistoricalEvaluator.prepare(history, target)
        val top = CherryV2Expression.Feature(one, CherryV2Expression.Part.TOP)
        val constantTail = CherryV2Expression.Feature(
            one, CherryV2Expression.Part.TAIL)
        val full = requireNotNull(CherryV2HistoricalEvaluator.trace(
            context, constantTail))
        val prefiltered = requireNotNull(CherryV2HistoricalEvaluator.trace(
            context, constantTail, candidateKind = CherryDiscoveryKind.BREAK))
        assertTrue(full.historicalBases.size > 10)
        assertEquals(3, prefiltered.historicalBases.size)
        assertTrue(CherryV2HistoricalEvaluator.evaluateTrace(
            context, CherryDiscoveryKind.BREAK, one, prefiltered).isEmpty())

        val eligible = requireNotNull(CherryV2HistoricalEvaluator.trace(
            context, top, candidateKind = CherryDiscoveryKind.BREAK))
        assertTrue(eligible.historicalBases.size > 10)
        val matches = CherryV2HistoricalEvaluator.evaluateTrace(
            context, CherryDiscoveryKind.BREAK, BigInteger.ZERO, eligible)
        assertTrue(matches.any { it.historicalStreak >= 10 })
    }

}
