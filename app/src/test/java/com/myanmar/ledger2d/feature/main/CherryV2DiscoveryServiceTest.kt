package com.myanmar.ledger2d.feature.main

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryV2TimedSearch
import com.myanmar.ledger2d.core.domain.CherryV2HistoricalEvaluator
import com.myanmar.ledger2d.core.domain.CherryV2StreakRegistry
import com.myanmar.ledger2d.core.repository.CherryV2CheckpointStore
import java.security.MessageDigest
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class CherryV2DiscoveryServiceTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val days = generateSequence(LocalDate.of(2026, 11, 2)) {
        it.plusDays(1)
    }.filter(DrawSchedule::isWeekday).take(11).toList()
    private val target = DrawIdentity(days[10], DrawSession.MORNING)
    private val asOf = LocalDateTime.of(target.date, LocalTime.of(11, 0))
    private fun prefs() = app.getSharedPreferences(
        "cherry_v2_discovery_cursors_v3", Context.MODE_PRIVATE)

    @Before fun cleanStart() { assertTrue(prefs().edit().clear().commit()) }
    @After fun cleanFinish() { assertTrue(prefs().edit().clear().commit()) }

    private fun database(): LedgerDatabase =
        Room.inMemoryDatabaseBuilder(app, LedgerDatabase::class.java)
            .allowMainThreadQueries().build()

    private fun row(date: LocalDate, morning: String = "00") =
        HistoryResultEntity(
            date = date, morning2d = morning, evening2d = "00",
            morningSet = "", morningValue = "",
            eveningSet = "", eveningValue = "",
            modern930 = "", internet930 = "",
            modern200 = "", internet200 = "",
        )

    @Test fun serviceOnlySearchesWhenInvokedAndResumesAfterProcessRecreation() = runBlocking {
        val db = database()
        try {
            db.historyResultDao().upsertAll(days.take(10).map(::row))
            val kind = CherryDiscoveryKind.LONE
            val service = CherryV2DiscoveryService(app, db)
            assertNull(service.saved(target, kind, asOf))
            assertTrue(prefs().all.isEmpty()) // no background run on construction

            val first = service.search(target, kind, asOf,
                timeBudgetNanos = 180_000_000L)
            assertEquals(CherryV2TimedSearch.StopReason.TIME_LIMIT,
                first.result.stopReason)
            assertTrue(first.result.inspectedThisBatch > 0)
            assertTrue(first.contiguousSlots >= 10)
            assertTrue(first.contiguousSlots <=
                CherryV2HistoricalEvaluator.CONTEXT_LIMIT)
            assertNotNull(first.recommended)
            assertTrue("At most eight first-found tiers 3..10+",
                first.results.size <= 8)
            assertEquals(first.results.map { it.historicalStreak },
                first.results.map { it.historicalStreak }.sorted())
            assertEquals(first.result.cursor,
                CherryV2DiscoveryService(app, db).saved(target, kind, asOf))

            val second = CherryV2DiscoveryService(app, db).search(
                target, kind, asOf, timeBudgetNanos = 180_000_000L)
            assertTrue(second.result.cursor.nextIndex > first.result.cursor.nextIndex)
            assertTrue(second.result.cursor.firstByStreak.keys.containsAll(
                first.result.cursor.firstByStreak.keys))
            first.result.cursor.firstByStreak.forEach { (streak, choice) ->
                assertEquals(choice.formulaId,
                    second.result.cursor.firstByStreak.getValue(streak).formulaId)
            }
            assertTrue("Continue must not expand visible results past eight",
                second.results.size <= 8)
            assertEquals(second.result.cursor,
                CherryV2DiscoveryService(app, db).saved(target, kind, asOf))
        } finally {
            db.close()
        }
    }

    @Test fun masterPlan203PolicyDoesNotSilentlyReuseOld212PolicyCheckpoint() =
        runBlocking {
            val db = database()
            try {
                db.historyResultDao().upsertAll(days.take(10).map(::row))
                val kind = CherryDiscoveryKind.LONE
                val bounded = CherryDiscoveryBoundedSource(db)
                    .latest(target, asOf)
                val prepared = CherryV2HistoricalEvaluator.prepare(
                    bounded.draws, target, bounded.closedDates)
                // Reconstruct the old V2 hash over this SHORT fixture. The
                // original 200+12 policy sees the exact same rows here;
                // only the versioned policy label can invalidate its saved
                // checkpoint. No source rows or user formulas are removed.
                val oldEvidence = buildString {
                    append("V2|").append(target.date).append('|')
                        .append(target.session.name).append("|true|")
                    for (slot in prepared.prior) {
                        append('|').append(slot.date).append(':')
                            .append(slot.session.name).append('=')
                            .append(slot.digit).append('@')
                            .append(slot.source.name)
                    }
                }
                val oldHash = MessageDigest.getInstance("SHA-256")
                    .digest(oldEvidence.toByteArray(Charsets.UTF_8))
                    .joinToString("") {
                        (it.toInt() and 255).toString(16).padStart(2, '0')
                    }
                assertNotEquals(oldHash, prepared.historySignature)
                val store = CherryV2CheckpointStore(app)
                val old = CherryV2StreakRegistry(target, kind, oldHash)
                store.persist(old)
                assertEquals(old, store.resume(kind, target, oldHash))
                assertNull(
                    CherryV2DiscoveryService(app, db).saved(target, kind, asOf))
                // The prior incompatible state is not discarded on read;
                // explicit user search can establish a new policy snapshot.
                assertEquals(old, store.resume(kind, target, oldHash))
                val fresh = CherryV2DiscoveryService(app, db).search(
                    target, kind, asOf, timeBudgetNanos = 120_000_000L)
                assertEquals(prepared.historySignature,
                    fresh.result.cursor.historySignature)
                assertNotEquals(oldHash, fresh.result.cursor.historySignature)
            } finally {
                db.close()
            }
        }

    @Test fun categoryIsolationAndCorrectedHistoryNeverReuseStaleSearch() = runBlocking {
        val db = database()
        try {
            db.historyResultDao().upsertAll(days.take(10).map(::row))
            val service = CherryV2DiscoveryService(app, db)
            val lone = service.search(target, CherryDiscoveryKind.LONE,
                asOf, timeBudgetNanos = 120_000_000L).result.cursor
            assertNotNull(service.saved(target, CherryDiscoveryKind.LONE, asOf))
            assertNull(service.saved(target, CherryDiscoveryKind.WAM, asOf))
            assertTrue(lone.nextIndex.signum() > 0)

            val updatedDate = days[8]
            // Preserve the auto-generated Room primary key when changing an
            // existing unique-date row. Inserting id=0 is NOT a correction.
            val existingRow = requireNotNull(db.historyResultDao().get(updatedDate))
            db.historyResultDao().upsert(existingRow.copy(morning2d = "81"))
            assertEquals("81", db.historyResultDao().get(updatedDate)?.morning2d)
            val resolved = CherryDiscoveryBoundedSource(db).latest(
                target, asOf, slotLimit = 212)
            assertEquals("81", resolved.draws.single {
                it.date == updatedDate && it.session == DrawSession.MORNING
            }.digit)
            val revised = CherryV2DiscoveryService(app, db)
            assertNull(revised.saved(target, CherryDiscoveryKind.LONE, asOf))
            val fresh = revised.search(target, CherryDiscoveryKind.LONE,
                asOf, timeBudgetNanos = 120_000_000L).result.cursor
            assertNotEquals(lone.historySignature, fresh.historySignature)
            assertEquals(fresh, revised.saved(target, CherryDiscoveryKind.LONE, asOf))
            assertNull(revised.saved(target, CherryDiscoveryKind.WAM, asOf))
        } finally {
            db.close()
        }
    }

    @Test fun damagedCursorRaisesErrorInsteadOfLosingPreviouslyOfferedFormulas() = runBlocking {
        val db = database()
        try {
            db.historyResultDao().upsertAll(days.take(10).map(::row))
            val service = CherryV2DiscoveryService(app, db)
            val original = service.search(target, CherryDiscoveryKind.LONE, asOf,
                timeBudgetNanos = 120_000_000L).result.cursor
            assertTrue(original.firstByStreak.isNotEmpty())
            assertTrue(prefs().edit().putString("cursor_LONE", "{bad").commit())
            try {
                CherryV2DiscoveryService(app, db).saved(
                    target, CherryDiscoveryKind.LONE, asOf)
                fail("Damaged progress must not be discarded silently")
            } catch (_: IllegalStateException) {}
            try {
                CherryV2DiscoveryService(app, db).search(
                    target, CherryDiscoveryKind.LONE, asOf,
                    timeBudgetNanos = 120_000_000L)
                fail("Search must not overwrite a damaged checkpoint")
            } catch (_: IllegalStateException) {}
        } finally {
            db.close()
        }
    }
    @Test fun orderAuditIsExplicitReadOnlyAndDoesNotStartOrAlterDiscovery() = runBlocking {
        val db = database()
        try {
            db.historyResultDao().upsertAll(days.take(10).map(::row))
            val kind = CherryDiscoveryKind.LONE
            val service = CherryV2DiscoveryService(app, db)
            assertNull(service.saved(target, kind, asOf))
            assertTrue(prefs().all.isEmpty())

            val metrics = service.auditSearchOrder(
                target = target, kind = kind, asOf = asOf,
                // Cap at 12 formulas; allow CI classloading overhead without
                // confusing cold-start latency with formula throughput.
                candidatesPerMode = 12, budgetPerModeNanos = 5_000_000_000L,
            )
            assertEquals(12, metrics.raw.inspected)
            assertEquals(12, metrics.guided.inspected)
            assertEquals(kind, metrics.category)
            assertTrue(metrics.raw.longestHistoricalStreak >= 0)
            assertTrue(metrics.guided.longestHistoricalStreak >= 0)
            assertTrue(prefs().all.isEmpty())
            assertNull(service.saved(target, kind, asOf))
        } finally {
            db.close()
        }
    }

}
