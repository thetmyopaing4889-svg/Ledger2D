package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.DrawRatePolicy
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.model.DrawSession
import com.myanmar.ledger2d.core.model.ExpandedBet
import com.myanmar.ledger2d.core.repository.RoomAgentRepository
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
class RoomSessionRepricingTest {
    @Test fun agentChangeRepricesExistingSessionAndIsolatesHistoricalCorrection() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val agents=RoomAgentRepository(db)
            val bets=RoomBetRepository(db)
            val agentId=db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val customerId=db.customerDao().upsert(CustomerEntity(agentId=agentId,name="C",commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            val active=DrawRatePolicy.currentSession(LocalDateTime.now())
            var old=active.date.minusDays(1)
            while(!DrawSchedule.isWeekday(old))old=old.minusDays(1)
            val historical=bets.confirm(customerId,agentId,old,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val first=bets.confirm(customerId,agentId,active.date,active.session,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val second=bets.confirm(customerId,agentId,active.date,active.session,
                "21 2000",QuickFormat.MANUAL,listOf(ExpandedBet("21",2000)))
            // A saved winner must not block a correction to this session's rate.
            db.winningNumberDao().upsert(WinningNumberEntity(date=active.date,session=active.session,digit="20",createdAt=1,updatedAt=1))
            agents.saveWithRateSchedule(db.agentDao().get(agentId)!!.copy(rate=90),
                active.date,active.session)
            assertEquals(80L,bets.getEntry(historical)!!.entry.payoutRate)
            assertEquals(90L,bets.getEntry(first)!!.entry.payoutRate)
            assertEquals(90L,bets.getEntry(second)!!.entry.payoutRate)
            assertEquals(90000L,bets.getAgentPayout(agentId,active.date,active.session,"20"))
            val additional=bets.confirm(customerId,agentId,active.date,active.session,
                "22 1000",QuickFormat.MANUAL,listOf(ExpandedBet("22",1000)))
            assertEquals(90L,bets.getEntry(additional)!!.entry.payoutRate)

            agents.saveWithRateSchedule(db.agentDao().get(agentId)!!.copy(rate=75),
                old,DrawSession.MORNING)
            assertEquals(75L,bets.getEntry(historical)!!.entry.payoutRate)
            assertEquals(90L,bets.getEntry(first)!!.entry.payoutRate)
            assertEquals(90L,agents.rateForDraw(agentId,active.date,active.session))
            assertEquals(90L,db.agentDao().get(agentId)!!.rate)
            // A historical session can be corrected back to the master rate,
            // even though the master value itself has not changed.
            agents.saveWithRateSchedule(db.agentDao().get(agentId)!!.copy(rate=90),
                old,DrawSession.MORNING,forceSessionReprice=true)
            assertEquals(90L,bets.getEntry(historical)!!.entry.payoutRate)
            assertEquals(90L,bets.getEntry(first)!!.entry.payoutRate)
        } finally {db.close()}
    }

    @Test fun commissionChangeRepricesTheSelectedCustomersWholeSessionOnly()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val agentId=db.agentDao().upsert(AgentEntity(name="A",rate=80,createdAt=1,updatedAt=1))
            val customer=db.customerDao().upsert(CustomerEntity(agentId=agentId,name="C",
                commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            val other=db.customerDao().upsert(CustomerEntity(agentId=agentId,name="D",
                commissionRateBasisPoints=1200,createdAt=1,updatedAt=1))
            val active=DrawRatePolicy.currentSession(LocalDateTime.now())
            var old=active.date.minusDays(1)
            while(!DrawSchedule.isWeekday(old))old=old.minusDays(1)
            val bets=RoomBetRepository(db)
            val repo=RoomCommissionRepository(db)
            val prior=bets.confirm(customer,agentId,old,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val first=bets.confirm(customer,agentId,active.date,active.session,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val second=bets.confirm(customer,agentId,active.date,active.session,
                "21 1000",QuickFormat.MANUAL,listOf(ExpandedBet("21",1000)))
            val otherEntry=bets.confirm(other,agentId,active.date,active.session,
                "22 1000",QuickFormat.MANUAL,listOf(ExpandedBet("22",1000)))
            repo.schedule(customer,2000,active.date,active.session)
            assertEquals(150L,bets.getEntry(prior)!!.entry.commissionAmount)
            assertEquals(200L,bets.getEntry(first)!!.entry.commissionAmount)
            assertEquals(200L,bets.getEntry(second)!!.entry.commissionAmount)
            assertEquals(120L,bets.getEntry(otherEntry)!!.entry.commissionAmount)
            assertEquals(400L,bets.getCustomerCommission(customer,active.date,active.session))
            repo.overrideDraw(customer,old,DrawSession.MORNING,2500)
            assertEquals(250L,bets.getEntry(prior)!!.entry.commissionAmount)
            assertEquals(400L,bets.getCustomerCommission(customer,active.date,active.session))
        } finally {db.close()}
    }
}
