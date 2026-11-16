package com.myanmar.ledger2d.core.domain

import java.util.concurrent.CancellationException

/**
 * Experimental UI-free search controller. Search only runs from an explicit
 * invocation. A future Android service must call this off the UI thread and
 * persist complete checkpoints; no hidden worker is created here.
 */
internal object CherryV2TimedSearch {
    const val DEFAULT_BUDGET_NANOS = 30_000_000_000L
    private const val CHECKPOINT_EVERY = 512
    // Avoid rewriting a large compact fingerprint window 60 times per search.
    // Always flush on timeout and cancellation.
    private const val CHECKPOINT_MIN_INTERVAL_NANOS = 3_000_000_000L

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

    /** Stable bounded-size semantic fingerprint for persisted exact-window traces.
     * Two independent mixed 64-bit accumulators make incidental collisions
     * extremely unlikely; behavior equivalence remains evidence-window only.
     */
    private fun behaviorFingerprint(value: String): String {
        var first = 0x6a09e667f3bcc909L
        var second = 0x3c6ef372fe94f82bL
        for (char in value) {
            first = (first xor char.code.toLong()) * 1099511628211L
            second = java.lang.Long.rotateLeft(
                second xor (char.code.toLong() + 0x9e37L), 7
            ) * 1099511628211L
        }
        return java.lang.Long.toHexString(first).padStart(16, '0') +
            java.lang.Long.toHexString(second).padStart(16, '0')
    }

    /**
     * One search window runs until the monotonic *time* budget expires, NOT
     * until a fabricated formula count (the old 100,000 cap) is reached.
     * The cursor advances ONLY after a complete expression/offset evaluation.
     * If time expires mid-expression, Continue safely retries that expression.
     *
     * The bounded recent-behavior queue is included in snapshots; no global
     * unlimited in-memory set is retained. Periodic checkpoint writes are
     * throttled to avoid saturating flash storage on fast phones; checkpoint
     * is always written at timeout or cancellation. Caller is responsible for
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
        var lastPersistedAt = started
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
                val expression = CherryV2GuidedSchedule.expressionAt(
                    index, ::checkBudget)
                val trace = CherryV2HistoricalEvaluator.trace(
                    context, expression, ::checkBudget, candidateKind = kind)

                val behaviorKey = trace?.let { behaviorFingerprint(it.behaviorKey) }
                val isDuplicate = behaviorKey != null && seen.contains(behaviorKey)
                val matches = when {
                    trace == null || isDuplicate -> emptyList()
                    else -> CherryV2HistoricalEvaluator.evaluateTrace(
                        context, kind, index, trace, ::checkBudget)
                }
                // No partial formula/trace is ever committed after timeout.
                checkBudget()
                val advanced = cursor.inspectedBatch(index, matches)
                if (trace == null) {
                    unsupported++
                } else if (isDuplicate) {
                    duplicates++
                } else {
                    uniqueScored++
                    remember(requireNotNull(behaviorKey))
                }
                newlyRetained.addAll(
                    advanced.firstByStreak.keys - cursor.firstByStreak.keys)
                cursor = advanced
                inspected++

                if (inspected % CHECKPOINT_EVERY == 0) {
                    cursor = snapshot()
                    // Let cooperative Android workers yield even when a
                    // durable write is not yet needed.
                    onBatchBoundary()
                    val now = monotonicNanos()
                    if ((now - lastPersistedAt) >= CHECKPOINT_MIN_INTERVAL_NANOS) {
                        onCheckpoint(cursor)
                        lastPersistedAt = now
                    }
                }
            } catch (_: DeadlineReached) {
                cursor = snapshot()
                onCheckpoint(cursor)
                return Result(
                    cursor, inspected, newlyRetained.toSet(), elapsed(),
                    StopReason.TIME_LIMIT, uniqueScored, duplicates, unsupported,
                )
            } catch (cancelled: CancellationException) {
                // Preserve all fully completed formulas even if cancellation
                // comes before the next 64-expression periodic checkpoint.
                // The currently interrupted expression was never committed.
                cursor = snapshot()
                onCheckpoint(cursor)
                throw cancelled
            }
        }
    }
}
