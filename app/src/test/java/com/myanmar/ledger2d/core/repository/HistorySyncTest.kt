package com.myanmar.ledger2d.core.repository

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySyncTest {
    @Test
    fun parse2DHistory_mapsSourceFields_andFiltersWindow() {
        val body = """
            [
              {
                "date": "30-09-2026",
                "result_1200": "30",
                "set_1200": "1,562.83",
                "val_1200": "68,480.86",
                "result_430": "13",
                "set_430": "1,559.01",
                "val_430": "121,003.14",
                "modern_930": "38",
                "internet_930": "79",
                "tw_1206": "36",
                "modern_200": "79",
                "internet_200": "75"
              },
              {
                "date": "01-01-2026",
                "result_1200": "01",
                "set_1200": "-",
                "val_1200": "-",
                "result_430": "02",
                "set_430": "-",
                "val_430": "-",
                "modern_930": "-",
                "internet_930": "-",
                "tw_1206": "99",
                "modern_200": "-",
                "internet_200": "-"
              }
            ]
        """.trimIndent()

        val rows = HistorySync.parse2DHistory(
            body,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 9, 30)
        )

        assertEquals(2, rows.size)
        assertEquals(LocalDate.of(2026, 9, 30), rows[0].date)
        assertEquals("30", rows[0].morning2d)
        assertEquals("1,562.83", rows[0].morningSet)
        assertEquals("68,480.86", rows[0].morningValue)
        assertEquals("13", rows[0].evening2d)
        assertEquals("38", rows[0].modern930)
        assertEquals("79", rows[0].internet930)
        assertEquals("79", rows[0].modern200)
        assertEquals("75", rows[0].internet200)
        assertTrue(rows[0].date != LocalDate.of(2026, 10, 1))
    }

    @Test
    fun parse2DHistory_preservesOffAndIgnoresTwField() {
        val body = """
            [{
              "date": "02-01-2023",
              "result_1200": "Off",
              "set_1200": "-",
              "val_1200": "-",
              "result_430": "-",
              "set_430": "-",
              "val_430": "-",
              "modern_930": "-",
              "internet_930": "-",
              "tw_1206": "23",
              "modern_200": "-",
              "internet_200": "-"
            }]
        """.trimIndent()

        val rows = HistorySync.parse2DHistory(
            body,
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2023, 1, 3)
        )

        assertEquals(1, rows.size)
        assertEquals("Off", rows.single().morning2d)
        assertEquals("-", rows.single().evening2d)
    }
}
