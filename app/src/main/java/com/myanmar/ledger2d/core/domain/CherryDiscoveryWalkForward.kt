package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/**
 * Offline, deterministic, as-of replay. Does not publish predictions or
 * change a live selection, repository, database, or UI.
 *
 * For each already-completed draw T, reconstruct only pre-T history, discover
 * from that history, freeze the selected next-digit output, and THEN compare
 * it with the observed result of T. Historical fit and this held-out result
 * are intentionally recorded as separate facts.
 */
data class CherryDiscoveryForwardCheck(
    val target: DrawIdentity,
    val actualPair: String,
    val kind: CherryDiscoveryKind,
    val discoveredFormulaId: String?,
    val historicalFitStreak: Int?,
    val outputDigit: Int?,
    /** Null = no eligible formula or missing contiguous prior slots. */
    val heldOutHit: Boolean?,
)

data class CherryDiscoveryForwardStats(
    val kind: CherryDiscoveryKind,
    val targetCount: Int,
    val checked: Int,
    val hits: Int,
    val noSelection: Int,
) {
    val rate: Double? get() = if (checked == 0) null else hits.toDouble() / checked
}

data class CherryDiscoveryForwardReport(
    val checks: List<CherryDiscoveryForwardCheck>,
    val stagesEvaluated: Int,
) {
    fun stats(kind: CherryDiscoveryKind): CherryDiscoveryForwardStats {
        val data = checks.filter { it.kind == kind }
        return CherryDiscoveryForwardStats(
            kind = kind,
            targetCount = data.size,
            checked = data.count { it.heldOutHit != null },
            hits = data.count { it.heldOutHit == true },
            noSelection = data.count { it.heldOutHit == null },
        )
    }
}

object CherryDiscoveryWalkForward {
    private fun before(d: HistoricDraw, t: DrawIdentity): Boolean =
        d.date < t.date || (d.date == t.date && d.session.ordinal < t.session.ordinal)

    private fun mod(n: Int) = Math.floorMod(n, 10)

    /** Exactly the same category-specific match definitions as the discovery engine. */
    internal fun matches(kind: CherryDiscoveryKind, digit: Int, actual: String): Boolean {
        val a = actual[0].digitToInt()
        val b = actual[1].digitToInt()
        return when (kind) {
            CherryDiscoveryKind.LONE -> a == digit || b == digit
            CherryDiscoveryKind.WAM ->
                a == digit || b == digit ||
                    a == mod(digit + 5) || b == mod(digit + 5)
            CherryDiscoveryKind.BREAK -> mod(a + b) == digit
        }
    }

    fun replay(
        historicalDraws: List<HistoricDraw>,
        lastTargets: Int = 20,
        stages: Int = CherryFormulaDiscoveryEngine.STAGE_COUNT,
        closedDates: Set<LocalDate> = emptySet(),
    ): CherryDiscoveryForwardReport {
        require(lastTargets > 0)
        require(stages in 1..CherryFormulaDiscoveryEngine.STAGE_COUNT)
        val rows = historicalDraws.filter {
            validHistoric2D(it.digit) && it.date !in closedDates
        }.sortedWith(compareBy<HistoricDraw> { it.date }.thenBy { it.session.ordinal })
            .distinctBy { it.date to it.session }
        val selectedTargets = rows.takeLast(lastTargets)
        val checks = buildList {
            for (actual in selectedTargets) {
                val t = DrawIdentity(actual.date, actual.session)
                val past = rows.filter { before(it, t) }
                var result: CherryDiscoverySession? = null
                repeat(stages) {
                    result = CherryFormulaDiscoveryEngine.nextRound(
                        history = past,
                        target = t,
                        previousState = result,
                        closedDates = closedDates,
                    )
                }
                val state = requireNotNull(result)
                for (kind in CherryDiscoveryKind.entries) {
                    val choice = state.longest(kind)
                    add(CherryDiscoveryForwardCheck(
                        target = t,
                        actualPair = actual.digit,
                        kind = kind,
                        discoveredFormulaId = choice?.id,
                        historicalFitStreak = choice?.historicalStreak,
                        outputDigit = choice?.currentDigit,
                        heldOutHit = choice?.let { matches(kind, it.currentDigit, actual.digit) },
                    ))
                }
            }
        }
        return CherryDiscoveryForwardReport(checks, stages)
    }
}
