package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Prospective evidence ONLY. This engine never requests discovery and never
 * rebuilds a prediction after the target's result is known.
 */
internal enum class CherrySuccessCriterion { PAIR, LONE, WAM, BREAK }
internal data class CherrySuccessRule(
    val id: String,
    val name: String,
    val tab: CherryTab,
    val criterion: CherrySuccessCriterion,
    val predictions: List<String>,
    /** Rule provenance from the already-completed input draw, not a score. */
    val sourceOrigin: String = "",
) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && predictions.isNotEmpty())
        require(tab != CherryTab.SUMMARY)
        require(predictions.distinct().size == predictions.size)
        require(predictions.all {
            when (criterion) {
                CherrySuccessCriterion.PAIR -> it.matches(Regex("[0-9]{2}"))
                else -> it.matches(Regex("[0-9]"))
            }
        })
    }
}
internal data class CherrySuccessSnapshot(
    val target: DrawIdentity,
    val capturedAtMillis: Long,
    val rules: List<CherrySuccessRule>,
    val sourceVersion: String = "cherry-success-snapshot-v1",
    val inputEvidence: String = "",
) {
    init {
        require(capturedAtMillis > 0L)
        require(sourceVersion.isNotBlank() && inputEvidence.length <= 512)
        require(rules.map { it.tab.name + ":" + it.id }.distinct().size == rules.size)
    }
}

internal data class CherrySuccessCheck(
    val rule: CherrySuccessRule,
    val correct: Boolean,
    val result: String,
)
internal data class CherrySuccessDraw(
    val snapshot: CherrySuccessSnapshot,
    val result: String,
    val checks: List<CherrySuccessCheck>,
) {
    fun forTab(tab: CherryTab): List<CherrySuccessCheck> = checks.filter { it.rule.tab == tab }
}
internal data class CherrySuccessRate(val correct: Int, val evaluated: Int) {
    val percent: Int get() = if (evaluated == 0) 0 else correct * 100 / evaluated
}
internal data class CherrySuccessRank(
    val id: String,
    val name: String,
    val tab: CherryTab,
    val criterion: CherrySuccessCriterion,
    val correct: Int,
    val evaluated: Int,
    val currentStreak: Int,
    /** Number of separately offered 2D pairs for a pair-type rule. */
    val pairCount: Int,
) {
    val percent: Int get() = if (evaluated == 0) 0 else correct * 100 / evaluated
}

internal object CherrySuccessEngine {
    private val yangon = ZoneId.of("Asia/Yangon")
    private val twoDigits = Regex("[0-9]{2}")
    private val order = listOf(CherryTab.CURRENT, CherryTab.BORN, CherryTab.MATH)

    fun cutoff(target: DrawIdentity): LocalDateTime =
        target.date.atTime(when (target.session) {
            DrawSession.MORNING -> DrawSchedule.morningBetCutoff
            DrawSession.EVENING -> DrawSchedule.eveningResultTime
        })

    fun beforeCutoff(target: DrawIdentity, instantMillis: Long): Boolean =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(instantMillis), yangon)
            .isBefore(cutoff(target))

    /**
     * CURRENT/BORN: all actively applicable, uncut rule-specific pairs.
     * These are *automatic* applicable rules, never claimed to be manual picks.
     * MATH: the user's explicitly selected formula only (per category).
     */
    fun createSnapshot(
        report: CherryReport,
        selectedMath: Collection<CherryV3FormulaSelection>,
        capturedAtMillis: Long,
        inputEvidence: String = "",
    ): CherrySuccessSnapshot {
        val rules = mutableListOf<CherrySuccessRule>()
        for ((tab, picks) in listOf(
            CherryTab.CURRENT to report.current,
            CherryTab.BORN to report.born,
        )) {
            val byRule = linkedMapOf<String, MutableList<CherryEvidence>>()
            picks.filter { it.cuts.isEmpty() }.forEach { pick ->
                pick.evidence.filter { it.tab == tab }.forEach { evidence ->
                    byRule.getOrPut(evidence.id) { mutableListOf() }.add(evidence)
                }
            }
            byRule.forEach { (id, evidence) ->
                val pairs = evidence.map { it.pair }.filter(twoDigits::matches)
                    .distinct().sorted()
                if (pairs.isNotEmpty()) {
                    rules.add(CherrySuccessRule(id, evidence.first().name,
                        tab, CherrySuccessCriterion.PAIR, pairs,
                        evidence.first().origin?.let {
                            it.date.toString() + "/" + it.session.name
                        }.orEmpty()))
                }
            }
        }
        for (choice in selectedMath.distinctBy { it.kind }.sortedBy { it.kind.name }) {
            val criterion = when (choice.kind) {
                CherryDiscoveryKind.LONE -> CherrySuccessCriterion.LONE
                CherryDiscoveryKind.WAM -> CherrySuccessCriterion.WAM
                CherryDiscoveryKind.BREAK -> CherrySuccessCriterion.BREAK
            }
            rules.add(CherrySuccessRule(choice.kind.name + ":" + choice.formulaId,
                when (criterion) {
                    CherrySuccessCriterion.LONE -> "လုံးပိုင်"
                    CherrySuccessCriterion.WAM -> "ဝမ်းချိန်း"
                    CherrySuccessCriterion.BREAK -> "ဘရိတ်"
                    else -> error("Unreachable")
                } + " · " + choice.formulaId,
                CherryTab.MATH, criterion, listOf(choice.digit.toString())))
        }
        val context = "previous=" + (report.last?.let {
            it.date.toString() + "/" + it.session.name + ":" + it.digit
        } ?: "none") + ";has930=" + report.has930 +
            (if(inputEvidence.isBlank()) "" else ";" + inputEvidence)
        return CherrySuccessSnapshot(report.target, capturedAtMillis, rules,
            sourceVersion="cherry-better-data-v3.2-success-v1",inputEvidence=context)
    }

    fun evaluate(snapshot: CherrySuccessSnapshot, result: String): CherrySuccessDraw? {
        if (!twoDigits.matches(result) ||
            !beforeCutoff(snapshot.target, snapshot.capturedAtMillis)) return null
        val checks = snapshot.rules.map { rule ->
            val correct = when (rule.criterion) {
                CherrySuccessCriterion.PAIR -> result in rule.predictions
                CherrySuccessCriterion.LONE,
                CherrySuccessCriterion.WAM,
                CherrySuccessCriterion.BREAK -> {
                    val kind = when (rule.criterion) {
                        CherrySuccessCriterion.LONE -> CherryDiscoveryKind.LONE
                        CherrySuccessCriterion.WAM -> CherryDiscoveryKind.WAM
                        else -> CherryDiscoveryKind.BREAK
                    }
                    rule.predictions.any { prediction ->
                        CherryDiscoveryWalkForward.matches(kind, prediction.toInt(), result)
                    }
                }
            }
            CherrySuccessCheck(rule, correct, result)
        }
        return CherrySuccessDraw(snapshot, result, checks)
    }

    /** Only a confirmed result from a matching draw can score a snapshot. */
    fun completed(
        snapshots: List<CherrySuccessSnapshot>,
        confirmed: Map<DrawIdentity, String>,
    ): List<CherrySuccessDraw> =
        snapshots.mapNotNull { snap ->
            confirmed[snap.target]?.let { evaluate(snap, it) }
        }.sortedWith(compareByDescending<CherrySuccessDraw> { it.snapshot.target.date }
            .thenByDescending { it.snapshot.target.session.ordinal })

    /**
     * Denominator is draws where this tab actually had a pre-result rule.
     * Absent snapshots are never false negatives. A tab is a hit when >=1 of
     * its pre-selected rules matched; rule-level ranking uses another measure.
     */
    fun tabRate(draws: List<CherrySuccessDraw>, tab: CherryTab): CherrySuccessRate {
        val eligible = draws.filter { it.forTab(tab).isNotEmpty() }
        return CherrySuccessRate(eligible.count { draw ->
            draw.forTab(tab).any { it.correct }
        }, eligible.size)
    }

    fun ranking(
        draws: List<CherrySuccessDraw>,
        minimumEvaluated: Int = 3,
        limit: Int = 5,
    ): List<CherrySuccessRank> {
        require(minimumEvaluated > 0 && limit > 0)
        val groups = draws.flatMap { draw ->
            draw.checks.map { check -> draw.snapshot.target to check }
        }.groupBy { (_, check) -> check.rule.tab.name + ":" + check.rule.id }
        return groups.mapNotNull { (id, records) ->
            val ordered = records.sortedWith(
                compareByDescending<Pair<DrawIdentity, CherrySuccessCheck>> { it.first.date }
                    .thenByDescending { it.first.session.ordinal })
            if (ordered.size < minimumEvaluated) null else {
                val first = ordered.first().second.rule
                CherrySuccessRank(id, first.name, first.tab, first.criterion,
                    ordered.count { it.second.correct }, ordered.size,
                    ordered.takeWhile { it.second.correct }.size,
                    first.predictions.size)
            }
        }.sortedWith(compareByDescending<CherrySuccessRank> { it.percent }
            .thenByDescending { it.evaluated }
            .thenByDescending { it.currentStreak }
            .thenBy { it.id }).take(limit)
    }
}
