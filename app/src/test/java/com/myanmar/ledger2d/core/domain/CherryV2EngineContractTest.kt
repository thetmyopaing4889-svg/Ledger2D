package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigInteger
import java.time.LocalDate
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

/**
 * Core contract for the original agreed search flow. This exercises the
 * ACTUAL guided grammar, historical evaluator, time-limited controller and
 * portable checkpoint, not a ranking-only audit or production UI.
 *
 * Synthetic outcomes here are deterministic fixtures ONLY. They do not
 * represent evidence of any future forecasting advantage.
 */
class CherryV2EngineContractTest {
    private val dates = generateSequence(LocalDate.parse("2026-08-03")) {
        it.plusDays(1)
    }.filter(DrawSchedule::isWeekday).take(32).toList()
    private val target = DrawIdentity(dates[25], DrawSession.MORNING)

    private fun history(): List<HistoricDraw> {
        val pairs = mutableListOf("12", "34")
        repeat(58) {
            val top = (pairs.last()[0].digitToInt() +
                pairs[pairs.lastIndex - 1][0].digitToInt()) % 10
            pairs.add("$top${(top + 1) % 10}")
        }
        return pairs.mapIndexed { i, pair ->
            HistoricDraw(
                dates[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                pair,
                HistoricSource.WINNER,
            )
        }
    }

    private fun context() =
        CherryV2HistoricalEvaluator.prepare(history(), target)

    private fun clock(tickNanos: Long = 1_000L): () -> Long {
        var now = 0L
        return { now += tickNanos; now }
    }

    @Test fun firstThreeWinsSecondThreeDiscardedFourAndTenRetained() {
        val ctx = context()
        val kind = CherryDiscoveryKind.LONE
        fun fit(slot: Long, streak: Int) = CherryV2StreakMatch(
            target = target,
            kind = kind,
            historySignature = ctx.historySignature,
            formulaIndex = BigInteger.valueOf(slot),
            expression = "candidate$slot",
            predictedDigit = 1,
            historicalStreak = streak,
        )
        val first = CherryV2StreakRegistry(
            target, kind, ctx.historySignature)
            .inspected(BigInteger.ZERO, fit(0, 3))
        val repeatedThree = first.inspected(BigInteger.ONE, fit(1, 3))
        assertEquals(first.firstByStreak, repeatedThree.firstByStreak)
        val addedFour = repeatedThree.inspected(
            BigInteger.valueOf(2), fit(2, 4))
        val foundTen = addedFour.inspected(
            BigInteger.valueOf(3), fit(3, 10))
        val repeatedTen = foundTen.inspected(
            BigInteger.valueOf(4), fit(4, 10))

        assertEquals(setOf(3, 4, 10), repeatedTen.firstByStreak.keys)
        assertEquals(BigInteger.ZERO,
            repeatedTen.firstByStreak.getValue(3).formulaIndex)
        assertEquals(BigInteger.valueOf(2),
            repeatedTen.firstByStreak.getValue(4).formulaIndex)
        assertEquals(BigInteger.valueOf(3),
            repeatedTen.firstByStreak.getValue(10).formulaIndex)
        assertEquals(10, repeatedTen.recommended()!!.historicalStreak)
        assertEquals(BigInteger.valueOf(5), repeatedTen.nextIndex)
    }

    @Test fun realEngineScansBeyondLegacy207AndContinuesWithoutLosingFirstFits() {
        val ctx = context()
        assertTrue(ctx.prior.size >= 25)
        val results = mutableListOf<CherryV2StreakRegistry>()
        val first = CherryV2TimedSearch.run(
            ctx, CherryDiscoveryKind.LONE,
            timeBudgetNanos = 4_000_000L,
            monotonicNanos = clock(),
            onCheckpoint = { results.add(it) },
            onBatchBoundary = {},
        )
        assertEquals(CherryV2TimedSearch.StopReason.TIME_LIMIT,
            first.stopReason)
        assertTrue(first.inspectedThisBatch > 0)
        assertEquals(first.cursor, results.last())
        assertTrue(first.cursor.firstByStreak.isNotEmpty())

        // An actual cross-lag candidate must reach the target 10+ tier.
        val guided = CherryV2GuidedSchedule.expressionAt(
            BigInteger.TEN)
        assertEquals("(top1+top2)", guided.structuralKey)
        val crossLagFits = CherryV2HistoricalEvaluator.evaluate(
            ctx, CherryDiscoveryKind.LONE, BigInteger.TEN, guided)
        assertTrue(crossLagFits.any {
            it.offset == 0 && it.historicalStreak >= 10
        })

        val stored = CherryV2CheckpointCodec.encode(first.cursor)
        val resumedState = requireNotNull(CherryV2CheckpointCodec.decode(
            stored, target, CherryDiscoveryKind.LONE,
            ctx.historySignature))
        assertEquals(first.cursor, resumedState)

        val continued = CherryV2TimedSearch.run(
            ctx, CherryDiscoveryKind.LONE, previous = resumedState,
            timeBudgetNanos = 15_000_000L,
            monotonicNanos = clock(),
            onBatchBoundary = {},
        )
        assertTrue("Must REALLY inspect slot 207 and beyond, not just decode it",
            continued.cursor.nextIndex > BigInteger.valueOf(207))
        assertEquals(
            first.cursor.nextIndex.add(
                BigInteger.valueOf(continued.inspectedThisBatch.toLong())),
            continued.cursor.nextIndex,
        )
        assertTrue(continued.cursor.firstByStreak.keys.containsAll(
            first.cursor.firstByStreak.keys))
        for ((streak, firstFound) in first.cursor.firstByStreak) {
            assertEquals("First-found $streak must survive a second run",
                firstFound, continued.cursor.firstByStreak[streak])
        }
        assertTrue("A real engine scan must capture the 10+ target milestone",
            continued.cursor.firstByStreak.containsKey(10))
        assertTrue("New search cannot store more than eight milestone tiers",
            continued.cursor.firstByStreak.size <= 8)
        assertTrue(continued.cursor.firstByStreak.keys.all { it in 3..10 })
        assertEquals(10, continued.cursor.recommended()!!.historicalStreak)
        assertTrue(continued.cursor.recentBehaviorKeys.size <=
            CherryV2StreakRegistry.BEHAVIOR_WINDOW)
        assertEquals(continued.cursor.recentBehaviorKeys.size,
            continued.cursor.recentBehaviorKeys.toSet().size)
        println("CHERRY_V2_CORE_GATE legacyShapes=207 " +
            "completedSlots=${continued.cursor.nextIndex} " +
            "keptExactStreaks=${continued.cursor.firstByStreak.keys.sorted()} " +
            "checkpoint=RESTORED firstFound=PRESERVED " +
            "label=SYNTHETIC_ENGINE_CONTRACT_ONLY")
    }

    @Test fun explicitThirtySecondBudgetStopsOnClockNotFormulaCount() {
        val ctx = context()
        var observedCheckpoint: CherryV2StreakRegistry? = null
        // Simulated monotonic time: every clock read advances 1ms, so a
        // 30-second budget can be verified quickly WITHOUT sleeping 30s.
        val result = CherryV2TimedSearch.run(
            ctx, CherryDiscoveryKind.WAM,
            timeBudgetNanos = CherryV2TimedSearch.DEFAULT_BUDGET_NANOS,
            monotonicNanos = clock(1_000_000L),
            onCheckpoint = { observedCheckpoint = it },
            onBatchBoundary = {},
        )
        assertEquals(30_000_000_000L,
            CherryV2TimedSearch.DEFAULT_BUDGET_NANOS)
        assertEquals(CherryV2TimedSearch.StopReason.TIME_LIMIT,
            result.stopReason)
        assertTrue(result.elapsedNanos >=
            CherryV2TimedSearch.DEFAULT_BUDGET_NANOS)
        assertTrue(result.inspectedThisBatch > 0)
        assertEquals(result.cursor, observedCheckpoint)
        assertEquals(BigInteger.valueOf(result.inspectedThisBatch.toLong()),
            result.cursor.nextIndex)
        println("CHERRY_V2_CORE_GATE timer=30s_SIMULATED_MONOTONIC " +
            "stop=TIME_LIMIT checkpoint=PERSISTABLE " +
            "label=NOT_REAL_DEVICE_WALL_CLOCK")
    }

    @Test fun cancellationSavesCompletedSlotsAndResumeNeverRestartsFromZero() {
        val ctx = context()
        var checkpoint: CherryV2StreakRegistry? = null
        var checks = 0
        try {
            CherryV2TimedSearch.run(
                ctx, CherryDiscoveryKind.BREAK,
                timeBudgetNanos = CherryV2TimedSearch.DEFAULT_BUDGET_NANOS,
                monotonicNanos = clock(),
                checkCancellation = {
                    if (++checks >= 240) {
                        throw CancellationException("user cancelled")
                    }
                },
                onCheckpoint = { checkpoint = it },
                onBatchBoundary = {},
            )
            fail("Cancellation must reach caller")
        } catch (_: CancellationException) {}
        val persisted = requireNotNull(checkpoint)
        assertTrue(persisted.nextIndex > BigInteger.ZERO)
        val decoded = requireNotNull(CherryV2CheckpointCodec.decode(
            CherryV2CheckpointCodec.encode(persisted),
            target, CherryDiscoveryKind.BREAK, ctx.historySignature))
        assertEquals(persisted, decoded)
        val continued = CherryV2TimedSearch.run(
            ctx, CherryDiscoveryKind.BREAK, previous = decoded,
            timeBudgetNanos = 2_000_000L,
            monotonicNanos = clock(),
            onBatchBoundary = {},
        )
        assertTrue(continued.cursor.nextIndex > decoded.nextIndex)
        for ((tier, selected) in decoded.firstByStreak) {
            assertEquals(selected,
                continued.cursor.firstByStreak[tier])
        }
    }

    @Test fun categoriesAndFutureTargetOutcomeCannotAlterSearchEvidence() {
        val rows = history()
        val ctx = context()
        val altered = rows.map {
            if (it.date == target.date &&
                it.session == DrawSession.MORNING) {
                it.copy(digit = "99")
            } else it
        }
        val before = CherryV2HistoricalEvaluator.prepare(rows, target)
        val after = CherryV2HistoricalEvaluator.prepare(altered, target)
        assertEquals(ctx.historySignature, before.historySignature)
        assertEquals(before.historySignature, after.historySignature)
        assertEquals(before.prior, after.prior)
        for (kind in CherryDiscoveryKind.entries) {
            val sample = CherryV2TimedSearch.run(
                before, kind,
                timeBudgetNanos = 1_500_000L,
                monotonicNanos = clock(),
                onBatchBoundary = {},
            )
            assertEquals(CherryV2TimedSearch.StopReason.TIME_LIMIT,
                sample.stopReason)
            assertTrue(sample.cursor.firstByStreak.values.all {
                it.kind == kind && it.target == target
            })
        }
        println("CHERRY_V2_CORE_GATE categories=LONE,WAM,BREAK " +
            "targetLeakage=NONE label=SYNTHETIC_ENGINE_CONTRACT_ONLY")
    }
}
