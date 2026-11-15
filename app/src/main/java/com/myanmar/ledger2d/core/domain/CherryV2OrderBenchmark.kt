package com.myanmar.ledger2d.core.domain

import java.math.BigInteger

/**
 * Explicit, read-only V2 discovery comparison. This NEVER runs on app load,
 * changes Champion or persists a result. Both modes receive the SAME frozen
 * pre-target history, category, candidate count and monotonic time budget.
 *
 * Reports in-sample structural diversity / retrospective streaks only.
 * Similar or better results here do NOT imply better future hit probability.
 */
internal object CherryV2OrderBenchmark {
    const val MAX_CANDIDATES = 2048
    const val DEFAULT_CANDIDATES = 256
    const val DEFAULT_BUDGET_NANOS = 3_000_000_000L

    enum class Order { RAW, GUIDED }

    data class Metrics(
        val order: Order,
        val inspected: Int,
        val scorable: Int,
        val distinctBehaviorTraces: Int,
        val duplicateBehaviorTraces: Int,
        val firstByStreak: Map<Int, Int>,
        val firstAtLeastTen: Int?,
        val elapsedNanos: Long,
        val budgetExpired: Boolean,
    ) {
        val longestHistoricalStreak: Int get() = firstByStreak.keys.maxOrNull() ?: 0
    }

    data class Comparison(
        val raw: Metrics,
        val guided: Metrics,
        val category: CherryDiscoveryKind,
        val historySignature: String,
    )

    fun compare(
        context: CherryV2HistoricalEvaluator.Context,
        category: CherryDiscoveryKind,
        candidatesPerMode: Int = DEFAULT_CANDIDATES,
        budgetPerModeNanos: Long = DEFAULT_BUDGET_NANOS,
        nanoTime: () -> Long = System::nanoTime,
        checkCancellation: () -> Unit = {},
    ): Comparison {
        require(candidatesPerMode in 1..MAX_CANDIDATES)
        require(budgetPerModeNanos > 0)
        checkCancellation()
        val raw = scan(
            Order.RAW, context, category, candidatesPerMode,
            budgetPerModeNanos, nanoTime, checkCancellation,
        )
        val guided = scan(
            Order.GUIDED, context, category, candidatesPerMode,
            budgetPerModeNanos, nanoTime, checkCancellation,
        )
        return Comparison(raw, guided, category, context.historySignature)
    }

    private fun scan(
        order: Order,
        context: CherryV2HistoricalEvaluator.Context,
        category: CherryDiscoveryKind,
        limit: Int,
        budgetNanos: Long,
        nanoTime: () -> Long,
        checkCancellation: () -> Unit,
    ): Metrics {
        val started = nanoTime()
        var inspected = 0
        var scorable = 0
        var duplicates = 0
        var distinct = 0
        var firstTen: Int? = null
        val byStreak = sortedMapOf<Int, Int>()

        // Same bounded semantic-dedup policy as current V2 search. The
        // oldest behavior fingerprints may be revisited after eviction.
        val seen = LinkedHashSet<String>()
        while (!context.blocked && inspected < limit) {
            checkCancellation()
            if ((nanoTime() - started).coerceAtLeast(0L) >= budgetNanos) break
            val index = BigInteger.valueOf(inspected.toLong())
            val expression = when (order) {
                Order.RAW -> CherryV2ExpandableGrammar.expressionAt(
                    index, checkCancellation)
                Order.GUIDED -> CherryV2GuidedSchedule.expressionAt(
                    index, checkCancellation)
            }
            val trace = CherryV2HistoricalEvaluator.trace(
                context, expression, checkCancellation, category)
            // A candidate is committed only after all its computations.
            if (trace != null) {
                scorable++
                if (trace.behaviorKey in seen) {
                    duplicates++
                } else {
                    val fits = CherryV2HistoricalEvaluator.evaluateTrace(
                        context, category, index, trace, checkCancellation)
                    distinct++
                    for (fit in fits) {
                        byStreak.putIfAbsent(fit.historicalStreak, inspected)
                        if (fit.historicalStreak >= 10 && firstTen == null) {
                            firstTen = inspected
                        }
                    }
                    seen.add(trace.behaviorKey)
                    if (seen.size > CherryV2StreakRegistry.BEHAVIOR_WINDOW) {
                        val oldest = seen.iterator()
                        oldest.next()
                        oldest.remove()
                    }
                }
            }
            inspected++
        }
        val elapsed = (nanoTime() - started).coerceAtLeast(0L)
        return Metrics(
            order = order,
            inspected = inspected,
            scorable = scorable,
            distinctBehaviorTraces = distinct,
            duplicateBehaviorTraces = duplicates,
            firstByStreak = byStreak.toMap(),
            firstAtLeastTen = firstTen,
            elapsedNanos = elapsed,
            budgetExpired = elapsed >= budgetNanos,
        )
    }
}
