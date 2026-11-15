package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigInteger
import java.security.MessageDigest
import java.time.LocalDate

/**
 * V2 research-only evaluator. No results at/after [target] are ever inputs.
 * Evaluates a bounded contiguous AM/PM suffix; unknown missing sessions
 * stop the suffix rather than being silently bridged.
 */
internal object CherryV2HistoricalEvaluator {
    const val MAX_LAG = 12
    const val MAX_SCORED = CherryFormulaDiscoveryEngine.SEARCH_DRAWS

    internal data class Context(
        val target: DrawIdentity,
        val closedDates: Set<LocalDate>,
        /** Confirmed slots, newest first, contiguous back from target. */
        val prior: List<HistoricDraw>,
        val historySignature: String,
        val validTarget: Boolean,
    ) {
        val blocked: Boolean get() = !validTarget || prior.isEmpty()
    }

    private fun previous(target: DrawIdentity, closed: Set<LocalDate>): DrawIdentity {
        if (target.session == DrawSession.EVENING) {
            return DrawIdentity(target.date, DrawSession.MORNING)
        }
        var date = target.date.minusDays(1)
        while (!DrawSchedule.isWorkingDate(date, closed)) date = date.minusDays(1)
        return DrawIdentity(date, DrawSession.EVENING)
    }

    /**
     * Input may be unsorted and contain source corrections. Existing
     * precedence-aware normalizer removes future/invalid/closed rows and
     * supplies at most MAX_SCORED + MAX_LAG slots.
     */
    fun prepare(
        history: List<HistoricDraw>,
        target: DrawIdentity,
        closedDates: Set<LocalDate> = emptySet(),
        checkCancellation: () -> Unit = {},
    ): Context {
        checkCancellation()
        val working = DrawSchedule.isWorkingDate(target.date, closedDates)
        val rows = if (working) CherryDiscoveryHistoryNormalizer.newestBefore(
            history, target, closedDates, MAX_SCORED + MAX_LAG, checkCancellation,
        ) else emptyList()
        val lookup = rows.associateBy { DrawIdentity(it.date, it.session) }
        val contiguous = ArrayList<HistoricDraw>(rows.size)
        if (working) {
            var cursor = previous(target, closedDates)
            while (contiguous.size < MAX_SCORED + MAX_LAG) {
                checkCancellation()
                val row = lookup[cursor] ?: break
                contiguous.add(row)
                cursor = previous(cursor, closedDates)
            }
        }
        val relevantOldest = contiguous.lastOrNull()?.date ?: target.date
        val relevantClosures = closedDates.asSequence()
            .filter { !it.isBefore(relevantOldest) && !it.isAfter(target.date) }
            .sorted().joinToString(",")
        val evidence = buildString {
            append("V2|").append(target.date).append('|')
                .append(target.session.name).append('|')
                .append(working).append('|').append(relevantClosures)
            for (row in contiguous) {
                append('|').append(row.date).append(':').append(row.session.name)
                    .append('=').append(row.digit).append('@').append(row.source.name)
            }
        }
        val signature = MessageDigest.getInstance("SHA-256")
            .digest(evidence.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        checkCancellation()
        return Context(target, closedDates.toSet(), contiguous, signature, working)
    }

    private fun requiredLag(expression: CherryV2Expression): BigInteger =
        when (expression) {
            is CherryV2Expression.Feature -> expression.lag
            is CherryV2Expression.Complement -> requiredLag(expression.inner)
            is CherryV2Expression.Binary -> maxOf(
                requiredLag(expression.left), requiredLag(expression.right))
        }

    /**
     * All historical windows and the next-target base are evaluated ONCE per
     * candidate, rather than recomputed for each of the ten offsets.
     * Only prior completed draws are provided at every historical position.
     */
    data class Trace(
        val expressionKey: String,
        val currentBase: Int,
        val historicalBases: List<Int>,
        /**
         * Exact in-window behavioral equivalence up to a constant mod-10
         * shift. All 10 offsets are tested, so shifted expressions cannot
         * offer a different candidate on this same confirmed history.
         * Includes the next-target output; not a claim of algebraic identity.
         */
        val behaviorKey: String,
    )

    fun trace(
        context: Context,
        expression: CherryV2Expression,
        checkCancellation: () -> Unit = {},
        candidateKind: CherryDiscoveryKind? = null,
    ): Trace? {
        checkCancellation()
        if (context.blocked ||
            requiredLag(expression) > BigInteger.valueOf(MAX_LAG.toLong())) return null
        val digits = context.prior.map { it.digit }
        val currentBase = expression.evaluate(digits) ?: return null
        val historicalBase = ArrayList<Int>(minOf(MAX_SCORED, digits.size))
        for (index in 0 until minOf(MAX_SCORED, digits.size)) {
            if ((index and 15) == 0) checkCancellation()
            // Exclude the scored draw itself: index+1 and older only.
            val value = expression.evaluate(digits.subList(index + 1, digits.size))
                ?: break
            historicalBase.add(value)
            if (candidateKind != null &&
                historicalBase.size == CherryFormulaDiscoveryEngine.MIN_STREAK) {
                // A qualifying consecutive streak MUST match its first
                // three sessions for one unchanged offset. If none does,
                // spending time on another ~197 history slots is pointless.
                // This prefilter uses only already-settled, pre-target draws.
                val canReachMinimum = (0..9).any { offset ->
                    (0 until CherryFormulaDiscoveryEngine.MIN_STREAK).all { i ->
                        CherryDiscoveryWalkForward.matches(
                            candidateKind,
                            Math.floorMod(historicalBase[i] + offset, 10),
                            context.prior[i].digit,
                        )
                    }
                }
                if (!canReachMinimum) break
            }
        }
        val key = buildString(historicalBase.size + 16) {
            append(historicalBase.size).append(':')
            // A constant shift in the expression output only changes the
            // offset labels, never the set of offset-adjusted predictions.
            for (value in historicalBase) {
                append(Math.floorMod(value - currentBase, 10))
            }
        }
        checkCancellation()
        return Trace(expression.structuralKey, currentBase, historicalBase, key)
    }

    /**
     * Evaluate all ten FIXED offsets against a trace. A first candidate may
     * directly fit 10+ consecutive historical sessions; no required ladder.
     * These are in-sample digit/category fits, not prospective 2D wins.
     */
    fun evaluateTrace(
        context: Context,
        kind: CherryDiscoveryKind,
        formulaIndex: BigInteger,
        trace: Trace,
        checkCancellation: () -> Unit = {},
    ): List<CherryV2StreakMatch> {
        require(formulaIndex.signum() >= 0)
        checkCancellation()
        val found = ArrayList<CherryV2StreakMatch>(10)
        for (offset in 0..9) {
            checkCancellation()
            var streak = 0
            for (i in trace.historicalBases.indices) {
                if ((i and 31) == 0) checkCancellation()
                val output = Math.floorMod(trace.historicalBases[i] + offset, 10)
                if (!CherryDiscoveryWalkForward.matches(
                    kind, output, context.prior[i].digit)) break
                streak++
            }
            if (streak < CherryFormulaDiscoveryEngine.MIN_STREAK) continue
            found.add(CherryV2StreakMatch(
                target = context.target,
                kind = kind,
                historySignature = context.historySignature,
                formulaIndex = formulaIndex,
                expression = trace.expressionKey,
                offset = offset,
                predictedDigit = Math.floorMod(trace.currentBase + offset, 10),
                historicalStreak = streak,
            ))
        }
        return found
    }

    /** Compatibility entry point for direct scoring and existing tests. */
    fun evaluate(
        context: Context,
        kind: CherryDiscoveryKind,
        formulaIndex: BigInteger,
        expression: CherryV2Expression,
        checkCancellation: () -> Unit = {},
    ): List<CherryV2StreakMatch> {
        val values = trace(context, expression, checkCancellation) ?: return emptyList()
        return evaluateTrace(context, kind, formulaIndex, values, checkCancellation)
    }
}
