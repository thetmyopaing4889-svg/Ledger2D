package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CancellationException

class CherryDiscoverySearchMoreTest {
    private val dates = generateSequence(LocalDate.of(2026, 11, 2)) {
        it.plusDays(1)
    }.filter(DrawSchedule::isWeekday).take(12).toList()

    private val target = DrawIdentity(dates[10], DrawSession.MORNING)

    private fun history(): List<HistoricDraw> = (0 until 20).map { i ->
        HistoricDraw(dates[i / 2],
            if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
            "00", HistoricSource.WINNER)
    }

    @Test fun searchMoreEnumeratesDistinctFormulasAndOnlyAdvancesNewStages() {
        for (kind in CherryDiscoveryKind.entries) {
            val rows = history()
            var cursor: CherryDiscoverySearchCursor? = null
            val ids = mutableSetOf<String>()
            var lastStages = 0
            // The degenerate all-zero fixture has hundreds of valid
            // alternatives, so sample the first dozen instead of retaining
            // a large result list or wasting CI time enumerating them all.
            repeat(12) {
                val next = CherryDiscoverySearchMore.next(
                    rows, target, kind, cursor)
                assertFalse(next.blocked)
                assertFalse(next.exhausted)
                assertTrue(next.completedStages >= lastStages)
                lastStages = next.completedStages
                val fresh = requireNotNull(next.newCandidate)
                assertEquals(kind, fresh.kind)
                assertTrue("Search More must not repeat an old formula",
                    ids.add(fresh.id))
                assertEquals(ids, next.offeredIds)
                assertEquals("HISTORICAL_FIT", fresh.evidenceLabel)
                assertEquals(19, fresh.historicalStreak)
                assertEquals(19, next.bestCandidate?.historicalStreak)
                assertTrue(next.nextPosition > (cursor?.nextPosition ?: 0))
                cursor = next
            }
            val last = requireNotNull(cursor)
            assertEquals(12, last.offeredIds.size)
            // End-of-grammar handling must return exhaustion while keeping
            // the previous best fit and all already-offered IDs.
            val skipToEnd = last.copy(
                nextPosition = 207 * 4)
            val completed = CherryDiscoverySearchMore.next(
                rows, target, kind, skipToEnd)
            assertTrue(completed.exhausted)
            assertTrue(completed.engine.isComplete)
            assertEquals(207, completed.inspectedExpressions)
            assertNull(completed.newCandidate)
            assertEquals(last.bestCandidate, completed.bestCandidate)
            assertEquals(last.offeredIds, completed.offeredIds)
            assertEquals(completed, CherryDiscoverySearchMore.next(
                rows, target, kind, completed))
        }
    }

    @Test fun nextAlternativeWithinSameStageDoesNotRepeatAnExpression() {
        val rows = history()
        val first = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE)
        val second = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE, first)
        assertEquals(1, first.completedStages)
        assertEquals(1, second.completedStages)
        assertEquals(2, second.offeredIds.size)
        assertNotEquals(first.newCandidate?.id, second.newCandidate?.id)
        assertEquals(first.bestCandidate, second.bestCandidate)
    }

    @Test fun changingHistoryResetsOfferedIdsAndNeverReusesOldFingerprint() {
        val oldHistory = history()
        val previous = CherryDiscoverySearchMore.next(
            oldHistory, target, CherryDiscoveryKind.LONE)
        assertEquals(1, previous.offeredIds.size)
        val changed = oldHistory.toMutableList()
        changed[changed.lastIndex] =
            changed.last().copy(digit = "98")
        val restarted = CherryDiscoverySearchMore.next(
            changed, target, CherryDiscoveryKind.LONE, previous)
        assertNotEquals(previous.engine.historySignature,
            restarted.engine.historySignature)
        assertTrue("Previous offered IDs must not be treated as new-session state",
            restarted.offeredIds.size <= 1)
    }

    @Test fun missingPreviousDrawBlocksSearchInsteadOfFabricatingAFormula() {
        val rows = history()
        val good = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.WAM)
        val broken = CherryDiscoverySearchMore.next(
            rows.dropLast(1), target, CherryDiscoveryKind.WAM, good)
        assertTrue(broken.blocked)
        assertNull(broken.newCandidate)
        assertNull(broken.bestCandidate)
        assertTrue(broken.offeredIds.isEmpty())
        assertFalse(broken.exhausted)
    }

    @Test fun cancelledContinuationLeavesPriorCandidateAndProgressUnchanged() {
        val rows = history()
        val first = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE)
        var checked = 0
        try {
            CherryDiscoverySearchMore.next(
                rows, target, CherryDiscoveryKind.LONE, first,
                checkCancellation = {
                    if (++checked >= 3) throw CancellationException("stop")
                })
            fail("Search More should propagate cancellation")
        } catch (_: CancellationException) { /* expected */ }
        assertEquals(1, first.offeredIds.size)
        assertNotNull(first.newCandidate)
        val resumed = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE, first)
        assertNotEquals(first, resumed)
        assertEquals(first.bestCandidate, resumed.bestCandidate)
    }
}
