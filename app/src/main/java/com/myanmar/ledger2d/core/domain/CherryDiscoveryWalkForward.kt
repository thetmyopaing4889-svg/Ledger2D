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
/**
 * Research-only comparisons. The default LONGEST preserves the agreed
 * user-facing search target. SIMPLEST is a diagnostic counterfactual, not
 * a production recommendation until independent results justify it.
 */
enum class CherryDiscoverySelectionPolicy { LONGEST, SIMPLEST }

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

/**
 * Research-only window audit. sampledTargets are deliberately spaced through
 * the entire period; this does NOT claim that every draw was replayed.
 */
data class CherryDiscoveryWindowAudit(
    val years: Int,
    val fromInclusive: LocalDate,
    val throughInclusive: LocalDate,
    val eligibleTargetsInWindow: Int,
    val sampledTargets: Int,
    val report: CherryDiscoveryForwardReport,
)

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

    /**
     * Default historical replay: newest already-settled targets. Every target
     * discovers only from its preceding 203 (200 scored + 3 context) slots.
     * No target result is used to choose the formula or its offset.
     */
    fun replay(
        historicalDraws: List<HistoricDraw>,
        lastTargets: Int = 20,
        stages: Int = CherryFormulaDiscoveryEngine.STAGE_COUNT,
        closedDates: Set<LocalDate> = emptySet(),
        policy: CherryDiscoverySelectionPolicy = CherryDiscoverySelectionPolicy.LONGEST,
        checkCancellation: () -> Unit = {},
    ): CherryDiscoveryForwardReport {
        require(lastTargets > 0)
        require(stages in 1..CherryFormulaDiscoveryEngine.STAGE_COUNT)
        checkCancellation()
        val rows = CherryDiscoveryHistoryNormalizer.normalize(
            historicalDraws.filter {
                validHistoric2D(it.digit) &&
                    DrawSchedule.isWorkingDate(it.date, closedDates)
            }
        )
        return replayAtIndices(
            rows, rows.indices.takeLast(lastTargets), stages,
            closedDates, policy, checkCancellation,
        )
    }

    /**
     * Explicit slow/research path, separate from normal 200-draw discovery.
     * A 2- or 3-year period is sampled at evenly spaced historic targets,
     * with 80 samples by default. The report clearly labels the sample size.
     * Past context may precede the audited period but cannot reach the
     * held-out target itself.
     */
    fun replayResearchWindow(
        historicalDraws: List<HistoricDraw>,
        throughInclusive: LocalDate,
        years: Int = 2,
        sampleTargets: Int = 80,
        stages: Int = CherryFormulaDiscoveryEngine.STAGE_COUNT,
        closedDates: Set<LocalDate> = emptySet(),
        policy: CherryDiscoverySelectionPolicy = CherryDiscoverySelectionPolicy.LONGEST,
        checkCancellation: () -> Unit = {},
    ): CherryDiscoveryWindowAudit {
        require(years == 2 || years == 3)
        require(sampleTargets in 1..200) {
            "Research samples are bounded; run larger audits offline in batches"
        }
        require(stages in 1..CherryFormulaDiscoveryEngine.STAGE_COUNT)
        checkCancellation()
        val from = throughInclusive.minusYears(years.toLong())
        val rows = CherryDiscoveryHistoryNormalizer.normalize(
            historicalDraws.filter {
                validHistoric2D(it.digit) &&
                    DrawSchedule.isWorkingDate(it.date, closedDates) &&
                    !it.date.isAfter(throughInclusive)
            }
        )
        val eligible = rows.indices.filter { rows[it].date >= from }
        val count = minOf(sampleTargets, eligible.size)
        // Deterministic stratified temporal sample, not only the latest 80.
        val selected = if (count == 0) emptyList() else
            (0 until count).map { index ->
                eligible[index * eligible.size / count]
            }
        return CherryDiscoveryWindowAudit(
            years, from, throughInclusive, eligible.size, count,
            replayAtIndices(rows, selected, stages,
                closedDates, policy, checkCancellation),
        )
    }

    private fun replayAtIndices(
        rows: List<HistoricDraw>,
        selectedIndices: List<Int>,
        stages: Int,
        closedDates: Set<LocalDate>,
        policy: CherryDiscoverySelectionPolicy,
        checkCancellation: () -> Unit,
    ): CherryDiscoveryForwardReport {
        val checks = buildList {
            for (index in selectedIndices) {
                checkCancellation()
                val actual = rows[index]
                val t = DrawIdentity(actual.date, actual.session)
                // Already sorted, unique and time-filtered: avoid scanning
                // the entire archive for each replay target.
                val past = rows.subList(
                    maxOf(0, index - CherryFormulaDiscoveryEngine.SEARCH_DRAWS - 3),
                    index,
                )
                var result: CherryDiscoverySession? = null
                repeat(stages) {
                    result = CherryFormulaDiscoveryEngine.nextRound(
                        history = past,
                        target = t,
                        previousState = result,
                        closedDates = closedDates,
                        checkCancellation = checkCancellation,
                    )
                }
                val state = requireNotNull(result)
                for (kind in CherryDiscoveryKind.entries) {
                    val choice = when (policy) {
                        CherryDiscoverySelectionPolicy.LONGEST -> state.longest(kind)
                        CherryDiscoverySelectionPolicy.SIMPLEST ->
                            state.formulas.filter { it.kind == kind }
                                .maxWithOrNull(compareBy<CherryDiscoveredFormula> {
                                    -it.discoveredAtStage
                                }.thenBy { it.historicalStreak })
                    }
                    add(CherryDiscoveryForwardCheck(
                        target = t,
                        actualPair = actual.digit,
                        kind = kind,
                        discoveredFormulaId = choice?.id,
                        historicalFitStreak = choice?.historicalStreak,
                        outputDigit = choice?.currentDigit,
                        heldOutHit = choice?.let {
                            matches(kind, it.currentDigit, actual.digit)
                        },
                    ))
                }
            }
        }
        return CherryDiscoveryForwardReport(checks, stages)
    }

}
