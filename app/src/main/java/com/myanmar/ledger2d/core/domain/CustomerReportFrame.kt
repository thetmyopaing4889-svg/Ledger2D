package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Display-only calendar frame: never changes statement financial data or selected dates. */
data class CustomerReportPage(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) }
}

data class CustomerReportSlot(val date: LocalDate, val session: DrawSession)

object CustomerReportFrame {
    /** Day and week have one continuous table. Larger selections page by calendar month. */
    fun pages(period: StatementPeriod, range: StatementRange): List<CustomerReportPage> {
        if (period == StatementPeriod.DAY || period == StatementPeriod.WEEK) {
            return listOf(CustomerReportPage(range.start, range.end))
        }
        val pages = mutableListOf<CustomerReportPage>()
        var first = range.start.withDayOfMonth(1)
        while (!first.isAfter(range.end)) {
            pages += CustomerReportPage(
                start = maxOf(first, range.start),
                end = minOf(first.plusMonths(1).minusDays(1), range.end),
            )
            first = first.plusMonths(1)
        }
        return pages
    }

    /**
     * Every selected workday has a frame even with no bets or confirmed winner.
     * A selected single weekend day is still displayed as closed, not a blank screen.
     * A missing draw filter means the morning and evening are separate rows.
     */
    fun slots(page: CustomerReportPage, selectedSession: DrawSession?, includeWeekend: Boolean = false): List<CustomerReportSlot> {
        val rows = mutableListOf<CustomerReportSlot>()
        var day = page.start
        while (!day.isAfter(page.end)) {
            val weekday = day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
            if (weekday || includeWeekend) {
                if (selectedSession == null) {
                    rows += CustomerReportSlot(day, DrawSession.MORNING)
                    rows += CustomerReportSlot(day, DrawSession.EVENING)
                } else {
                    rows += CustomerReportSlot(day, selectedSession)
                }
            }
            day = day.plusDays(1)
        }
        return rows
    }

    fun weekStart(day: LocalDate): LocalDate =
        day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
