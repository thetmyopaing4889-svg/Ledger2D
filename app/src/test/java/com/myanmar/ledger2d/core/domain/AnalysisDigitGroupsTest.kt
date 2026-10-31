package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import org.junit.Assert.*
import org.junit.Test

class AnalysisDigitGroupsTest {
    private val calc = AnalysisCalculator()
    private fun from(stakes: Map<String, Long>, overrides: Map<String, Long>? = null): AnalysisResult =
        calc.calculate(stakes, EffectiveLimits(null, emptyMap()), emptySet(), 80, overrides)

    @Test fun fortyThreeStakedDigitsPartitionExactlyInto57Unstaked12Profit31Loss() {
        val stakes = (0..42).associate {
            it.toString().padStart(2, '0') to if (it < 31) 10_000L else 4_000L
        }
        val analysis = from(stakes)
        val groups = AnalysisDigitGroups.counts(analysis)
        assertEquals(100, groups.values.sum())
        assertEquals(57, groups.getValue(AnalysisDigitGroup.UNSTAKED))
        assertEquals(12, groups.getValue(AnalysisDigitGroup.STAKED_PROFIT))
        assertEquals(31, groups.getValue(AnalysisDigitGroup.STAKED_LOSS))
        assertEquals(0, groups.getValue(AnalysisDigitGroup.BREAK_EVEN))
        assertEquals(43, analysis.distinctDigits)
        assertTrue(AnalysisDigitGroups.select(analysis, AnalysisDigitGroup.UNSTAKED).all { it.stake == 0L })
        assertTrue(AnalysisDigitGroups.select(analysis, AnalysisDigitGroup.STAKED_PROFIT).all { it.stake > 0L && it.profitLoss > 0L })
        assertTrue(AnalysisDigitGroups.select(analysis, AnalysisDigitGroup.STAKED_LOSS).all { it.stake > 0L && it.profitLoss < 0L })
        assertEquals("43", AnalysisDigitGroups.select(analysis, AnalysisDigitGroup.UNSTAKED).first().digit)
    }

    @Test fun breakEvenDigitsHaveStakeAndReallyEqualPayoutWithTotal() {
        val stakes = (0..79).associate { it.toString().padStart(2, '0') to 1000L }
        val analysis = from(stakes)
        assertEquals(80, AnalysisDigitGroups.select(analysis, AnalysisDigitGroup.BREAK_EVEN).size)
        assertEquals(20, AnalysisDigitGroups.select(analysis, AnalysisDigitGroup.UNSTAKED).size)
        assertTrue(AnalysisDigitGroups.select(analysis, AnalysisDigitGroup.BREAK_EVEN).all {
            it.payout == 80_000L && it.profitLoss == 0L
        })
    }

    @Test fun detailedRowsReuseHistoricalPayoutAndProfitRatherThanRecalculateOrAddCommission() {
        val stakes = mapOf("11" to 2000L, "22" to 1000L, "33" to 500L)
        val source = from(stakes, overrides = mapOf("11" to 190_000L))
        val losing = AnalysisDigitGroups.select(source, AnalysisDigitGroup.STAKED_LOSS)
        assertEquals(listOf("11", "22"), losing.map { it.digit })
        assertEquals(190_000L, losing.first().payout)
        assertEquals(-186_500L, losing.first().profitLoss)
        assertEquals(2_000L, losing.first().stake)
        assertEquals(500L, AnalysisDigitGroups.select(source, AnalysisDigitGroup.STAKED_PROFIT).single().stake)
        assertEquals(0L, AnalysisDigitGroups.select(source, AnalysisDigitGroup.UNSTAKED).first().payout)
        assertEquals(3_500L, AnalysisDigitGroups.select(source, AnalysisDigitGroup.UNSTAKED).first().profitLoss)
    }

    @Test fun editedStakesRebuildGroupRowsWithoutStaleCachedCounts() {
        val initial = from(mapOf("00" to 500L, "01" to 100L))
        val edited = from(mapOf("00" to 3000L))
        assertEquals(98, AnalysisDigitGroups.select(initial, AnalysisDigitGroup.UNSTAKED).size)
        assertEquals(99, AnalysisDigitGroups.select(edited, AnalysisDigitGroup.UNSTAKED).size)
        assertEquals(listOf("00"), AnalysisDigitGroups.select(edited, AnalysisDigitGroup.STAKED_LOSS).map { it.digit })
    }

    @Test fun emptyGroupIsNeverFilledWithUnrelatedDigits() {
        val source = from(mapOf("00" to 1000L))
        assertTrue(AnalysisDigitGroups.select(source, AnalysisDigitGroup.BREAK_EVEN).isEmpty())
    }
}
