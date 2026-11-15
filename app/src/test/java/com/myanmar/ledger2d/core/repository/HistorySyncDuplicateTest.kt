package com.myanmar.ledger2d.core.repository

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Parser regression for the real public 2D_history duplicate-day issue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class HistorySyncDuplicateTest {
    private val start = LocalDate.of(2025, 1, 1)
    private val end = LocalDate.of(2025, 12, 31)

    private fun item(date: String, am: String, pm: String, note: String = "x") =
        """{"date":"$date","result_1200":"$am","result_430":"$pm","set_1200":"$note","val_1200":"-","set_430":"-","val_430":"-"}"""

    @Test fun validResultBeatsOffDuplicateRegardlessOfSourceOrder() {
        val actual = item("10-06-2025", "55", "60", "real")
        val off = item("10-06-2025", "off", "off", "off")
        for (body in listOf("[" + off + "," + actual + "]",
            "[" + actual + "," + off + "]")) {
            val rows = HistorySync.parse2DHistory(body, start, end)
            assertEquals(1, rows.size)
            assertEquals("55", rows.single().morning2d)
            assertEquals("60", rows.single().evening2d)
            assertEquals("real", rows.single().morningSet)
        }
    }

    @Test fun missingEveningRemainsMissingAndDoesNotBecomeClosed() {
        val real = item("28-03-2025", "30", "xx")
        val closed = item("28-03-2025", "off", "off")
        val rows = HistorySync.parse2DHistory("[" + closed + "," + real + "]",
            start, end)
        assertEquals(1, rows.size)
        assertEquals("30", rows.single().morning2d)
        assertEquals("xx", rows.single().evening2d)
    }

    @Test fun exactTiesKeepFirstSourceRecordAndDatesAreSorted() {
        val first = item("10-06-2025", "55", "60", "first")
        val second = item("10-06-2025", "55", "60", "second")
        val earlier = item("09-06-2025", "12", "34")
        val rows = HistorySync.parse2DHistory(
            "[" + earlier + "," + first + "," + second + "]", start, end)
        assertEquals(2, rows.size)
        assertEquals(LocalDate.of(2025, 6, 10), rows[0].date)
        assertEquals("first", rows[0].morningSet)
        assertEquals("12", rows[1].morning2d)
    }

    @Test fun outOfRequestedRangeDoesNotReappear() {
        val rows = HistorySync.parse2DHistory(
            "[" + item("10-06-2025", "55", "60") +
                "," + item("10-06-2026", "66", "99") + "]",
            start, end)
        assertEquals(1, rows.size)
        assertEquals(LocalDate.of(2025, 6, 10), rows.single().date)
    }
}
