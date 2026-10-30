package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import org.junit.Assert.*
import org.junit.Test

class CustomerRiskPlannerTest {
    private val calculator = AnalysisCalculator()
    private val planner = CustomerRiskPlanner()

    private fun from(amounts: Map<String, Long>, rate: Long = 80L,
                     payoutOverrides: Map<String, Long>? = null) =
        calculator.calculate(amounts, EffectiveLimits(null, emptyMap()), emptySet(), rate, payoutOverrides)

    private fun example(): AnalysisResult {
        val bets = mutableMapOf<String, Long>()
        // 456 'အခွေပူး' = six permutations of different digits, plus 44,55,66.
        listOf("45", "46", "54", "56", "64", "65", "44", "55", "66")
            .forEach { bets[it] = 500L }
        // 8ပါ = every two-digit result containing 8, including 88 only once.
        (0..99).map { it.toString().padStart(2, '0') }
            .filter { it.contains('8') }.forEach { bets[it] = 1_000L }
        // R for 10,20,30,40,50 = original and reverse for each.
        listOf("10", "01", "20", "02", "30", "03", "40", "04", "50", "05")
            .forEach { bets[it] = 2_000L }
        return from(bets)
    }

    @Test fun baselineChecksAll100OutcomesWithoutCommission() {
        val result = planner.calculate(example(), 80L)
        assertEquals(43_500L, result.baseline.totalStake)
        assertEquals(38, result.baseline.coveredDigits)
        assertEquals(71, result.baseline.profitableDigits)
        assertEquals(29, result.baseline.losingDigits)
        assertEquals(0, result.baseline.breakEvenDigits)
        assertEquals(43_500L, result.baseline.bestProfit)
        assertEquals(-116_500L, result.baseline.worstProfit)
        assertEquals(116_500L, result.baseline.worstLoss)
        assertEquals(10, result.baseline.riskDigits.size)
        assertFalse(result.alreadyGuaranteed)
        assertNull(result.guaranteedPlan)
    }

    @Test fun balancedPreviewSubtractsHedgeCostInEveryOutcome() {
        val result = planner.calculate(example(), 80L)
        val plan = result.plans.single { it.id == "balance" }
        assertEquals(24_500L, plan.upstreamTotal)
        assertEquals(19_000L, plan.keptCash)
        assertEquals(19_000L, plan.bestProfit)
        assertEquals(-21_000L, plan.worstProfit)
        assertEquals(21_000L, plan.worstLoss)
        assertEquals(62, plan.profitableDigits)
        assertEquals(38, plan.losingDigits)
        assertEquals("47.5 : 52.5", plan.balanceRatioLabel) // monetary magnitudes, not win probability
        assertEquals(19, plan.hedges.count { it.amount == 500L })
        assertEquals(10, plan.hedges.count { it.amount == 1_500L })
        assertEquals(29, plan.hedges.size)
    }

    @Test fun profitPreservingOptionsAndLossBudgetAreConsistent() {
        val source = example()
        val result = planner.calculate(source, 80L)
        val low = result.plans.single { it.id == "low" }
        val medium = result.plans.single { it.id == "middle" }
        val growth = result.plans.single { it.id == "growth" }
        assertEquals(9_500L, low.keptCash)
        assertEquals(10_500L, low.worstLoss)
        assertEquals(26_250L, medium.keptCash)
        assertEquals(33_750L, medium.worstLoss)
        assertEquals(33_500L, growth.keptCash)
        assertEquals(46_500L, growth.worstLoss)

        val budget = planner.planForLossBudget(source, 80L, 30_000L)!!
        assertEquals(24_104L, budget.keptCash)
        assertEquals(19_396L, budget.upstreamTotal)
        assertEquals(29_976L, budget.worstLoss)
        assertTrue(budget.worstLoss <= 30_000L)
    }

    @Test fun exactly80EqualDigitsIsBreakEvenNotGuaranteed() {
        val stakes = (0..79).associate { it.toString().padStart(2, '0') to 1_000L }
        val result = planner.calculate(from(stakes), 80L)
        assertEquals(0L, result.baseline.worstProfit)
        assertEquals(80, result.baseline.breakEvenDigits)
        assertFalse(result.alreadyGuaranteed)
        assertNull(result.guaranteedPlan)
    }

    @Test fun moreThan80EqualDigitsAlreadyGuaranteesProfitWithoutHedge() {
        val stakes = (0..80).associate { it.toString().padStart(2, '0') to 1_000L }
        val result = planner.calculate(from(stakes), 80L)
        assertTrue(result.alreadyGuaranteed)
        assertEquals(1_000L, result.baseline.worstProfit)
        assertEquals(0L, result.guaranteedPlan!!.upstreamTotal)
        assertTrue(result.plans.isEmpty())
    }

    @Test fun uneven81DigitsCanBeMadeStrictlyProfitableWithMinimumHedge() {
        val stakes = (0..80).associate { it.toString().padStart(2, '0') to
            if (it == 80) 3_000L else 1_000L }
        val result = planner.calculate(from(stakes), 80L)
        assertFalse(result.alreadyGuaranteed)
        val guarantee = result.guaranteedPlan!!
        assertEquals(1_988L, guarantee.upstreamTotal)
        assertEquals(81_012L, guarantee.keptCash)
        assertEquals(52L, guarantee.worstProfit)
        assertEquals(0L, guarantee.worstLoss)
        assertEquals(100, guarantee.profitableDigits)
        assertEquals(1, guarantee.hedges.size)
        assertEquals("80", guarantee.hedges.single().digit)
        assertEquals(1_988L, guarantee.hedges.single().amount)
    }

    @Test fun emptyBetsAndMissingAgentRateDoNotProposeHedges() {
        val empty = planner.calculate(from(emptyMap()), 80L)
        assertEquals(0, empty.baseline.coveredDigits)
        assertTrue(empty.plans.isEmpty())
        assertNull(empty.guaranteedPlan)
        val missing = planner.calculate(example(), 0L)
        assertTrue(missing.missingHedgeRate)
        assertTrue(missing.plans.isEmpty())
    }

    @Test fun savedHistoricalPayoutOverridesAreUsedForOriginalLiability() {
        val source = from(mapOf("00" to 1_000L), payoutOverrides = mapOf("00" to 90_000L))
        val result = planner.calculate(source, 80L)
        assertEquals(-89_000L, result.baseline.worstProfit)
        assertEquals(89_000L, result.baseline.worstLoss)
        val low = result.plans.single { it.id == "low" }
        assertEquals(500L, low.upstreamTotal)
        assertEquals(-49_500L, low.worstProfit) // 1000 - 500 - (90000 - 80*500)
        assertNull(planner.planForLossBudget(source, 80L, 30_000L))
    }

    @Test fun smartInputExpandsExactlyLikeTheRealQuickEntryEngine() {
        val parsed = BetExpansionEngine().smartExpand(
            "456အခွေပူး500, 8ပါ1000, 10.20.30.40.50r2000"
        )
        assertFalse(parsed.hasErrors)
        assertEquals(38, parsed.bets.size)
        assertEquals(43_500L, parsed.total)
        assertEquals(9, parsed.bets.count { it.amount == 500L })
        assertEquals(19, parsed.bets.count { it.amount == 1_000L })
        assertEquals(10, parsed.bets.count { it.amount == 2_000L })
        val analysis = from(parsed.bets.associate { it.digit to it.amount })
        val plan = planner.calculate(analysis, 80L)
        assertEquals(71, plan.baseline.profitableDigits)
        assertEquals(24_500L, plan.plans.single { it.id == "balance" }.upstreamTotal)
    }

    @Test fun equalStakeSheetMustNotCallNoHedgeTheBalancedRecommendation() {
        val bets = (0..34).associate { it.toString().padStart(2, '0') to 1_000L }
        val result = planner.calculate(from(bets), 80L)
        assertEquals(35, result.baseline.coveredDigits)
        val balance = result.plans.single { it.id == "balance" }
        assertEquals(17_500L, balance.upstreamTotal)
        assertEquals(17_500L, balance.keptCash)
        assertEquals(22_500L, balance.worstLoss)
        assertTrue(balance.hedges.isNotEmpty())
        assertEquals("43.8 : 56.3", balance.balanceRatioLabel)
        val growth = result.plans.single { it.id == "growth" }
        assertEquals(0L, growth.upstreamTotal)
    }

    @Test fun strictProfitRequiresAllOneHundredResultsPositive() {
        val stakes = (0..99).associate { it.toString().padStart(2, '0') to
            if (it == 99) 3_000L else 1_000L }
        val result = planner.calculate(from(stakes), 80L)
        val g = result.guaranteedPlan!!
        assertEquals(100, g.profitableDigits)
        assertEquals(0, g.breakEvenDigits)
        assertTrue(g.worstProfit > 0L)
        assertTrue(g.hedges.all { it.amount <= it.originalStake })
    }
}
