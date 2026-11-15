package com.myanmar.ledger2d.core.domain

/**
 * Experimental, UI-free V2 search budget controller. This object never
 * launches a coroutine, schedules background work, changes selections or
 * publishes results. A future explicit user button must call it off Main.
 */
internal object CherryV2TimedSearch {
    const val DEFAULT_BUDGET_NANOS = 30_000_000_000L
    const val MAX_EXPRESSIONS_PER_BATCH = 100_000
    private const val CHECKPOINT_EVERY = 64

    enum class StopReason { TIME_LIMIT, SAFETY_CAP, BLOCKED }

    data class Result(
        val cursor: CherryV2StreakRegistry,
        val inspectedThisBatch: Int,
        val newlyRetainedStreakLengths: Set<Int>,
        val elapsedNanos: Long,
        val stopReason: StopReason,
    )

    private class DeadlineReached : RuntimeException()

    /**
     * Every successful expression index is committed ONCE. A deadline
     * arriving inside decoding/evaluation discards only that incomplete
     * expression, so Continue retries its exact same index safely.
     *
     * onCheckpoint is called after each 64 finished expressions and on
     * return; the integration caller may persist it atomically. External
     * cancellation propagates without writing a partial expression.
     */
    fun run(
        context: CherryV2HistoricalEvaluator.Context,
        kind: CherryDiscoveryKind,
        previous: CherryV2StreakRegistry? = null,
        timeBudgetNanos: Long = DEFAULT_BUDGET_NANOS,
        monotonicNanos: () -> Long = System::nanoTime,
        checkCancellation: () -> Unit = {},
        onCheckpoint: (CherryV2StreakRegistry) -> Unit = {},
    ): Result {
        require(timeBudgetNanos > 0L)
        require(previous == null || (
            previous.target == context.target &&
                previous.kind == kind &&
                previous.historySignature == context.historySignature
        )) { "Wrong target/category or corrected history: do not resume stale search" }

        val started = monotonicNanos()
        var cursor = previous ?: CherryV2StreakRegistry(
            context.target, kind, context.historySignature)
        if (context.blocked) {
            return Result(cursor, 0, emptySet(), 0L, StopReason.BLOCKED)
        }

        fun elapsed(): Long = (monotonicNanos() - started).coerceAtLeast(0L)
        fun checkBudget() {
            checkCancellation()
            if (elapsed() >= timeBudgetNanos) throw DeadlineReached()
        }

        val added = mutableSetOf<Int>()
        var inspected = 0
        while (inspected < MAX_EXPRESSIONS_PER_BATCH) {
            try {
                checkBudget()
                val index = cursor.nextIndex
                val expression = CherryV2ExpandableGrammar.expressionAt(
                    index, ::checkBudget)
                val matches = CherryV2HistoricalEvaluator.evaluate(
                    context, kind, index, expression, ::checkBudget)
                checkBudget() // no partial expression may cross the deadline
                val advanced = cursor.inspectedBatch(index, matches)
                added.addAll(advanced.firstByStreak.keys - cursor.firstByStreak.keys)
                cursor = advanced
                inspected++
                if (inspected % CHECKPOINT_EVERY == 0) onCheckpoint(cursor)
            } catch (_: DeadlineReached) {
                onCheckpoint(cursor)
                return Result(cursor, inspected, added.toSet(), elapsed(),
                    StopReason.TIME_LIMIT)
            }
        }
        onCheckpoint(cursor)
        return Result(cursor, inspected, added.toSet(), elapsed(),
            StopReason.SAFETY_CAP)
    }
}
