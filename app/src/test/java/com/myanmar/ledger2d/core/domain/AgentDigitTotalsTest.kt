package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test

class AgentDigitTotalsTest {
    private fun row(digit: String, stake: Long, netCommission: Long) =
        AgentDigitLine(digit, stake, netCommission)

    private val examples = listOf(
        AgentDigitGroup(1L, "Agent A", listOf(
            row("20", 1_000L, 100L),
            row("39", 500L, 50L),
        )),
        AgentDigitGroup(2L, "Agent B", listOf(
            row("20", 2_000L, 200L),
            row("45", 1_000L, 100L),
        )),
    )

    @Test fun combinedViewSumsMatchingDigitsAcrossAgents() {
        val combined = AgentDigitTotalsAggregator.combine(examples)
        assertEquals(listOf("20", "39", "45"), combined.map { it.digit })
        assertEquals(3_000L, combined.single { it.digit == "20" }.stake)
        assertEquals(300L, combined.single { it.digit == "20" }.netCommission)
        assertEquals(4_500L, combined.sumOf { it.stake })
        assertEquals(450L, combined.sumOf { it.netCommission })
        assertEquals(examples.sumOf { it.totalStake }, combined.sumOf { it.stake })
        assertEquals(examples.sumOf { it.totalNetCommission }, combined.sumOf { it.netCommission })
    }

    @Test fun perAgentViewNeverMergesAgentsOrTheirNames() {
        val sameNames = listOf(
            AgentDigitGroup(1L, "Customer A", listOf(row("20", 1_000L, 150L))),
            AgentDigitGroup(2L, "Customer A", listOf(row("20", 3_000L, 50L))),
        )
        assertEquals(listOf(1_000L, 3_000L), sameNames.map { it.totalStake })
        assertEquals(listOf(150L, 50L), sameNames.map { it.totalNetCommission })
        assertEquals(4_000L, AgentDigitTotalsAggregator.combine(sameNames).single().stake)
    }

    @Test fun selfAndForwardedCopiesAreCountedAsSeparateRecordedAgentStakes() {
        val original = AgentDigitGroup(1L, "Agent A", listOf(row("20", 100_000L, 15_000L)))
        val self = AgentDigitGroup(2L, "Self", listOf(row("20", 40_000L, 400L)))
        val forwarded = AgentDigitGroup(3L, "Agent B", listOf(row("20", 60_000L, 600L)))
        assertEquals(200_000L,
            AgentDigitTotalsAggregator.combine(listOf(original, self, forwarded)).single().stake)
        assertEquals(16_000L,
            AgentDigitTotalsAggregator.combine(listOf(original, self, forwarded)).single().netCommission)
        assertEquals(100_000L,
            AgentDigitTotalsAggregator.combine(listOf(original)).single().stake)
    }

    @Test fun emptyAndZeroCommissionTotalsAreStable() {
        assertTrue(AgentDigitTotalsAggregator.combine(emptyList()).isEmpty())
        assertTrue(AgentDigitTotalsAggregator.combine(
            listOf(AgentDigitGroup(1L, "Agent A", emptyList()))).isEmpty())
        val combined = AgentDigitTotalsAggregator.combine(
            listOf(AgentDigitGroup(1L, "Agent A", listOf(row("00", 12_000L, 0L)))))
        assertEquals("00", combined.single().digit)
        assertEquals(0L, combined.single().netCommission)
    }

    @Test fun sameDigitRepeatedWithinAgentAndOtherAgentsIsOnlyOneCombinedDigit() {
        val groups = listOf(
            AgentDigitGroup(1L, "Agent A", listOf(row("02", 500L, 25L), row("02", 300L, 15L))),
            AgentDigitGroup(2L, "Agent B", listOf(row("02", 700L, 35L))),
        )
        val total = AgentDigitTotalsAggregator.combine(groups)
        assertEquals(1, total.size)
        assertEquals(1_500L, total.single().stake)
        assertEquals(75L, total.single().netCommission)
    }

    @Test fun invalidDigitsAndOverflowCannotSilentlyProduceWrongTotals() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentDigitTotalsAggregator.combine(listOf(
                AgentDigitGroup(1L, "A", listOf(row("2", 500L, 0L)))))
        }
        assertThrows(ArithmeticException::class.java) {
            AgentDigitTotalsAggregator.combine(listOf(
                AgentDigitGroup(1L, "A", listOf(row("20", Long.MAX_VALUE, 0))),
                AgentDigitGroup(2L, "B", listOf(row("20", 1L, 0)))))
        }
    }
}
