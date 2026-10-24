package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import com.myanmar.ledger2d.core.model.ExpandedBet
import org.junit.Assert.*
import org.junit.Test

class EditCorrectionPolicyTest {
    private val policy=EditCorrectionPolicy()
    private val limits=EffectiveLimits(null,mapOf("20" to 1000L))
    @Test fun previouslyAcceptedClosedDigitCanStay(){
        val result=policy.validate(listOf(ExpandedBet("20",1500L)),mapOf("20" to 1500L),emptyMap(),limits,setOf("20"))
        assertTrue(result.canConfirm)
    }
    @Test fun reducingAcceptedStakeWorksWithTightenedLimit(){
        val result=policy.validate(listOf(ExpandedBet("20",900L)),mapOf("20" to 1500L),emptyMap(),limits,setOf("20"))
        assertTrue(result.canConfirm)
    }
    @Test fun increasingClosedDigitIsRejected(){
        val result=policy.validate(listOf(ExpandedBet("20",1600L)),mapOf("20" to 1500L),emptyMap(),limits,setOf("20"))
        assertFalse(result.canConfirm)
    }
    @Test fun aNewClosedDigitIsRejected(){
        val result=policy.validate(listOf(ExpandedBet("20",100L)),emptyMap(),emptyMap(),limits,setOf("20"))
        assertFalse(result.canConfirm)
    }
    @Test fun historicalOtherBetsCountTowardLimit(){
        val result=policy.validate(listOf(ExpandedBet("20",800L)),mapOf("20" to 300L),mapOf("20" to 400L),limits,emptySet())
        assertFalse(result.canConfirm)
    }
}
