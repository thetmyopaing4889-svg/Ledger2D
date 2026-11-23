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
import java.util.UUID
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
    data class ReplaceDraft(val token: String) : ProfilePhotoChange
}

/** Private 512px JPEG thumbnails. Picked gallery URIs are never kept as permanent references. */
class ProfilePhotoStore(private val context: Context) {
    companion object {
        internal val fileLock = Any()
        private val mutableRevision = MutableStateFlow(0L)
        val revision: StateFlow<Long> get() = mutableRevision
        internal val validName = Regex("(agent|customer)-[1-9][0-9]*\\.jpg")
        private val validDraft = Regex("(agent-[0-9]+|customer-[0-9]+-[0-9]+)")
        private fun drafts(context: Context) = File(context.filesDir, "cherry_profile_drafts")
        private fun atomicMove(from: File, to: File) {
            try {
                Files.move(from.toPath(), to.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        fun photoDirectory(context: Context): File = File(context.filesDir, "cherry_profile_photos")
        fun notifyChanged() { mutableRevision.value = mutableRevision.value + 1L }
    }

    fun photoFile(kind: ProfileKind, id: Long): File =
        File(photoDirectory(context), kind.stem + "-" + id + ".jpg")

    /** Draft names are stable through Android process recreation, not ephemeral URI strings. */
    fun draftToken(kind: ProfileKind, id: Long, agentId: Long = 0L): String {
        require(id >= 0 && agentId >= 0)
        return if (kind == ProfileKind.AGENT) "agent-" + id
            else "customer-" + agentId + "-" + id
    }
    fun draftFile(token: String): File {
        require(validDraft.matches(token)) { "Invalid photo draft key" }
        return File(drafts(context), token + ".jpg")
    }
    fun hasDraft(token: String): Boolean = draftFile(token).let { it.isFile && it.length() > 3L }
    fun discardDraft(token: String) {
        synchronized(fileLock) { draftFile(token).delete() }
    }

    /** Copy and downsample while the Gallery URI grant is still alive. */
    suspend fun stageGallery(uri: Uri, token: String): String = withContext(Dispatchers.IO) {
        val output = draftFile(token)
        val photo = prepare(ProfilePhotoChange.Replace(uri))
        try {
            val directory = drafts(context)
            check(directory.isDirectory || directory.mkdirs())
            val partial = File(directory, "." + token + "." + UUID.randomUUID() + ".partial")
            try {
                requireNotNull(photo.temp).copyTo(partial, overwrite = true)
                synchronized(fileLock) { atomicMove(partial, output) }
                notifyChanged()
            } finally { partial.delete() }
            token
        } finally { photo.cleanup() }
    }

    data class PreparedPhoto(
        val change: ProfilePhotoChange, val temp: File? = null, val owned: Boolean = false,
    ) {
        fun cleanup() { if (owned) temp?.delete() }
    }

    suspend fun prepare(change: ProfilePhotoChange): PreparedPhoto = withContext(Dispatchers.IO) {
        if (change is ProfilePhotoChange.ReplaceDraft) {
            val draft = draftFile(change.token)
            require(draft.isFile && draft.length() in 4L..(2L * 1024 * 1024)) {
                "ပုံဖိုင် မတွေ့ပါ။ Gallery မှ ပြန်ရွေးပါ"
            }
            return@withContext PreparedPhoto(change, draft)
        }
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
            PreparedPhoto(change, temp, owned = true)
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            bmp.recycle()
        }
    }

    /** Photo undo log survives until Room finishes its transaction. */
    class AppliedChange internal constructor(
        private val target: File?, private val previous: File?, private val existed: Boolean,
    ) {
        fun finish() {
            synchronized(fileLock) {
                previous?.delete()
                if (target != null) notifyChanged()
            }
        }
        fun rollback() {
            synchronized(fileLock) {
                if (target != null) {
                    if (existed) {
                        check(previous?.isFile == true) { "Original profile photo missing for rollback" }
                        atomicMove(previous, target)
                    } else {
                        check(!target.exists() || target.delete()) { "Cannot undo new photo" }
                    }
                    notifyChanged()
                }
                previous?.delete()
            }
        }
    }

    /** Must run inside the caller's outer Room transaction. */
    suspend fun applyReversible(kind: ProfileKind, id: Long, prepared: PreparedPhoto): AppliedChange =
        withContext(Dispatchers.IO) {
            require(id > 0L)
            if (prepared.change == ProfilePhotoChange.Keep)
                return@withContext AppliedChange(null, null, false)
            synchronized(fileLock) {
                val target = photoFile(kind, id)
                val directory = photoDirectory(context)
                check(directory.isDirectory || directory.mkdirs())
                val existed = target.isFile
                val old = if (existed)
                    File(directory, "." + target.name + "." + UUID.randomUUID() + ".rollback")
                    else null
                var backedUp = false
                try {
                    if (old != null) {
                        target.copyTo(old)
                        backedUp = true
                    }
                    when (prepared.change) {
                        ProfilePhotoChange.Remove -> {
                            if (target.exists()) check(target.delete())
                        }
                        is ProfilePhotoChange.Replace, is ProfilePhotoChange.ReplaceDraft -> {
                            val source = requireNotNull(prepared.temp)
                            check(source.isFile) { "Gallery ပုံ မတွေ့ပါ" }
                            val pending = File(directory,
                                "." + target.name + "." + UUID.randomUUID() + ".partial")
                            try {
                                source.copyTo(pending)
                                atomicMove(pending, target)
                            } finally { pending.delete() }
                        }
                        ProfilePhotoChange.Keep -> Unit
                    }
                    AppliedChange(target, old, existed)
                } catch (error: Throwable) {
                    try {
                        if (backedUp) atomicMove(requireNotNull(old), target)
                        else if (!existed) target.delete()
                    } catch (rollbackFailure: Throwable) {
                        error.addSuppressed(rollbackFailure)
                    }
                    old?.delete()
                    throw error
                }
            }
        }

    /** Legacy direct caller compatibility; transactional callers use applyReversible. */
    suspend fun apply(kind: ProfileKind, id: Long, prepared: PreparedPhoto) {
        applyReversible(kind, id, prepared).finish()
    }

}
