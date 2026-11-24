package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C01 UI source contracts; JPEG pixel and pagination tests remain separate. */
class VoucherPreviewHomeChromeContractTest {
    private fun source(name: String) =
        File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun previewKeepsBitmapFitAndPageLifecycle() {
        val s = source("VoucherPreviewPlaceholderScreen.kt")
        assertTrue(s.contains(".cherryFinancialBackground()"))
        assertEquals(1, s.split("CherryFinancialPanel {").size - 1)
        assertTrue(s.contains("rememberPagerState(pageCount = { renderer.pageCount })"))
        assertTrue(s.contains("renderer.render(index)"))
        assertTrue(s.contains("contentScale = ContentScale.Fit"))
        assertTrue(s.contains("pageBitmap.recycle()"))
        assertTrue(s.contains("pagerState.currentPage > 0 && !exporting"))
        assertTrue(s.contains("pagerState.currentPage < renderer.pageCount - 1 && !exporting"))
        assertTrue(s.contains("Modifier.fillMaxWidth().heightIn(min = 54.dp)"))
    }

    @Test fun exportStillUsesOriginalRendererAndSingleActionGuard() {
        val s = source("VoucherPreviewPlaceholderScreen.kt")
        assertTrue(s.contains("VoucherRenderer(document, sourceTypeface, headerPhoto)"))
        assertTrue(s.contains("VoucherGalleryExporter.save(context.applicationContext, renderer)"))
        assertTrue(s.contains("if (!exporting)"))
        assertTrue(s.contains("BackHandler(enabled = exporting)"))
        assertTrue(s.contains("enabled = !exporting"))
        assertTrue(s.contains("legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)"))
        assertTrue(s.contains("message = result.fold("))
        assertFalse(s.contains("ContentScale.Crop"))
    }

    @Test fun historySelectionAndTotalKeepTheSameInputs() {
        val s = source("Screens.kt").substringAfter("fun BetHistoryScreen(")
            .substringBefore("@Composable fun SettingsScreen(")
        assertTrue(s.contains("voucherSelectedEntryIds(entries, customerId, selectedDate, session, voucherIds)"))
        assertTrue(s.contains("betEntriesTotal(entries.filter { it.entry.id in selectedVoucherIds })"))
        assertTrue(s.contains("onVoucherPreview(selectedDate, session, selectedVoucherIds)"))
        assertTrue(s.contains("enabled = selectedVoucherIds.isNotEmpty()"))
        assertTrue(s.contains("Modifier.fillMaxWidth().heightIn(min = 52.dp)"))
    }
}
