package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/**
 * Lightweight, UI-free progress snapshot. The 24-slot leaderboard is rebuilt
 * from the bounded history at restore time; it is never stored redundantly.
 * An old/corrected history fingerprint invalidates this checkpoint, but must
 * not affect immutable pre-result locks in the independent trial journal.
 */
data class CherryDiscoveryCheckpoint(
    val target: DrawIdentity,
    val kind: CherryDiscoveryKind,
    val historySignature: String,
    val completedStages: Int,
    val nextPosition: Int,
    val offeredIds: Set<String>,
    val newCandidate: CherryDiscoveredFormula?,
    val bestCandidate: CherryDiscoveredFormula?,
    val exhausted: Boolean,
    val offeredPositions: Map<String, Int> = emptyMap(),
    val offeredBehaviors: Map<String, String> = emptyMap(),
)

object CherryDiscoveryCheckpoints {
    const val MAX_POSITIONS = 207 * 4

    fun validate(snapshot: CherryDiscoveryCheckpoint) {
        require(snapshot.historySignature.isNotBlank())
        require(snapshot.completedStages in 0..CherryFormulaDiscoveryEngine.STAGE_COUNT)
        require(snapshot.nextPosition in 0..MAX_POSITIONS)
        require(snapshot.offeredIds.size <= MAX_POSITIONS)
        require(snapshot.offeredIds.all { it.isNotBlank() })
        require(snapshot.offeredPositions.keys == snapshot.offeredIds &&
            snapshot.offeredBehaviors.keys == snapshot.offeredIds)
        require(snapshot.offeredPositions.values.all {
            it in 0 until MAX_POSITIONS && it < snapshot.nextPosition
        })
        require(snapshot.offeredPositions.values.distinct().size ==
            snapshot.offeredPositions.size)
        require(snapshot.offeredBehaviors.values.all {
            it.matches(Regex("[0-9a-f]{64}"))
        })
        require(snapshot.offeredBehaviors.values.distinct().size ==
            snapshot.offeredBehaviors.size)
        require(!snapshot.exhausted || snapshot.nextPosition == MAX_POSITIONS)
        require(!snapshot.exhausted ||
            snapshot.completedStages == CherryFormulaDiscoveryEngine.STAGE_COUNT)
        for (formula in listOfNotNull(snapshot.newCandidate, snapshot.bestCandidate)) {
            require(formula.kind == snapshot.kind && formula.id in snapshot.offeredIds)
            require(formula.discoveredAtStage in 1..snapshot.completedStages)
            require(formula.historicalStreak >= CherryFormulaDiscoveryEngine.MIN_STREAK)
            require(formula.currentDigit in 0..9 &&
                formula.offset in 0..9 && formula.evidenceLabel == "HISTORICAL_FIT")
        }
    }

    fun capture(cursor: CherryDiscoverySearchCursor): CherryDiscoveryCheckpoint {
        val snapshot = CherryDiscoveryCheckpoint(
            target = cursor.engine.target,
            kind = cursor.kind,
            historySignature = cursor.engine.historySignature,
            completedStages = cursor.engine.completedStages,
            nextPosition = cursor.nextPosition,
            offeredIds = cursor.offeredIds.toSet(),
            newCandidate = cursor.newCandidate,
            bestCandidate = cursor.bestCandidate,
            exhausted = cursor.exhausted,
            offeredPositions = cursor.offeredPositions.toMap(),
            offeredBehaviors = cursor.offeredBehaviors.toMap(),
        )
        validate(snapshot)
        return snapshot
    }

    /** Returns null for corrected history instead of reusing stale output. */
    fun restore(
        snapshot: CherryDiscoveryCheckpoint,
        history: List<HistoricDraw>,
        closedDates: Set<LocalDate> = emptySet(),
        checkCancellation: () -> Unit = {},
    ): CherryDiscoverySearchCursor? {
        validate(snapshot)
        checkCancellation()
        var state = CherryFormulaDiscoveryEngine.nextRound(
            history = history, target = snapshot.target,
            closedDates = closedDates, checkCancellation = checkCancellation,
            advanceStage = false)
        if (!state.hasImmediatePreviousDraw ||
            state.historySignature != snapshot.historySignature) return null
        repeat(snapshot.completedStages) {
            checkCancellation()
            state = CherryFormulaDiscoveryEngine.nextRound(
                history = history, target = snapshot.target,
                previousState = state, closedDates = closedDates,
                checkCancellation = checkCancellation)
        }
        require(state.completedStages == snapshot.completedStages)
        return CherryDiscoverySearchCursor(
            kind = snapshot.kind, engine = state,
            offeredIds = snapshot.offeredIds.toSet(),
            newCandidate = snapshot.newCandidate,
            bestCandidate = snapshot.bestCandidate,
            exhausted = snapshot.exhausted,
            nextPosition = snapshot.nextPosition,
            offeredPositions = snapshot.offeredPositions.toMap(),
            offeredBehaviors = snapshot.offeredBehaviors.toMap(),
        )
    }
}
