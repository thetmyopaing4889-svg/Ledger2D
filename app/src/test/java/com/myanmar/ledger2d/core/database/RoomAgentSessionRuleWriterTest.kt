package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.repository.AgentSessionRuleWriter
import com.myanmar.ledger2d.core.repository.AuditRepository
import com.myanmar.ledger2d.core.repository.RoomAuditRepository
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], manifest=Config.NONE, application=Application::class)
class RoomAgentSessionRuleWriterTest {
    private val date = LocalDate.of(2026, 11, 2)

    @Test fun actualGroupedCloseAndReopenNeverLeaksBetweenAgentsOrDraws() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val a = db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val b = db.agentDao().upsert(AgentEntity(name="B",rate=85,createdAt=1,updatedAt=1))
            val writer = AgentSessionRuleWriter(db, RoomAuditRepository(db.auditDao()))
            assertEquals(3, writer.closed(a,date,DrawSession.MORNING,
                listOf("05","16","27"),true))
            assertEquals(0, writer.closed(a,date,DrawSession.MORNING,
                listOf("05","16","27"),true))
            assertEquals(1, writer.closed(a,date,DrawSession.MORNING,
                listOf("16","27","38"),true))
            assertEquals(setOf("05","16","27","38"),
                db.closedNumberDao().getDigits(a,date,DrawSession.MORNING).toSet())
            assertTrue(db.closedNumberDao().getDigits(a,date,DrawSession.EVENING).isEmpty())
            assertTrue(db.closedNumberDao().getDigits(b,date,DrawSession.MORNING).isEmpty())
            assertTrue(db.closedNumberDao().getDigits(a,date.plusDays(1),DrawSession.MORNING).isEmpty())

            assertEquals(2, writer.closed(a,date,DrawSession.MORNING,
                listOf("05","16","05"),false))
            assertEquals(setOf("27","38"),
                db.closedNumberDao().getDigits(a,date,DrawSession.MORNING).toSet())
            assertEquals(0, writer.closed(a,date,DrawSession.MORNING,listOf("05"),false))
        } finally { db.close() }
    }

    @Test fun actualGroupedLimitsPreserveGlobalAndOtherSessionRules() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val a = db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val b = db.agentDao().upsert(AgentEntity(name="B",rate=90,createdAt=1,updatedAt=1))
            val writer = AgentSessionRuleWriter(db, RoomAuditRepository(db.auditDao()))
            val limits = db.agentLimitDao()
            limits.upsert(AgentAllLimitEntity(agentId=a,drawDate=date,
                drawSession=DrawSession.MORNING,amount=6000,createdAt=1,updatedAt=1))
            assertEquals(3, writer.specialLimits(a,date,DrawSession.MORNING,
                listOf("00","11","22"),2000L,false))
            assertEquals(0, writer.specialLimits(a,date,DrawSession.MORNING,
                listOf("00","11","22"),2000L,false))
            val originalId = limits.getSpecial(a,date,DrawSession.MORNING,"00")!!.id
            assertEquals(2, writer.specialLimits(a,date,DrawSession.MORNING,
                listOf("00","11"),3000L,false))
            assertEquals(originalId, limits.getSpecial(a,date,DrawSession.MORNING,"00")!!.id)
            assertEquals(3000L,limits.getSpecial(a,date,DrawSession.MORNING,"11")!!.amount)
            assertEquals(1, writer.specialLimits(a,date,DrawSession.MORNING,
                listOf("00"),null,true))
            assertNull(limits.getSpecial(a,date,DrawSession.MORNING,"00"))
            assertEquals(6000L, limits.getAllLimit(a,date,DrawSession.MORNING)!!.amount)
            assertTrue(limits.getSpecialLimits(a,date,DrawSession.EVENING).isEmpty())
            assertTrue(limits.getSpecialLimits(b,date,DrawSession.MORNING).isEmpty())
            assertTrue(limits.getSpecialLimits(a,date.plusDays(1),DrawSession.MORNING).isEmpty())
        } finally { db.close() }
    }

    @Test fun invalidBatchIsRejectedBeforeAnyPartialWrites() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val a=db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val writer=AgentSessionRuleWriter(db,RoomAuditRepository(db.auditDao()))
            assertTrue(runCatching {
                writer.closed(a,date,DrawSession.MORNING,listOf("20","999"),true)
            }.isFailure)
            assertTrue(runCatching {
                writer.specialLimits(a,date,DrawSession.MORNING,
                    listOf("20","39"),0L,false)
            }.isFailure)
            assertTrue(db.closedNumberDao().getDigits(a,date,DrawSession.MORNING).isEmpty())
            assertTrue(db.agentLimitDao().getSpecialLimits(a,date,DrawSession.MORNING).isEmpty())
        } finally { db.close() }
    }

    @Test fun auditWriteFailureRollsBackTheEntireRealMutation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val a = db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            var calls=0
            val failingAudit = object: AuditRepository {
                override fun observeAll(): Flow<List<AuditEventEntity>> = flowOf(emptyList())
                override suspend fun record(
                    type: String, id: Long, action: String, detail: String,
                ): Long {
                    calls++
                    if (calls==2) error("Injected audit failure")
                    return 1L
                }
            }
            val writer=AgentSessionRuleWriter(db,failingAudit)
            assertTrue(runCatching {
                writer.closed(a,date,DrawSession.MORNING,listOf("05","16"),true)
            }.isFailure)
            assertTrue(db.closedNumberDao().getDigits(a,date,DrawSession.MORNING).isEmpty())
            calls=0
            assertTrue(runCatching {
                writer.specialLimits(a,date,DrawSession.MORNING,
                    listOf("05","16"),2000L,false)
            }.isFailure)
            assertTrue(db.agentLimitDao().getSpecialLimits(a,date,DrawSession.MORNING).isEmpty())
        } finally { db.close() }
    }
}
