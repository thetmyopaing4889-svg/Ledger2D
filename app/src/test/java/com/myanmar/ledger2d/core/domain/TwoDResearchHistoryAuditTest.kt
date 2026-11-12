package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TwoDResearchHistoryAuditTest {
    private val am = DrawSession.MORNING
    private val pm = DrawSession.EVENING
    private fun row(date: String, session: DrawSession, pair: String) =
        HistoricDraw(LocalDate.parse(date), session, pair, HistoricSource.HISTORY)
    private fun slot(date: String, session: DrawSession) =
        DrawIdentity(LocalDate.parse(date), session)

    @Test fun emptyHistoryMakesNoClaimsAboutEarlierMissingDates() {
        val result = TwoDResearchHistoryAuditor.inspect(
            emptyList(), slot("2026-11-12", am))
        assertEquals(0, result.verifiedSlots)
        assertEquals(0, result.expectedOpenSlots)
        assertEquals(0, result.unverifiedCount)
        assertNull(result.earliestVerified)
        assertNull(result.latestVerified)
        assertNull(result.coverageRatio)
        assertFalse(result.coverageComplete)
    }

    @Test fun classifiesExcludedRowsAndUnverifiedOpenSlotWithoutFalseNonmatches() {
        val rows = listOf(
            row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "21"),
            row("2026-11-02", am, "12"), // one extra copy
            row("2026-11-03", am, "17"), // PM truly unverified
            row("2026-11-04", am, "07"),
            row("2026-11-04", pm, "11"),
            row("2026-11-03", pm, "1X"), // invalid pair
            row("2026-10-31", am, "55"), // Saturday
            row("2026-10-30", pm, "65"), // verified closed
            row("2026-11-05", am, "23"), // exclusive cutoff
        )
        val closed = setOf(LocalDate.parse("2026-10-30"))
        val cutoff = slot("2026-11-05", am)
        val report = TwoDResearchHistoryAuditor.inspect(rows, cutoff, closed)
        assertEquals(rows.size, report.sourceRows)
        assertEquals(5, report.verifiedSlots)
        assertEquals(1, report.invalidPairRows)
        assertEquals(2, report.nonWorkingDayRows)
        assertEquals(1, report.atOrAfterCutoffRows)
        assertEquals(1, report.duplicateSlotRows)
        assertEquals(0, report.conflictingSlots)
        assertEquals(6, report.expectedOpenSlots)
        assertEquals(listOf(slot("2026-11-03", pm)), report.unverifiedOpenSlots)
        assertEquals(5.0 / 6.0, report.coverageRatio!!, 0.00001)
        assertEquals(TwoDResearchEngine.eligibleHistoryCount(rows, cutoff, closed),
            report.verifiedSlots)
        assertEquals(report.sourceRows,
            report.invalidPairRows + report.nonWorkingDayRows +
                report.atOrAfterCutoffRows + report.duplicateSlotRows +
                report.verifiedSlots)
    }

    @Test fun skipsVerifiedClosuresAndWeekendsButNotUnknownOpenDays() {
        val rows = listOf(
            row("2026-11-06", am, "70"), // Friday
            row("2026-11-06", pm, "17"),
            row("2026-11-10", am, "21"), // Tuesday after closed Monday
        )
        val cutoff = slot("2026-11-10", pm)
        val closed = setOf(LocalDate.parse("2026-11-09"))
        val report = TwoDResearchHistoryAuditor.inspect(rows, cutoff, closed)
        assertEquals(3, report.verifiedSlots)
        assertEquals(3, report.expectedOpenSlots)
        assertTrue(report.unverifiedOpenSlots.isEmpty())
        assertTrue(report.coverageComplete)
        assertEquals(1.0, report.coverageRatio!!, 0.00001)
        val following = TwoDResearchEngine.followingSlot(rows[1],
            TwoDResearchRelationship.NEXT_DRAW, closed)
        assertEquals(slot("2026-11-10", am), following)
    }

    @Test fun conflictingDuplicatesAreFlaggedButNotInventedAsTwoDraws() {
        val rows = listOf(
            row("2026-11-02", am, "12"),
            row("2026-11-02", am, "34"), // conflicting duplicate
            row("2026-11-02", pm, "21"),
        )
        val cutoff = slot("2026-11-03", am)
        val report = TwoDResearchHistoryAuditor.inspect(rows, cutoff)
        assertEquals(2, report.verifiedSlots)
        assertEquals(1, report.duplicateSlotRows)
        assertEquals(1, report.conflictingSlots)
        assertEquals(0, report.unverifiedCount)
        assertEquals(TwoDResearchEngine.eligibleHistoryCount(rows, cutoff),
            report.verifiedSlots)
    }

    @Test fun missingEarlierHistoryBeforeFirstAvailableDateIsNotInvented() {
        val rows = listOf(row("2026-11-11", pm, "88"))
        val audit = TwoDResearchHistoryAuditor.inspect(
            rows, slot("2026-11-12", am))
        assertEquals(1, audit.verifiedSlots)
        assertEquals(2, audit.expectedOpenSlots)
        assertEquals(listOf(slot("2026-11-11", am)), audit.unverifiedOpenSlots)
        assertFalse(audit.unverifiedOpenSlots.any {
            it.date.isBefore(LocalDate.parse("2026-11-11"))
        })
    }
}
