package com.myanmar.ledger2d.feature.live

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myanmar.ledger2d.LedgerApplication
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * A bounded, one-shot fallback for the existing app-scoped LIVE collector.
 * Reuses Luke parsing, the LIVE Room checkpoint/merge rules and the manual
 * winner-lock policy. Does not own or modify LIVE UI/temporary-winner state.
 */
internal class LiveBackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // The Activity owns LIVE polling while the app is visible.
        if (LiveAppVisibility.isVisible) return Result.success()

        val date = currentYangonDate()
        val time = LocalTime.now(YANGON)
        if (!isWorkingDay(date) || time.isBefore(MORNING_REFERENCE)) return Result.success()
        if (liveClosedDayHoldDate(LiveClosedDayStore.load(applicationContext), date, time) != null) {
            return Result.success()
        }

        return try {
            val app = applicationContext as LedgerApplication
            val container = app.container
            val stored = container.liveResults.get(date)

            // Repair a current-day missing operational winner even if the LIVE
            // calendar has already received its complete record.
            saveEligibleWinners(app, date, time, stored)
            if (!BackgroundLiveSyncPolicy.needsFetch(date, time, stored) ||
                LiveAppVisibility.isVisible
            ) {
                // A user may foreground the app while the worker is checking Room.
                Result.success()
            } else {
                val feed = withContext(Dispatchers.IO) { LiveApi.fetch() }
                val completedDate = currentYangonDate()
                val completedTime = LocalTime.now(YANGON)

                // Protect the day boundary and all existing current-day rules.
                if (feed == null || completedDate != date ||
                    liveClosedDayHoldDate(
                        LiveClosedDayStore.load(applicationContext), completedDate, completedTime,
                    ) != null
                ) {
                    Result.success() // periodic work will check again
                } else {
                    val patches = buildLiveDailyResultPatches(feed, completedDate, completedTime)
                    if (patches.isNotEmpty()) {
                        // Unlike fire-and-forget LiveCollector callbacks, await
                        // Room persistence before completing this Worker.
                        container.liveResults.apply(patches)
                        saveEligibleWinners(
                            app, completedDate, LocalTime.now(YANGON),
                            container.liveResults.get(completedDate),
                        )
                    }
                    Result.success()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Background LIVE sync failed; next periodic attempt will retry", error)
            Result.success()
        }
    }

    private suspend fun saveEligibleWinners(
        app: LedgerApplication,
        date: LocalDate,
        now: LocalTime,
        row: LiveDailyResultEntity?,
    ) {
        if (row == null || currentYangonDate() != date) return
        val container = app.container
        val isClosed = container.closedDays.isClosed(date) ||
            liveClosedDayHoldDate(LiveClosedDayStore.load(applicationContext), date, now) != null

        // Same eligibility policy and atomic manual lock as the existing bridge.
        RoomWinningNumberAutoSavePolicy.candidates(row, date, now, isClosed).forEach {
            container.winners.syncFromLive(it.date, it.session, it.digit)
        }
    }

    companion object {
        const val WORK_NAME = "live-background-daily-sync"
        private const val TAG = "BackgroundLiveSync"
    }
}
