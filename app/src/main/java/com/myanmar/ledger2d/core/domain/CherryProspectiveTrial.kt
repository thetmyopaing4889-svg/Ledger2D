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
    val outputDigit: Int?,
    val historicalFitStreak: Int?,
    val discoveredAtStage: Int?,
) {
    init {
        if (formulaId == null) {
            require(outputDigit == null && historicalFitStreak == null &&
                discoveredAtStage == null)
        } else {
            require(formulaId.isNotBlank())
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
                kind, formula?.id, formula?.currentDigit,
                formula?.historicalStreak, formula?.discoveredAtStage,
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
