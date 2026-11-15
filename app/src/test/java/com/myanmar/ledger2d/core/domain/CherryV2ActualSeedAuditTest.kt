package com.myanmar.ledger2d.core.domain

import android.app.Application
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An opt-in audit of the ACTUAL checked-in historical import, not synthetic
 * lottery outcomes. These are archive replays, never live locked predictions.
 * The source file is read from packaged assets; nothing here writes to
 * the Research workspace, database, Champion, or user's saved formulas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class CherryV2ActualSeedAuditTest {
    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-uuuu")

    private data class Seed(
        val dates: Int,
        val rows: List<HistoricDraw>,
        val offDates: Set<LocalDate>,
        val latest: LocalDate,
    )

    private fun readSeed(): Seed {
        val app = RuntimeEnvironment.getApplication()
        val jsonText = app.assets.open("history_seed.json").bufferedReader().use {
            it.readText()
        }
        val raw = JSONArray(jsonText)
        val draws = ArrayList<HistoricDraw>(raw.length() * 2)
        val off = mutableSetOf<LocalDate>()
        var latest = LocalDate.MIN
        repeat(raw.length()) { index ->
            val day = raw.getJSONObject(index)
            val date = LocalDate.parse(day.getString("date"), dateFormat)
            if (date > latest) latest = date
            val am = day.optString("morning2d")
            val pm = day.optString("evening2d")
            if (!DrawSchedule.isWeekday(date)) return@repeat
            if (am.equals("off", ignoreCase = true) ||
                pm.equals("off", ignoreCase = true)) {
                // This is EXPLICIT source evidence of a closure. A missing
                // date or "-" alone never authorizes skipping a working draw.
                if (!validHistoric2D(am) && !validHistoric2D(pm)) {
                    off.add(date)
                }
            }
            if (validHistoric2D(am)) {
                draws.add(HistoricDraw(
                    date, DrawSession.MORNING, am, HistoricSource.HISTORY))
            }
            if (validHistoric2D(pm)) {
                draws.add(HistoricDraw(
                    date, DrawSession.EVENING, pm, HistoricSource.HISTORY))
            }
        }
        return Seed(raw.length(),
            CherryDiscoveryHistoryNormalizer.normalize(draws), off, latest)
    }

    private fun identity(draw: HistoricDraw) = DrawIdentity(draw.date, draw.session)
    private fun strictlyLater(a: DrawIdentity, b: DrawIdentity) =
        a.date > b.date || (a.date == b.date &&
            a.session.ordinal > b.session.ordinal)

    private fun sampleTargets(
        seed: Seed, count: Int,
    ): List<HistoricDraw> {
        val cutoff = seed.latest.minusYears(2)
        // Keep 6 actual outcomes after each selection target for validation.
        // The audit cutoff is based on the seed's last recorded date, never
        // the device date (which may be months newer than the import).
        val eligible = seed.rows.filter { it.date >= cutoff }
            .dropLast(6)
        require(eligible.size >= count)
        return (0 until count).map { i ->
            eligible[i * (eligible.size - 1) / (count - 1)]
        }
    }

    private data class Summary(
        var contexts: Int = 0,
        var nonemptyHistory: Int = 0,
        var rawInspected: Long = 0,
        var guidedInspected: Long = 0,
        var rawDistinct: Long = 0,
        var guidedDistinct: Long = 0,
        var rawTen: Int = 0,
        var guidedTen: Int = 0,
        var selected: Int = 0,
        var holdoutChecked: Int = 0,
        var holdoutHits: Int = 0,
        var holdoutAbstained: Int = 0,
    )

    @Test fun checkedInSeedIsRealImportWithExplicitOffDaysNotFabricatedData() {
        val seed = readSeed()
        assertTrue("Expected multi-year historical import", seed.dates >= 900)
        assertTrue("Expected 1500+ actual AM/PM results", seed.rows.size > 1500)
        assertTrue("Some explicit off days must exist", seed.offDates.size >= 40)
        assertEquals(LocalDate.of(2026, 9, 29), seed.latest)
        assertEquals(seed.rows.size, seed.rows.map(::identity).distinct().size)
        assertTrue(seed.rows.all { validHistoric2D(it.digit) })
        assertTrue(seed.rows.none { it.date in seed.offDates })
        val selected = sampleTargets(seed, 80)
        assertEquals(80, selected.size)
        assertEquals(80, selected.map(::identity).distinct().size)
        println(
            "CHERRY_V2_SEED_SOURCE rows=${seed.dates} validSlots=${seed.rows.size} " +
                "explicitOffDates=${seed.offDates.size} latest=${seed.latest} " +
                "twoYearSampleTargets=${selected.size} label=IMPORTED_HISTORY_ONLY"
        )
    }

    @Test fun twoYearEightyTargetThreeCategoryRawVersusGuidedAndHoldoutAudit() {
        val seed = readSeed()
        val sampled = sampleTargets(seed, 80)
        assertEquals(80, sampled.size)
        val totals = CherryDiscoveryKind.entries.associateWith { Summary() }
        for ((position, selection) in sampled.withIndex()) {
            val target = identity(selection)
            val context = CherryV2HistoricalEvaluator.prepare(
                seed.rows, target, seed.offDates)
            val future = seed.rows.asSequence()
                .filter { strictlyLater(identity(it), target) }
                .take(4).toList()
            for (kind in CherryDiscoveryKind.entries) {
                val stats = totals.getValue(kind)
                stats.contexts++
                if (context.blocked || context.prior.size < 4) continue
                stats.nonemptyHistory++

                val comparison = CherryV2OrderBenchmark.compare(
                    context, kind,
                    candidatesPerMode = 64,
                    budgetPerModeNanos = 350_000_000L,
                )
                stats.rawInspected += comparison.raw.inspected
                stats.guidedInspected += comparison.guided.inspected
                stats.rawDistinct += comparison.raw.distinctBehaviorTraces
                stats.guidedDistinct += comparison.guided.distinctBehaviorTraces
                if (comparison.raw.firstAtLeastTen != null) stats.rawTen++
                if (comparison.guided.firstAtLeastTen != null) stats.guidedTen++

                // Training choice is fixed from THIS target's earlier
                // results. Neither later actuals nor their hit-rate affect
                // the formula, offset, or category chosen.
                val best = comparison.guided.firstByStreak.maxByOrNull { it.key }
                    ?: continue
                val formulaIndex = java.math.BigInteger.valueOf(best.value.toLong())
                val expression = CherryV2GuidedSchedule.expressionAt(formulaIndex)
                val selected = CherryV2HistoricalEvaluator.evaluate(
                    context, kind, formulaIndex, expression
                ).firstOrNull { it.historicalStreak == best.key } ?: continue
                stats.selected++

                val holdout = CherryV2ForwardValidation.replay(
                    selected, seed.rows, future, seed.offDates)
                assertEquals("HOLDOUT_REPLAY_NOT_PROSPECTIVE", holdout.evidenceLabel)
                assertEquals(future.size, holdout.trials.size)
                stats.holdoutChecked += holdout.checked
                stats.holdoutHits += holdout.hits
                stats.holdoutAbstained += holdout.abstentions
            }
            if (position == 0) {
                // An actual pre-target input check: full archive includes
                // this result, but the selection context must exclude it.
                assertTrue(context.prior.none { identity(it) == target })
            }
        }
        for ((kind, x) in totals) {
            assertEquals(80, x.contexts)
            assertTrue(x.nonemptyHistory > 40)
            assertTrue(x.rawInspected > 0 && x.guidedInspected > 0)
            val observed = if (x.holdoutChecked == 0) "NA" else
                "%.4f".format(java.util.Locale.US,
                    x.holdoutHits.toDouble() / x.holdoutChecked)
            println(
                "CHERRY_V2_SEED_AUDIT category=${kind.name} targets=${x.contexts} " +
                    "usable=${x.nonemptyHistory} rawInspected=${x.rawInspected} " +
                    "guidedInspected=${x.guidedInspected} rawDistinct=${x.rawDistinct} " +
                    "guidedDistinct=${x.guidedDistinct} rawTenTargets=${x.rawTen} " +
                    "guidedTenTargets=${x.guidedTen} chosen=${x.selected} " +
                    "holdoutChecked=${x.holdoutChecked} holdoutHits=${x.holdoutHits} " +
                    "holdoutAbstain=${x.holdoutAbstained} holdoutHitRate=$observed " +
                    "label=IMPORTED_SEED_HISTORICAL_REPLAY_NOT_PROSPECTIVE"
            )
        }
    }
}
