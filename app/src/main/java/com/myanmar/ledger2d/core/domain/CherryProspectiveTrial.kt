package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Experimental, pure-domain prospective observation ledger.
 *
 * An entry must be frozen before its session cutoff and can be evaluated only
 * after that cutoff. All three categories (including NO_SELECTION) are frozen
 * together, so an empty category is not selectively omitted after a miss.
 *
 * SECURITY LIMIT: a caller-supplied local Instant and in-memory immutable log
 * are NOT an independently verifiable timestamp or durable audit trail.
 * This cannot by itself prove a historical lock actually preceded an outcome.
 * Integrate a trusted append-only store / timestamp authority separately.
 */
data class CherryProspectiveChoice(
    val kind: CherryDiscoveryKind,
    /** Null represents an explicitly recorded abstention. */
    val formulaId: String?,
    /** Keep the exact discovered expression and offset for later audit. */
    val expression: String?,
    val offset: Int?,
    val outputDigit: Int?,
    val historicalFitStreak: Int?,
    val discoveredAtStage: Int?,
) {
    init {
        if (formulaId == null) {
            require(expression == null && offset == null &&
                outputDigit == null && historicalFitStreak == null &&
                discoveredAtStage == null)
        } else {
            require(formulaId.isNotBlank())
            require(!expression.isNullOrBlank())
            require(offset != null && offset in 0..9)
            require(outputDigit != null && outputDigit in 0..9)
            require(historicalFitStreak != null && historicalFitStreak >=
                CherryFormulaDiscoveryEngine.MIN_STREAK)
            require(discoveredAtStage != null && discoveredAtStage in
                1..CherryFormulaDiscoveryEngine.STAGE_COUNT)
        }
    }
}

data class CherryProspectiveLockedTrial(
    val target: DrawIdentity,
    val lockedAt: Instant,
    val cutoffAt: Instant,
    /** SHA-256 fingerprint of only the data available during discovery. */
    val historyFingerprint: String,
    val completedStages: Int,
    val choices: List<CherryProspectiveChoice>,
    val evidenceLabel: String = "LOCAL_PRE_RESULT_LOCK_UNVERIFIED",
)

data class CherryProspectiveOutcome(
    val kind: CherryDiscoveryKind,
    /** Null = abstention, not a missed prediction. */
    val hit: Boolean?,
)

data class CherryProspectiveEvaluation(
    val target: DrawIdentity,
    val actualPair: String,
    val resultSource: HistoricSource,
    val evaluatedAt: Instant,
    val outcomes: List<CherryProspectiveOutcome>,
)

data class CherryProspectiveStats(
    val kind: CherryDiscoveryKind,
    val lockedTargets: Int,
    val completedTargets: Int,
    val pendingTargets: Int,
    val checked: Int,
    val hits: Int,
    val abstentions: Int,
) {
    val checkedHitRate: Double? get() =
        if (checked == 0) null else hits.toDouble() / checked
}

/** New values are returned rather than modifying earlier snapshots. */
class CherryProspectiveTrialJournal private constructor(
    private val locks: List<CherryProspectiveLockedTrial>,
    private val evaluations: List<CherryProspectiveEvaluation>,
) {
    companion object {
        fun empty(): CherryProspectiveTrialJournal =
            CherryProspectiveTrialJournal(emptyList(), emptyList())

        /**
         * Restore a previously persisted local journal without allowing a
         * malformed or edited historical lock to be accepted silently.
         * This checks internal consistency; it is NOT a trusted timestamp.
         */
        fun restore(
            locked: List<CherryProspectiveLockedTrial>,
            evaluated: List<CherryProspectiveEvaluation>,
        ): CherryProspectiveTrialJournal {
            require(locked.map { it.target }.distinct().size == locked.size) {
                "Duplicate locked draw"
            }
            require(evaluated.map { it.target }.distinct().size == evaluated.size) {
                "Duplicate settled draw"
            }
            val byTarget = locked.associateBy { it.target }
            for (lock in locked) {
                require(lock.cutoffAt == CherryProspectiveTrialClock.cutoff(lock.target) &&
                    lock.lockedAt.isBefore(lock.cutoffAt)) {
                    "Invalid pre-result local lock boundary"
                }
                require(lock.historyFingerprint.matches(Regex("[0-9a-f]{64}")) &&
                    lock.completedStages in 1..CherryFormulaDiscoveryEngine.STAGE_COUNT &&
                    lock.evidenceLabel == "LOCAL_PRE_RESULT_LOCK_UNVERIFIED") {
                    "Invalid locked formula provenance"
                }
                require(lock.choices.map { it.kind } == CherryDiscoveryKind.entries) {
                    "A lock must preserve one choice or abstention for every kind"
                }
            }
            for (record in evaluated) {
                val lock = requireNotNull(byTarget[record.target]) {
                    "Settled result has no original lock"
                }
                require(validHistoric2D(record.actualPair) &&
                    record.resultSource in setOf(HistoricSource.WINNER, HistoricSource.DAILY) &&
                    !record.evaluatedAt.isBefore(lock.cutoffAt) &&
                    !record.evaluatedAt.isBefore(lock.lockedAt)) {
                    "Invalid confirmed result or verification time"
                }
                val expected = lock.choices.map { choice ->
                    CherryProspectiveOutcome(
                        choice.kind,
                        choice.outputDigit?.let { digit ->
                            CherryDiscoveryWalkForward.matches(
                                choice.kind, digit, record.actualPair)
                        },
                    )
                }
                require(record.outcomes == expected) {
                    "Stored hit/miss does not match the original locked digit"
                }
            }
            val fixedLocks = locked.map { lock ->
                lock.copy(choices = java.util.Collections.unmodifiableList(
                    ArrayList(lock.choices)))
            }
            val fixedResults = evaluated.map { record ->
                record.copy(outcomes = java.util.Collections.unmodifiableList(
                    ArrayList(record.outcomes)))
            }
            return CherryProspectiveTrialJournal(fixedLocks, fixedResults)
        }
    }

    fun lockedTrials(): List<CherryProspectiveLockedTrial> = locks.toList()
    fun verifiedResults(): List<CherryProspectiveEvaluation> = evaluations.toList()

    /**
     * Freeze one and only one chosen formula (or explicit abstention) for
     * EACH of LONE / WAM / BREAK, at the same instant.
     *
     * Null selected IDs are intentional abstentions. Every key is mandatory.
     * Caller may nominate one candidate from the immutable discovered session,
     * otherwise the default choice is the user-approved longest fit.
     */
    fun lock(
        session: CherryDiscoverySession,
        lockedAt: Instant,
        selectedIds: Map<CherryDiscoveryKind, String?> =
            CherryDiscoveryKind.entries.associateWith { kind ->
                session.longest(kind)?.id
            },
        closedDates: Set<LocalDate> = emptySet(),
    ): CherryProspectiveTrialJournal {
        require(locks.none { it.target == session.target }) {
            "Target already locked: a prior snapshot cannot be replaced"
        }
        require(session.completedStages > 0 && session.hasImmediatePreviousDraw) {
            "Cannot lock an incomplete discovery session"
        }
        require(session.historySignature.isNotBlank())
        require(DrawSchedule.isWorkingDate(session.target.date, closedDates)) {
            "Target is not a working draw day"
        }
        require(selectedIds.keys == CherryDiscoveryKind.entries.toSet()) {
            "Every category must be locked, including abstentions"
        }
        val cutoff = CherryProspectiveTrialClock.cutoff(session.target)
        require(lockedAt.isBefore(cutoff)) {
            "A prediction registered at/after the cutoff is not prospective"
        }
        val choices = CherryDiscoveryKind.entries.map { kind ->
            val id = selectedIds[kind]
            val formula = if (id == null) null else
                session.formulas.singleOrNull { it.kind == kind && it.id == id }
                    ?: throw IllegalArgumentException(
                        "Selection is not in the frozen discovery session: $kind/$id")
            CherryProspectiveChoice(
                kind, formula?.id, formula?.expression, formula?.offset,
                formula?.currentDigit, formula?.historicalStreak,
                formula?.discoveredAtStage,
            )
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(session.historySignature.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        val lock = CherryProspectiveLockedTrial(
            target = session.target,
            lockedAt = lockedAt,
            cutoffAt = cutoff,
            historyFingerprint = digest,
            completedStages = session.completedStages,
            choices = java.util.Collections.unmodifiableList(ArrayList(choices)),
        )
        return CherryProspectiveTrialJournal(locks + lock, evaluations)
    }

    /**
     * Freeze a user-approved Search More alternative without requiring it to
     * occupy the engine's 24 representative leaderboard slots.
     *
     * Only the current offered candidate or the explicitly retained best
     * candidate may be selected. Every category must be represented; null is
     * an explicit abstention. All cursors must refer to the same settled
     * history fingerprint and target. Does not silently change an old lock.
     */
    fun lockFromSearchCursors(
        cursors: Map<CherryDiscoveryKind, CherryDiscoverySearchCursor?>,
        selectedIds: Map<CherryDiscoveryKind, String?>,
        lockedAt: Instant,
        closedDates: Set<LocalDate> = emptySet(),
    ): CherryProspectiveTrialJournal {
        val kinds = CherryDiscoveryKind.entries.toSet()
        require(cursors.keys == kinds && selectedIds.keys == kinds) {
            "All three categories must be included, including abstentions"
        }
        val available = cursors.values.filterNotNull()
        require(available.isNotEmpty()) { "A discovery session is required" }
        val reference = available.first().engine
        require(reference.hasImmediatePreviousDraw && reference.completedStages > 0) {
            "Only valid evaluated discovery sessions can be locked"
        }
        require(available.all {
            it.kind in kinds && it.engine.target == reference.target &&
                it.engine.historySignature == reference.historySignature &&
                it.engine.hasImmediatePreviousDraw
        }) {
            "Cannot combine different target sessions or corrected histories"
        }
        val selected = CherryDiscoveryKind.entries.mapNotNull { kind ->
            val id = selectedIds.getValue(kind) ?: return@mapNotNull null
            val cursor = requireNotNull(cursors.getValue(kind)) {
                "Selected category requires its discovery cursor"
            }
            require(cursor.kind == kind && id in cursor.offeredIds) {
                "The chosen formula was not offered for this category"
            }
            val candidate = listOfNotNull(
                cursor.newCandidate, cursor.bestCandidate)
                .firstOrNull { it.kind == kind && it.id == id }
            requireNotNull(candidate) {
                "Only the current offered formula or preserved best can be locked"
            }
        }
        val snapshot = reference.copy(
            completedStages = available.maxOf { it.engine.completedStages },
            formulas = selected,
        )
        return lock(snapshot, lockedAt, selectedIds, closedDates)
    }

    /**
     * Outcome is always compared with the previously frozen digit.
     * A completed trial is NEVER reopened, even to record the same pair.
     */
    fun evaluate(
        actual: HistoricDraw,
        evaluatedAt: Instant,
    ): CherryProspectiveTrialJournal {
        require(validHistoric2D(actual.digit)) { "Actual outcome must be exact 2D pair" }
        require(actual.source == HistoricSource.WINNER ||
            actual.source == HistoricSource.DAILY) {
            "Unverified historical imports cannot settle prospective trials"
        }
        val target = DrawIdentity(actual.date, actual.session)
        val lock = locks.singleOrNull { it.target == target }
            ?: throw IllegalArgumentException("No pre-result lock for target")
        require(evaluations.none { it.target == target }) {
            "Settled trial cannot be re-evaluated or overwritten"
        }
        require(!evaluatedAt.isBefore(lock.cutoffAt) &&
            !evaluatedAt.isBefore(lock.lockedAt)) {
            "Outcome may be evaluated only after session cutoff"
        }
        val outcomes = lock.choices.map { choice ->
            CherryProspectiveOutcome(
                choice.kind,
                choice.outputDigit?.let { digit ->
                    CherryDiscoveryWalkForward.matches(
                        choice.kind, digit, actual.digit)
                },
            )
        }
        val evaluation = CherryProspectiveEvaluation(
            target, actual.digit, actual.source, evaluatedAt,
            java.util.Collections.unmodifiableList(ArrayList(outcomes)),
        )
        return CherryProspectiveTrialJournal(locks, evaluations + evaluation)
    }

    /** Pending choices never count as misses; abstentions do not boost accuracy. */
    fun stats(kind: CherryDiscoveryKind): CherryProspectiveStats {
        val finished = evaluations.associateBy { it.target }
        val actual = locks.mapNotNull { lock ->
            finished[lock.target]?.let { done ->
                lock to done.outcomes.single { it.kind == kind }.hit
            }
        }
        return CherryProspectiveStats(
            kind = kind,
            lockedTargets = locks.size,
            completedTargets = actual.size,
            pendingTargets = locks.size - actual.size,
            checked = actual.count { it.second != null },
            hits = actual.count { it.second == true },
            abstentions = actual.count { it.second == null },
        )
    }
}

/**
 * Asia/Yangon is explicit: the device's Singapore, UTC or system timezone
 * must never change the draw cutoff. Morning is locked before 12:00,
 * evening before 16:30. Time injection is for tests, not attestation.
 */
object CherryProspectiveTrialClock {
    private val zone = ZoneId.of("Asia/Yangon")

    fun cutoff(target: DrawIdentity): Instant =
        target.date.atTime(
            when (target.session) {
                DrawSession.MORNING -> DrawSchedule.morningBetCutoff
                DrawSession.EVENING -> DrawSchedule.eveningResultTime
            }
        ).atZone(zone).toInstant()
}
