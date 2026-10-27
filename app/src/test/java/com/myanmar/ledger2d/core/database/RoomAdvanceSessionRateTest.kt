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
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class RoomAdvanceSessionRateTest {
    private fun active()=DrawRatePolicy.currentSession(LocalDateTime.now())
    private fun preceding(date:LocalDate):LocalDate {
        var old=date.minusDays(1)
        while(!DrawSchedule.isWeekday(old)) old=old.minusDays(1)
        return old
    }
    @Test fun advanceBetsRespectNewForwardRateButKeepLaterScheduledChanges()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val agent=db.agentDao().upsert(AgentEntity(name="Agent A",rate=80,createdAt=1,updatedAt=1))
            val customer=db.customerDao().upsert(CustomerEntity(agentId=agent,name="Customer A",
                commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            val bets=RoomBetRepository(db)
            val agents=RoomAgentRepository(db)
            val commissions=RoomCommissionRepository(db)
            val old=preceding(active().date)
            val start=active().date.plusWeeks(1)
            val later=start.plusWeeks(1)
            val oldId=bets.confirm(customer,agent,old,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val startId=bets.confirm(customer,agent,start,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val startEvening=bets.confirm(customer,agent,start,DrawSession.EVENING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val laterId=bets.confirm(customer,agent,later,DrawSession.MORNING,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            assertEquals(80L,bets.getEntry(laterId)!!.entry.payoutRate)

            // Later already-scheduled boundary wins after its effective date.
            agents.saveWithRateSchedule(db.agentDao().get(agent)!!.copy(rate=100),
                later,DrawSession.MORNING)
            commissions.schedule(customer,2500,later,DrawSession.MORNING)
            agents.saveWithRateSchedule(db.agentDao().get(agent)!!.copy(rate=90),
                start,DrawSession.MORNING)
            commissions.schedule(customer,2000,start,DrawSession.MORNING)

            assertEquals(80L,bets.getEntry(oldId)!!.entry.payoutRate)
            assertEquals(150L,bets.getEntry(oldId)!!.entry.commissionAmount)
            for(id in listOf(startId,startEvening)) {
                assertEquals(90L,bets.getEntry(id)!!.entry.payoutRate)
                assertEquals(200L,bets.getEntry(id)!!.entry.commissionAmount)
            }
            assertEquals(100L,bets.getEntry(laterId)!!.entry.payoutRate)
            assertEquals(250L,bets.getEntry(laterId)!!.entry.commissionAmount)
            assertEquals(90_000L,bets.getCustomerPayout(customer,start,DrawSession.MORNING,"20"))

            // A backdated entry must never bypass the selected session rate,
            // even if a legacy caller supplies explicit obsolete overrides.
            val backdated=bets.confirm(customer,agent,start,DrawSession.MORNING,
                "21 1000",QuickFormat.MANUAL,listOf(ExpandedBet("21",1000)),
                payoutRateOverride=75,commissionOverride=1200)
            assertEquals(90L,bets.getEntry(backdated)!!.entry.payoutRate)
            assertEquals(200L,bets.getEntry(backdated)!!.entry.commissionAmount)
        } finally { db.close() }
    }

    @Test fun singleDrawChangeDoesNotTouchAdvanceEntriesOrOtherCustomers()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val agent=db.agentDao().upsert(AgentEntity(name="Agent A",rate=80,createdAt=1,updatedAt=1))
            val customer=db.customerDao().upsert(CustomerEntity(agentId=agent,name="Customer A",
                commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            val other=db.customerDao().upsert(CustomerEntity(agentId=agent,name="Customer B",
                commissionRateBasisPoints=1300,createdAt=1,updatedAt=1))
            val bets=RoomBetRepository(db)
            val agents=RoomAgentRepository(db)
            val commissions=RoomCommissionRepository(db)
            val draw=active()
            val next=DrawRatePolicy.following(draw.date,draw.session)
            val morning=bets.confirm(customer,agent,draw.date,draw.session,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val future=bets.confirm(customer,agent,next.date,next.session,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val otherBet=bets.confirm(other,agent,draw.date,draw.session,
                "20 1000",QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            agents.saveWithRateSchedule(db.agentDao().get(agent)!!.copy(rate=95),
                draw.date,draw.session,forceSessionReprice=true)
            commissions.overrideDraw(customer,draw.date,draw.session,2200)
            assertEquals(95L,bets.getEntry(morning)!!.entry.payoutRate)
            assertEquals(95L,bets.getEntry(otherBet)!!.entry.payoutRate)
            assertEquals(220L,bets.getEntry(morning)!!.entry.commissionAmount)
            assertEquals(130L,bets.getEntry(otherBet)!!.entry.commissionAmount)
            assertEquals(80L,bets.getEntry(future)!!.entry.payoutRate)
            assertEquals(150L,bets.getEntry(future)!!.entry.commissionAmount)
            assertEquals(80L,agents.rateForDraw(agent,next.date,next.session))
            assertEquals(1500,commissions.rateForDraw(customer,next.date,next.session))
        } finally { db.close() }
    }
}
