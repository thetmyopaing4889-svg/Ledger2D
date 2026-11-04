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
        assertEquals(-60000L, report.totals.profitLoss)
        assertEquals(-45000L, report.totals.netProfitLoss)
        assertEquals(15000L, report.totals.completedNetCommission)
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
        assertNull(report.totals.profitLoss)
        assertNull(report.totals.netProfitLoss)
        assertEquals(0L, report.totals.completedNetCommission)
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
        assertEquals(2000L, report.totals.profitLoss)
        assertEquals(2250L, report.totals.netProfitLoss)
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
        assertEquals(-79000L, customer.profitLoss)
        assertEquals(-78880L, customer.netProfitLoss)
    }

    @Test fun mixedCompletedAndPendingSessionsKeepFourMoneyAmountsConsistent() {
        val stakes = listOf(
            bet(1, 10, "20", 1000, 80, DrawSession.MORNING),
            bet(1, 10, "30", 500, 85, DrawSession.EVENING),
        )
        val commissions = listOf(
            comm(1, 10, 150, DrawSession.MORNING),
            comm(1, 10, 75, DrawSession.EVENING),
        )
        val statement = AgentStatementCalculator.build(
            stakes, commissions,
            mapOf((day to DrawSession.EVENING) to "30"),
        )
        assertEquals(1500L, statement.totals.totalStake)
        assertEquals(225L, statement.totals.savedNetCommission)
        assertEquals(1000L, statement.totals.pendingStake)
        assertEquals(500L, statement.totals.completedStake)
        assertEquals(500L, statement.totals.winningStake)
        assertEquals(42500L, statement.totals.payout)
        assertEquals(-42000L, statement.totals.profitLoss)
        // The morning session's 150-kyat commission is still shown, but is not realized yet.
        assertEquals(225L, statement.totals.savedNetCommission)
        assertEquals(75L, statement.totals.completedNetCommission)
        assertEquals(-41925L, statement.totals.netProfitLoss)
        assertEquals(1, statement.totals.completedDraws)
        assertEquals(1, statement.totals.pendingDraws)
        assertNull(statement.draws.single { it.session == DrawSession.MORNING }.payout)
        assertEquals(42500L, statement.draws.single { it.session == DrawSession.EVENING }.payout)
    }

    @Test fun reenteredAndDeletedFinalWinnerRecalculateWithoutChangingAcceptedBets() {
        val stakes = listOf(bet(1, 10, "20", 1000, 80), bet(1, 10, "30", 2000, 85))
        val commission = listOf(comm(1, 10, 300))
        val original = AgentStatementCalculator.build(
            stakes, commission, mapOf((day to DrawSession.MORNING) to "20"))
        val edited = AgentStatementCalculator.build(
            stakes, commission, mapOf((day to DrawSession.MORNING) to "30"))
        val deleted = AgentStatementCalculator.build(stakes, commission, emptyMap())
        assertEquals(80000L, original.totals.payout)
        assertEquals(170000L, edited.totals.payout)
        assertEquals(3000L, original.totals.totalStake)
        assertEquals(3000L, edited.totals.totalStake)
        assertEquals(3000L, deleted.totals.pendingStake)
        assertEquals(300L, deleted.totals.savedNetCommission)
        assertNull(deleted.draws.single().payout)
        assertEquals(-77000L, original.totals.profitLoss)
        assertEquals(-76700L, original.totals.netProfitLoss)
        assertEquals(-167000L, edited.totals.profitLoss)
        assertEquals(-166700L, edited.totals.netProfitLoss)
        assertNull(deleted.totals.netProfitLoss)
    }

    @Test fun trulyEmptyPeriodDoesNotLookLikeAPendingSession() {
        val empty = AgentStatementCalculator.build(emptyList(), emptyList(), emptyMap())
        assertEquals(0, empty.totals.draws)
        assertEquals(0, empty.totals.pendingDraws)
        assertEquals(0, empty.totals.completedDraws)
    }

    @Test fun agentSettlementDeductsSavedCustomerCommissionsAndPayout() {
        val noWin = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 115000, 80)),
            listOf(comm(1, 10, 17250)),
            mapOf((day to DrawSession.MORNING) to "40"))
        assertEquals(97750L, noWin.totals.settlementAmount)
        assertEquals(97750L, noWin.draws.single().settlementAmount)

        val winning = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 200000, 80)),
            listOf(comm(1, 10, 30000)),
            mapOf((day to DrawSession.MORNING) to "20"))
        assertEquals(-15830000L, winning.totals.settlementAmount)

        val pending = AgentStatementCalculator.build(
            listOf(bet(1, 10, "20", 2000, 80)),
            listOf(comm(1, 10, 300)), emptyMap())
        assertNull(pending.draws.single().settlementAmount)
        assertNull(pending.totals.settlementAmount)
    }


    @Test fun originalBetLineCountSumsPerCustomerAndAgentWithRepeatDigits() {
        val stakes = listOf(
            bet(1, 10, "00", 106000, 80).copy(slotCount = 51),
            bet(1, 11, "00", 180000, 80).copy(slotCount = 55),
        )
        val savedCommissions = listOf(comm(1, 10, 1000), comm(1, 11, 2000))
        val statement = AgentStatementCalculator.build(
            stakes, savedCommissions, mapOf((day to DrawSession.MORNING) to "00"),
        )
        assertEquals(106, statement.draws.single().slots)
        assertEquals(106L, statement.totals.digitSlots)
        assertEquals(51, statement.draws.single().forCustomer(10).slots)
        assertEquals(55, statement.draws.single().forCustomer(11).slots)
        assertEquals(286000L, statement.totals.totalStake)
        assertEquals(3000L, statement.totals.savedNetCommission)
        assertEquals(22880000L, statement.totals.payout)
        assertEquals(-22597000L, statement.totals.settlementAmount)
    }

    @Test fun separateAgentsAndSavedRatesKeepCountsIndependent() {
        val stakes = listOf(
            bet(1, 10, "00", 1000, 80).copy(slotCount = 5),
            bet(1, 10, "00", 2000, 90).copy(slotCount = 2),
            bet(2, 20, "00", 3000, 85).copy(slotCount = 3),
        )
        val report = AgentStatementCalculator.build(
            stakes, emptyList(), mapOf((day to DrawSession.MORNING) to "00"),
        )
        assertEquals(10L, report.totals.digitSlots)
        assertEquals(7, report.draws.single { it.agentId == 1L }.slots)
        assertEquals(3, report.draws.single { it.agentId == 2L }.slots)
        assertEquals(515000L, report.totals.payout)
    }

    @Test fun incompleteAndMultipleAgentSettlementsKeepMoneyDirectionsSeparate() {
        val stakes = listOf(
            bet(1, 10, "20", 1000, 80),
            bet(1, 10, "30", 5000, 80, DrawSession.EVENING),
            bet(2, 11, "30", 2000, 80))
        val commissions = listOf(
            comm(1, 10, 100),
            comm(1, 10, 500, DrawSession.EVENING),
            comm(2, 11, 200))
        val winners = mapOf((day to DrawSession.MORNING) to "20")
        val report = AgentStatementCalculator.build(stakes, commissions, winners)
        // Morning agent 1: 1000 - 100 - 80000 = -79100; agent 2: 2000 - 200 = +1800.
        // The pending evening 5000 stake / 500 commission must not be settled yet.
        assertEquals(-77300L, report.totals.settlementAmount)
        assertEquals(1800L to 79100L, AgentStatementCalculator.settlementByAgent(report.draws))
        assertEquals(5000L, report.totals.pendingStake)
    }
}
