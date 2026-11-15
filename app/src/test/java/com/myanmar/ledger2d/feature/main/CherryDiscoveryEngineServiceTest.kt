package com.myanmar.ledger2d.feature.main

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.database.WinningNumberEntity
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryProspectiveTrialClock
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.domain.HistoricSource
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
class CherryDiscoveryEngineServiceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun clear() {
        for (key in listOf("cherry_discovery_cursors_v1",
                "cherry_prospective_trial_v1")) {
            assertTrue(context.getSharedPreferences(key, Context.MODE_PRIVATE)
                .edit().clear().commit())
        }
    }
    @Before fun setup() = clear()
    @After fun teardown() = clear()

    @Test fun endToEndSearchRestartLockAndConfirmedSettlement() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val days = generateSequence(LocalDate.of(2026, 11, 2)) {
                it.plusDays(1)
            }.filter(DrawSchedule::isWeekday).take(7).toList()
            db.historyResultDao().upsertAll(days.take(6).map { day ->
                HistoryResultEntity(
                    date = day, morning2d = "00", evening2d = "00",
                    morningSet = "", morningValue = "",
                    eveningSet = "", eveningValue = "",
                    modern930 = "", internet930 = "",
                    modern200 = "", internet200 = "",
                )
            })
            val target = DrawIdentity(days[6], DrawSession.MORNING)
            val asOf = LocalDateTime.of(target.date, LocalTime.of(11, 0))
            val first = CherryDiscoveryEngineService(context, db)
                .searchMore(target, CherryDiscoveryKind.LONE, asOf)
            assertNotNull(first.cursor.newCandidate)
            assertEquals(6, first.databaseRowsRead)
            val otherInstance = CherryDiscoveryEngineService(context, db)
            val second = otherInstance.searchMore(
                target, CherryDiscoveryKind.LONE, asOf)
            assertNotEquals(first.cursor.newCandidate?.id,
                second.cursor.newCandidate?.id)
            assertEquals(2, second.cursor.offeredIds.size)
            val third = CherryDiscoveryEngineService(context, db)
                .searchMore(target, CherryDiscoveryKind.LONE, asOf)
            assertEquals(3, third.cursor.offeredIds.size)
            assertNotEquals(second.cursor.newCandidate?.id,
                third.cursor.newCandidate?.id)
            assertNotEquals(second.cursor.newCandidate?.id,
                third.cursor.bestCandidate?.id)

            // Earlier, non-best formula remains user-selectable even after
            // searching past it and reconstructing progress from disk.
            val selected = CherryDiscoveryKind.entries.associateWith { kind ->
                if (kind == CherryDiscoveryKind.LONE)
                    second.cursor.newCandidate?.id else null
            }
            val cutoff = CherryProspectiveTrialClock.cutoff(target)
            val lock = otherInstance.lock(
                target, selected, cutoff.minusSeconds(60), asOf)
            assertEquals(1, lock.lockedTrials().size)
            assertEquals(second.cursor.newCandidate?.id,
                lock.lockedTrials().single().choices.single {
                    it.kind == CherryDiscoveryKind.LONE }.formulaId)

            val laterInstance = CherryDiscoveryEngineService(context, db)
            assertEquals(lock.lockedTrials(), laterInstance.trials().lockedTrials())
            val final = laterInstance.recordConfirmedResult(
                HistoricDraw(target.date, target.session, "00",
                    HistoricSource.WINNER),
                cutoff.plusSeconds(20))
            assertEquals(1, final.stats(CherryDiscoveryKind.LONE).checked)
            assertEquals(1, final.stats(CherryDiscoveryKind.LONE).hits)
            assertEquals(1, final.stats(CherryDiscoveryKind.WAM).abstentions)
            assertEquals(lock.lockedTrials(), laterInstance.trials().lockedTrials())
        } finally { db.close() }
    }
    @Test fun uiServiceRestoresSearchAndSettlesOnlyStoredFinalResult() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val days = generateSequence(LocalDate.of(2026, 11, 2)) {
                it.plusDays(1)
            }.filter(DrawSchedule::isWeekday).take(7).toList()
            db.historyResultDao().upsertAll(days.take(6).map { day ->
                HistoryResultEntity(
                    date = day, morning2d = "00", evening2d = "00",
                    morningSet = "", morningValue = "",
                    eveningSet = "", eveningValue = "",
                    modern930 = "", internet930 = "",
                    modern200 = "", internet200 = "",
                )
            })
            val target = DrawIdentity(days[6], DrawSession.MORNING)
            val asOfBefore = LocalDateTime.of(target.date, LocalTime.of(11, 0))
            val service = CherryDiscoveryEngineService(context, db)
            assertTrue(service.savedCursors(target, asOfBefore)
                .values.all { it == null })
            assertTrue(service.trials().lockedTrials().isEmpty())

            val first = service.searchMore(target, CherryDiscoveryKind.LONE, asOfBefore)
            val second = service.searchMore(target, CherryDiscoveryKind.LONE, asOfBefore)
            val earlierId = requireNotNull(first.cursor.newCandidate).id
            assertNotEquals(earlierId, second.cursor.newCandidate?.id)
            val reopened = CherryDiscoveryEngineService(context, db)
            assertEquals(second.cursor,
                reopened.savedCursors(target, asOfBefore)[CherryDiscoveryKind.LONE])
            val preview = reopened.preview(target, CherryDiscoveryKind.LONE,
                earlierId, asOfBefore)
            assertEquals(earlierId, preview?.id)
            val evidence = requireNotNull(reopened.previewEvidence(
                target, CherryDiscoveryKind.LONE, earlierId, asOfBefore))
            assertEquals(preview, evidence.formula)
            assertEquals(preview?.matchedDraws?.size, evidence.matched.size)
            assertTrue(evidence.matched.isNotEmpty())
            assertTrue(evidence.matched.all { it.digit == "00" })
            assertNull(reopened.previewEvidence(target, CherryDiscoveryKind.LONE,
                "unknown-formula-id", asOfBefore))
            assertNull(reopened.preview(target, CherryDiscoveryKind.LONE,
                "unknown-formula-id", asOfBefore))

            // The UI observes REAL scanned positions, not an animation timer.
            val progress = mutableListOf<Int>()
            val third = reopened.searchMore(
                target, CherryDiscoveryKind.LONE, asOfBefore,
                onProgress = { progress.add(it) })
            assertTrue(progress.isNotEmpty())
            assertEquals(third.cursor.nextPosition, progress.last())
            assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
            assertTrue(progress.all { it in 0..828 })

            val selections = CherryDiscoveryKind.entries.associateWith {
                if (it == CherryDiscoveryKind.LONE) earlierId else null
            }
            val cutoff = CherryProspectiveTrialClock.cutoff(target)
            val locked = reopened.lock(target, selections, cutoff.minusSeconds(90),
                asOfBefore)
            val pending = reopened.checkConfirmedResults(
                asOfBefore, cutoff.plusSeconds(60))
            assertEquals(locked.lockedTrials(), pending.lockedTrials())
            assertTrue(pending.verifiedResults().isEmpty())

            // A saved result before its publication boundary cannot settle.
            db.winningNumberDao().upsert(WinningNumberEntity(
                date = target.date, session = target.session,
                digit = "00", createdAt = 1, updatedAt = 1))
            assertTrue(reopened.checkConfirmedResults(
                asOfBefore, cutoff.plusSeconds(60)).verifiedResults().isEmpty())

            val asOfAfter = LocalDateTime.of(target.date, LocalTime.of(12, 30))
            val checked = reopened.checkConfirmedResults(
                asOfAfter, cutoff.plusSeconds(60))
            assertEquals(1, checked.verifiedResults().size)
            assertEquals("00", checked.verifiedResults().single().actualPair)
            assertEquals(HistoricSource.WINNER,
                checked.verifiedResults().single().resultSource)
            assertEquals(1, checked.stats(CherryDiscoveryKind.LONE).checked)
            assertEquals(locked.lockedTrials(), checked.lockedTrials())
            // A second result-check cannot rewrite a settled future prediction.
            assertEquals(checked.verifiedResults(),
                reopened.checkConfirmedResults(
                    asOfAfter, cutoff.plusSeconds(120)).verifiedResults())
        } finally {
            db.close()
        }
    }

}
