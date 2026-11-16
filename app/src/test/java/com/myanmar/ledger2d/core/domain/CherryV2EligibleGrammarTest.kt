package com.myanmar.ledger2d.core.domain

import java.math.BigInteger
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CherryV2EligibleGrammarTest {
    private fun at(n: Long) =
        CherryV2EligibleGrammar.expressionAt(BigInteger.valueOf(n))

    private fun lags(expr: CherryV2Expression): List<Int> = when (expr) {
        is CherryV2Expression.Feature -> listOf(expr.lag.toInt())
        is CherryV2Expression.Complement -> lags(expr.inner)
        is CherryV2Expression.Binary ->
            lags(expr.left) + lags(expr.right)
    }

    @Test fun firstNineRemainLegacyAndLaterExpressionsNeverDemandLagFour() {
        for (n in 0L..8L) {
            assertEquals(CherryV2ExpandableGrammar.expressionAt(
                BigInteger.valueOf(n)).structuralKey, at(n).structuralKey)
        }
        assertEquals("(top1+top1)", at(9).structuralKey)
        for (n in 0L..1000L) {
            val used = lags(at(n))
            assertTrue("Candidate $n has no input", used.isNotEmpty())
            assertTrue("Candidate $n uses out-of-policy prior data",
                used.all { it in 1..3 })
        }
    }

    @Test fun grammarIsStillLazyOpenEndedAndStructurallyDiverseAfter207() {
        val first = (0L..600L).map { at(it).structuralKey }
        assertEquals("No duplicate syntax tree IDs in this finite sample",
            first.size, first.toSet().size)
        assertTrue(first.size > 207)
        for (n in listOf(828L, 10_000L, 100_000L)) {
            assertTrue(lags(at(n)).all { it in 1..3 })
        }
        try {
            CherryV2EligibleGrammar.expressionAt(BigInteger.valueOf(10_000)) {
                throw CancellationException("cancel")
            }
            fail("Generation must respect cooperative cancellation")
        } catch (_: CancellationException) {}
    }
}
