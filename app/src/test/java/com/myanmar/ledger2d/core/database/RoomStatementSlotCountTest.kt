package com.myanmar.ledger2d.core.database

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.domain.AgentRatedStake
import com.myanmar.ledger2d.core.domain.AgentStatementCalculator
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
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class RoomStatementSlotCountTest {
    @Test fun sixTransactionsMatchCustomerAgentReportsAndRecountAfterDelete() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), LedgerDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val agent = db.agentDao().upsert(
                AgentEntity(name = "Agent A", rate = 80, createdAt = 1, updatedAt = 1),
            )
            val kaing = db.customerDao().upsert(
                CustomerEntity(agentId = agent, name = "Kaing", createdAt = 1, updatedAt = 1),
            )
            val yu = db.customerDao().upsert(
                CustomerEntity(agentId = agent, name = "Yu", createdAt = 1, updatedAt = 1),
            )
            val date = LocalDate.of(2026, 11, 4)
            val dao = db.betDao()

            suspend fun addEntries(
                customer: Long, chunks: List<Int>, ordinary: Long, finalAmount: Long,
            ): Long {
                val total = chunks.sum()
                var position = 0
                var lastId = 0L
                for (chunk in chunks) {
                    val id = dao.insertEntry(BetEntryEntity(
                        agentId = agent, customerId = customer,
                        drawDate = date, drawSession = DrawSession.MORNING,
                        sourceText = "repeated lines", payoutRate = 80,
                        createdAt = 1, updatedAt = 1,
                    ))
                    lastId = id
                    dao.insertLines((0 until chunk).map { i ->
                        position++
                        BetLineEntity(
                            betEntryId = id,
                            digit = i.toString().padStart(2, '0'),
                            amount = if (position == total) finalAmount else ordinary,
                        )
                    })
                }
                return lastId
            }

            // Three original transactions per customer. Repeated digits across
            // transactions MUST count again although SQL groups money by digit and rate.
            val kaingLastId = addEntries(kaing, listOf(20, 20, 11), 2000, 6000)
            addEntries(yu, listOf(20, 20, 15), 3000, 18000)
            val winners = mapOf((date to DrawSession.MORNING) to "00")

            suspend fun checkReports(expectedSlots: Long, expectedStake: Long) {
                val customerRows = dao.statementRatedRows(agent, -1L, date, date)
                val agentRows = dao.agentStatementRatedRows(agent, date, date)
                assertEquals(expectedSlots, customerRows.sumOf { it.slotCount })
                assertEquals(expectedSlots, agentRows.sumOf { it.slotCount })
                assertEquals(expectedStake, customerRows.sumOf { it.amount })
                assertEquals(expectedStake, agentRows.sumOf { it.amount })
                val customerReport = CustomerStatementCalculator.build(
                    customerRows.map {
                        StatementStake(it.customerId, it.drawDate, it.drawSession,
                            it.digit, it.payoutRate, it.amount, it.slotCount)
                    }, winners,
                )
                val agentReport = AgentStatementCalculator.build(
                    agentRows.map {
                        AgentRatedStake(it.agentId, it.customerId, it.drawDate,
                            it.drawSession, it.digit, it.amount, it.payoutRate, it.slotCount)
                    }, emptyList(), winners,
                )
                assertEquals(expectedSlots, customerReport.totals.slots)
                assertEquals(expectedSlots, agentReport.totals.digitSlots)
                assertEquals(expectedStake, customerReport.totals.totalBet)
                assertEquals(expectedStake, agentReport.totals.totalStake)
                assertEquals(customerReport.totals.payout, agentReport.totals.payout)
            }
            checkReports(106L, 286000L)
            val originalRows = dao.statementRatedRows(agent, -1L, date, date)
            assertEquals(51L, originalRows.filter { it.customerId == kaing }.sumOf { it.slotCount })
            assertEquals(55L, originalRows.filter { it.customerId == yu }.sumOf { it.slotCount })
            // Deleting a stored transaction changes slots and money together.
            dao.deleteEntry(requireNotNull(dao.getEntry(kaingLastId)).entry)
            checkReports(95L, 260000L)
        } finally {
            db.close()
        }
    }
}
