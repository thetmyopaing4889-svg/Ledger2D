package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TwoDResearchEngineTest {
    private val am = DrawSession.MORNING
    private val pm = DrawSession.EVENING
    private fun row(day: String, session: DrawSession, pair: String) =
        HistoricDraw(LocalDate.parse(day), session, pair, HistoricSource.HISTORY)
    private fun cutoff(day: String, session: DrawSession = am) =
        DrawIdentity(LocalDate.parse(day), session)
    private fun exact(
        mode: TwoDResearchRelationship = TwoDResearchRelationship.SAME_DAY_PM,
        scope: TwoDResearchScope = TwoDResearchScope.AM,
        target: String? = "21",
        range: TwoDHistoryRange = TwoDHistoryRange(),
    ) = TwoDResearchQuery("12", scope, mode, target, range)

    @Test fun sameDayCountsMatchedOtherMissingAndNotYetDueSeparately() {
        val rows = listOf(
            row("2026-11-02", am, "12"), row("2026-11-02", pm, "21"),
            row("2026-11-03", am, "12"), row("2026-11-03", pm, "22"),
            row("2026-11-04", am, "12"), // missing PM, never a negative
            row("2026-11-05", am, "12"), row("2026-11-05", pm, "21"),
            row("2026-11-06", am, "12"), row("2026-11-06", pm, "21"), // not yet final
        )
        val report = TwoDResearchEngine.search(rows, exact(), cutoff("2026-11-06", pm))
        assertEquals(5, report.sourceCount)
        assertEquals(3, report.observedCount)
        assertEquals(2, report.matchCount)
        assertEquals(1, report.otherCount)
        assertEquals(1, report.missingCount)
        assertEquals(1, report.pendingCount)
        assertEquals(0, report.outsideCount)
        assertEquals(listOf("21", "22"), report.followingFrequency.map { it.digit })
        assertEquals(listOf(2, 1), report.followingFrequency.map { it.count })
    }

    @Test fun discoveryReturnsAllRecordedFollowingPairsNotOnlyTargetMatches() {
        val rows = listOf(
            row("2026-11-02", am, "12"), row("2026-11-02", pm, "21"),
            row("2026-11-03", am, "12"), row("2026-11-03", pm, "34"),
        )
        val report = TwoDResearchEngine.search(rows, exact(target = null),
            cutoff("2026-11-04"))
        assertEquals(2, report.observedCount)
        assertEquals(0, report.matchCount)
        assertTrue(report.evidence.all { it.status == TwoDResearchStatus.OBSERVED })
        assertEquals(listOf("21", "34"), report.followingFrequency.map { it.digit })
    }

    @Test fun fridayPmFlowsToMondayAmAndSkipsOnlyVerifiedClosedDays() {
        val friday = row("2026-10-30", pm, "12")
        val normal = TwoDResearchEngine.followingSlot(
            friday, TwoDResearchRelationship.NEXT_DRAW)
        assertEquals(cutoff("2026-11-02"), normal)
        val closed = setOf(LocalDate.parse("2026-11-02"))
        assertEquals(cutoff("2026-11-03"), TwoDResearchEngine.followingSlot(
            friday, TwoDResearchRelationship.NEXT_DRAW, closed))
        val rows = listOf(friday, row("2026-11-03", am, "21"))
        val report = TwoDResearchEngine.search(rows,
            exact(mode = TwoDResearchRelationship.NEXT_DRAW, scope = TwoDResearchScope.PM),
            cutoff("2026-11-04"), closed)
        assertEquals(1, report.matchCount)
        assertEquals(cutoff("2026-11-03"), report.evidence.single().expected)
    }

    @Test fun unknownOpenSlotIsNotCompressedToNextKnownDraw() {
        val rows = listOf(row("2026-11-02", am, "12"),
            row("2026-11-03", am, "21"))
        val result = TwoDResearchEngine.search(rows,
            exact(mode = TwoDResearchRelationship.NEXT_DRAW), cutoff("2026-11-04"))
        assertEquals(1, result.missingCount)
        assertEquals(0, result.matchCount)
        assertEquals(cutoff("2026-11-02", pm), result.evidence.single().expected)
    }

    @Test fun researchRangeBoundaryIsNotAReportedNonmatch() {
        val rows = listOf(row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "21"))
        val range = TwoDHistoryRange(LocalDate.parse("2026-11-02"),
            LocalDate.parse("2026-11-02"))
        val a = TwoDResearchEngine.search(rows, exact(range = range),
            cutoff("2026-11-03"))
        assertEquals(1, a.matchCount)
        val friday = listOf(row("2026-11-06", pm, "12"),
            row("2026-11-09", am, "21"))
        val end = TwoDHistoryRange(LocalDate.parse("2026-11-06"),
            LocalDate.parse("2026-11-06"))
        val b = TwoDResearchEngine.search(friday,
            exact(mode = TwoDResearchRelationship.NEXT_DRAW,
                scope = TwoDResearchScope.PM, range = end),
            cutoff("2026-11-10"))
        assertEquals(1, b.outsideCount)
        assertEquals(0, b.otherCount)
        assertEquals(0, b.missingCount)
    }

    @Test fun resultAtExclusiveCutoffIsNeverUsedAsHistoricalEvidence() {
        val rows = listOf(row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "21"))
        val result = TwoDResearchEngine.search(rows, exact(),
            cutoff("2026-11-02", pm))
        assertEquals(1, result.pendingCount)
        assertEquals(0, result.observedCount)
    }

    @Test fun duplicatesBadPairsAndClosedSourcesAreExcluded() {
        val rows = listOf(
            row("2026-11-02", am, "12"),
            row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "21"),
            row("2026-11-03", am, "12"),
            row("2026-11-03", pm, "21"),
            row("2026-11-04", am, "A2"),
            row("2026-11-05", am, "12"),
        )
        val r = TwoDResearchEngine.search(rows, exact(),
            cutoff("2026-11-06"),
            setOf(LocalDate.parse("2026-11-03")))
        assertEquals(2, r.sourceCount)
        assertEquals(1, r.matchCount)
        assertEquals(1, r.missingCount)
    }

    @Test fun validatesImpossibleSameDayScopeAndTwoDigitInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            TwoDResearchEngine.search(emptyList(), exact(scope = TwoDResearchScope.ALL),
                cutoff("2026-11-06"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TwoDResearchEngine.search(emptyList(),
                TwoDResearchQuery("7", TwoDResearchScope.ALL,
                    TwoDResearchRelationship.NEXT_DRAW),
                cutoff("2026-11-06"))
        }
        val doubles = listOf(row("2026-11-02", am, "11"),
            row("2026-11-02", pm, "11"))
        assertEquals(1, TwoDResearchEngine.search(doubles,
            TwoDResearchQuery("11", TwoDResearchScope.AM,
                TwoDResearchRelationship.SAME_DAY_PM, "11"),
            cutoff("2026-11-03")).matchCount)
    }
}
