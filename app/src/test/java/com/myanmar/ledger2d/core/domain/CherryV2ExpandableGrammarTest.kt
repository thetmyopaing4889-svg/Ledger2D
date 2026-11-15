package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.CancellationException

class CherryV2ExpandableGrammarTest {
    private fun index(i: Long) = BigInteger.valueOf(i)

    @Test fun firstFeaturesMatchPriorDrawPositionsAndBreakMath() {
        val rows = listOf("12", "34", "56") // newest first, no target result
        assertEquals("top1", CherryV2ExpandableGrammar.expressionAt(index(0)).structuralKey)
        assertEquals(1, CherryV2ExpandableGrammar.expressionAt(index(0)).evaluate(rows))
        assertEquals(2, CherryV2ExpandableGrammar.expressionAt(index(1)).evaluate(rows))
        assertEquals(3, CherryV2ExpandableGrammar.expressionAt(index(2)).evaluate(rows))
        assertEquals(3, CherryV2ExpandableGrammar.expressionAt(index(3)).evaluate(rows))
        assertEquals(1, CherryV2ExpandableGrammar.expressionAt(index(8)).evaluate(rows))
    }

    @Test fun grammarContinuesPast207AndPreservesUniqueStructuralIdentifiers() {
        val keys = (0L..1000L).map {
            CherryV2ExpandableGrammar.expressionAt(index(it)).structuralKey
        }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.size > 207)
        assertEquals(index(1001), CherryV2ExpandableGrammar.next(index(1000)))
        assertFalse(keys.last().isBlank())
    }

    @Test fun nestedExpressionsAndOperatorsUseOnlyPastInputs() {
        val rows = listOf("12", "34", "56", "78")
        // Index 9 is the first lag-4 feature; index 15 encodes (9-top1).
        assertEquals("top4", CherryV2ExpandableGrammar.expressionAt(index(9)).structuralKey)
        assertEquals(7, CherryV2ExpandableGrammar.expressionAt(index(9)).evaluate(rows))
        assertEquals("(9-top1)", CherryV2ExpandableGrammar.expressionAt(index(15)).structuralKey)
        assertEquals(8, CherryV2ExpandableGrammar.expressionAt(index(15)).evaluate(rows))
        assertEquals(1, CherryV2ExpandableGrammar.expressionAt(index(12)).evaluate(rows))
        assertNull(CherryV2ExpandableGrammar.expressionAt(index(9)).evaluate(rows.take(3)))
        assertNull(CherryV2ExpandableGrammar.expressionAt(index(0)).evaluate(listOf("X2")))
        assertEquals(1, CherryV2ExpandableGrammar.expressionAt(index(0))
            .evaluate(rows + "99")) // An older draw cannot alter a one-lag output.
    }

    @Test fun largeIndicesCanBeDecodedWithoutFiniteStopOrEagerLists() {
        val huge = BigInteger("1000000000000000000000000000000")
        val formula = CherryV2ExpandableGrammar.expressionAt(huge)
        assertTrue(formula.structuralKey.isNotEmpty())
        assertEquals(huge.add(BigInteger.ONE), CherryV2ExpandableGrammar.next(huge))
    }

    @Test fun cancellationsArePropagatedAndNoMutableProgressEscapes() {
        try {
            CherryV2ExpandableGrammar.expressionAt(index(500000)) {
                throw CancellationException("stop")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // No hidden cursor state is mutated by expressionAt.
        }
        assertEquals(
            CherryV2ExpandableGrammar.expressionAt(index(500000)).structuralKey,
            CherryV2ExpandableGrammar.expressionAt(index(500000)).structuralKey,
        )
    }

    @Test fun invalidIndicesAndMissingInputDoNotProduceFakeDigits() {
        try {
            CherryV2ExpandableGrammar.expressionAt(BigInteger.valueOf(-1))
            fail("Negative grammar index must be rejected")
        } catch (_: IllegalArgumentException) {}
        assertNull(CherryV2ExpandableGrammar.expressionAt(index(0)).evaluate(emptyList()))
        assertNull(CherryV2ExpandableGrammar.expressionAt(index(9)).evaluate(listOf("12")))
    }
}
