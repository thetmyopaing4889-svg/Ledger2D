package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.AgentRatedStake
import com.myanmar.ledger2d.core.domain.AgentSavedCommission
import com.myanmar.ledger2d.core.domain.AgentStatementCalculator
import com.myanmar.ledger2d.core.model.DrawSession
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
class RoomAgentStatementTest {
    @Test fun agentStatementQueriesSeparateCommissionFromDigitsAndKeepSelfIndependent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val a = db.agentDao().upsert(AgentEntity(name="A", rate=80, createdAt=1, updatedAt=1))
            val self = db.agentDao().upsert(AgentEntity(name="Self", rate=85, createdAt=1, updatedAt=1))
            val b = db.agentDao().upsert(AgentEntity(name="B", rate=90, createdAt=1, updatedAt=1))
            val aCust = db.customerDao().upsert(CustomerEntity(agentId=a, name="Customer A", createdAt=1, updatedAt=1))
            val selfCust = db.customerDao().upsert(CustomerEntity(agentId=self, name="Customer A", createdAt=1, updatedAt=1))
            val bCust = db.customerDao().upsert(CustomerEntity(agentId=b, name="Customer A", createdAt=1, updatedAt=1))
            val date = LocalDate.of(2026,10,30)

            suspend fun entry(agent:Long, customer:Long, rate:Long, commission:Long,
                first:String, firstAmount:Long, second:String, secondAmount:Long) {
                val entryId = db.betDao().insertEntry(BetEntryEntity(
                    agentId=agent,customerId=customer,drawDate=date,drawSession=DrawSession.MORNING,
                    sourceText="test",payoutRate=rate,commissionAmount=commission,
                    createdAt=1,updatedAt=1,
                ))
                db.betDao().insertLines(listOf(
                    BetLineEntity(betEntryId=entryId,digit=first,amount=firstAmount),
                    BetLineEntity(betEntryId=entryId,digit=second,amount=secondAmount),
                ))
            }
            entry(a,aCust,80,15000,"20",2000,"30",98000)
            entry(self,selfCust,85,400,"20",1000,"31",39000)
            entry(b,bCust,90,600,"30",1000,"32",59000)
            val dao = db.betDao()
            val agentRows = dao.agentStatementRatedRows(a,date,date)
            val agentCommissions = dao.agentStatementCommissionRows(a,date,date)
            assertEquals(2,agentRows.size)
            assertEquals(100000L,agentRows.sumOf{it.amount})
            assertEquals(2L,agentRows.sumOf{it.slotCount})
            assertEquals(1,agentCommissions.size)
            assertEquals(15000L,agentCommissions.single().commission)
            assertEquals(setOf(a),agentRows.map{it.agentId}.toSet())
            assertEquals(setOf(aCust),agentRows.map{it.customerId}.toSet())

            val allStakes = dao.agentStatementRatedRows(-1L,date,date)
            val allCommissions = dao.agentStatementCommissionRows(-1L,date,date)
            val summary = AgentStatementCalculator.build(
                allStakes.map {
                    AgentRatedStake(it.agentId,it.customerId,it.drawDate,it.drawSession,
                        it.digit,it.amount,it.payoutRate,it.slotCount)
                },
                allCommissions.map {
                    AgentSavedCommission(it.agentId,it.customerId,it.drawDate,it.drawSession,it.commission)
                },
                mapOf((date to DrawSession.MORNING) to "20"),
            )
            assertEquals(3,summary.draws.size)
            assertEquals(6L,summary.totals.digitSlots)
            assertEquals(200000L,summary.totals.totalStake)
            assertEquals(16000L,summary.totals.savedNetCommission)
            assertEquals(3000L,summary.totals.winningStake)
            assertEquals(245000L,summary.totals.payout)
            assertEquals(0L,summary.draws.single{it.agentId==b}.payout)
        } finally {
            db.close()
        }
    }
}
