package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import org.junit.Assert.*
import org.junit.Test

/**
 * Exhaustively compare the loss-budget search with every legal hedge combination
 * on two mixed-rate digits. The other 98 digits remain possible zero-payout results.
 * This catches subtle minimum-loss and remainder-boundary mistakes.
 */
class CustomerRiskBudgetOptimalityTest {
    private val analysis = AnalysisCalculator()
    private val planner = CustomerRiskPlanner()

    @Test fun mixedRateBudgetIsMinimumCostOrProperlyUnavailable() {
        val newHedgeRate = 3L
        val originalRates = listOf(2L, 3L, 5L)
        val budgets = listOf(0L, 1L, 3L, 6L, 15L)
        for (stakeA in 1L..3L) {
            for (stakeB in 1L..3L) {
                for (rateA in originalRates) {
                    for (rateB in originalRates) {
                        val total = stakeA + stakeB
                        val payoutA = stakeA * rateA
                        val payoutB = stakeB * rateB
                        val source = analysis.calculate(
                            mapOf("00" to stakeA, "01" to stakeB),
                            EffectiveLimits(null, emptyMap()), emptySet(),
                            newHedgeRate, mapOf("00" to payoutA, "01" to payoutB)
                        )
                        for (budget in budgets) {
                            val actual = planner.planForLossBudget(source, newHedgeRate, budget)
                            var minimumCost: Long? = null
                            for (hedgeA in 0L..stakeA) {
                                for (hedgeB in 0L..stakeB) {
                                    val spend = hedgeA + hedgeB
                                    val kept = total - spend
                                    val worstResidual = maxOf(
                                        0L, payoutA - newHedgeRate * hedgeA,
                                        payoutB - newHedgeRate * hedgeB
                                    )
                                    val worstLoss = maxOf(0L, worstResidual - kept)
                                    if (worstLoss <= budget &&
                                        (minimumCost == null || spend < minimumCost)) {
                                        minimumCost = spend
                                    }
                                }
                            }
                            val label = "A=$stakeA*$rateA B=$stakeB*$rateB budget=$budget"
                            if (minimumCost == null) {
                                assertNull(label, actual)
                            } else {
                                assertNotNull(label, actual)
                                assertEquals(label, minimumCost, actual!!.upstreamTotal)
                                assertTrue(label, actual.worstLoss <= budget)
                                assertTrue(label, actual.hedges.all {
                                    it.amount >= 0L && it.amount <= it.originalStake
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}
