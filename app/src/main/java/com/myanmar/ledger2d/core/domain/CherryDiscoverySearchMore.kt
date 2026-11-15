package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/**
 * UI-independent, bounded-memory Search More cursor.
 *
 * One fresh viable expression/offset is offered on each request. The cursor
 * iterates all 207 grammar shapes and per-category result-derived offsets,
 * rather than only the 24 representative leaderboard formulas. Previously
 * seen IDs and the strongest historical fit survive continuation.
 *
 * A correction to the relevant history window invalidates this cursor.
 * These are historical digit-category fits, NOT exact 2D predictions.
 */
data class CherryDiscoverySearchCursor(
    val kind: CherryDiscoveryKind,
    val engine: CherryDiscoverySession,
    val offeredIds: Set<String>,
    /** Newly offered on this call; null when blocked or exhausted. */
    val newCandidate: CherryDiscoveredFormula?,
    /** Best historical fit ever offered by this cursor. */
    val bestCandidate: CherryDiscoveredFormula?,
    val exhausted: Boolean,
    /** Immutable scan position in the finite grammar/offset space. */
    val nextPosition: Int = 0,
    /** ID -> finite grammar slot, sufficient to reproduce any offered fit. */
    val offeredPositions: Map<String, Int> = emptyMap(),
    /** ID -> bounded behavioral output fingerprint (not just formula name). */
    val offeredBehaviors: Map<String, String> = emptyMap(),
) {
    val blocked: Boolean get() = !engine.hasImmediatePreviousDraw
    val inspectedExpressions: Int get() = engine.inspectedExpressions
    val completedStages: Int get() = engine.completedStages
}

/**
 * Pure domain: never launches a task on its own. Use the cancellable
 * CherryDiscoveryBackgroundRunner when connected to a future UI.
 */
object CherryDiscoverySearchMore {
    fun next(
        history: List<HistoricDraw>,
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        previous: CherryDiscoverySearchCursor? = null,
        closedDates: Set<LocalDate> = emptySet(),
        checkCancellation: () -> Unit = {},
        onProgress: (Int) -> Unit = {},
    ): CherryDiscoverySearchCursor {
        checkCancellation()
        val old = previous?.takeIf {
            it.kind == kind && it.engine.target == target
        }
        val validated = CherryFormulaDiscoveryEngine.nextRound(
            history = history, target = target,
            previousState = old?.engine, closedDates = closedDates,
            checkCancellation = checkCancellation, advanceStage = false,
        )
        val continued = old?.takeIf {
            it.engine.historySignature == validated.historySignature &&
                it.engine.completedStages == validated.completedStages
        }
        val seen = continued?.offeredIds.orEmpty()
        val positions = continued?.offeredPositions.orEmpty()
        val behaviors = continued?.offeredBehaviors.orEmpty()
        val oldBest = continued?.bestCandidate
        val startingPosition = continued?.nextPosition ?: 0
        if (!validated.hasImmediatePreviousDraw) {
            return CherryDiscoverySearchCursor(
                kind, validated, seen, null, oldBest, false,
                nextPosition = startingPosition,
                offeredPositions = positions, offeredBehaviors = behaviors)
        }
        var position = startingPosition
        while (true) {
            checkCancellation()
            val next = CherryFormulaDiscoveryEngine.nextAlternative(
                history = history, target = target, kind = kind,
                startPosition = position, closedDates = closedDates,
                checkCancellation = checkCancellation,
                onProgress = onProgress,
            )
            // The immutable input list supplied by the caller must be
            // consistent within this operation, even during async refresh.
            check(next.historySignature == validated.historySignature) {
                "Discovery history changed during Search More"
            }
            if (!next.hasImmediatePreviousDraw) {
                return CherryDiscoverySearchCursor(
                    kind, validated, seen, null, oldBest, false,
                    nextPosition = startingPosition,
                    offeredPositions = positions, offeredBehaviors = behaviors)
            }
            position = next.nextPosition
            val candidate = next.candidate
            if (candidate != null) {
                val key = requireNotNull(next.behaviorKey) {
                    "A historical-fit alternative must include its behavior"
                }
                if (candidate.id in seen || key in behaviors.values) continue
                // Keep the existing leaderboard session in sync with the
                // candidate's grammar stage, preserving old engine API/tests.
                var stage = validated
                while (stage.completedStages < candidate.discoveredAtStage) {
                    checkCancellation()
                    stage = CherryFormulaDiscoveryEngine.nextRound(
                        history = history, target = target, previousState = stage,
                        closedDates = closedDates,
                        checkCancellation = checkCancellation,
                    )
                }
                val best = listOfNotNull(oldBest, candidate).maxWithOrNull(
                    compareBy<CherryDiscoveredFormula> {
                        it.historicalStreak
                    }.thenBy { -it.discoveredAtStage }
                )
                return CherryDiscoverySearchCursor(
                    kind, stage, seen + candidate.id, candidate, best, false,
                    nextPosition = position,
                    offeredPositions = positions + (candidate.id to (position - 1)),
                    offeredBehaviors = behaviors + (candidate.id to key),
                )
            }
            if (next.isExhausted) {
                // A stage-complete result remains available to existing audit
                // and trial code; the cursor offers no duplicate after finish.
                var stage = validated
                while (!stage.isComplete) {
                    checkCancellation()
                    stage = CherryFormulaDiscoveryEngine.nextRound(
                        history = history, target = target, previousState = stage,
                        closedDates = closedDates,
                        checkCancellation = checkCancellation,
                    )
                }
                return CherryDiscoverySearchCursor(
                    kind, stage, seen, null, oldBest, true,
                    nextPosition = position,
                    offeredPositions = positions,
                    offeredBehaviors = behaviors,
                )
            }
        }
    }
}
