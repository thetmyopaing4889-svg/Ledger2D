package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/**
 * Read-only, time-ordered replay of a SINGLE previously selected formula.
 *
 * An expression and its offset are frozen at the selection target. No
 * searching, retuning, Champion promotion or persistence is performed while
 * observing later, known results. Every replay target is evaluated using
 * only history strictly BEFORE that target (never its own actual outcome).
 *
 * These outcomes are HISTORICAL HELD-OUT REPLAY, NOT genuine predictions
 * timestamp-locked before publication. A useful control for overfitting,
 * never proof of a predictive edge over chance.
 */
internal object CherryV2ForwardValidation {
    enum class Outcome { HIT, MISS, ABSTAIN }

    data class Trial(
        val target: DrawIdentity,
        val predictedDigit: Int?,
        val actualPair: String,
        val outcome: Outcome,
    )

    data class Report(
        val formulaId: String,
        val category: CherryDiscoveryKind,
        val trainingTarget: DrawIdentity,
        val checked: Int,
        val hits: Int,
        val misses: Int,
        val abstentions: Int,
        val trials: List<Trial>,
    ) {
        val hitRate: Double? get() =
            if (checked > 0) hits.toDouble() / checked else null

        /** Idealized uniform random 2D digit baseline; not empirical edge. */
        val uniformChanceReference: Double get() = when (category) {
            CherryDiscoveryKind.LONE -> 0.19
            CherryDiscoveryKind.WAM -> 0.36
            CherryDiscoveryKind.BREAK -> 0.10
        }
        val evidenceLabel: String get() = "HOLDOUT_REPLAY_NOT_PROSPECTIVE"
    }

    private fun isLater(a: DrawIdentity, b: DrawIdentity): Boolean =
        a.date > b.date || (a.date == b.date &&
            a.session.ordinal > b.session.ordinal)

    fun replay(
        frozen: CherryV2StreakMatch,
        fullArchive: List<HistoricDraw>,
        heldOutActuals: List<HistoricDraw>,
        closedDates: Set<LocalDate> = emptySet(),
        checkCancellation: () -> Unit = {},
    ): Report {
        checkCancellation()
        // Prevent accidental reuse of a schedule index from another version.
        require(frozen.grammarVersion == CherryV2GuidedSchedule.VERSION)
        require(heldOutActuals.size <= 256) { "Bound audit window to 256 draws" }
        val normalized = heldOutActuals
            .map { DrawIdentity(it.date, it.session) to it }
        require(normalized.all { (identity, actual) ->
            isLater(identity, frozen.target) &&
                validHistoric2D(actual.digit) &&
                DrawSchedule.isWorkingDate(actual.date, closedDates)
        }) { "Holdout must contain only valid later completed draw slots" }
        require(normalized.map { it.first }.distinct().size ==
            normalized.size) { "Duplicate holdout result identity" }

        val formula = CherryV2GuidedSchedule.expressionAt(
            frozen.formulaIndex, checkCancellation)
        require(formula.structuralKey == frozen.expression) {
            "Formula changed since selection: cannot audit a different tree"
        }

        val trials = ArrayList<Trial>(heldOutActuals.size)
        for ((identity, actual) in normalized.sortedWith(
            compareBy<Pair<DrawIdentity, HistoricDraw>>(
                { it.first.date }, { it.first.session.ordinal })
        )) {
            checkCancellation()
            val context = CherryV2HistoricalEvaluator.prepare(
                fullArchive, identity, closedDates, checkCancellation)
            val base = if (!context.blocked) {
                // No target result is supplied to the formula.
                formula.evaluate(context.digits)
            } else null
            val predicted = base?.let { Math.floorMod(it + frozen.offset, 10) }
            val outcome = when {
                predicted == null -> Outcome.ABSTAIN
                CherryDiscoveryWalkForward.matches(
                    frozen.kind, predicted, actual.digit) -> Outcome.HIT
                else -> Outcome.MISS
            }
            trials.add(Trial(identity, predicted, actual.digit, outcome))
        }
        val hits = trials.count { it.outcome == Outcome.HIT }
        val misses = trials.count { it.outcome == Outcome.MISS }
        return Report(
            formulaId = frozen.formulaId,
            category = frozen.kind,
            trainingTarget = frozen.target,
            checked = hits + misses,
            hits = hits,
            misses = misses,
            abstentions = trials.size - hits - misses,
            trials = trials,
        )
    }
}
