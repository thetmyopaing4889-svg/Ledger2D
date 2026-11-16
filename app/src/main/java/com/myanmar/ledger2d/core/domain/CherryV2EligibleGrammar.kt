package com.myanmar.ledger2d.core.domain

import java.math.BigInteger

/**
 * Search-only V3 grammar for the approved 200+3 input policy.
 *
 * All expressions use lag 1..3. Nesting remains unbounded IN PRINCIPLE,
 * is generated on demand and needs no formula list in memory. The older
 * raw expandable grammar remains untouched for historical/diagnostic use.
 * This does not imply unbounded unique behavior or forecasting accuracy.
 *
 * Index 0..8: the nine lag-1..3 features, exactly as in the original grammar.
 * Index 9+: one of five binary operators or unary complement applied to
 * recursively numbered children. Inverse Cantor pairing ensures all finite
 * binary trees over the nine features can eventually be enumerated.
 */
internal object CherryV2EligibleGrammar {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val two = BigInteger.valueOf(2)
    private val three = BigInteger.valueOf(3)
    private val six = BigInteger.valueOf(6)
    private val nine = BigInteger.valueOf(9)

    fun expressionAt(
        index: BigInteger,
        checkCancellation: () -> Unit = {},
    ): CherryV2Expression {
        require(index.signum() >= 0)
        checkCancellation()
        if (index < nine) {
            val i = index.toInt()
            return CherryV2Expression.Feature(
                BigInteger.valueOf((i / 3 + 1).toLong()),
                CherryV2Expression.Part.entries[i % 3],
            )
        }
        val (q, type) = index.subtract(nine).divideAndRemainder(six)
        if (type.toInt() == 5) {
            return CherryV2Expression.Complement(
                expressionAt(q, checkCancellation))
        }
        val (left, right) = unpair(q, checkCancellation)
        return CherryV2Expression.Binary(
            CherryV2Expression.Operator.entries[type.toInt()],
            expressionAt(left, checkCancellation),
            expressionAt(right, checkCancellation),
        )
    }

    private fun unpair(
        value: BigInteger,
        checkCancellation: () -> Unit,
    ): Pair<BigInteger, BigInteger> {
        val root = sqrt(value.multiply(BigInteger.valueOf(8)).add(one),
            checkCancellation)
        val diagonal = root.subtract(one).divide(two)
        val base = diagonal.multiply(diagonal.add(one)).divide(two)
        val right = value.subtract(base)
        val left = diagonal.subtract(right)
        check(left.signum() >= 0 && right.signum() >= 0)
        return left to right
    }

    private fun sqrt(number: BigInteger, checkCancellation: () -> Unit): BigInteger {
        require(number.signum() >= 0)
        if (number <= one) return number
        var guess = one.shiftLeft((number.bitLength() + 1) / 2)
        while (true) {
            checkCancellation()
            val next = guess.add(number.divide(guess)).shiftRight(1)
            if (next >= guess) return guess
            guess = next
        }
    }
}
