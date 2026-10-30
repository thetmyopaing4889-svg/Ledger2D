package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import org.junit.Assert.*
import org.junit.Test

class CustomerLimitWarningCalculatorTest {
    @Test fun individualCustomerLimitsRemainSeparateWhenViewingAll() {
        // Shared agent-wide 2,000; first customer has an additional 1,000 limit on 33.
        val a = CustomerLimitWarningCalculator.inspect(1L, "A",
            mapOf("33" to 1_000L, "11" to 1_800L, "00" to 1_600L),
            EffectiveLimits(2_000L, mapOf("33" to 1_000L)))!!
        val b = CustomerLimitWarningCalculator.inspect(2L, "B",
            mapOf("33" to 600L, "11" to 200L),
            EffectiveLimits(2_000L, mapOf("33" to 800L)))
        assertEquals(listOf("33"), a.fullDigits)
        assertEquals(listOf("11"), a.nearDigits)
        assertEquals(listOf("00"), a.warningDigits)
        assertNull(b)
    }

    @Test fun eachSelectedSessionUsesOnlyItsOwnLimitsAndStakes() {
        val morning = CustomerLimitWarningCalculator.inspect(3L, "C",
            mapOf("88" to 800L), EffectiveLimits(1_000L, emptyMap()))
        val evening = CustomerLimitWarningCalculator.inspect(3L, "C",
            mapOf("88" to 800L), EffectiveLimits(2_000L, emptyMap()))
        assertEquals(listOf("88"), morning!!.warningDigits)
        assertNull(evening)
    }

    @Test fun zeroStakeAndUnlimitedDigitsDoNotReportFalseWarnings() {
        val result = CustomerLimitWarningCalculator.inspect(4L, "D",
            mapOf("44" to 0L, "77" to 1_500L),
            EffectiveLimits(null, mapOf("44" to 100L)))
        assertNull(result)
    }
}
