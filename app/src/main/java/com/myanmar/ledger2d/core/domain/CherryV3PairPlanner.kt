package com.myanmar.ledger2d.core.domain

/**
 * One user-confirmed V3 formula for this draw. Stars describe the historical
 * consecutive-fit tier; they are not prospective probabilities.
 */
internal data class CherryV3FormulaSelection(
    val kind: CherryDiscoveryKind,
    val digit: Int,
    val streak: Int,
    val formulaId: String,
) {
    init {
        require(digit in 0..9)
        require(streak >= 3)
        require(formulaId.isNotBlank())
    }

    val stars: Int get() = when {
        streak >= 9 -> 5
        streak >= 7 -> 4
        streak >= 5 -> 3
        streak >= 4 -> 2
        else -> 1
    }
}

internal data class CherryV3PairPlan(
    val main: List<String> = emptyList(),
    val remaining: List<String> = emptyList(),
    val priorityKinds: List<CherryDiscoveryKind> = emptyList(),
) {
    val all: List<String> get() = main + remaining
}

/**
 * Lightweight user-selection expansion; never starts or repeats discovery.
 *
 * LONE: one digit must appear in a pair.
 * WAM: either its digit or +5 counterpart must appear.
 * BREAK: sum of pair digits modulo 10 equals its selected digit.
 *
 * Only cross-category links are generated. Do not invent BREAK+5 or a
 * stand-alone WAM power pair (the V3 WAM hit contract does not assert one).
 * The two highest-star selections produce the visible main pairs. All other
 * distinct cross-category links are expandable reserves.
 */
internal object CherryV3PairPlanner {
    private val kindOrder = listOf(
        CherryDiscoveryKind.LONE,
        CherryDiscoveryKind.BREAK,
        CherryDiscoveryKind.WAM,
    )

    fun plan(selections: Collection<CherryV3FormulaSelection>): CherryV3PairPlan {
        val ordered = selections
            .groupBy { it.kind }
            .values.map { same ->
                same.sortedWith(compareByDescending<CherryV3FormulaSelection> { it.stars }
                    .thenByDescending { it.streak }
                    .thenBy { it.formulaId }).first()
            }
            .sortedWith(compareByDescending<CherryV3FormulaSelection> { it.stars }
                .thenByDescending { it.streak }
                .thenBy { kindOrder.indexOf(it.kind) }
                .thenBy { it.formulaId })
        if (ordered.size < 2) return CherryV3PairPlan()

        val main = link(ordered[0], ordered[1])
        val all = linkedSetOf<String>()
        all.addAll(main)
        for (i in ordered.indices) {
            for (j in i + 1 until ordered.size) {
                if (i != 0 || j != 1) all.addAll(link(ordered[i], ordered[j]))
            }
        }
        return CherryV3PairPlan(
            main = main,
            remaining = all.filterNot { it in main },
            priorityKinds = ordered.take(2).map { it.kind },
        )
    }

    private fun link(
        first: CherryV3FormulaSelection,
        second: CherryV3FormulaSelection,
    ): List<String> {
        val entries = listOf(first, second).associateBy { it.kind }
        val lone = entries[CherryDiscoveryKind.LONE]?.digit
        val wam = entries[CherryDiscoveryKind.WAM]?.digit
        val br = entries[CherryDiscoveryKind.BREAK]?.digit
        val pairs = linkedSetOf<String>()
        fun insert(a: Int, b: Int) {
            pairs.add("$a$b")
            pairs.add("$b$a")
        }
        when {
            lone != null && br != null ->
                insert(lone, Math.floorMod(br - lone, 10))
            lone != null && wam != null -> {
                insert(lone, wam)
                insert(lone, (wam + 5) % 10)
            }
            wam != null && br != null -> {
                listOf(wam, (wam + 5) % 10).forEach { digit ->
                    insert(digit, Math.floorMod(br - digit, 10))
                }
            }
        }
        return pairs.toList()
    }
}
