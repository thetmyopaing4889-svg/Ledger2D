package com.myanmar.ledger2d.feature.main

import org.junit.Assert.*
import org.junit.Test

class VoucherPaginationTest {
    @Test fun rowsKeepSubmissionOrderAndVariableHeights() {
        val rows = listOf(
            VoucherMeasuredRow(listOf(42, 42, 42, 42)),
            VoucherMeasuredRow(listOf(42)),
            VoucherMeasuredRow(listOf(42, 42)),
            VoucherMeasuredRow(listOf(42)),
        )
        val pages = VoucherPaginator.paginate(rows, 330)
        val parts = pages.flatMap { it.slices }
        assertEquals(listOf(0, 1, 2, 3), parts.map { it.rowIndex })
        assertEquals(218, parts[0].height)
        assertEquals(112, parts[1].height)
        assertEquals(134, parts[2].height)
        assertTrue(pages.all { page -> page.slices.sumOf { it.height } <= 330 })
    }

    @Test fun longTransactionSplitsOnlyBetweenLinesAndNeverDropsContent() {
        val rows = listOf(
            VoucherMeasuredRow(List(20) { 48 }),
            VoucherMeasuredRow(listOf(45)),
        )
        val pages = VoucherPaginator.paginate(rows, 310)
        val parts = pages.flatMap { it.slices }
        val firstRow = parts.filter { it.rowIndex == 0 }
        assertTrue(firstRow.size > 1)
        assertEquals(0, firstRow.first().firstLine)
        assertEquals(20, firstRow.last().untilLine)
        firstRow.zipWithNext { a, b -> assertEquals(a.untilLine, b.firstLine) }
        assertEquals(1, parts.last().rowIndex)
        assertTrue(pages.all { page -> page.slices.isNotEmpty() && page.slices.sumOf { it.height } <= 310 })
    }

    @Test fun regularRowMovesWholeToNextPageInsteadOfSplitting() {
        val rows = List(3) { VoucherMeasuredRow(listOf(45, 45)) }
        val pages = VoucherPaginator.paginate(rows, 260)
        assertEquals(3, pages.size)
        pages.forEachIndexed { index, page ->
            assertEquals(listOf(index), page.slices.map { it.rowIndex })
            assertEquals(0, page.slices.single().firstLine)
            assertEquals(2, page.slices.single().untilLine)
        }
    }

    @Test fun emptyDocumentHasOneBlankPageAndInvalidDimensionsAreRejected() {
        assertEquals(1, VoucherPaginator.paginate(emptyList(), 400).size)
        assertTrue(VoucherPaginator.paginate(emptyList(), 400).single().slices.isEmpty())
        try {
            VoucherPaginator.paginate(listOf(VoucherMeasuredRow(listOf(50))), 60)
            fail("Expected invalid capacity")
        } catch (_: IllegalArgumentException) { }
    }
}
