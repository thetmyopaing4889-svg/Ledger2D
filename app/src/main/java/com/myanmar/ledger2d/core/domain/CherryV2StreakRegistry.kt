package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.math.BigInteger

/**
 * One historical fit offered by a future V2 scorer. This evidence is
 * retrospective only, not a prospectively locked winner or a two-digit hit.
 */
internal data class CherryV2StreakMatch(
    val target: DrawIdentity,
    val kind: CherryDiscoveryKind,
    val historySignature: String,
    val formulaIndex: BigInteger,
    val expression: String,
    val predictedDigit: Int,
    val historicalStreak: Int,
    val grammarVersion: Int = CherryV2ExpandableGrammar.VERSION,
) {
    init {
        require(historySignature.isNotBlank())
        require(formulaIndex.signum() >= 0)
        require(expression.isNotBlank())
        require(predictedDigit in 0..9)
        require(historicalStreak in 0..CherryFormulaDiscoveryEngine.SEARCH_DRAWS)
        require(grammarVersion == CherryV2ExpandableGrammar.VERSION)
    }

    val formulaId: String get() = "v$grammarVersion:$formulaIndex@${kind.name}"
    val evidenceLabel: String get() = "HISTORICAL_FIT"
}

/**
 * Pure, immutable engine-only checkpoint. Never exposes a second formula for
 * an already populated *exact* streak length in the same target/category.
 * The cursor moves after every *examined* expression, not just a saved fit.
 *
 * The future session controller is responsible for persisting snapshots
 * atomically and discarding a cursor if bounded history has changed.
 */
internal data class CherryV2StreakRegistry(
    val target: DrawIdentity,
    val kind: CherryDiscoveryKind,
    val historySignature: String,
    val nextIndex: BigInteger = CherryV2ExpandableGrammar.FIRST_INDEX,
    val firstByStreak: Map<Int, CherryV2StreakMatch> = emptyMap(),
) {
    init {
        require(historySignature.isNotBlank())
        require(nextIndex.signum() >= 0)
        require(firstByStreak.all { (count, candidate) ->
            count >= CherryFormulaDiscoveryEngine.MIN_STREAK &&
                count == candidate.historicalStreak &&
                candidate.kind == kind && candidate.target == target &&
                candidate.historySignature == historySignature &&
                candidate.formulaIndex < nextIndex
        })
    }

    /** Advances exactly one expression, including misses and shorter fits. */
    fun inspected(index: BigInteger, candidate: CherryV2StreakMatch?): CherryV2StreakRegistry {
        require(index == nextIndex) { "Out-of-order or repeated formula index" }
        if (candidate != null) {
            require(candidate.formulaIndex == index)
            require(candidate.target == target && candidate.kind == kind)
            require(candidate.historySignature == historySignature)
        }
        val newResults = if (candidate == null ||
            candidate.historicalStreak < CherryFormulaDiscoveryEngine.MIN_STREAK ||
            firstByStreak.containsKey(candidate.historicalStreak)) {
            firstByStreak
        } else {
            firstByStreak + (candidate.historicalStreak to candidate)
        }
        return copy(nextIndex = CherryV2ExpandableGrammar.next(index),
            firstByStreak = newResults)
    }

    fun recommended(): CherryV2StreakMatch? =
        firstByStreak.maxByOrNull { it.key }?.value

    fun orderedResults(): List<CherryV2StreakMatch> =
        firstByStreak.toSortedMap().values.toList()
}
