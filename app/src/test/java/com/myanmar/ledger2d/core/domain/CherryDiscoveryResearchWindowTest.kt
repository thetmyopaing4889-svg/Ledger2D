package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Research windows are explicitly separate from normal discovery. */
class CherryDiscoveryResearchWindowTest {
    private val days by lazy {
        generateSequence(LocalDate.of(2020, 1, 1)) { it.plusDays(1) }
            .filter(DrawSchedule::isWeekday).take(1500).toList()
    }
    private val rows by lazy {
        (0 until 3000).map { i ->
            // Non-constant input to avoid unrealistically easy full-history fits.
            val number = ((i * 37 + (i / 7) * 11) % 100)
                .toString().padStart(2, '0')
            HistoricDraw(days[i / 2], if (i % 2 == 0)
                DrawSession.MORNING else DrawSession.EVENING,
                number, HistoricSource.HISTORY)
        }
    }

    @Test fun twoAndThreeYearSamplesAreExplicitAndTimeDistributed() {
        val through = days.last()
        val two = CherryDiscoveryWalkForward.replayResearchWindow(
            rows, throughInclusive = through, years = 2,
            sampleTargets = 16)
        val three = CherryDiscoveryWalkForward.replayResearchWindow(
            rows, throughInclusive = through, years = 3,
            sampleTargets = 16)
        assertEquals(2, two.years)
        assertEquals(3, three.years)
        assertEquals(16, two.sampledTargets)
        assertEquals(16, three.sampledTargets)
        assertEquals(48, two.report.checks.size)
        assertEquals(48, three.report.checks.size)
        assertTrue(three.eligibleTargetsInWindow >
            two.eligibleTargetsInWindow)
        assertTrue(two.report.checks.map { it.target }.distinct().size == 16)
        assertTrue(three.report.checks.map { it.target }.distinct().size == 16)
        assertTrue(two.report.checks.all {
            it.target.date >= two.fromInclusive &&
                !it.target.date.isAfter(through)
        })
        assertTrue(three.report.checks.all {
            it.target.date >= three.fromInclusive &&
                !it.target.date.isAfter(through)
        })
        assertTrue(two.report.checks.first().target.date <
            through.minusMonths(12))
        assertTrue(three.report.checks.first().target.date <
            through.minusMonths(24))
        println("CHERRY_RESEARCH_SAMPLE twoYearPool=" +
            two.eligibleTargetsInWindow + " threeYearPool=" +
            three.eligibleTargetsInWindow + " chosen=" + two.sampledTargets)
    }

    @Test fun recordsAfterAsOfCannotLeakIntoOldResearchAudit() {
        val through = days[1350]
        val all = CherryDiscoveryWalkForward.replayResearchWindow(
            rows, throughInclusive = through, years = 2,
            sampleTargets = 10)
        val earlier = CherryDiscoveryWalkForward.replayResearchWindow(
            rows.filter { !it.date.isAfter(through) },
            throughInclusive = through, years = 2,
            sampleTargets = 10)
        assertEquals(earlier, all)
    }

    @Test fun boundedResearchSamplesCanBeCancelled() {
        try {
            var calls = 0
            CherryDiscoveryWalkForward.replayResearchWindow(
                rows, throughInclusive = days.last(), years = 2,
                sampleTargets = 16,
                checkCancellation = {
                    if (++calls > 15)
                        throw java.util.concurrent.CancellationException()
                })
            fail("Research cancellation should propagate")
        } catch (_: java.util.concurrent.CancellationException) { /* expected */ }
    }

    @Test fun refusesUnboundedOrInvalidResearchArguments() {
        try {
            CherryDiscoveryWalkForward.replayResearchWindow(
                rows, throughInclusive = days.last(), years = 4)
            fail("Research years must be 2 or 3")
        } catch (_: IllegalArgumentException) { /* expected */ }
        try {
            CherryDiscoveryWalkForward.replayResearchWindow(
                rows, throughInclusive = days.last(), sampleTargets = 3000)
            fail("Sample targets must be bounded")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }
}
