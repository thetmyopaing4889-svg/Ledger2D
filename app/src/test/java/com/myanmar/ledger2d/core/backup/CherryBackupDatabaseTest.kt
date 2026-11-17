package com.myanmar.ledger2d.core.backup

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.database.AgentEntity
import com.myanmar.ledger2d.core.database.CustomerEntity
import com.myanmar.ledger2d.core.database.LedgerDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real Room schema + actual foreign keys; verifies no user rows are dropped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class CherryBackupDatabaseTest {
    @Test fun fullUserTableRestoreReplacesNewerRowsButKeepsOriginalForeignKeys()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val agent=db.agentDao().upsert(AgentEntity(
                name="Before backup",rate=80,createdAt=1,updatedAt=1))
            val customer=db.customerDao().upsert(CustomerEntity(
                agentId=agent,name="Before backup",createdAt=1,updatedAt=1))
            val sqlite=db.openHelper.writableDatabase
            val original=ByteArrayOutputStream()
            val written=CherryBackupDatabase.write(sqlite,original)
            assertTrue(written>=2L)
            assertEquals(written,CherryBackupDatabase.inspect(sqlite,
                ByteArrayInputStream(original.toByteArray())))
            db.agentDao().upsert(AgentEntity(
                name="After backup",rate=80,createdAt=2,updatedAt=2))
            sqlite.beginTransaction()
            try {
                CherryBackupDatabase.importInsideTransaction(
                    sqlite,ByteArrayInputStream(original.toByteArray()))
                sqlite.setTransactionSuccessful()
            } finally { sqlite.endTransaction() }
            assertEquals(1L,sqlite.query("SELECT COUNT(*) FROM agents").use {
                it.moveToFirst();it.getLong(0)
            })
            assertEquals(1L,sqlite.query("SELECT COUNT(*) FROM customers").use {
                it.moveToFirst();it.getLong(0)
            })
            assertEquals(customer,sqlite.query("SELECT id FROM customers").use {
                it.moveToFirst();it.getLong(0)
            })
            assertEquals(agent,sqlite.query("SELECT agentId FROM customers").use {
                it.moveToFirst();it.getLong(0)
            })
            val damaged=original.toString("UTF-8").replace(
                "\"database_version\":12","\"database_version\":99").toByteArray()
            assertThrows(Exception::class.java) {
                CherryBackupDatabase.inspect(sqlite,ByteArrayInputStream(damaged))
            }
        } finally { db.close() }
    }
}
