package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AgentLedgerFrameTest {
    private val day = LocalDate.of(2026, 11, 5)
    private val morning = DrawSession.MORNING
    private val evening = DrawSession.EVENING

    private fun report(): AgentStatement {
        val stakes = listOf(
            AgentRatedStake(1, 10, day, evening, "20", 1000, 80, slotCount = 5),
            AgentRatedStake(2, 11, day, evening, "30", 2000, 85, slotCount = 3),
        )
        val commissions = listOf(
            AgentSavedCommission(1, 10, day, evening, 100),
            AgentSavedCommission(2, 11, day, evening, 200),
        )
        return AgentStatementCalculator.build(stakes, commissions, mapOf((day to evening) to "20"))
    }

    @Test fun calendarIncludesMorningAndEveningAndNeverInfersHistory() {
        val page = CustomerReportPage(day, day)
        val saved = mapOf((day to evening) to "20")
        val slots = AgentLedgerFrame.rows(page, StatementPeriod.DAY, null,
            report().draws, saved, emptySet())
        assertEquals(listOf(morning, evening), slots.map { it.session })
        assertNull(slots[0].winner)
        assertTrue(slots[0].draws.isEmpty())
        assertEquals("20", slots[1].winner)
        assertEquals(2, slots[1].draws.size)
        assertEquals(8L, slots[1].totals.digitSlots)
    }

    @Test fun missingStoredWinnerRemainsPendingDespiteEnteredBets() {
        val page = CustomerReportPage(day, day)
        val slot = AgentLedgerFrame.rows(page, StatementPeriod.DAY, evening,
            report().draws.map { it.copy(winner = null) }, emptyMap(), emptySet()).single()
        assertNull(slot.winner)
        assertEquals(2, slot.totals.pendingDraws)
        assertNull(slot.totals.settlementAmount)
    }

    @Test fun oppositeAgentMoneyDirectionsRemainSeparateInAllAgentView() {
        val draws = report().draws
        // Agent 1: 1,000 - 100 - 80,000 = -79,100, so agent owes the user.
        // Agent 2: 2,000 - 200 = 1,800, so user owes the agent.
        assertEquals(79100L to 1800L, AgentLedgerFrame.userDirections(draws))
        assertEquals(79100L to 0L,
            AgentLedgerFrame.userDirections(draws.filter { it.agentId == 1L }))
        assertEquals(0L to 1800L,
            AgentLedgerFrame.userDirections(draws.filter { it.agentId == 2L }))
        assertEquals(3000L, AgentStatementCalculator.summarize(draws).totalStake)
        assertEquals(300L, AgentStatementCalculator.summarize(draws).savedNetCommission)
    }

    @Test fun weekGroupsAndFilteredDrawsPreserveBothSessions() {
        val start = LocalDate.of(2026, 11, 2)
        val page = CustomerReportPage(start, start.plusDays(11))
        val rows = AgentLedgerFrame.rows(page, StatementPeriod.MONTH, null,
            report().draws, mapOf((day to evening) to "20"), emptySet())
        assertEquals(20, rows.size) // Ten weekdays, two draws per day.
        assertEquals(2, rows.count { it.date == day })
        assertEquals(0L, CustomerReportFrame.weekBandIndex(page, start))
        assertEquals(1L, CustomerReportFrame.weekBandIndex(page, start.plusDays(7)))
        assertEquals(1, AgentLedgerFrame.rows(page, StatementPeriod.MONTH,
            evening, report().draws, emptyMap(), emptySet())
            .count { it.date == day })
    }
}
