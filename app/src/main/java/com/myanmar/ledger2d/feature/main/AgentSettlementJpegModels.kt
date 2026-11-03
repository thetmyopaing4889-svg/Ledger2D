package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.domain.AgentStatement
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession

/** One row is one customer's TOTAL for the entire requested period, not one day. */
internal data class AgentSettlementCustomerRow(
    val customerId: Long,
    val name: String,
    val digits: Long,
    val bet: Long,
    val winningStake: Long,
    val payout: Long,
    val commission: Long,
)

internal data class AgentSettlementDocument(
    val agentName: String,
    val range: StatementRange,
    val rows: List<AgentSettlementCustomerRow>,
    val selectedSession: DrawSession? = null,
) {
    val digits: Long = rows.fold(0L) { sum, row -> Math.addExact(sum, row.digits) }
    val totalBet: Long = rows.fold(0L) { sum, row -> Math.addExact(sum, row.bet) }
    val totalWinningStake: Long = rows.fold(0L) { sum, row -> Math.addExact(sum, row.winningStake) }
    val totalPayout: Long = rows.fold(0L) { sum, row -> Math.addExact(sum, row.payout) }
    /** Agent Report's saved NET commission. Never derive a gross rate or use customer JPEG rebate. */
    val commission: Long = rows.fold(0L) { sum, row -> Math.addExact(sum, row.commission) }
    val totalDeduction: Long = Math.addExact(totalPayout, commission)
    /** Positive: User pays agent. Negative: agent pays User. */
    val settlement: Long = Math.subtractExact(totalBet, totalDeduction)
}

/**
 * Read-only statement calculator. Reuses CustomerSettlementCalculator for every customer
 * so the detail JPEG TOTAL and the agent's per-customer row cannot diverge.
 * Independently cross-checks stakes, payouts and saved commission with Agent Report.
 */
internal object AgentSettlementJpegCalculator {
    fun build(
        agentId: Long,
        agentName: String,
        range: StatementRange,
        records: List<BetEntryWithLines>,
        customerNames: Map<Long, String>,
        agentStatement: AgentStatement,
        selectedSession: DrawSession? = null,
    ): AgentSettlementDocument {
        require(agentId > 0L && agentName.isNotBlank())
        require(records.isNotEmpty()) { "ရွေးထားသောကာလအတွင်း ဒိုင်စာရင်းမရှိပါ။" }
        require(records.all {
            it.entry.agentId == agentId && range.contains(it.entry.drawDate) &&
                (selectedSession == null || it.entry.drawSession == selectedSession)
        }) { "ဒိုင်၊ ရက်စွဲ သို့မဟုတ် အကြိမ် စာရင်းမကိုက်ပါ။" }
        require(records.map { it.entry.id }.distinct().size == records.size) {
            "ထိုးစာရင်း ထပ်နေပါသည်။"
        }
        require(records.all { record ->
            record.lines.isNotEmpty() && record.lines.all {
                it.digit.matches(Regex("[0-9]{2}")) && it.amount > 0L
            }
        }) { "ထိုးကွက် သို့မဟုတ် ထိုးကြေး မှတ်တမ်း မမှန်ပါ။" }
        // A payout rate is agent+draw scoped; a commission rate is customer+draw
        // scoped. Historical edits must not quietly create different snapshots
        // in the same morning/evening draw and still yield an apparently valid JPEG.
        records.groupBy { it.entry.drawDate to it.entry.drawSession }.forEach { (draw, entries) ->
            check(entries.map { it.entry.payoutRate }.distinct().size == 1 &&
                entries.all { it.entry.payoutRate > 0L }) {
                "$draw ဒိုင်လျော်ကြေးအဆ မှတ်တမ်းများ မကိုက်ညီပါ။"
            }
        }
        records.groupBy { Triple(it.entry.customerId, it.entry.drawDate, it.entry.drawSession) }
            .forEach { (customerDraw, entries) ->
                check(entries.map { it.entry.commissionRateBasisPoints }.distinct().size == 1 &&
                    entries.all {
                        it.entry.commissionRateBasisPoints in 0..10000 &&
                            it.entry.commissionAmount >= 0L
                    }) {
                    "$customerDraw ကော်မရှင်နှုန်းထား မှတ်တမ်းများ မကိုက်ညီပါ။"
                }
            }
        require(agentStatement.agentIds == setOf(agentId)) {
            "ဒိုင် Report နှင့် စာရင်းမကိုက်ပါ။"
        }
        require(agentStatement.draws.all { range.contains(it.date) &&
            (selectedSession == null || it.session == selectedSession) })
        val winners = agentStatement.draws.associate { draw ->
            (draw.date to draw.session) to draw.winner
        }.mapNotNull { (key, winner) -> winner?.let { key to it } }.toMap()
        val commissions = agentStatement.draws.flatMap { it.commissions }
            .groupBy { it.customerId }.mapValues { (_, lines) ->
                lines.fold(0L) { total, line -> Math.addExact(total, line.amount) }
            }
        val rows = records.groupBy { it.entry.customerId }.map { (customerId, entries) ->
            val name = customerNames[customerId]?.takeIf { it.isNotBlank() }
                ?: error("Customer $customerId အမည် မတွေ့ပါ။")
            val customerTotal = CustomerSettlementCalculator.build(
                CustomerSettlementSource(name, range, entries, winners), null,
            )
            // Compare this customer's EACH draw with Agent Report. A grand-total check
            // alone could hide two customers' opposite errors canceling each other.
            val reportDraws = agentStatement.draws.map { it.forCustomer(customerId) }
                .filter { it.stakes.isNotEmpty() || it.commissions.isNotEmpty() }
            check(reportDraws.size == customerTotal.draws.size) {
                "Customer $name အကြိမ်အရေအတွက် Agent Report နှင့် မကိုက်ပါ။"
            }
            customerTotal.draws.forEach { customerDraw ->
                val reportDraw = reportDraws.singleOrNull {
                    it.date == customerDraw.date && it.session == customerDraw.session
                } ?: error("Customer $name အကြိမ်စာရင်း Agent Report တွင် မတွေ့ပါ။")
                check(customerDraw.winner == reportDraw.winner &&
                    customerDraw.bet == reportDraw.totalStake &&
                    customerDraw.winningStake == reportDraw.winningStake &&
                    customerDraw.payout == reportDraw.payout) {
                    "Customer $name ${customerDraw.date} ${customerDraw.session} " +
                        "ထိုးကြေး/လျော်ငွေ Agent Report နှင့် မကိုက်ပါ။"
                }
            }
            val commission = commissions[customerId] ?: 0L
            val recordedCommission = entries.fold(0L) { sum, entry ->
                Math.addExact(sum, entry.entry.commissionAmount)
            }
            check(recordedCommission == commission) {
                "Customer $name ကော်မရှင် Agent Report နှင့် မကိုက်ပါ။"
            }
            AgentSettlementCustomerRow(
                customerId, name, customerTotal.digits, customerTotal.totalBet,
                customerTotal.totalWinningStake, customerTotal.totalPayout, commission,
            )
        }.sortedWith(compareBy<AgentSettlementCustomerRow> { it.name.lowercase() }
            .thenBy { it.customerId })
        // Two distinct customers are permitted to share a display name.
        // Keep separate rows, but identify them unambiguously on the exported JPEG.
        val nameCounts = rows.groupingBy { it.name.trim().lowercase() }.eachCount()
        val distinctRows = rows.map { row ->
            if (nameCounts.getValue(row.name.trim().lowercase()) > 1)
                row.copy(name = "${row.name} (ID ${row.customerId})")
            else row
        }
        val doc = AgentSettlementDocument(agentName, range, distinctRows, selectedSession)
        val report = agentStatement.totals
        check(report.pendingDraws == 0) { "ပေါက်ဂဏန်း အတည်မပြုရသေးသော အကြိမ်ရှိပါသည်။" }
        check(doc.totalBet == report.totalStake &&
            doc.totalWinningStake == report.winningStake &&
            doc.totalPayout == report.payout &&
            doc.commission == report.savedNetCommission) {
            "Customer TOTAL နှင့် Agent Report စုစုပေါင်း မကိုက်ပါ။"
        }
        return doc
    }
}
