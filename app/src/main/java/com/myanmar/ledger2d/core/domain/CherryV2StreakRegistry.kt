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
    /** One unchanged mod-10 offset applied at every checked draw. */
    val offset: Int = 0,
    val grammarVersion: Int = CherryV2ExpandableGrammar.VERSION,
) {
    init {
        require(historySignature.isNotBlank())
        require(formulaIndex.signum() >= 0)
        require(expression.isNotBlank())
        require(predictedDigit in 0..9)
        require(offset in 0..9)
        require(historicalStreak in 0..CherryFormulaDiscoveryEngine.SEARCH_DRAWS)
        require(grammarVersion == CherryV2ExpandableGrammar.VERSION)
    }

    val formulaId: String get() = "v$grammarVersion:$formulaIndex+$offset@${kind.name}"
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
    /** Recent equivalent-output signatures only; never an unlimited HashSet. */
    val recentBehaviorKeys: List<String> = emptyList(),
    val grammarVersion: Int = CherryV2ExpandableGrammar.VERSION,
) {
    companion object { const val BEHAVIOR_WINDOW = 512 }

    init {
        require(grammarVersion == CherryV2ExpandableGrammar.VERSION)
        require(recentBehaviorKeys.size <= BEHAVIOR_WINDOW)
        require(recentBehaviorKeys.size == recentBehaviorKeys.toSet().size)
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

    /** Advances exactly one examined expression, including misses. */
    fun inspected(index: BigInteger, candidate: CherryV2StreakMatch?): CherryV2StreakRegistry =
        inspectedBatch(index, listOfNotNull(candidate))

    /**
     * A grammar expression can produce multiple qualifying offset variants.
     * First-found wins per exact streak length, in deterministic offset order.
     * All candidates are validated BEFORE making the immutable state transition.
     */
    fun inspectedBatch(
        index: BigInteger,
        candidates: List<CherryV2StreakMatch>,
    ): CherryV2StreakRegistry {
        require(index == nextIndex) { "Out-of-order or repeated formula index" }
        candidates.forEach { candidate ->
            require(candidate.formulaIndex == index)
            require(candidate.target == target && candidate.kind == kind)
            require(candidate.historySignature == historySignature)
            require(candidate.grammarVersion == CherryV2ExpandableGrammar.VERSION)
        }
        val newResults = firstByStreak.toMutableMap()
        candidates.forEach { candidate ->
            if (candidate.historicalStreak >= CherryFormulaDiscoveryEngine.MIN_STREAK) {
                newResults.putIfAbsent(candidate.historicalStreak, candidate)
            }
        }
        return copy(
            nextIndex = CherryV2ExpandableGrammar.next(index),
            firstByStreak = newResults.toMap(),
        )
    }

    /** Preserve only a bounded recent equivalence window in checkpoints. */
    fun withRecentBehaviors(keys: List<String>): CherryV2StreakRegistry {
        require(keys.size <= BEHAVIOR_WINDOW)
        require(keys.size == keys.toSet().size)
        return copy(recentBehaviorKeys = keys.toList())
    }

    fun recommended(): CherryV2StreakMatch? =
        firstByStreak.maxByOrNull { it.key }?.value

    fun orderedResults(): List<CherryV2StreakMatch> =
        firstByStreak.toSortedMap().values.toList()
}
