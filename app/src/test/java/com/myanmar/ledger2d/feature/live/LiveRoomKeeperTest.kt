package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.ClosedDayEntity
import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
import com.myanmar.ledger2d.core.repository.ClosedDayRepository
import com.myanmar.ledger2d.core.repository.LiveDailyResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LiveRoomKeeperTest {
    private val friday = LocalDate.of(2026, 10, 9)
    private val monday = LocalDate.of(2026, 10, 12)

    @Test
    fun fresh_install_monday_starts_from_previous_working_day_and_does_not_touch_today() = runTest {
        val live = FakeLiveRepository(
            mapOf(
                monday to completeRow(monday),
            ),
        )
        val keeper = keeper(
            live = live,
            today = monday,
            primaryRows = mapOf(friday to completeHistory(friday)),
        )

        val outcome = keeper.runOnce()

        assertEquals(LiveRoomKeeperOutcome.Complete, outcome)
        assertTrue(live.get(friday) != null)
        assertEquals("22", live.get(friday)?.morning2d)
        assertEquals("25", live.get(friday)?.evening2d)
        assertEquals("80", live.get(friday)?.modern930)
        assertEquals("98", live.get(friday)?.modern200)

        // Today is never part of Keeper processing.
        assertEquals("12", live.get(monday)?.morning2d)
    }

    @Test
    fun closed_working_day_is_skipped_without_blocking_later_gap() = runTest {
        val closedDate = LocalDate.of(2026, 10, 8)
        val dateBefore = LocalDate.of(2026, 10, 7)

        val live = FakeLiveRepository()
        val keeper = keeper(
            live = live,
            today = monday,
            start = dateBefore,
            closedDates = setOf(closedDate),
            primaryRows = mapOf(
                dateBefore to completeHistory(dateBefore),
            ),
        )

        val outcome = keeper.runOnce()

        assertEquals(LiveRoomKeeperOutcome.Complete, outcome)
        assertTrue(live.get(dateBefore) != null)
        assertEquals(dateBefore.plusDays(1), keeper.checkpoint.get())
    }

    @Test
    fun primary_partial_then_backup_fills_only_missing_fields() = runTest {
        val date = friday
        val live = FakeLiveRepository(
            mapOf(
                date to LiveDailyResultEntity(
                    date = date,
                    modern930 = "80",
                    internet930 = "33",
                    modern200 = "98",
                    internet200 = "78",
                    morning2d = null,
                    morningSet = null,
                    morningValue = null,
                    evening2d = null,
                    eveningSet = null,
                    eveningValue = null,
                    reference930SourceAt = 100L,
                    reference200SourceAt = 200L,
                    morningSourceAt = null,
                    eveningSourceAt = null,
                    updatedAt = 200L,
                ),
            ),
        )

        var primaryCalls = 0
        var backupCalls = 0
        val keeper = keeper(
            live = live,
            today = monday,
            start = date,
            primary = object : HistoricalBackfillSource {
                override suspend fun fetch(date: LocalDate): HistoryResultEntity? {
                    primaryCalls++
                    return completeHistory(date).copy(
                        morning2d = "35",
                        morningSet = "-",
                        morningValue = "-",
                        evening2d = "-",
                        eveningSet = "-",
                        eveningValue = "-",
                    )
                }
            },
            backup = object : HistoricalBackfillSource {
                override suspend fun fetch(date: LocalDate): HistoryResultEntity? {
                    backupCalls++
                    return completeHistory(date)
                }
            },
        )

        assertEquals(LiveRoomKeeperOutcome.Complete, keeper.runOnce())
        assertEquals(1, primaryCalls)
        assertEquals(1, backupCalls)
        assertEquals("35", live.get(date)?.morning2d)
        assertEquals("1,000", live.get(date)?.morningSet)
        assertEquals("25", live.get(date)?.evening2d)
        assertEquals("3,000", live.get(date)?.eveningSet)
        assertEquals(100L, live.get(date)?.reference930SourceAt)
    }

    @Test
    fun incomplete_backup_does_not_advance_checkpoint() = runTest {
        val date = friday
        val live = FakeLiveRepository()

        val keeper = keeper(
            live = live,
            today = monday,
            start = date,
            primaryRows = emptyMap(),
            backupRows = mapOf(
                date to completeHistory(date).copy(
                    modern930 = "-",
                    internet930 = "-",
                    modern200 = "-",
                    internet200 = "-",
                ),
            ),
        )

        val outcome = keeper.runOnce()

        assertTrue(outcome is LiveRoomKeeperOutcome.Pending)
        assertEquals(date, keeper.checkpoint.get())
    }

    @Test
    fun checkpoint_stops_at_first_incomplete_gap_instead_of_skipping_it() = runTest {
        val first = LocalDate.of(2026, 10, 6)
        val second = LocalDate.of(2026, 10, 7)
        val live = FakeLiveRepository()

        val keeper = keeper(
            live = live,
            today = monday,
            start = first,
            primaryRows = mapOf(
                first to completeHistory(first),
                second to HistoryResultEntity(
                    date = second,
                    morning2d = "22",
                    morningSet = "1,000",
                    morningValue = "2,000",
                    evening2d = "-",
                    eveningSet = "-",
                    eveningValue = "-",
                    modern930 = "-",
                    internet930 = "-",
                    modern200 = "-",
                    internet200 = "-",
                ),
            ),
            backupRows = emptyMap(),
        )

        val outcome = keeper.runOnce()

        assertTrue(outcome is LiveRoomKeeperOutcome.Pending)
        assertEquals(second, (outcome as LiveRoomKeeperOutcome.Pending).date)
        assertEquals(second, keeper.checkpoint.get())
        assertTrue(live.get(first) != null)
        assertEquals(null, live.get(second)?.evening2d)
    }

    private fun keeper(
        live: FakeLiveRepository,
        today: LocalDate,
        start: LocalDate = previousWorkingDay(today),
        closedDates: Set<LocalDate> = emptySet(),
        primaryRows: Map<LocalDate, HistoryResultEntity> = emptyMap(),
        backupRows: Map<LocalDate, HistoryResultEntity> = emptyMap(),
        primary: HistoricalBackfillSource = MapSource(primaryRows),
        backup: HistoricalBackfillSource = MapSource(backupRows),
    ): TestKeeper {
        val checkpoint = TestCheckpointStore(start)
        val actual = LiveRoomKeeper(
            liveResults = live,
            closedDays = FakeClosedDayRepository(closedDates),
            primary = primary,
            backup = backup,
            checkpointStore = checkpoint,
            todayProvider = { today },
        )
        return TestKeeper(actual, checkpoint)
    }

    private data class TestKeeper(
        val keeper: LiveRoomKeeper,
        val checkpoint: TestCheckpointStore,
    ) {
        suspend fun runOnce() = keeper.runOnce()
    }

    private class MapSource(
        private val rows: Map<LocalDate, HistoryResultEntity>,
    ) : HistoricalBackfillSource {
        override suspend fun fetch(date: LocalDate): HistoryResultEntity? = rows[date]
    }

    private class TestCheckpointStore(
        private var value: LocalDate?,
    ) : HistoricalCheckpointStore {
        override fun get(): LocalDate? = value
        override fun set(value: LocalDate) {
            this.value = value
        }
    }

    private class FakeClosedDayRepository(
        private val closedDates: Set<LocalDate>,
    ) : ClosedDayRepository {
        private val flow = MutableStateFlow(
            closedDates.map { ClosedDayEntity(0, it, 0L, 0L) },
        )

        override fun observeAll(): Flow<List<ClosedDayEntity>> = flow
        override suspend fun isClosed(date: LocalDate): Boolean = date in closedDates
        override suspend fun add(date: LocalDate): Long = error("unused")
        override suspend fun remove(value: ClosedDayEntity) = error("unused")
    }

    private class FakeLiveRepository(
        initial: Map<LocalDate, LiveDailyResultEntity> = emptyMap(),
    ) : LiveDailyResultRepository {
        private val rows = initial.toMutableMap()
        private val flow = MutableStateFlow(rows.values.sortedByDescending { it.date })

        override fun observeAll(): Flow<List<LiveDailyResultEntity>> = flow
        override fun observe(date: LocalDate): Flow<LiveDailyResultEntity?> =
            flow.map { it.firstOrNull { row -> row.date == date } }

        override suspend fun get(date: LocalDate): LiveDailyResultEntity? = rows[date]

        override suspend fun apply(patches: List<LiveDailyResultPatch>): Int {
            patches.forEach { patch ->
                val current = rows[patch.date]
                rows[patch.date] = mergeForTest(current, patch)
            }
            flow.value = rows.values.sortedByDescending { it.date }
            return patches.size
        }

        private fun mergeForTest(
            current: LiveDailyResultEntity?,
            patch: LiveDailyResultPatch,
        ): LiveDailyResultEntity {
            val base = current ?: LiveDailyResultEntity(
                date = patch.date,
                modern930 = null,
                internet930 = null,
                modern200 = null,
                internet200 = null,
                morning2d = null,
                morningSet = null,
                morningValue = null,
                evening2d = null,
                eveningSet = null,
                eveningValue = null,
                reference930SourceAt = null,
                reference200SourceAt = null,
                morningSourceAt = null,
                eveningSourceAt = null,
                updatedAt = 0L,
            )
            return base.copy(
                modern930 = patch.modern930 ?: base.modern930,
                internet930 = patch.internet930 ?: base.internet930,
                modern200 = patch.modern200 ?: base.modern200,
                internet200 = patch.internet200 ?: base.internet200,
                morning2d = patch.morning2d ?: base.morning2d,
                morningSet = patch.morningSet ?: base.morningSet,
                morningValue = patch.morningValue ?: base.morningValue,
                evening2d = patch.evening2d ?: base.evening2d,
                eveningSet = patch.eveningSet ?: base.eveningSet,
                eveningValue = patch.eveningValue ?: base.eveningValue,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun completeHistory(date: LocalDate) = HistoryResultEntity(
        date = date,
        morning2d = "22",
        morningSet = "1,000",
        morningValue = "2,000",
        evening2d = "25",
        eveningSet = "3,000",
        eveningValue = "4,000",
        modern930 = "80",
        internet930 = "33",
        modern200 = "98",
        internet200 = "78",
    )

    private fun completeRow(date: LocalDate) = LiveDailyResultEntity(
        date = date,
        modern930 = "80",
        internet930 = "33",
        modern200 = "98",
        internet200 = "78",
        morning2d = "12",
        morningSet = "1,000",
        morningValue = "2,000",
        evening2d = "25",
        eveningSet = "3,000",
        eveningValue = "4,000",
        reference930SourceAt = 1L,
        reference200SourceAt = 2L,
        morningSourceAt = 3L,
        eveningSourceAt = 4L,
        updatedAt = 4L,
    )

    private fun previousWorkingDay(date: LocalDate): LocalDate {
        var cursor = date.minusDays(1)
        while (cursor.dayOfWeek.value > 5) cursor = cursor.minusDays(1)
        return cursor
    }
}
