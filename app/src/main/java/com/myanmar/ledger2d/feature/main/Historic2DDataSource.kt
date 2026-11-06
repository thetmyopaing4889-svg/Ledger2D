package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.domain.HistoricDrawMerger
import com.myanmar.ledger2d.core.domain.HistoricMerge
import com.myanmar.ledger2d.core.domain.HistoricSource
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Read-only historical analytics projection. No writes to history, live, or
 * operational winning_numbers. The app's LIVE current-day values are not yet
 * persistently marked as verified final, so today's values are deliberately
 * withheld until the following day. This avoids provisional checkpoint leaks.
 */
internal object Historic2DDataSource {
    fun resolve(
        liveDaily: List<LiveDailyResultEntity>,
        history: List<HistoryResultEntity>,
        today: LocalDate,
    ): HistoricMerge {
        val primary = liveDaily.flatMap { row ->
            listOfNotNull(
                row.morning2d?.let { HistoricDraw(row.date, DrawSession.MORNING, it, HistoricSource.DAILY) },
                row.evening2d?.let { HistoricDraw(row.date, DrawSession.EVENING, it, HistoricSource.DAILY) },
            )
        }
        val secondary = history.flatMap { row ->
            listOf(
                HistoricDraw(row.date, DrawSession.MORNING, row.morning2d, HistoricSource.HISTORY),
                HistoricDraw(row.date, DrawSession.EVENING, row.evening2d, HistoricSource.HISTORY),
            )
        }
        return HistoricDrawMerger.merge(primary, secondary, today.minusDays(1))
    }
}
