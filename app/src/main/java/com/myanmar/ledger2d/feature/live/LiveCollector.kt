package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

// ============================================================================
// Configuration — the LIVE polling interval lives here (single constant)
// ============================================================================

/** LIVE SET/VALUE/2D polling cadence while a live window is active. Tune here. */
internal const val LIVE_POLL_INTERVAL_MS = 5_000L

/**
 * Modern/Internet reference refresh cadence in the 09:30/14:00 windows.
 * Reference values change rarely; they are deliberately NOT polled at the
 * 5-second LIVE cadence.
 */
internal const val LIVE_REFERENCE_FETCH_INTERVAL_MS = 60_000L

// Scheduled LIVE windows (device local time).
private val MORNING_REFERENCE_START: LocalTime = LocalTime.of(9, 30)   // 09:30 Modern/Internet
private val MORNING_LIVE_START: LocalTime = LocalTime.of(11, 30)       // 11:30 LIVE SET/VALUE/2D
private val MORNING_FINAL_AT: LocalTime = LocalTime.of(12, 1)          // 12:01 final (inclusive)
private val EVENING_REFERENCE_START: LocalTime = LocalTime.of(14, 0)   // 14:00 Modern/Internet
private val EVENING_LIVE_START: LocalTime = LocalTime.of(16, 0)        // 16:00 LIVE SET/VALUE/2D
private val EVENING_FINAL_AT: LocalTime = LocalTime.of(16, 30)         // 16:30 final (inclusive)

/** What the collector should do at a given moment. */
internal enum class LiveWindowAction { NONE, REFERENCE_ONLY, LIVE_POLLING }

/**
 * Pure schedule decision (unit-tested):
 *  - 09:30–11:30 and 14:00–16:00: reference fetches only (Modern/Internet).
 *  - 11:30–12:01 and 16:00–16:30 (inclusive ends): LIVE polling; the final
 *    tick at 12:01 / 16:30 also picks up the finalized result fields.
 *  - Everything else: no polling at all.
 */
internal fun liveWindowAction(time: LocalTime): LiveWindowAction = when {
    !time.isBefore(MORNING_REFERENCE_START) && time.isBefore(MORNING_LIVE_START) -> LiveWindowAction.REFERENCE_ONLY
    !time.isBefore(MORNING_LIVE_START) && !time.isAfter(MORNING_FINAL_AT) -> LiveWindowAction.LIVE_POLLING
    !time.isBefore(EVENING_REFERENCE_START) && time.isBefore(EVENING_LIVE_START) -> LiveWindowAction.REFERENCE_ONLY
    !time.isBefore(EVENING_LIVE_START) && !time.isAfter(EVENING_FINAL_AT) -> LiveWindowAction.LIVE_POLLING
    else -> LiveWindowAction.NONE
}

/**
 * Process-wide LIVE collector.
 *
 * Owned by the Application (not the screen): it keeps observing/refreshing the
 * cached state during the scheduled windows even when the LIVE screen is
 * closed, using short request/response polling (never a held-open connection).
 * Outside the windows the loop idles without any network activity.
 *
 * The cache is the single source the LIVE screen renders; re-entering the
 * screen shows the latest cached state immediately (StateFlow semantics).
 */
internal class LiveCollector(
    private val scope: CoroutineScope,
    private val fetcher: suspend () -> LiveFeedData?,
    private val clock: () -> LocalTime = { LocalTime.now() },
) {
    private val _state = MutableStateFlow<LiveUiState>(LiveUiState.Loading)
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null
    private var latestCycle = 0L
    private var lastReferenceFetchMs = 0L

    /**
     * One fetch cycle. A still-running request is never duplicated (overlap
     * prevention). State is untouched while a request is in flight; on success
     * the newest response replaces the cache immediately (stale cleared); on
     * failure the last valid data is retained and marked stale. UI animation
     * is presentation-only and can never delay these updates.
     */
    fun fetchCycle() {
        if (fetchJob?.isActive == true) return
        fetchJob = scope.launch {
            val cycle = ++latestCycle
            val next = try {
                fetcher()
            } catch (_: Exception) {
                null
            }
            if (cycle == latestCycle) {
                _state.update { s ->
                    when {
                        next != null -> LiveUiState.Data(next, stale = false)
                        s is LiveUiState.Data -> s.copy(stale = true) // retain last valid data
                        else -> LiveUiState.Error(retrying = false)
                    }
                }
            }
        }
    }

    /** Window-gated polling loop. Ticks every LIVE_POLL_INTERVAL_MS. */
    fun start() {
        scope.launch {
            while (isActive) {
                when (liveWindowAction(clock())) {
                    LiveWindowAction.NONE -> Unit // outside windows: no network activity
                    LiveWindowAction.REFERENCE_ONLY -> {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastReferenceFetchMs >= LIVE_REFERENCE_FETCH_INTERVAL_MS) {
                            lastReferenceFetchMs = nowMs
                            fetchCycle()
                        }
                    }
                    LiveWindowAction.LIVE_POLLING -> fetchCycle()
                }
                delay(LIVE_POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        @Volatile private var shared: LiveCollector? = null

        /** App-wide shared collector cache. The LIVE screen observes this. */
        val instance: LiveCollector
            get() = shared ?: error("LiveCollector not started")

        /** Called once from LedgerApplication.onCreate. Survives screen changes. */
        fun startOnce() {
            if (shared != null) return
            synchronized(this) {
                if (shared == null) {
                    val collector = LiveCollector(
                        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                        fetcher = { withContext(Dispatchers.IO) { LiveApi.fetch() } },
                    )
                    collector.start()
                    shared = collector
                }
            }
        }
    }
}
