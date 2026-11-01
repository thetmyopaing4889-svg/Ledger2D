package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.AgentEntity
import org.junit.Assert.*
import org.junit.Test

class AgentListFilterTest {
    private fun agent(id: Long, name: String, phone: String, address: String, rate: Long) =
        AgentEntity(
            id = id, name = name, phone = phone, address = address,
            rate = rate, createdAt = 1L, updatedAt = 1L,
        )

    private val agents = listOf(
        agent(1L, "Agent A", "091234", "Yangon", 80L),
        agent(2L, "Self", "095555", "Mandalay", 85L),
        agent(3L, "Agent B", "097777", "Bago", 90L),
    )

    @Test fun emptyQueryReturnsSameOrderedAgentsWithoutChangingData() {
        assertEquals(agents, filterAgentsForList(agents, ""))
        assertEquals(agents, filterAgentsForList(agents, "   "))
        assertEquals(listOf(80L, 85L, 90L), agents.map { it.rate })
    }

    @Test fun searchMatchesNamePhoneOrAddressCaseInsensitively() {
        assertEquals(listOf(1L), filterAgentsForList(agents, " aGeNt a ").map { it.id })
        assertEquals(listOf(2L), filterAgentsForList(agents, "SELF").map { it.id })
        assertEquals(listOf(3L), filterAgentsForList(agents, "097777").map { it.id })
        assertEquals(listOf(1L), filterAgentsForList(agents, "yangon").map { it.id })
    }

    @Test fun distinctAgentsAreNotMergedByMatchingNamesOrCommission() {
        val sameName = agents + agent(4L, "Agent A", "098888", "Naypyitaw", 95L)
        val filtered = filterAgentsForList(sameName, "agent a")
        assertEquals(listOf(1L, 4L), filtered.map { it.id })
        assertEquals(listOf(80L, 95L), filtered.map { it.rate })
    }

    @Test fun noMatchLeavesTheUnderlyingAgentListIntact() {
        assertTrue(filterAgentsForList(agents, "Not present").isEmpty())
        assertEquals(3, agents.size)
    }

    @Test fun nextAgentDateSkipsClosedWorkingDay() {
        val monday = java.time.LocalDate.of(2026, 11, 2)
        val tuesday = monday.plusDays(1)
        val date = nextAgentListDraw(java.time.LocalDateTime.of(2026, 11, 1, 18, 0), setOf(monday))
        assertEquals(tuesday, date.date)
        assertEquals(com.myanmar.ledger2d.core.model.DrawSession.MORNING, date.session)
    }

    @Test fun nextAgentDrawUpdatesWhenMorningAndEveningCutoffsPass() {
        val d = java.time.LocalDate.of(2026, 11, 2)
        val morning = nextAgentListDraw(java.time.LocalDateTime.of(2026, 11, 2, 11, 59), emptySet())
        val evening = nextAgentListDraw(java.time.LocalDateTime.of(2026, 11, 2, 12, 1), emptySet())
        val next = nextAgentListDraw(java.time.LocalDateTime.of(2026, 11, 2, 16, 30), emptySet())
        assertEquals(d, morning.date)
        assertEquals(com.myanmar.ledger2d.core.model.DrawSession.MORNING, morning.session)
        assertEquals(d, evening.date)
        assertEquals(com.myanmar.ledger2d.core.model.DrawSession.EVENING, evening.session)
        assertEquals(d.plusDays(1), next.date)
        assertEquals(com.myanmar.ledger2d.core.model.DrawSession.MORNING, next.session)
    }
}
