package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.myanmar.ledger2d.core.domain.SessionScopedLimitValidator
import com.myanmar.ledger2d.core.model.DrawSession
import com.myanmar.ledger2d.core.model.ExpandedBet
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
class RoomSessionPatternRulesTest {
    private val date = LocalDate.of(2026,11,2)

    @Test fun closedAndLimitRowsBelongToExactlyOneAgentDateAndSession() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val a=db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val b=db.agentDao().upsert(AgentEntity(name="B",rate=90,createdAt=1,updatedAt=1))
            val closed=db.closedNumberDao()
            val limits=db.agentLimitDao()
            suspend fun close(agent:Long,at:LocalDate,session:DrawSession,digit:String) {
                closed.upsert(ClosedNumberEntity(agentId=agent,drawDate=at,
                    drawSession=session,digit=digit,createdAt=1,updatedAt=1))
            }
            suspend fun limit(agent:Long,at:LocalDate,session:DrawSession,
                digit:String,amount:Long) {
                limits.upsert(AgentSpecialLimitEntity(agentId=agent,
                    drawDate=at,drawSession=session,digit=digit,amount=amount,
                    createdAt=1,updatedAt=1))
            }
            db.withTransaction {
                close(a,date,DrawSession.MORNING,"20")
                close(a,date,DrawSession.MORNING,"39")
                limit(a,date,DrawSession.MORNING,"20",2000L)
                limit(a,date,DrawSession.MORNING,"39",2000L)
            }
            close(a,date,DrawSession.EVENING,"99")
            close(b,date,DrawSession.MORNING,"88")
            close(a,date.plusDays(1),DrawSession.MORNING,"77")
            limit(a,date,DrawSession.EVENING,"20",9000L)
            limit(b,date,DrawSession.MORNING,"20",8000L)
            limit(a,date.plusDays(1),DrawSession.MORNING,"20",7000L)

            assertEquals(setOf("20","39"),
                closed.getDigits(a,date,DrawSession.MORNING).toSet())
            assertEquals(listOf("99"),closed.getDigits(a,date,DrawSession.EVENING))
            assertEquals(listOf("88"),closed.getDigits(b,date,DrawSession.MORNING))
            assertEquals(listOf("77"),closed.getDigits(a,date.plusDays(1),DrawSession.MORNING))
            assertEquals(2,limits.getSpecialLimits(a,date,DrawSession.MORNING).size)
            assertEquals(2000L,limits.getSpecial(a,date,DrawSession.MORNING,"20")!!.amount)
            assertEquals(9000L,limits.getSpecial(a,date,DrawSession.EVENING,"20")!!.amount)
            assertEquals(8000L,limits.getSpecial(b,date,DrawSession.MORNING,"20")!!.amount)
            assertEquals(7000L,limits.getSpecial(a,date.plusDays(1),DrawSession.MORNING,"20")!!.amount)

            // Existing session validator rejects full over-limit bets and closed digits.
            val amountLimits = com.myanmar.ledger2d.core.model.EffectiveLimits(
                null,mapOf("20" to 2000L))
            val noLimits=com.myanmar.ledger2d.core.model.EffectiveLimits(null,emptyMap())
            val rejected=SessionScopedLimitValidator().validate(
                listOf(ExpandedBet("20",500L)),mapOf("20" to 1800L),
                emptyMap(),amountLimits,noLimits,emptySet())
            assertFalse(rejected.canConfirm)
            val closedRule=SessionScopedLimitValidator().validate(
                listOf(ExpandedBet("39",100L)),emptyMap(),emptyMap(),
                noLimits,noLimits,setOf("39"))
            assertFalse(closedRule.canConfirm)
        } finally { db.close() }
    }

    @Test fun roomTransactionRollsBackAllPatternRowsOnFailure() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val a=db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val failed=runCatching {
                db.withTransaction {
                    db.closedNumberDao().upsert(ClosedNumberEntity(
                        agentId=a,drawDate=date,drawSession=DrawSession.MORNING,
                        digit="05",createdAt=1,updatedAt=1))
                    db.agentLimitDao().upsert(AgentSpecialLimitEntity(
                        agentId=a,drawDate=date,drawSession=DrawSession.MORNING,
                        digit="05",amount=2000,createdAt=1,updatedAt=1))
                    error("force rollback")
                }
            }
            assertTrue(failed.isFailure)
            assertTrue(db.closedNumberDao().getDigits(a,date,DrawSession.MORNING).isEmpty())
            assertTrue(db.agentLimitDao().getSpecialLimits(a,date,DrawSession.MORNING).isEmpty())
        } finally { db.close() }
    }
}
