package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import com.myanmar.ledger2d.core.model.ExpandedBet

/**
 * Agent rules cover all customers of that agent; customer rules cover only that
 * customer's bets. Both sets are session-specific and must pass together.
 */
class SessionScopedLimitValidator(private val validator:LimitValidator=LimitValidator()) {
    fun validate(
        input:List<ExpandedBet>,
        agentTotals:Map<String,Long>,
        customerTotals:Map<String,Long>,
        agentLimits:EffectiveLimits,
        customerLimits:EffectiveLimits,
        closed:Set<String>,
        originalAmounts:Map<String,Long>?=null,
    ):ValidationResult {
        val corrections=EditCorrectionPolicy(validator)
        val a=if(originalAmounts==null)validator.validate(input,agentTotals,agentLimits,closed)
            else corrections.validate(input,originalAmounts,agentTotals,agentLimits,closed)
        val c=if(originalAmounts==null)validator.validate(input,customerTotals,customerLimits,emptySet())
            else corrections.validate(input,originalAmounts,customerTotals,customerLimits,emptySet())
        require(a.rows.size==c.rows.size)
        return ValidationResult(a.rows.zip(c.rows){agent,customer->
            require(agent.digit==customer.digit)
            val mostRestrictive=when{
                agent.exceedsLimit||agent.isClosed->agent
                customer.exceedsLimit->customer
                (customer.remaining?:Long.MAX_VALUE)<(agent.remaining?:Long.MAX_VALUE)->customer
                else->agent
            }
            mostRestrictive.copy(
                isClosed=agent.isClosed || customer.isClosed,
                exceedsLimit=agent.exceedsLimit||customer.exceedsLimit
            )
        })
    }
}
