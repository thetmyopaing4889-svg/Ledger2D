package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Eight read-only historical checks for the selected (not-yet-final) draw.
 * All methods use VERIFIED draws before target; no invented or predictive values.
 */
data class TwoDHistoryRange(val start: LocalDate? = null, val end: LocalDate? = null) {
    fun includes(date: LocalDate): Boolean =
        (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end))
}

data class TwoDCount(val digit: String, val count: Int)
data class TwoDMatch(val date: LocalDate, val session: DrawSession, val digit: String)

data class TwoDSameSlot(
    val samples: Int,
    val numbers: List<TwoDCount>,
    val singles: List<TwoDCount>,
    val tens: List<TwoDCount>,
    val ones: List<TwoDCount>,
    val parity: Map<String, Int>,
    val groups: Map<String, Int>,
    val unseenNumbers: List<String>,
    /** Verified same-weekday/same-session historical evidence for each exact 2D pair. */
    val numberOccurrences: Map<String, List<TwoDMatch>> = emptyMap(),
    /** One record per draw; an equal double such as 55 contributes two digit appearances. */
    val digitOccurrences: Map<String, List<TwoDMatch>> = emptyMap(),
    val recordedResults: List<TwoDMatch> = emptyList(),
)

data class TwoDPatternWeekEvidence(
    val weekStart: LocalDate,
    val preceding: List<TwoDMatch>,
    val following: TwoDMatch,
)
data class TwoDPattern(
    val eligible: Boolean,
    val checkedPrefix: List<String>,
    val matched: List<TwoDMatch>,
    val nextParity: Map<String, Int>,
    val currentPreceding: List<TwoDMatch> = emptyList(),
    val matchingWeeks: List<TwoDPatternWeekEvidence> = emptyList(),
    /** Matching ordered prefixes whose following result was not recorded. */
    val missingFollowingCount: Int = 0,
)

data class TwoDStreak(
    val value: String,
    val observedDrawsSince: Int,
    val lastSeen: TwoDMatch?,
    /** A never-seen streak is only a lower bound within available history. */
    val historyStartTruncated: Boolean,
)
data class TwoDStreakStream(
    val observedDraws: Int,
    val hasUnknownOpenSlots: Boolean,
    val fullNumbers: List<TwoDStreak>,
    val singleDigits: List<TwoDStreak>,
)

data class TwoDTransition(
    val result: TwoDMatch,
    val newTens: Boolean,
    val newOnes: Boolean,
    val newlySeenDigits: Set<Char>,
    /** False if an earlier open result of this week is unknown. */
    val classificationVerified: Boolean,
)
data class TwoDWeekFlow(
    val transitions: List<TwoDTransition>,
    val remainingDigits: List<Char>,
    /** Missing open slots prevent a definitive claim of complete weekly absence. */
    val completeThroughTarget: Boolean,
)

data class TwoDWeekSpecial(
    val observedDraws: Int,
    val completeThroughTarget: Boolean,
    val groups: Map<String, Int>,
    val groupOccurrences: Map<String, List<TwoDMatch>>,
    /** Exact same 2D returning: every appearance after its first counts. */
    val repeatedNumbers: List<TwoDCount>,
    /** Include first occurrence and subsequent repeated occurrences in chronological order. */
    val repeatedOccurrences: Map<String, List<TwoDMatch>> = emptyMap(),
)

data class TwoDWeekReview(
    val weekStart: LocalDate,
    val observedDraws: Int,
    val expectedOpenDraws: Int,
    val complete: Boolean,
    val singleDigitFrequency: List<TwoDCount>,
    val remainingDigits: List<Char>,
    val parity: Map<String, Int>,
    val groups: Map<String, Int>,
    val repeatedNumbers: List<TwoDCount>,
    val recordedResults: List<TwoDMatch> = emptyList(),
)

data class TwoDReturnCompanion(
    val companion: Char,
    val count: Int,
)
data class TwoDMissingReturnEpisode(
    val missingWeekStart: LocalDate,
    val followingWeekFirstReturn: TwoDMatch,
)
data class TwoDMissingDigitReturn(
    val digit: Char,
    val thisWeekFirstSeen: TwoDMatch?,
    val firstSeenVerified: Boolean,
    /** Completed historical weeks where the digit is verified entirely absent. */
    val historicalMissingWeeks: Int,
    /** Fully observed following weeks with no occurrence of the digit. */
    val historicalNoReturnWeeks: Int,
    /** Following weeks with incomplete/uncertain first return or no complete outcome. */
    val historicalUnknownNextWeeks: Int,
    val historyFirstReturnEpisodes: Int,
    val commonCompanions: List<TwoDReturnCompanion>,
    val historicalFirstReturns: List<TwoDMatch>,
    val historicalEpisodes: List<TwoDMissingReturnEpisode> = emptyList(),
)
data class TwoDMissingReturn(
    val previousWeekComplete: Boolean,
    val missingDigits: List<Char>,
    val results: List<TwoDMissingDigitReturn>,
)

data class TwoDReferenceChain(
    val precedingFirst: TwoDMatch?,
    val precedingSecond: TwoDMatch?,
    val following: TwoDMatch,
)
data class TwoDConditional(
    val sourceDigit: String?,
    val matches: List<TwoDMatch>,
    val frequencies: List<TwoDCount>,
    /** Historical source matches whose corresponding target result is unknown. */
    val missingTargetCount: Int,
    val referenceChains: List<TwoDReferenceChain> = emptyList(),
)
data class TwoDPreviousResults(
    val firstReference: TwoDMatch?,
    val secondReference: TwoDMatch?,
    val both: TwoDConditional,
    val firstOnly: TwoDConditional,
    val secondOnly: TwoDConditional,
)

data class TwoDAnalysisReport(
    val target: DrawIdentity,
    /** Group 1 — current occasion (4 checks). */
    val a02: TwoDSameSlot,
    val a03: TwoDPattern,
    val a06: Map<String, TwoDStreakStream>,
    val a08: TwoDPreviousResults,
    /** Group 2 — current week (2 checks). */
    val a05: TwoDWeekFlow,
    val a07: TwoDWeekSpecial,
    /** Group 3 — previous week (2 checks). */
    val a04: TwoDWeekReview,
    val a01: TwoDMissingReturn,
)

/**
 * No Room, Compose, network, or wall clock dependencies.
 * All occurrences are scoped by the selected target (exclusive).
 */
object TwoDAnalysisEngine {
    private val chronological = compareBy<HistoricDraw> { it.date }.thenBy { it.session.ordinal }
    private val allDigits = ('0'..'9').toList()
    private val parityNames = listOf("စုံစုံ", "မမ", "စုံမ", "မစုံ")

    fun calculate(
        allDraws: List<HistoricDraw>,
        target: DrawIdentity,
        verifiedClosedDays: Set<LocalDate> = emptySet(),
        historyRange: TwoDHistoryRange = TwoDHistoryRange(),
    ): TwoDAnalysisReport {
        val known = allDraws.asSequence()
            .filter { validHistoric2D(it.digit) && it.date !in verifiedClosedDays }
            .filter { isWeekday(it.date) && isBefore(it.date, it.session, target) }
            .distinctBy { it.date to it.session }
            .sortedWith(chronological)
            .toList()
        val bySlot = known.associateBy { it.date to it.session }
        val currentMonday = monday(target.date)
        val lastMonday = currentMonday.minusWeeks(1)
        val thisWeek = known.filter { !it.date.isBefore(currentMonday) }
        val previousWeek = known.filter { it.date >= lastMonday && it.date < currentMonday }
        val historical = known.filter { historyRange.includes(it.date) }
        val a04 = review(lastMonday, previousWeek, bySlot, verifiedClosedDays)
        val currentWeekSlots = slotsForWeek(currentMonday, verifiedClosedDays)
            .filter { isBefore(it.first, it.second, target) }
        val currentWeekComplete = currentWeekSlots.all { it in bySlot }
        return TwoDAnalysisReport(
            target = target,
            a02 = sameSlot(historical, target),
            a03 = pattern(known, bySlot, target, verifiedClosedDays, historyRange),
            a06 = streaks(historical, target, verifiedClosedDays, historyRange),
            a08 = priorResults(historical, bySlot, target, verifiedClosedDays, historyRange),
            a05 = flow(thisWeek, currentWeekSlots),
            a07 = special(thisWeek, currentWeekComplete),
            a04 = a04,
            a01 = returns(known, bySlot, a04, currentMonday, target, verifiedClosedDays, historyRange),
        )
    }

    private fun isWeekday(date: LocalDate) =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    private fun isBefore(date: LocalDate, session: DrawSession, target: DrawIdentity) =
        date.isBefore(target.date) || (date == target.date && session.ordinal < target.session.ordinal)

    private fun monday(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    private fun slotsForWeek(start: LocalDate, closed: Set<LocalDate>): List<Pair<LocalDate, DrawSession>> =
        (0L..4L).flatMap { d ->
            val day = start.plusDays(d)
            if (day in closed) emptyList()
            else listOf(day to DrawSession.MORNING, day to DrawSession.EVENING)
        }

    private fun numberCounts(values: List<String>, includeZero: Boolean = false): List<TwoDCount> {
        val counts = values.groupingBy { it }.eachCount()
        return (if (includeZero) (0..99).map { it.toString().padStart(2, '0') } else counts.keys.toList())
            .map { TwoDCount(it, counts[it] ?: 0) }
            .sortedWith(compareByDescending<TwoDCount> { it.count }.thenBy { it.digit })
    }

    private fun singles(values: List<String>): List<TwoDCount> =
        allDigits.map { digit ->
            TwoDCount(digit.toString(), values.sumOf { n -> n.count { it == digit } })
        }.sortedWith(compareByDescending<TwoDCount> { it.count }.thenBy { it.digit })

    private fun positionCounts(values: List<String>, position: Int): List<TwoDCount> =
        allDigits.map { digit ->
            TwoDCount(digit.toString(), values.count { it[position] == digit })
        }.sortedWith(compareByDescending<TwoDCount> { it.count }.thenBy { it.digit })

    private fun parityCounts(values: List<String>): Map<String, Int> {
        val counts = values.groupingBy { TwoDigitGroups.parity(it) }.eachCount()
        return parityNames.associateWith { counts[it] ?: 0 }
    }

    private fun repeats(values: List<String>): List<TwoDCount> =
        numberCounts(values).filter { it.count > 1 }
            .map { it.copy(count = it.count - 1) }

    /** A-02: same weekday + same AM/PM, counting BOTH digit positions. */
    private fun sameSlot(historical: List<HistoricDraw>, target: DrawIdentity): TwoDSameSlot {
        val candidates = historical.filter {
            it.date.dayOfWeek == target.date.dayOfWeek && it.session == target.session
        }
        val digits = candidates.map { it.digit }
        return TwoDSameSlot(
            samples = candidates.size,
            numbers = numberCounts(digits).filter { it.count > 0 },
            singles = singles(digits),
            tens = positionCounts(digits, 0),
            ones = positionCounts(digits, 1),
            parity = parityCounts(digits),
            groups = TwoDigitGroups.counts(digits),
            unseenNumbers = numberCounts(digits, includeZero = true).filter { it.count == 0 }.map { it.digit },
            numberOccurrences = candidates.groupBy { it.digit }.mapValues { (_, occurrences) ->
                occurrences.sortedWith(chronological).map { row ->
                    TwoDMatch(row.date, row.session, row.digit)
                }
            },
            digitOccurrences = allDigits.associate { digit ->
                digit.toString() to candidates.filter { digit in it.digit }.map { row ->
                    TwoDMatch(row.date, row.session, row.digit)
                }
            },
            recordedResults = candidates.map { TwoDMatch(it.date, it.session, it.digit) },
        )
    }

    /** A-03: an exactly slot-aligned Monday-anchored weekly parity prefix. */
    private fun pattern(
        known: List<HistoricDraw>,
        bySlot: Map<Pair<LocalDate, DrawSession>, HistoricDraw>,
        target: DrawIdentity,
        closed: Set<LocalDate>,
        range: TwoDHistoryRange,
    ): TwoDPattern {
        val start = monday(target.date)
        val prefixSlots = slotsForWeek(start, emptySet()).takeWhile {
            it.first.isBefore(target.date) || (it.first == target.date && it.second.ordinal < target.session.ordinal)
        }
        if (prefixSlots.size < 4 || (0L..4L).any { start.plusDays(it) in closed }) {
            return TwoDPattern(false, emptyList(), emptyList(), emptyMap())
        }
        val prefix = prefixSlots.map { bySlot[it] ?: return TwoDPattern(false, emptyList(), emptyList(), emptyMap()) }
        val weeklyCandidates = known.map { monday(it.date) }.distinct().filter { it < start }
        var missingFollowingCount = 0
        val matchingWeeks = weeklyCandidates.mapNotNull { candidateStart ->
            if ((0L..4L).any { candidateStart.plusDays(it) in closed }) return@mapNotNull null
            val candidateTarget = candidateStart.plusDays(ChronoUnit.DAYS.between(start, target.date))
            if (!range.includes(candidateTarget)) return@mapNotNull null
            val earlier = prefixSlots.map { (date, slot) ->
                bySlot[candidateStart.plusDays(ChronoUnit.DAYS.between(start, date)) to slot]
                    ?: return@mapNotNull null
            }
            if (!earlier.zip(prefix).all { (a, b) ->
                    TwoDigitGroups.parity(a.digit) == TwoDigitGroups.parity(b.digit)
                }) return@mapNotNull null
            // A matching prefix is evidence even if its following result is absent.
            // Only verified following outcomes contribute to next-parity frequencies.
            val next = bySlot[candidateTarget to target.session]
            if (next == null) {
                missingFollowingCount++
                return@mapNotNull null
            }
            TwoDPatternWeekEvidence(
                candidateStart,
                earlier.map { TwoDMatch(it.date, it.session, it.digit) },
                TwoDMatch(next.date, next.session, next.digit),
            )
        }
        val matched = matchingWeeks.map { it.following }
        val classes = matched.map { TwoDigitGroups.parity(it.digit) }
        return TwoDPattern(
            true, prefix.map { TwoDigitGroups.parity(it.digit) },
            matched, parityNames.associateWith { clazz -> classes.count { it == clazz } },
            currentPreceding = prefix.map { TwoDMatch(it.date, it.session, it.digit) },
            matchingWeeks = matchingWeeks,
            missingFollowingCount = missingFollowingCount,
        )
    }

    /** A-04: previous week, missing digits derived from all available recorded draws. */
    private fun review(
        start: LocalDate,
        draws: List<HistoricDraw>,
        bySlot: Map<Pair<LocalDate, DrawSession>, HistoricDraw>,
        closed: Set<LocalDate>,
    ): TwoDWeekReview {
        val expected = slotsForWeek(start, closed)
        val values = draws.map { it.digit }
        val observed = values.flatMap { it.toList() }.toSet()
        return TwoDWeekReview(
            start, draws.size, expected.size, expected.isNotEmpty() && expected.all { it in bySlot },
            singles(values), if (values.isEmpty()) emptyList() else allDigits.filter { it !in observed },
            parityCounts(values), TwoDigitGroups.counts(values), repeats(values),
            draws.map { TwoDMatch(it.date, it.session, it.digit) },
        )
    }

    /** A-05: chronological within-week new/old, tens and ones order never reversed. */
    private fun flow(
        draws: List<HistoricDraw>,
        expectedSlots: List<Pair<LocalDate, DrawSession>>,
    ): TwoDWeekFlow {
        val seen = mutableSetOf<Char>()
        val knownSlots = draws.map { it.date to it.session }.toSet()
        val complete = expectedSlots.all { it in knownSlots }
        val transitions = draws.map { draw ->
            val a = draw.digit[0]
            val b = draw.digit[1]
            val newValues = listOf(a, b).filter { it !in seen }.toSet()
            val earlierFullyObserved = expectedSlots.takeWhile { (day, session) ->
                day.isBefore(draw.date) ||
                    (day == draw.date && session.ordinal < draw.session.ordinal)
            }.all { it in knownSlots }
            val transition = TwoDTransition(
                TwoDMatch(draw.date, draw.session, draw.digit),
                a !in seen, b !in seen, newValues, earlierFullyObserved,
            )
            seen.add(a); seen.add(b)
            transition
        }
        return TwoDWeekFlow(transitions, allDigits.filter { it !in seen }, complete)
    }

    /** A-07: actual current-week occurrences and exact-pair repeat EVENTS. */
    private fun special(draws: List<HistoricDraw>, complete: Boolean): TwoDWeekSpecial {
        val values = draws.map { it.digit }
        val occurrences = TwoDigitGroups.named.mapValues { (_, members) ->
            draws.filter { it.digit in members }.map {
                TwoDMatch(it.date, it.session, it.digit)
            }
        }
        val repeatHistory = draws.groupBy { it.digit }.filterValues { it.size > 1 }
            .mapValues { (_, items) -> items.map { TwoDMatch(it.date, it.session, it.digit) } }
        return TwoDWeekSpecial(
            draws.size, complete, TwoDigitGroups.counts(values), occurrences,
            repeats(values), repeatHistory,
        )
    }

    /** A-06: ongoing absence separately for AM, PM and both, per observed draw. */
    private fun streaks(
        historical: List<HistoricDraw>,
        target: DrawIdentity,
        closed: Set<LocalDate>,
        range: TwoDHistoryRange,
    ): Map<String, TwoDStreakStream> {
        fun scan(rows: List<HistoricDraw>, session: DrawSession?): TwoDStreakStream {
            // A selected end date is an intentional cutoff, not "missing data"
            // between the chosen period and the target occasion.
            val effectiveEnd = if (range.end != null && range.end.isBefore(target.date))
                range.end else target.date
            val first = range.start?.takeIf { !it.isAfter(effectiveEnd) }
                ?: rows.firstOrNull()?.date
            val hasUnknown = if (first == null) false else run {
                var cursor: LocalDate = first
                var gap = false
                val slots = rows.map { it.date to it.session }.toSet()
                while (!cursor.isAfter(effectiveEnd)) {
                    if (isWeekday(cursor) && cursor !in closed) {
                        for (s in DrawSession.entries) {
                            if (session != null && s != session) continue
                            if (isBefore(cursor, s, target) && (cursor to s) !in slots) gap = true
                        }
                    }
                    cursor = cursor.plusDays(1)
                }
                gap
            }
            fun find(value: String, predicate: (String, String) -> Boolean): TwoDStreak {
                val lastIndex = rows.indexOfLast { predicate(it.digit, value) }
                val last = rows.getOrNull(lastIndex)
                return TwoDStreak(
                    value, if (lastIndex < 0) rows.size else rows.lastIndex - lastIndex,
                    last?.let { TwoDMatch(it.date, it.session, it.digit) },
                    lastIndex < 0,
                )
            }
            val allNumbers = (0..99).map { it.toString().padStart(2, '0') }
                .map { find(it) { n, v -> n == v } }
                .sortedWith(compareByDescending<TwoDStreak> { it.observedDrawsSince }.thenBy { it.value })
            val digits = allDigits.map { find(it.toString()) { n, v -> v.any { digit -> digit in n } } }
                .sortedWith(compareByDescending<TwoDStreak> { it.observedDrawsSince }.thenBy { it.value })
            return TwoDStreakStream(rows.size, hasUnknown, allNumbers, digits)
        }
        return linkedMapOf(
            "morning" to scan(historical.filter { it.session == DrawSession.MORNING }, DrawSession.MORNING),
            "evening" to scan(historical.filter { it.session == DrawSession.EVENING }, DrawSession.EVENING),
            "combined" to scan(historical, null),
        )
    }

    private fun previousTradingDay(date: LocalDate, closed: Set<LocalDate>): LocalDate {
        var candidate = date.minusDays(1)
        while (!isWeekday(candidate) || candidate in closed) candidate = candidate.minusDays(1)
        return candidate
    }

    private fun referenceSlots(target: DrawIdentity, closed: Set<LocalDate>)
        : Pair<Pair<LocalDate, DrawSession>, Pair<LocalDate, DrawSession>> {
        val prior = previousTradingDay(target.date, closed)
        return if (target.session == DrawSession.MORNING) {
            (prior to DrawSession.MORNING) to (prior to DrawSession.EVENING)
        } else {
            (prior to DrawSession.EVENING) to (target.date to DrawSession.MORNING)
        }
    }

    /** A-08: all three independently counted conditional searches, same weekday/slot. */
    private fun priorResults(
        historical: List<HistoricDraw>,
        bySlot: Map<Pair<LocalDate, DrawSession>, HistoricDraw>,
        target: DrawIdentity,
        closed: Set<LocalDate>,
        range: TwoDHistoryRange,
    ): TwoDPreviousResults {
        val (aSlot, bSlot) = referenceSlots(target, closed)
        val a = bySlot[aSlot]
        val b = bySlot[bSlot]
        fun match(rows: List<HistoricDraw>, source: String?, missing: Int): TwoDConditional =
            TwoDConditional(
                source, rows.map { TwoDMatch(it.date, it.session, it.digit) },
                numberCounts(rows.map { it.digit }), missing,
                referenceChains = rows.map { row ->
                    val (firstSlot, secondSlot) =
                        referenceSlots(DrawIdentity(row.date, row.session), closed)
                    TwoDReferenceChain(
                        bySlot[firstSlot]?.let { TwoDMatch(it.date, it.session, it.digit) },
                        bySlot[secondSlot]?.let { TwoDMatch(it.date, it.session, it.digit) },
                        TwoDMatch(row.date, row.session, row.digit),
                    )
                },
            )
        val candidates = historical.filter {
            range.includes(it.date) && it.date.dayOfWeek == target.date.dayOfWeek &&
                it.session == target.session && it.date != target.date
        }
        val first = mutableListOf<HistoricDraw>()
        val second = mutableListOf<HistoricDraw>()
        val both = mutableListOf<HistoricDraw>()
        for (candidate in candidates) {
            val (ca, cb) = referenceSlots(DrawIdentity(candidate.date, candidate.session), closed)
            val candidateA = bySlot[ca]
            val candidateB = bySlot[cb]
            val aMatch = a != null && candidateA?.digit == a.digit
            val bMatch = b != null && candidateB?.digit == b.digit
            if (aMatch) first.add(candidate)
            if (bMatch) second.add(candidate)
            if (aMatch && bMatch) both.add(candidate)
        }
        // A historical source sequence can match even when its target 2D is
        // unrecorded. Do not call these "no matching history" or invent outcomes.
        var missingBoth = 0
        var missingFirst = 0
        var missingSecond = 0
        val earliest = bySlot.keys.minOfOrNull { it.first }
        if (earliest != null) {
            var candidateDate: LocalDate = earliest ?: target.date
            while (candidateDate.dayOfWeek != target.date.dayOfWeek)
                candidateDate = candidateDate.plusDays(1)
            while (candidateDate.isBefore(target.date)) {
                if (candidateDate !in closed && range.includes(candidateDate) &&
                    (candidateDate to target.session) !in bySlot) {
                    val (candidateFirst, candidateSecond) =
                        referenceSlots(DrawIdentity(candidateDate, target.session), closed)
                    val firstMatches = a != null && bySlot[candidateFirst]?.digit == a.digit
                    val secondMatches = b != null && bySlot[candidateSecond]?.digit == b.digit
                    if (firstMatches) missingFirst++
                    if (secondMatches) missingSecond++
                    if (firstMatches && secondMatches) missingBoth++
                }
                candidateDate = candidateDate.plusWeeks(1)
            }
        }
        return TwoDPreviousResults(
            a?.let { TwoDMatch(it.date, it.session, it.digit) },
            b?.let { TwoDMatch(it.date, it.session, it.digit) },
            match(both, if (a != null && b != null) a.digit + " + " + b.digit else null, missingBoth),
            match(first, a?.digit, missingFirst),
            match(second, b?.digit, missingSecond),
        )
    }

    /** A-01: absent digits in recorded previous-week draws, observed next-week returns. */
    private fun returns(
        known: List<HistoricDraw>,
        bySlot: Map<Pair<LocalDate, DrawSession>, HistoricDraw>,
        previous: TwoDWeekReview,
        currentMonday: LocalDate,
        target: DrawIdentity,
        closed: Set<LocalDate>,
        range: TwoDHistoryRange,
    ): TwoDMissingReturn {
        val episodes = known.map { monday(it.date) }.distinct().filter { it < previous.weekStart }
        val thisWeekSlots = slotsForWeek(currentMonday, closed).filter {
            isBefore(it.first, it.second, target)
        }
        val thisWeek = thisWeekSlots.mapNotNull { bySlot[it] }
        val results = previous.remainingDigits.map { digit ->
            val firstReturn = thisWeek.firstOrNull { digit in it.digit }
            val precedingKnown = if (firstReturn == null) false else {
                thisWeekSlots.takeWhile { it.first != firstReturn.date || it.second != firstReturn.session }
                    .all { it in bySlot }
            }
            val historicalReturns = mutableListOf<TwoDMatch>()
            val historicalEpisodes = mutableListOf<TwoDMissingReturnEpisode>()
            var qualifyingMissingWeeks = 0
            var noReturnWeeks = 0
            var unknownNextWeeks = 0
            for (week in episodes) {
                val sourceSlots = slotsForWeek(week, closed)
                // An absent digit is VERIFIED missing only if every scheduled
                // source-week draw is recorded. Partial weeks are unknown, not
                // proof of absence; never rank companion digits from them.
                if (sourceSlots.isEmpty() || sourceSlots.any { it !in bySlot }) continue
                if (sourceSlots.any { digit in requireNotNull(bySlot[it]).digit }) continue
                val nextWeek = week.plusWeeks(1)
                val followingSlots = slotsForWeek(nextWeek, closed)
                if (followingSlots.isEmpty() || followingSlots.none { range.includes(it.first) }) continue
                qualifyingMissingWeeks++
                // A range cutting across a following week cannot prove its first
                // return or complete non-return without the earlier draws.
                if (followingSlots.any { !range.includes(it.first) }) {
                    unknownNextWeeks++
                    continue
                }
                val firstReturnSlot = followingSlots.firstOrNull { slot ->
                    bySlot[slot]?.digit?.contains(digit) == true
                }
                if (firstReturnSlot != null) {
                    // An earlier open slot without a result could contain the digit:
                    // later first-OBSERVED is not a verified FIRST return.
                    val preceding = followingSlots.takeWhile { it != firstReturnSlot }
                    if (preceding.any { it !in bySlot }) {
                        unknownNextWeeks++
                        continue
                    }
                    val firstObserved = requireNotNull(bySlot[firstReturnSlot])
                    val returning = TwoDMatch(
                        firstObserved.date, firstObserved.session, firstObserved.digit,
                    )
                    historicalReturns.add(returning)
                    historicalEpisodes.add(TwoDMissingReturnEpisode(week, returning))
                } else if (followingSlots.all { it in bySlot }) {
                    // Only a fully recorded following week proves non-return.
                    noReturnWeeks++
                } else {
                    unknownNextWeeks++
                }
            }
            val companions = historicalReturns.map { returned ->
                if (returned.digit[0] == digit) returned.digit[1] else returned.digit[0]
            }
            val counts = companions.groupingBy { it }.eachCount()
            val highest = counts.values.maxOrNull() ?: 0
            TwoDMissingDigitReturn(
                digit,
                firstReturn?.let { TwoDMatch(it.date, it.session, it.digit) },
                precedingKnown, qualifyingMissingWeeks,
                noReturnWeeks, unknownNextWeeks, companions.size,
                counts.filterValues { it == highest }.map { TwoDReturnCompanion(it.key, it.value) }
                    .sortedBy { it.companion },
                historicalReturns, historicalEpisodes,
            )
        }
        return TwoDMissingReturn(previous.complete, previous.remainingDigits, results)
    }
}
