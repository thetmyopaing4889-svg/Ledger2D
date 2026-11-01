package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/** One saved customer's commission for one agent/session. No bet-line join to duplicate money. */
data class CommissionLedgerLine(
    val agentId: Long,
    val agentName: String,
    val customerId: Long,
    val customerName: String,
    val date: LocalDate,
    val session: DrawSession,
    val amount: Long,
)

data class HomeCommissionOverview(
    val morning: Long,
    val evening: Long,
    val todayTotal: Long,
    val toDateTotal: Long,
)

/** Always uses the amount saved with each entry, never today's customer rate. */
object CommissionLedgerCalculator {
    fun total(lines: List<CommissionLedgerLine>): Long =
        lines.fold(0L) { sum, line -> Math.addExact(sum, line.amount) }

    fun overview(lines: List<CommissionLedgerLine>, today: LocalDate): HomeCommissionOverview {
        val throughToday = lines.filter { !it.date.isAfter(today) }
        val morning = total(throughToday.filter { it.date == today && it.session == DrawSession.MORNING })
        val evening = total(throughToday.filter { it.date == today && it.session == DrawSession.EVENING })
        return HomeCommissionOverview(morning, evening, Math.addExact(morning, evening), total(throughToday))
    }

    fun select(
        lines: List<CommissionLedgerLine>,
        range: StatementRange,
        agentId: Long = -1L,
        session: DrawSession? = null,
    ): List<CommissionLedgerLine> {
        require(agentId == -1L || agentId > 0L)
        return lines.filter {
            range.contains(it.date) && (agentId == -1L || it.agentId == agentId) &&
                (session == null || it.session == session)
        }
    }
}
