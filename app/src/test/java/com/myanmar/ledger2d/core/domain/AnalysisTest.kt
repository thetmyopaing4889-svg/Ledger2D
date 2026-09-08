package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {
    @Test fun thresholds_closed_slots_and_worst_case_are_deterministic() {
        val result = AnalysisCalculator().calculate(mapOf("00" to 80L, "01" to 100L), EffectiveLimits(100, emptyMap()), setOf("02"), 80)
        assertEquals(2, result.distinctDigits)
        assertEquals(100, result.limitedDigits)
        assertTrue(result.warningDigits >= 2)
        assertTrue("02" in result.closedDigits)
        assertTrue("01" in result.rejectDigits)
        assertEquals(8000L, result.worstCasePayout)
        assertEquals(-7820L, result.worstCaseProfitLoss)
    }

    @Test fun special_limit_overrides_all_limit_in_analysis() {
        val result = AnalysisCalculator().calculate(mapOf("00" to 150L), EffectiveLimits(100, mapOf("00" to 200)), emptySet(), 80)
        assertEquals(0, result.fullDigits)
        assertEquals(100, result.limitedDigits)
    }

    @Test fun percentage_calculation_does_not_overflow_for_large_amounts() {
        val result = AnalysisCalculator().calculate(mapOf("00" to Long.MAX_VALUE / 2), EffectiveLimits(Long.MAX_VALUE, emptyMap()), emptySet(), 1)
        assertTrue(result.scenarios.first { it.digit == "00" }.percentUsed!! > 0)
    }
}
