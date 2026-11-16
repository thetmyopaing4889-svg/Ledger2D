package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import kotlin.math.abs

/**
 * Replays exactly two calculations of one SAVED, verified V3 discovery:
 * the most recent completed draw (using only draws before that draw), and
 * the upcoming target draw (using only already-completed prior draws).
 *
 * Never searches new formulas, uses target outcomes, writes Champion, or
 * returns a plausible-looking explanation if the formula/history disagree.
 */
internal data class CherryV3WorkedExample(
    val draw: DrawIdentity,
    val inputs: List<String>,
    val steps: List<String>,
    val digit: Int,
    val actualPair: String?,
)

internal data class CherryV3WorkedExamples(
    val previous: CherryV3WorkedExample,
    val current: CherryV3WorkedExample,
)

internal object CherryV3FormulaExplainer {
    private fun sessionName(session: DrawSession): String =
        if (session == DrawSession.MORNING) "မနက်" else "ညနေ"

    private fun partName(part: CherryV2Expression.Part): String = when (part) {
        CherryV2Expression.Part.TOP -> "ထိပ်လုံး"
        CherryV2Expression.Part.TAIL -> "နောက်ပိတ်"
        CherryV2Expression.Part.BREAK -> "ဘရိတ်"
    }

    private fun calculate(
        target: DrawIdentity,
        available: List<HistoricDraw>,
        expression: CherryV2Expression,
        offset: Int,
        actualPair: String?,
    ): CherryV3WorkedExample? {
        val inputs = linkedMapOf<String, String>()
        val steps = ArrayList<String>()

        fun value(node: CherryV2Expression): Int? = when (node) {
            is CherryV2Expression.Feature -> {
                // The engine admits up to lag 3; reject any overflow/wrap.
                if (node.lag.bitLength() > 30) null else {
                    val lag = node.lag.toInt()
                    val row = available.getOrNull(lag - 1)
                    if (row == null || !validHistoric2D(row.digit)) null
                    else {
                        val top = row.digit[0].digitToInt()
                        val tail = row.digit[1].digitToInt()
                        val digit = when (node.part) {
                            CherryV2Expression.Part.TOP -> top
                            CherryV2Expression.Part.TAIL -> tail
                            CherryV2Expression.Part.BREAK -> (top + tail) % 10
                        }
                        val key = "$lag/${node.part.name}"
                        inputs.putIfAbsent(
                            key,
                            "အရင် $lag ကြိမ် (${row.date} ${sessionName(row.session)}) " +
                                "— ${row.digit} → ${partName(node.part)} $digit",
                        )
                        digit
                    }
                }
            }
            is CherryV2Expression.Complement -> {
                val a = value(node.inner)
                if (a == null) null else {
                    val result = 9 - a
                    steps.add("၉ − $a = $result")
                    result
                }
            }
            is CherryV2Expression.Binary -> {
                val left = value(node.left)
                val right = value(node.right)
                if (left == null || right == null) null
                else {
                    val raw = when (node.op) {
                        CherryV2Expression.Operator.ADD -> left + right
                        CherryV2Expression.Operator.SUBTRACT -> left - right
                        CherryV2Expression.Operator.MULTIPLY -> left * right
                        CherryV2Expression.Operator.ABS_DIFFERENCE -> abs(left - right)
                        CherryV2Expression.Operator.TWICE_PLUS -> left * 2 + right
                    }
                    val output = Math.floorMod(raw, 10)
                    val calculation = when (node.op) {
                        CherryV2Expression.Operator.ADD -> "$left + $right"
                        CherryV2Expression.Operator.SUBTRACT -> "$left − $right"
                        CherryV2Expression.Operator.MULTIPLY -> "$left × $right"
                        CherryV2Expression.Operator.ABS_DIFFERENCE -> "|$left − $right|"
                        CherryV2Expression.Operator.TWICE_PLUS -> "($left × ၂) + $right"
                    }
                    steps.add(
                        if (raw == output) "$calculation = $output"
                        else "$calculation = $raw → နောက်ဆုံးလုံး $output",
                    )
                    output
                }
            }
        }

        val base = value(expression) ?: return null
        // The searched engine uses one fixed mod-10 offset for both examples.
        val raw = base + offset
        val digit = Math.floorMod(raw, 10)
        steps.add(
            if (offset == 0) "နောက်ဆုံးရလဒ် = $digit"
            else "$base + $offset = $raw → နောက်ဆုံးလုံး $digit",
        )
        return CherryV3WorkedExample(
            target, inputs.values.toList(), steps, digit, actualPair,
        )
    }

    fun explain(
        context: CherryV2HistoricalEvaluator.Context,
        formula: CherryV2StreakMatch,
    ): CherryV3WorkedExamples? {
        if (context.blocked || formula.target != context.target ||
            formula.historySignature != context.historySignature ||
            formula.kind !in CherryDiscoveryKind.entries
        ) return null
        val expression = CherryV2GuidedSchedule.expressionAt(formula.formulaIndex)
        if (expression.structuralKey != formula.expression) return null

        val current = calculate(
            context.target, context.prior, expression, formula.offset, null,
        ) ?: return null
        if (current.digit != formula.predictedDigit) return null

        val previousResult = context.prior.firstOrNull() ?: return null
        val last = DrawIdentity(previousResult.date, previousResult.session)
        val previous = calculate(
            last, context.prior.drop(1), expression, formula.offset,
            previousResult.digit,
        ) ?: return null
        // The most recent historical draw MUST be part of the retained
        // consecutive hit streak; fail closed if the display is stale.
        if (!CherryDiscoveryWalkForward.matches(
                formula.kind, previous.digit, previousResult.digit)
        ) return null
        return CherryV3WorkedExamples(previous, current)
    }
}
