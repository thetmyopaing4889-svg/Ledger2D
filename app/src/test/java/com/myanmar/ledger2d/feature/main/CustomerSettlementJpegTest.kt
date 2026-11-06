package com.myanmar.ledger2d.feature.main

import android.graphics.Typeface
import com.myanmar.ledger2d.core.database.BetEntryEntity
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.database.BetLineEntity
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class CustomerSettlementJpegTest {
    private val morning = LocalDate.of(2026, 10, 5)
    private val evening = DrawSession.EVENING
    private fun record(
        id: Long, date: LocalDate, session: DrawSession, text: String, rate: Long,
        stakes: List<Pair<String, Long>>,
    ): BetEntryWithLines = BetEntryWithLines(
        entry = BetEntryEntity(
            id = id, customerId = 101L, agentId = 201L,
            drawDate = date, drawSession = session, sourceText = text,
            payoutRate = rate, createdAt = id, updatedAt = id,
        ),
        lines = stakes.mapIndexed { index, (digit, amount) ->
            BetLineEntity(id = id * 100 + index, betEntryId = id, digit = digit, amount = amount)
        },
    )

    private fun sample(): CustomerSettlementSource {
        val six = listOf("19", "91", "23", "32", "45", "54").map { it to 2000L }
        val twelve = listOf(
            "34", "35", "36", "37", "43", "45", "46", "47", "53", "54", "56", "57",
        ).map { it to 1000L }
        return CustomerSettlementSource(
            "Mg Mg", StatementRange(morning, morning),
            records = listOf(
                record(2, morning, DrawSession.MORNING, "3456ခွေ1000", 80, twelve),
                record(1, morning, DrawSession.MORNING, "19.23.45R2000", 80, six),
                record(3, morning, evening, "19R500", 80, listOf("19" to 500L, "91" to 500L)),
                record(4, morning, evening, "20.39=1000", 80, listOf("20" to 1000L, "39" to 1000L)),
            ),
            winners = mapOf(
                (morning to DrawSession.MORNING) to "36",
                (morning to evening) to "19",
            ),
        )
    }

    @Test fun eachDrawHasOnlyItsOwnSubtotalAndThereIsOneGrandTotal() {
        val statement = CustomerSettlementCalculator.build(sample(), null)
        assertEquals(2, statement.draws.size)
        assertEquals(listOf(1L, 2L), statement.draws[0].rows.map { it.id })
        assertEquals(18L, statement.draws[0].digits)
        assertEquals(24000L, statement.draws[0].bet)
        assertEquals(1000L, statement.draws[0].winningStake)
        assertEquals(80000L, statement.draws[0].payout)
        assertEquals(4L, statement.draws[1].digits)
        assertEquals(3000L, statement.draws[1].bet)
        assertEquals(500L, statement.draws[1].winningStake)
        assertEquals(40000L, statement.draws[1].payout)
        assertEquals(27000L, statement.totalBet)
        assertEquals(1500L, statement.totalWinningStake)
        assertEquals(120000L, statement.totalPayout)
        assertEquals(-93000L, statement.settlement)
    }

    @Test fun optionalRebateOnlySubtractsOnceFromGrossStake() {
        val source = sample()
        val noRebate = CustomerSettlementCalculator.build(source, null)
        val yesRebate = CustomerSettlementCalculator.build(source, 500)
        assertEquals(0L, noRebate.rebateAmount)
        assertEquals(1350L, yesRebate.rebateAmount)
        assertEquals(121350L, yesRebate.totalDeduction)
        assertEquals(-94350L, yesRebate.settlement)
        assertEquals(27000L, noRebate.totalBet)
        assertEquals(27000L, yesRebate.totalBet)
        assertEquals(120000L, yesRebate.totalPayout)
    }

    @Test fun rebatePercentIsValidatedAndRoundedToWholeKyat() {
        assertEquals(525, customerRebateBasisPoints("5.25"))
        assertEquals(0, customerRebateBasisPoints("0"))
        assertNull(customerRebateBasisPoints("100.001"))
        assertNull(customerRebateBasisPoints("-1"))
        assertNull(customerRebateBasisPoints("101"))
        assertNull(customerRebateBasisPoints("x"))
        val row = record(1, morning, DrawSession.MORNING,
            "36=1001", 80, listOf("36" to 1001L))
        val source = CustomerSettlementSource("Customer", StatementRange(morning, morning),
            listOf(row), mapOf((morning to DrawSession.MORNING) to "36"))
        assertEquals(13L, CustomerSettlementCalculator.build(source, 125).rebateAmount)
    }

    @Test fun pendingResultCannotCreateFinalStatement() {
        val source = sample().copy(winners = emptyMap())
        val error = runCatching { CustomerSettlementCalculator.build(source, null) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("ပေါက်ဂဏန်း"))
    }

    @Test fun payoutRateIsTheHistoricalSnapshotForEachEntry() {
        val second = record(5, morning, DrawSession.MORNING,
            "36=500", 85, listOf("36" to 500L))
        val source = sample().copy(records = sample().records + second)
        val result = CustomerSettlementCalculator.build(source, null)
        assertEquals(120000L + 42500L, result.totalPayout)
        assertEquals(1500L + 500L, result.totalWinningStake)
    }

    @Test fun fullPortraitPagesHaveLastPageReservedForSummary() {
        val many = (1L..50L).map {
            record(it, morning, DrawSession.MORNING,
                "20.39R1000", 80, listOf("20" to 1000L, "39" to 1000L))
        }
        val source = CustomerSettlementSource(
            "Mg Mg", StatementRange(morning, morning),
            many, mapOf((morning to DrawSession.MORNING) to "36"),
        )
        val renderer = CustomerSettlementJpegRenderer(CustomerSettlementCalculator.build(source, null))
        assertTrue(renderer.pageCount > 2)
        assertFalse(renderer.isSummary(0))
        assertTrue(renderer.isSummary(renderer.pageCount - 1))
        assertEquals(50, (0 until renderer.pageCount - 1).flatMap { renderer.pageEntryIds(it) }.size)
        val first = renderer.render(0)
        val summary = renderer.render(renderer.pageCount - 1)
        try {
            assertEquals(1080, first.width)
            assertEquals(1920, first.height)
            assertEquals(1080, summary.width)
            assertEquals(1920, summary.height)
        } finally { first.recycle(); summary.recycle() }
    }
    @Test fun injectedMyanmarFontPreservesTransactionOrderAndFinancialTotals() {
        val accented = record(8, morning, DrawSession.MORNING,
            "4567ခွေ5000\nအပူး3000\n9ထိပ်1000", 80,
            listOf("36" to 3000L, "37" to 5000L))
        val document = CustomerSettlementCalculator.build(sample().copy(
            records = sample().records + accented), null)
        val renderer = CustomerSettlementJpegRenderer(
            document, Typeface.create("sans-serif", Typeface.NORMAL))
        val allIds = (0 until renderer.pageCount - 1)
            .flatMap { renderer.pageEntryIds(it) }
        assertEquals(5, allIds.size)
        assertTrue(8L in allIds)
        assertEquals(document.totalBet, 35000L)
        assertEquals(document.totalPayout, 360000L)
        val bitmap = renderer.render(0)
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(1920, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

}
