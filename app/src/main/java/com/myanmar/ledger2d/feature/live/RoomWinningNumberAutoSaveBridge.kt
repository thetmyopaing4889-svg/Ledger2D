package com.myanmar.ledger2d.feature.live

import android.content.Context
import android.util.Log
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.repository.ClosedDayRepository
import com.myanmar.ledger2d.core.repository.LiveDailyResultRepository
import com.myanmar.ledger2d.core.repository.WinningNumberRepository
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Separate Room -> operational Winner route. This does not fetch data and does
 * not change the existing Live Collector or Calendar ingestion/display path.
 */
internal class RoomWinningNumberAutoSaveBridge(
    private val context: Context,
    private val liveResults: LiveDailyResultRepository,
    private val winners: WinningNumberRepository,
    private val closedDays: ClosedDayRepository,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate = { currentYangonDate() },
    private val clock: () -> LocalTime = { LocalTime.now(YANGON) },
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            liveResults.observeAll().collect { rows ->
                try {
                    syncCurrentDay(rows)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Room-to-WinningNumber sync pass failed", error)
                }
            }
        }
    }

    private suspend fun syncCurrentDay(rows: List<LiveDailyResultEntity>) {
        val date = today()
        val time = clock()
        if (!DrawSchedule.isWeekday(date)) return

        val lukeClosedHold = liveClosedDayHoldDate(
            closedDate = LiveClosedDayStore.load(context),
            today = date,
            now = time,
        ) != null
        val manuallyClosed = closedDays.isClosed(date)
        if (lukeClosedHold || manuallyClosed) return

        // Only today's Room row is eligible. History Result / Calendar projection
        // is intentionally not used as an input to operational Winner storage.
        val row = rows.firstOrNull { it.date == date } ?: return
        val candidates = RoomWinningNumberAutoSavePolicy.candidates(
            row = row,
            today = date,
            now = time,
            isClosedDay = false,
        )
        for (candidate in candidates) {
            try {
                winners.syncFromLive(candidate.date, candidate.session, candidate.digit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Auto Save failed for ${candidate.date}/${candidate.session}", error)
            }
        }
    }

    private companion object {
        const val TAG = "WinnerAutoSave"
    }
}
