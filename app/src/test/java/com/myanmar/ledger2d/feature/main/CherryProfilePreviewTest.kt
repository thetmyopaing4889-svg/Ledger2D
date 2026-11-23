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

    @Test fun profileFormsReuseStandardHeaderWithSeparatedPhotoAndSingleFieldFrames() {
        val source=File("src/main/java/com/myanmar/ledger2d/feature/main/CherryProfileFormUi.kt")
            .readText()
        val page=source.substringAfter("internal fun CherryProfileFormPage(")
            .substringBefore("@Composable\ninternal fun CherryProfileField(")
        val field=source.substringAfter("internal fun CherryProfileField(")
        assertTrue(page.contains("AppScaffold(title=title, onBack=onBack)"))
        assertTrue(page.contains("BackHandler(onBack = onBack)"))
        assertTrue(page.contains("Modifier.size(126.dp)"))
        assertFalse(page.contains("cherry_header_art"))
        assertFalse(page.contains("height(274.dp)"))
        assertFalse(page.contains("start=141.dp"))
        assertFalse(page.contains("TextAlign.End"))
        assertTrue(field.contains("BasicTextField("))
        assertTrue(field.contains("Text("))
        assertTrue(field.contains("language.translate(label)"))
        assertFalse(field.contains("OutlinedTextField("))
        assertTrue(field.contains("if(focused) 1.5.dp else 1.dp"))
        assertTrue(field.contains("singleLine=label!=\"မှတ်ချက်\""))
        assertTrue(page.contains("val saveEnabled=canSave&&!saving"))
        assertTrue(page.contains("Spacer(Modifier.height(104.dp))"))
    }

    @Test fun agentAndCustomerUseTheSameFormAndKeepCropCallbacks() {
        val screens=File("src/main/java/com/myanmar/ledger2d/feature/main/Screens.kt").readText()
        assertEquals(2, Regex("CherryProfileFormPage\\(").findAll(screens).count())
        assertEquals(2, Regex("CherryProfileCropDialog\\(").findAll(screens).count())
        assertEquals(2, Regex("vm.profilePhotos.cropStaged\\(").findAll(screens).count())
        assertTrue(screens.contains("vm.saveAgentWithPhoto("))
        assertTrue(screens.contains("vm.saveCustomerWithPhoto("))
    }

    @Test fun premiumFormUsesLayeredGlassAndRaisedGlossySaveInsteadOfFlatWhite() {
        val source=File("src/main/java/com/myanmar/ledger2d/feature/main/CherryProfileFormUi.kt")
            .readText()
        assertTrue(source.contains("Color(0xFFFEF8FB)"))
        assertTrue(source.contains("Color(0xFFF4DFE9)"))
        assertTrue(source.contains(".homeRubyShadow(radius=26.dp"))
        assertTrue(source.contains(".homeRubyShadow(radius=29.dp"))
        assertTrue(source.contains("Color(0xFFFF5E93)"))
        assertTrue(source.contains("Color(0xFF78072E)"))
        assertTrue(source.contains("Color(0xB9FFFFFF)"))
        assertTrue(source.contains("AppScaffold(title=title, onBack=onBack)"))
        assertTrue(source.contains("dimensions.use { BitmapFactory.decodeStream(it, null, bounds) }"))
    }
}
