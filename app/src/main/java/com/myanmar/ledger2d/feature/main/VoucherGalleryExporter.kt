package com.myanmar.ledger2d.feature.main

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Writes each preview page as an individual JPEG. On API 29+, pending MediaStore
 * rows are not published until every page has been written successfully.
 * A failed batch is cleaned up; no voucher status is saved in the ledger.
 */
internal object VoucherGalleryExporter {
    const val albumName = "Cherry 2D Ledger"

    fun save(context: Context, renderer: VoucherRenderer): Int {
        val suffix = System.currentTimeMillis()
        val document = renderer.document
        val base = "Cherry2D_Voucher_${document.date}_${document.session.name}_${document.records.first().id}_$suffix"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeMediaStore(context, renderer, base)
        } else {
            writeLegacyGallery(context, renderer, base)
        }
    }

    private fun writeMediaStore(context: Context, renderer: VoucherRenderer, base: String): Int {
        val resolver = context.contentResolver
        val created = mutableListOf<android.net.Uri>()
        try {
            for (page in 0 until renderer.pageCount) {
                val metadata = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, pageFileName(base, page, renderer.pageCount))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$albumName")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, metadata)
                    ?: throw IOException("Gallery ထဲ JPEG ထည့်၍ မရပါ")
                created += uri
                val output = resolver.openOutputStream(uri)
                    ?: throw IOException("Gallery ပုံဖိုင်ဖွင့်၍ မရပါ")
                output.use { stream ->
                    val bitmap = renderer.render(page)
                    try {
                        if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)) {
                            throw IOException("JPEG ပုံပြောင်း၍ မရပါ")
                        }
                        stream.flush()
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            created.forEach { uri ->
                val visible = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(uri, visible, null, null) != 1) {
                    throw IOException("JPEG ပုံကို Gallery မှာ မပြနိုင်ပါ")
                }
            }
            return renderer.pageCount
        } catch (error: Exception) {
            created.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun writeLegacyGallery(context: Context, renderer: VoucherRenderer, base: String): Int {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), albumName)
        if (!folder.exists() && !folder.mkdirs()) throw IOException("Gallery Folder ဖန်တီး၍ မရပါ")
        val files = mutableListOf<File>()
        try {
            for (page in 0 until renderer.pageCount) {
                val file = File(folder, pageFileName(base, page, renderer.pageCount))
                files += file
                FileOutputStream(file).use { stream ->
                    val bitmap = renderer.render(page)
                    try {
                        if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)) {
                            throw IOException("JPEG ပုံပြောင်း၍ မရပါ")
                        }
                        stream.flush()
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            MediaScannerConnection.scanFile(
                context, files.map { it.absolutePath }.toTypedArray(), Array(files.size) { "image/jpeg" }, null,
            )
            return renderer.pageCount
        } catch (error: Exception) {
            files.forEach { runCatching { it.delete() } }
            throw error
        }
    }

    private fun pageFileName(base: String, index: Int, total: Int): String =
        "${base}_P${(index + 1).toString().padStart(total.toString().length, '0')}.jpg"
}
