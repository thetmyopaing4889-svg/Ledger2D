package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigInteger
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * V3.2 engine-only acceptance: indexed primitives and 3-streak rejection
 * must preserve the exact pre-target formula evidence of the old evaluator.
 */
class CherryV32SearchOptimizationTest {
    private val days = generateSequence(LocalDate.of(2026, 1, 5)) {
        it.plusDays(1)
    }.filter { DrawSchedule.isWeekday(it) }.take(110).toList()

    private val draws = (0 until 210).map { i ->
        val top = (i * 7 + (i / 9) * 3 + i * i % 7) % 10
        val tail = (i * 3 + i / 4 + (i * i % 11)) % 10
        HistoricDraw(
            days[i / 2],
            if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
            "$top$tail", HistoricSource.WINNER,
        )
    }

    private val target = DrawIdentity(days[105], DrawSession.MORNING)
    private val context = CherryV2HistoricalEvaluator.prepare(draws, target)

    private fun legacyValues(expression: CherryV2Expression): Pair<Int, List<Int>>? {
        val input = context.digits
        val current = expression.evaluate(input) ?: return null
        val values = ArrayList<Int>(minOf(200, input.size))
        for (index in 0 until minOf(200, input.size)) {
            val result = expression.evaluate(input.subList(index + 1, input.size))
                ?: break
            values.add(result)
        }
        return current to values
    }

    @Test fun indexedEvaluationIsIdenticalToLegacyForAllFourGeneratorLanes() {
        assertEquals(203, context.prior.size)
        val started = System.nanoTime()
        for (index in 0L until 800L) {
            val expression = CherryV2GuidedSchedule.expressionAt(BigInteger.valueOf(index))
            val original = legacyValues(expression)
            val optimized = CherryV2HistoricalEvaluator.trace(context, expression)
            assertEquals("Current result of candidate $index",
                original?.first, optimized?.currentBase)
            assertEquals("Historical trace of candidate $index",
                original?.second, optimized?.historicalBases)
        }
        println("CHERRY_V32_TRACE_AUDIT identicalSlots=800 elapsedMs=" +
            (System.nanoTime() - started) / 1_000_000 +
            " categories=LONE,WAM,BREAK result=EXACT_HISTORY_MATCH")
    }

    @Test fun belowThreeFastRejectionNeverDropsAQualifyingFormulaForAnyKind() {
        for (kind in CherryDiscoveryKind.entries) {
            var discarded = 0
            var retained = 0
            for (index in 0L until 1200L) {
                val slot = BigInteger.valueOf(index)
                val expression = CherryV2GuidedSchedule.expressionAt(slot)
                val ordinary = CherryV2HistoricalEvaluator.trace(
                    context, expression, candidateKind = kind)
                val quick = CherryV2HistoricalEvaluator.trace(
                    context, expression, candidateKind = kind,
                    rejectBelowMinimum = true)
                val matches = ordinary?.let {
                    CherryV2HistoricalEvaluator.evaluateTrace(context, kind, slot, it)
                }.orEmpty()
                if (matches.isEmpty()) {
                    assertNull("Candidate $index was not eligible for $kind", quick)
                    discarded++
                } else {
                    assertEquals("Candidate $index lost a qualifying $kind fit",
                        ordinary, quick)
                    val quickMatches = CherryV2HistoricalEvaluator.evaluateTrace(
                        context, kind, slot, requireNotNull(quick))
                    assertEquals("Candidate $index changed the saved $kind matches",
                        matches, quickMatches)
                    retained++
                }
            }
            println("CHERRY_V32_TRACE_AUDIT category=$kind " +
                "checked=1200 shortCircuited=$discarded eligible=$retained " +
                "retainedMatches=IDENTICAL")
            assertEquals(1200, discarded + retained)
        }
    }

    @Test fun indexedEvaluationReducesAllocationsAndReportsControlledReplayTiming() {
        val expressions = (0L until 1000L).map { index ->
            CherryV2GuidedSchedule.expressionAt(BigInteger.valueOf(index))
        }
        // Warm up both code paths on the same bounded historical sample.
        expressions.take(120).forEach {
            legacyValues(it)
            CherryV2HistoricalEvaluator.trace(context, it)
        }
        val oldStarted = System.nanoTime()
        val old = expressions.map { legacyValues(it) }
        val oldElapsed = System.nanoTime() - oldStarted
        val newStarted = System.nanoTime()
        val newer = expressions.map {
            CherryV2HistoricalEvaluator.trace(context, it)
        }
        val newElapsed = System.nanoTime() - newStarted
        for (i in expressions.indices) {
            assertEquals(old[i]?.first, newer[i]?.currentBase)
            assertEquals(old[i]?.second, newer[i]?.historicalBases)
        }
        println("CHERRY_V32_TRACE_BENCH formulaSlots=1000 " +
            "oldNs=$oldElapsed indexedNs=$newElapsed " +
            "label=JVM_MICROBENCH_NOT_ANDROID_DEVICE")
    }
}
