package com.myanmar.ledger2d.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CherryV3PairPlannerTest {
    private fun lone(d: Int, s: Int = 10) =
        CherryV3FormulaSelection(CherryDiscoveryKind.LONE, d, s, "l$d")
    private fun wam(d: Int, s: Int = 5) =
        CherryV3FormulaSelection(CherryDiscoveryKind.WAM, d, s, "w$d")
    private fun br(d: Int, s: Int = 7) =
        CherryV3FormulaSelection(CherryDiscoveryKind.BREAK, d, s, "b$d")

    @Test fun loneAndBreakReturnDirectAndReverse() {
        assertEquals(listOf("85", "58"),
            CherryV3PairPlanner.plan(listOf(lone(8), br(3))).main)
    }

    @Test fun selectedStarsPrioritizeMainAndKeepEightOtherPairs() {
        val result = CherryV3PairPlanner.plan(listOf(lone(8), wam(2), br(3)))
        assertEquals(listOf(CherryDiscoveryKind.LONE, CherryDiscoveryKind.BREAK),
            result.priorityKinds)
        assertEquals(listOf("85", "58"), result.main)
        assertEquals(listOf("82", "28", "87", "78", "21", "12", "76", "67"),
            result.remaining)
        assertEquals(10, result.all.toSet().size)
        assertFalse("27" in result.all)
        assertFalse("72" in result.all)
    }

    @Test fun wamAndBreakCanBeMainWhenTheyHaveHighestStreaks() {
        val result = CherryV3PairPlanner.plan(listOf(lone(8, 5), wam(2, 7), br(3, 10)))
        assertEquals(listOf(CherryDiscoveryKind.BREAK, CherryDiscoveryKind.WAM),
            result.priorityKinds)
        assertEquals(listOf("21", "12", "76", "67"), result.main)
        assertEquals(listOf("85", "58", "82", "28", "87", "78"), result.remaining)
    }

    @Test fun loneAndWamIncludePowerDigitButNoIndependentPowerPair() {
        assertEquals(listOf("83", "38", "88"),
            CherryV3PairPlanner.plan(listOf(lone(8), wam(3))).main)
    }

    @Test fun doubleIsNotRepeatedAndOneFormulaHasNoPairs() {
        assertEquals(listOf("88"),
            CherryV3PairPlanner.plan(listOf(lone(8), br(6))).main)
        assertTrue(CherryV3PairPlanner.plan(listOf(lone(8))).all.isEmpty())
        assertTrue(CherryV3PairPlanner.plan(emptyList()).all.isEmpty())
    }

    @Test fun sameStarTierUsesStreakThenStableKindOrder() {
        val result = CherryV3PairPlanner.plan(listOf(lone(8, 7), wam(2, 8), br(3, 7)))
        assertEquals(listOf(CherryDiscoveryKind.WAM, CherryDiscoveryKind.LONE),
            result.priorityKinds)
        val tied = CherryV3PairPlanner.plan(listOf(lone(8, 7), wam(2, 7), br(3, 7)))
        assertEquals(listOf(CherryDiscoveryKind.LONE, CherryDiscoveryKind.BREAK),
            tied.priorityKinds)
    }
}
