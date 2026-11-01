package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.AgentDigitGroup
import com.myanmar.ledger2d.core.domain.AgentDigitLine
import com.myanmar.ledger2d.core.domain.AgentDigitTotalsAggregator
import com.myanmar.ledger2d.core.model.DrawSession
import com.myanmar.ledger2d.core.repository.RoomBetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class RoomAgentDigitTotalsTest {
    private val date = LocalDate.of(2026, 10, 30)

    @Test
    fun storedEntriesAggregateExactlyWithoutDuplicatingNetCommissionAcrossDigitsOrAgents() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LedgerDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val a = db.agentDao().upsert(AgentEntity(name = "A", rate = 80, createdAt = 1, updatedAt = 1))
            val b = db.agentDao().upsert(AgentEntity(name = "B", rate = 85, createdAt = 1, updatedAt = 1))
            val self = db.agentDao().upsert(AgentEntity(name = "Self", rate = 90, createdAt = 1, updatedAt = 1))
            val aCustomer = db.customerDao().upsert(CustomerEntity(agentId = a, name = "Customer A", createdAt = 1, updatedAt = 1))
            val bCustomer = db.customerDao().upsert(CustomerEntity(agentId = b, name = "Customer A", createdAt = 1, updatedAt = 1))
            val selfCustomer = db.customerDao().upsert(CustomerEntity(agentId = self, name = "Customer A", createdAt = 1, updatedAt = 1))
            val dao = db.betDao()

            suspend fun entry(
                agent: Long, customer: Long, commission: Long,
                lines: List<Pair<String, Long>>,
                session: DrawSession = DrawSession.MORNING,
                drawDate: LocalDate = date,
            ): Long {
                val id = dao.insertEntry(BetEntryEntity(
                    agentId = agent, customerId = customer,
                    drawDate = drawDate, drawSession = session,
                    sourceText = "test", commissionAmount = commission,
                    payoutRate = 80, createdAt = 1, updatedAt = 1,
                ))
                dao.insertLines(lines.map { (digit, amount) ->
                    BetLineEntity(betEntryId = id, digit = digit, amount = amount)
                })
                return id
            }

            entry(a, aCustomer, 150, listOf("20" to 1000L, "39" to 500L))
            val editedId = entry(a, aCustomer, 101, listOf("20" to 1000L, "45" to 1000L))
            entry(b, bCustomer, 400, listOf("20" to 3000L, "00" to 1000L))
            entry(self, selfCustomer, 0, listOf("20" to 5000L, "30" to 5000L))
            entry(b, bCustomer, 999, listOf("20" to 9999L), DrawSession.EVENING)
            entry(b, bCustomer, 999, listOf("20" to 9999L), drawDate = date.minusDays(1))

            val repository = RoomBetRepository(db)
            suspend fun group(agent: Long, name: String) = AgentDigitGroup(
                agent, name,
                repository.observeAgentTotalsWithCommission(agent, date, DrawSession.MORNING)
                    .first().map { AgentDigitLine(it.digit, it.amount, it.commission) },
            )
            val aGroup = group(a, "A")
            val bGroup = group(b, "B")
            val selfGroup = group(self, "Self")
            assertEquals(3500L, aGroup.totalStake)
            assertEquals(251L, aGroup.totalNetCommission)
            assertEquals(4000L, bGroup.totalStake)
            assertEquals(400L, bGroup.totalNetCommission)
            assertEquals(10000L, selfGroup.totalStake)
            assertEquals(0L, selfGroup.totalNetCommission)
            val all = AgentDigitTotalsAggregator.combine(listOf(aGroup, bGroup, selfGroup))
            assertEquals(listOf("00", "20", "30", "39", "45"), all.map { it.digit })
            assertEquals(10000L, all.single { it.digit == "20" }.stake)
            assertEquals(450L, all.single { it.digit == "20" }.netCommission)
            assertEquals(17500L, all.sumOf { it.stake })
            assertEquals(651L, all.sumOf { it.netCommission })
            assertEquals(17500L, listOf(aGroup, bGroup, selfGroup).sumOf { it.totalStake })
            assertEquals(651L, listOf(aGroup, bGroup, selfGroup).sumOf { it.totalNetCommission })

            // Historical commission recalculation remains correct after editing an entry.
            val record = dao.getEntry(editedId)!!.entry
            dao.updateEntry(record.copy(commissionAmount = 200L, updatedAt = 2L))
            assertEquals(350L, group(a, "A").totalNetCommission)
            // Removing an entry must remove both its stake and its commission.
            dao.deleteEntry(dao.getEntry(editedId)!!.entry)
            assertEquals(1500L, group(a, "A").totalStake)
            assertEquals(150L, group(a, "A").totalNetCommission)
            assertEquals(15500L, AgentDigitTotalsAggregator.combine(listOf(
                group(a, "A"), group(b, "B"), group(self, "Self")
            )).sumOf { it.stake })
        } finally {
            db.close()
        }
    }
}
