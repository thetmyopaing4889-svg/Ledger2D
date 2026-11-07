package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.domain.HistoricSource
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class Historic2DDataSourceTest {
    private val yangon = ZoneId.of("Asia/Yangon")
    private fun stamp(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(yangon)
            .toInstant().toEpochMilli()

    private fun daily(
        date: String, am: String?, pm: String?,
        amSource: Long? = null, pmSource: Long? = null,
    ): LiveDailyResultEntity = LiveDailyResultEntity(
        date = LocalDate.parse(date), modern930 = null, internet930 = null,
        modern200 = null, internet200 = null,
        morning2d = am, morningSet = null, morningValue = null,
        evening2d = pm, eveningSet = null, eveningValue = null,
        reference930SourceAt = null, reference200SourceAt = null,
        morningSourceAt = amSource, eveningSourceAt = pmSource, updatedAt = 1L,
    )

    private fun history(date: String, am: String, pm: String): HistoryResultEntity =
        HistoryResultEntity(
            date = LocalDate.parse(date),
            morning2d = am, morningSet = "-", morningValue = "-",
            evening2d = pm, eveningSet = "-", eveningValue = "-",
            modern930 = "-", internet930 = "-", modern200 = "-", internet200 = "-",
        )

    @Test fun dailyPrimaryAndHistoricalFallbackAreSessionScoped() {
        val merged = Historic2DDataSource.resolve(
            liveDaily = listOf(daily("2026-11-03", "64", null)),
            history = listOf(
                history("2026-10-30", "25", "52"),
                history("2026-11-03", "12", "46"),
            ),
            today = LocalDate.parse("2026-11-04"),
        )
        assertEquals(4, merged.draws.size)
        assertEquals("64", merged.draws.single {
            it.date == LocalDate.parse("2026-11-03") &&
                it.session == DrawSession.MORNING
        }.digit)
        assertEquals(HistoricSource.HISTORY, merged.draws.single {
            it.date == LocalDate.parse("2026-11-03") &&
                it.session == DrawSession.EVENING
        }.source)
        assertEquals(1, merged.conflicts.size)
    }

    @Test fun currentDayWithoutProofOfFinalityIsExcluded() {
        val merged = Historic2DDataSource.resolve(
            liveDaily = listOf(daily("2026-11-06", "64", "65")),
            history = listOf(history("2026-11-05", "23", "32"),
                history("2026-11-06", "91", "19")),
            today = LocalDate.parse("2026-11-06"),
            now = LocalTime.of(17, 0),
        )
        assertEquals(2, merged.draws.size)
        assertTrue(merged.draws.all { it.date == LocalDate.parse("2026-11-05") })
    }

    @Test fun confirmedMorningCountsButEveningPendingDoesNot() {
        val merged = Historic2DDataSource.resolve(
            liveDaily = listOf(daily("2026-11-06", "64", "65",
                amSource = stamp("2026-11-06", "12:02"),
                pmSource = stamp("2026-11-06", "16:30"))),
            history = emptyList(),
            today = LocalDate.parse("2026-11-06"),
            now = LocalTime.of(15, 33),
        )
        assertEquals(1, merged.draws.size)
        assertEquals("64", merged.draws.single().digit)
        assertEquals(DrawSession.MORNING, merged.draws.single().session)
    }

    @Test fun confirmedEveningCountsAfterCloseAndNoDuplicates() {
        val merged = Historic2DDataSource.resolve(
            liveDaily = listOf(daily("2026-11-06", "64", "65",
                amSource = stamp("2026-11-06", "12:02"),
                pmSource = stamp("2026-11-06", "16:31"))),
            history = listOf(history("2026-11-06", "22", "33")),
            today = LocalDate.parse("2026-11-06"),
            now = LocalTime.of(17, 0),
        )
        assertEquals(listOf("64", "65"), merged.draws.map { it.digit })
        assertTrue(merged.conflicts.isEmpty())
    }

    @Test fun finalResultWithinEarlyGraceCountsOnlyAfterClose() {
        val daily = listOf(daily("2026-11-06", "64", "65",
            amSource = stamp("2026-11-06", "12:00:55"),
            pmSource = stamp("2026-11-06", "16:29:55")))
        val date = LocalDate.parse("2026-11-06")
        val before = Historic2DDataSource.resolve(daily, emptyList(), date, LocalTime.of(12, 0, 59))
        assertTrue(before.draws.isEmpty())
        val afterMorning = Historic2DDataSource.resolve(daily, emptyList(), date, LocalTime.of(12, 1))
        assertEquals(listOf("64"), afterMorning.draws.map { it.digit })
        val afterEvening = Historic2DDataSource.resolve(daily, emptyList(), date, LocalTime.of(16, 30))
        assertEquals(listOf("64", "65"), afterEvening.draws.map { it.digit })
    }

    @Test fun earlyOrWrongDateSourceCannotProveFinality() {
        val merged = Historic2DDataSource.resolve(
            liveDaily = listOf(daily("2026-11-06", "64", "65",
                amSource = stamp("2026-11-06", "12:00"),
                pmSource = stamp("2026-11-05", "16:40"))),
            history = emptyList(),
            today = LocalDate.parse("2026-11-06"),
            now = LocalTime.of(18, 0),
        )
        assertTrue(merged.draws.isEmpty())
    }

    @Test fun operationalWinnerOverridesDailyAndImportedHistory() {
        val date = LocalDate.parse("2026-11-05")
        val winner = com.myanmar.ledger2d.core.database.WinningNumberEntity(
            date = date, session = DrawSession.MORNING, digit = "78",
            createdAt = 0, updatedAt = 0,
        )
        val result = Historic2DDataSource.resolve(
            liveDaily = listOf(daily("2026-11-05", "12", null)),
            history = listOf(history("2026-11-05", "34", "56")),
            today = LocalDate.parse("2026-11-07"),
            winners = listOf(winner),
        )
        assertEquals("78", result.draws.first { it.date == date && it.session == DrawSession.MORNING }.digit)
        assertEquals(HistoricSource.WINNER,
            result.draws.first { it.date == date && it.session == DrawSession.MORNING }.source)
        assertEquals("56", result.draws.first { it.date == date && it.session == DrawSession.EVENING }.digit)
    }

    @Test fun manualDeletionLockPreventsHistoricFallbackResurrection() {
        val date = LocalDate.parse("2026-11-05")
        val lock = com.myanmar.ledger2d.core.database.ManualWinnerLockEntity(
            date = date, session = DrawSession.MORNING,
            createdAt = 0, updatedAt = 0,
        )
        val result = Historic2DDataSource.resolve(
            liveDaily = emptyList(),
            history = listOf(history("2026-11-05", "12", "34")),
            today = LocalDate.parse("2026-11-07"),
            manualLocks = listOf(lock),
        )
        assertFalse(result.draws.any { it.date == date && it.session == DrawSession.MORNING })
        assertTrue(result.draws.any { it.date == date && it.session == DrawSession.EVENING })
    }

    @Test fun existingManualWinnerRemainsVisibleWithItsLock() {
        val date = LocalDate.parse("2026-11-05")
        val lock = com.myanmar.ledger2d.core.database.ManualWinnerLockEntity(
            date = date, session = DrawSession.MORNING,
            createdAt = 0, updatedAt = 0,
        )
        val winner = com.myanmar.ledger2d.core.database.WinningNumberEntity(
            date = date, session = DrawSession.MORNING, digit = "89",
            createdAt = 0, updatedAt = 0,
        )
        val result = Historic2DDataSource.resolve(
            liveDaily = emptyList(),
            history = listOf(history("2026-11-05", "12", "34")),
            today = LocalDate.parse("2026-11-07"),
            winners = listOf(winner), manualLocks = listOf(lock),
        )
        assertEquals("89", result.draws.first { it.session == DrawSession.MORNING }.digit)
    }

    @Test fun currentWinnerRequiresReachedResultBoundary() {
        val date = LocalDate.parse("2026-11-06")
        val winner = com.myanmar.ledger2d.core.database.WinningNumberEntity(
            date = date, session = DrawSession.EVENING, digit = "45",
            createdAt = 0, updatedAt = 0,
        )
        val early = Historic2DDataSource.resolve(
            emptyList(), emptyList(), date, LocalTime.of(15, 59),
            winners = listOf(winner),
        )
        assertTrue(early.draws.isEmpty())
        val onTime = Historic2DDataSource.resolve(
            emptyList(), emptyList(), date, LocalTime.of(16, 30),
            winners = listOf(winner),
        )
        assertEquals("45", onTime.draws.single().digit)
    }

}
