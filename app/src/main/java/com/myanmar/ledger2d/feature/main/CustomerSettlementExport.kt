package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * In-memory, read-only source for ONE customer and a selected date range.
 * This voucher-only commission is never written to the ledger or used by reports.
 */
internal data class CustomerSettlementSource(
    val customerName: String,
    val range: StatementRange,
    val records: List<BetEntryWithLines>,
    val winners: Map<Pair<LocalDate, DrawSession>, String>,
)

internal data class CustomerSettlementRow(
    val id: Long,
    val source: String,
    val digits: Int,
    val bet: Long,
    val winningStake: Long,
    val payout: Long,
    val winner: String,
)

internal data class CustomerSettlementDraw(
    val date: LocalDate,
    val session: DrawSession,
    val winner: String,
    val rows: List<CustomerSettlementRow>,
) {
    val digits: Long = rows.sumOf { it.digits.toLong() }
    val bet: Long = rows.fold(0L) { acc, row -> Math.addExact(acc, row.bet) }
    val winningStake: Long = rows.fold(0L) { acc, row -> Math.addExact(acc, row.winningStake) }
    val payout: Long = rows.fold(0L) { acc, row -> Math.addExact(acc, row.payout) }
}

internal data class CustomerSettlementDocument(
    val customerName: String,
    val range: StatementRange,
    val draws: List<CustomerSettlementDraw>,
    /** Null = do not show or subtract customer rebate. 0 = enabled, at 0%. */
    val rebateBasisPoints: Int?,
) {
    init { require(rebateBasisPoints == null || rebateBasisPoints in 0..10000) }
    val digits: Long = draws.fold(0L) { acc, d -> Math.addExact(acc, d.digits) }
    val totalBet: Long = draws.fold(0L) { acc, d -> Math.addExact(acc, d.bet) }
    val totalWinningStake: Long = draws.fold(0L) { acc, d -> Math.addExact(acc, d.winningStake) }
    val totalPayout: Long = draws.fold(0L) { acc, d -> Math.addExact(acc, d.payout) }
    val rebateAmount: Long = rebateBasisPoints?.let { bp ->
        BigDecimal.valueOf(totalBet).multiply(BigDecimal.valueOf(bp.toLong()))
            .divide(BigDecimal.valueOf(10000L), 0, RoundingMode.HALF_UP).longValueExact()
    } ?: 0L
    val totalDeduction: Long = Math.addExact(totalPayout, rebateAmount)
    /** Positive => Customer မှ ပေးရန်; negative => Customer ကို ပေးရန်. */
    val settlement: Long = Math.subtractExact(totalBet, totalDeduction)
}

/** Percentage accepts up to two decimal places, so 5.25% = 525 basis points. */
internal fun customerRebateBasisPoints(input: String): Int? {
    val value = input.trim().toBigDecimalOrNull() ?: return null
    if (value < BigDecimal.ZERO || value > BigDecimal(100)) return null
    return runCatching { value.movePointRight(2).intValueExact() }.getOrNull()
}

internal object CustomerSettlementCalculator {
    fun build(source: CustomerSettlementSource, rebateBasisPoints: Int?): CustomerSettlementDocument {
        require(rebateBasisPoints == null || rebateBasisPoints in 0..10000)
        require(source.records.isNotEmpty()) { "ရွေးထားသောကာလအတွင်း ထိုးစာရင်းမရှိပါ။" }
        val customerIds = source.records.map { it.entry.customerId }.toSet()
        require(customerIds.size == 1) { "Customer တစ်ယောက်စာပဲ ထုတ်နိုင်ပါသည်။" }
        require(source.records.all { source.range.contains(it.entry.drawDate) })

        val sorted = source.records.sortedWith(
            compareBy<BetEntryWithLines> { it.entry.drawDate }
                .thenBy { it.entry.drawSession.ordinal }
                .thenBy { it.entry.createdAt }
                .thenBy { it.entry.id },
        )
        val draws = sorted.groupBy { it.entry.drawDate to it.entry.drawSession }
            .map { (key, records) ->
                // Only committed final results may generate a financial statement.
                val winner = source.winners[key]?.takeIf { it.matches(Regex("[0-9]{2}")) }
                    ?: throw IllegalStateException(
                        "${key.first} ${if (key.second == DrawSession.MORNING) "မနက်အကြိမ်" else "ညနေအကြိမ်"} " +
                            "ပေါက်ဂဏန်း အတည်မပြုရသေး၍ ငွေရှင်း JPEG မထုတ်နိုင်ပါ။",
                    )
                CustomerSettlementDraw(
                    date = key.first, session = key.second, winner = winner,
                    rows = records.map { record ->
                        val bet = record.lines.fold(0L) { acc, line ->
                            require(line.amount >= 0L)
                            Math.addExact(acc, line.amount)
                        }
                        val winningStake = record.lines.filter { it.digit == winner }
                            .fold(0L) { acc, line -> Math.addExact(acc, line.amount) }
                        val payout = if (winningStake == 0L) 0L else {
                            require(record.entry.payoutRate > 0L) {
                                "စာရင်း၏ လျော်ကြေးအဆ မမှန်သဖြင့် JPEG မထုတ်နိုင်ပါ။"
                            }
                            Math.multiplyExact(winningStake, record.entry.payoutRate)
                        }
                        CustomerSettlementRow(
                            id = record.entry.id,
                            source = transactionDisplayText(record.entry.inputFormat, record.entry.sourceText),
                            digits = record.lines.size,
                            bet = bet, winningStake = winningStake, payout = payout, winner = winner,
                        )
                    },
                )
            }
        return CustomerSettlementDocument(source.customerName, source.range, draws, rebateBasisPoints)
    }
}
