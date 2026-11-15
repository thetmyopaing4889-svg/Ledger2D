package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Regression and CPU microbenchmarks for growing input histories.
 *
 * Times are GitHub/JVM runner observations, NOT mobile-device guarantees.
 * No fragile hard millisecond threshold: CI hardware/JIT/GC vary.
 */
class CherryDiscoveryBoundedHistoryTest {
    private val days: List<LocalDate> by lazy {
        generateSequence(LocalDate.of(2020, 1, 1)) { it.plusDays(1) }
            .filter(DrawSchedule::isWeekday)
            .take(1501).toList()
    }
    private val target: DrawIdentity by lazy {
        DrawIdentity(days[1500], DrawSession.MORNING)
    }
    private val all: List<HistoricDraw> by lazy {
        (0 until 3000).map { i ->
            HistoricDraw(days[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00", HistoricSource.HISTORY)
        }
    }

    private fun search(rows: List<HistoricDraw>): CherryDiscoverySession {
        var state: CherryDiscoverySession? = null
        repeat(CherryFormulaDiscoveryEngine.STAGE_COUNT) {
            state = CherryFormulaDiscoveryEngine.nextRound(
                rows, target, state)
        }
        return requireNotNull(state)
    }

    @Test fun twoHundredToThreeThousandDrawsKeepWorkBoundedAndResultsStable() {
        val results = linkedMapOf<Int, CherryDiscoverySession>()
        for (size in listOf(200, 500, 1000, 3000)) {
            val rows = all.takeLast(size)
            val before = System.nanoTime()
            val state = search(rows)
            val elapsedMs = (System.nanoTime() - before) / 1_000_000L
            results[size] = state
            assertTrue(state.isComplete)
            assertEquals(207, state.inspectedExpressions)
            assertTrue(state.formulas.size <= 24)
            assertTrue("Old record signatures must never grow with database size",
                state.historySignature.length < 10_000)
            val expected = if (size == 200) 199 else 200
            for (kind in CherryDiscoveryKind.entries) {
                assertEquals("Only latest 200 frames may be scored",
                    expected, state.longest(kind)?.historicalStreak)
            }
            println("CHERRY_BOUNDED_BENCH inputDraws=" + size +
                " scoredDraws=" + expected + " stages=" +
                state.completedStages + " expressions=" + state.inspectedExpressions +
                " signatureChars=" + state.historySignature.length +
                " elapsedMs=" + elapsedMs)
        }
        val baseline = requireNotNull(results[500])
        assertEquals(baseline, results[1000])
        assertEquals(baseline, results[3000])
        assertEquals(baseline, search(all.takeLast(203)))
    }

    @Test fun tenThousandDrawsKeepTheSameBoundedFormulaSearch() {
        val weekdays = generateSequence(LocalDate.of(2005, 1, 3)) {
            it.plusDays(1)
        }.filter(DrawSchedule::isWeekday).take(5001).toList()
        val rows = (0 until 10_000).map { index ->
            HistoricDraw(weekdays[index / 2],
                if (index % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00", HistoricSource.HISTORY)
        }
        val next = DrawIdentity(weekdays[5000], DrawSession.MORNING)
        fun run(input: List<HistoricDraw>): CherryDiscoverySession {
            var session: CherryDiscoverySession? = null
            repeat(CherryFormulaDiscoveryEngine.STAGE_COUNT) {
                session = CherryFormulaDiscoveryEngine.nextRound(input, next, session)
            }
            return requireNotNull(session)
        }
        val start = System.nanoTime()
        val fromFullArchive = run(rows)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000L
        val fromOnlyLatest203 = run(rows.takeLast(203))
        assertEquals(fromOnlyLatest203, fromFullArchive)
        assertEquals(207, fromFullArchive.inspectedExpressions)
        assertTrue(fromFullArchive.formulas.size <= 24)
        assertTrue(fromFullArchive.historySignature.length < 10_000)
        for (kind in CherryDiscoveryKind.entries) {
            assertEquals(200, fromFullArchive.longest(kind)?.historicalStreak)
        }
        println("CHERRY_BOUNDED_10K inputDraws=10000 scoredDraws=200 " +
            "expressions=207 elapsedMs=" + elapsedMs)
    }

    @Test fun changingOnlyAncientHistoryDoesNotInvalidateFinishedSearch() {
        val old = search(all)
        val changed = all.toMutableList()
        changed[0] = changed[0].copy(digit = "99")
        assertEquals(old, CherryFormulaDiscoveryEngine.nextRound(
            changed, target, previousState = old))
        // A recent correction must still invalidate and restart the search.
        changed[changed.lastIndex] = changed.last().copy(digit = "98")
        val restarted = CherryFormulaDiscoveryEngine.nextRound(
            changed, target, previousState = old)
        assertEquals(1, restarted.completedStages)
        assertNotEquals(old.historySignature, restarted.historySignature)
    }

    @Test fun reverseOrderAndSourceOverridesCannotEvictRecentTruth() {
        val original = CherryDiscoveryHistoryNormalizer.newestBefore(
            all, target, emptySet(), 203)
        assertEquals(203, original.size)
        assertEquals(original, CherryDiscoveryHistoryNormalizer.newestBefore(
            all.reversed(), target, emptySet(), 203))
        val last = all.last()
        val winner = last.copy(digit = "79", source = HistoricSource.WINNER)
        val mixed = all + listOf(winner)
        val selected = CherryDiscoveryHistoryNormalizer.newestBefore(
            mixed, target, emptySet(), 203)
        assertEquals(203, selected.size)
        assertEquals(winner, selected.last())
        assertEquals(selected, CherryDiscoveryHistoryNormalizer.newestBefore(
            listOf(winner) + all.reversed(), target, emptySet(), 203))
    }

    @Test fun cancellationDuringLargeInputScanDoesNotReturnPartialSearch() {
        var checks = 0
        try {
            CherryFormulaDiscoveryEngine.nextRound(all, target,
                checkCancellation = {
                    if (++checks > 5) throw java.util.concurrent.CancellationException()
                })
            fail("Expected cancellation before returning a result")
        } catch (_: java.util.concurrent.CancellationException) {
            assertTrue(checks >= 6)
        }
        assertTrue(search(all.takeLast(203)).isComplete)
    }
}
