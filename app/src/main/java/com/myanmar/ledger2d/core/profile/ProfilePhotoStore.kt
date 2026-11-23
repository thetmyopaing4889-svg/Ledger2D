package com.myanmar.ledger2d.core.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

enum class ProfileKind(val stem: String) { AGENT("agent"), CUSTOMER("customer") }
sealed interface ProfilePhotoChange {
    data object Keep : ProfilePhotoChange
    data object Remove : ProfilePhotoChange
    data class Replace(val uri: Uri) : ProfilePhotoChange
}

/** Private 512px JPEG thumbnails. Picked gallery URIs are never kept as permanent references. */
class ProfilePhotoStore(private val context: Context) {
    companion object {
        internal val fileLock = Any()
        private val mutableRevision = MutableStateFlow(0L)
        val revision: StateFlow<Long> get() = mutableRevision
        internal val validName = Regex("(agent|customer)-[1-9][0-9]*\\.jpg")
        fun photoDirectory(context: Context): File = File(context.filesDir, "cherry_profile_photos")
        fun notifyChanged() { mutableRevision.value = mutableRevision.value + 1L }
    }

    fun photoFile(kind: ProfileKind, id: Long): File =
        File(photoDirectory(context), kind.stem + "-" + id + ".jpg")

    data class PreparedPhoto(val change: ProfilePhotoChange, val temp: File? = null) {
        fun cleanup() { temp?.delete() }
    }

    suspend fun prepare(change: ProfilePhotoChange): PreparedPhoto = withContext(Dispatchers.IO) {
        if (change !is ProfilePhotoChange.Replace) return@withContext PreparedPhoto(change)
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, change.uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                require(w in 1..25000 && h in 1..25000) { "ပုံဖိုင် မမှန်ကန်ပါ" }
                val factor = minOf(1f, 512f / max(w, h))
                decoder.setTargetSize(max(1, (w * factor).toInt()), max(1, (h * factor).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(change.uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            require(bounds.outWidth in 1..25000 && bounds.outHeight in 1..25000) { "ပုံဖိုင် မမှန်ကန်ပါ" }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
            context.contentResolver.openInputStream(change.uri)?.use {
                BitmapFactory.decodeStream(it, null,
                    BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("ပုံကို ဖတ်၍မရပါ")
        }
        val temp = File.createTempFile("cherry_profile_", ".jpg", context.cacheDir)
        try {
            temp.outputStream().use { require(bmp.compress(Bitmap.CompressFormat.JPEG, 82, it)) }
            require(temp.length() in 1..(2L * 1024L * 1024L)) { "ပုံဖိုင် ကြီးလွန်းပါသည်" }
            PreparedPhoto(change, temp)
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            bmp.recycle()
        }
    }

    suspend fun apply(kind: ProfileKind, id: Long, prepared: PreparedPhoto) = withContext(Dispatchers.IO) {
        require(id > 0L)
        synchronized(fileLock) {
            val target = photoFile(kind, id)
            when (prepared.change) {
                ProfilePhotoChange.Keep -> Unit
                ProfilePhotoChange.Remove -> {
                    if (target.exists()) check(target.delete())
                    notifyChanged()
                }
                is ProfilePhotoChange.Replace -> {
                    val stage = requireNotNull(prepared.temp)
                    val dir = photoDirectory(context)
                    check(dir.isDirectory || dir.mkdirs())
                    val tmp = File(dir, "." + target.name + ".partial")
                    try {
                        stage.copyTo(tmp, overwrite = true)
                        try {
                            Files.move(tmp.toPath(), target.toPath(),
                                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        notifyChanged()
                    } finally { tmp.delete() }
                }
            }
        }
    }
}
