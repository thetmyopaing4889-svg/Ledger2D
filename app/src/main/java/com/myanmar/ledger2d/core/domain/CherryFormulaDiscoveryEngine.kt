package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Experimental, UI-independent RESULTS-FIRST discovery.
 *
 * These fits were DISCOVERED AFTER seeing historical outcomes. Even ten consecutive
 * in-sample matches are NOT ten pre-registered predictions or evidence of odds.
 * This prototype never changes the original DOC or Gemini formula engines.
 */
enum class CherryDiscoveryKind { LONE, WAM, BREAK }

data class CherryDiscoveredFormula(
    val kind: CherryDiscoveryKind,
    val id: String,
    val expression: String,
    val offset: Int,
    val currentDigit: Int,
    val historicalStreak: Int,
    val matchedDraws: List<DrawIdentity>,
    val discoveredAtStage: Int,
    val evidenceLabel: String = "HISTORICAL_FIT",
)

data class CherryDiscoverySession(
    val target: DrawIdentity,
    val historySignature: String,
    /** Completed levels, in [0, STAGE_COUNT]. */
    val completedStages: Int,
    val inspectedExpressions: Int,
    /** First discovered formula for each kind and streak bucket (3..10). */
    val formulas: List<CherryDiscoveredFormula>,
    val hasImmediatePreviousDraw: Boolean,
) {
    val isComplete: Boolean get() =
        completedStages >= CherryFormulaDiscoveryEngine.STAGE_COUNT

    fun longest(kind: CherryDiscoveryKind): CherryDiscoveredFormula? =
        formulas.filter { it.kind == kind }
            .maxWithOrNull(
                compareBy<CherryDiscoveredFormula> { it.historicalStreak }
                    .thenByDescending { it.discoveredAtStage }
            )
}

/**
 * A cursor step over finite expressions and their possible result-derived
 * offsets. No unbounded candidate pool is retained in memory.
 */
data class CherryDiscoveryAlternativeScan(
    val historySignature: String,
    val candidate: CherryDiscoveredFormula?,
    val nextPosition: Int,
    val isExhausted: Boolean,
    val hasImmediatePreviousDraw: Boolean,
    /** SHA-256 of the formula's complete historical and next-session outputs. */
    val behaviorKey: String? = null,
)

/**
 * Deterministic four-level search. Each round inspects one NEW grammar level.
 * It derives offsets from historical results; it does not enumerate arbitrary
 * prewritten "winning numbers". Its finite grammar is a safety boundary.
 *
 * Caution: a historical fit must pass a separate future walk-forward evaluation
 * before its streak can be called a prospectively verified streak.
 */
object CherryFormulaDiscoveryEngine {
    const val STAGE_COUNT = 4
    const val MIN_STREAK = 3
    const val TARGET_STREAK = 10
    /** Normal discovery evaluates at most the latest 200 confirmed draw slots. */
    const val SEARCH_DRAWS = 200
    /** The grammar references up to three PRECEDING slots for each draw. */
    private const val CONTEXT_DRAWS = 3

    private fun mod(n: Int): Int = Math.floorMod(n, 10)
    private fun before(d: HistoricDraw, t: DrawIdentity): Boolean =
        d.date < t.date || (d.date == t.date && d.session.ordinal < t.session.ordinal)

    private fun previous(t: DrawIdentity, closed: Set<LocalDate>): DrawIdentity {
        if (t.session == DrawSession.EVENING)
            return DrawIdentity(t.date, DrawSession.MORNING)
        var date = t.date.minusDays(1)
        while (!DrawSchedule.isWorkingDate(date, closed)) date = date.minusDays(1)
        return DrawIdentity(date, DrawSession.EVENING)
    }

    private fun identity(d: HistoricDraw): DrawIdentity =
        DrawIdentity(d.date, d.session)

    private data class Frame(val draw: HistoricDraw, val inputs: List<HistoricDraw>)

    /** Missing a real session is NOT silently bridged with older history. */
    private fun inputs(
        t: DrawIdentity,
        lookup: Map<Pair<LocalDate, DrawSession>, HistoricDraw>,
        closed: Set<LocalDate>,
    ): List<HistoricDraw> {
        val result = ArrayList<HistoricDraw>(3)
        var slot = previous(t, closed)
        repeat(3) {
            val row = lookup[slot.date to slot.session] ?: return result
            result.add(row)
            slot = previous(slot, closed)
        }
        return result
    }

    private data class Feature(val label: String, val lag: Int, val part: Int) {
        fun get(rows: List<HistoricDraw>): Int? {
            val s = rows.getOrNull(lag - 1)?.digit ?: return null
            return when (part) {
                0 -> s[0].digitToInt()
                1 -> s[1].digitToInt()
                else -> (s[0].digitToInt() + s[1].digitToInt()) % 10
            }
        }
    }

    private data class Expr(
        val label: String,
        val eval: (List<HistoricDraw>) -> Int?,
    )

    private fun features(lags: IntRange, includeBreak: Boolean): List<Feature> =
        lags.flatMap { lag ->
            val parts = if (includeBreak) 0..2 else 0..1
            parts.map { part ->
                Feature(listOf("top", "tail", "break")[part] + lag, lag, part)
            }
        }

    /** Each stage searches a distinct expression grammar, simplest first. */
    private fun expressions(stage: Int): List<Expr> {
        val two = features(1..2, true)
        val three = features(1..3, false)
        return when (stage) {
            0 -> features(1..3, true).map { f ->
                Expr(f.label) { rows -> f.get(rows) }
            }
            1 -> buildList {
                for (i in two.indices) for (j in i + 1 until two.size) {
                    val a = two[i]
                    val b = two[j]
                    add(Expr("(" + a.label + "+" + b.label + ")") { r ->
                        val x = a.get(r); val y = b.get(r)
                        if (x == null || y == null) null else mod(x + y)
                    })
                    add(Expr("(" + a.label + "-" + b.label + ")") { r ->
                        val x = a.get(r); val y = b.get(r)
                        if (x == null || y == null) null else mod(x - y)
                    })
                    add(Expr("(" + b.label + "-" + a.label + ")") { r ->
                        val x = a.get(r); val y = b.get(r)
                        if (x == null || y == null) null else mod(y - x)
                    })
                    add(Expr("abs(" + a.label + "-" + b.label + ")") { r ->
                        val x = a.get(r); val y = b.get(r)
                        if (x == null || y == null) null else abs(x - y)
                    })
                }
            }
            2 -> buildList {
                for (i in three.indices)
                    for (j in i + 1 until three.size)
                        for (k in j + 1 until three.size) {
                            val a = three[i]; val b = three[j]; val c = three[k]
                            add(Expr("(" + a.label + "+" + b.label + "+" + c.label + ")") { r ->
                                val x = a.get(r); val y = b.get(r); val z = c.get(r)
                                if (x == null || y == null || z == null) null else mod(x + y + z)
                            })
                            add(Expr("(" + a.label + "+" + b.label + "-" + c.label + ")") { r ->
                                val x = a.get(r); val y = b.get(r); val z = c.get(r)
                                if (x == null || y == null || z == null) null else mod(x + y - z)
                            })
                            add(Expr("(" + a.label + "-" + b.label + "+" + c.label + ")") { r ->
                                val x = a.get(r); val y = b.get(r); val z = c.get(r)
                                if (x == null || y == null || z == null) null else mod(x - y + z)
                            })
                        }
            }
            3 -> buildList {
                for (a in three) {
                    add(Expr("(9-" + a.label + ")") { r ->
                        a.get(r)?.let { mod(9 - it) }
                    })
                    for (b in three) {
                        add(Expr("(" + a.label + "*" + b.label + ")") { r ->
                            val x = a.get(r); val y = b.get(r)
                            if (x == null || y == null) null else mod(x * y)
                        })
                        add(Expr("(" + a.label + "*2+" + b.label + ")") { r ->
                            val x = a.get(r); val y = b.get(r)
                            if (x == null || y == null) null else mod(x * 2 + y)
                        })
                    }
                }
            }
            else -> emptyList()
        }
    }

    private fun targets(kind: CherryDiscoveryKind, actual: String): List<Int> {
        val top = actual[0].digitToInt()
        val tail = actual[1].digitToInt()
        return when (kind) {
            CherryDiscoveryKind.LONE -> listOf(top, tail).distinct()
            CherryDiscoveryKind.WAM ->
                listOf(top, tail, mod(top + 5), mod(tail + 5)).distinct()
            CherryDiscoveryKind.BREAK -> listOf(mod(top + tail))
        }
    }

    private fun hit(kind: CherryDiscoveryKind, output: Int, actual: String): Boolean {
        val top = actual[0].digitToInt()
        val tail = actual[1].digitToInt()
        return when (kind) {
            CherryDiscoveryKind.LONE -> top == output || tail == output
            CherryDiscoveryKind.WAM ->
                top == output || tail == output ||
                    top == mod(output + 5) || tail == mod(output + 5)
            CherryDiscoveryKind.BREAK -> mod(top + tail) == output
        }
    }

    /**
     * Search identities depend only on the bounded history window, the target,
     * and closures that could affect slots inside that window. Including every
     * historic closure would grow signatures forever and would invalidate a
     * finished search when a holiday from decades earlier is edited.
     */
    private fun signature(
        rows: List<HistoricDraw>,
        closed: Set<LocalDate>,
        target: DrawIdentity,
    ): String {
        val oldest = rows.firstOrNull()?.date ?: target.date
        val relevantClosures = closed.asSequence()
            .filter { !it.isBefore(oldest) && !it.isAfter(target.date) }
            .sorted()
            .joinToString(",")
        return rows.joinToString(";") {
            it.date.toString() + ":" + it.session.name + "=" + it.digit
        } + "|closed=" + relevantClosures
    }

    /**
     * Calculate one search level only. Passing the returned state to the next
     * call advances the grammar without repeating previous levels.
     *
     * Only the newest SEARCH_DRAWS frames are scored, while CONTEXT_DRAWS
     * extra older slots supply lagged inputs. This bounds the expensive
     * expression-streak search even if the repository grows to many years.
     * Historical data is NOT deleted; research walk-forward is separate.
     */
    fun nextRound(
        history: List<HistoricDraw>,
        target: DrawIdentity,
        previousState: CherryDiscoverySession? = null,
        closedDates: Set<LocalDate> = emptySet(),
        /** Call coroutineContext.ensureActive() here from a background caller. */
        checkCancellation: () -> Unit = {},
        /** Validate an existing search without consuming a new grammar stage. */
        advanceStage: Boolean = true,
    ): CherryDiscoverySession {
        // A bounded ordered buffer avoids materializing the entire historical
        // source into memory. Only 200 scored draws + 3 lag inputs survive.
        val rows = CherryDiscoveryHistoryNormalizer.newestBefore(
            history, target, closedDates, SEARCH_DRAWS + CONTEXT_DRAWS,
            checkCancellation,
        )
        val sig = signature(rows, closedDates, target)
        val old = previousState?.takeIf {
            it.target == target && it.historySignature == sig
        } ?: CherryDiscoverySession(target, sig, 0, 0, emptyList(), false)
        if (old.isComplete) return old
        if (!DrawSchedule.isWorkingDate(target.date, closedDates)) {
            return old.copy(hasImmediatePreviousDraw = false)
        }
        checkCancellation()

        val lookup = rows.associateBy { it.date to it.session }
        val currentInputs = inputs(target, lookup, closedDates)
        val immediate = currentInputs.firstOrNull()?.let {
            identity(it) == previous(target, closedDates)
        } ?: false
        if (!immediate) {
            // No trustworthy current inputs: do not claim a historical streak
            // supports this target. Caller can retry when results are complete.
            return old.copy(hasImmediatePreviousDraw = false)
        }

        if (!advanceStage) return old.copy(hasImmediatePreviousDraw = true)

        // Do not evaluate the three extra lag-context draws as targets.
        val frames = rows.takeLast(SEARCH_DRAWS).asReversed().map { row ->
            Frame(row, inputs(identity(row), lookup, closedDates))
        }
        val stage = old.completedStages
        val level = expressions(stage)
        val slots = linkedMapOf<Pair<CherryDiscoveryKind, Int>, CherryDiscoveredFormula>()
        old.formulas.forEach { slots[it.kind to minOf(it.historicalStreak, TARGET_STREAK)] = it }
        val mostRecent = frames.firstOrNull()
        for (expression in level) {
            checkCancellation()
            val first = mostRecent?.let { expression.eval(it.inputs) } ?: continue
            val current = expression.eval(currentInputs) ?: continue
            for (kind in CherryDiscoveryKind.entries) {
                for (wanted in targets(kind, mostRecent.draw.digit)) {
                    val offset = mod(wanted - first)
                    // Count first; only allocate matchedDraws if the formula
                    // actually earns a saved slot. This avoids thousands of
                    // short-lived lists and identities in worst-case searches.
                    var count = 0
                    for (frame in frames) {
                        if ((count and 63) == 0) checkCancellation()
                        val base = expression.eval(frame.inputs) ?: break
                        if (!hit(kind, mod(base + offset), frame.draw.digit)) break
                        count++
                    }
                    if (count < MIN_STREAK) continue
                    val bucket = kind to minOf(count, TARGET_STREAK)
                    val already = slots[bucket]
                    // First-found wins exact ties; in the 10+ bucket keep a
                    // genuinely longer streak instead of truncating its length.
                    if (already != null && already.historicalStreak >= count) continue
                    val hits = frames.take(count).map { identity(it.draw) }
                    slots[bucket] = CherryDiscoveredFormula(
                        kind = kind,
                        id = expression.label + "+" + offset + "@" + kind.name,
                        expression = expression.label,
                        offset = offset,
                        currentDigit = mod(current + offset),
                        historicalStreak = count,
                        matchedDraws = hits,
                        discoveredAtStage = stage + 1,
                    )
                }
            }
        }
        return CherryDiscoverySession(
            target = target,
            historySignature = sig,
            completedStages = stage + 1,
            inspectedExpressions = old.inspectedExpressions + level.size,
            formulas = slots.values.sortedWith(
                compareBy<CherryDiscoveredFormula> { it.kind.ordinal }
                    .thenBy { minOf(it.historicalStreak, TARGET_STREAK) }
            ),
            hasImmediatePreviousDraw = true,
        )
    }

    /**
     * Search exactly one next viable expression/offset candidate for a kind.
     *
     * Unlike the leaderboard's one-formula-per-streak-bucket rule, this cursor
     * can enumerate every distinct viable expression and offset in the bounded
     * 207-shape grammar. It retains only a single returned candidate, even in
     * an all-zero worst-case history. The caller stores a cursor position and
     * checks the fingerprint to restart safely after data corrections.
     *
     * Four cursor slots per expression cover the largest WAM target set;
     * LONE/BREAK leave some slots empty but maintain stable positions.
     */
    fun nextAlternative(
        history: List<HistoricDraw>,
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        startPosition: Int = 0,
        closedDates: Set<LocalDate> = emptySet(),
        checkCancellation: () -> Unit = {},
    ): CherryDiscoveryAlternativeScan {
        require(startPosition >= 0)
        val rows = CherryDiscoveryHistoryNormalizer.newestBefore(
            history, target, closedDates, SEARCH_DRAWS + CONTEXT_DRAWS,
            checkCancellation,
        )
        val fingerprint = signature(rows, closedDates, target)
        if (!DrawSchedule.isWorkingDate(target.date, closedDates)) {
            return CherryDiscoveryAlternativeScan(
                fingerprint, null, startPosition, false, false)
        }
        val lookup = rows.associateBy { it.date to it.session }
        val currentInputs = inputs(target, lookup, closedDates)
        val validImmediate = currentInputs.firstOrNull()?.let {
            identity(it) == previous(target, closedDates)
        } ?: false
        if (!validImmediate) {
            return CherryDiscoveryAlternativeScan(
                fingerprint, null, startPosition, false, false)
        }
        val frames = rows.takeLast(SEARCH_DRAWS).asReversed().map { row ->
            Frame(row, inputs(identity(row), lookup, closedDates))
        }
        val newest = frames.firstOrNull() ?: return CherryDiscoveryAlternativeScan(
            fingerprint, null, startPosition, false, true)
        val wanted = targets(kind, newest.draw.digit)
        val grammar = (0 until STAGE_COUNT).flatMap { stage ->
            expressions(stage).map { stage to it }
        }
        val stop = grammar.size * 4
        for (index in startPosition until stop) {
            if ((index and 31) == 0) checkCancellation()
            val wantedIndex = index % 4
            if (wantedIndex >= wanted.size) continue
            val (stage, expression) = grammar[index / 4]
            val first = expression.eval(newest.inputs) ?: continue
            val current = expression.eval(currentInputs) ?: continue
            val offset = mod(wanted[wantedIndex] - first)
            var streak = 0
            for (frame in frames) {
                if ((streak and 63) == 0) checkCancellation()
                val base = expression.eval(frame.inputs) ?: break
                if (!hit(kind, mod(base + offset), frame.draw.digit)) break
                streak++
            }
            if (streak < MIN_STREAK) continue
            val selected = CherryDiscoveredFormula(
                kind = kind,
                id = expression.label + "+" + offset + "@" + kind.name,
                expression = expression.label,
                offset = offset,
                currentDigit = mod(current + offset),
                historicalStreak = streak,
                matchedDraws = frames.take(streak).map { identity(it.draw) },
                discoveredAtStage = stage + 1,
            )
            // Behavioral dedup, not expression-name dedup. Commutative
            // expressions and offset variants may be exactly equivalent on
            // the available bounded evidence. WAM digit and digit+5 cover
            // identical actual outcomes, so canonicalize modulo five.
            fun canonical(digit: Int): Int =
                if (kind == CherryDiscoveryKind.WAM) mod(digit) % 5 else mod(digit)
            val trace = buildString {
                append(kind.name).append(':')
                append(canonical(selected.currentDigit)).append(':')
                for ((n, frame) in frames.withIndex()) {
                    if ((n and 31) == 0) checkCancellation()
                    val value = expression.eval(frame.inputs)
                    if (value == null) append('x')
                    else append(('a'.code + canonical(value + offset)).toChar())
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(trace.toByteArray(Charsets.UTF_8))
                .joinToString("") {
                    (it.toInt() and 255).toString(16).padStart(2, '0')
                }
            return CherryDiscoveryAlternativeScan(
                fingerprint, selected, index + 1, false, true, digest)
        }
        checkCancellation()
        return CherryDiscoveryAlternativeScan(
            fingerprint, null, stop, true, true)
    }

}
