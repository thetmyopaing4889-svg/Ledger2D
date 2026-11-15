package com.myanmar.ledger2d.core.domain

/**
 * Experimental UI-free search controller. Search only runs from an explicit
 * invocation. A future Android service must call this off the UI thread and
 * persist complete checkpoints; no hidden worker is created here.
 */
internal object CherryV2TimedSearch {
    const val DEFAULT_BUDGET_NANOS = 30_000_000_000L
    private const val CHECKPOINT_EVERY = 64

    enum class StopReason { TIME_LIMIT, BLOCKED }

    data class Result(
        val cursor: CherryV2StreakRegistry,
        val inspectedThisBatch: Int,
        val newlyRetainedStreakLengths: Set<Int>,
        val elapsedNanos: Long,
        val stopReason: StopReason,
        val distinctBehaviorsEvaluated: Int = 0,
        val equivalentBehaviorsSkipped: Int = 0,
        val unscorableExpressionsSkipped: Int = 0,
    )

    private class DeadlineReached : RuntimeException()

    /**
     * One search window runs until the monotonic *time* budget expires, NOT
     * until a fabricated formula count (the old 100,000 cap) is reached.
     * The cursor advances ONLY after a complete expression/offset evaluation.
     * If time expires mid-expression, Continue safely retries that expression.
     *
     * The bounded recent-behavior queue is included in snapshots; no global
     * unlimited in-memory set is retained. onCheckpoint is called after each
     * completed 64 expressions and at timeout. The caller is responsible for
     * atomic durability and serializing simultaneous sessions.
     *
     * onBatchBoundary is a cooperative hook for an Android integration worker
     * to yield/throttle; never run this blocking search on Compose/Main.
     */
    fun run(
        context: CherryV2HistoricalEvaluator.Context,
        kind: CherryDiscoveryKind,
        previous: CherryV2StreakRegistry? = null,
        timeBudgetNanos: Long = DEFAULT_BUDGET_NANOS,
        monotonicNanos: () -> Long = System::nanoTime,
        checkCancellation: () -> Unit = {},
        onCheckpoint: (CherryV2StreakRegistry) -> Unit = {},
        onBatchBoundary: () -> Unit = { Thread.yield() },
    ): Result {
        require(timeBudgetNanos > 0L)
        require(previous == null || (
            previous.target == context.target &&
                previous.kind == kind &&
                previous.historySignature == context.historySignature &&
                previous.grammarVersion == CherryV2ExpandableGrammar.VERSION
        )) { "Wrong target/category, grammar or corrected history: stale search" }

        val started = monotonicNanos()
        var cursor = previous ?: CherryV2StreakRegistry(
            context.target, kind, context.historySignature)
        if (context.blocked) {
            return Result(cursor, 0, emptySet(), 0L, StopReason.BLOCKED)
        }

        // A fixed-size LRU-like window, not a set that grows forever. The
        // cursor means old formula indices are never examined again anyway.
        val recent = ArrayDeque<String>()
        val seen = HashSet<String>()
        for (key in cursor.recentBehaviorKeys) {
            if (seen.add(key)) recent.addLast(key)
        }

        fun remember(key: String) {
            if (!seen.add(key)) return
            recent.addLast(key)
            if (recent.size > CherryV2StreakRegistry.BEHAVIOR_WINDOW) {
                seen.remove(recent.removeFirst())
            }
        }

        fun snapshot(): CherryV2StreakRegistry =
            cursor.withRecentBehaviors(recent.toList())

        fun elapsed(): Long = (monotonicNanos() - started).coerceAtLeast(0L)
        fun checkBudget() {
            checkCancellation()
            if (elapsed() >= timeBudgetNanos) throw DeadlineReached()
        }

        val newlyRetained = mutableSetOf<Int>()
        var inspected = 0
        var uniqueScored = 0
        var duplicates = 0
        var unsupported = 0

        while (true) {
            try {
                checkBudget()
                val index = cursor.nextIndex
                val expression = CherryV2ExpandableGrammar.expressionAt(
                    index, ::checkBudget)
                val trace = CherryV2HistoricalEvaluator.trace(
                    context, expression, ::checkBudget, candidateKind = kind)

                val matches = when {
                    trace == null -> emptyList()
                    seen.contains(trace.behaviorKey) -> emptyList()
                    else -> CherryV2HistoricalEvaluator.evaluateTrace(
                        context, kind, index, trace, ::checkBudget)
                }
                // No partial formula/trace is ever committed after timeout.
                checkBudget()
                val advanced = cursor.inspectedBatch(index, matches)
                if (trace == null) {
                    unsupported++
                } else if (seen.contains(trace.behaviorKey)) {
                    duplicates++
                } else {
                    uniqueScored++
                    remember(trace.behaviorKey)
                }
                newlyRetained.addAll(
                    advanced.firstByStreak.keys - cursor.firstByStreak.keys)
                cursor = advanced
                inspected++

                if (inspected % CHECKPOINT_EVERY == 0) {
                    cursor = snapshot()
                    onCheckpoint(cursor)
                    onBatchBoundary()
                }
            } catch (_: DeadlineReached) {
                cursor = snapshot()
                onCheckpoint(cursor)
                return Result(
                    cursor, inspected, newlyRetained.toSet(), elapsed(),
                    StopReason.TIME_LIMIT, uniqueScored, duplicates, unsupported,
                )
            }
        }
    }
}
