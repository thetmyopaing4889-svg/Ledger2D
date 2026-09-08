package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyReportTest {
    private val calc=WeeklyReportCalculator()
    private val sample=DrawCalculation(100,1,10,800,15,-700)
    @Test fun weekly_always_has_ten_rows(){assertEquals(10,calc.build(LocalDate.of(2026,9,9),emptyMap()).rows.size)}
    @Test fun weekly_starts_monday_and_ends_friday(){val rows=calc.build(LocalDate.of(2026,9,9),emptyMap()).rows;assertEquals(DayOfWeek.MONDAY,rows.first().date.dayOfWeek);assertEquals(DayOfWeek.FRIDAY,rows.last().date.dayOfWeek)}
    @Test fun every_day_has_morning_and_evening(){val rows=calc.build(LocalDate.of(2026,9,9),emptyMap()).rows;for(chunk in rows.chunked(2))assertEquals(listOf(DrawSession.MORNING,DrawSession.EVENING),chunk.map{it.session})}
    @Test fun future_blank_rows_are_preserved(){val monday=LocalDate.of(2026,9,7);val report=calc.build(monday,mapOf((monday to DrawSession.MORNING) to sample));assertEquals(9,report.rows.count{it.calculation==null})}
    @Test fun total_row_aggregates_only_existing_data(){val monday=LocalDate.of(2026,9,7);val report=calc.build(monday,mapOf((monday to DrawSession.MORNING) to sample,(monday to DrawSession.EVENING) to sample));assertEquals(200,report.totalBet);assertEquals(30,report.commission);assertEquals(1600,report.payout);assertEquals(-1400,report.profitLoss)}
    @Test fun winning_digits_remain_scoped_by_draw(){val monday=LocalDate.of(2026,9,7);val winners=mapOf((monday to DrawSession.MORNING) to "13",(monday to DrawSession.EVENING) to "42");val r=calc.build(monday,emptyMap(),winners);assertEquals("13",r.rows[0].winningDigit);assertEquals("42",r.rows[1].winningDigit)}
}
