package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits

/** One customer's limit alerts in one specific draw; never combines customers' limits. */
data class CustomerLimitWarning(
    val customerId: Long,
    val name: String,
    val warningDigits: List<String>, // 80 to 89 percent
    val nearDigits: List<String>,    // 90 to 99 percent
    val fullDigits: List<String>,    // 100 percent and over
) {
    val affectedCount: Int get() = warningDigits.size + nearDigits.size + fullDigits.size
}

object CustomerLimitWarningCalculator {
    fun inspect(
        customerId: Long,
        name: String,
        amounts: Map<String, Long>,
        limits: EffectiveLimits,
    ): CustomerLimitWarning? {
        val warning = mutableListOf<String>()
        val near = mutableListOf<String>()
        val full = mutableListOf<String>()
        for ((digit, amount) in amounts.toSortedMap()) {
            if (amount <= 0L) continue
            val used = DigitViewSelector.percentUsed(amount, limits.forDigit(digit)) ?: continue
            when {
                used >= 100 -> full.add(digit)
                used >= 90 -> near.add(digit)
                used >= 80 -> warning.add(digit)
            }
        }
        if (warning.isEmpty() && near.isEmpty() && full.isEmpty()) return null
        return CustomerLimitWarning(customerId, name, warning, near, full)
    }
}
