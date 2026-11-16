package com.myanmar.ledger2d.core.domain

import java.math.BigInteger

/**
 * V2 candidate priority schedule. A cursor position uniquely identifies a
 * deterministic candidate, so pause/resume never needs a large priority queue.
 *
 * First nine slots retain the original one-lag/three-lag feature order.
 * All later candidate trees also use ONLY lag 1..3, so none is rejected
 * purely due to the approved Master Plan's 203-slot/lag-3 policy.
 * This changes schedule slot meanings and intentionally bumps VERSION.
 * Thereafter every three slots interleave:
 *  0: the indefinitely expandable lag-1..3 eligible expression grammar;
 *  1: cross-feature binary combinations over useful recent lags;
 *  2: nested three-feature/nonlinear combinations.
 *
 * This is *structural-diversity prioritization*, not evidence of increased
 * predictive accuracy. All lanes are lazily decoded, all are subject to the
 * same time budget, and the full grammar lane reaches every finite grammar
 * index eventually. Even a first candidate can fit 10+ historical draws.
 *
 * A schedule version change MUST invalidate older persisted cursors: the
 * registry identifies a formula by its stable *schedule slot*, not by the
 * index passed to the underlying raw syntax enumeration.
 */
internal object CherryV2GuidedSchedule {
    // V3 changes the meaning of slots >=9. V2 checkpoints must fail closed
    // rather than being resumed as different formulas after the 203-slot fix.
    const val VERSION = 3

    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val nine = BigInteger.valueOf(9)
    private val three = BigInteger.valueOf(3)
    private val five = BigInteger.valueOf(5)
    private val nineFeatures = BigInteger.valueOf(9)
    private val eightyOne = BigInteger.valueOf(81)
    private val sevenHundredTwentyNine = BigInteger.valueOf(729)

    enum class Lane { SIMPLE, CROSS_LAG, NESTED }

    fun laneAt(slot: BigInteger): Lane {
        require(slot.signum() >= 0)
        if (slot < nine) return Lane.SIMPLE
        return Lane.entries[slot.subtract(nine).mod(three).toInt()]
    }

    fun expressionAt(
        slot: BigInteger,
        checkCancellation: () -> Unit = {},
    ): CherryV2Expression {
        require(slot.signum() >= 0)
        checkCancellation()
        if (slot < nine) {
            return CherryV2EligibleGrammar.expressionAt(slot, checkCancellation)
        }
        val divided = slot.subtract(nine).divideAndRemainder(three)
        val round = divided[0]
        return when (Lane.entries[divided[1].toInt()]) {
            Lane.SIMPLE -> CherryV2EligibleGrammar.expressionAt(
                nine.add(round), checkCancellation)
            Lane.CROSS_LAG -> pair(round, checkCancellation)
            Lane.NESTED -> triple(round, checkCancellation)
        }
    }

    private fun feature(number: BigInteger): CherryV2Expression.Feature {
        // Feature 0..8 spans TOP, TAIL, BREAK for lags 1..3.
        val lagAndPart = number.mod(nineFeatures).divideAndRemainder(three)
        return CherryV2Expression.Feature(
            lagAndPart[0].add(one),
            CherryV2Expression.Part.entries[lagAndPart[1].toInt()],
        )
    }

    private fun pair(
        round: BigInteger,
        checkCancellation: () -> Unit,
    ): CherryV2Expression {
        checkCancellation()
        val opAndFeatures = round.divideAndRemainder(five)
        val op = CherryV2Expression.Operator.entries[opAndFeatures[1].toInt()]
        val bAndA = opAndFeatures[0].divideAndRemainder(nineFeatures)
        val left = feature(bAndA[1])
        // Offset the second operand to prioritize genuinely cross-lag
        // relations (top1 + top2), not top1 + top1 duplicates first.
        val right = feature(bAndA[0].add(three))
        val layer = opAndFeatures[0].divide(eightyOne)
        if (layer == zero) return CherryV2Expression.Binary(op, left, right)

        // Once the shallow pair space is visited, increase expressiveness
        // instead of wrapping around the same finite 18x18x5 combinations.
        return CherryV2Expression.Binary(
            op,
            CherryV2EligibleGrammar.expressionAt(layer.subtract(one),
                checkCancellation),
            CherryV2Expression.Binary(
                CherryV2Expression.Operator.SUBTRACT, left, right),
        )
    }

    private fun triple(
        round: BigInteger,
        checkCancellation: () -> Unit,
    ): CherryV2Expression {
        checkCancellation()
        val first = round.divideAndRemainder(five)
        val second = first[0].divideAndRemainder(five)
        val opInner = CherryV2Expression.Operator.entries[first[1].toInt()]
        val opOuter = CherryV2Expression.Operator.entries[second[1].toInt()]
        val features = second[0]
        val firstFeature = feature(features)
        // Start with distinct prior components and lags. Mixed features
        // are evaluated before algebraically redundant repeated top1s.
        val secondFeature = feature(features.divide(nineFeatures).add(one))
        val thirdFeature = feature(features.divide(eightyOne).add(three))
        val layer = features.divide(sevenHundredTwentyNine)
        val lhs = if (layer == zero) firstFeature
        else CherryV2EligibleGrammar.expressionAt(layer.subtract(one),
            checkCancellation)
        return CherryV2Expression.Binary(
            opOuter,
            CherryV2Expression.Binary(opInner, lhs, secondFeature),
            thirdFeature,
        )
    }
}
