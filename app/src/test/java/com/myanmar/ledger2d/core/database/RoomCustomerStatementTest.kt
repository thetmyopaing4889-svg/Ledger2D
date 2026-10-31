package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.CustomerStatementCalculator
import com.myanmar.ledger2d.core.domain.StatementStake
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
class RoomCustomerStatementTest {
    @Test fun boundedStatementRowsRespectAgentCustomerDateAndSavedPayoutRates()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val a=db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val b=db.agentDao().upsert(AgentEntity(name="B",rate=90,createdAt=1,updatedAt=1))
            val c1=db.customerDao().upsert(CustomerEntity(agentId=a,name="X",createdAt=1,updatedAt=1))
            val c2=db.customerDao().upsert(CustomerEntity(agentId=a,name="Y",createdAt=1,updatedAt=1))
            val other=db.customerDao().upsert(CustomerEntity(agentId=b,name="Z",createdAt=1,updatedAt=1))
            val d=LocalDate.of(2026,10,30)
            val older=d.minusDays(1)
            suspend fun entry(customerId:Long,agentId:Long,date:LocalDate,rate:Long,amount:Long) {
                val id=db.betDao().insertEntry(BetEntryEntity(customerId=customerId,agentId=agentId,
                    drawDate=date,drawSession=DrawSession.MORNING,sourceText="20 $amount",
                    payoutRate=rate,createdAt=1,updatedAt=1))
                db.betDao().insertLines(listOf(BetLineEntity(betEntryId=id,digit="20",amount=amount)))
            }
            entry(c1,a,d,80,1000)
            entry(c1,a,d,80,500)
            entry(c2,a,d,85,2000)
            entry(other,b,d,90,3000)
            entry(c1,a,older,80,4000)
            val rows=db.betDao().statementRatedRows(a,-1L,d,d)
            assertEquals(2,rows.size)
            assertEquals(3500L,rows.sumOf{it.amount})
            assertEquals(setOf(c1,c2),rows.map{it.customerId}.toSet())
            assertEquals(1500L,rows.single{it.customerId==c1}.amount)
            assertEquals(80L,rows.single{it.customerId==c1}.payoutRate)
            assertEquals(85L,rows.single{it.customerId==c2}.payoutRate)
            assertEquals(1,db.betDao().statementRatedRows(a,c1,d,d).size)
            assertTrue(db.betDao().statementRatedRows(b,c1,d,d).isEmpty())
            val report=CustomerStatementCalculator.build(rows.map {
                StatementStake(it.customerId,it.drawDate,it.drawSession,it.digit,it.payoutRate,it.amount)
            },mapOf((d to DrawSession.MORNING) to "20"))
            assertEquals(290000L,report.totals.payout)
            assertEquals(3500L,report.totals.winningStake)
            assertEquals(286500L,report.totals.customerWinLoss)
        } finally { db.close() }
    }
}
