package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.database.WinningNumberEntity
import com.myanmar.ledger2d.core.database.ManualWinnerLockEntity
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.domain.HistoricDrawMerger
import com.myanmar.ledger2d.core.domain.HistoricMerge
import com.myanmar.ledger2d.core.domain.HistoricSource
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Read-only historical analytics projection. Prior dates keep the existing
 * daily-first/history-fallback rule. Today is different: only an independently
 * recorded Luke result checkpoint can be included, never live/provisional values.
 */
internal object Historic2DDataSource {
    private val yangon = ZoneId.of("Asia/Yangon")
    private val morningClose = LocalTime.of(12, 1)
    private val eveningClose = LocalTime.of(16, 30)

    /**
     * A Room result value can be included for today only when its corresponding
     * source timestamp belongs to today and is at/after the scheduled close.
     * LiveDailyResultPatchBuilder writes result_1200/result_430 (not the LIVE
     * checkpoint) into morning2d/evening2d, with that source timestamp.
     * Older/untimestamped rows cannot prove today's finality and are withheld.
     */
    internal fun confirmedToday(
        sourceAt: Long?,
        date: LocalDate,
        close: LocalTime,
        now: LocalTime,
    ): Boolean {
        if (sourceAt == null || now.isBefore(close)) return false
        val observed = runCatching {
            Instant.ofEpochMilli(sourceAt).atZone(yangon).toLocalDateTime()
        }.getOrNull() ?: return false
        // The Room patch builder accepts a 10-second early Luke checkpoint.
        // The value is still the explicit result_1200/result_430 field, never LIVE.
        return observed.toLocalDate() == date &&
            !observed.toLocalTime().isBefore(close.minusSeconds(10))
    }

    fun resolve(
        liveDaily: List<LiveDailyResultEntity>,
        history: List<HistoryResultEntity>,
        today: LocalDate,
        now: LocalTime = LocalTime.now(yangon),
        winners: List<WinningNumberEntity> = emptyList(),
        manualLocks: List<ManualWinnerLockEntity> = emptyList(),
    ): HistoricMerge {
        val primary = liveDaily.flatMap { row ->
            val isToday = row.date == today
            listOfNotNull(
                row.morning2d?.takeIf {
                    !isToday || confirmedToday(row.morningSourceAt, today, morningClose, now)
                }?.let { HistoricDraw(row.date, DrawSession.MORNING, it, HistoricSource.DAILY) },
                row.evening2d?.takeIf {
                    !isToday || confirmedToday(row.eveningSourceAt, today, eveningClose, now)
                }?.let { HistoricDraw(row.date, DrawSession.EVENING, it, HistoricSource.DAILY) },
            )
        }
        // Imported history must never act as proof that a *current-day* session
        // is final. It remains a fallback for all completed prior dates.
        val secondary = history.filter { it.date < today }.flatMap { row ->
            listOf(
                HistoricDraw(row.date, DrawSession.MORNING, row.morning2d, HistoricSource.HISTORY),
                HistoricDraw(row.date, DrawSession.EVENING, row.evening2d, HistoricSource.HISTORY),
            )
        }
        val available = HistoricDrawMerger.merge(primary, secondary, today)
        // Operational winners are authoritative: user-owned corrections override
        // Luke checkpoints, historical imports and previous daily projections.
        // Respect today's result boundary even if the winner was manually saved early.
        val authoritative = winners.mapNotNull { winner ->
            val close = when (winner.session) {
                DrawSession.MORNING -> morningClose
                DrawSession.EVENING -> eveningClose
            }
            if (winner.date > today || (winner.date == today && now.isBefore(close))) {
                null
            } else {
                HistoricDraw(winner.date, winner.session, winner.digit, HistoricSource.WINNER)
            }
        }
        val merged = HistoricDrawMerger.merge(authoritative, available.draws, today)
        // A deleted manual winner leaves a lock/tombstone. In that case a
        // historical or live fallback MUST NOT resurrect the withdrawn result.
        val winnerKeys = authoritative.map { it.date to it.session }.toSet()
        val suppressed = manualLocks.map { it.date to it.session }.toSet() - winnerKeys
        return HistoricMerge(
            draws = merged.draws.filterNot { (it.date to it.session) in suppressed },
            conflicts = (available.conflicts + merged.conflicts).filterNot {
                (it.date to it.session) in suppressed
            },
        )
    }
}
