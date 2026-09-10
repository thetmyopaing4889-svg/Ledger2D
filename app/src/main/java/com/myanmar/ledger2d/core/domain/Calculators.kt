package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import com.myanmar.ledger2d.core.model.ExpandedBet
import java.math.BigInteger

object MoneyMath {
    const val MaxSafeAmount: Long = 9_000_000_000_000_000L
    fun percentage(amount: Long, basisPoints: Int): Long {
        require(amount >= 0 && basisPoints in 0..10_000)
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(basisPoints.toLong())).divide(BigInteger.valueOf(10_000)).longValueExact()
    }
    fun multiply(amount: Long, multiplier: Long): Long { require(amount >= 0 && multiplier >= 0); return Math.multiplyExact(amount, multiplier) }
}
class CommissionCalculator { fun calculate(totalBet: Long, rateBasisPoints: Int) = MoneyMath.percentage(totalBet, rateBasisPoints) }
class PayoutCalculator { fun calculate(winningStake: Long, agentRate: Long) = MoneyMath.multiply(winningStake, agentRate) }
class ProfitLossCalculator { fun calculate(totalBet: Long, payout: Long): Long = Math.subtractExact(totalBet, payout) }
class BetAggregator {
    fun byDigit(bets: Iterable<ExpandedBet>): Map<String, Long> { val result = sortedMapOf<String, Long>(); bets.forEach { require(it.amount in 1..MoneyMath.MaxSafeAmount); result[it.digit] = Math.addExact(result[it.digit] ?: 0, it.amount).also { total -> require(total <= MoneyMath.MaxSafeAmount) } }; return result }
    fun winningStake(bets: Iterable<ExpandedBet>, winningDigit: String): Long = byDigit(bets)[winningDigit] ?: 0
}
class ClosedNumberValidator { fun blockedDigits(bets: Iterable<ExpandedBet>, closed: Set<String>): Set<String> = bets.asSequence().map { it.digit }.filter { it in closed }.toSortedSet() }
data class DigitValidation(val digit: String, val currentTotal: Long, val thisInput: Long, val limit: Long?, val afterInput: Long, val remaining: Long?, val isClosed: Boolean, val exceedsLimit: Boolean)
data class ValidationResult(val rows: List<DigitValidation>) { val canConfirm get() = rows.isNotEmpty() && rows.none { it.isClosed || it.exceedsLimit } }
class LimitValidator {
    fun validate(bets: Iterable<ExpandedBet>, current: Map<String, Long>, limits: EffectiveLimits, closed: Set<String> = emptySet()): ValidationResult {
        val input = BetAggregator().byDigit(bets)
        return ValidationResult(input.map { (digit, amount) ->
            val before = current[digit] ?: 0L
            val after = try { Math.addExact(before, amount) } catch (_: ArithmeticException) { Long.MAX_VALUE }
            val limit = limits.forDigit(digit)
            DigitValidation(digit, before, amount, limit, after, limit?.let { it - after }, digit in closed, limit != null && after > limit)
        })
    }
}
data class DrawCalculation(val totalBet: Long, val distinctSlots: Int, val winningStake: Long, val payout: Long, val commission: Long, val profitLoss: Long)
class ReportCalculator(private val commission: CommissionCalculator = CommissionCalculator(), private val payout: PayoutCalculator = PayoutCalculator(), private val profitLoss: ProfitLossCalculator = ProfitLossCalculator()) {
    fun calculate(amountsByDigit: Map<String, Long>, winningDigit: String?, agentRate: Long, commissionBasisPoints: Int): DrawCalculation {
        val total = amountsByDigit.values.fold(0L, Math::addExact)
        val stake = winningDigit?.let { amountsByDigit[it] } ?: 0
        val paid = payout.calculate(stake, agentRate)
        require(total in 0..MoneyMath.MaxSafeAmount && stake in 0..MoneyMath.MaxSafeAmount)
        return DrawCalculation(total, amountsByDigit.count { it.value > 0 }, stake, paid, commission.calculate(total, commissionBasisPoints), profitLoss.calculate(total, paid))
    }
}
