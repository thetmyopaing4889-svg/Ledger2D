package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.BetEntryEntity
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.database.BetLineEntity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BetEntriesPresentationTest {
    private val date = LocalDate.of(2026, 10, 30)
    private fun entry(id: Long, customer: Long, source: String, amount: Long): BetEntryWithLines =
        BetEntryWithLines(
            BetEntryEntity(id=id, customerId=customer, agentId=1, drawDate=date,
                drawSession=DrawSession.MORNING, sourceText=source,
                createdAt=id, updatedAt=100),
            listOf(BetLineEntity(id=id, betEntryId=id, digit="20", amount=amount)),
        )

    @Test fun submissionOrderIsAscendingAndNotChangedByEdits() {
        val first = entry(1, 10, "20 1000", 1000)
        val second = entry(2, 20, "21 3000", 3000)
        val third = entry(3, 10, "22 500", 500)
        val ordered = betEntriesInSubmissionOrder(listOf(third, first, second))
        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.entry.id })
        assertEquals(listOf(10L, 20L, 10L), ordered.map { it.entry.customerId })
        assertEquals(4500L, betEntriesTotal(ordered).amount)
        assertEquals(3, betEntriesTotal(ordered).digits)
        val grouped = ordered.groupBy { it.entry.customerId }
        assertEquals(listOf(10L, 20L), grouped.keys.toList())
        assertEquals(1500L, betEntriesTotal(grouped.getValue(10L)).amount)
        assertEquals(3000L, betEntriesTotal(grouped.getValue(20L)).amount)
    }

    @Test fun voucherSelectionRejectsAllCustomerAndCrossDrawTransactions() {
        val a = entry(1L, 10L, "1000", 1000L)
        val b = entry(2L, 20L, "2000", 2000L)
        val c = entry(3L, 10L, "3000", 3000L)
        val evening = entry(4L, 10L, "4000", 4000L).let {
            it.copy(entry = it.entry.copy(drawSession = DrawSession.EVENING))
        }
        val records = listOf(c, evening, b, a)
        assertEquals(
            listOf(1L, 3L),
            voucherSelectedEntryIds(records, 10L, date, DrawSession.MORNING, listOf(3L, 2L, 4L, 1L)),
        )
        assertEquals(emptyList<Long>(), voucherSelectedEntryIds(records, -1L, date, DrawSession.MORNING, listOf(1L)))
        assertEquals(emptyList<Long>(), voucherSelectedEntryIds(records, 10L, date.plusDays(1), DrawSession.MORNING, listOf(1L)))
    }

    @Test fun originalMultilineMessengerTextSurvivesWithoutAddedFormatOrReordering() {
        val raw = "4567အခွေပူး2000\n8ပတ်သီး3000\n20.30R500"
        val record = entry(5, 10, raw, 8000)
        assertEquals(raw, record.entry.sourceText)
        assertEquals(raw, betEntriesInSubmissionOrder(listOf(record))[0].entry.sourceText)
    }
}
