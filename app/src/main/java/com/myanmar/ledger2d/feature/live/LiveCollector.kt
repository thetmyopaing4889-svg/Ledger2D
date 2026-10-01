package com.myanmar.ledger2d.feature.live

import android.content.Context
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
import org.json.JSONObject
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

/** API sentinel for "not available yet". */
internal const val LIVE_PENDING = "--"

internal const val LIVE_SESSION_MORNING_LABEL = "12:01 PM"
internal const val LIVE_SESSION_EVENING_LABEL = "4:30 PM"

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
 * Persistent hero snapshot. Only FINAL results are ever stored here (and in
 * the display cache) — intermediate LIVE values are never persisted.
 */
data class LiveHeroSnapshot(
    val result: String,
    val set: String,
    val value: String,
    /** "12:01 PM" / "4:30 PM" for finals, or the source time while LIVE. */
    val sessionLabel: String,
    /** Feed date the snapshot was captured on (e.g. "29/09/2026"). */
    val date: String,
)

/** Hero derivation outcome (pure, unit-tested). */
internal data class HeroDerivation(val hero: LiveHeroSnapshot?, val isLive: Boolean)

/**
 * Hero state machine (pure):
 *  - During an active LIVE window: that session's final wins as soon as it
 *    exists; otherwise the streaming live value; otherwise the latest known
 *    final (today's earlier session or the persisted previous final).
 *  - Outside LIVE windows: the latest known final — today's finalized session
 *    if any, else the persisted previous final. Never blank once something
 *    is known.
 */
internal fun deriveHero(feed: LiveFeedData, now: LocalTime, cachedFinal: LiveHeroSnapshot?): HeroDerivation {
    if (liveWindowAction(now) == LiveWindowAction.LIVE_POLLING) {
        val eveningWindow = !now.isBefore(EVENING_LIVE_START)
        val session = if (eveningWindow) feed.evening else feed.morning
        val label = if (eveningWindow) LIVE_SESSION_EVENING_LABEL else LIVE_SESSION_MORNING_LABEL
        if (session.finalized) {
            return HeroDerivation(LiveHeroSnapshot(session.result, session.set, session.value, label, feed.date), isLive = false)
        }
        if (feed.live.isNotBlank() && feed.live != LIVE_PENDING) {
            return HeroDerivation(LiveHeroSnapshot(feed.live, feed.liveSet, feed.liveVal, feed.currentTime, feed.date), isLive = true)
        }
    }
    return HeroDerivation(latestKnownFinal(feed, cachedFinal), isLive = false)
}

private fun latestKnownFinal(feed: LiveFeedData, cachedFinal: LiveHeroSnapshot?): LiveHeroSnapshot? = when {
    feed.evening.finalized -> LiveHeroSnapshot(feed.evening.result, feed.evening.set, feed.evening.value, LIVE_SESSION_EVENING_LABEL, feed.date)
    feed.morning.finalized -> LiveHeroSnapshot(feed.morning.result, feed.morning.set, feed.morning.value, LIVE_SESSION_MORNING_LABEL, feed.date)
    else -> cachedFinal
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
 * screen shows the latest cached state immediately (StateFlow semantics), and
 * the persisted final hero survives app restart.
 */
internal class LiveCollector(
    private val scope: CoroutineScope,
    private val fetcher: suspend () -> LiveFeedData?,
    private val secondaryFetcher: (suspend () -> LiveFeedData?)? = null,
    private val clock: () -> LocalTime = { LocalTime.now() },
    cacheLoader: () -> LiveHeroSnapshot? = { null },
    private val cacheSaver: (LiveHeroSnapshot) -> Unit = {},
) {
    private val _state = MutableStateFlow<LiveUiState>(LiveUiState.Loading)
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null
    private var secondaryFetchJob: Job? = null
    private var latestCycle = 0L
    private var lastReferenceFetchMs = 0L
    private var lastSavedFinal: LiveHeroSnapshot? = null

    init {
        // Surface the persisted final hero immediately (app restart / screen
        // re-entry): the display never starts from a blank state when a cache
        // exists. Today's feed stays null until the first successful fetch.
        val cached = cacheLoader()
        if (cached != null) {
            lastSavedFinal = cached
            _state.value = LiveUiState.Data(feed = null, hero = cached, heroLive = false, stale = false)
        }
    }

    /**
     * One fetch cycle. A still-running request is never duplicated (overlap
     * prevention). State is untouched while a request is in flight; on success
     * the newest response replaces the cache immediately (stale cleared); on
     * failure the last valid data and hero are retained and marked stale. UI
     * animation is presentation-only and can never delay these updates.
     */
    fun fetchCycle() {
        if (fetchJob?.isActive != true) {
            fetchJob = scope.launch {
                val cycle = ++latestCycle
                val next = try { fetcher() } catch (_: Exception) { null }
                if (cycle == latestCycle) applyPrimaryResult(next)
            }
        }
        if (secondaryFetcher != null && secondaryFetchJob?.isActive != true) {
            secondaryFetchJob = scope.launch {
                val next = try { secondaryFetcher.invoke() } catch (_: Exception) { null }
                applySecondaryResult(next)
            }
        }
    }

    private fun applyPrimaryResult(next: LiveFeedData?) {
        if (next == null) {
            _state.update { s ->
                when (s) {
                    is LiveUiState.Data -> s.copy(stale = true) // retain feed + hero
                    else -> LiveUiState.Error(retrying = false)
                }
            }
            return
        }
        val derived = deriveHero(next, serverDecisionTime(next), lastSavedFinal)
        if (!derived.isLive) derived.hero?.let { saveFinalIfNew(it) } // finals only, never live values
        _state.update { s ->
            when (s) {
                is LiveUiState.Data -> s.copy(feed = next, hero = derived.hero ?: s.hero, heroLive = derived.isLive, stale = false, sourceMessage = sourceAgreementMessage(next, s.secondaryFeed))
                else -> LiveUiState.Data(feed = next, hero = derived.hero, heroLive = derived.isLive, stale = false)
            }
        }
    }

    private fun applySecondaryResult(next: LiveFeedData?) {
        _state.update { current ->
            if (current !is LiveUiState.Data) {
                if (next == null) current else {
                    val d = deriveHero(next, serverDecisionTime(next), null)
                    LiveUiState.Data(feed = null, hero = d.hero, heroLive = d.isLive, stale = false, secondaryFeed = next, sourceMessage = "ThaiStock2D only")
                }
            } else {
                current.copy(secondaryFeed = next, sourceMessage = sourceAgreementMessage(current.feed, next))
            }
        }
    }

    private fun sourceAgreementMessage(primary: LiveFeedData?, secondary: LiveFeedData?): String {
        if (primary == null && secondary == null) return ""
        if (primary == null) return "ThaiStock2D only"
        if (secondary == null) return "Luke only"
        if (primary.date != secondary.date) return "DATA MISMATCH • date"
        val evening = !clock().isBefore(EVENING_LIVE_START)
        val pf = if (evening) primary.evening.result else primary.morning.result
        val sf = if (evening) secondary.evening.result else secondary.morning.result
        if (pf != LIVE_PENDING && sf != LIVE_PENDING) return if (pf == sf) "FINAL SOURCES ALIGNED" else "FINAL MISMATCH"
        if (primary.live != LIVE_PENDING && secondary.live != LIVE_PENDING) return if (primary.live == secondary.live) "2 SOURCES ALIGNED" else "DATA MISMATCH"
        return "WAITING FOR SOURCE ALIGNMENT"
    }

    private fun serverDecisionTime(feed: LiveFeedData): LocalTime {
        val raw = feed.currentTime.substringAfterLast(' ').trim()
        return runCatching { LocalTime.parse(raw.take(8)) }.getOrElse { clock() }
    }

    private fun saveFinalIfNew(snapshot: LiveHeroSnapshot) {
        if (snapshot != lastSavedFinal) {
            lastSavedFinal = snapshot
            cacheSaver(snapshot)
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
        fun startOnce(context: Context) {
            if (shared != null) return
            synchronized(this) {
                if (shared == null) {
                    val collector = LiveCollector(
                        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                        fetcher = { withContext(Dispatchers.IO) { LiveApi.fetch() } },
                        secondaryFetcher = { withContext(Dispatchers.IO) { LiveApi.fetchThaiStock() } },
                        cacheLoader = { LiveCacheStore.load(context) },
                        cacheSaver = { LiveCacheStore.save(context, it) },
                    )
                    collector.start()
                    // One-time synchronization fetch at app start: today's
                    // finals, reference values, and hero surface immediately
                    // even when the process starts outside a collection
                    // window. A single request — continuous polling stays
                    // exclusively with the window-gated loop started above.
                    collector.fetchCycle()
                    shared = collector
                }
            }
        }
    }
}

/**
 * Dedicated lightweight display-cache persistence (SharedPreferences + the
 * already-used org.json). Kept strictly separate from the ledger/business Room
 * database: only the latest known FINAL hero snapshot is stored.
 */
internal object LiveCacheStore {
    private const val PREFS_NAME = "live_display_cache"
    private const val KEY_LATEST_FINAL = "latest_final"

    fun load(context: Context): LiveHeroSnapshot? = try {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LATEST_FINAL, null) ?: return null
        val obj = JSONObject(raw)
        LiveHeroSnapshot(
            result = obj.getString("result"),
            set = obj.getString("set"),
            value = obj.getString("value"),
            sessionLabel = obj.getString("sessionLabel"),
            date = obj.getString("date"),
        )
    } catch (_: Exception) {
        null
    }

    fun save(context: Context, snapshot: LiveHeroSnapshot) {
        try {
            val obj = JSONObject()
                .put("result", snapshot.result)
                .put("set", snapshot.set)
                .put("value", snapshot.value)
                .put("sessionLabel", snapshot.sessionLabel)
                .put("date", snapshot.date)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LATEST_FINAL, obj.toString()).apply()
        } catch (_: Exception) {
            // Display cache is best-effort; never crash the collector on it.
        }
    }
}
