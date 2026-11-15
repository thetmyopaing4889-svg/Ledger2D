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

    @Test fun noFormulaIsOfferedTwiceAndStagesOnlyAdvanceWhenNeeded() {
        for (kind in CherryDiscoveryKind.entries) {
            val rows = history()
            var cursor: CherryDiscoverySearchCursor? = null
            val ids = mutableSetOf<String>()
            var lastStages = 0
            var steps = 0
            while (true) {
                val next = CherryDiscoverySearchMore.next(
                    rows, target, kind, cursor)
                assertTrue(next.completedStages >= lastStages)
                lastStages = next.completedStages
                assertEquals(next.offeredIds.size, ids.size +
                    if (next.newCandidate != null) 1 else 0)
                next.newCandidate?.let {
                    assertEquals(kind, it.kind)
                    assertTrue("Search More must not repeat an old formula",
                        ids.add(it.id))
                    assertEquals("HISTORICAL_FIT", it.evidenceLabel)
                    assertEquals(19, it.historicalStreak)
                }
                cursor = next
                assertTrue("Finite search must exhaust", ++steps < 50)
                if (next.exhausted) break
            }
            val final = requireNotNull(cursor)
            assertTrue(final.engine.isComplete)
            assertNull(final.newCandidate)
            assertTrue(final.bestCandidate != null)
            assertEquals(ids, final.offeredIds)
            assertEquals(207, final.inspectedExpressions)
            assertEquals(final, CherryDiscoverySearchMore.next(
                rows, target, kind, final))
        }
    }

    @Test fun checkingAnotherCandidateDoesNotConsumeStageIfOneAlreadyAvailable() {
        val rows = (0 until 20).map { i ->
            val pair = when (i % 4) {
                0 -> "12"
                1 -> "27"
                2 -> "32"
                else -> "47"
            }
            HistoricDraw(dates[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                pair, HistoricSource.WINNER)
        }
        var cursor: CherryDiscoverySearchCursor? = null
        var prev = 0
        repeat(8) {
            val result = CherryDiscoverySearchMore.next(
                rows, target, CherryDiscoveryKind.LONE, cursor)
            assertTrue(result.completedStages >= prev)
            assertTrue(result.completedStages <= CherryFormulaDiscoveryEngine.STAGE_COUNT)
            assertTrue(result.offeredIds.size <= 24)
            prev = result.completedStages
            cursor = result
        }
        assertNotNull(cursor)
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
