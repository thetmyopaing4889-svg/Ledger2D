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
        // A customer can be deleted between UI selection and the repository read.
        // The existing ViewModel returns an empty scenario list for a missing customer.
        if (rows.isEmpty() && source.totalBet == 0L) {
            val empty = RiskSnapshot(0L, 0, 0, 0, 100, 0L, 0L, 0L, emptyList())
            return CustomerRiskDecision(empty, emptyList(), emptyList(), null, false,
                hedgeRate, hedgeRate <= 0L)
        }
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

    /**
     * Find the least-cost hedge whose worst loss stays within [budget], including
     * draws containing different saved payout rates. Original liabilities are
     * the historical [DigitScenario.payout] snapshots; only NEW hedge tickets
     * are valued at [hedgeRate].
     *
     * For a residual payout ceiling C the minimum hedge on digit d is
     * ceil(max(0, payout[d]-C) / hedgeRate). Among payout ceilings with the same
     * remainder modulo hedgeRate, C + totalHedge(C) is convex. Its rightmost
     * budget-feasible point gives the lowest hedge cost for that remainder.
     * Every real optimum is covered by a ceiling having the remainder of one
     * of the saved per-digit payouts (at least one digit attains the ceiling).
     */
    fun planForLossBudget(source: AnalysisResult, hedgeRate: Long, budget: Long): RiskHedgePlan? {
        if (hedgeRate <= 0L || budget < 0L || source.scenarios.size != 100 || source.totalBet <= 0L)
            return null
        val rows = source.scenarios.sortedBy { it.digit }
        if (rows.any { it.stake < 0L || it.payout < 0L }) return null
        val total = source.totalBet
        val noHedge = evaluate("budget", "စိတ်ကြိုက်အရှုံးကန့်သတ်", rows, total,
            hedgeRate, emptyList()) ?: return null
        if (noHedge.worstLoss <= budget) return noHedge

        val minCap = rows.maxOf { row ->
            val excess = java.math.BigInteger.valueOf(row.payout)
                .subtract(java.math.BigInteger.valueOf(row.stake)
                    .multiply(java.math.BigInteger.valueOf(hedgeRate)))
            if (excess.signum() > 0) excess.longValueExact() else 0L
        }
        val maxByRemainder = rows.groupBy { it.payout % hedgeRate }
            .mapValues { (_, group) -> group.maxOf { it.payout } }
        var best: RiskHedgePlan? = null

        for ((remainder, lastPayout) in maxByRemainder) {
            val qStart = if (minCap <= remainder) 0L else {
                val diff = minCap - remainder
                diff / hedgeRate + if (diff % hedgeRate == 0L) 0L else 1L
            }
            val qEnd = (lastPayout - remainder) / hedgeRate
            if (qStart > qEnd) continue
            fun ceiling(q: Long): Long = Math.addExact(remainder, Math.multiplyExact(q, hedgeRate))

            // A plan with more hedge stake than money collected is not actionable.
            // This predicate is monotone as the payout ceiling increases.
            fun affordable(q: Long): Boolean {
                val cap = ceiling(q)
                var spent = 0L
                for (row in rows) {
                    if (row.payout <= cap) continue
                    val diff = row.payout - cap
                    val hedge = diff / hedgeRate + if (diff % hedgeRate == 0L) 0L else 1L
                    if (hedge > row.stake || hedge > total - spent) return false
                    spent += hedge
                }
                return true
            }
            if (!affordable(qEnd)) continue
            var lo = qStart
            var hi = qEnd
            while (lo < hi) {
                val mid = lo + (hi - lo) / 2L
                if (affordable(mid)) hi = mid else lo = mid + 1L
            }
            val firstAffordable = lo

            // C + H(C) reaches its minimum when there are at most hedgeRate
            // positive-payout digits above C (the discrete slope flips sign).
            lo = firstAffordable
            hi = qEnd
            while (lo < hi) {
                val mid = lo + (hi - lo) / 2L
                val active = rows.count { it.payout > ceiling(mid) }.toLong()
                if (active > hedgeRate) lo = mid + 1L else hi = mid
            }
            val minimumLossPoint = lo
            fun withinBudget(q: Long): Boolean =
                planForLiabilityCap(rows, total, hedgeRate, ceiling(q))
                    ?.let { it.worstLoss <= budget } ?: false
            if (!withinBudget(minimumLossPoint)) continue

            // After the minimum, worst loss is nondecreasing within the
            // remainder class, so find its rightmost feasible ceiling.
            lo = minimumLossPoint
            hi = qEnd
            while (lo < hi) {
                val mid = lo + (hi - lo) / 2L + 1L
                if (withinBudget(mid)) lo = mid else hi = mid - 1L
            }
            val candidate = planForLiabilityCap(rows, total, hedgeRate, ceiling(lo))
                ?: continue
            if (candidate.worstLoss > budget) continue
            if (best == null || candidate.upstreamTotal < best.upstreamTotal ||
                (candidate.upstreamTotal == best.upstreamTotal &&
                    candidate.worstLoss < best.worstLoss)) {
                best = candidate
            }
        }
        return best?.copy(id = "budget", title = "စိတ်ကြိုက်အရှုံးကန့်သတ်")
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
