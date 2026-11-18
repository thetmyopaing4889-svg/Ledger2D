package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SetSnapshotCoreTest {
    private val drawDate = LocalDate.of(2026, 11, 18)
    private val yangon = ZoneId.of("Asia/Yangon")
    private val bangkok = ZoneId.of("Asia/Bangkok")

    private fun snapshot(time: String, index: String, value: String): SetMarketSnapshot {
        val localTime = java.time.LocalTime.parse(time)
        val sourceTime = LocalDateTime.of(drawDate, localTime)
            .atZone(yangon).withZoneSameInstant(bangkok)
        val digit = index.substringAfterLast('.').last().toString() +
            value.substringBefore('.').filter(Char::isDigit).last()
        return SetMarketSnapshot(index, value, sourceTime, "Intermission", digit)
    }

    private fun html(
        index: String = "1,481.46",
        value: String = "29,810.24",
        update: String = "18 Nov 2026 12:30:53",
    ) = """
        <html><head><script>SET Index Series Index 999.99 Value (M.Baht) 99.99</script></head>
        <body><div>SET Index Series</div><h2>SET</h2><div>Index $index</div>
        <p>Market Status : Intermission</p><p>Last Update $update</p>
        <div>Open 1,478.00</div><div>Value (M.Baht) $value</div>
        <div>SET50</div><p>Index 9,999.99 Value (M.Baht) 8,888.88</p>
        </body></html>
    """.trimIndent()

    @Test fun parses_labeled_index_and_value_not_set50_or_script() {
        val actual = SetSnapshotHtmlParser.parse(html())
        assertNotNull(actual)
        assertEquals("1,481.46", actual!!.index)
        assertEquals("29,810.24", actual.tradingValueMbaht)
        assertEquals("60", actual.computed2d)
        assertEquals("Intermission", actual.marketStatus)
        assertEquals("12:00:53", actual.sourceUpdateYangon.toLocalTime().toString())
    }

    @Test fun rejects_missing_labeled_set_index_panel() {
        assertNull(SetSnapshotHtmlParser.parse("<p>SET50 Index 1,481.46 Value (M.Baht) 29,810.24</p>"))
        assertNull(SetSnapshotHtmlParser.parse(html().replace("Value (M.Baht)", "Trading Value")))
    }

    @Test fun actual_nov18_sequence_selects_60_only_after_source_crosses_cutoff() {
        val selector = SetMorningSnapshotSelector(drawDate)
        assertEquals(SetMorningSelectionPhase.SET_LIVE, selector.observe(
            snapshot("12:00:23", "1,481.46", "29,802.34")
        ).phase)
        assertEquals("62", selector.current().snapshot?.computed2d)
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24"))
        assertEquals("60", selector.current().snapshot?.computed2d)
        assertEquals(SetMorningSelectionPhase.SET_LIVE, selector.current().phase)
        val result = selector.observe(snapshot("12:01:23", "1,481.46", "29,811.21"))
        assertEquals(SetMorningSelectionPhase.UI_RESULT_READY, result.phase)
        assertEquals("60", result.snapshot?.computed2d)
    }

    @Test fun exactly_120100_is_eligible_but_not_a_crossing_signal() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:01:00", "1,481.46", "29,810.24"))
        assertEquals(SetMorningSelectionPhase.SET_LIVE, selector.current().phase)
        assertEquals(SetMorningSelectionPhase.UI_RESULT_READY, selector.observe(
            snapshot("12:01:01", "1,481.46", "29,811.21")
        ).phase)
    }

    @Test fun cutoff_without_1200_snapshot_does_not_claim_result() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("11:59:53", "1,480.97", "29,783.77"))
        val actual = selector.observe(snapshot("12:01:23", "1,481.46", "29,811.21"))
        assertEquals(SetMorningSelectionPhase.WAITING, actual.phase)
        assertNull(actual.snapshot)
    }

    @Test fun timestamp_conflict_blocks_ui_result() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24"))
        assertEquals(SetMorningSelectionPhase.CONFLICT, selector.observe(
            snapshot("12:00:53", "1,481.46", "29,811.21")
        ).phase)
        assertEquals(SetMorningSelectionPhase.CONFLICT, selector.observe(
            snapshot("12:01:23", "1,481.46", "29,820.21")
        ).phase)
    }

    @Test fun out_of_order_and_duplicate_observations_do_not_regress_result() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24"))
        selector.observe(snapshot("12:00:23", "1,481.46", "29,802.34"))
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24"))
        val actual = selector.observe(snapshot("12:01:23", "1,481.46", "29,811.21"))
        assertEquals("60", actual.snapshot?.computed2d)
        assertEquals("60", selector.observe(
            snapshot("12:00:59", "1,481.46", "29,812.24")
        ).snapshot?.computed2d)
    }
}
