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
    companion object {
        // Compact 128-bit behavior fingerprints allow 4096 distinct traces within a
        // bounded checkpoint, versus 512 full-length textual traces in V3.
        const val BEHAVIOR_WINDOW = 4096
        // User target: one first-found tier for each of 3,4,...,9,10+.
        // This is at most eight recommendations, not ~198 numbers.
        const val MAX_TARGET_STREAK = 10
    }

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
        // Most generated expressions miss the minimum streak. Reuse the
        // immutable existing result map instead of copying it millions of
        // times. Only the exact cursor position advances.
        if (candidates.isEmpty()) {
            return copy(nextIndex = CherryV2ExpandableGrammar.next(index))
        }
        val newResults = firstByStreak.toMutableMap()
        candidates.forEach { candidate ->
            if (candidate.historicalStreak < CherryFormulaDiscoveryEngine.MIN_STREAK) {
                return@forEach
            }
            if (candidate.historicalStreak >= MAX_TARGET_STREAK) {
                // One first-seen 10+ match represents the target milestone.
                // Older V2 checkpoints could contain 11+, so never overwrite
                // their earliest already-retained 10+ matching candidate.
                if (newResults.keys.none { it >= MAX_TARGET_STREAK }) {
                    newResults[MAX_TARGET_STREAK] = candidate.copy(
                        historicalStreak = MAX_TARGET_STREAK)
                }
            } else {
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

    fun recommended(): CherryV2StreakMatch? = orderedResults().lastOrNull()

    /**
     * Never expose more than eight 3..10+ tiers to Math UI. Existing
     * pre-cap V2 checkpoints remain readable and are NOT rewritten, lost or
     * silently truncated in persistent storage. For multiple 10+ entries in
     * such a checkpoint the FIRST discovered schedule index wins.
     */
    fun orderedResults(): List<CherryV2StreakMatch> {
        val perTier = sortedMapOf<Int, CherryV2StreakMatch>()
        for (candidate in firstByStreak.values.sortedWith(
            compareBy<CherryV2StreakMatch> { it.formulaIndex }
                .thenBy { it.offset }
        )) {
            val tier = minOf(candidate.historicalStreak, MAX_TARGET_STREAK)
            if (tier >= CherryFormulaDiscoveryEngine.MIN_STREAK) {
                perTier.putIfAbsent(tier,
                    if (candidate.historicalStreak == tier) candidate
                    else candidate.copy(historicalStreak = tier))
            }
        }
        return perTier.values.toList()
    }
}
