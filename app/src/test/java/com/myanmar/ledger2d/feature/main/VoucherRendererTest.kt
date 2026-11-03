package com.myanmar.ledger2d.feature.main

import android.graphics.Color
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoucherRendererTest {
    private fun document(rows: List<VoucherRecord>) = VoucherDocument(
        customer = "Mg Mg",
        date = LocalDate.of(2026, 10, 2),
        session = DrawSession.MORNING,
        records = rows,
    )

    @Test fun generatedPagesAreFullPortraitAndOnlyLastHasBottomTotalBox() {
        val records = (1..28).map { index ->
            VoucherRecord(index.toLong(), "19R500", 2, 1000)
        }
        val renderer = VoucherRenderer(document(records))
        assertTrue(renderer.pageCount > 1)

        val first = renderer.render(0)
        val last = renderer.render(renderer.pageCount - 1)
        try {
            assertEquals(1080, first.width)
            assertEquals(1920, first.height)
            assertEquals(1080, last.width)
            assertEquals(1920, last.height)
            // Non-final pages leave the area white; final page paints the pale
            // pink total panel exactly at the bottom of the table.
            assertEquals(Color.WHITE, first.getPixel(200, 1764))
            assertEquals(Color.rgb(255, 246, 248), last.getPixel(200, 1764))
            assertEquals(56L, renderer.document.totalDigits)
            assertEquals(28000L, renderer.document.totalAmount)
        } finally {
            first.recycle()
            last.recycle()
        }
    }

    @Test fun multilineRowsHaveDifferentHeightsAndNoLinesAreDropped() {
        val source = listOf("8ပါ1000", "4567ခွေ500", "အပူး1000", "9ထိပ်1000").joinToString("\n")
        val renderer = VoucherRenderer(document(listOf(
            VoucherRecord(1, source, 0, 1),
            VoucherRecord(2, "19R500", 2, 1000),
            VoucherRecord(3, "ပါဝါ2000\n56.1000", 11, 22000),
        )))
        val parts = renderer.pages.flatMap { it.slices }
        assertEquals(listOf(0, 1, 2), parts.map { it.rowIndex })
        assertTrue(parts[0].height > parts[1].height)
        assertTrue(parts[2].height > parts[1].height)
    }

    @Test fun oversizedTransactionContinuesToFollowingPagesWithoutReordering() {
        val longText = List(95) { "8ပါ1000" }.joinToString("\n")
        val renderer = VoucherRenderer(document(listOf(
            VoucherRecord(1, longText, 950, 950000),
            VoucherRecord(2, "19R500", 2, 1000),
        )))
        val parts = renderer.pages.flatMap { it.slices }
        val longRowParts = parts.filter { it.rowIndex == 0 }
        assertTrue(longRowParts.size > 1)
        assertEquals(0, longRowParts.first().firstLine)
        assertEquals(95, longRowParts.last().untilLine)
        longRowParts.zipWithNext { a, b -> assertEquals(a.untilLine, b.firstLine) }
        assertEquals(1, parts.last().rowIndex)
    }
}
