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
 * Gallery-only export for a customer money statement.
 * Reuses the existing voucher album and all-or-nothing rollback behavior;
 * never inserts or modifies a ledger record.
 */
internal object CustomerSettlementJpegExporter {
    fun save(context: Context, renderer: CustomerSettlementJpegRenderer): Int {
        val base = "Cherry2D_CustomerStatement_${renderer.document.range.start}_" +
            "${renderer.document.range.end}_${System.currentTimeMillis()}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            saveMediaStore(context, renderer, base)
        else saveLegacy(context, renderer, base)
    }

    private fun pageName(base: String, page: Int, count: Int): String =
        "${base}_P${(page + 1).toString().padStart(count.toString().length, '0')}.jpg"

    private fun saveMediaStore(
        context: Context, renderer: CustomerSettlementJpegRenderer, base: String,
    ): Int {
        val resolver = context.contentResolver
        val created = mutableListOf<android.net.Uri>()
        try {
            for (page in 0 until renderer.pageCount) {
                val info = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, pageName(base, page, renderer.pageCount))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/${VoucherGalleryExporter.albumName}")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, info)
                    ?: throw IOException("JPEG သိမ်း၍ မရပါ။")
                created += uri
                resolver.openOutputStream(uri)?.use { out ->
                    val image = renderer.render(page)
                    try {
                        if (!image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out))
                            throw IOException("JPEG ပြောင်း၍ မရပါ။")
                        out.flush()
                    } finally { image.recycle() }
                } ?: throw IOException("JPEG ပုံဖိုင် ဖွင့်၍ မရပါ။")
            }
            created.forEach { uri ->
                val update = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(uri, update, null, null) != 1)
                    throw IOException("Gallery တွင် JPEG မပြနိုင်ပါ။")
            }
            return renderer.pageCount
        } catch (error: Exception) {
            created.forEach { runCatching { resolver.delete(it, null, null) } }
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context, renderer: CustomerSettlementJpegRenderer, base: String,
    ): Int {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            VoucherGalleryExporter.albumName)
        if (!folder.exists() && !folder.mkdirs()) throw IOException("Gallery Folder ဖန်တီး၍ မရပါ။")
        val files = mutableListOf<File>()
        try {
            for (page in 0 until renderer.pageCount) {
                val file = File(folder, pageName(base, page, renderer.pageCount))
                files += file
                FileOutputStream(file).use { out ->
                    val image = renderer.render(page)
                    try {
                        if (!image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out))
                            throw IOException("JPEG ပြောင်း၍ မရပါ။")
                        out.flush()
                    } finally { image.recycle() }
                }
            }
            MediaScannerConnection.scanFile(
                context, files.map { it.absolutePath }.toTypedArray(),
                Array(files.size) { "image/jpeg" }, null,
            )
            return renderer.pageCount
        } catch (error: Exception) {
            files.forEach { runCatching { it.delete() } }
            throw error
        }
    }
}
