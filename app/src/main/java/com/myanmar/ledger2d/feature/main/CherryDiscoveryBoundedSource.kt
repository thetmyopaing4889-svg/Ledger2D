package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.domain.CherryDiscoveryHistoryNormalizer
import com.myanmar.ledger2d.core.domain.CherryFormulaDiscoveryEngine
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Experimental discovery-only Room adapter; no observer or existing feature
 * is changed. Source precedence, manual-winner tombstones and finality are
 * delegated to the SAME existing Historic2DDataSource as Better Data.
 */
internal data class CherryDiscoveryBoundedInput(
    val draws: List<HistoricDraw>,
    val closedDates: Set<LocalDate>,
    val databaseRowsRead: Int,
)

/**
 * Read a bounded number of rows PER SOURCE (not the full archive). Limit 256
 * date rows/source provides some slack for holidays or incomplete source rows;
 * the normalizer restricts the engine's actual input to 200 + 3 slots.
 *
 * A large number of missing dates may yield fewer than 203 slots. This is
 * correct: the engine must never fabricate continuity across missing draws.
 * Caller uses the returned snapshot on Dispatchers.Default for formula work.
 */
internal class CherryDiscoveryBoundedSource(private val database: LedgerDatabase) {
    companion object {
        const val PER_SOURCE_LIMIT = 256
        const val ENGINE_SLOT_LIMIT = CherryFormulaDiscoveryEngine.SEARCH_DRAWS + 3
        private val YANGON = ZoneId.of("Asia/Yangon")
    }

    suspend fun latest(
        target: DrawIdentity,
        asOf: LocalDateTime = LocalDateTime.now(YANGON),
    ): CherryDiscoveryBoundedInput = withContext(Dispatchers.IO) {
        val through = minOf(target.date, asOf.toLocalDate())
        val history = database.historyResultDao()
            .newestThrough(through, PER_SOURCE_LIMIT)
        val daily = database.liveDailyResultDao()
            .newestThrough(through, PER_SOURCE_LIMIT)
        val winners = database.winningNumberDao()
            .newestThrough(through, PER_SOURCE_LIMIT)
        val locks = database.manualWinnerLockDao()
            .newestThrough(through, PER_SOURCE_LIMIT)

        val oldest = listOfNotNull(
            history.minOfOrNull { it.date },
            daily.minOfOrNull { it.date },
            winners.minOfOrNull { it.date },
            locks.minOfOrNull { it.date },
        ).minOrNull() ?: through

        val closed = database.closedDayDao().between(oldest, through)
            .map { it.date }.toSet()
        val merged = Historic2DDataSource.resolve(
            liveDaily = daily,
            history = history,
            today = asOf.toLocalDate(),
            now = asOf.toLocalTime(),
            winners = winners,
            manualLocks = locks,
        )
        val newest = CherryDiscoveryHistoryNormalizer.newestBefore(
            merged.draws, target, closed, ENGINE_SLOT_LIMIT)
        CherryDiscoveryBoundedInput(
            newest, closed,
            history.size + daily.size + winners.size + locks.size,
        )
    }
}
