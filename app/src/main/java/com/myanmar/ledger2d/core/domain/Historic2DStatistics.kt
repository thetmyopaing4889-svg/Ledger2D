package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * A read-only, independent historical draw. It is never an operational ledger winner.
 * One draw is uniquely identified by (date, session), not by its number.
 */
enum class HistoricSource { DAILY, HISTORY }

data class HistoricDraw(
    val date: LocalDate,
    val session: DrawSession,
    val digit: String,
    val source: HistoricSource,
)

data class HistoricConflict(
    val date: LocalDate,
    val session: DrawSession,
    val primaryDigit: String,
    val historyDigit: String,
)

data class HistoricMerge(
    val draws: List<HistoricDraw>,
    val conflicts: List<HistoricConflict>,
) {
    val dailyCount: Int get() = draws.count { it.source == HistoricSource.DAILY }
    val historyCount: Int get() = draws.count { it.source == HistoricSource.HISTORY }
}

/** A valid 2D result must be exactly two ASCII digits, including leading zeroes. */
fun validHistoric2D(digit: String): Boolean =
    digit.length == 2 && digit.all { it in '0'..'9' }

/**
 * Primary-first and history-fallback, separately for each date/session.
 *
 * Finality is deliberately NOT guessed here. The caller admits only already
 * settled daily results (currently before the present calendar day). This
 * avoids accidentally analyzing an unstable live checkpoint as final.
 */
object HistoricDrawMerger {
    fun merge(
        primary: List<HistoricDraw>,
        secondary: List<HistoricDraw>,
        latestAllowed: LocalDate,
    ): HistoricMerge {
        val primaryMap = primary.asSequence()
            .filter { it.date <= latestAllowed && validHistoric2D(it.digit) }
            .associateBy { it.date to it.session }
        val historyMap = secondary.asSequence()
            .filter { it.date <= latestAllowed && validHistoric2D(it.digit) }
            .associateBy { it.date to it.session }
        val keys = (primaryMap.keys + historyMap.keys).sortedWith(
            compareBy<Pair<LocalDate, DrawSession>> { it.first }.thenBy { it.second.ordinal }
        )
        val conflicts = keys.mapNotNull { key ->
            val a = primaryMap[key] ?: return@mapNotNull null
            val b = historyMap[key] ?: return@mapNotNull null
            if (a.digit == b.digit) null else HistoricConflict(key.first, key.second, a.digit, b.digit)
        }
        return HistoricMerge(
            draws = keys.mapNotNull { key -> primaryMap[key] ?: historyMap[key] },
            conflicts = conflicts,
        )
    }
}

data class HistoricNumberStat(
    val digit: String,
    val count: Int,
    /** Null means the digit was not found in available history through the end date. */
    val lastSeen: HistoricDraw?,
    /** Number of *observed completed draws* since lastSeen, not calendar sessions. */
    val observedDrawsSince: Int?,
)

data class HistoricPairStat(
    val first: String,
    val reverse: String,
    val firstCount: Int,
    val reverseCount: Int,
)

data class HistoricDailyPair(val date: LocalDate, val morning: String, val evening: String)

data class HistoricStatistics(
    val start: LocalDate,
    val end: LocalDate,
    val session: DrawSession?,
    val totalDraws: Int,
    val counts: List<HistoricNumberStat>,
    val tens: List<Int>,
    val ones: List<Int>,
    val missing: List<HistoricNumberStat>,
    val topNumbers: List<HistoricNumberStat>,
    val reversePairs: List<HistoricPairStat>,
    val dailyPairs: List<HistoricDailyPair>,
    val doubleCount: Int,
    val lastResult: HistoricDraw?,
) {
    val distinctCount: Int get() = counts.count { it.count > 0 }
}

/**
 * Pure, deterministic statistics: no network, no database writes, no probabilities.
 * Recency is anchored to end, not to the device's current date.
 */
object HistoricStatisticsCalculator {
    fun calculate(
        allDraws: List<HistoricDraw>,
        start: LocalDate,
        end: LocalDate,
        session: DrawSession? = null,
    ): HistoricStatistics {
        require(!start.isAfter(end)) { "Invalid date range" }
        val relevant = allDraws.asSequence()
            .filter { it.date <= end && (session == null || it.session == session) }
            .filter { validHistoric2D(it.digit) }
            .distinctBy { it.date to it.session }
            .sortedWith(compareBy<HistoricDraw> { it.date }.thenBy { it.session.ordinal })
            .toList()
        val window = relevant.filter { it.date >= start }
        val digitCounts = IntArray(100)
        val tens = IntArray(10)
        val ones = IntArray(10)
        for (row in window) {
            val n = row.digit.toInt()
            digitCounts[n]++
            tens[n / 10]++
            ones[n % 10]++
        }
        val lastSeenByNumber = relevant.withIndex().associate { (idx, draw) ->
            draw.digit to (draw to (relevant.lastIndex - idx))
        }
        val counts = (0..99).map { n ->
            val digit = n.toString().padStart(2, '0')
            val entry = lastSeenByNumber[digit]
            HistoricNumberStat(digit, digitCounts[n], entry?.first, entry?.second)
        }
        val pairs = (0..99).map { n -> n.toString().padStart(2, '0') }
            .filter { it < it.reversed() }
            .map { first ->
                val reverse = first.reversed()
                HistoricPairStat(first, reverse, digitCounts[first.toInt()], digitCounts[reverse.toInt()])
            }
            .sortedWith(compareByDescending<HistoricPairStat> { it.firstCount + it.reverseCount }
                .thenBy { it.first })
        val dailyPairs = if (session != null) emptyList() else window
            .groupBy { it.date }
            .toSortedMap()
            .mapNotNull { (date, entries) ->
                val am = entries.firstOrNull { it.session == DrawSession.MORNING }
                val pm = entries.firstOrNull { it.session == DrawSession.EVENING }
                if (am == null || pm == null) null
                else HistoricDailyPair(date, am.digit, pm.digit)
            }
        return HistoricStatistics(
            start = start,
            end = end,
            session = session,
            totalDraws = window.size,
            counts = counts,
            tens = tens.toList(),
            ones = ones.toList(),
            missing = if (window.isEmpty()) emptyList() else counts.filter { it.count == 0 },
            topNumbers = counts.filter { it.count > 0 }
                .sortedWith(compareByDescending<HistoricNumberStat> { it.count }.thenBy { it.digit })
                .take(10),
            reversePairs = pairs,
            dailyPairs = dailyPairs,
            doubleCount = window.count { it.digit[0] == it.digit[1] },
            lastResult = window.lastOrNull(),
        )
    }
}
