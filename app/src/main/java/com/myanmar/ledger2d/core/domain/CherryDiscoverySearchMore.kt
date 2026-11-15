package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/**
 * UI-independent Search More controller.
 *
 * Returns one unseen formula at a time. An existing stage's candidates are
 * exhausted before evaluating the next grammar stage; no previous stage is
 * rescanned. A data correction or new target invalidates the previous cursor.
 *
 * This controller intentionally exposes only representative candidates
 * retained by CherryFormulaDiscoveryEngine (at most 24 at a time). It does
 * not yet enumerate every viable expression/offset combination; expanding
 * the candidate pool is a separate, bounded engine task.
 */
data class CherryDiscoverySearchCursor(
    val kind: CherryDiscoveryKind,
    val engine: CherryDiscoverySession,
    val offeredIds: Set<String>,
    /** Newly offered on this call; null when blocked or exhausted. */
    val newCandidate: CherryDiscoveredFormula?,
    /** Best historical fit ever seen by this cursor, not a future win claim. */
    val bestCandidate: CherryDiscoveredFormula?,
    val exhausted: Boolean,
) {
    val blocked: Boolean get() = !engine.hasImmediatePreviousDraw
    val inspectedExpressions: Int get() = engine.inspectedExpressions
    val completedStages: Int get() = engine.completedStages
}

/**
 * Pure-domain continuation, no Main-thread work or background tasks created.
 * A UI integration must call this through an owned coroutine on Default.
 *
 * A candidate is offered at most once per category and unchanged history
 * signature. Cache invalidation resets only this search cursor; an already
 * accepted, separately time-locked prospective trial must never be rewritten.
 */
object CherryDiscoverySearchMore {
    fun next(
        history: List<HistoricDraw>,
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        previous: CherryDiscoverySearchCursor? = null,
        closedDates: Set<LocalDate> = emptySet(),
        checkCancellation: () -> Unit = {},
    ): CherryDiscoverySearchCursor {
        checkCancellation()
        val old = previous?.takeIf {
            it.kind == kind && it.engine.target == target
        }
        // Verify the bounded input fingerprint without advancing the grammar.
        val validated = CherryFormulaDiscoveryEngine.nextRound(
            history = history, target = target,
            previousState = old?.engine, closedDates = closedDates,
            checkCancellation = checkCancellation, advanceStage = false,
        )
        val continued = old?.takeIf {
            it.engine.historySignature == validated.historySignature &&
                validated.completedStages == it.engine.completedStages
        }
        val seen = continued?.offeredIds.orEmpty()
        val previousBest = continued?.bestCandidate

        if (!validated.hasImmediatePreviousDraw) {
            return CherryDiscoverySearchCursor(
                kind, validated, seen, null, previousBest, exhausted = false)
        }
        var session = validated
        while (true) {
            checkCancellation()
            val unseen = session.formulas.asSequence()
                .filter { it.kind == kind && it.id !in seen }
                .sortedWith(compareByDescending<CherryDiscoveredFormula> {
                    it.historicalStreak
                }.thenBy { it.discoveredAtStage }.thenBy { it.id })
                .firstOrNull()
            if (unseen != null) {
                val best = listOfNotNull(previousBest, unseen).maxWithOrNull(
                    compareBy<CherryDiscoveredFormula> {
                        it.historicalStreak
                    }.thenBy { -it.discoveredAtStage }
                )
                return CherryDiscoverySearchCursor(
                    kind, session, seen + unseen.id, unseen, best,
                    exhausted = false,
                )
            }
            if (session.isComplete) {
                return CherryDiscoverySearchCursor(
                    kind, session, seen, null, previousBest, exhausted = true)
            }
            // Consume exactly one new grammar level; never revisit a stage
            // when the caller passes this cursor back on the next request.
            session = CherryFormulaDiscoveryEngine.nextRound(
                history = history, target = target,
                previousState = session, closedDates = closedDates,
                checkCancellation = checkCancellation,
            )
            if (!session.hasImmediatePreviousDraw) {
                return CherryDiscoverySearchCursor(
                    kind, session, seen, null, previousBest, exhausted = false)
            }
        }
    }
}
