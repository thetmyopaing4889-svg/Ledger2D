package com.myanmar.ledger2d.feature.live

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Compatibility shim for WorkManager requests persisted by older releases.
 *
 * Historical backfill has been retired. LedgerApplication cancels the unique
 * work chain at startup; if a queued request is restored before cancellation
 * finishes, this worker completes without fetching or writing any data and
 * deliberately does not enqueue a successor.
 */
internal class LiveRoomKeeperWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val WORK_NAME = "live-room-keeper"
    }
}
