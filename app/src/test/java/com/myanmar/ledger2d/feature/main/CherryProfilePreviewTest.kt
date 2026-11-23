package com.myanmar.ledger2d.feature.main

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.myanmar.ledger2d.core.profile.ProfileKind
import com.myanmar.ledger2d.core.profile.ProfilePhotoStore
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real BitmapFactory regression for selected Gallery drafts and saved portrait files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class CherryProfilePreviewTest {
    private fun realJpeg(file: File) {
        file.parentFile!!.mkdirs()
        val bitmap=Bitmap.createBitmap(180,120,Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(205,36,83))
            file.outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,86,it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun selectedGalleryDraftDecodesIntoVisibleImageBitmap() {
        val app=RuntimeEnvironment.getApplication() as Application
        val store=ProfilePhotoStore(app)
        val token=store.draftToken(ProfileKind.AGENT,0L)
        val file=store.draftFile(token)
        try {
            realJpeg(file)
            assertTrue(store.hasDraft(token))
            val decoded=decodeThumbnail(app,null,file)
            assertNotNull("Gallery-selected photo must appear in the form avatar",decoded)
            assertEquals(180,decoded!!.width)
            assertEquals(120,decoded.height)
        } finally { store.discardDraft(token) }
    }

    @Test fun savedAgentAndCustomerAvatarsRenderAndMissingPhotoUsesFallback() {
        val app=RuntimeEnvironment.getApplication() as Application
        val store=ProfilePhotoStore(app)
        val id=892441L
        val agent=store.photoFile(ProfileKind.AGENT,id)
        val customer=store.photoFile(ProfileKind.CUSTOMER,id)
        try {
            realJpeg(agent)
            realJpeg(customer)
            assertNotNull(decodeThumbnail(app,null,agent))
            assertNotNull(decodeThumbnail(app,null,customer))
            assertNull(decodeThumbnail(app,null,File(app.cacheDir,"no_photo_does_not_exist.jpg")))
        } finally {
            agent.delete()
            customer.delete()
        }
    }

    @Test fun premiumFormUsesLayeredGlassAndRaisedGlossySaveInsteadOfFlatWhite() {
        val source=File("src/main/java/com/myanmar/ledger2d/feature/main/CherryProfileFormUi.kt")
            .readText()
        assertTrue(source.contains("Brush.radialGradient("))
        assertTrue(source.contains("Color(0x9EFFFFFF)"))
        assertTrue(source.contains(".homeRubyShadow(radius=28.dp"))
        assertTrue(source.contains(".homeRubyShadow(radius=29.dp"))
        assertTrue(source.contains("Color(0xFFFF5E93)"))
        assertTrue(source.contains("Color(0xFF78072E)"))
        assertTrue(source.contains("Color(0xB9FFFFFF)"))
        assertTrue(source.contains("textAlign=TextAlign.End"))
        assertTrue(source.contains("dimensions.use { BitmapFactory.decodeStream(it, null, bounds) }"))
    }
}
