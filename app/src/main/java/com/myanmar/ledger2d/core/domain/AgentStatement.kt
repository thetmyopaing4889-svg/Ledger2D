package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/** A separate agent/customer account is never silently merged with another agent. */
data class AgentRatedStake(
    val agentId: Long,
    val customerId: Long,
    val date: LocalDate,
    val session: DrawSession,
    val digit: String,
    val stake: Long,
    val savedPayoutRate: Long,
)

/** Already-saved NET commission; not the agent's gross commission agreement. */
data class AgentSavedCommission(
    val agentId: Long,
    val customerId: Long,
    val date: LocalDate,
    val session: DrawSession,
    val amount: Long,
)

data class AgentStatementDraw(
    val agentId: Long,
    val date: LocalDate,
    val session: DrawSession,
    val winner: String?,
    val stakes: List<AgentRatedStake>,
    val commissions: List<AgentSavedCommission>,
) {
    val totalStake: Long = stakes.fold(0L) { t, s -> Math.addExact(t, s.stake) }
    val commission: Long = commissions.fold(0L) { t, c -> Math.addExact(t, c.amount) }
    val winningStake: Long? = winner?.let { w ->
        stakes.filter { it.digit == w }.fold(0L) { t, s -> Math.addExact(t, s.stake) }
    }
    val payout: Long? = winner?.let { w ->
        stakes.filter { it.digit == w }.fold(0L) { t, s ->
            Math.addExact(t, Math.multiplyExact(s.stake, s.savedPayoutRate))
        }
    }
    val slots: Int = stakes.map { it.digit }.distinct().size
    val resultKnown: Boolean get() = winner != null
    fun forCustomer(id: Long): AgentStatementDraw = copy(
        stakes = stakes.filter { it.customerId == id },
        commissions = commissions.filter { it.customerId == id },
    )
}

data class AgentStatementTotals(
    val totalStake: Long,
    val savedNetCommission: Long,
    val completedStake: Long,
    val winningStake: Long,
    val payout: Long,
    val pendingStake: Long,
    val completedDraws: Int,
    val pendingDraws: Int,
    val digitSlots: Long,
) {
    val draws: Int get() = completedDraws + pendingDraws
}

data class AgentStatement(val draws: List<AgentStatementDraw>) {
    val totals: AgentStatementTotals = AgentStatementCalculator.summarize(draws)
    val agentIds: Set<Long> = draws.map { it.agentId }.toSet()
}

/**
 * Does not mark unfinished results as zero payout, count transferred bets only
 * once per recorded agent, or mistake saved net commission for a gross payment.
 */
object AgentStatementCalculator {
    fun build(
        stakes: List<AgentRatedStake>,
        commissions: List<AgentSavedCommission>,
        winners: Map<Pair<LocalDate, DrawSession>, String>,
        session: DrawSession? = null,
    ): AgentStatement {
        val eligibleStakes = stakes.filter { session == null || it.session == session }
        val eligibleCommissions = commissions.filter { session == null || it.session == session }
        val stakeGroups = eligibleStakes.groupBy { Triple(it.agentId, it.date, it.session) }
        val commissionGroups = eligibleCommissions.groupBy { Triple(it.agentId, it.date, it.session) }
        val keys = (stakeGroups.keys + commissionGroups.keys).distinct().sortedWith(
            compareBy<Triple<Long, LocalDate, DrawSession>> { it.second }
                .thenBy { it.third.ordinal }.thenBy { it.first }
        )
        return AgentStatement(keys.map { (agent, date, draw) ->
            AgentStatementDraw(
                agentId = agent, date = date, session = draw,
                winner = winners[date to draw],
                stakes = stakeGroups[Triple(agent, date, draw)].orEmpty(),
                commissions = commissionGroups[Triple(agent, date, draw)].orEmpty(),
            )
        })
    }

    fun summarize(draws: List<AgentStatementDraw>): AgentStatementTotals {
        var stake = 0L
        var commission = 0L
        var completedStake = 0L
        var payout = 0L
        var winningStake = 0L
        var pendingStake = 0L
        var completed = 0
        var pending = 0
        var slots = 0L
        for (draw in draws) {
            stake = Math.addExact(stake, draw.totalStake)
            commission = Math.addExact(commission, draw.commission)
            slots = Math.addExact(slots, draw.slots.toLong())
            if (draw.resultKnown) {
                completed++
                completedStake = Math.addExact(completedStake, draw.totalStake)
                payout = Math.addExact(payout, draw.payout ?: 0L)
                winningStake = Math.addExact(winningStake, draw.winningStake ?: 0L)
            } else {
                pending++
                pendingStake = Math.addExact(pendingStake, draw.totalStake)
            }
        }
        return AgentStatementTotals(stake, commission, completedStake, winningStake, payout, pendingStake, completed, pending, slots)
    }
}
