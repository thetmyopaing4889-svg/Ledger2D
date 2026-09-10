package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import com.myanmar.ledger2d.core.model.ExpandedBet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentWideLimitTest {
    private val validator = LimitValidator()

    @Test fun totals_from_all_customers_are_checked_against_one_agent_limit() {
        val result = validator.validate(listOf(ExpandedBet("11", 100)), mapOf("11" to 900), EffectiveLimits(1000, emptyMap()))
        assertFalse(result.canConfirm)
        assertTrue(result.rows.single().exceedsLimit)
    }

    @Test fun special_agent_limit_overrides_agent_all_limit() {
        val result = validator.validate(listOf(ExpandedBet("11", 500)), mapOf("11" to 900), EffectiveLimits(1000, mapOf("11" to 1500)))
        assertTrue(result.canConfirm)
    }
}
