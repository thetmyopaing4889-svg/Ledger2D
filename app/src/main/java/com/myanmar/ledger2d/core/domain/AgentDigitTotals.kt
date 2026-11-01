package com.myanmar.ledger2d.core.domain

/** Agent-specific recorded stakes. The commission is the saved reseller NET commission. */
data class AgentDigitLine(
    val digit: String,
    val stake: Long,
    val netCommission: Long,
)

data class AgentDigitGroup(
    val agentId: Long,
    val agentName: String,
    val digits: List<AgentDigitLine>,
) {
    val totalStake: Long get() = digits.fold(0L) { acc, row -> Math.addExact(acc, row.stake) }
    val totalNetCommission: Long get() =
        digits.fold(0L) { acc, row -> Math.addExact(acc, row.netCommission) }
}

/**
 * Combines the ALREADY RECORDED rows of independent agents by 2D digit.
 * This is NOT a de-duplicated sale total: Self/forwarded copies are included.
 * Commission was allocated per bet entry upstream, so summing is safe here.
 */
object AgentDigitTotalsAggregator {
    fun combine(groups: List<AgentDigitGroup>): List<AgentDigitLine> {
        val stake = sortedMapOf<String, Long>()
        val commission = sortedMapOf<String, Long>()
        groups.forEach { group ->
            group.digits.forEach { row ->
                require(row.digit.length == 2 && row.digit.all { it in '0'..'9' }) {
                    "Invalid two-digit stake"
                }
                require(row.stake >= 0 && row.netCommission >= 0)
                stake[row.digit] = Math.addExact(stake[row.digit] ?: 0L, row.stake)
                commission[row.digit] = Math.addExact(
                    commission[row.digit] ?: 0L, row.netCommission
                )
            }
        }
        return stake.map { (digit, amount) ->
            AgentDigitLine(digit, amount, commission[digit] ?: 0L)
        }
    }
}
