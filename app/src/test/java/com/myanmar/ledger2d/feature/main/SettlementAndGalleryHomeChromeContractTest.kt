package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C03/C04 source contracts. Existing JPEG and monetary tests remain independent. */
class SettlementAndGalleryHomeChromeContractTest {
    private fun source(name: String) = File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun customerSelectorAndRebateRetainOriginalMoneyInputs() {
        val s = source("CustomerSettlementJpegScreen.kt")
        val selector = s.substringBefore("private fun CustomerSettlementJpegPreview(")
        assertEquals(4, selector.split("CherryFinancialPanel {").size - 1)
        assertTrue(selector.contains("agentId = it; customerId = -1L; error = null"))
        assertTrue(selector.contains("enabled = !busy && chosenCustomer != null && range != null"))
        assertTrue(selector.contains("StatementRange(customStart, customEnd)"))
        assertTrue(selector.contains("vm.customerEntries(customer.id).first()"))
        assertTrue(selector.contains("requestedRange.contains(it.entry.drawDate)"))
        assertTrue(selector.contains("CustomerSettlementCalculator.build(prepared, null)"))
        val preview = s.substringAfter("private fun CustomerSettlementJpegPreview(")
        assertEquals(2, preview.split("CherryFinancialPanel {").size - 1)
        assertTrue(preview.contains("customerRebateBasisPoints(percentText)"))
        assertTrue(preview.contains("CustomerSettlementCalculator.build(source, basisPoints)"))
        assertTrue(preview.contains("preparation?.takeIf { it.first === document }?.second"))
        assertTrue(preview.contains("contentScale = ContentScale.Fit"))
        assertTrue(preview.contains("image.recycle()"))
        assertFalse(preview.contains("ContentScale.Crop"))
        assertTrue(preview.contains("enabled = !exporting && renderer != null && !invalidPercent && !permissionPending"))
    }

    @Test fun allGalleryPreviewsGuardPermissionAndShowLocalizedFeedback() {
        for (name in listOf("VoucherPreviewPlaceholderScreen.kt", "AgentSettlementJpegScreen.kt", "CustomerSettlementJpegScreen.kt")) {
            val s = source(name)
            assertTrue(s.contains("var permissionPending by remember"))
            assertTrue(s.contains("permissionPending = true"))
            assertTrue(s.contains("permissionPending = false"))
            assertTrue(s.contains("if (exporting || permissionPending) return@Button"))
            assertTrue(s.contains("BackHandler(enabled = exporting)"))
            assertTrue(s.contains("BackHandler(enabled = permissionPending)"))
            assertTrue(s.contains("if (!exporting && !permissionPending) onBack()"))
            assertTrue(s.contains("message = result.fold("))
            assertTrue(s.contains("Saving to Gallery"))
            assertTrue(s.contains("Waiting for permission"))
            assertTrue(s.contains("Storage permission is required to save images on Android 8/9."))
            assertTrue(s.contains("JPEG export"))
            assertTrue(s.contains("language.text(\"အဆင်ပြေ\", \"OK\")"))
        }
    }

    @Test fun originalExportersAndAgentFreshnessGuardRemain() {
        assertTrue(source("VoucherPreviewPlaceholderScreen.kt").contains("VoucherGalleryExporter.save(context.applicationContext, renderer)"))
        val agent = source("AgentSettlementJpegScreen.kt")
        assertTrue(agent.contains("check(latest == document)"))
        assertTrue(agent.contains("AgentSettlementJpegExporter.save(context.applicationContext, ready)"))
        assertTrue(source("CustomerSettlementJpegScreen.kt").contains("CustomerSettlementJpegExporter.save(context.applicationContext, current)"))
    }
}
