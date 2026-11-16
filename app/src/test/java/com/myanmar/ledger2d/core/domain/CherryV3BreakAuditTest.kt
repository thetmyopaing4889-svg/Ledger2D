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
 * Audit-only exact V3 on real checked-in imported history.
 * Does not touch app UI, production search, history, or checkpoint store.
 * A historical in-sample fit is NOT prospective validation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class CherryV3BreakAuditTest {
    private val fmt = DateTimeFormatter.ofPattern("dd-MM-uuuu")

    private data class Archive(
        val draws: List<HistoricDraw>,
        val closures: Set<LocalDate>,
        val latest: LocalDate,
    )

    private fun archive(): Archive {
        val app = RuntimeEnvironment.getApplication()
        val json = JSONArray(app.assets.open("history_seed.json")
            .bufferedReader().use { it.readText() })
        val all = ArrayList<HistoricDraw>(json.length() * 2)
        val off = mutableSetOf<LocalDate>()
        var latest = LocalDate.MIN
        repeat(json.length()) { i ->
            val item = json.getJSONObject(i)
            val day = LocalDate.parse(item.getString("date"), fmt)
            if (day > latest) latest = day
            val am = item.optString("morning2d")
            val pm = item.optString("evening2d")
            if (!DrawSchedule.isWeekday(day)) return@repeat
            if ((am.equals("off", true) || pm.equals("off", true)) &&
                !validHistoric2D(am) && !validHistoric2D(pm)) off.add(day)
            if (validHistoric2D(am)) all.add(
                HistoricDraw(day, DrawSession.MORNING, am, HistoricSource.HISTORY))
            if (validHistoric2D(pm)) all.add(
                HistoricDraw(day, DrawSession.EVENING, pm, HistoricSource.HISTORY))
        }
        val conflict = off.filterTo(mutableSetOf()) { day ->
            all.any { it.date == day }
        }
        return Archive(
            CherryDiscoveryHistoryNormalizer.normalize(all.filterNot {
                it.date in conflict
            }),
            off - conflict,
            latest,
        )
    }

    @Test fun breakpointAuditRetainsFirstFoundAndQuantifiesUniqueEvaluations() {
        val source = archive()
        val target = DrawIdentity(LocalDate.of(2026, 9, 30), DrawSession.MORNING)
        val context = CherryV2HistoricalEvaluator.prepare(
            source.draws, target, source.closures)
        assertEquals(LocalDate.of(2026, 9, 29), source.latest)
        assertTrue(context.prior.size >= 6)
        val first = CherryV2TimedSearch.run(
            context, CherryDiscoveryKind.BREAK,
            timeBudgetNanos = 8_000_000_000L)
        val next = CherryV2TimedSearch.run(
            context, CherryDiscoveryKind.BREAK,
            previous = first.cursor, timeBudgetNanos = 8_000_000_000L)
        assertTrue(next.cursor.nextIndex > first.cursor.nextIndex)
        assertEquals(first.cursor.firstByStreak,
            first.cursor.firstByStreak.filterKeys {
                next.cursor.firstByStreak[it] == first.cursor.firstByStreak[it]
            })
        assertTrue(next.cursor.firstByStreak.size <= 8)
        val total = first.inspectedThisBatch + next.inspectedThisBatch
        val distinct = first.distinctBehaviorsEvaluated +
            next.distinctBehaviorsEvaluated
        val repeated = first.equivalentBehaviorsSkipped +
            next.equivalentBehaviorsSkipped
        val unscorable = first.unscorableExpressionsSkipped +
            next.unscorableExpressionsSkipped
        assertEquals(total, distinct + repeated + unscorable)
        println("CHERRY_V3_BREAK_AUDIT target=$target " +
            "contiguousSlots=${context.prior.size} " +
            "maxPossibleByLag1=${context.prior.size - 1} " +
            "inspected=$total distinct=$distinct duplicates=$repeated " +
            "unscorable=$unscorable retained=${next.cursor.firstByStreak.keys.sorted()} " +
            "cursor=${next.cursor.nextIndex} firstCursor=${first.cursor.nextIndex} " +
            "label=ARCHIVED_HISTORY_NOT_PHONE_DATA")
    }

    @Test fun aSecondActualHistoryWindowCanHaveDifferentLength() {
        val source = archive()
        val target = DrawIdentity(LocalDate.of(2025, 9, 30), DrawSession.MORNING)
        val context = CherryV2HistoricalEvaluator.prepare(
            source.draws, target, source.closures)
        val result = CherryV2TimedSearch.run(
            context, CherryDiscoveryKind.BREAK,
            timeBudgetNanos = 5_000_000_000L)
        println("CHERRY_V3_BREAK_AUDIT target=$target " +
            "contiguousSlots=${context.prior.size} " +
            "inspected=${result.inspectedThisBatch} " +
            "distinct=${result.distinctBehaviorsEvaluated} " +
            "duplicates=${result.equivalentBehaviorsSkipped} " +
            "unscorable=${result.unscorableExpressionsSkipped} " +
            "retained=${result.cursor.firstByStreak.keys.sorted()} " +
            "label=ARCHIVED_HISTORY_NOT_PHONE_DATA")
        assertTrue(result.cursor.firstByStreak.size <= 8)
    }
}
