package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test

class DigitViewSelectorTest {
    private val stakes = mapOf(
        "33" to 2_000L,
        "03" to 500L,
        "30" to 2_000L,
        "10" to 100L,
        "99" to 0L,
    )

    @Test fun defaultShowsEveryDigitInNumberOrder() {
        val rows = DigitViewSelector.select(stakes)
        assertEquals(100, rows.size)
        assertEquals("00", rows.first().digit)
        assertEquals("99", rows.last().digit)
        assertEquals(2_000L, rows.single { it.digit == "33" }.stake)
        assertEquals(0L, rows.single { it.digit == "01" }.stake)
    }

    @Test fun stakedViewSortsMoneyDescendingAndBreaksTiesByDigit() {
        val rows = DigitViewSelector.select(stakes, DigitViewFilter.STAKED, DigitViewSort.STAKE_DESC)
        assertEquals(listOf("30", "33", "03", "10"), rows.map { it.digit })
        assertEquals(listOf(2_000L, 2_000L, 500L, 100L), rows.map { it.stake })
    }

    @Test fun stakedViewSortsMoneyAscendingAndAlsoSupportsNumberOrder() {
        val ascending = DigitViewSelector.select(stakes, DigitViewFilter.STAKED, DigitViewSort.STAKE_ASC)
        assertEquals(listOf("10", "03", "30", "33"), ascending.map { it.digit })
        val numerical = DigitViewSelector.select(stakes, DigitViewFilter.STAKED, DigitViewSort.NUMBER)
        assertEquals(listOf("03", "10", "30", "33"), numerical.map { it.digit })
    }

    @Test fun emptyViewUsesOnlyZeroStakeAndStaysInNumberOrder() {
        val rows = DigitViewSelector.select(stakes, DigitViewFilter.EMPTY, DigitViewSort.STAKE_DESC)
        assertEquals(96, rows.size)
        assertEquals("00", rows.first().digit)
        assertEquals("99", rows.last().digit)
        assertFalse(rows.any { it.digit in setOf("33", "03", "30", "10") })
    }

    @Test fun searchCombinesWithViewAndUnderstandsMyanmarDigits() {
        val allThree = DigitViewSelector.select(stakes, search = "၃")
        assertEquals(19, allThree.size)
        assertTrue(allThree.any { it.digit == "03" })
        assertTrue(allThree.any { it.digit == "33" })
        val stakedThree = DigitViewSelector.select(stakes, DigitViewFilter.STAKED, search = "3")
        assertEquals(listOf("03", "30", "33"), stakedThree.map { it.digit })
        val emptyThree = DigitViewSelector.select(stakes, DigitViewFilter.EMPTY, search = "3")
        assertEquals(16, emptyThree.size)
        val exact = DigitViewSelector.select(stakes, search = "၃၃")
        assertEquals(listOf("33"), exact.map { it.digit })
    }

    @Test fun selectingViewsNeverChangesTheOriginalStakes() {
        val original = stakes.toMap()
        DigitViewSelector.select(stakes, DigitViewFilter.STAKED, DigitViewSort.STAKE_ASC, "3")
        DigitViewSelector.select(stakes, DigitViewFilter.EMPTY, DigitViewSort.STAKE_DESC, "8")
        assertEquals(original, stakes)
    }

    @Test fun allStakedOrAllEmptyCasesRemainValid() {
        val full = (0..99).associate { it.toString().padStart(2, '0') to 10L }
        assertEquals(100, DigitViewSelector.select(full, DigitViewFilter.STAKED).size)
        assertTrue(DigitViewSelector.select(full, DigitViewFilter.EMPTY).isEmpty())
        assertEquals(100, DigitViewSelector.select(emptyMap(), DigitViewFilter.EMPTY).size)
        assertTrue(DigitViewSelector.select(emptyMap(), DigitViewFilter.STAKED).isEmpty())
    }

    @Test fun individualLimitOverridesGeneralLimitAndAgentCustomerMinimumWins() {
        val agentSpecial = mapOf("33" to 1_800L, "22" to 2_500L)
        val customerSpecial = mapOf("33" to 1_200L)
        val agentAll = 3_000L
        val customerAll = 2_000L
        assertEquals(
            1_200L,
            DigitViewSelector.effectiveLimit("33", agentAll, agentSpecial,
                customerAll, customerSpecial, aggregate = false)
        )
        assertEquals(
            2_000L,
            DigitViewSelector.effectiveLimit("22", agentAll, agentSpecial,
                customerAll, customerSpecial, aggregate = false)
        )
        assertEquals(
            2_000L,
            DigitViewSelector.effectiveLimit("44", agentAll, agentSpecial,
                customerAll, customerSpecial, aggregate = false)
        )
        assertEquals(
            1_800L,
            DigitViewSelector.effectiveLimit("33", agentAll, agentSpecial,
                customerAll, customerSpecial, aggregate = true)
        )
        assertEquals(
            3_000L,
            DigitViewSelector.effectiveLimit("44", agentAll, agentSpecial,
                customerAll, customerSpecial, aggregate = true)
        )
    }

    @Test fun absentLimitHasNoPercentageAndHugeAmountsDoNotOverflow() {
        assertNull(DigitViewSelector.effectiveLimit("00", null, emptyMap(), null, emptyMap(), false))
        assertNull(DigitViewSelector.percentUsed(1_000L, null))
        assertNull(DigitViewSelector.percentUsed(1_000L, 0L))
        assertEquals(50, DigitViewSelector.percentUsed(500L, 1_000L))
        assertEquals(100, DigitViewSelector.percentUsed(1_000L, 1_000L))
        assertEquals(125, DigitViewSelector.percentUsed(1_250L, 1_000L))
        assertEquals(Int.MAX_VALUE, DigitViewSelector.percentUsed(Long.MAX_VALUE, 1L))
    }
}
