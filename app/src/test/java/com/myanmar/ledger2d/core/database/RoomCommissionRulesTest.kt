package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.DrawRatePolicy
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
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class RoomCommissionRulesTest {
    @Test fun futureRateAndHistoricalDrawCorrectionStayIsolated()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val agent=db.agentDao().upsert(AgentEntity(name="Agent A",rate=80,createdAt=1,updatedAt=1))
            val customer=db.customerDao().upsert(CustomerEntity(agentId=agent,name="Customer A",
                commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            val other=db.customerDao().upsert(CustomerEntity(agentId=agent,name="Customer B",
                commissionRateBasisPoints=1200,createdAt=1,updatedAt=1))
            val commissions=RoomCommissionRepository(db)
            val bets=RoomBetRepository(db)
            val future=DrawSchedule.nextDraw(LocalDateTime.now()).date.plusWeeks(1)
            var old=future.minusDays(1)
            while(!DrawSchedule.isWeekday(old)) old=old.minusDays(1)
            val oldMorning=bets.confirm(customer,agent,old,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            bets.confirm(customer,agent,old,DrawSession.EVENING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            bets.confirm(other,agent,old,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            assertEquals(1500,commissions.rateForDraw(customer,old,DrawSession.MORNING))
            commissions.schedule(customer,2000,future,DrawSession.MORNING)
            assertEquals(1500,commissions.rateForDraw(customer,old,DrawSession.MORNING))
            assertEquals(1500,commissions.rateForDraw(customer,old,DrawSession.EVENING))
            assertEquals(2000,commissions.rateForDraw(customer,future,DrawSession.MORNING))
            assertEquals(150L,bets.getCustomerCommission(customer,old,DrawSession.MORNING))
            val futureEntry=bets.confirm(customer,agent,future,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            assertEquals(2000,bets.getEntry(futureEntry)!!.entry.commissionRateBasisPoints)
            assertEquals(200L,bets.getCustomerCommission(customer,future,DrawSession.MORNING))

            db.settlementDao().upsert(SettlementEntity(agentId=agent,date=old,
                session=DrawSession.MORNING,totalBet=2000,payout=160000,commission=270,
                netSettlement=-157730,settledAt=100))
            commissions.overrideDraw(customer,old,DrawSession.MORNING,2000)
            assertEquals(200L,bets.getCustomerCommission(customer,old,DrawSession.MORNING))
            assertEquals(150L,bets.getCustomerCommission(customer,old,DrawSession.EVENING))
            assertEquals(120L,bets.getCustomerCommission(other,old,DrawSession.MORNING))
            assertEquals(2000,commissions.rateForDraw(customer,old,DrawSession.MORNING))
            assertEquals(1500,commissions.rateForDraw(customer,old,DrawSession.EVENING))
            assertNull(db.settlementDao().get(agent,old,DrawSession.MORNING))
            // A later edit must preserve the corrected snapshot rather than
            // snapping back to the customer's future default.
            val corrected=bets.getEntry(oldMorning)!!
            bets.edit(corrected.entry.copy(sourceText="20 1500"),
                listOf(ExpandedBet("20",1500)),QuickFormat.MANUAL)
            assertEquals(300L,bets.getCustomerCommission(customer,old,DrawSession.MORNING))
            assertEquals(2000,bets.getEntry(oldMorning)!!.entry.commissionRateBasisPoints)
            val second=bets.confirm(customer,agent,old,DrawSession.MORNING,
                "21 1000",QuickFormat.MANUAL,listOf(ExpandedBet("21",1000)))
            assertEquals(2000,bets.getEntry(second)!!.entry.commissionRateBasisPoints)
            assertEquals(500L,bets.getCustomerCommission(customer,old,DrawSession.MORNING))
            // The agent payout multiplier must not be altered by commission corrections.
            assertEquals(80L,bets.getEntry(oldMorning)!!.entry.payoutRate)
        } finally {db.close()}
    }

    @Test fun rejectedFutureScheduleCannotModifyHistoricalOrCurrentCommission()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val agent=db.agentDao().upsert(AgentEntity(name="Agent A",rate=80,createdAt=1,updatedAt=1))
            val customer=db.customerDao().upsert(CustomerEntity(agentId=agent,name="Customer A",
                commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            val repo=RoomCommissionRepository(db)
            val past=DrawSchedule.nextDraw(LocalDateTime.now()).date.minusWeeks(1)
            assertTrue(runCatching {repo.schedule(customer,2000,past,DrawSession.MORNING)}.isFailure)
            assertEquals(1500,db.customerDao().get(customer)!!.commissionRateBasisPoints)
            assertEquals(null,db.customerCommissionRuleDao().forwardRate(customer,Long.MAX_VALUE))
        } finally {db.close()}
    }
}
