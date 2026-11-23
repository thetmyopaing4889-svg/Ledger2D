package com.myanmar.ledger2d.feature.main

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession
import com.myanmar.ledger2d.core.profile.ProfileKind
import com.myanmar.ledger2d.core.profile.ProfilePhotoStore
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Portrait is a header decoration only; exported money and pagination never depend on it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], manifest=Config.NONE, application=Application::class)
class ProfileJpegHeaderTest {
    private val date = LocalDate.of(2026, 11, 20)

    @Test fun readsStoredPhotoOnceAndFallsBackForMissingOrDeletedPortraits() {
        val app = RuntimeEnvironment.getApplication() as Application
        val store = ProfilePhotoStore(app)
        val id = 849211L
        val photoFile = store.photoFile(ProfileKind.CUSTOMER, id)
        photoFile.parentFile?.mkdirs()
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            bmp.eraseColor(Color.rgb(200, 40, 80))
            photoFile.outputStream().use {
                assertTrue(bmp.compress(Bitmap.CompressFormat.JPEG, 90, it))
            }
            val decoded = loadJpegProfilePhoto(store, ProfileKind.CUSTOMER, id)
            assertNotNull(decoded)
            try {
                assertEquals(128, decoded!!.width)
                assertEquals(128, decoded.height)
                val page = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                try {
                    drawJpegProfileAvatar(Canvas(page), decoded)
                    drawJpegProfileAvatar(Canvas(page), null)
                } finally { page.recycle() }
            } finally { decoded?.recycle() }
            photoFile.delete()
            assertNull(loadJpegProfilePhoto(store, ProfileKind.CUSTOMER, id))
            assertNull(loadJpegProfilePhoto(store, ProfileKind.CUSTOMER, 0L))
        } finally {
            bmp.recycle()
            photoFile.delete()
        }
    }

    @Test fun voucherHeaderPhotoDoesNotChangeSelectedRowsTotalsOrPagination() {
        val document = VoucherDocument(
            "A long customer name preserved in the document",
            date, DrawSession.MORNING,
            (1..22).map { VoucherRecord(it.toLong(), "19R500", 2, 1000) },
        )
        val photo = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            val baseline = VoucherRenderer(document)
            val withPhoto = VoucherRenderer(document, null, photo)
            assertEquals(baseline.pageCount, withPhoto.pageCount)
            assertEquals(baseline.document.totalDigits, withPhoto.document.totalDigits)
            assertEquals(baseline.document.totalAmount, withPhoto.document.totalAmount)
            val page = withPhoto.render(0)
            try {
                assertEquals(1080, page.width)
                assertEquals(1920, page.height)
                assertEquals(baseline.showsTotals(0), withPhoto.showsTotals(0))
            } finally { page.recycle() }
        } finally { photo.recycle() }
    }

    @Test fun bothSettlementHeadersDrawPortraitIncludingFinalSummaryWithoutChangingMoney() {
        val range = StatementRange(date, date)
        val photo = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        try {
            val agentDocument = AgentSettlementDocument("Agent name", range, emptyList())
            val agents = AgentSettlementJpegRenderer(agentDocument, photo)
            val agentSummary = agents.render(agents.pageCount - 1)
            try {
                assertEquals(1920, agentSummary.height)
                assertTrue(agents.isSummary(agents.pageCount - 1))
                assertEquals(0L, agents.document.settlement)
            } finally { agentSummary.recycle() }

            val customerDocument = CustomerSettlementDocument(
                "Customer name", range, emptyList(), null,
            )
            val customers = CustomerSettlementJpegRenderer(customerDocument, null, photo)
            val customerSummary = customers.render(customers.pageCount - 1)
            try {
                assertEquals(1920, customerSummary.height)
                assertTrue(customers.isSummary(customers.pageCount - 1))
                assertEquals(0L, customers.document.settlement)
            } finally { customerSummary.recycle() }
        } finally { photo.recycle() }
    }

    @Test fun profilePhotoChangesNeverTouchTransactionRowRenderers() {
        val path = "src/main/java/com/myanmar/ledger2d/feature/main/"
        for (renderer in listOf("VoucherRenderer.kt",
                "AgentSettlementJpegRenderer.kt", "CustomerSettlementJpegRenderer.kt")) {
            val source = File(path + renderer).readText()
            assertTrue(renderer, source.contains("drawJpegProfileAvatar(canvas, headerPhoto)"))
            val header = source.substringAfter("private fun drawHeader(")
                .substringBefore("private fun drawRows(")
                .substringBefore("private fun drawTable(")
            assertTrue(renderer, header.contains("drawJpegProfileAvatar(canvas, headerPhoto)"))
            assertEquals(renderer, 1, Regex("drawJpegProfileAvatar\\(").findAll(source).count())
        }
    }
}
