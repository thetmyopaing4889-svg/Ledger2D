package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AgentStatementTest {
    private val day = LocalDate.of(2026, 10, 30)
    private fun bet(agent: Long, customer: Long, digit: String, amount: Long, rate: Long,
        session: DrawSession = DrawSession.MORNING) =
        AgentRatedStake(agent, customer, day, session, digit, amount, rate)
    private fun comm(agent: Long, customer: Long, amount: Long,
        session: DrawSession = DrawSession.MORNING) =
        AgentSavedCommission(agent, customer, day, session, amount)

    @Test fun allFourMetricsUseSavedRateAndNetCommissionOnlyOnce() {
        val report = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 2000, 80), bet(1, 10, "30", 98000, 80)),
            listOf(comm(1, 10, 15000)),
            mapOf((day to DrawSession.MORNING) to "20"),
        )
        assertEquals(100000L, report.totals.totalStake)
        assertEquals(15000L, report.totals.savedNetCommission)
        assertEquals(2000L, report.totals.winningStake)
        assertEquals(160000L, report.totals.payout)
        assertEquals(1, report.totals.completedDraws)
        assertEquals(0, report.totals.pendingDraws)
    }

    @Test fun pendingResultDoesNotPretendZeroPayoutOrExcludeStakeCommission() {
        val report = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 2000, 80)),
            listOf(comm(1, 10, 250)), emptyMap(),
        )
        assertEquals(2000L, report.totals.totalStake)
        assertEquals(250L, report.totals.savedNetCommission)
        assertEquals(2000L, report.totals.pendingStake)
        assertEquals(0, report.totals.completedDraws)
        assertEquals(1, report.totals.pendingDraws)
        assertNull(report.draws.single().payout)
        assertNull(report.draws.single().winningStake)
    }

    @Test fun winningDigitNotBetReturnsGenuineZeroAfterResult() {
        val report = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 2000, 80)),
            listOf(comm(1, 10, 250)),
            mapOf((day to DrawSession.MORNING) to "40"),
        )
        assertEquals(0L, report.draws.single().winningStake)
        assertEquals(0L, report.draws.single().payout)
        assertEquals(1, report.totals.completedDraws)
    }

    @Test fun sameCustomerNameAtDifferentAgentsNeverMergesAccounts() {
        val report = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 100000, 80), bet(2, 11, "20", 40000, 85),
                bet(3, 12, "30", 60000, 90)),
            listOf(comm(1, 10, 15000), comm(2, 11, 400), comm(3, 12, 600)),
            mapOf((day to DrawSession.MORNING) to "20"),
        )
        assertEquals(3, report.draws.size)
        assertEquals(setOf(1L, 2L, 3L), report.agentIds)
        assertEquals(200000L, report.totals.totalStake)
        assertEquals(16000L, report.totals.savedNetCommission)
        assertEquals(11400000L, report.totals.payout)
        assertEquals(15000L, report.draws.single { it.agentId == 1L }.commission)
        assertEquals(400L, report.draws.single { it.agentId == 2L }.commission)
    }

    @Test fun morningEveningAndSavedHistoricalRatesStayIndependent() {
        val bets = listOf(bet(1, 10, "20", 1000, 80),
            bet(1, 10, "20", 1000, 85),
            bet(1, 10, "20", 500, 90, DrawSession.EVENING))
        val commissions = listOf(comm(1, 10, 50), comm(1, 10, 60, DrawSession.EVENING))
        val winners = mapOf(
            (day to DrawSession.MORNING) to "20",
            (day to DrawSession.EVENING) to "20",
        )
        val all = AgentStatementCalculator.build(bets, commissions, winners)
        val morning = AgentStatementCalculator.build(bets, commissions, winners, DrawSession.MORNING)
        assertEquals(210000L, all.totals.payout)
        assertEquals(165000L, morning.totals.payout)
        assertEquals(50L, morning.totals.savedNetCommission)
        assertEquals(1, morning.totals.completedDraws)
    }

    @Test fun customerDrillDownContainsOnlyItsOwnCommissionAndDigitStakes() {
        val report = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 1000, 80), bet(1, 11, "30", 2000, 80)),
            listOf(comm(1, 10, 120), comm(1, 11, 240)),
            mapOf((day to DrawSession.MORNING) to "20"),
        )
        val customer = report.draws.single().forCustomer(10)
        assertEquals(1000L, customer.totalStake)
        assertEquals(120L, customer.commission)
        assertEquals(80000L, customer.payout)
        assertEquals(setOf("20"), customer.stakes.map { it.digit }.toSet())
    }
}
