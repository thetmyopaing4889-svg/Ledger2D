package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Customer-facing statement model: deliberately excludes all commission and agent-profit data. */
enum class StatementPeriod { DAY, WEEK, MONTH, YEAR, CUSTOM }

data class StatementRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) }
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

object StatementPeriods {
    fun range(period: StatementPeriod, anchor: LocalDate, customStart: LocalDate, customEnd: LocalDate): StatementRange = when (period) {
        StatementPeriod.DAY -> StatementRange(anchor, anchor)
        StatementPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { StatementRange(it, it.plusDays(4)) }
        StatementPeriod.MONTH -> StatementRange(anchor.withDayOfMonth(1), anchor.withDayOfMonth(anchor.lengthOfMonth()))
        StatementPeriod.YEAR -> StatementRange(anchor.withDayOfYear(1), anchor.withDayOfYear(anchor.lengthOfYear()))
        StatementPeriod.CUSTOM -> StatementRange(customStart, customEnd)
    }
    fun move(period: StatementPeriod, anchor: LocalDate, delta: Long): LocalDate = when (period) {
        StatementPeriod.DAY -> anchor.plusDays(delta)
        StatementPeriod.WEEK -> anchor.plusWeeks(delta)
        StatementPeriod.MONTH -> anchor.plusMonths(delta)
        StatementPeriod.YEAR -> anchor.plusYears(delta)
        StatementPeriod.CUSTOM -> anchor
    }
}

/** Aggregated SQL input; a rate is preserved for each winning stake, even in historical draws. */
data class StatementStake(
    val customerId: Long,
    val date: LocalDate,
    val session: DrawSession,
    val digit: String,
    val payoutRate: Long,
    val amount: Long,
)

data class StatementDraw(
    val customerId: Long,
    val date: LocalDate,
    val session: DrawSession,
    val slots: Int,
    val totalBet: Long,
    val winner: String?,
    val winningStake: Long?,
    val payout: Long?,
    val payoutRates: Set<Long>,
) {
    val resultKnown get() = winner != null
    /** Positive means the CUSTOMER won; negative means the CUSTOMER lost. */
    val customerWinLoss: Long? get() = payout?.let { Math.subtractExact(it, totalBet) }
}

data class StatementTotals(
    val totalBet: Long,
    val completedBet: Long,
    val winningStake: Long,
    val payout: Long,
    val customerWinLoss: Long,
    val slots: Long,
    val drawsWithBets: Int,
    val completedDraws: Int,
    val awaitingDraws: Int,
) {
    val isComplete get() = awaitingDraws == 0
}

data class CustomerStatement(
    val draws: List<StatementDraw>,
    val totals: StatementTotals,
    val customers: Map<Long, StatementTotals>,
)

object CustomerStatementCalculator {
    fun totals(draws: List<StatementDraw>): StatementTotals {
        var bet = 0L; var completedBet = 0L; var winning = 0L; var payout = 0L
        var slots = 0L; var completed = 0
        for (draw in draws) {
            bet = Math.addExact(bet, draw.totalBet)
            slots = Math.addExact(slots, draw.slots.toLong())
            if (draw.resultKnown) {
                completed++
                completedBet = Math.addExact(completedBet, draw.totalBet)
                winning = Math.addExact(winning, draw.winningStake ?: 0L)
                payout = Math.addExact(payout, draw.payout ?: 0L)
            }
        }
        return StatementTotals(
            totalBet = bet, completedBet = completedBet,
            winningStake = winning, payout = payout,
            customerWinLoss = Math.subtractExact(payout, completedBet),
            slots = slots, drawsWithBets = draws.size,
            completedDraws = completed, awaitingDraws = draws.size - completed,
        )
    }

    fun build(
        stakes: List<StatementStake>,
        winners: Map<Pair<LocalDate, DrawSession>, String>,
        selectedSession: DrawSession? = null,
    ): CustomerStatement {
        val draws = stakes.asSequence()
            .filter { selectedSession == null || it.session == selectedSession }
            .groupBy { Triple(it.customerId, it.date, it.session) }
            .map { (key, lines) ->
                val digit = winners[key.second to key.third]
                var bet = 0L; var winning = 0L; var payout = 0L
                for (line in lines) {
                    require(line.amount >= 0L && line.payoutRate >= 0L)
                    bet = Math.addExact(bet, line.amount)
                    if (digit != null && line.digit == digit) {
                        winning = Math.addExact(winning, line.amount)
                        payout = Math.addExact(payout, Math.multiplyExact(line.amount, line.payoutRate))
                    }
                }
                StatementDraw(
                    customerId = key.first, date = key.second, session = key.third,
                    slots = lines.map { it.digit }.distinct().size,
                    totalBet = bet, winner = digit,
                    winningStake = if (digit == null) null else winning,
                    payout = if (digit == null) null else payout,
                    payoutRates = lines.map { it.payoutRate }.toSet(),
                )
            }
            .sortedWith(compareBy<StatementDraw> { it.date }.thenBy { it.session.ordinal }.thenBy { it.customerId })
            .toList()
        return CustomerStatement(
            draws = draws, totals = totals(draws),
            customers = draws.groupBy { it.customerId }.mapValues { (_, items) -> totals(items) },
        )
    }
}
