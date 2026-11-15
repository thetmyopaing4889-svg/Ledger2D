package com.myanmar.ledger2d.feature.main

import android.content.Context
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.domain.CherryDiscoveryBackgroundRunner
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryDiscoveredFormula
import com.myanmar.ledger2d.core.domain.CherryFormulaDiscoveryEngine
import com.myanmar.ledger2d.core.domain.CherryDiscoverySearchCursor
import com.myanmar.ledger2d.core.domain.CherryProspectiveTrialJournal
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.repository.CherryDiscoveryCursorStore
import com.myanmar.ledger2d.core.repository.CherryProspectiveTrialStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Stable engine-only entry point; no UI, observers, automatic search or jobs. */
internal data class CherryDiscoverySearchReceipt(
    val cursor: CherryDiscoverySearchCursor,
    val databaseRowsRead: Int,
    val completedSlotsScored: Int,
)

/**
 * One explicit Search More request = load bounded settled data, resume a
 * checkpoint or restart after data correction, compute off Main, and commit
 * progress. Nothing happens unless its suspend functions are invoked.
 *
 * Locking is separately explicit. A genuine future pre-result audit needs a
 * trusted timestamp; local Instants and SharedPreferences cannot attest one.
 */
internal class CherryDiscoveryEngineService(
    context: Context,
    database: LedgerDatabase,
) {
    private val source = CherryDiscoveryBoundedSource(database)
    private val cursors = CherryDiscoveryCursorStore(context)
    private val trials = CherryProspectiveTrialStore(context)
    private val mutex = Mutex()
    private val yangon = ZoneId.of("Asia/Yangon")

    suspend fun searchMore(
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        asOf: LocalDateTime = LocalDateTime.now(yangon),
    ): CherryDiscoverySearchReceipt = mutex.withLock {
        val data = source.latest(target, asOf)
        val previous = withContext(Dispatchers.Default) {
            cursors.resume(kind, target, data.draws, data.closedDates)
        }
        val next = CherryDiscoveryBackgroundRunner.searchMore(
            data.draws, target, kind, previous, data.closedDates)
        withContext(Dispatchers.IO) { cursors.persist(next) }
        CherryDiscoverySearchReceipt(
            cursor = next,
            databaseRowsRead = data.databaseRowsRead,
            completedSlotsScored = data.draws.size.coerceAtMost(200),
        )
    }

    /** Restore persisted progress without running a fresh Search More. */
    suspend fun savedCursors(
        target: DrawIdentity,
        asOf: LocalDateTime = LocalDateTime.now(yangon),
    ): Map<CherryDiscoveryKind, CherryDiscoverySearchCursor?> = mutex.withLock {
        val data = source.latest(target, asOf)
        withContext(Dispatchers.Default) {
            CherryDiscoveryKind.entries.associateWith { kind ->
                cursors.resume(kind, target, data.draws, data.closedDates)
            }
        }
    }

    /**
     * Rebuild exactly one previously offered candidate for user inspection.
     * Verifies the unchanged result window and semantic behavior fingerprint.
     * Does not search or commit a new offer.
     */
    suspend fun preview(
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        formulaId: String,
        asOf: LocalDateTime = LocalDateTime.now(yangon),
    ): CherryDiscoveredFormula? = mutex.withLock {
        val data = source.latest(target, asOf)
        withContext(Dispatchers.Default) {
            val cursor = cursors.resume(kind, target, data.draws, data.closedDates)
                ?: return@withContext null
            if (formulaId !in cursor.offeredIds) return@withContext null
            val position = cursor.offeredPositions[formulaId]
                ?: return@withContext null
            val scan = CherryFormulaDiscoveryEngine.nextAlternative(
                data.draws, target, kind, position, data.closedDates)
            scan.candidate?.takeIf {
                scan.historySignature == cursor.engine.historySignature &&
                    scan.nextPosition == position + 1 &&
                    scan.behaviorKey == cursor.offeredBehaviors[formulaId] &&
                    it.id == formulaId
            }
        }
    }

    /**
     * Explicit refresh of previously locked, unfinished trials from persisted
     * winner/daily sources only. A user action invokes this; it never starts a
     * new formula search and never revises existing settlement records.
     */
    suspend fun checkConfirmedResults(
        asOf: LocalDateTime = LocalDateTime.now(yangon),
        checkedAt: Instant = Instant.now(),
    ): CherryProspectiveTrialJournal = mutex.withLock {
        var journal = withContext(Dispatchers.IO) { trials.load() }
        val done = journal.verifiedResults().map { it.target }.toSet()
        for (lock in journal.lockedTrials()) {
            if (lock.target in done || checkedAt.isBefore(lock.cutoffAt)) continue
            val final = source.confirmedFor(lock.target, asOf) ?: continue
            val updated = withContext(Dispatchers.IO) {
                journal.evaluate(final, checkedAt)
            }
            withContext(Dispatchers.IO) { trials.persist(updated) }
            journal = updated
        }
        journal
    }

    /** Only a previously offered formula (or the preserved best) can be locked. */
    suspend fun lock(
        target: DrawIdentity,
        selectedIds: Map<CherryDiscoveryKind, String?>,
        lockedAt: Instant,
        asOf: LocalDateTime = LocalDateTime.now(yangon),
    ): CherryProspectiveTrialJournal = mutex.withLock {
        val data = source.latest(target, asOf)
        val allCursors = CherryDiscoveryKind.entries.associateWith { kind ->
            withContext(Dispatchers.Default) {
                cursors.resume(kind, target, data.draws, data.closedDates)
            }
        }
        withContext(Dispatchers.IO) {
            val next = trials.load().lockFromSearchCursors(
                allCursors, selectedIds, lockedAt, data.closedDates,
                history = data.draws)
            trials.persist(next)
            next
        }
    }

    /**
     * A caller must supply a FINAL confirmed winner/daily result. This method
     * never constructs or edits a result, never settles a HISTORICAL fallback,
     * and never changes a previously locked formula.
     */
    suspend fun recordConfirmedResult(
        confirmed: HistoricDraw,
        evaluatedAt: Instant,
    ): CherryProspectiveTrialJournal = mutex.withLock {
        withContext(Dispatchers.IO) {
            val next = trials.load().evaluate(confirmed, evaluatedAt)
            trials.persist(next)
            next
        }
    }

    suspend fun trials(): CherryProspectiveTrialJournal =
        withContext(Dispatchers.IO) { trials.load() }
}
