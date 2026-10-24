package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import com.myanmar.ledger2d.core.model.ExpandedBet

/**
 * A previously accepted digit may stay or be reduced even if the session's
 * rules were tightened afterwards. New/increased stakes obey the saved rules.
 * Never discard rejected lines: callers must reject the complete edit.
 */
class EditCorrectionPolicy(private val validator:LimitValidator=LimitValidator()) {
    fun validate(
        newLines:List<ExpandedBet>,
        originalAmounts:Map<String,Long>,
        otherAcceptedTotals:Map<String,Long>,
        limits:EffectiveLimits,
        closed:Set<String>,
    ):ValidationResult {
        val result=validator.validate(newLines,otherAcceptedTotals,limits,closed)
        return ValidationResult(result.rows.map { row ->
            if(row.thisInput <= (originalAmounts[row.digit] ?: 0L))
                row.copy(isClosed=false,exceedsLimit=false)
            else row
        })
    }
}
