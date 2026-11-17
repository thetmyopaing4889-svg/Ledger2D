package com.myanmar.ledger2d.core.backup

import android.content.Context
import android.net.Uri
import com.myanmar.ledger2d.core.database.LedgerDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONObject

internal data class CherryBackupPreview(
    val createdAt: String,
    val databaseRows: Long,
    val preferenceFiles: Int,
)

/**
 * Portable, password-protected full user-data archive: Room user tables and
 * all application preferences. Does not copy WorkManager's transient DB or
 * files the user previously exported outside the application's storage.
 */
internal class CherryBackupManager(
    private val context: Context,
    private val room: LedgerDatabase,
) {
    private val prefs=CherryBackupPreferences(context)
    private val marker=File(context.noBackupFilesDir,"cherry_restore.pending")
    private val recovery=File(context.noBackupFilesDir,"cherry_pre_restore_recovery.zip")

    companion object {
        private const val ARCHIVE_VERSION = 1
        private const val MAX_ENCRYPTED = 512L * 1024 * 1024
        private const val MAX_PLAIN_ZIP = 768L * 1024 * 1024
        private val lock=Any()

        /** Called before LIVE collectors/workers start, on every app launch. */
        fun recoverIfPending(context: Context, room: LedgerDatabase): Boolean =
            synchronized(lock) {
                val manager=CherryBackupManager(context,room)
                // An interrupted export may leave plaintext cache fragments.
                // They must never survive the next process startup.
                context.cacheDir.listFiles().orEmpty()
                    .filter { it.name.startsWith("cherry_backup_") && it.isFile }
                    .forEach { it.delete() }
                if(!manager.marker.exists()) {
                    manager.recovery.delete()
                    File(context.noBackupFilesDir,"cherry_recovery_staging.zip").delete()
                    return@synchronized false
                }
                check(manager.recovery.isFile) {
                    "Incomplete restore without recovery file; refusing to open ledger"
                }
                val old=manager.inspectPlainArchive(manager.recovery)
                manager.applyPlainArchive(manager.recovery,old.preferences)
                check(manager.marker.delete()) { "Cannot finalize restore recovery" }
                manager.recovery.delete()
                true
            }
    }

    private data class Inspection(
        val preview: CherryBackupPreview,
        val preferences: Map<String,Map<String,Any>>,
    )
    private class LimitInput(source:InputStream,private val max:Long):FilterInputStream(source) {
        private var size=0L
        override fun read():Int {
            val n=super.read()
            if(n>=0) { size++;if(size>max)throw IOException("Backup file too large") }
            return n
        }
        override fun read(bytes:ByteArray,off:Int,len:Int):Int {
            val n=super.read(bytes,off,len)
            if(n>0) { size+=n;if(size>max)throw IOException("Backup file too large") }
            return n
        }
    }
    private class LimitOutput(out:OutputStream,private val max:Long):FilterOutputStream(out) {
        private var size=0L
        override fun write(value:Int) {
            size++
            if(size>max)throw IOException("Backup file too large")
            out.write(value)
        }
        override fun write(bytes:ByteArray,off:Int,len:Int) {
            size+=len
            if(size>max)throw IOException("Backup file too large")
            out.write(bytes,off,len)
        }
    }
    private class KeepOpen(out:OutputStream):FilterOutputStream(out) {
        override fun close() { flush() }
    }
    private fun temp(ext:String)=File.createTempFile("cherry_backup_",ext,context.cacheDir)

    private fun buildPlainArchive(destination:File) {
        val db=room.openHelper.writableDatabase
        db.beginTransaction()
        try {
            FileOutputStream(destination).use { file ->
                ZipOutputStream(KeepOpen(file).buffered()).use { zip ->
                    zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(JSONObject().put("version",ARCHIVE_VERSION)
                        .put("package","com.myanmar.ledger2d")
                        .put("created_at",Instant.now().toString())
                        .toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("database.json"))
                    CherryBackupDatabase.write(db,KeepOpen(zip))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("preferences.json"))
                    prefs.write(KeepOpen(zip))
                    zip.closeEntry()
                }
                file.fd.sync()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun inspectPlainArchive(plain:File):Inspection {
        ZipFile(plain).use { zip ->
            val names=mutableSetOf<String>()
            val entries=zip.entries()
            while(entries.hasMoreElements()) {
                val entry=entries.nextElement()
                require(!entry.isDirectory && names.add(entry.name))
            }
            require(names==setOf("manifest.json","database.json","preferences.json"))
            val manifest=zip.getInputStream(zip.getEntry("manifest.json")).use {
                val bytes=it.readBytes()
                require(bytes.size<16_384)
                JSONObject(String(bytes,Charsets.UTF_8))
            }
            require(manifest.getInt("version")==ARCHIVE_VERSION)
            require(manifest.getString("package")=="com.myanmar.ledger2d")
            val createdAt=manifest.getString("created_at")
            Instant.parse(createdAt)
            val rows=zip.getInputStream(zip.getEntry("database.json")).use {
                CherryBackupDatabase.inspect(room.openHelper.readableDatabase,it)
            }
            val state=zip.getInputStream(zip.getEntry("preferences.json")).use {
                prefs.parse(it)
            }
            return Inspection(CherryBackupPreview(createdAt,rows,state.size),state)
        }
    }

    private fun decryptToPrivateZip(uri:Uri,password:CharArray):File {
        val stage=temp(".zip")
        try {
            val source=context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open backup file")
            source.use { input ->
                FileOutputStream(stage).use { output ->
                    CherryBackupCipher.decrypt(LimitInput(input,MAX_ENCRYPTED),
                        LimitOutput(KeepOpen(output),MAX_PLAIN_ZIP),password)
                    output.fd.sync()
                }
            }
            return stage
        } catch(error:Exception) {
            stage.delete()
            throw error
        }
    }

    /** Checks password, tag, every database row, schema and preferences before restore. */
    fun inspect(uri:Uri,password:CharArray):CherryBackupPreview = synchronized(lock) {
        val file=decryptToPrivateZip(uri,password)
        try { inspectPlainArchive(file).preview } finally { file.delete() }
    }

    fun backup(uri:Uri,password:CharArray):CherryBackupPreview = synchronized(lock) {
        check(!marker.exists()) { "Incomplete restore must recover first" }
        val plain=temp(".zip")
        val encrypted=temp(".cherrybackup")
        try {
            buildPlainArchive(plain)
            FileInputStream(plain).use { input ->
                FileOutputStream(encrypted).use { output ->
                    CherryBackupCipher.encrypt(input,KeepOpen(output),password)
                    output.fd.sync()
                }
            }
            require(encrypted.length()<=MAX_ENCRYPTED) {
                "Backup exceeds the maximum supported file size"
            }
            val preview=inspectPlainArchive(plain).preview
            val target=context.contentResolver.openOutputStream(uri,"wt")
                ?: throw IOException("Could not write backup destination")
            target.use { output ->
                FileInputStream(encrypted).use { it.copyTo(output) }
                output.flush()
            }
            preview
        } finally {
            plain.delete()
            encrypted.delete()
        }
    }

    /** Database transaction + in-process preference rollback on error. */
    private fun applyPlainArchive(plain:File,state:Map<String,Map<String,Any>>) {
        val db=room.openHelper.writableDatabase
        val previous=prefs.snapshot()
        var open=false
        try {
            db.beginTransaction()
            open=true
            ZipFile(plain).use { zip ->
                zip.getInputStream(zip.getEntry("database.json")).use {
                    CherryBackupDatabase.importInsideTransaction(db,it)
                }
            }
            prefs.replace(state)
            db.setTransactionSuccessful()
            db.endTransaction()
            open=false
        } catch(error:Exception) {
            if(open)try { db.endTransaction() } catch(failure:Exception) {
                error.addSuppressed(failure)
            }
            try { prefs.replace(previous) } catch(failure:Exception) {
                error.addSuppressed(failure)
            }
            throw error
        }
    }

    /** Restore only after confirmation; crash recovery journal prevents partial state. */
    fun restore(uri:Uri,password:CharArray):CherryBackupPreview = synchronized(lock) {
        check(!marker.exists()) { "Incomplete restore must recover first" }
        val incoming=decryptToPrivateZip(uri,password)
        try {
            val inspected=inspectPlainArchive(incoming)
            val staging=File(context.noBackupFilesDir,"cherry_recovery_staging.zip")
            try {
                buildPlainArchive(staging)
                check(staging.renameTo(recovery)) { "Could not create recovery archive" }
                FileOutputStream(marker).use { output ->
                    output.write("pending".toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                try {
                    applyPlainArchive(incoming,inspected.preferences)
                } catch(error:Exception) {
                    try {
                        val old=inspectPlainArchive(recovery)
                        applyPlainArchive(recovery,old.preferences)
                    } catch(failure:Exception) {
                        error.addSuppressed(failure)
                        // Leave pending marker for startup to retry recovery.
                        throw error
                    }
                    marker.delete()
                    recovery.delete()
                    throw error
                }
                check(marker.delete()) { "Restore recovery marker could not be finalized" }
                recovery.delete()
                inspected.preview
            } finally { staging.delete() }
        } finally { incoming.delete() }
    }
}
