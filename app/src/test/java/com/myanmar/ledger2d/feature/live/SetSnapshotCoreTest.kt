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

    private fun snapshot(
        time: String, index: String, value: String, status: String = "Intermission",
    ): SetMarketSnapshot {
        val localTime = java.time.LocalTime.parse(time)
        val sourceTime = LocalDateTime.of(drawDate, localTime)
            .atZone(yangon).withZoneSameInstant(bangkok)
        val digit = index.substringAfterLast('.').last().toString() +
            value.substringBefore('.').filter(Char::isDigit).last()
        return SetMarketSnapshot(index, value, sourceTime, status, digit)
    }

    private fun html(
        index: String = "1,481.46",
        value: String = "29,810.24",
        update: String = "18 Nov 2026 12:30:53",
        marketStatus: String = "Intermission",
    ) = """
        <html><head><script>SET Index Series Index 999.99 Value (M.Baht) 99.99</script></head>
        <body><div>SET Index Series</div><h2>SET</h2><div>Index $index</div>
        <p>Market Status : $marketStatus</p><p>Last Update $update</p>
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

    @Test fun html_entities_and_nested_tags_match_colab_visible_text() {
        val fixture = html()
            .replace("SET Index Series", "SET&nbsp;Index&nbsp;Series")
            .replace("Index 1,481.46", "Index&nbsp;<strong>1,481.46</strong>")
            .replace("Value (M.Baht) 29,810.24", "Value&nbsp;(M.Baht)&nbsp;<b>29,810.24</b>")
        val parsed = SetSnapshotHtmlParser.parseDetailed(fixture)
        assertNull(parsed.failureStage)
        assertEquals("60", parsed.snapshot?.computed2d)
        assertEquals("1,481.46", parsed.snapshot?.index)
    }

    @Test fun parse_details_identify_real_missing_fields() {
        assertEquals("SET_PANEL_MISSING", SetSnapshotHtmlParser.parseDetailed(
            "<html><body>SET50 Index 1,481.46</body></html>"
        ).failureStage)
        assertEquals("TRADING_VALUE_MISSING", SetSnapshotHtmlParser.parseDetailed(
            html().replace("Value (M.Baht)", "Trading Value")
        ).failureStage)
        assertEquals("SOURCE_TIME_MISSING", SetSnapshotHtmlParser.parseDetailed(
            html().replace("Last Update", "Unknown Time")
        ).failureStage)
    }

    @Test fun conflict_recovers_on_newer_source_timestamp_before_cutoff() {
        val selector = SetMorningSnapshotSelector(drawDate)
        assertEquals(SetMorningSelectionPhase.SET_LIVE, selector.observe(
            snapshot("12:00:23", "1,481.46", "29,802.34")
        ).phase)
        assertEquals(SetMorningSelectionPhase.CONFLICT, selector.observe(
            snapshot("12:00:23", "1,481.46", "29,803.34")
        ).phase)
        // New source timestamp resolves only this conflict, no stale old snapshot.
        assertEquals(SetMorningSelectionPhase.SET_LIVE, selector.observe(
            snapshot("12:00:53", "1,481.46", "29,810.24")
        ).phase)
        val selected = selector.observe(snapshot("12:01:23", "1,481.46", "29,811.21"))
        assertEquals(SetMorningSelectionPhase.UI_RESULT_READY, selected.phase)
        assertEquals("60", selected.snapshot?.computed2d)
    }

    @Test fun cutoff_crossing_first_never_promotes_later_stale_html() {
        val selector = SetMorningSnapshotSelector(drawDate)
        assertEquals(SetMorningSelectionPhase.WAITING, selector.observe(
            snapshot("12:01:44", "1,461.81", "35,473.96")
        ).phase)
        assertEquals(SetMorningSelectionPhase.WAITING, selector.observe(
            snapshot("12:00:53", "1,461.81", "35,471.77")
        ).phase)
        assertNull(selector.current().snapshot)
    }


    @Test fun parser_extracts_exact_intermission_and_normalizes_adjacent_page_digits() {
        assertEquals("Intermission", SetSnapshotHtmlParser.parse(
            html(marketStatus = "Intermission2")
        )?.marketStatus)
        assertEquals("Open", SetSnapshotHtmlParser.parse(
            html(marketStatus = "Open2")
        )?.marketStatus)
        assertEquals("Closed", SetSnapshotHtmlParser.parse(
            html(marketStatus = "Closed")
        )?.marketStatus)
        assertEquals(null, SetSnapshotHtmlParser.parse(
            html(marketStatus = "Unknown")
        )?.marketStatus)
        assertEquals(false, isSetIntermission("Open2"))
        assertEquals(true, isSetIntermission(" Intermission "))
    }

    @Test fun selects_nearest_intermission_not_nearest_open_before_cutoff() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:23", "1,481.46", "29,802.34", "Intermission"))
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24", "Intermission"))
        // A newer Open page must not displace the last eligible Intermission.
        selector.observe(snapshot("12:00:59", "1,481.49", "29,811.21", "Open"))
        val selected = selector.observe(snapshot("12:01:23", "1,481.50", "29,820.21", "Open"))
        assertEquals(SetMorningSelectionPhase.UI_RESULT_READY, selected.phase)
        assertEquals("12:00:53", selected.snapshot?.sourceUpdateYangon?.toLocalTime().toString())
        assertEquals("60", selected.snapshot?.computed2d)
        assertEquals("Intermission", selected.snapshot?.marketStatus)
    }

    @Test fun exact_120100_only_qualifies_when_intermission() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24"))
        selector.observe(snapshot("12:01:00", "1,481.46", "29,811.21", "Open"))
        val selected = selector.observe(snapshot("12:01:01", "1,481.47", "29,811.21"))
        assertEquals("12:00:53", selected.snapshot?.sourceUpdateYangon?.toLocalTime().toString())

        val exact = SetMorningSnapshotSelector(drawDate)
        exact.observe(snapshot("12:01:00", "1,481.46", "29,810.24", "Intermission"))
        assertEquals("12:01:00", exact.observe(
            snapshot("12:01:01", "1,481.47", "29,811.21", "Intermission")
        ).snapshot?.sourceUpdateYangon?.toLocalTime().toString())
    }

    @Test fun never_selects_open_only_even_when_closest_to_cutoff() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:59", "1,481.46", "29,810.24", "Open"))
        val result = selector.observe(snapshot("12:01:10", "1,481.47", "29,811.21", "Intermission"))
        assertEquals(SetMorningSelectionPhase.WAITING, result.phase)
        assertNull(result.snapshot)
    }

    @Test fun older_source_after_newer_does_not_replace_nearest_intermission() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24"))
        selector.observe(snapshot("12:00:23", "1,481.46", "29,802.34"))
        selector.observe(snapshot("12:00:59", "1,481.47", "29,813.34", "Open"))
        val result = selector.observe(snapshot("12:01:23", "1,481.47", "29,822.24"))
        assertEquals("12:00:53", result.snapshot?.sourceUpdateYangon?.toLocalTime().toString())
        assertEquals("60", result.snapshot?.computed2d)
    }

    @Test fun conflicting_nearest_stamp_quarantines_both_and_falls_back_to_earlier_eligible() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:23", "1,481.46", "29,802.34"))
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24"))
        assertEquals(SetMorningSelectionPhase.CONFLICT, selector.observe(
            snapshot("12:00:53", "1,481.48", "29,810.24")
        ).phase)
        val result = selector.observe(snapshot("12:01:23", "1,481.49", "29,820.21"))
        assertEquals(SetMorningSelectionPhase.UI_RESULT_READY, result.phase)
        assertEquals("12:00:23", result.snapshot?.sourceUpdateYangon?.toLocalTime().toString())
        assertEquals("62", result.snapshot?.computed2d)
    }

    @Test fun conflicting_market_status_at_same_timestamp_cannot_fabricate_result() {
        val selector = SetMorningSnapshotSelector(drawDate)
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24", "Open"))
        selector.observe(snapshot("12:00:53", "1,481.46", "29,810.24", "Intermission"))
        val result = selector.observe(snapshot("12:01:23", "1,481.46", "29,820.21"))
        assertEquals(SetMorningSelectionPhase.CONFLICT, result.phase)
        assertNull(result.snapshot)
    }

    @Test fun no_cutoff_crossing_does_not_publish_ui_result() {
        val selector = SetMorningSnapshotSelector(drawDate)
        val picked = selector.observe(snapshot("12:00:59", "1,481.46", "29,810.24"))
        assertEquals(SetMorningSelectionPhase.SET_LIVE, picked.phase)
        assertEquals("60", picked.snapshot?.computed2d)
    }


}
