package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Category-specific reference counts under an IDEALIZED UNIFORM 00..99
 * outcome model. This is a mathematical null benchmark only; it is neither
 * an estimated market distribution nor evidence of predictive usefulness.
 */
class CherryDiscoveryChanceBaselineTest {
    @Test fun exhaustiveUniformReferenceCountsAreNotConfusedAcrossCategories() {
        val outcomes = (0..99).map { it.toString().padStart(2, '0') }
        for (digit in 0..9) {
            val count = CherryDiscoveryKind.entries.associateWith { kind ->
                outcomes.count { actual ->
                    CherryDiscoveryWalkForward.matches(kind, digit, actual)
                }
            }
            assertEquals("LONE of digit $digit", 19,
                count.getValue(CherryDiscoveryKind.LONE))
            assertEquals("WAM of digit $digit or power+5", 36,
                count.getValue(CherryDiscoveryKind.WAM))
            assertEquals("BREAK modulo ten", 10,
                count.getValue(CherryDiscoveryKind.BREAK))
        }
    }

    @Test fun categoriesAreDigitOrBreakHitsNotExactPairs() {
        assertTrue(CherryDiscoveryWalkForward.matches(
            CherryDiscoveryKind.LONE, 2, "27"))
        assertTrue(CherryDiscoveryWalkForward.matches(
            CherryDiscoveryKind.WAM, 2, "79"))
        assertTrue(CherryDiscoveryWalkForward.matches(
            CherryDiscoveryKind.BREAK, 6, "24"))
        assertFalse(CherryDiscoveryWalkForward.matches(
            CherryDiscoveryKind.BREAK, 7, "24"))
    }
}
