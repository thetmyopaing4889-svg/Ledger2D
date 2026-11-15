package com.myanmar.ledger2d.feature.main

import android.app.Application
import androidx.room.Room
import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.database.ManualWinnerLockEntity
import com.myanmar.ledger2d.core.database.WinningNumberEntity
import com.myanmar.ledger2d.core.domain.CherryDiscoveryHistoryNormalizer
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.domain.HistoricSource
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class CherryDiscoveryBoundedSourceTest {
    private fun db(): LedgerDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), LedgerDatabase::class.java)
        .allowMainThreadQueries().build()

    private fun row(day: LocalDate, a: String = "12", b: String = "34") =
        HistoryResultEntity(
            date = day,
            morning2d = a, evening2d = b,
            morningSet = "", morningValue = "",
            eveningSet = "", eveningValue = "",
            modern930 = "", internet930 = "",
            modern200 = "", internet200 = "",
        )

    @Test fun databaseReadsOnlyBoundedRowsWithoutDeletingArchive() = runBlocking {
        val database = db()
        try {
            val days = generateSequence(LocalDate.of(2023, 1, 2)) {
                it.plusDays(1)
            }.filter(DrawSchedule::isWeekday).take(350).toList()
            val all = days.mapIndexed { i, date ->
                row(date, (i % 100).toString().padStart(2, '0'),
                    ((i * 7) % 100).toString().padStart(2, '0'))
            }
            database.historyResultDao().upsertAll(all)
            val target = DrawIdentity(days[349], DrawSession.MORNING)
            val asOf = LocalDateTime.of(target.date, LocalTime.of(17, 0))
            val snapshot = CherryDiscoveryBoundedSource(database).latest(target, asOf)
            assertEquals(256, snapshot.databaseRowsRead)
            assertEquals(203, snapshot.draws.size)
            val expected = CherryDiscoveryHistoryNormalizer.newestBefore(
                Historic2DDataSource.resolve(
                    liveDaily = emptyList(),
                    history = all, today = target.date,
                    now = asOf.toLocalTime()).draws,
                target, emptySet(), 203)
            assertEquals(expected, snapshot.draws)
            assertEquals(350, database.historyResultDao().count())
        } finally { database.close() }
    }

    @Test fun verifiedWinnerOverridesHistoryAndDeletedWinnerStaysSuppressed() =
        runBlocking {
            val database = db()
            try {
                val days = generateSequence(LocalDate.of(2026, 9, 1)) {
                    it.plusDays(1)
                }.filter(DrawSchedule::isWeekday).take(16).toList()
                database.historyResultDao().upsertAll(
                    days.map { row(it) })
                val saved = days[14]
                database.winningNumberDao().upsert(WinningNumberEntity(
                    date = saved, session = DrawSession.EVENING,
                    digit = "79", createdAt = 1, updatedAt = 1))
                database.manualWinnerLockDao().upsert(ManualWinnerLockEntity(
                    date = saved, session = DrawSession.MORNING,
                    createdAt = 1, updatedAt = 1))
                val target = DrawIdentity(days[15], DrawSession.MORNING)
                val snapshot = CherryDiscoveryBoundedSource(database).latest(
                    target, LocalDateTime.of(target.date, LocalTime.of(17, 0)))
                assertEquals("79", snapshot.draws.single {
                    it.date == saved && it.session == DrawSession.EVENING
                }.digit)
                assertEquals(HistoricSource.WINNER, snapshot.draws.single {
                    it.date == saved && it.session == DrawSession.EVENING
                }.source)
                assertFalse(snapshot.draws.any {
                    it.date == saved && it.session == DrawSession.MORNING
                })
            } finally { database.close() }
        }

    @Test fun todaysWinnerBeforeCutoffIsNotAnAcceptedResult() = runBlocking {
        val database = db()
        try {
            val day = LocalDate.of(2026, 11, 16)
            database.winningNumberDao().upsert(WinningNumberEntity(
                date = day, session = DrawSession.MORNING, digit = "79",
                createdAt = 1, updatedAt = 1))
            val target = DrawIdentity(day, DrawSession.EVENING)
            val source = CherryDiscoveryBoundedSource(database)
            val early = source.latest(target, LocalDateTime.of(day, LocalTime.of(11, 0)))
            assertTrue(early.draws.isEmpty())
            val after = source.latest(target, LocalDateTime.of(day, LocalTime.of(13, 0)))
            assertEquals(1, after.draws.size)
            assertEquals("79", after.draws.single().digit)
        } finally { database.close() }
    }
}
