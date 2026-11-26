package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.CherryVerifiedHistory
import com.myanmar.ledger2d.core.domain.CherrySuccessCheck
import com.myanmar.ledger2d.core.domain.CherrySuccessCriterion
import com.myanmar.ledger2d.core.domain.CherrySuccessDraw
import com.myanmar.ledger2d.core.domain.CherrySuccessEngine
import com.myanmar.ledger2d.core.domain.CherrySuccessSnapshot
import com.myanmar.ledger2d.core.domain.CherryTab
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession

/** One eligible draw counts once, even if multiple subrules/tabs predict a pair. */
internal data class CherryLibrarySuccessEvent(
    val target: DrawIdentity,
    val winning: String,
    val correct: Boolean,
    val pairCount: Int,
    val tabs: Set<CherryTab>,
)

internal data class CherryLibrarySuccessScore(
    val libraryRuleId: String,
    val correct: Int,
    val eligible: Int,
    val events: List<CherryLibrarySuccessEvent>,
) {
    init {
        require(eligible == events.size && correct == events.count { it.correct })
    }
    val percent: Double get() =
        if (eligible == 0) 0.0 else kotlin.math.round(correct * 1000.0 / eligible) / 10.0
}

/**
 * A deliberately conservative ID bridge. Names and substring similarities
 * must NEVER map an unrelated prediction to a canonical Library source.
 *
 * CURRENT/BORN only. Discovered MATH formulas are not Library rules.
 * Unmatched and partially represented source rules keep their Better Data
 * success records but receive no misleading Library score.
 */
internal object CherryLibrarySuccessBridge {
    // Only source-complete, draw-scoped rules may receive a whole-rule score.
    // Weekly goals and partial implementations are intentionally unmapped:
    // their Better Data snapshots remain intact, but a single winning pair
    // is NOT evidence the entire original Library rule succeeded.
    private val legacyDirect = mapOf(
        "s1-day-head-tail" to "cherry-001",
        "s1-day-break" to "cherry-002",
        "s1-day-diagonal" to "cherry-003",
        "s6-57" to "cherry-046",
        "s8-zero-follow" to "cherry-078",
        "s8-evening-four" to "cherry-085",
        "s9-wailin" to "cherry-089",
        "new-mob-double" to "new-mob-double",
        "new-bk-six" to "new-bk-six",
        "new-born-75" to "new-born-75",
        "new-bk-19r" to "new-bk-19r",
        "new-born-50" to "new-born-50",
        "s1-930" to "cherry-004",
        "s1-930bk" to "cherry-005",
        "s3-ten" to "cherry-009",
        "s3-astro" to "cherry-016",
        "s3-9to7" to "cherry-017",
        "s3-7to4" to "cherry-017",
        "s5-fri" to "cherry-030",
        "s8-80" to "cherry-076",
        "s8-67" to "cherry-077",
        "s8-74" to "cherry-079",
        "s8-48" to "cherry-080",
        "s8-71" to "cherry-081",
        "s8-15" to "cherry-082",
        "s8-88" to "cherry-083",
        "s8-13" to "cherry-084",
        "s6-04" to "cherry-033",
        "s6-09" to "cherry-036",
        "s6-14" to "cherry-039",
        "s6-17" to "cherry-040",
        "s6-23" to "cherry-041",
        "s6-43" to "cherry-043",
        "s6-49" to "cherry-044",
        "s6-54" to "cherry-045",
        "s6-64" to "cherry-048",
        "s6-65" to "cherry-049",
        "s6-69" to "cherry-050",
        "s6-79" to "cherry-052",
        "s6-83" to "cherry-053",
        "s6-84" to "cherry-054",
        "s6-89" to "cherry-055",
    )
    private val doubleWheel = Regex("""s4-wheel-(00|11|22|33|44|55|66|77|88|99)-[0-9]+""")
    private val doubleBorn = Regex("""s4-born-(00|11|22|33|44|55|66|77|88|99)""")

    // Conservative first batch: exact, reviewed, draw-scoped current rules.
    // Multi-session born and weekly goals await a separate lifetime metric.
    private val currentDirect = mapOf(
        "s1-day-head-tail" to "cherry-001",
        "s1-day-break" to "cherry-002",
        "s1-day-diagonal" to "cherry-003",
        "s1-930" to "cherry-004",
        "s1-930bk-v2" to "cherry-005",
        "s6-10" to "cherry-037",
        "s6-57" to "cherry-046",
        "s8-zero-follow" to "cherry-078",
        "s8-74" to "cherry-079",
        "s8-48" to "cherry-080",
        "s8-71" to "cherry-081",
        "s8-15" to "cherry-082",
        "s8-88" to "cherry-083",
        "s8-13" to "cherry-084",
        "s8-evening-four" to "cherry-085",
        "s9-wailin" to "cherry-089",
        "new-bk-six" to "new-bk-six",
        "new-born-50" to "new-born-50",
    )

    fun libraryRuleId(successRuleId: String, legacyArchive: Boolean = false): String? {
        // Born IDs include their input origin as a suffix. Strip only this
        // stable suffix, never match by a rule's display name.
        val normalized = successRuleId.substringBefore('@')
        if (!legacyArchive) return currentDirect[normalized]
        return legacyDirect[normalized] ?: when {
            doubleWheel.matches(normalized) -> "cherry-021#wheel"
            doubleBorn.matches(normalized) -> "cherry-021#born"
            else -> null
        }
    }

    /** Only the cross-listed 021 table needs a distinct presentation key. */
    fun isSupportedSource(bookId: String, sourceId: String, legacyArchive: Boolean = false): Boolean {
        val scoreKey = entryScoreKey(bookId, sourceId)
        return if (!legacyArchive) scoreKey in currentDirect.values
        else scoreKey in legacyDirect.values ||
            scoreKey == "cherry-021#wheel" || scoreKey == "cherry-021#born"
    }

    fun entryScoreKey(bookId: String, ruleId: String): String = when {
        ruleId == "cherry-021" && bookId == "category-double" -> "cherry-021#wheel"
        ruleId == "cherry-021" && bookId == "category-born" -> "cherry-021#born"
        else -> ruleId
    }

    fun scored(
        snapshots: List<CherrySuccessSnapshot>,
        confirmedHistory: List<HistoricDraw>,
        allowedLibraryIds: Set<String>,
    ): Map<String, CherryLibrarySuccessScore> {
        // Same confirmed-draw matching semantics as Better Data -> Success.
        // In particular: never retroactively generate a missing snapshot.
        val verified = CherryVerifiedHistory.results(confirmedHistory)
        return scoredCompleted(
            CherrySuccessEngine.completed(snapshots, verified),
            allowedLibraryIds,
        )
    }

    fun scoredCompleted(
        draws: List<CherrySuccessDraw>,
        allowedLibraryIds: Set<String>,
        legacyArchive: Boolean = false,
    ): Map<String, CherryLibrarySuccessScore> {
        val grouped = linkedMapOf<String, MutableMap<DrawIdentity, MutableList<CherrySuccessCheck>>>()
        for (draw in draws) {
            val version = draw.snapshot.sourceVersion
            if (legacyArchive) {
                if (version !in setOf("cherry-success-snapshot-v1", "cherry-better-data-v3.2-success-v1")) continue
            } else if (version != CherrySuccessEngine.CURRENT_RULE_VERSION) continue
            for (check in draw.checks) {
                if (check.rule.tab != CherryTab.CURRENT &&
                    check.rule.tab != CherryTab.BORN) continue
                if (check.rule.criterion != CherrySuccessCriterion.PAIR) continue
                if (!legacyArchive && check.rule.tab != CherryTab.CURRENT) continue
                val libraryId = libraryRuleId(check.rule.id, legacyArchive) ?: continue
                if (libraryId.substringBefore('#') !in allowedLibraryIds) continue
                grouped.getOrPut(libraryId) { linkedMapOf() }
                    .getOrPut(draw.snapshot.target) { mutableListOf() }.add(check)
            }
        }
        return grouped.mapValues { (id, perDraw) ->
            val events = perDraw.map { (target, checks) ->
                CherryLibrarySuccessEvent(
                    target = target,
                    winning = checks.first().result,
                    correct = checks.any { it.correct },
                    pairCount = checks.flatMap { it.rule.predictions }.distinct().size,
                    tabs = checks.map { it.rule.tab }.toSet(),
                )
            }.sortedWith(
                compareByDescending<CherryLibrarySuccessEvent> { it.target.date }
                    .thenByDescending { it.target.session.ordinal }
            )
            CherryLibrarySuccessScore(
                libraryRuleId = id,
                correct = events.count { it.correct },
                eligible = events.size,
                events = events,
            )
        }
    }

    fun successful(
        scores: Map<String, CherryLibrarySuccessScore>,
    ): List<CherryLibrarySuccessScore> = scores.values
        .filter { it.correct > 0 }
        .sortedWith(compareByDescending<CherryLibrarySuccessScore> { it.correct }
            .thenByDescending { it.percent }
            .thenByDescending { it.eligible }
            .thenBy { it.libraryRuleId })
}
