package com.myanmar.ledger2d.core.profile

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.database.AgentEntity
import com.myanmar.ledger2d.core.database.CustomerEntity
import com.myanmar.ledger2d.core.database.LedgerDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercise genuine Room transactions and real app-private JPEG files, not string-only tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], manifest=Config.NONE, application=Application::class)
class AtomicProfileSaveTest {
    private val first = byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),1,2,3)
    private val second = byteArrayOf(0xFF.toByte(),0xD8.toByte(),0xFF.toByte(),4,5,6)

    @Test fun failedPhotoSaveDoesNotCreateDuplicateAgentAndDraftCanBeRetried() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Application
        val photos = ProfilePhotoStore(context)
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        val key = photos.draftToken(ProfileKind.AGENT,0L)
        photos.draftFile(key).parentFile!!.mkdirs()
        photos.draftFile(key).writeBytes(first)
        try {
            val abort = runCatching {
                    saveProfileAtomically(db,photos,ProfileKind.AGENT,
                        ProfilePhotoChange.ReplaceDraft(key),
                        saveRecord={ db.agentDao().upsert(AgentEntity(
                            name="Test agent",rate=80L,createdAt=1L,updatedAt=1L)) },
                        afterPhotoApplied={ error("Simulated failure between photo write and commit") },
                    )
            }.exceptionOrNull()
            assertTrue(abort is IllegalStateException)
            assertTrue(db.agentDao().observeAll().first().isEmpty())
            assertTrue(photos.hasDraft(key))
            assertFalse(photos.photoDirectoryForTest().listFiles().orEmpty()
                .any { it.name == "agent-1.jpg" })
            val id = saveProfileAtomically(db,photos,ProfileKind.AGENT,
                ProfilePhotoChange.ReplaceDraft(key)) {
                db.agentDao().upsert(AgentEntity(
                    name="Test agent",rate=80L,createdAt=1L,updatedAt=1L))
            }
            assertEquals(1,db.agentDao().observeAll().first().size)
            assertArrayEquals(first,photos.photoFile(ProfileKind.AGENT,id).readBytes())
            assertFalse(photos.hasDraft(key))
        } finally {
            photos.discardDraft(key)
            photos.photoDirectoryForTest().deleteRecursively()
            db.close()
        }
    }

    @Test fun interruptedProfileUpdateRestoresExistingPhotoAndOriginalRecord() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Application
        val photos = ProfilePhotoStore(context)
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        val id = db.agentDao().upsert(AgentEntity(
            name="Before",rate=80L,createdAt=1L,updatedAt=1L))
        val oldPhoto = photos.photoFile(ProfileKind.AGENT,id)
        oldPhoto.parentFile!!.mkdirs()
        oldPhoto.writeBytes(first)
        val key = photos.draftToken(ProfileKind.AGENT,id)
        photos.draftFile(key).parentFile!!.mkdirs()
        photos.draftFile(key).writeBytes(second)
        try {
            val abort = runCatching {
                    saveProfileAtomically(db,photos,ProfileKind.AGENT,
                        ProfilePhotoChange.ReplaceDraft(key),
                        saveRecord={
                            db.agentDao().upsert(db.agentDao().get(id)!!.copy(name="After"))
                            id
                        },
                        afterPhotoApplied={ error("Force rollback after replacing photo") },
                    )
            }.exceptionOrNull()
            assertTrue(abort is IllegalStateException)
            assertEquals("Before",db.agentDao().get(id)!!.name)
            assertArrayEquals(first,oldPhoto.readBytes())
            assertTrue(photos.hasDraft(key))
        } finally {
            photos.discardDraft(key)
            oldPhoto.delete()
            db.close()
        }
    }

    @Test fun failedPhotoSaveNeverLeavesAnOrphanCustomer() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Application
        val photos = ProfilePhotoStore(context)
        val db = Room.inMemoryDatabaseBuilder(context,LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        val agentId = db.agentDao().upsert(AgentEntity(
            name="Parent",rate=80L,createdAt=1L,updatedAt=1L))
        val key = photos.draftToken(ProfileKind.CUSTOMER,0L,agentId)
        photos.draftFile(key).parentFile!!.mkdirs()
        photos.draftFile(key).writeBytes(first)
        try {
            val abort = runCatching {
                    saveProfileAtomically(db,photos,ProfileKind.CUSTOMER,
                        ProfilePhotoChange.ReplaceDraft(key),
                        saveRecord={ db.customerDao().upsert(CustomerEntity(
                            agentId=agentId,name="New customer",commissionRateBasisPoints=1500,
                            createdAt=1L,updatedAt=1L)) },
                        afterPhotoApplied={ error("Simulated interruption") },
                    )
            }.exceptionOrNull()
            assertTrue(abort is IllegalStateException)
            assertTrue(db.customerDao().getForAgent(agentId).isEmpty())
            assertTrue(photos.hasDraft(key))
        } finally {
            photos.discardDraft(key)
            photos.photoDirectoryForTest().deleteRecursively()
            db.close()
        }
    }

    private fun ProfilePhotoStore.photoDirectoryForTest(): File =
        ProfilePhotoStore.photoDirectory(RuntimeEnvironment.getApplication())
}
