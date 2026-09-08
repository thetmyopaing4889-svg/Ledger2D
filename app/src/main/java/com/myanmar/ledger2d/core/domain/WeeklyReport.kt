package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeeklyDrawRow(val date: LocalDate, val session: DrawSession, val calculation: DrawCalculation?, val winningDigit: String?)
data class WeeklyReport(val rows: List<WeeklyDrawRow>) {
    init { require(rows.size == 10) }
    val totalBet = rows.mapNotNull { it.calculation }.fold(0L) { sum, it -> Math.addExact(sum, it.totalBet) }
    val commission = rows.mapNotNull { it.calculation }.fold(0L) { sum, it -> Math.addExact(sum, it.commission) }
    val payout = rows.mapNotNull { it.calculation }.fold(0L) { sum, it -> Math.addExact(sum, it.payout) }
    val profitLoss = rows.mapNotNull { it.calculation }.fold(0L) { sum, it -> Math.addExact(sum, it.profitLoss) }
}
class WeeklyReportCalculator {
    fun build(anyDateInWeek: LocalDate, calculations: Map<Pair<LocalDate, DrawSession>, DrawCalculation>, winners: Map<Pair<LocalDate, DrawSession>, String> = emptyMap()): WeeklyReport {
        val monday = anyDateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val rows = (0L..4L).flatMap { offset -> DrawSession.entries.map { session -> val date = monday.plusDays(offset); val key = date to session; WeeklyDrawRow(date, session, calculations[key], winners[key]) } }
        return WeeklyReport(rows)
    }
}
