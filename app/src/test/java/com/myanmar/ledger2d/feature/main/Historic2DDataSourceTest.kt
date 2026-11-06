package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.domain.HistoricSource
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Historic2DDataSourceTest {
    private fun daily(date: String, am: String?, pm: String?): LiveDailyResultEntity =
        LiveDailyResultEntity(
            date = LocalDate.parse(date), modern930 = null, internet930 = null,
            modern200 = null, internet200 = null,
            morning2d = am, morningSet = null, morningValue = null,
            evening2d = pm, eveningSet = null, eveningValue = null,
            reference930SourceAt = null, reference200SourceAt = null,
            morningSourceAt = null, eveningSourceAt = null, updatedAt = 1L,
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
        assertEquals(
            "64",
            merged.draws.single { it.date == LocalDate.parse("2026-11-03") &&
                it.session == DrawSession.MORNING }.digit
        )
        assertEquals(
            HistoricSource.HISTORY,
            merged.draws.single { it.date == LocalDate.parse("2026-11-03") &&
                it.session == DrawSession.EVENING }.source
        )
        assertEquals(1, merged.conflicts.size)
    }

    @Test fun currentDayCannotLeakProvisionalValuesIntoHistory() {
        val merged = Historic2DDataSource.resolve(
            liveDaily = listOf(daily("2026-11-06", "64", "65")),
            history = listOf(history("2026-11-05", "23", "32")),
            today = LocalDate.parse("2026-11-06"),
        )
        assertEquals(2, merged.draws.size)
        assertTrue(merged.draws.all { it.date == LocalDate.parse("2026-11-05") })
    }
}
