package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate
import java.util.concurrent.CancellationException

class CherryV2GuidedScheduleTest {
    private fun at(n: Long) = CherryV2GuidedSchedule.expressionAt(BigInteger.valueOf(n))

    @Test fun firstNineOriginalFeaturesAreUnchangedAndSimpleLaneStillExpands() {
        repeat(9) { i ->
            assertEquals(
                CherryV2ExpandableGrammar.expressionAt(BigInteger.valueOf(i.toLong()))
                    .structuralKey,
                at(i.toLong()).structuralKey,
            )
        }
        // The old raw grammar's slot 9 was top4, which cannot be evaluated
        // under the approved lag-3 policy. V3 instead starts an indefinitely
        // expandable family of valid nested lag-1..3 expressions.
        assertEquals("top4",
            CherryV2ExpandableGrammar.expressionAt(BigInteger.valueOf(9))
                .structuralKey)
        assertEquals("(top1+top1)", at(9).structuralKey)
        assertEquals("(top1-top1)", at(13).structuralKey)
        assertEquals("top1", at(0).structuralKey)
    }

    @Test fun crossLagAndNonlinearExpressionsAppearWithoutWaitingForRawGrammar() {
        assertEquals(CherryV2GuidedSchedule.Lane.SIMPLE,
            CherryV2GuidedSchedule.laneAt(BigInteger.valueOf(9)))
        assertEquals(CherryV2GuidedSchedule.Lane.CROSS_LAG,
            CherryV2GuidedSchedule.laneAt(BigInteger.valueOf(10)))
        assertEquals(CherryV2GuidedSchedule.Lane.NESTED,
            CherryV2GuidedSchedule.laneAt(BigInteger.valueOf(11)))
        assertEquals("(top1+top2)", at(10).structuralKey)
        assertEquals("(top1+top2)",
            CherryV2ExpandableGrammar.expressionAt(BigInteger.valueOf(73))
                .structuralKey) // raw linear search finds this much later
        assertEquals("((top1+tail1)+top2)", at(11).structuralKey)
        // Early guided candidates mix distinct lags and components.
        assertEquals("(top1+break2)", at(370).structuralKey)
        assertEquals(CherryV2GuidedSchedule.Lane.BALANCED,
            CherryV2GuidedSchedule.laneAt(BigInteger.valueOf(12)))
        assertTrue(at(12).structuralKey.count { it == '(' } >= 3)
        val first = (0L..600L).map { at(it).structuralKey }
        assertTrue(first.any { it.contains("top2") && it.contains("tail1") })
        assertTrue(first.any { it.count { ch -> ch == '(' } >= 2 })
        assertTrue(first.size > 207)
    }

    @Test fun everyLagThreeEligibleExpressionEventuallyHasAStableSimpleLaneSlot() {
        // V3 retains an unbounded SIMPLE lane, now built only from lag-1..3
        // features; it cannot replay incompatible V2 raw-index candidates.
        for (raw in listOf(9L, 10L, 20L, 207L, 828L, 10_000L)) {
            val slot = BigInteger.valueOf(9L + 4L * (raw - 9L))
            assertEquals(CherryV2GuidedSchedule.Lane.SIMPLE,
                CherryV2GuidedSchedule.laneAt(slot))
            assertEquals(
                CherryV2EligibleGrammar.expressionAt(BigInteger.valueOf(raw))
                    .structuralKey,
                CherryV2GuidedSchedule.expressionAt(slot).structuralKey,
            )
        }
    }

    @Test fun everyGuidedCandidateUsesOnlyApprovedThreeLagsEvenFarPast207() {
        fun maxLag(exp: CherryV2Expression): BigInteger = when (exp) {
            is CherryV2Expression.Feature -> exp.lag
            is CherryV2Expression.Complement -> maxLag(exp.inner)
            is CherryV2Expression.Binary -> maxOf(maxLag(exp.left), maxLag(exp.right))
        }
        for (slot in 0L..2100L) {
            val expr = at(slot)
            assertTrue("Unsupported lag at formula slot $slot: ${expr.structuralKey}",
                maxLag(expr) <= BigInteger.valueOf(3))
        }
        val deep = at(100_000L)
        assertTrue(maxLag(deep) <= BigInteger.valueOf(3))
        assertEquals(4, CherryV2GuidedSchedule.VERSION)
    }

    @Test fun crossLagCandidateDirectlyFitsLongRecentStreakWithoutLadder() {
        val sequence = mutableListOf("12", "34")
        repeat(28) {
            // Truly nonconstant Fibonacci-like recurrence: the next top
            // digit equals top1+top2, modulo 10.
            val top = (sequence.last()[0].digitToInt() +
                sequence[sequence.lastIndex - 1][0].digitToInt()) % 10
            sequence.add("$top${(top + 1) % 10}")
        }
        val days = generateSequence(LocalDate.parse("2026-08-03")) {
            it.plusDays(1)
        }.filter { DrawSchedule.isWeekday(it) }.take(16).toList()
        val draws = sequence.mapIndexed { i, digits ->
            HistoricDraw(days[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                digits, HistoricSource.WINNER)
        }
        val target = DrawIdentity(days[15], DrawSession.MORNING)
        val context = CherryV2HistoricalEvaluator.prepare(draws, target)
        val slot = BigInteger.valueOf(10)
        val expression = CherryV2GuidedSchedule.expressionAt(slot)
        val found = CherryV2HistoricalEvaluator.evaluate(
            context, CherryDiscoveryKind.LONE, slot, expression)
        assertTrue(found.any { it.offset == 0 && it.historicalStreak >= 10 })
    }

    @Test fun scheduleIsDeterministicAndHonorsCancellationAndInputBounds() {
        for (n in listOf(0L, 9L, 10L, 11L, 280L, 5000L, 200_000L)) {
            assertEquals(at(n).structuralKey, at(n).structuralKey)
        }
        assertNull(at(10).evaluate(emptyList()))
        try {
            CherryV2GuidedSchedule.expressionAt(BigInteger.valueOf(-1))
            fail("Negative search cursor not allowed")
        } catch (_: IllegalArgumentException) {}
        try {
            CherryV2GuidedSchedule.expressionAt(BigInteger.valueOf(280)) {
                throw CancellationException("cancel")
            }
            fail("Schedule must propagate cancel")
        } catch (_: CancellationException) {}
        assertEquals(CherryV2GuidedSchedule.VERSION,
            CherryV2ExpandableGrammar.VERSION)
    }
}
