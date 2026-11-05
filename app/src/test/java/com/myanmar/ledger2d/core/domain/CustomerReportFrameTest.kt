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
}
