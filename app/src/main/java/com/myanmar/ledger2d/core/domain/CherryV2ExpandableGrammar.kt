package com.myanmar.ledger2d.core.domain

import java.math.BigInteger
import kotlin.math.abs

/**
 * Experimental V2 expression tree. Inputs are *completed prior draws* in
 * newest-first order; the future target draw is never an input.
 *
 * Every evaluation returns one digit (0..9), or null when an input is absent
 * or malformed. This is not an exact 2D-pair prediction.
 */
internal sealed class CherryV2Expression {
    abstract val structuralKey: String
    abstract fun evaluate(priorDrawsNewestFirst: List<String>): Int?

    data class Feature(val lag: BigInteger, val part: Part) : CherryV2Expression() {
        init { require(lag.signum() > 0) }
        override val structuralKey: String get() = "${part.key}$lag"
        override fun evaluate(priorDrawsNewestFirst: List<String>): Int? {
            // Never truncate an oversized lag to Int or wrap its index.
            val zeroBased = lag.subtract(BigInteger.ONE)
            if (zeroBased.bitLength() > 31) return null
            val pair = priorDrawsNewestFirst.getOrNull(zeroBased.toInt()) ?: return null
            if (pair.length != 2 || pair.any { it !in '0'..'9' }) return null
            val top = pair[0].digitToInt()
            val tail = pair[1].digitToInt()
            return when (part) {
                Part.TOP -> top
                Part.TAIL -> tail
                Part.BREAK -> (top + tail) % 10
            }
        }
    }

    data class Complement(val inner: CherryV2Expression) : CherryV2Expression() {
        override val structuralKey: String get() = "(9-${inner.structuralKey})"
        override fun evaluate(priorDrawsNewestFirst: List<String>): Int? =
            inner.evaluate(priorDrawsNewestFirst)?.let { 9 - it }
    }

    data class Binary(
        val op: Operator,
        val left: CherryV2Expression,
        val right: CherryV2Expression,
    ) : CherryV2Expression() {
        override val structuralKey: String get() =
            "(${left.structuralKey}${op.key}${right.structuralKey})"

        override fun evaluate(priorDrawsNewestFirst: List<String>): Int? {
            val a = left.evaluate(priorDrawsNewestFirst) ?: return null
            val b = right.evaluate(priorDrawsNewestFirst) ?: return null
            return when (op) {
                Operator.ADD -> Math.floorMod(a + b, 10)
                Operator.SUBTRACT -> Math.floorMod(a - b, 10)
                Operator.MULTIPLY -> Math.floorMod(a * b, 10)
                Operator.ABS_DIFFERENCE -> abs(a - b)
                Operator.TWICE_PLUS -> Math.floorMod(a * 2 + b, 10)
            }
        }
    }

    enum class Part(val key: String) { TOP("top"), TAIL("tail"), BREAK("break") }
    enum class Operator(val key: String) {
        ADD("+"), SUBTRACT("-"), MULTIPLY("*"),
        ABS_DIFFERENCE("~"), TWICE_PLUS("*2+"),
    }
}

/**
 * Total, deterministic, seekable enumeration of an *expandable* expression
 * grammar; unlike the legacy 207-shape scanner it has no finite end marker.
 *
 * Indices 0..8 are the familiar top/tail/break features for lags 1..3.
 * At 9+ every index uniquely encodes either a feature with a deeper lag,
 * a unary complement or one of five binary operators. Cantor unpairing
 * reconstructs two strictly smaller child indices; no expression list is
 * generated or kept in memory. Every finite tree in this grammar is encodable.
 *
 * NOTE: A mathematically unlimited *syntax* space is NOT unlimited distinct
 * digit behavior on a finite 200-draw dataset. A future V2 search controller
 * must enforce CPU/time/RAM budgets, semantic deduplication, historical-fit
 * labeling and a recoverable cursor. This generator never starts work itself.
 */
internal object CherryV2ExpandableGrammar {
    const val VERSION = 1
    val FIRST_INDEX: BigInteger = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val two = BigInteger.valueOf(2)
    private val three = BigInteger.valueOf(3)
    private val seven = BigInteger.valueOf(7)
    private val nine = BigInteger.valueOf(9)

    fun next(index: BigInteger): BigInteger {
        require(index.signum() >= 0) { "Negative formula index" }
        return index.add(one)
    }

    fun expressionAt(
        index: BigInteger,
        checkCancellation: () -> Unit = {},
    ): CherryV2Expression {
        require(index.signum() >= 0) { "Negative formula index" }
        return decode(index, checkCancellation)
    }

    private fun decode(index: BigInteger, checkCancellation: () -> Unit): CherryV2Expression {
        checkCancellation()
        if (index < nine) {
            val lag = BigInteger.valueOf((index.toInt() / 3 + 1).toLong())
            val part = CherryV2Expression.Part.entries[index.toInt() % 3]
            return CherryV2Expression.Feature(lag, part)
        }
        val parts = index.subtract(nine).divideAndRemainder(seven)
        val quotient = parts[0]
        return when (parts[1].toInt()) {
            0 -> {
                val lagAndPart = quotient.divideAndRemainder(three)
                CherryV2Expression.Feature(
                    lagAndPart[0].add(BigInteger.valueOf(4)),
                    CherryV2Expression.Part.entries[lagAndPart[1].toInt()],
                )
            }
            6 -> CherryV2Expression.Complement(decode(quotient, checkCancellation))
            else -> {
                val (leftIndex, rightIndex) = unpair(quotient, checkCancellation)
                val op = CherryV2Expression.Operator.entries[parts[1].toInt() - 1]
                CherryV2Expression.Binary(
                    op, decode(leftIndex, checkCancellation),
                    decode(rightIndex, checkCancellation),
                )
            }
        }
    }

    /** Inverse Cantor pairing, using integer-only square root (Android API 26). */
    private fun unpair(
        value: BigInteger,
        checkCancellation: () -> Unit,
    ): Pair<BigInteger, BigInteger> {
        val root = integerSqrt(value.multiply(BigInteger.valueOf(8)).add(one),
            checkCancellation)
        // floor((sqrt(8*z+1) - 1) / 2)
        val diagonal = root.subtract(one).divide(two)
        val base = diagonal.multiply(diagonal.add(one)).divide(two)
        val right = value.subtract(base)
        val left = diagonal.subtract(right)
        check(left.signum() >= 0 && right.signum() >= 0)
        return left to right
    }

    private fun integerSqrt(
        value: BigInteger,
        checkCancellation: () -> Unit,
    ): BigInteger {
        require(value.signum() >= 0)
        if (value <= one) return value
        var guess = one.shiftLeft((value.bitLength() + 1) / 2)
        while (true) {
            checkCancellation()
            val next = guess.add(value.divide(guess)).shiftRight(1)
            if (next >= guess) return guess
            guess = next
        }
    }
}
