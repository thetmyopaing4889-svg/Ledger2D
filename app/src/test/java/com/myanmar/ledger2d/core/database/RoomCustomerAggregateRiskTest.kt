package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.domain.CustomerRiskPlanner
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.model.DrawSession
import com.myanmar.ledger2d.core.model.ExpandedBet
import com.myanmar.ledger2d.core.repository.RoomAgentLimitRepository
import com.myanmar.ledger2d.core.repository.RoomBetRepository
import com.myanmar.ledger2d.core.repository.RoomLimitRepository
import com.myanmar.ledger2d.feature.main.LedgerViewModel
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Integration coverage: one agent's "all customers" must not leak another agent/draw. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], manifest=Config.NONE, application=Application::class)
class RoomCustomerAggregateRiskTest {
    @Test fun allCustomersUsesStoredMixedRatesButOnlySelectedAgentAndSession() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val day = LocalDate.of(2026, 10, 26)
            val agentA = db.agentDao().upsert(
                AgentEntity(name="Agent A", rate=80L, createdAt=1, updatedAt=1))
            val agentB = db.agentDao().upsert(
                AgentEntity(name="Agent B", rate=90L, createdAt=1, updatedAt=1))
            fun customer(agent:Long, name:String) = CustomerEntity(
                agentId=agent, name=name, commissionRateBasisPoints=1500,
                createdAt=1, updatedAt=1)
            val alice=db.customerDao().upsert(customer(agentA,"Alice"))
            val bob=db.customerDao().upsert(customer(agentA,"Bob"))
            val other=db.customerDao().upsert(customer(agentB,"Other"))
            val bets=RoomBetRepository(db)

            val first=bets.confirm(alice,agentA,day,DrawSession.MORNING,
                "33 1000",QuickFormat.MANUAL,listOf(ExpandedBet("33",1_000L)))
            val second=bets.confirm(bob,agentA,day,DrawSession.MORNING,
                "33 2000",QuickFormat.MANUAL,listOf(ExpandedBet("33",2_000L)))
            bets.confirm(other,agentB,day,DrawSession.MORNING,
                "33 10000",QuickFormat.MANUAL,listOf(ExpandedBet("33",10_000L)))
            bets.confirm(alice,agentA,day,DrawSession.EVENING,
                "33 5000",QuickFormat.MANUAL,listOf(ExpandedBet("33",5_000L)))

            // Simulate legitimate historical entry snapshots from different rates.
            // Never reprice these records as a side effect of reading Analysis.
            val originalSecond=bets.getEntry(second)!!.entry
            db.betDao().updateEntry(originalSecond.copy(payoutRate=85L))
            val vm=LedgerViewModel(AppContainer(db))
            val morning=vm.aggregateAnalysis(agentA,day,DrawSession.MORNING)
            val digit33=morning.scenarios.single { it.digit=="33" }
            assertEquals(3_000L,morning.totalBet)
            assertEquals(1,morning.distinctDigits)
            assertEquals(3_000L,digit33.stake)
            assertEquals(250_000L,digit33.payout)
            assertEquals(-247_000L,digit33.profitLoss)

            val hedge=CustomerRiskPlanner().calculate(morning,80L)
            assertEquals(247_000L,hedge.baseline.worstLoss)
            assertEquals(2_988L,CustomerRiskPlanner()
                .planForLossBudget(morning,80L,11_000L)!!.upstreamTotal)

            val otherAgent=vm.aggregateAnalysis(agentB,day,DrawSession.MORNING)
            assertEquals(10_000L,otherAgent.totalBet)
            assertEquals(900_000L,
                otherAgent.scenarios.single { it.digit=="33" }.payout)
            val evening=vm.aggregateAnalysis(agentA,day,DrawSession.EVENING)
            assertEquals(5_000L,evening.totalBet)
            assertEquals(400_000L,evening.scenarios.single { it.digit=="33" }.payout)
            assertEquals(80L,bets.getEntry(first)!!.entry.payoutRate)
            assertEquals(85L,bets.getEntry(second)!!.entry.payoutRate)

            // Same selected agent/draw, but each customer has an independent
            // effective limit; do not replace it with a synthetic combined limit.
            RoomAgentLimitRepository(db.agentLimitDao()).setSpecial(
                agentA,day,DrawSession.MORNING,"33",4_000L)
            RoomLimitRepository(db.limitDao()).setSpecial(
                alice,day,DrawSession.MORNING,"33",1_000L)
            val warnings=vm.customerLimitWarnings(agentA,day,DrawSession.MORNING)
            assertEquals(1,warnings.size)
            assertEquals(alice,warnings.single().customerId)
            assertEquals(listOf("33"),warnings.single().fullDigits)
            assertTrue(vm.customerLimitWarnings(agentA,day,DrawSession.EVENING).isEmpty())
        } finally { db.close() }
    }
}
