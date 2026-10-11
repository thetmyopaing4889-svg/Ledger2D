package com.myanmar.ledger2d.feature.live

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Compatibility no-op for a Keeper WorkManager request persisted by an older
 * app version. Keep this class name until the old unique work has been cancelled.
 */
internal class LiveRoomKeeperWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.success()
}

internal object LiveRoomKeeperScheduler {
    private const val WORK_NAME = "live-room-keeper"

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
