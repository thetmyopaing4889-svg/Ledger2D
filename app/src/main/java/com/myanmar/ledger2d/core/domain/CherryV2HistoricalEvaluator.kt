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
     * One expression is evaluated against all ten constant mod-10 offsets.
     * Each offset is fixed across the ENTIRE streak; it is never retuned per
     * historical draw. A 10+ match can be returned on the very first call.
     *
     * The returned matches are IN-SAMPLE HISTORICAL_FIT evidence, not
     * forward-locked accuracy or exact two-digit predictions.
     */
    fun evaluate(
        context: Context,
        kind: CherryDiscoveryKind,
        formulaIndex: BigInteger,
        expression: CherryV2Expression,
        checkCancellation: () -> Unit = {},
    ): List<CherryV2StreakMatch> {
        require(formulaIndex.signum() >= 0)
        checkCancellation()
        if (context.blocked ||
            requiredLag(expression) > BigInteger.valueOf(MAX_LAG.toLong())) return emptyList()

        val digits = context.prior.map { it.digit }
        val currentBase = expression.evaluate(digits) ?: return emptyList()
        val historicalBase = ArrayList<Int>(minOf(MAX_SCORED, digits.size))
        for (index in 0 until minOf(MAX_SCORED, digits.size)) {
            if ((index and 15) == 0) checkCancellation()
            // The draw being scored is NEVER passed to its own formula.
            val value = expression.evaluate(digits.subList(index + 1, digits.size))
                ?: break
            historicalBase.add(value)
        }
        val found = ArrayList<CherryV2StreakMatch>(10)
        for (offset in 0..9) {
            checkCancellation()
            var streak = 0
            for (i in historicalBase.indices) {
                if ((i and 31) == 0) checkCancellation()
                val output = Math.floorMod(historicalBase[i] + offset, 10)
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
                expression = expression.structuralKey,
                offset = offset,
                predictedDigit = Math.floorMod(currentBase + offset, 10),
                historicalStreak = streak,
            ))
        }
        return found
    }
}
