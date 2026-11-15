package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

/**
 * Experimental safe entry-point for future UI integration.
 *
 * Compute-heavy work never executes on Main; cancellation is checked during
 * both input scanning and formula evaluation. Nothing is launched in a global
 * scope and nothing continues after the requesting coroutine is cancelled.
 *
 * Not yet used by Compose UI. The caller owns job lifecycle and progress UI.
 */
object CherryDiscoveryBackgroundRunner {
    suspend fun nextRound(
        history: List<HistoricDraw>,
        target: DrawIdentity,
        previousState: CherryDiscoverySession? = null,
        closedDates: Set<LocalDate> = emptySet(),
    ): CherryDiscoverySession = withContext(Dispatchers.Default) {
        val jobContext = coroutineContext
        CherryFormulaDiscoveryEngine.nextRound(
            history = history,
            target = target,
            previousState = previousState,
            closedDates = closedDates,
            checkCancellation = { jobContext.ensureActive() },
        )
    }

    /** Offer the next unseen representative; never block Compose/Main. */
    suspend fun searchMore(
        history: List<HistoricDraw>,
        target: DrawIdentity,
        kind: CherryDiscoveryKind,
        previous: CherryDiscoverySearchCursor? = null,
        closedDates: Set<LocalDate> = emptySet(),
        onProgress: (Int) -> Unit = {},
    ): CherryDiscoverySearchCursor = withContext(Dispatchers.Default) {
        val jobContext = coroutineContext
        CherryDiscoverySearchMore.next(
            history = history,
            target = target,
            kind = kind,
            previous = previous,
            closedDates = closedDates,
            checkCancellation = { jobContext.ensureActive() },
            onProgress = onProgress,
        )
    }

    /** Research mode: 80 time-distributed samples over 2 years by default. */
    suspend fun research(
        history: List<HistoricDraw>,
        throughInclusive: LocalDate,
        years: Int = 2,
        samples: Int = 80,
        closedDates: Set<LocalDate> = emptySet(),
    ): CherryDiscoveryWindowAudit = withContext(Dispatchers.Default) {
        val jobContext = coroutineContext
        CherryDiscoveryWalkForward.replayResearchWindow(
            historicalDraws = history,
            throughInclusive = throughInclusive,
            years = years,
            sampleTargets = samples,
            closedDates = closedDates,
            checkCancellation = { jobContext.ensureActive() },
        )
    }
}
