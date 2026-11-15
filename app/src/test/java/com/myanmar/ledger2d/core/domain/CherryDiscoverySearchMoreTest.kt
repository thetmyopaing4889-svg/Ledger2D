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

    @Test fun equivalenceDedupSkipsDifferentNamesWithSameOutputBehavior() {
        val rows = history()
        val first = CherryFormulaDiscoveryEngine.nextAlternative(
            rows, target, CherryDiscoveryKind.LONE)
        val equivalent = CherryFormulaDiscoveryEngine.nextAlternative(
            rows, target, CherryDiscoveryKind.LONE, first.nextPosition)
        assertNotEquals(first.candidate?.id, equivalent.candidate?.id)
        assertEquals("top1 and tail1 are indistinguishable in this fixture",
            first.behaviorKey, equivalent.behaviorKey)

        val offered = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE)
        assertEquals(first.candidate?.id, offered.newCandidate?.id)
        val second = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE, offered)
        assertNotEquals("An equivalent formula must be skipped",
            equivalent.candidate?.id, second.newCandidate?.id)
        assertEquals(2, second.offeredBehaviors.size)
        assertEquals(2, second.offeredBehaviors.values.toSet().size)
    }

    @Test fun searchMoreEnumeratesUniqueBehaviorsAndExhaustsFiniteGrammar() {
        for (kind in CherryDiscoveryKind.entries) {
            val rows = history()
            var cursor: CherryDiscoverySearchCursor? = null
            val ids = mutableSetOf<String>()
            val behaviors = mutableSetOf<String>()
            var lastStages = 0
            var calls = 0
            while (true) {
                val next = CherryDiscoverySearchMore.next(
                    rows, target, kind, cursor)
                assertFalse(next.blocked)
                assertTrue(next.completedStages >= lastStages)
                lastStages = next.completedStages
                if (next.exhausted) {
                    assertNull(next.newCandidate)
                    assertTrue(next.engine.isComplete)
                    assertEquals(207, next.inspectedExpressions)
                    assertEquals(ids, next.offeredIds)
                    assertEquals(behaviors, next.offeredBehaviors.values.toSet())
                    assertEquals(next, CherryDiscoverySearchMore.next(
                        rows, target, kind, next))
                    break
                }
                val fresh = requireNotNull(next.newCandidate)
                assertEquals(kind, fresh.kind)
                assertTrue(ids.add(fresh.id))
                assertTrue(behaviors.add(requireNotNull(
                    next.offeredBehaviors[fresh.id])))
                assertEquals(ids, next.offeredIds)
                assertEquals(ids, next.offeredPositions.keys)
                assertEquals(ids, next.offeredBehaviors.keys)
                assertEquals("HISTORICAL_FIT", fresh.evidenceLabel)
                assertTrue(fresh.historicalStreak >= 3)
                assertNotNull(next.bestCandidate)
                assertTrue(next.nextPosition > (cursor?.nextPosition ?: 0))
                cursor = next
                assertTrue("Finite grammar must terminate", ++calls <= 828)
            }
            assertTrue(ids.isNotEmpty())
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
