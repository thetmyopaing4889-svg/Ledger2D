package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.model.DrawSession
import com.myanmar.ledger2d.core.model.ExpandedBet
import com.myanmar.ledger2d.core.repository.RoomBetRepository
import com.myanmar.ledger2d.core.repository.RoomCommissionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], manifest=Config.NONE, application=Application::class)
class RoomBulkCommissionTest {
    @Test fun agentBulkForwardAndDrawCorrectionPreserveHistoryAndOtherAgents()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val a=db.agentDao().upsert(AgentEntity(name="Agent A",rate=80,createdAt=1,updatedAt=1))
            val b=db.agentDao().upsert(AgentEntity(name="Agent B",rate=90,createdAt=1,updatedAt=1))
            val x=db.customerDao().upsert(CustomerEntity(agentId=a,name="X",commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            val y=db.customerDao().upsert(CustomerEntity(agentId=a,name="Y",commissionRateBasisPoints=1200,createdAt=1,updatedAt=1))
            val z=db.customerDao().upsert(CustomerEntity(agentId=b,name="Z",commissionRateBasisPoints=1700,createdAt=1,updatedAt=1))
            val bets=RoomBetRepository(db)
            val rates=RoomCommissionRepository(db)
            val future=DrawSchedule.nextDraw(LocalDateTime.now()).date.plusWeeks(1)
            var past=java.time.LocalDate.now().minusWeeks(1)
            while(!DrawSchedule.isWeekday(past))past=past.minusDays(1)
            fun line()=listOf(ExpandedBet("20",1_000))
            val xBefore=bets.confirm(x,a,past,DrawSession.MORNING,"20 1000",QuickFormat.MANUAL,line())
            bets.confirm(y,a,past,DrawSession.MORNING,"20 1000",QuickFormat.MANUAL,line())
            bets.confirm(z,b,past,DrawSession.MORNING,"20 1000",QuickFormat.MANUAL,line())
            val xFuture=bets.confirm(x,a,future,DrawSession.MORNING,"20 1000",QuickFormat.MANUAL,line())
            val yFuture=bets.confirm(y,a,future,DrawSession.MORNING,"20 1000",QuickFormat.MANUAL,line())
            val zFuture=bets.confirm(z,b,future,DrawSession.MORNING,"20 1000",QuickFormat.MANUAL,line())
            assertEquals(2,rates.scheduleForAgent(a,1400,future,DrawSession.MORNING))
            assertEquals(150L,bets.getCustomerCommission(x,past,DrawSession.MORNING))
            assertEquals(120L,bets.getCustomerCommission(y,past,DrawSession.MORNING))
            assertEquals(140L,bets.getCustomerCommission(x,future,DrawSession.MORNING))
            assertEquals(140L,bets.getCustomerCommission(y,future,DrawSession.MORNING))
            assertEquals(170L,bets.getCustomerCommission(z,future,DrawSession.MORNING))
            assertEquals(1400,bets.getEntry(xFuture)!!.entry.commissionRateBasisPoints)
            assertEquals(1400,bets.getEntry(yFuture)!!.entry.commissionRateBasisPoints)
            assertEquals(1700,bets.getEntry(zFuture)!!.entry.commissionRateBasisPoints)
            assertEquals(2,rates.overrideDrawForAgent(a,past,DrawSession.MORNING,1800))
            assertEquals(180L,bets.getCustomerCommission(x,past,DrawSession.MORNING))
            assertEquals(180L,bets.getCustomerCommission(y,past,DrawSession.MORNING))
            assertEquals(170L,bets.getCustomerCommission(z,past,DrawSession.MORNING))
            assertEquals(140L,bets.getCustomerCommission(x,future,DrawSession.MORNING))
            assertEquals(140L,bets.getCustomerCommission(y,future,DrawSession.MORNING))
            assertEquals(1800,bets.getEntry(xBefore)!!.entry.commissionRateBasisPoints)
            assertEquals(80L,bets.getEntry(xBefore)!!.entry.payoutRate)
            assertEquals(90L,bets.getEntry(zFuture)!!.entry.payoutRate)
            assertTrue(runCatching { rates.scheduleForAgent(a,2500,past,DrawSession.MORNING) }.isFailure)
            assertEquals(1400,rates.rateForDraw(x,future,DrawSession.MORNING))
            assertEquals(1400,rates.rateForDraw(y,future,DrawSession.MORNING))
        } finally { db.close() }
    }
}
