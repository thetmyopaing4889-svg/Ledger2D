package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.BetEntryEntity
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.database.BetLineEntity
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class AgentSettlementJpegTest {
    private val start = LocalDate.of(2026, 10, 26)
    private val end = start.plusDays(4)
    private val morning = DrawSession.MORNING
    private val evening = DrawSession.EVENING

    private fun entry(id: Long, customer: Long, date: LocalDate, session: DrawSession,
        text: String, rate: Long, commission: Long,
        bets: List<Pair<String, Long>>, agent: Long = 201L): BetEntryWithLines =
        BetEntryWithLines(
            entry = BetEntryEntity(
                id = id, agentId = agent, customerId = customer,
                drawDate = date, drawSession = session, sourceText = text,
                payoutRate = rate, commissionAmount = commission,
                createdAt = id, updatedAt = id,
            ),
            lines = bets.mapIndexed { index, (digit, amount) ->
                BetLineEntity(id = id * 100 + index, betEntryId = id, digit = digit, amount = amount)
            },
        )

    private val records by lazy {
        listOf(
            entry(1, 101, start, morning, "20.31=1000", 80, 200,
                listOf("20" to 1000L, "31" to 1000L)),
            entry(2, 102, start, morning, "31=2000", 80, 150,
                listOf("31" to 2000L)),
            entry(3, 101, start.plusDays(1), evening, "31=500", 90, 50,
                listOf("31" to 500L)),
            entry(4, 103, start.plusDays(1), evening, "20=1000", 80, 60,
                listOf("20" to 1000L)),
        )
    }
    private val winners get() = mapOf(
        (start to morning) to "20",
        (start.plusDays(1) to evening) to "31",
    )
    private fun agentReport(
        bets: List<BetEntryWithLines> = records,
        winning: Map<Pair<LocalDate, DrawSession>, String> = winners,
        selectedSession: DrawSession? = null,
    ): AgentStatement {
        val rated = bets.flatMap { row ->
            row.lines.map {
                AgentRatedStake(row.entry.agentId, row.entry.customerId,
                    row.entry.drawDate, row.entry.drawSession,
                    it.digit, it.amount, row.entry.payoutRate)
            }
        }
        val commissions = bets.map {
            AgentSavedCommission(it.entry.agentId, it.entry.customerId, it.entry.drawDate,
                it.entry.drawSession, it.entry.commissionAmount)
        }
        return AgentStatementCalculator.build(rated, commissions, winning, selectedSession)
    }
    private fun document(
        bets: List<BetEntryWithLines> = records,
        report: AgentStatement = agentReport(bets),
        names: Map<Long, String> = mapOf(101L to "Customer A", 102L to "Customer B", 103L to "Customer C"),
    ) = AgentSettlementJpegCalculator.build(
        201L, "Agent A", StatementRange(start, end), bets, names, report,
    )

    @Test fun fiveDayPeriodHasOneRowPerCustomerFromCustomerJpegTotals() {
        val doc = document()
        assertEquals(3, doc.rows.size)
        assertEquals(listOf("Customer A", "Customer B", "Customer C"), doc.rows.map { it.name })
        assertEquals(3L, doc.rows[0].digits)
        assertEquals(2500L, doc.rows[0].bet)
        assertEquals(1500L, doc.rows[0].winningStake)
        assertEquals(125000L, doc.rows[0].payout)
        assertEquals(250L, doc.rows[0].commission)
        assertEquals(5L, doc.digits)
        assertEquals(5500L, doc.totalBet)
        assertEquals(1500L, doc.totalWinningStake)
        assertEquals(125000L, doc.totalPayout)
        assertEquals(460L, doc.commission)
        assertEquals(125460L, doc.totalDeduction)
        assertEquals(-119960L, doc.settlement)
    }

    @Test fun matchesCustomerJpegTotalAndAgentReportForEachCustomer() {
        val doc = document()
        val byCustomer = records.groupBy { it.entry.customerId }
        doc.rows.forEach { row ->
            val customer = CustomerSettlementCalculator.build(
                CustomerSettlementSource(row.name, StatementRange(start, end),
                    requireNotNull(byCustomer[row.customerId]), winners), null)
            assertEquals(customer.digits, row.digits)
            assertEquals(customer.totalBet, row.bet)
            assertEquals(customer.totalWinningStake, row.winningStake)
            assertEquals(customer.totalPayout, row.payout)
        }
        val report = agentReport().totals
        assertEquals(report.totalStake, doc.totalBet)
        assertEquals(report.winningStake, doc.totalWinningStake)
        assertEquals(report.payout, doc.totalPayout)
        assertEquals(report.savedNetCommission, doc.commission)
    }

    @Test fun unfinishedWinnerCannotBeExported() {
        val missing = agentReport(winning = mapOf((start to morning) to "20"))
        assertTrue(runCatching { document(report = missing) }.isFailure)
    }

    @Test fun commissionMismatchOrWrongAgentCannotBeExported() {
        val noCommission = AgentStatementCalculator.build(
            records.flatMap { row -> row.lines.map {
                AgentRatedStake(row.entry.agentId, row.entry.customerId, row.entry.drawDate,
                    row.entry.drawSession, it.digit, it.amount, row.entry.payoutRate)
            } }, emptyList(), winners,
        )
        assertTrue(runCatching { document(report = noCommission) }.isFailure)
        assertTrue(runCatching {
            document(bets = records + entry(5, 104, start, morning, "20=10", 80, 0,
                listOf("20" to 10L), agent = 202L))
        }.isFailure)
    }

    @Test fun jpegHasSharedPortraitFrameAndDedicatedFinalSummary() {
        val renderer = AgentSettlementJpegRenderer(document())
        assertEquals(2, renderer.pageCount)
        assertFalse(renderer.isSummary(0))
        assertTrue(renderer.isSummary(1))
        assertEquals(listOf(101L, 102L, 103L), renderer.pageCustomerIds(0))
        val first = renderer.render(0)
        val summary = renderer.render(1)
        try {
            assertEquals(CustomerSettlementJpegRenderer.width, first.width)
            assertEquals(CustomerSettlementJpegRenderer.height, first.height)
            assertEquals(first.width, summary.width)
            assertEquals(first.height, summary.height)
        } finally { first.recycle(); summary.recycle() }
    }

    @Test fun canceledPerCustomerErrorsCannotHideBehindCorrectGrandTotal() {
        // Move 500 kyats of losing stakes from Customer B to A. The agent's
        // grand stake, payout and commission stay unchanged, but A/B totals don't.
        val shifted = records.map { record ->
            when (record.entry.id) {
                1L -> record.copy(lines = record.lines.map { line ->
                    if (line.digit == "31") line.copy(amount = 1500L) else line
                })
                2L -> record.copy(lines = record.lines.map { line ->
                    if (line.digit == "31") line.copy(amount = 1500L) else line
                })
                else -> record
            }
        }
        assertEquals(records.sumOf { it.lines.sumOf { line -> line.amount } },
            shifted.sumOf { it.lines.sumOf { line -> line.amount } })
        assertTrue(runCatching { document(bets = shifted, report = agentReport()) }.isFailure)
    }

    @Test fun oneSelectedDrawAcrossFiveDaysDoesNotLeakOtherSessionEntries() {
        val filtered = records.filter { it.entry.drawSession == evening }
        val report = agentReport(records, winners, selectedSession = evening)
        val doc = AgentSettlementJpegCalculator.build(
            201L, "Agent A", StatementRange(start, end),
            filtered, mapOf(101L to "Customer A", 103L to "Customer C"),
            report, evening,
        )
        assertEquals(2, doc.rows.size)
        assertEquals(1500L, doc.totalBet)
        assertEquals(500L, doc.totalWinningStake)
        assertEquals(45000L, doc.totalPayout)
        assertEquals(110L, doc.commission)
    }

    @Test fun extremelyLongCustomerNamePaginatesRatherThanCrashingOrRepeatingTotals() {
        val names = mapOf(101L to (1..100).joinToString("\n") { "Customer name continuation $it" },
            102L to "Customer B", 103L to "Customer C")
        val doc = document(names = names)
        val renderer = AgentSettlementJpegRenderer(doc)
        assertTrue(renderer.pageCount > 2)
        assertEquals(3, (0 until renderer.pageCount - 1)
            .flatMap { renderer.pageCustomerIds(it) }.size)
        assertTrue(renderer.isSummary(renderer.pageCount - 1))
        val image = renderer.render(0)
        try {
            assertEquals(1080, image.width)
            assertEquals(1920, image.height)
        } finally { image.recycle() }
    }

    @Test fun manyCustomersPaginateWithoutDuplication() {
        val bets = (1L..55L).map { i ->
            entry(i, 1000L + i, start, morning, "31=1000", 80, 0,
                listOf("31" to 1000L))
        }
        val names = bets.associate { it.entry.customerId to "Customer ${it.entry.customerId}" }
        val doc = document(bets, agentReport(bets, mapOf((start to morning) to "20")), names)
        val renderer = AgentSettlementJpegRenderer(doc)
        assertTrue(renderer.pageCount > 2)
        assertEquals(55, (0 until renderer.pageCount - 1)
            .flatMap { renderer.pageCustomerIds(it) }.distinct().size)
        assertTrue(renderer.isSummary(renderer.pageCount - 1))
    }
}
