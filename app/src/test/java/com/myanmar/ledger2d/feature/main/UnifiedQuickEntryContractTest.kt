package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.QuickFormat
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UnifiedQuickEntryContractTest {
    private val root = "src/main/java/com/myanmar/ledger2d/feature/main/"
    private val bet = File(root + "Screens.kt").readText().substringAfter("fun BettingScreen(")
        .substringBefore("@Composable private fun PreviewCard(")

    @Test fun allOriginalFormatsAppearExactlyOnceInApprovedRows() {
        assertEquals(listOf(1, 3, 3, 3, 4), betEntryFormatRows.map { it.size })
        assertEquals(QuickFormat.entries.toList(), betEntryFormatRows.flatten())
        assertEquals(QuickFormat.MANUAL, betEntryFormatRows.first().single())
    }

    @Test fun compactFirstViewRetainsDateSessionAndAllFormatTouchTargets() {
        assertTrue(bet.contains("BetEntryDateSessionRow("))
        assertTrue(bet.contains("onDate = { dateText = it; language.setDate(it) }"))
        assertTrue(bet.contains("onSession = { session = it; language.setSession(it) }"))
        assertTrue(bet.contains("heightIn(min = 130.dp)"))
        assertTrue(bet.contains("Checkbox(checked = backdated"))
        assertTrue(bet.contains("if(backdated&&entryId==0L)item"))
        assertFalse(bet.contains("ထိုးမည့်အချိန်"))
        val ui = File(root + "BetEntryControls.kt").readText()
        assertTrue(ui.contains("maxWidth >= 340.dp"))
        assertTrue(ui.contains("LocalDensity.current.fontScale <= 1.25f"))
        assertTrue(ui.contains("while (!DrawSchedule.isWeekday(picked)) picked = picked.plusDays(1)"))
        assertTrue(ui.contains("Modifier.weight(1f).heightIn(min = 48.dp)"))
        assertTrue(ui.contains("DrawSession.entries.forEachIndexed"))
    }

    @Test fun scopeAndPreviewMustMatchBeforeConfirmAndSuccessResetsOnlyNewEntry() {
        assertTrue(bet.contains("customerId = 0L\n                                    agentId = selected"))
        assertTrue(bet.contains("customers.any { it.id == customerId && it.agentId == agentId }"))
        assertTrue(bet.contains("requestedKey == previewKey) requestedPreview"))
        assertTrue(bet.contains("var requestedPreview by remember(previewKey)"))
        assertTrue(bet.contains("vm.preview.collect { result ->"))
        assertTrue(bet.indexOf("vm.refreshPreview(customerId, agentId") < bet.indexOf("vm.preview.collect { result ->"))
        assertFalse(bet.substringBefore("BetEntryConfirmButton(").contains("vm.preview.value"))
        assertTrue(bet.contains("currentPreview !== vm.preview.value"))
        assertTrue(bet.contains("if (submit is SubmitState.Success)"))
        assertTrue(bet.contains("if (entryId == 0L) {\n                raw = \"\"\n                format = QuickFormat.MANUAL"))
        assertTrue(bet.contains("receiptTotal = (currentPreview.parse as? ParseResult.Success)"))
        assertTrue(bet.contains("vm.editConfirm(item, it, session, raw, format)"))
        val ui = File(root + "BetEntryControls.kt").readText()
        assertTrue(ui.contains("language.text(\"အထူး\", \"Special\")"))
        assertTrue(ui.contains("Color(0xFFF44982), Color(0xFFBC0C50), Color(0xFF820A38)"))
    }
}
