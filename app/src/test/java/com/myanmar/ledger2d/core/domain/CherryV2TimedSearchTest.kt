package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate
import java.util.concurrent.CancellationException

class CherryV2TimedSearchTest {
    private val dates = generateSequence(LocalDate.parse("2026-09-01")) { it.plusDays(1) }
        .filter { DrawSchedule.isWeekday(it) }.take(14).toList()
    private val target = DrawIdentity(dates[12], DrawSession.MORNING)
    private val history = (0 until 24).map { n ->
        val top = n % 10
        HistoricDraw(
            dates[n / 2],
            if (n % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
            "$top${(top + 4) % 10}", HistoricSource.WINNER)
    }

    private fun context() = CherryV2HistoricalEvaluator.prepare(history, target)
    private fun testClock(): () -> Long {
        var clock = 0L
        return { clock += 1_000L; clock }
    }

    @Test fun timeBudgetStopsWithoutExhaustingInfiniteGrammarOrLosingBestResults() {
        val ctx = context()
        val snapshots = mutableListOf<CherryV2StreakRegistry>()
        val result = CherryV2TimedSearch.run(
            ctx, CherryDiscoveryKind.LONE,
            timeBudgetNanos = 2_000_000L,
            monotonicNanos = testClock(),
            onCheckpoint = { snapshots.add(it) },
        )
        assertEquals(CherryV2TimedSearch.StopReason.TIME_LIMIT, result.stopReason)
        assertTrue("Must make forward progress", result.inspectedThisBatch > 0)
        assertEquals(BigInteger.valueOf(result.inspectedThisBatch.toLong()),
            result.cursor.nextIndex)
        assertEquals(result.cursor, snapshots.last())
        assertTrue(result.cursor.recommended()!!.historicalStreak >= 10)
    }

    @Test fun continuingStartsAtPreviouslyExaminedIndexAndKeepsFirstFound() {
        val ctx = context()
        val first = CherryV2TimedSearch.run(
            ctx, CherryDiscoveryKind.LONE, timeBudgetNanos = 1_500_000L,
            monotonicNanos = testClock())
        val second = CherryV2TimedSearch.run(
            ctx, CherryDiscoveryKind.LONE, previous = first.cursor,
            timeBudgetNanos = 1_500_000L, monotonicNanos = testClock())
        assertTrue(second.cursor.nextIndex > first.cursor.nextIndex)
        assertTrue(second.cursor.firstByStreak.keys.containsAll(first.cursor.firstByStreak.keys))
        first.cursor.firstByStreak.forEach { (streak, found) ->
            assertEquals(found.formulaId,
                second.cursor.firstByStreak.getValue(streak).formulaId)
        }
        assertEquals(first.cursor.nextIndex.add(
            BigInteger.valueOf(second.inspectedThisBatch.toLong())),
            second.cursor.nextIndex)
    }

    @Test fun staleHistoryIsRejectedRatherThanReplayingWrongResults() {
        val before = context()
        val cursor = CherryV2StreakRegistry(target, CherryDiscoveryKind.LONE,
            before.historySignature)
        val altered = history.toMutableList()
        altered[20] = altered[20].copy(digit = "99")
        val updated = CherryV2HistoricalEvaluator.prepare(altered, target)
        assertNotEquals(before.historySignature, updated.historySignature)
        try {
            CherryV2TimedSearch.run(updated, CherryDiscoveryKind.LONE, cursor)
            fail("Changed history must not use stale cursor")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun cancelledSearchPropagatesWithoutMutatingIncomingCursor() {
        val ctx = context()
        val original = CherryV2StreakRegistry(target, CherryDiscoveryKind.LONE,
            ctx.historySignature)
        var checked = 0
        try {
            CherryV2TimedSearch.run(ctx, CherryDiscoveryKind.LONE,
                previous = original, monotonicNanos = testClock(),
                checkCancellation = {
                    if (++checked == 7) throw CancellationException("cancel")
                })
            fail("Must respond to cancellation")
        } catch (_: CancellationException) {}
        assertEquals(BigInteger.ZERO, original.nextIndex)
        assertTrue(original.firstByStreak.isEmpty())
    }

    @Test fun emptyPreviousResultDoesNotWasteBudgetOrForgeAnyFormula() {
        val blocked = CherryV2HistoricalEvaluator.prepare(
            history.dropLast(1), target)
        val result = CherryV2TimedSearch.run(
            blocked, CherryDiscoveryKind.BREAK, monotonicNanos = testClock())
        assertEquals(CherryV2TimedSearch.StopReason.BLOCKED, result.stopReason)
        assertEquals(0, result.inspectedThisBatch)
        assertTrue(result.cursor.firstByStreak.isEmpty())
    }
}
