package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import java.math.BigInteger

data class DigitScenario(val digit: String, val stake: Long, val payout: Long, val profitLoss: Long, val limit: Long?, val percentUsed: Int?, val closed: Boolean)
data class AnalysisResult(val distinctDigits: Int, val totalBet: Long, val limitedDigits: Int, val warningDigits: Int, val nearDigits: Int, val fullDigits: Int, val highest: List<Pair<String, Long>>, val closedDigits: Set<String>, val rejectDigits: Set<String>, val scenarios: List<DigitScenario>) {
    val worstCasePayout: Long = scenarios.maxOfOrNull { it.payout } ?: 0L
    val worstCaseProfitLoss: Long = scenarios.minOfOrNull { it.profitLoss } ?: 0L
}

class AnalysisCalculator {
    fun calculate(amounts: Map<String, Long>, limits: EffectiveLimits, closed: Set<String>, agentRate: Long): AnalysisResult {
        val total = amounts.values.fold(0L, Math::addExact)
        val scenarios = (0..99).map { it.toString().padStart(2, '0') }.map { digit ->
            val stake = amounts[digit] ?: 0L
            val limit = limits.forDigit(digit)
            val used = if (limit != null && limit > 0) BigInteger.valueOf(stake).multiply(BigInteger.valueOf(100)).divide(BigInteger.valueOf(limit)).min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt() else null
            DigitScenario(digit, stake, MoneyMath.multiply(stake, agentRate), Math.subtractExact(total, MoneyMath.multiply(stake, agentRate)), limit, used, digit in closed)
        }
        return AnalysisResult(amounts.count { it.value > 0 }, total, scenarios.count { it.limit != null }, scenarios.count { it.percentUsed != null && it.percentUsed >= 80 }, scenarios.count { it.percentUsed != null && it.percentUsed >= 90 }, scenarios.count { it.percentUsed != null && it.percentUsed >= 100 }, amounts.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }, scenarios.filter { it.closed }.map { it.digit }.toSet(), scenarios.filter { it.percentUsed != null && it.percentUsed >= 100 || it.closed }.map { it.digit }.toSet(), scenarios)
    }
}
