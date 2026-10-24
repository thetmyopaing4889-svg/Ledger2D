package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import com.myanmar.ledger2d.core.model.ExpandedBet
import org.junit.Assert.*
import org.junit.Test

class SessionScopedLimitValidatorTest {
    private val checker=SessionScopedLimitValidator()
    private val noLimit=EffectiveLimits(null,emptyMap())
    @Test fun anotherCustomerStakeDoesNotConsumeCustomerLimit(){
        val cLimit=EffectiveLimits(null,mapOf("10" to 1000L))
        val result=checker.validate(listOf(ExpandedBet("10",500)),mapOf("10" to 800L),emptyMap(),noLimit,cLimit,emptySet())
        assertTrue(result.canConfirm)
    }
    @Test fun otherCustomersConsumeAgentWideLimit(){
        val aLimit=EffectiveLimits(null,mapOf("10" to 1000L))
        val result=checker.validate(listOf(ExpandedBet("10",500)),mapOf("10" to 800L),emptyMap(),aLimit,noLimit,emptySet())
        assertFalse(result.canConfirm)
    }
    @Test fun customerOwnHistoryCountsTowardCustomerLimit(){
        val cLimit=EffectiveLimits(1000L,emptyMap())
        val result=checker.validate(listOf(ExpandedBet("10",400)),emptyMap(),mapOf("10" to 800L),noLimit,cLimit,emptySet())
        assertFalse(result.canConfirm)
    }
    @Test fun editsPreserveOriginalDigitWhenLaterClosed(){
        val aLimit=EffectiveLimits(null,mapOf("10" to 1000L))
        val result=checker.validate(listOf(ExpandedBet("10",1500)),emptyMap(),emptyMap(),aLimit,noLimit,setOf("10"),mapOf("10" to 1500L))
        assertTrue(result.canConfirm)
    }
}
