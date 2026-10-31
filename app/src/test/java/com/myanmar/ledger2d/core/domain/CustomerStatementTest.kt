package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CustomerStatementTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val am = DrawSession.MORNING
    private val pm = DrawSession.EVENING
    private fun stake(id: Long, session: DrawSession, digit: String, amount: Long, rate: Long): StatementStake =
        StatementStake(id, monday, session, digit, rate, amount)

    @Test fun dayWeekMonthYearAndCrossYearCustomAreInclusive() {
        val a = LocalDate.of(2026,10,28)
        assertEquals(StatementRange(a,a), StatementPeriods.range(StatementPeriod.DAY,a,a,a))
        assertEquals(StatementRange(monday,monday.plusDays(4)), StatementPeriods.range(StatementPeriod.WEEK,a,a,a))
        assertEquals(LocalDate.of(2026,10,31),StatementPeriods.range(StatementPeriod.MONTH,a,a,a).end)
        assertEquals(LocalDate.of(2026,12,31),StatementPeriods.range(StatementPeriod.YEAR,a,a,a).end)
        val start=LocalDate.of(2026,12,30);val end=LocalDate.of(2027,1,4)
        val custom=StatementPeriods.range(StatementPeriod.CUSTOM,a,start,end)
        assertTrue(custom.contains(start));assertTrue(custom.contains(end))
        assertFalse(custom.contains(start.minusDays(1)))
        assertTrue(runCatching { StatementRange(end,start) }.isFailure)
        assertEquals(LocalDate.of(2026,11,28),StatementPeriods.move(StatementPeriod.MONTH,a,1))
    }

    @Test fun customerWinsArePayoutMinusBetWithoutCommissionAndPendingNeverCountsAsZeroResult() {
        val lines=listOf(
            stake(1,am,"20",1000,80),
            stake(1,am,"30",500,80),
            stake(2,am,"20",2000,90),
            stake(1,pm,"20",500,85),
        )
        val report=CustomerStatementCalculator.build(lines,mapOf((monday to am) to "20"))
        assertEquals(3,report.draws.size)
        assertEquals(4000L,report.totals.totalBet)
        assertEquals(3500L,report.totals.completedBet)
        assertEquals(3000L,report.totals.winningStake)
        assertEquals(260000L,report.totals.payout)
        assertEquals(256500L,report.totals.customerWinLoss)
        assertEquals(4L,report.totals.slots)
        assertEquals(2,report.totals.completedDraws)
        assertEquals(1,report.totals.awaitingDraws)
        val pending=report.draws.single{it.session==pm}
        assertNull(pending.winningStake);assertNull(pending.payout);assertNull(pending.customerWinLoss)
        assertEquals(78500L,report.customers.getValue(1).customerWinLoss)
        assertEquals(178000L,report.customers.getValue(2).customerWinLoss)
        assertEquals(report.totals.totalBet,report.customers.values.sumOf{it.totalBet})
        assertEquals(report.totals.payout,report.customers.values.sumOf{it.payout})
    }

    @Test fun winDigitWithoutStakeMeansRealZeroPayoutNotPending() {
        val report=CustomerStatementCalculator.build(listOf(stake(1,am,"20",1000,80)),mapOf((monday to am) to "42"))
        assertEquals(0L,report.draws.single().payout)
        assertEquals(-1000L,report.totals.customerWinLoss)
        assertEquals(0,report.totals.awaitingDraws)
    }

    @Test fun morningFilterPreservesOnlyMorningAndRateSnapshots() {
        val lines=listOf(stake(1,am,"20",100,80),stake(1,am,"20",200,90),stake(1,pm,"20",1000,85))
        val winners=mapOf((monday to am) to "20",(monday to pm) to "20")
        val morning=CustomerStatementCalculator.build(lines,winners,am)
        assertEquals(1,morning.draws.size)
        assertEquals(300L,morning.totals.totalBet)
        assertEquals(26000L,morning.totals.payout)
        assertEquals(setOf(80L,90L),morning.draws.single().payoutRates)
        assertEquals(1,morning.draws.single().slots)
        val all=CustomerStatementCalculator.build(lines,winners)
        assertEquals(2,all.draws.size)
        assertEquals(111000L,all.totals.payout)
    }

    @Test fun allCustomersShareOneMorningOrEveningJobRegardlessOfCustomerCount() {
        val lines = listOf(
            stake(1,am,"20",1000,80),
            stake(2,am,"20",2000,80),
            stake(3,am,"30",3000,80),
            stake(1,pm,"40",100,80),
            stake(2,pm,"41",200,80),
        )
        val result = CustomerStatementCalculator.build(lines, mapOf((monday to am) to "20"))
        // Five customer/draw rows, but only TWO distinct draw jobs.
        assertEquals(5, result.draws.size)
        assertEquals(3, result.totals.completedDraws)
        assertEquals(2, result.totals.awaitingDraws)
        val counted = statementDrawCounts(result.draws)
        assertEquals(2, counted.jobs)
        assertEquals(1, counted.completed)
        assertEquals(1, counted.awaiting)
    }

    @Test fun drawAggregatesRecalculateAfterEditedOrDeletedEntries() {
        val base=listOf(stake(1,am,"20",500,80),stake(2,am,"20",1000,90))
        val win=mapOf((monday to am) to "20")
        val initial=CustomerStatementCalculator.build(base,win)
        val corrected=CustomerStatementCalculator.build(listOf(stake(1,am,"20",200,80)),win)
        assertEquals(130000L,initial.totals.payout)
        assertEquals(16000L,corrected.totals.payout)
        assertEquals(200L,corrected.totals.winningStake)
        assertEquals(1,corrected.customers.size)
        assertEquals(0L,CustomerStatementCalculator.build(emptyList(),win).totals.totalBet)
    }
}
