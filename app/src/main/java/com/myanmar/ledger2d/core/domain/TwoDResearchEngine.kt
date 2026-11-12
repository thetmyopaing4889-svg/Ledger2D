package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Read-only, evidence-first historical research.
 *
 * Query conditions:
 *  - Source is an exact confirmed 00..99 result, optionally limited to AM or PM.
 *  - Relationship is same-day AM -> PM, or the next scheduled open draw.
 *  - An optional exact target turns a discovery query into a match query.
 *
 * A missing scheduled result is UNKNOWN, never a negative match. Verified closed
 * dates are skipped; an unverified missing draw must not be skipped.
 */
enum class TwoDResearchScope { AM, PM, ALL }
enum class TwoDResearchRelationship { SAME_DAY_PM, NEXT_DRAW }
enum class TwoDResearchStatus { OBSERVED, MATCH, OTHER, MISSING, PENDING, OUT_OF_RANGE }

/**
 * A typed condition instead of an arbitrary string expression.
 *
 * PAIR checks an exact 00–99 result. DIGIT checks either position, whereas
 * TENS and ONES are positional. PARITY uses tens-before-ones E/O classes.
 * DOUBLE checks 00,11,...,99; its value is deliberately unused.
 */
enum class TwoDResearchCriterionKind { PAIR, DIGIT, TENS, ONES, DOUBLE, PARITY }

data class TwoDResearchCriterion(
    val kind: TwoDResearchCriterionKind,
    val value: String = "",
) {
    fun valid(): Boolean = when (kind) {
        TwoDResearchCriterionKind.PAIR -> value.length == 2 &&
            value.all { it in '0'..'9' }
        TwoDResearchCriterionKind.DIGIT, TwoDResearchCriterionKind.TENS,
        TwoDResearchCriterionKind.ONES -> value.length == 1 && value[0] in '0'..'9'
        TwoDResearchCriterionKind.DOUBLE -> value.isEmpty()
        TwoDResearchCriterionKind.PARITY -> value in setOf("EE", "EO", "OE", "OO")
    }

    fun matches(pair: String): Boolean {
        if (!valid() || pair.length != 2 || pair.any { it !in '0'..'9' }) return false
        return when (kind) {
            TwoDResearchCriterionKind.PAIR -> pair == value
            TwoDResearchCriterionKind.DIGIT -> value[0] in pair
            TwoDResearchCriterionKind.TENS -> pair[0] == value[0]
            TwoDResearchCriterionKind.ONES -> pair[1] == value[0]
            TwoDResearchCriterionKind.DOUBLE -> pair[0] == pair[1]
            TwoDResearchCriterionKind.PARITY -> {
                val tens = if ((pair[0] - '0') % 2 == 0) 'E' else 'O'
                val ones = if ((pair[1] - '0') % 2 == 0) 'E' else 'O'
                value == "$tens$ones"
            }
        }
    }
}

data class TwoDResearchQuery(
    val sourcePair: String,
    val scope: TwoDResearchScope,
    val relationship: TwoDResearchRelationship,
    val targetPair: String? = null,
    val range: TwoDHistoryRange = TwoDHistoryRange(),
    val sourceCriterion: TwoDResearchCriterion? = null,
    val targetCriterion: TwoDResearchCriterion? = null,
)

data class TwoDResearchEvidence(
    val source: HistoricDraw,
    val expected: DrawIdentity,
    val following: HistoricDraw?,
    val status: TwoDResearchStatus,
)

data class TwoDResearchReport(
    val query: TwoDResearchQuery,
    val evidence: List<TwoDResearchEvidence>,
    val followingFrequency: List<TwoDCount>,
) {
    val sourceCount: Int get() = evidence.size
    val observedCount: Int get() = evidence.count {
        it.status == TwoDResearchStatus.OBSERVED ||
            it.status == TwoDResearchStatus.MATCH ||
            it.status == TwoDResearchStatus.OTHER
    }
    val matchCount: Int get() = evidence.count { it.status == TwoDResearchStatus.MATCH }

    /**
     * Histogram of actual following pairs that satisfied the selected TARGET.
     * All-outcome followingFrequency remains unchanged, so the lab can offer
     * separate "Matches" and "All outcomes" views without altering evidence.
     * No target means there are no MATCH statuses and this list is empty.
     */
    val matchedFollowingFrequency: List<TwoDCount> by lazy {
        evidence.asSequence()
            .filter { it.status == TwoDResearchStatus.MATCH }
            .mapNotNull { it.following?.digit }
            .groupingBy { it }.eachCount()
            .map { (pair, count) -> TwoDCount(pair, count) }
            .sortedWith(compareByDescending<TwoDCount> { it.count }.thenBy { it.digit })
    }

    val otherCount: Int get() = evidence.count { it.status == TwoDResearchStatus.OTHER }
    val missingCount: Int get() = evidence.count { it.status == TwoDResearchStatus.MISSING }
    val pendingCount: Int get() = evidence.count { it.status == TwoDResearchStatus.PENDING }
    val outsideCount: Int get() = evidence.count { it.status == TwoDResearchStatus.OUT_OF_RANGE }
}

/** No Android, Compose, database writes, randomness or future-outcome inference. */
object TwoDResearchEngine {
    private val chronological = compareBy<HistoricDraw> { it.date }.thenBy { it.session.ordinal }

    private fun validPair(pair: String): Boolean =
        pair.length == 2 && pair.all { it in '0'..'9' }

    private fun isBefore(date: LocalDate, session: DrawSession, cutoff: DrawIdentity): Boolean =
        date.isBefore(cutoff.date) ||
            (date == cutoff.date && session.ordinal < cutoff.session.ordinal)

    /**
     * Keep the Atlas header and every Research query on exactly the same
     * eligibility policy. A confirmed source has a real two-digit value,
     * occupies one open slot and is strictly earlier than the live cutoff.
     */
    private fun eligibleDraws(
        draws: List<HistoricDraw>,
        cutoffExclusive: DrawIdentity,
        verifiedClosedDays: Set<LocalDate>,
    ): List<HistoricDraw> = draws.asSequence()
        .filter { validPair(it.digit) }
        .filter { DrawSchedule.isWorkingDate(it.date, verifiedClosedDays) }
        .filter { isBefore(it.date, it.session, cutoffExclusive) }
        .distinctBy { it.date to it.session }
        .sortedWith(chronological)
        .toList()

    /** Number of verified, usable historical slots shown in the Atlas hero. */
    fun eligibleHistoryCount(
        draws: List<HistoricDraw>,
        cutoffExclusive: DrawIdentity,
        verifiedClosedDays: Set<LocalDate> = emptySet(),
    ): Int = eligibleDraws(draws, cutoffExclusive, verifiedClosedDays).size

    private fun nextOpenDate(date: LocalDate, closed: Set<LocalDate>): LocalDate {
        var candidate = date.plusDays(1)
        while (!DrawSchedule.isWorkingDate(candidate, closed)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    /** Exposed for isolated schedule/closure unit tests. */
    fun followingSlot(
        source: HistoricDraw,
        relationship: TwoDResearchRelationship,
        closed: Set<LocalDate> = emptySet(),
    ): DrawIdentity = when (relationship) {
        TwoDResearchRelationship.SAME_DAY_PM -> {
            require(source.session == DrawSession.MORNING) {
                "Same-day AM to PM requires a morning source"
            }
            DrawIdentity(source.date, DrawSession.EVENING)
        }
        TwoDResearchRelationship.NEXT_DRAW ->
            if (source.session == DrawSession.MORNING) {
                DrawIdentity(source.date, DrawSession.EVENING)
            } else {
                DrawIdentity(nextOpenDate(source.date, closed), DrawSession.MORNING)
            }
    }

    fun search(
        draws: List<HistoricDraw>,
        query: TwoDResearchQuery,
        cutoffExclusive: DrawIdentity,
        verifiedClosedDays: Set<LocalDate> = emptySet(),
    ): TwoDResearchReport {
        val sourceRule = query.sourceCriterion ?: TwoDResearchCriterion(
            TwoDResearchCriterionKind.PAIR, query.sourcePair)
        val targetRule = query.targetCriterion ?: query.targetPair?.let {
            TwoDResearchCriterion(TwoDResearchCriterionKind.PAIR, it)
        }
        require(sourceRule.valid()) { "Invalid source research criterion" }
        require(query.targetPair == null || query.targetCriterion == null) {
            "Supply one target criterion only"
        }
        require(targetRule == null || targetRule.valid()) {
            "Invalid target research criterion"
        }
        require(query.range.start == null || query.range.end == null ||
            !query.range.start.isAfter(query.range.end)) {
            "Invalid research history range"
        }
        require(query.relationship != TwoDResearchRelationship.SAME_DAY_PM ||
            query.scope == TwoDResearchScope.AM) {
            "Same-day AM to PM requires AM source scope"
        }

        // The caller passes the app's already reconciled verified result stream.
        // Preserve its authoritative per-slot row while filtering invalid or future data.
        val valid = eligibleDraws(draws, cutoffExclusive, verifiedClosedDays)
        val bySlot = valid.associateBy { it.date to it.session }
        val sources = valid.filter { row ->
            sourceRule.matches(row.digit) && query.range.includes(row.date) &&
                when (query.scope) {
                    TwoDResearchScope.AM -> row.session == DrawSession.MORNING
                    TwoDResearchScope.PM -> row.session == DrawSession.EVENING
                    TwoDResearchScope.ALL -> true
                }
        }
        val evidence = sources.map { source ->
            val slot = followingSlot(source, query.relationship, verifiedClosedDays)
            val withinRange = query.range.includes(slot.date)
            val arrived = isBefore(slot.date, slot.session, cutoffExclusive)
            val following = if (withinRange && arrived)
                bySlot[slot.date to slot.session] else null
            val status = when {
                !withinRange -> TwoDResearchStatus.OUT_OF_RANGE
                !arrived -> TwoDResearchStatus.PENDING
                following == null -> TwoDResearchStatus.MISSING
                targetRule == null -> TwoDResearchStatus.OBSERVED
                targetRule.matches(following.digit) -> TwoDResearchStatus.MATCH
                else -> TwoDResearchStatus.OTHER
            }
            TwoDResearchEvidence(source, slot, following, status)
        }
        val occurrences = evidence.mapNotNull { it.following?.digit }
            .groupingBy { it }.eachCount()
        val frequencies = occurrences.map { (pair, amount) -> TwoDCount(pair, amount) }
            .sortedWith(compareByDescending<TwoDCount> { it.count }.thenBy { it.digit })
        return TwoDResearchReport(query, evidence, frequencies)
    }
}
