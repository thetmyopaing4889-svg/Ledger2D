package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CherryV2OrderBenchmarkTest {
    private val days = generateSequence(LocalDate.parse("2026-08-03")) {
        it.plusDays(1)
    }.filter(DrawSchedule::isWeekday).take(22).toList()
    private val target = DrawIdentity(days[20], DrawSession.MORNING)

    private fun fibonacciDraws(): List<HistoricDraw> {
        val sequence = mutableListOf("12", "34")
        repeat(38) {
            val nextTop = (sequence.last()[0].digitToInt() +
                sequence[sequence.lastIndex - 1][0].digitToInt()) % 10
            sequence.add("$nextTop${(nextTop + 1) % 10}")
        }
        return sequence.mapIndexed { i, digit ->
            HistoricDraw(
                days[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                digit, HistoricSource.WINNER,
            )
        }
    }

    private fun clock(): () -> Long {
        var ticks = 0L
        return { ticks += 1_000L; ticks }
    }

    @Test fun guidedScheduleSurfacesCrossLagTenWithinSmallEqualCandidateBudget() {
        val ctx = CherryV2HistoricalEvaluator.prepare(fibonacciDraws(), target)
        val comparison = CherryV2OrderBenchmark.compare(
            ctx, CherryDiscoveryKind.LONE,
            candidatesPerMode = 12,
            budgetPerModeNanos = 200_000_000L,
            nanoTime = clock(),
        )
        assertEquals(12, comparison.raw.inspected)
        assertEquals(12, comparison.guided.inspected)
        assertEquals(10, comparison.guided.firstAtLeastTen)
        assertTrue(comparison.guided.longestHistoricalStreak >= 10)
        assertEquals(ctx.historySignature, comparison.historySignature)
        assertTrue(comparison.guided.distinctBehaviorTraces <=
            comparison.guided.scorable)
        assertTrue(comparison.raw.distinctBehaviorTraces <=
            comparison.raw.scorable)
    }

    @Test fun anyFutureResultAtOrAfterTargetCannotLeakIntoComparison() {
        val original = fibonacciDraws()
        val before = CherryV2HistoricalEvaluator.prepare(original, target)
        val future = original + listOf(
            HistoricDraw(target.date, target.session, "99", HistoricSource.WINNER),
            HistoricDraw(target.date, DrawSession.EVENING, "88", HistoricSource.WINNER),
        )
        val after = CherryV2HistoricalEvaluator.prepare(future, target)
        assertEquals(before.historySignature, after.historySignature)
        val first = CherryV2OrderBenchmark.compare(
            before, CherryDiscoveryKind.LONE, 16, 200_000_000L, clock())
        val second = CherryV2OrderBenchmark.compare(
            after, CherryDiscoveryKind.LONE, 16, 200_000_000L, clock())
        assertEquals(first.guided.firstByStreak, second.guided.firstByStreak)
        assertEquals(first.raw.firstByStreak, second.raw.firstByStreak)
        assertEquals(first.guided.firstAtLeastTen, second.guided.firstAtLeastTen)
    }

    @Test fun strictBudgetAndCandidateCapPreventUnboundedBenchmarkRuns() {
        val ctx = CherryV2HistoricalEvaluator.prepare(fibonacciDraws(), target)
        val report = CherryV2OrderBenchmark.compare(
            ctx, CherryDiscoveryKind.WAM,
            candidatesPerMode = 64, budgetPerModeNanos = 4_000L,
            nanoTime = clock())
        assertTrue(report.guided.inspected <= 64)
        assertTrue(report.raw.inspected <= 64)
        assertTrue(report.guided.budgetExpired && report.raw.budgetExpired)
        // Timeouts inside formula evaluation must never count incomplete
        // candidates in scorable/distinct/duplicate performance totals.
        for (metrics in listOf(report.raw, report.guided)) {
            assertTrue(metrics.scorable <= metrics.inspected)
            assertEquals(metrics.scorable,
                metrics.distinctBehaviorTraces +
                    metrics.duplicateBehaviorTraces)
        }
        for (bad in listOf(0, CherryV2OrderBenchmark.MAX_CANDIDATES + 1)) {
            try {
                CherryV2OrderBenchmark.compare(ctx, CherryDiscoveryKind.LONE,
                    candidatesPerMode = bad)
                fail("Benchmark must reject invalid candidate limits")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            CherryV2OrderBenchmark.compare(ctx, CherryDiscoveryKind.LONE,
                budgetPerModeNanos = 0)
            fail("Nonpositive deadline must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun cancellationPropagatesAndAuditCannotChangeRuntimeSearchCursor() {
        val ctx = CherryV2HistoricalEvaluator.prepare(fibonacciDraws(), target)
        val cursor = CherryV2StreakRegistry(target, CherryDiscoveryKind.BREAK,
            ctx.historySignature)
        try {
            CherryV2OrderBenchmark.compare(ctx, CherryDiscoveryKind.BREAK,
                candidatesPerMode = 200,
                checkCancellation = { throw CancellationException("cancel") })
            fail("Audit must be cancellable")
        } catch (_: CancellationException) {}
        assertEquals(CherryV2ExpandableGrammar.FIRST_INDEX, cursor.nextIndex)
        assertTrue(cursor.firstByStreak.isEmpty())
    }
}
