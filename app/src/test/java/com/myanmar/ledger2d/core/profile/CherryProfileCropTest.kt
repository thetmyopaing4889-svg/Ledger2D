package com.myanmar.ledger2d.core.profile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real JPEG crop, cancellation, and agent/customer draft identity regressions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class CherryProfileCropTest {
    private fun stripedBitmap(): Bitmap {
        val bitmap=Bitmap.createBitmap(240,120,Bitmap.Config.ARGB_8888)
        for (y in 0 until 120) for (x in 0 until 240)
            bitmap.setPixel(x,y,if(x<120) Color.RED else Color.BLUE)
        return bitmap
    }

    @Test fun squareCropRespectsHorizontalPanAndZoom() {
        val original=stripedBitmap()
        try {
            val left=ProfilePhotoStore.CropMath.squareCrop(original,1f,-1f,0f)
            val right=ProfilePhotoStore.CropMath.squareCrop(original,1f,1f,0f)
            val tight=ProfilePhotoStore.CropMath.squareCrop(original,2f,1f,0f)
            try {
                assertEquals(120,left.width)
                assertEquals(120,left.height)
                assertEquals(Color.RED,left.getPixel(60,60))
                assertEquals(Color.BLUE,right.getPixel(60,60))
                assertEquals(60,tight.width)
                assertEquals(60,tight.height)
            } finally { left.recycle();right.recycle();tight.recycle() }
        } finally { original.recycle() }
    }

    @Test fun cropStagedDoesNotOverwriteAcceptedPhotoBeforeConfirmation() = runBlocking {
        val context=RuntimeEnvironment.getApplication() as Application
        val photos=ProfilePhotoStore(context)
        val token=photos.draftToken(ProfileKind.AGENT,0L)
        val previous=photos.draftFile(token)
        val staged=photos.stagedFile(token)
        staged.parentFile!!.mkdirs()
        val accepted=byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),1,2,3)
        previous.writeBytes(accepted)
        val original=stripedBitmap()
        try {
            staged.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.JPEG,90,it)) }
            assertTrue(photos.hasStaged(token))
            assertArrayEquals("Gallery selection cannot replace the accepted draft",accepted,previous.readBytes())
            val result=photos.cropStaged(token,1f,1f,0f)
            assertEquals(token,result)
            assertFalse(photos.hasStaged(token))
            assertTrue(photos.hasDraft(token))
            val cropped=BitmapFactory.decodeFile(previous.absolutePath)
            assertNotNull(cropped)
            assertEquals(120,cropped!!.width)
            assertEquals(120,cropped.height)
            cropped.recycle()
        } finally {
            original.recycle()
            photos.discardDraft(token)
        }
    }

    @Test fun cancelStagedRetainsPreviouslyAcceptedGalleryDraft() {
        val context=RuntimeEnvironment.getApplication() as Application
        val photos=ProfilePhotoStore(context)
        val token=photos.draftToken(ProfileKind.CUSTOMER,0L,101L)
        val accepted=photos.draftFile(token)
        val staged=photos.stagedFile(token)
        accepted.parentFile!!.mkdirs()
        val bytes=byteArrayOf(1,2,3,4,5)
        accepted.writeBytes(bytes)
        staged.writeBytes(byteArrayOf(9,8,7,6,5))
        try {
            photos.discardStaged(token)
            assertArrayEquals(bytes,accepted.readBytes())
            assertFalse(staged.exists())
            assertTrue(photos.hasDraft(token))
        } finally { photos.discardDraft(token) }
    }
}
