package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CustomerReportFrameTest {
    @Test fun weekFrameRetainsFiveWorkingDaysAndBothDrawsWithoutBets() {
        val range = StatementPeriods.range(
            StatementPeriod.WEEK, LocalDate.of(2026,11,5),
            LocalDate.of(2026,11,5), LocalDate.of(2026,11,5)
        )
        val pages = CustomerReportFrame.pages(StatementPeriod.WEEK, range)
        assertEquals(LocalDate.of(2026,11,2), pages.single().start)
        assertEquals(LocalDate.of(2026,11,6), pages.single().end)
        val slots = CustomerReportFrame.slots(pages.single(), null)
        assertEquals(10, slots.size)
        assertEquals(CustomerReportSlot(LocalDate.of(2026,11,2), DrawSession.MORNING), slots.first())
        assertEquals(CustomerReportSlot(LocalDate.of(2026,11,6), DrawSession.EVENING), slots.last())
    }

    @Test fun monthFrameHasEveryWorkingDayAndNeverWeekends() {
        val month = StatementPeriods.range(
            StatementPeriod.MONTH, LocalDate.of(2026,11,5),
            LocalDate.of(2026,11,5), LocalDate.of(2026,11,5)
        )
        val page = CustomerReportFrame.pages(StatementPeriod.MONTH,month).single()
        assertEquals(LocalDate.of(2026,11,1),page.start)
        assertEquals(LocalDate.of(2026,11,30),page.end)
        val slots = CustomerReportFrame.slots(page, null)
        assertEquals(42, slots.size) // 21 weekdays * two independent draws
        assertTrue(slots.all { it.date.dayOfWeek.value <= 5 })
        assertEquals(21, CustomerReportFrame.slots(page, DrawSession.EVENING).size)
    }

    @Test fun yearHasTwelvePagesIncludingEmptyMonths() {
        val period = StatementPeriods.range(
            StatementPeriod.YEAR, LocalDate.of(2026,11,5),
            LocalDate.of(2026,11,5), LocalDate.of(2026,11,5)
        )
        val pages = CustomerReportFrame.pages(StatementPeriod.YEAR, period)
        assertEquals(12,pages.size)
        assertEquals(LocalDate.of(2026,1,1),pages.first().start)
        assertEquals(LocalDate.of(2026,12,31),pages.last().end)
    }

    @Test fun customRangeClipsFirstAndLastMonthsAndHonorsSession() {
        val range = StatementRange(LocalDate.of(2026,3,15), LocalDate.of(2026,5,10))
        val pages = CustomerReportFrame.pages(StatementPeriod.CUSTOM,range)
        assertEquals(3,pages.size)
        assertEquals(LocalDate.of(2026,3,15),pages.first().start)
        assertEquals(LocalDate.of(2026,3,31),pages.first().end)
        assertEquals(LocalDate.of(2026,5,1),pages.last().start)
        assertEquals(LocalDate.of(2026,5,10),pages.last().end)
        assertTrue(pages.flatMap { CustomerReportFrame.slots(it, DrawSession.MORNING) }.all { it.session == DrawSession.MORNING && range.contains(it.date) })
    }

    @Test fun singleWeekendSelectionStillHasAClosedFrame() {
        val day = LocalDate.of(2026,11,7)
        val page = CustomerReportFrame.pages(StatementPeriod.DAY,StatementRange(day,day)).single()
        assertTrue(CustomerReportFrame.slots(page,null).isEmpty())
        assertEquals(2,CustomerReportFrame.slots(page,null,includeWeekend=true).size)
    }

    @Test fun allCustomerBalancesAreNeverSilentlyNetted() {
        val day = LocalDate.of(2026,11,5)
        val draws = listOf(
            StatementDraw(1,day,DrawSession.MORNING,1,100_000,"20",2_000,160_000,setOf(80)),
            StatementDraw(2,day,DrawSession.MORNING,1,100_000,"20",0,0,setOf(80))
        )
        assertEquals(100_000L to 60_000L,CustomerStatementCalculator.settlementByCustomer(draws))
    }

    @Test fun yearViewMonthTotalOnlyUsesVisibleMonthAndKeepsPendingSeparate() {
        val pages = CustomerReportFrame.pages(StatementPeriod.YEAR,
            StatementRange(LocalDate.of(2026,1,1), LocalDate.of(2026,12,31)))
        val october = LocalDate.of(2026,10,30)
        val november = LocalDate.of(2026,11,5)
        val december = LocalDate.of(2026,12,1)
        val draws = listOf(
            StatementDraw(1L, october, DrawSession.MORNING, 7, 500_000L, "13", 0L, 0L, setOf(80L)),
            StatementDraw(1L, november, DrawSession.MORNING, 3, 100_000L, "20", 1_000L, 80_000L, setOf(80L)),
            StatementDraw(2L, november, DrawSession.EVENING, 5, 200_000L, null, null, null, setOf(80L)),
            StatementDraw(1L, december, DrawSession.MORNING, 9, 900_000L, "25", 0L, 0L, setOf(80L))
        )
        val pageDraws = CustomerReportFrame.drawsOnPage(pages[10], draws)
        assertEquals(2, pageDraws.size)
        val totals = CustomerStatementCalculator.totals(pageDraws)
        assertEquals(8L, totals.slots)
        assertEquals(300_000L, totals.totalBet)
        assertEquals(100_000L, totals.completedBet)
        assertEquals(1_000L, totals.winningStake)
        assertEquals(80_000L, totals.payout)
        assertEquals(1, totals.awaitingDraws)
        assertEquals(20_000L to 0L, CustomerStatementCalculator.settlementByCustomer(pageDraws))
        assertEquals(100_000L, CustomerStatementCalculator.totals(pageDraws.filter { it.customerId == 1L }).totalBet)
        assertEquals(200_000L, CustomerStatementCalculator.totals(pageDraws.filter { it.customerId == 2L }).totalBet)
    }

    @Test fun monthNavigationNeverDriftsAfterTheThirtyFirst() {
        val first = LocalDate.of(2026, 1, 31)
        val feb = CustomerReportFrame.shiftedMonth(first, 1)
        val mar = CustomerReportFrame.shiftedMonth(feb, 1)
        assertEquals(LocalDate.of(2026,2,1),feb)
        assertEquals(LocalDate.of(2026,3,1),mar)
        assertEquals(LocalDate.of(2026,1,1),CustomerReportFrame.shiftedMonth(feb,-1))
        assertEquals(StatementRange(LocalDate.of(2026,3,1),LocalDate.of(2026,3,31)),
            StatementPeriods.range(StatementPeriod.MONTH,mar,mar,mar))
    }

    @Test fun emptySelectedMonthStillShowsEachWorkdayInBothSessions() {
        val page=CustomerReportPage(LocalDate.of(2026,2,1),LocalDate.of(2026,2,28))
        val slots=CustomerReportFrame.slots(page,null)
        assertEquals(40, slots.size) // 20 weekdays, two draws
        assertTrue(CustomerReportFrame.drawsOnPage(page,emptyList()).isEmpty())
        assertEquals(20, CustomerReportFrame.slots(page,DrawSession.MORNING).size)
    }

    @Test fun selectedMonthHasNoNavigationEvenWhenAdjacentMonthsHaveData() {
        val november=StatementPeriods.range(
            StatementPeriod.MONTH,LocalDate.of(2026,11,5),
            LocalDate.of(2026,11,5),LocalDate.of(2026,11,5))
        assertFalse(CustomerReportFrame.showMonthPager(StatementPeriod.MONTH,november))
        assertEquals(1,CustomerReportFrame.pages(StatementPeriod.MONTH,november).size)
        assertFalse(CustomerReportFrame.showMonthPager(
            StatementPeriod.DAY,StatementRange(LocalDate.of(2026,11,5),LocalDate.of(2026,11,5))))
        assertFalse(CustomerReportFrame.showMonthPager(
            StatementPeriod.WEEK,StatementRange(LocalDate.of(2026,11,2),LocalDate.of(2026,11,6))))
    }

    @Test fun onlyMultiMonthSelectionsEnablePagerAndStayInsideRange() {
        val year=StatementRange(LocalDate.of(2026,1,1),LocalDate.of(2026,12,31))
        assertTrue(CustomerReportFrame.showMonthPager(StatementPeriod.YEAR,year))
        val custom=StatementRange(LocalDate.of(2026,3,15),LocalDate.of(2026,5,10))
        assertTrue(CustomerReportFrame.showMonthPager(StatementPeriod.CUSTOM,custom))
        val pages=CustomerReportFrame.pages(StatementPeriod.CUSTOM,custom)
        assertTrue(pages.all { !it.start.isBefore(custom.start) && !it.end.isAfter(custom.end) })
        val sameMonth=StatementRange(LocalDate.of(2026,11,5),LocalDate.of(2026,11,20))
        assertFalse(CustomerReportFrame.showMonthPager(StatementPeriod.CUSTOM,sameMonth))
    }

    @Test fun monthWeekBandsSeparateConsecutiveWeeksWithoutSplittingLedger() {
        val page=CustomerReportPage(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30))
        assertEquals(0L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,11,2)))
        assertEquals(0L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,11,6)))
        assertEquals(1L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,11,9)))
        assertEquals(3L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,11,27)))
        assertEquals(4L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,11,30)))
    }

    @Test fun saturdayMonthStartDoesNotSkipFirstVisibleWeek() {
        val page=CustomerReportPage(LocalDate.of(2026,8,1),LocalDate.of(2026,8,31))
        val rows=CustomerReportFrame.slots(page,null)
        assertEquals(LocalDate.of(2026,8,3),rows.first().date)
        assertEquals(0L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,8,3)))
        assertEquals(1L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,8,10)))
        assertEquals(4L,CustomerReportFrame.weekBandIndex(page,LocalDate.of(2026,8,31)))
    }
}
