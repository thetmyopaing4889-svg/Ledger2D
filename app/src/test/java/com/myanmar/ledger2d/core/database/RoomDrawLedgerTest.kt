package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.domain.DrawRatePolicy
import com.myanmar.ledger2d.core.domain.DrawSchedule
import java.time.LocalDateTime
import com.myanmar.ledger2d.core.model.DrawSession
import com.myanmar.ledger2d.core.model.ExpandedBet
import com.myanmar.ledger2d.core.repository.RoomAgentLimitRepository
import com.myanmar.ledger2d.core.repository.RoomAgentRepository
import com.myanmar.ledger2d.core.repository.RoomBetRepository
import com.myanmar.ledger2d.core.repository.RoomClosedNumberRepository
import com.myanmar.ledger2d.core.repository.RoomWinningNumberRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Test the actual Room write/query path, not only the money calculator.
 * Same-draw post-result correction must recalculate on the next query;
 * the old draw and next session must remain independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class RoomDrawLedgerTest {
    private val date = LocalDate.of(2026, 10, 26)

    private suspend fun freshDb():Triple<LedgerDatabase,Long,Long> {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LedgerDatabase::class.java)
            .allowMainThreadQueries().build()
        val a=db.agentDao().upsert(AgentEntity(name="Test Agent",rate=80,createdAt=1,updatedAt=1))
        val c=db.customerDao().upsert(CustomerEntity(agentId=a,name="Test Customer",
            commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
        return Triple(db,a,c)
    }

    @Test fun limitsAndClosedDigitsResetAtNextSessionButMorningStillExists()=runBlocking {
        val (db,agent,customer)=freshDb()
        try {
            val closed=RoomClosedNumberRepository(db.closedNumberDao())
            val agentLimit=RoomAgentLimitRepository(db.agentLimitDao())
            closed.add(agent,date,DrawSession.MORNING,"20")
            agentLimit.setSpecial(agent,date,DrawSession.MORNING,"10",2_000)
            assertEquals(setOf("20"),closed.getDigits(agent,date,DrawSession.MORNING))
            assertEquals(2_000L,agentLimit.get(agent,date,DrawSession.MORNING).specialLimits["10"])
            assertEquals(emptySet<String>(),closed.getDigits(agent,date,DrawSession.EVENING))
            assertTrue(agentLimit.get(agent,date,DrawSession.EVENING).specialLimits.isEmpty())
            assertEquals(setOf("20"),closed.getDigits(agent,date,DrawSession.MORNING))
        } finally {db.close()}
    }

    @Test fun winningDrawEditAndDeleteRecalculateAndInvalidateSettledSnapshot()=runBlocking {
        val (db,agent,customer)=freshDb()
        try {
            val bets=RoomBetRepository(db)
            val winners=RoomWinningNumberRepository(db)
            val betId=bets.confirm(customer,agent,date,DrawSession.MORNING,"20 1000",
                QuickFormat.MANUAL,listOf(ExpandedBet("20",1_000)))
            winners.save(date,DrawSession.MORNING,"20")
            assertEquals(80_000L,bets.getCustomerPayout(customer,date,DrawSession.MORNING,"20"))
            assertEquals(150L,bets.getCustomerCommission(customer,date,DrawSession.MORNING))
            db.settlementDao().upsert(SettlementEntity(agentId=agent,date=date,
                session=DrawSession.MORNING,totalBet=1_000,payout=80_000,commission=150,
                netSettlement=-78_850,settledAt=100))
            val initial=bets.getEntry(betId)!!
            bets.edit(initial.entry.copy(sourceText="20 2000"),
                listOf(ExpandedBet("20",2_000)),QuickFormat.MANUAL)
            val revised=bets.getEntry(betId)!!
            assertEquals(2_000L,bets.getCustomerTotals(customer,date,DrawSession.MORNING)["20"])
            assertEquals(160_000L,bets.getCustomerPayout(customer,date,DrawSession.MORNING,"20"))
            assertEquals(300L,bets.getCustomerCommission(customer,date,DrawSession.MORNING))
            assertEquals(80L,revised.entry.payoutRate)
            assertEquals(1500,revised.entry.commissionRateBasisPoints)
            assertNull(db.settlementDao().get(agent,date,DrawSession.MORNING))
            assertTrue(bets.getAgentTotals(agent,date,DrawSession.EVENING).isEmpty())

            // A later session-specific closure does not erase a previously accepted stake.
            RoomClosedNumberRepository(db.closedNumberDao()).add(agent,date,DrawSession.MORNING,"20")
            RoomAgentLimitRepository(db.agentLimitDao()).setSpecial(agent,date,DrawSession.MORNING,"20",1_000)
            bets.edit(revised.entry.copy(sourceText="20 1500"),
                listOf(ExpandedBet("20",1_500)),QuickFormat.MANUAL)
            assertEquals(120_000L,bets.getCustomerPayout(customer,date,DrawSession.MORNING,"20"))
            val latest=bets.getEntry(betId)!!
            val wrong=runCatching { bets.edit(latest.entry.copy(sourceText="20 2500"),
                listOf(ExpandedBet("20",2_500)),QuickFormat.MANUAL) }
            assertTrue("New/increased closed stake must be rejected",wrong.isFailure)
            assertEquals(1_500L,bets.getCustomerTotals(customer,date,DrawSession.MORNING)["20"])

            bets.delete(latest.entry)
            assertTrue(bets.getCustomerTotals(customer,date,DrawSession.MORNING).isEmpty())
            assertEquals(0L,bets.getCustomerPayout(customer,date,DrawSession.MORNING,"20"))
            assertEquals(0L,bets.getCustomerCommission(customer,date,DrawSession.MORNING))
            assertEquals("20",winners.get(date,DrawSession.MORNING)?.digit)
        } finally {db.close()}
    }

    @Test fun plannedRateChangeStartsOnFutureSessionAndDoesNotRepriceEarlierDraws()=runBlocking {
        val (db,agent,customer)=freshDb()
        try {
            val agents=RoomAgentRepository(db)
            // Schedule against a future weekday, independent of the CI run date.
            val changeDate=DrawRatePolicy.earliestChange(LocalDateTime.now()).date.plusWeeks(1)
            var beforeChange=changeDate.minusDays(1)
            while(!DrawSchedule.isWeekday(beforeChange))beforeChange=beforeChange.minusDays(1)
            agents.saveWithRateSchedule(db.agentDao().get(agent)!!.copy(rate=85),
                changeDate,DrawSession.MORNING)
            assertEquals(80L,agents.rateForDraw(agent,beforeChange,DrawSession.MORNING))
            assertEquals(80L,agents.rateForDraw(agent,beforeChange,DrawSession.EVENING))
            assertEquals(85L,agents.rateForDraw(agent,changeDate,DrawSession.MORNING))
            assertEquals(85L,agents.rateForDraw(agent,changeDate,DrawSession.EVENING))
            val bets=RoomBetRepository(db)
            val morning=bets.confirm(customer,agent,beforeChange,DrawSession.MORNING,"20 1000",
                QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            val following=bets.confirm(customer,agent,changeDate,DrawSession.MORNING,"20 1000",
                QuickFormat.MANUAL,listOf(ExpandedBet("20",1000)))
            assertEquals(80L,bets.getEntry(morning)!!.entry.payoutRate)
            assertEquals(85L,bets.getEntry(following)!!.entry.payoutRate)
        } finally {db.close()}
    }

    @Test fun completedDrawKeepsHistoricalRateAndCommissionAfterMasterChanges()=runBlocking {
        val (db,agent,customer)=freshDb()
        try {
            val bets=RoomBetRepository(db)
            val id=bets.confirm(customer,agent,date,DrawSession.MORNING,"10 1000",
                QuickFormat.MANUAL,listOf(ExpandedBet("10",1_000)))
            db.agentDao().upsert(db.agentDao().get(agent)!!.copy(rate=90,updatedAt=20))
            db.customerDao().upsert(db.customerDao().get(customer)!!.copy(
                commissionRateBasisPoints=1600,updatedAt=20))
            val previous=bets.getEntry(id)!!
            bets.edit(previous.entry.copy(sourceText="10 1500"),
                listOf(ExpandedBet("10",1_500)),QuickFormat.MANUAL)
            val edited=bets.getEntry(id)!!
            assertEquals(80L,edited.entry.payoutRate)
            assertEquals(1500,edited.entry.commissionRateBasisPoints)
            assertEquals(120_000L,bets.getCustomerPayout(customer,date,DrawSession.MORNING,"10"))
            assertEquals(225L,bets.getCustomerCommission(customer,date,DrawSession.MORNING))
        } finally {db.close()}
    }
}
