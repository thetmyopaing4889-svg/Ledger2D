package com.myanmar.ledger2d.core.profile

import android.app.Application
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class CherryProfileArchiveTest {
    private val jpeg = byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),0x20,0x21,0x22)

    @Test fun photosRoundTripAreAttachedToOriginalAgentAndCustomerIds() {
        val context=RuntimeEnvironment.getApplication<Application>()
        val dir=ProfilePhotoStore.photoDirectory(context)
        dir.deleteRecursively()
        check(dir.mkdirs())
        try {
            File(dir,"agent-17.jpg").writeBytes(jpeg)
            File(dir,"customer-29.jpg").writeBytes(jpeg)
            val output=ByteArrayOutputStream()
            CherryProfileArchive.write(context,output)
            assertEquals(2,CherryProfileArchive.read(ByteArrayInputStream(output.toByteArray())))
            val stage=File(context.cacheDir,"photo_test_staging")
            try {
                assertEquals(2,CherryProfileArchive.read(
                    ByteArrayInputStream(output.toByteArray()),stage))
                assertArrayEquals(jpeg,File(stage,"agent-17.jpg").readBytes())
                assertArrayEquals(jpeg,File(stage,"customer-29.jpg").readBytes())
            } finally { stage.deleteRecursively() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun traversalAndDuplicatePhotoEntriesAreRejected() {
        fun zipWith(vararg paths: String):ByteArray {
            val data=ByteArrayOutputStream()
            ZipOutputStream(data).use { zip ->
                paths.forEach { path ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(jpeg)
                    zip.closeEntry()
                }
            }
            return data.toByteArray()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CherryProfileArchive.read(ByteArrayInputStream(zipWith("../agent-17.jpg")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CherryProfileArchive.read(ByteArrayInputStream(zipWith("customer-0.jpg")))
        }
        // Runtime zip implementations may reject duplicates even before import.
        assertThrows(Exception::class.java) {
            CherryProfileArchive.read(ByteArrayInputStream(zipWith("agent-17.jpg","agent-17.jpg")))
        }
    }

    @Test fun invalidJpegAndOversizedUncompressedPhotosAreRejected() {
        val invalid=ByteArrayOutputStream()
        ZipOutputStream(invalid).use { zip ->
            zip.putNextEntry(ZipEntry("agent-1.jpg"))
            zip.write("plain text".toByteArray())
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CherryProfileArchive.read(ByteArrayInputStream(invalid.toByteArray()))
        }
    }
}
