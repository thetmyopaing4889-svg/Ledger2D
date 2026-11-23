package com.myanmar.ledger2d.core.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.myanmar.ledger2d.core.database.AgentEntity
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.profile.ProfileKind
import com.myanmar.ledger2d.core.profile.ProfilePhotoStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Full encrypted backup -> database/prefs/photos mutation -> restore and failure preflight. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], manifest=Config.NONE, application=Application::class)
class CherryFullProfileRestoreTest {
    private val password = "CherryBackup#2026"
    private val photoA = byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),1,2,3)
    private val photoB = byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),7,8,9)

    @Test fun encryptedBackupRestoresDatabasePhotoAndPreferencesTogether() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Application
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        val mediaDir = ProfilePhotoStore.photoDirectory(context)
        val backup = File(context.cacheDir,"profile_backup_" + UUID.randomUUID() + ".cherrybackup")
        val prefs = context.getSharedPreferences("cherry_profile_restore_qa",Context.MODE_PRIVATE)
        mediaDir.deleteRecursively()
        try {
            val id = db.agentDao().upsert(AgentEntity(name="Before backup",rate=80,
                createdAt=1,updatedAt=1))
            val photo = ProfilePhotoStore(context).photoFile(ProfileKind.AGENT,id)
            photo.parentFile!!.mkdirs()
            photo.writeBytes(photoA)
            assertTrue(prefs.edit().putString("sentinel","original").commit())
            val manager = CherryBackupManager(context,db)
            val preview = manager.backup(Uri.fromFile(backup),password.toCharArray())
            assertTrue(preview.databaseRows > 0L)
            db.agentDao().upsert(db.agentDao().get(id)!!.copy(name="Modified after backup"))
            photo.writeBytes(photoB)
            assertTrue(prefs.edit().putString("sentinel","changed").commit())

            manager.restore(Uri.fromFile(backup),password.toCharArray())
            assertEquals("Before backup",db.agentDao().get(id)!!.name)
            assertArrayEquals(photoA,photo.readBytes())
            assertEquals("original",prefs.getString("sentinel",null))
        } finally {
            prefs.edit().clear().commit()
            mediaDir.deleteRecursively()
            backup.delete()
            db.close()
        }
    }

    @Test fun tamperedEncryptedArchiveCannotAlterCurrentDatabaseOrPhotos() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Application
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        val mediaDir = ProfilePhotoStore.photoDirectory(context)
        val backup = File(context.cacheDir,"profile_backup_" + UUID.randomUUID() + ".cherrybackup")
        val corrupt = File(context.cacheDir,"profile_corrupt_" + UUID.randomUUID() + ".cherrybackup")
        mediaDir.deleteRecursively()
        try {
            val id=db.agentDao().upsert(AgentEntity(name="Untouched",rate=90,
                createdAt=1,updatedAt=1))
            val photo=ProfilePhotoStore(context).photoFile(ProfileKind.AGENT,id)
            photo.parentFile!!.mkdirs()
            photo.writeBytes(photoA)
            val manager=CherryBackupManager(context,db)
            manager.backup(Uri.fromFile(backup),password.toCharArray())
            val damaged=backup.readBytes()
            damaged[damaged.size-4] = (damaged[damaged.size-4].toInt() xor 0x40).toByte()
            corrupt.writeBytes(damaged)
            assertThrows(Exception::class.java) {
                manager.restore(Uri.fromFile(corrupt),password.toCharArray())
            }
            assertEquals("Untouched",db.agentDao().get(id)!!.name)
            assertArrayEquals(photoA,photo.readBytes())
        } finally {
            mediaDir.deleteRecursively()
            backup.delete()
            corrupt.delete()
            db.close()
        }
    }

    @Test fun previousV1ArchivesStillRestoreAndClearNonexistentProfilePhotos() = runBlocking {
        val context=RuntimeEnvironment.getApplication() as Application
        val db=Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        val mediaDir=ProfilePhotoStore.photoDirectory(context)
        val archive=File(context.cacheDir,"profile_v1_" + UUID.randomUUID() + ".cherrybackup")
        mediaDir.deleteRecursively()
        try {
            val id=db.agentDao().upsert(AgentEntity(name="Legacy",rate=77,createdAt=1,updatedAt=1))
            val plain=ByteArrayOutputStream()
            val keepOpen = { out: ZipOutputStream -> object: FilterOutputStream(out) {
                override fun close() { flush() }
            } }
            ZipOutputStream(plain).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(JSONObject().put("version",1)
                    .put("package","com.myanmar.ledger2d")
                    .put("created_at",Instant.now().toString())
                    .toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("database.json"))
                CherryBackupDatabase.write(db.openHelper.writableDatabase,keepOpen(zip))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("preferences.json"))
                CherryBackupPreferences(context).write(keepOpen(zip))
                zip.closeEntry()
            }
            archive.outputStream().use { encrypted ->
                CherryBackupCipher.encrypt(ByteArrayInputStream(plain.toByteArray()),
                    encrypted,password.toCharArray())
            }
            db.agentDao().upsert(db.agentDao().get(id)!!.copy(name="Modified"))
            val photo=ProfilePhotoStore(context).photoFile(ProfileKind.AGENT,id)
            photo.parentFile!!.mkdirs()
            photo.writeBytes(photoB)
            CherryBackupManager(context,db).restore(Uri.fromFile(archive),password.toCharArray())
            assertEquals("Legacy",db.agentDao().get(id)!!.name)
            assertFalse(photo.exists()) // V1 has no photo entries, so no stale portraits.
        } finally {
            mediaDir.deleteRecursively()
            archive.delete()
            db.close()
        }
    }
}
