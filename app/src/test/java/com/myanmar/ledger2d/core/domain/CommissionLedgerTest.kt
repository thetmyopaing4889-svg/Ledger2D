package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CommissionLedgerTest {
    private val fri = LocalDate.of(2026, 10, 30)
    private val sat = fri.plusDays(1)
    private val mon = fri.plusDays(3)

    private fun row(a: Long, c: Long, date: LocalDate, draw: DrawSession, amount: Long) =
        CommissionLedgerLine(a, "Agent " + a, c, "Customer " + c, date, draw, amount)

    @Test fun todayMorningEveningAndLifetimeIncludeAllCustomersAndAgentsExactlyOnce() {
        val lines = listOf(
            row(1, 10, fri, DrawSession.MORNING, 150),
            row(1, 11, fri, DrawSession.MORNING, 200),
            row(2, 20, fri, DrawSession.EVENING, 800),
            row(2, 20, fri.minusDays(1), DrawSession.MORNING, 500),
            row(3, 30, mon, DrawSession.MORNING, 9999),
        )
        val actual = CommissionLedgerCalculator.overview(lines, fri)
        assertEquals(350L, actual.morning)
        assertEquals(800L, actual.evening)
        assertEquals(1150L, actual.todayTotal)
        assertEquals(1650L, actual.toDateTotal)
    }

    @Test fun weekendShowsZeroDailyWithoutResettingAccumulatedCommission() {
        val lines = listOf(row(1, 10, fri, DrawSession.MORNING, 1000))
        val actual = CommissionLedgerCalculator.overview(lines, sat)
        assertEquals(0L, actual.morning)
        assertEquals(0L, actual.evening)
        assertEquals(0L, actual.todayTotal)
        assertEquals(1000L, actual.toDateTotal)
    }

    @Test fun detailFiltersReconcileWithHomeAndRespectAgentAndSession() {
        val lines = listOf(
            row(1, 10, fri, DrawSession.MORNING, 400),
            row(1, 11, fri, DrawSession.EVENING, 600),
            row(2, 20, fri, DrawSession.MORNING, 100),
        )
        val today = CommissionLedgerCalculator.overview(lines, fri)
        val range = StatementRange(fri, fri)
        assertEquals(today.todayTotal, CommissionLedgerCalculator.total(
            CommissionLedgerCalculator.select(lines, range)))
        assertEquals(400L, CommissionLedgerCalculator.total(
            CommissionLedgerCalculator.select(lines, range, 1, DrawSession.MORNING)))
        assertEquals(500L, CommissionLedgerCalculator.total(
            CommissionLedgerCalculator.select(lines, range, -1, DrawSession.MORNING)))
    }

    @Test fun retroactiveCorrectionUsesSavedAmountsRatherThanRecomputingRate() {
        val old = listOf(row(1, 10, fri, DrawSession.MORNING, 500))
        val edited = listOf(row(1, 10, fri, DrawSession.MORNING, 900))
        assertEquals(500L, CommissionLedgerCalculator.overview(old, fri).toDateTotal)
        assertEquals(900L, CommissionLedgerCalculator.overview(edited, fri).toDateTotal)
        assertEquals(0L, CommissionLedgerCalculator.overview(emptyList(), fri).toDateTotal)
    }
}
