package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C02 source contracts: presentation only; renderer and money tests remain separate. */
class AgentSettlementPreviewHomeChromeContractTest {
    private val source = File("src/main/java/com/myanmar/ledger2d/feature/main/AgentSettlementJpegScreen.kt").readText()

    @Test fun selectorRetainsAllPeriodsAndOriginalGenerationInputs() {
        val s = source.substringBefore("private fun AgentSettlementJpegPreview(")
        assertFalse(s.contains("CherryFinancialPanel {"))
        assertTrue(s.contains("PremiumScopeDropdown("))
        assertTrue(s.contains("PremiumSelectionChip("))
        assertTrue(s.contains(".cherryFinancialBackground()"))
        for (preset in listOf("DRAW", "DAY", "WEEK", "MONTH", "CUSTOM")) {
            assertTrue(s.contains("AgentJpegPreset." + preset))
        }
        assertTrue(s.contains("StatementPeriods.range(StatementPeriod.WEEK, anchorDate, from, to)"))
        assertTrue(s.contains("StatementRange(from, to)"))
        assertTrue(s.contains("ProfileKind.AGENT, photoId"))
        assertTrue(s.contains("enabled = !busy && selectedAgent != null && range != null"))
        assertTrue(s.contains("vm.agentSettlementJpeg(chosen.id, chosen.name, dates, selectedSession)"))
        assertTrue(s.contains("Modifier.fillMaxWidth().heightIn(min = 54.dp)"))
    }

    @Test fun previewPreservesNeutralBitmapAndPagination() {
        val s = source.substringAfter("private fun AgentSettlementJpegPreview(")
        assertFalse(s.contains("CherryFinancialPanel {"))
        assertTrue(s.contains("horizontalArrangement = Arrangement.SpaceBetween"))
        assertTrue(s.contains(".cherryFinancialBackground()"))
        assertTrue(s.contains("AgentSettlementJpegRenderer(document, headerPhoto)"))
        assertTrue(s.contains("rememberPagerState(pageCount = { renderer?.pageCount ?: 1 })"))
        assertTrue(s.contains("ready.render(index)"))
        assertTrue(s.contains("image.recycle()"))
        assertTrue(s.contains("contentScale = ContentScale.Fit"))
        assertFalse(s.contains("ContentScale.Crop"))
        assertTrue(s.contains("pager.currentPage < renderer!!.pageCount - 1"))
    }

    @Test fun exportStillRevalidatesMoneyAndBlocksDuplicateActions() {
        val s = source.substringAfter("private fun AgentSettlementJpegPreview(")
        assertTrue(s.contains("if (!exporting && ready != null)"))
        assertTrue(s.contains("agentId, currentAgentName, document.range, selectedSession"))
        assertTrue(s.contains("check(latest == document)"))
        assertTrue(s.contains("AgentSettlementJpegExporter.save(context.applicationContext, ready)"))
        assertTrue(s.contains("BackHandler(enabled = exporting)"))
        assertTrue(s.contains("enabled = !exporting && renderer != null"))
        assertTrue(s.contains("permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)"))
        assertTrue(s.contains("message = result.fold("))
    }
}
