package com.myanmar.ledger2d.feature.live

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal class LiveRoomKeeperWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? com.myanmar.ledger2d.LedgerApplication
            ?: return Result.success()

        val keeper = LiveRoomKeeper(
            liveResults = application.container.liveResults,
            closedDays = application.container.closedDays,
            primary = GitHubHistoricalBackfillSource(),
            backup = ThaiStockHistoricalBackfillSource(),
            checkpointStore = SharedPrefsHistoricalCheckpointStore(applicationContext),
        )

        val outcome = runCatching { keeper.runOnce() }
            .getOrElse { LiveRoomKeeperOutcome.Pending(java.time.LocalDate.now()) }

        val nextDelayMinutes = when (outcome) {
            LiveRoomKeeperOutcome.Complete -> 6L * 60L
            is LiveRoomKeeperOutcome.Pending -> 15L
        }

        LiveRoomKeeperScheduler.enqueueNext(applicationContext, nextDelayMinutes)
        return Result.success()
    }
}

internal object LiveRoomKeeperScheduler {
    private const val WORK_NAME = "live-room-keeper"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Kick off immediately. After the first run, the Worker appends the next
     * delayed run to the same unique chain, so there is only one Keeper chain.
     */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<LiveRoomKeeperWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    internal fun enqueueNext(context: Context, delayMinutes: Long) {
        val request = OneTimeWorkRequestBuilder<LiveRoomKeeperWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        // APPEND_OR_REPLACE keeps the current worker as the head of one unique
        // chain and places the next run behind it. This avoids overlapping a
        // startup kickoff with a background Keeper run.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
    }
}
