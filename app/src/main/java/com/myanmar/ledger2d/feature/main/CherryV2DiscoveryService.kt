package com.myanmar.ledger2d.feature.main

import android.content.Context
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryV2HistoricalEvaluator
import com.myanmar.ledger2d.core.domain.CherryV2OrderBenchmark
import com.myanmar.ledger2d.core.domain.CherryV2StreakRegistry
import com.myanmar.ledger2d.core.domain.CherryV2TimedSearch
import com.myanmar.ledger2d.core.repository.CherryV2CheckpointStore
import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Experimental on-demand V2 Android adapter. No constructor side effects,
 * observers, global-scope jobs, services, background pre-runs or UI changes.
 * The caller explicitly invokes search() and owns its Coroutine Job and
 * eventual progress bar. A cancelled Job stops this search synchronously.
 *
 * Uses the existing settled-result precedence / tombstone policy, a bounded
 * 200+3 historical slot window, and an ISOLATED V2 checkpoint namespace.
 * All expensive work is dispatched away from Main.
 */
internal class CherryV2DiscoveryService(
    context: Context,
    database: LedgerDatabase,
) {
    private val input = CherryDiscoveryBoundedSource(database)
    private val store = CherryV2CheckpointStore(context)
    private val sessionLock = Mutex()
    private val yangon = ZoneId.of("Asia/Yangon")

    data class Receipt(
        val result: CherryV2TimedSearch.Result,
        val databaseRowsRead: Int,
        val contiguousSlots: Int,
    ) {
        val recommended get() = result.cursor.recommended()
        val results get() = result.cursor.orderedResults()
    }

    /**
     * Exactly one explicitly requested 30-second (default) computation.
     * Continue search is just a second call with the same target/category
     * and unchanged source results. Every success or cancellation checkpoint
     * is atomically persisted off the Main thread.
     *
     * The budget applies to CPU searching after bounded history preparation.
     * Delays before the search starts (database reads) must be represented
     * separately by any eventual UI timer.
     */
    suspend fun search(
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        asOf: LocalDateTime = LocalDateTime.now(yangon),
        timeBudgetNanos: Long = CherryV2TimedSearch.DEFAULT_BUDGET_NANOS,
    ): Receipt = sessionLock.withLock {
        require(timeBudgetNanos > 0L)
        val bounded = input.latest(
            target, asOf,
            CherryV2HistoricalEvaluator.CONTEXT_LIMIT,
        )
        withContext(Dispatchers.Default) {
            val work = coroutineContext
            val context = CherryV2HistoricalEvaluator.prepare(
                bounded.draws, target, bounded.closedDates,
                checkCancellation = { work.ensureActive() },
            )
            work.ensureActive()
            val existing = withContext(Dispatchers.IO) {
                store.resume(kind, target, context.historySignature)
            }
            // The isolated V2 store validates all persisted state before
            // resuming. Formula Search never writes to existing Champions.
            val result = CherryV2TimedSearch.run(
                context = context,
                kind = kind,
                previous = existing,
                timeBudgetNanos = timeBudgetNanos,
                checkCancellation = { work.ensureActive() },
                onCheckpoint = { state -> store.persist(state) },
            )
            Receipt(result, bounded.databaseRowsRead, context.prior.size)
        }
    }

    /**
     * Opt-in developer research comparison only. Runs NO normal discovery,
     * stores NO checkpoint, and cannot alter any formula/Champion selection.
     * Raw and guided modes use the same exact bounded snapshot and budget.
     */
    suspend fun auditSearchOrder(
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        asOf: LocalDateTime = LocalDateTime.now(yangon),
        candidatesPerMode: Int = CherryV2OrderBenchmark.DEFAULT_CANDIDATES,
        budgetPerModeNanos: Long = CherryV2OrderBenchmark.DEFAULT_BUDGET_NANOS,
    ): CherryV2OrderBenchmark.Comparison = sessionLock.withLock {
        val bounded = input.latest(
            target, asOf,
            CherryV2HistoricalEvaluator.CONTEXT_LIMIT,
        )
        withContext(Dispatchers.Default) {
            val work = coroutineContext
            val context = CherryV2HistoricalEvaluator.prepare(
                bounded.draws, target, bounded.closedDates,
                checkCancellation = { work.ensureActive() },
            )
            CherryV2OrderBenchmark.compare(
                context, kind, candidatesPerMode, budgetPerModeNanos,
                checkCancellation = { work.ensureActive() },
            )
        }
    }

    /** Read-only restore for later UI display. No search is started. */
    suspend fun saved(
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        asOf: LocalDateTime = LocalDateTime.now(yangon),
    ): CherryV2StreakRegistry? = sessionLock.withLock {
        val bounded = input.latest(
            target, asOf,
            CherryV2HistoricalEvaluator.CONTEXT_LIMIT,
        )
        withContext(Dispatchers.Default) {
            val work = coroutineContext
            val context = CherryV2HistoricalEvaluator.prepare(
                bounded.draws, target, bounded.closedDates,
                checkCancellation = { work.ensureActive() },
            )
            withContext(Dispatchers.IO) {
                store.resume(kind, target, context.historySignature)
            }
        }
    }
}
