package com.myanmar.ledger2d.core.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Read-only customer exposure planning. No commission, writes, or implied executed hedges.
 *
 * Customer liabilities come from the saved per-digit payout snapshots. A hedge is a separate
 * hypothetical upstream ticket (same digit) paying [hedgeRate] times its stake; its cost is
 * deducted in EVERY possible outcome. This keeps past-session rates intact.
 */
data class RiskSnapshot(
    val totalStake: Long,
    val coveredDigits: Int,
    val profitableDigits: Int,
    val losingDigits: Int,
    val breakEvenDigits: Int,
    val bestProfit: Long,
    val worstProfit: Long,
    val worstLoss: Long,
    val riskDigits: List<String>,
)

data class SuggestedHedge(
    val digit: String,
    val amount: Long,
    val originalStake: Long,
)

data class RiskHedgePlan(
    val id: String,
    val title: String,
    val upstreamTotal: Long,
    val keptCash: Long,
    val bestProfit: Long,
    val worstProfit: Long,
    val worstLoss: Long,
    val profitableDigits: Int,
    val losingDigits: Int,
    val breakEvenDigits: Int,
    val hedges: List<SuggestedHedge>,
) {
    /** Compare MONETARY magnitudes, not the number of winning outcomes. */
    val balanceRatioLabel: String get() {
        val profit = bestProfit.coerceAtLeast(0).toDouble()
        val loss = worstLoss.toDouble()
        val share = if (profit + loss == 0.0) 50.0 else profit * 100.0 / (profit + loss)
        val rounded = kotlin.math.round(share * 10.0) / 10.0
        return String.format(java.util.Locale.US, "%.1f : %.1f", rounded, 100.0 - rounded)
    }
}

data class CustomerRiskDecision(
    val baseline: RiskSnapshot,
    val riskRows: List<DigitScenario>,
    val plans: List<RiskHedgePlan>,
    val guaranteedPlan: RiskHedgePlan?,
    val alreadyGuaranteed: Boolean,
    val hedgeRate: Long,
    val missingHedgeRate: Boolean,
)

/** Pure integer-math calculator; a 1-kyat hedge granularity is a modelling assumption. */
class CustomerRiskPlanner {
    fun calculate(source: AnalysisResult, hedgeRate: Long): CustomerRiskDecision {
        val rows = source.scenarios.sortedBy { it.digit }
        require(rows.size == 100) { "Analysis must contain all 100 outcomes" }
        require(rows.all { it.stake >= 0L && it.payout >= 0L }) { "Invalid stake or payout" }
        require(source.totalBet >= 0L)
        val baseline = snapshot(source.totalBet, rows)
        if (source.totalBet == 0L || hedgeRate <= 0L) {
            // A missing agent rate makes the upstream hedge unknowable; in older
            // rows it can also make a fallback scenario payout incorrectly zero.
            // Never describe this as a mathematically guaranteed-profit sheet.
            return CustomerRiskDecision(baseline, rows, emptyList(), null, false,
                hedgeRate, hedgeRate <= 0L)
        }

        val positiveStakes = rows.filter { it.stake > 0L }.map { it.stake }
        val minStake = positiveStakes.minOrNull() ?: 0L
        val maxStake = positiveStakes.maxOrNull() ?: 0L
        val baselinePlan = planForStakeCap("none", "မကာဘဲထား", rows, source.totalBet, hedgeRate, maxStake)!!
        if (baseline.worstProfit > 0L) {
            return CustomerRiskDecision(baseline, rows, emptyList(), baselinePlan, true, hedgeRate, false)
        }

        val caps = linkedMapOf<String, Pair<String, Long>>()
        val evenStakes = maxStake == minStake
        // Equal-stake sheets also need a genuine hedge option: a cap equal to
        // the original amount would otherwise recommend "no hedge" as 50/50.
        val low = max(1L, minStake / if (evenStakes) 4L else 2L)
        caps["low"] = "အရှုံးနည်း" to low
        caps["balance"] = "မျှခြေနီးစပ်" to if (evenStakes) max(1L, minStake / 2L) else minStake
        if (evenStakes) {
            caps["growth"] = "ငွေပိုကျန် (မကာ)" to maxStake
        } else {
            val growth = min(maxStake, if (minStake > Long.MAX_VALUE / 2L) maxStake else minStake * 2L)
            val middle = minStake + (growth - minStake) / 2L
            if (middle > minStake && middle < growth) caps["middle"] = "အလယ်အလတ်" to middle
            if (growth > minStake) caps["growth"] = "ငွေပိုကျန်" to growth
        }
        // Keep distinct outcomes; do not recommend a redundant duplicate choice.
        val seen = mutableSetOf<List<Pair<String, Long>>>()
        val plans = caps.mapNotNull { (id, pair) ->
            planForStakeCap(id, pair.first, rows, source.totalBet, hedgeRate, pair.second)
        }.filter { plan -> seen.add(plan.hedges.map { it.digit to it.amount }) }

        val guaranteed = findCheapestGuaranteed(rows, source.totalBet, hedgeRate)
        return CustomerRiskDecision(baseline, rows, plans, guaranteed, false, hedgeRate, false)
    }

    /** Maximize money retained while keeping worst loss within the user's budget. */
    fun planForLossBudget(source: AnalysisResult, hedgeRate: Long, budget: Long): RiskHedgePlan? {
        if (hedgeRate <= 0L || budget < 0L || source.scenarios.size != 100 || source.totalBet <= 0L) return null
        val rows = source.scenarios.sortedBy { it.digit }
        // The stake-cap risk curve is monotone in the constraint "worst loss <= budget"
        // when the customer liability for every digit is the same rate as the hedge.
        if (rows.any { it.payout != Math.multiplyExact(it.stake, hedgeRate) }) return null
        var lo = 0L
        var hi = rows.maxOf { it.stake }
        var best: RiskHedgePlan? = null
        while (lo <= hi) {
            val mid = lo + (hi - lo) / 2L
            val plan = planForStakeCap("budget", "စိတ်ကြိုက်အရှုံးကန့်သတ်", rows, source.totalBet, hedgeRate, mid)
                ?: return best
            if (plan.worstLoss <= budget) {
                best = plan
                if (mid == Long.MAX_VALUE) break
                lo = mid + 1L
            } else hi = mid - 1L
        }
        return best
    }

    private fun snapshot(total: Long, rows: List<DigitScenario>): RiskSnapshot {
        val best = rows.maxOf { Math.subtractExact(total, it.payout) }
        val worst = rows.minOf { Math.subtractExact(total, it.payout) }
        val worstPayout = rows.maxOf { it.payout }
        return RiskSnapshot(
            totalStake = total,
            coveredDigits = rows.count { it.stake > 0L },
            profitableDigits = rows.count { total > it.payout },
            losingDigits = rows.count { total < it.payout },
            breakEvenDigits = rows.count { total == it.payout },
            bestProfit = best,
            worstProfit = worst,
            worstLoss = max(0L, Math.subtractExact(worstPayout, total)),
            riskDigits = rows.filter { it.payout == worstPayout && it.stake > 0L }.map { it.digit },
        )
    }

    private fun planForStakeCap(id: String, title: String, rows: List<DigitScenario>,
                                total: Long, rate: Long, cap: Long): RiskHedgePlan? {
        if (cap < 0L) return null
        val hedges = rows.mapNotNull { row ->
            val amount = max(0L, row.stake - cap)
            if (amount > 0L) SuggestedHedge(row.digit, amount, row.stake) else null
        }
        return evaluate(id, title, rows, total, rate, hedges)
    }

    private fun planForLiabilityCap(rows: List<DigitScenario>, total: Long, rate: Long,
                                    cap: Long): RiskHedgePlan? {
        val hedges = mutableListOf<SuggestedHedge>()
        for (row in rows) {
            if (row.payout <= cap) continue
            val required = row.payout - cap
            val amount = (required / rate) + if (required % rate == 0L) 0L else 1L
            if (amount > row.stake) return null
            if (amount > 0L) hedges.add(SuggestedHedge(row.digit, amount, row.stake))
        }
        return evaluate("guaranteed", "အမြတ်သေချာ", rows, total, rate, hedges)
    }

    private fun evaluate(id: String, title: String, rows: List<DigitScenario>,
                         total: Long, rate: Long, hedges: List<SuggestedHedge>): RiskHedgePlan? {
        val hedgeByDigit = hedges.associate { it.digit to it.amount }
        val upstream = hedges.fold(0L) { sum, hedge -> Math.addExact(sum, hedge.amount) }
        if (upstream > total) return null
        val kept = Math.subtractExact(total, upstream)
        val profits = rows.map { row ->
            val paidByAgent = Math.multiplyExact(rate, hedgeByDigit[row.digit] ?: 0L)
            val outstanding = Math.subtractExact(row.payout, paidByAgent)
            Math.subtractExact(kept, outstanding)
        }
        val best = profits.maxOrNull() ?: kept
        val worst = profits.minOrNull() ?: kept
        return RiskHedgePlan(id, title, upstream, kept, best, worst,
            max(0L, -worst), profits.count { it > 0L }, profits.count { it < 0L },
            profits.count { it == 0L }, hedges)
    }

    /**
     * Globally minimize outgoing hedge stake subject to a STRICT positive profit in all 100
     * outcomes. For a fixed residual-payout ceiling C, the smallest hedge on digit d is
     * ceil((payout[d]-C)/rate). The function keptCash(C)-C is concave separately in each
     * residue class modulo rate. Search its peak and rightmost positive point; the true
     * maximum residual payout always has one of the original payout residue classes.
     */
    private fun findCheapestGuaranteed(rows: List<DigitScenario>, total: Long,
                                       rate: Long): RiskHedgePlan? {
        val maxPayout = rows.maxOf { it.payout }
        var minCap = 0L
        for (row in rows) {
            val offset = Math.subtractExact(row.payout, Math.multiplyExact(row.stake, rate))
            if (offset > minCap) minCap = offset
        }
        val residues = rows.map { it.payout % rate }.toSet()
        var best: RiskHedgePlan? = null
        for (residue in residues) {
            val qStart = if (minCap <= residue) 0L else {
                val difference = minCap - residue
                difference / rate + if (difference % rate == 0L) 0L else 1L
            }
            if (maxPayout < residue) continue
            val qEnd = (maxPayout - residue) / rate
            if (qStart > qEnd) continue
            fun ceiling(q: Long): Long = Math.addExact(residue, Math.multiplyExact(q, rate))
            // Difference in guaranteed lower bound when the cap rises by one rate-unit
            // is (number of digits with original payout above the cap) - rate.
            var left = qStart
            var right = qEnd
            while (left < right) {
                val mid = left + (right - left) / 2L
                val active = rows.count { it.payout > ceiling(mid) }
                if (active > rate) left = mid + 1L else right = mid
            }
            val peak = left
            fun guaranteedAt(q: Long): Boolean {
                val cap = ceiling(q)
                val p = planForLiabilityCap(rows, total, rate, cap) ?: return false
                return p.keptCash > cap
            }
            if (!guaranteedAt(peak)) continue
            left = peak
            right = qEnd
            while (left < right) {
                val mid = left + (right - left + 1L) / 2L
                if (guaranteedAt(mid)) left = mid else right = mid - 1L
            }
            val candidate = planForLiabilityCap(rows, total, rate, ceiling(left)) ?: continue
            if (candidate.worstProfit <= 0L) continue
            if (best == null || candidate.upstreamTotal < best.upstreamTotal ||
                (candidate.upstreamTotal == best.upstreamTotal && candidate.worstProfit > best.worstProfit)) {
                best = candidate
            }
        }
        return best
    }
}
