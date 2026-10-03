package com.myanmar.ledger2d.feature.live

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val YANGON = ZoneId.of("Asia/Yangon")

private fun monotonicMs(): Long = runCatching {
    SystemClock.elapsedRealtime()
}.getOrElse {
    System.nanoTime() / 1_000_000L
}

/**
 * Luke-only Phase 1:
 * - scheduled requests every 3 seconds while live/final data is expected
 * - request completion never controls the next request
 * - failed requests never erase the last successful snapshot
 * - late/out-of-order responses cannot overwrite a newer successful snapshot
 */
internal const val NORMAL_POLL_INTERVAL_MS = 3_000L
internal const val CLOSING_POLL_INTERVAL_MS = NORMAL_POLL_INTERVAL_MS
internal const val CYCLE_DEADLINE_MS = 0L
internal const val FINAL_GRACE_MS = 30_000L
internal const val SOURCE_FRESHNESS_MS = 5_000L
internal const val SOURCE_TIME_SKEW_MS = 2_000L
internal const val LIVE_REFERENCE_FETCH_INTERVAL_MS = 60_000L

private val MORNING_REFERENCE = LocalTime.of(9, 30)
private val MORNING_LIVE = LocalTime.of(11, 30)
private val MORNING_CLOSE = LocalTime.of(12, 1)
private val MORNING_CATCHUP_END = LocalTime.of(12, 31)
private val AFTERNOON_REFERENCE = LocalTime.of(14, 0)
private val EVENING_LIVE = LocalTime.of(16, 0)
private val EVENING_CLOSE = LocalTime.of(16, 30)
private val EVENING_CATCHUP_END = LocalTime.of(17, 0)
// Continue final-result catch-up in the background for a bounded period after 5 PM.
// This is final-result retry only; live-number polling never continues after 4:30 PM.
private val EVENING_RETRY_END = LocalTime.of(18, 0)

internal const val LIVE_PENDING = "--"
internal const val LIVE_SESSION_MORNING_LABEL = "12:01 PM"
internal const val LIVE_SESSION_EVENING_LABEL = "4:30 PM"

data class LiveHeroSnapshot(
    val result: String,
    val set: String,
    val value: String,
    val sessionLabel: String,
    val date: String,
)

internal enum class LiveWindowAction {
    NONE,
    REFERENCE_ONLY,
    LIVE_POLLING,
    FINALIZING,
}

enum class LiveStatus {
    WAITING,
    LIVE_CONFIRMED,
    LIVE_DEGRADED,
    WAITING_FOR_ALIGNMENT,
    LIVE_CONFLICT,
    STALE,
    FINALIZING,
    WAITING_FOR_PRIMARY,
    DRAW_FREEZE,
    RESULT_AVAILABLE,
    FINAL_CONFIRMED,
    DEGRADED_FINAL,
    FINAL_CONFLICT,
    STALE_PRIMARY,
}

internal data class SourceObservation(
    val feed: LiveFeedData,
    val fetchedAtElapsedMs: Long,
    val requestStartedElapsedMs: Long,
    val roundTripMs: Long,
)

internal data class LiveResolution(
    val displayFeed: LiveFeedData?,
    val hero: LiveHeroSnapshot?,
    val heroLive: Boolean,
    val status: LiveStatus,
    val message: String,
    val sourceCount: Int,
    val staleAgeMs: Long,
)

internal fun liveWindowAction(t: LocalTime): LiveWindowAction = when {
    t.isBefore(MORNING_REFERENCE) -> LiveWindowAction.NONE
    t.isBefore(MORNING_LIVE) -> LiveWindowAction.REFERENCE_ONLY
    t.isBefore(MORNING_CLOSE) -> LiveWindowAction.LIVE_POLLING
    t.isBefore(MORNING_CATCHUP_END) -> LiveWindowAction.FINALIZING
    t.isBefore(AFTERNOON_REFERENCE) -> LiveWindowAction.NONE
    t.isBefore(AFTERNOON_REFERENCE.plusMinutes(1)) -> LiveWindowAction.REFERENCE_ONLY
    t.isBefore(EVENING_LIVE) -> LiveWindowAction.NONE
    t.isBefore(EVENING_CLOSE) -> LiveWindowAction.LIVE_POLLING
    t.isBefore(EVENING_CATCHUP_END) -> LiveWindowAction.FINALIZING
    else -> LiveWindowAction.NONE
}

internal fun currentYangonDate(): LocalDate = LocalDate.now(YANGON)

internal fun canonicalDate(raw: String): LocalDate? = runCatching {
    LocalDate.parse(raw.trim())
}.getOrElse {
    runCatching {
        LocalDate.parse(
            raw.trim(),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        )
    }.getOrNull()
}

internal fun parseDecisionInstant(f: LiveFeedData): Instant? {
    f.serverTimeEpochMs?.let { return Instant.ofEpochMilli(it) }
    val raw = f.currentTime.trim()
    if (raw.isBlank() || raw == LIVE_PENDING) return null

    return try {
        when {
            raw.contains("T") -> Instant.parse(raw)
            raw.contains(" ") -> java.time.LocalDateTime
                .parse(raw.replace(' ', 'T'))
                .atZone(YANGON)
                .toInstant()
            else -> {
                val d = canonicalDate(f.date) ?: return null
                java.time.LocalDateTime
                    .of(d, LocalTime.parse(raw.take(8)))
                    .atZone(YANGON)
                    .toInstant()
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun isValidLive2d(v: String): Boolean =
    v.matches(Regex("^[0-9]{2}$"))

private fun validMoney(v: String): Boolean =
    v.isNotBlank() && v != LIVE_PENDING

private fun currentDay(f: LiveFeedData): Boolean =
    canonicalDate(f.date) == currentYangonDate()

private fun age(o: SourceObservation?): Long =
    if (o == null) Long.MAX_VALUE
    else maxOf(0L, monotonicMs() - o.fetchedAtElapsedMs)

private fun liveValid(o: SourceObservation?): Boolean {
    val f = o?.feed ?: return false
    return currentDay(f) &&
        isValidLive2d(f.live) &&
        validMoney(f.liveSet) &&
        validMoney(f.liveVal) &&
        parseDecisionInstant(f) != null
}

private fun finalValid(session: LiveSessionData?, feed: LiveFeedData?): Boolean {
    if (session == null || feed == null || !currentDay(feed) || !session.finalized) return false
    return isValidLive2d(session.result) &&
        validMoney(session.set) &&
        validMoney(session.value)
}

private fun latestFinalFor(feed: LiveFeedData): LiveHeroSnapshot? = when {
    feed.evening.finalized -> LiveHeroSnapshot(
        feed.evening.result,
        feed.evening.set,
        feed.evening.value,
        LIVE_SESSION_EVENING_LABEL,
        feed.date,
    )
    feed.morning.finalized -> LiveHeroSnapshot(
        feed.morning.result,
        feed.morning.set,
        feed.morning.value,
        LIVE_SESSION_MORNING_LABEL,
        feed.date,
    )
    else -> null
}

internal fun resolveLiveState(
    p: SourceObservation?,
    s: SourceObservation?,
    now: Instant,
    lastLive: LiveHeroSnapshot?,
    cachedFinal: LiveHeroSnapshot?,
    scheduleTime: LocalTime? = null,
): LiveResolution {
    val feed = p?.feed?.takeIf(::currentDay)
    val decisionTime = feed
        ?.let(::parseDecisionInstant)
        ?.atZone(YANGON)
        ?.toLocalTime()
        ?: scheduleTime
        ?: now.atZone(YANGON).toLocalTime()

    if (feed == null) {
        // Until today's 11:30 LIVE session starts, keep yesterday evening's
        // final visible as the carry-over display. It is explicitly dated
        // yesterday and is never treated as today's result.
        val cached = cachedFinal?.takeIf {
            decisionTime.isBefore(MORNING_LIVE) &&
                canonicalDate(it.date) == currentYangonDate().minusDays(1)
        }
        return LiveResolution(null, cached, false, LiveStatus.WAITING, "", 0, age(p))
    }

    val canShowLive = liveValid(p)
    val morningFinal = finalValid(feed.morning, feed)
    val eveningFinal = finalValid(feed.evening, feed)

    fun liveHero(): LiveHeroSnapshot? =
        if (canShowLive) {
            LiveHeroSnapshot(
                result = feed.live,
                set = feed.liveSet,
                value = feed.liveVal,
                sessionLabel = feed.currentTime,
                date = feed.date,
            )
        } else {
            null
        }

    fun finalHero(): LiveHeroSnapshot? = latestFinalFor(feed)

    return when {
        decisionTime.isBefore(MORNING_LIVE) -> {
            // Daily reset window: from 09:30 until 11:30 the reference table
            // belongs to TODAY, while the hero continues carrying yesterday's
            // evening final until today's LIVE feed becomes available.
            val cached = cachedFinal?.takeIf {
                canonicalDate(it.date) == currentYangonDate().minusDays(1)
            }
            LiveResolution(
                feed,
                cached,
                false,
                LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        decisionTime.isBefore(MORNING_CLOSE) -> {
            val live = liveHero()
            val carryOver = cachedFinal?.takeIf {
                canonicalDate(it.date) == currentYangonDate().minusDays(1)
            }
            val hero = live ?: carryOver ?: finalHero()
            LiveResolution(
                feed,
                hero,
                live != null || (hero != null && carryOver != null),
                if (live != null) LiveStatus.LIVE_CONFIRMED else LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        decisionTime.isBefore(MORNING_CATCHUP_END) -> {
            val live = liveHero()
            val carryOver = cachedFinal?.takeIf {
                canonicalDate(it.date) == currentYangonDate().minusDays(1)
            }
            val hero = if (morningFinal) finalHero() else live ?: carryOver
            LiveResolution(
                feed,
                hero,
                !morningFinal && hero != null,
                when {
                    morningFinal -> LiveStatus.FINAL_CONFIRMED
                    hero != null -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }

        decisionTime.isBefore(EVENING_LIVE) -> {
            // Afternoon idle period: freeze the morning final. Never expose
            // Luke's post-draw live field as a new LIVE session.
            val hero = finalHero()
            LiveResolution(
                feed,
                hero,
                false,
                if (hero != null) LiveStatus.FINAL_CONFIRMED else LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        decisionTime.isBefore(EVENING_CLOSE) -> {
            val hero = liveHero() ?: finalHero()
            LiveResolution(
                feed,
                hero,
                liveHero() != null,
                if (liveHero() != null) LiveStatus.LIVE_CONFIRMED
                else if (hero != null) LiveStatus.FINAL_CONFIRMED
                else LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        else -> {
            // After 4:30 PM the live field is no longer a display hero.
            // Keep the morning final stable until the evening final arrives.
            val hero = finalHero()
            LiveResolution(
                feed,
                hero,
                false,
                if (hero != null) LiveStatus.FINAL_CONFIRMED else LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }
    }
}

internal class LiveCollector(
    private val scope: CoroutineScope,
    private val fetcher: suspend () -> LiveFeedData?,
    private val secondaryFetcher: (suspend () -> LiveFeedData?)? = null,
    private val clock: () -> LocalTime = { LocalTime.now(YANGON) },
    cacheLoader: () -> LiveHeroSnapshot? = { null },
    private val cacheSaver: (LiveHeroSnapshot) -> Unit = {},
) {
    private val _state = MutableStateFlow<LiveUiState>(
        LiveUiState.Data(null, null, false, false)
    )
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val requestSequence = AtomicLong(0L)
    private val latestAppliedSequence = AtomicLong(0L)
    private val stateLock = Any()

    private var schedulerJob: Job? = null
    private var reference930Date: LocalDate? = null
    private var reference200Date: LocalDate? = null
    private var nextReference930AttemptElapsedMs: Long = 0L
    private var nextReference200AttemptElapsedMs: Long = 0L
    private var activeDate: LocalDate = currentYangonDate()

    private var primary: SourceObservation? = null
    private var lastLive: LiveHeroSnapshot? = null
    private var lastFinal: LiveHeroSnapshot? = null

    init {
        // Keep the latest persisted final in memory even when it belongs to
        // yesterday. Before 09:30 it is valid display context; after 09:30
        // the resolver deliberately hides it.
        val cached = cacheLoader()

        if (cached != null) {
            lastFinal = cached

            if (
                clock().isBefore(MORNING_REFERENCE) &&
                canonicalDate(cached.date) == currentYangonDate().minusDays(1)
            ) {
                _state.value = LiveUiState.Data(null, cached, false, false)
            }
        }
    }

    fun fetchCycle() {
        launchRequest()
    }

    private fun launchRequest() {
        val sequence = requestSequence.incrementAndGet()

        scope.launch {
            val startedAt = monotonicMs()
            val feed = try {
                fetcher()
            } catch (_: Exception) {
                null
            }
            val finishedAt = monotonicMs()

            if (feed == null) return@launch
            val normalized = normalizeDailyReset(feed)
            if (!isUsableLukeSnapshot(normalized)) return@launch

            synchronized(stateLock) {
                if (sequence <= latestAppliedSequence.get()) return@synchronized

                val previous = primary?.feed
                val incomingTime = parseDecisionInstant(normalized)
                val previousTime = previous?.let(::parseDecisionInstant)

                if (
                    incomingTime != null &&
                    previousTime != null &&
                    incomingTime.isBefore(previousTime)
                ) {
                    return@synchronized
                }

                val protected = protectFinalSessions(previous, normalized)
                latestAppliedSequence.set(sequence)
                primary = SourceObservation(
                    protected,
                    finishedAt,
                    startedAt,
                    finishedAt - startedAt,
                )
                if (protected.modern930 != LIVE_PENDING || protected.internet930 != LIVE_PENDING) {
                    reference930Date = currentYangonDate()
                }
                if (protected.modern200 != LIVE_PENDING || protected.internet200 != LIVE_PENDING) {
                    reference200Date = currentYangonDate()
                }
                publishLocked()
            }
        }
    }

    private fun protectFinalSessions(
        previous: LiveFeedData?,
        incoming: LiveFeedData,
    ): LiveFeedData {
        if (previous == null || !currentDay(previous) || !currentDay(incoming)) {
            return incoming
        }

        return incoming.copy(
            morning = if (previous.morning.finalized && !incoming.morning.finalized) {
                previous.morning
            } else {
                incoming.morning
            },
            evening = if (previous.evening.finalized && !incoming.evening.finalized) {
                previous.evening
            } else {
                incoming.evening
            },
            modern930 = if (previous.modern930 != LIVE_PENDING && incoming.modern930 == LIVE_PENDING) {
                previous.modern930
            } else {
                incoming.modern930
            },
            internet930 = if (previous.internet930 != LIVE_PENDING && incoming.internet930 == LIVE_PENDING) {
                previous.internet930
            } else {
                incoming.internet930
            },
            modern200 = if (previous.modern200 != LIVE_PENDING && incoming.modern200 == LIVE_PENDING) {
                previous.modern200
            } else {
                incoming.modern200
            },
            internet200 = if (previous.internet200 != LIVE_PENDING && incoming.internet200 == LIVE_PENDING) {
                previous.internet200
            } else {
                incoming.internet200
            },
        )
    }

    /**
     * Luke can expose values from the previous daily cycle in fields that are
     * not yet due for today's cycle. Once the collector has crossed 09:30,
     * today's reference/session state must start clean:
     * - 09:30 Modern/Internet are populated only from today's response.
     * - 14:00 Modern/Internet are forced to pending until the 14:00 cycle.
     * - 12:01 / 4:30 session cards are pending until their own final windows.
     *
     * The LIVE field is intentionally left untouched; display-window logic
     * decides when it can become the hero.
     */
    private fun normalizeDailyReset(feed: LiveFeedData): LiveFeedData {
        val today = currentYangonDate()
        if (canonicalDate(feed.date) != today) return feed

        val t = parseDecisionInstant(feed)
            ?.atZone(YANGON)
            ?.toLocalTime()
            ?: return feed

        return when {
            t >= MORNING_REFERENCE && t < MORNING_CLOSE -> feed.copy(
                morning = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false),
                evening = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false),
                modern200 = LIVE_PENDING,
                internet200 = LIVE_PENDING,
            )
            t >= MORNING_LIVE && t < AFTERNOON_REFERENCE -> feed.copy(
                evening = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false),
                modern200 = LIVE_PENDING,
                internet200 = LIVE_PENDING,
            )
            else -> feed
        }
    }

    private fun isUsableLukeSnapshot(feed: LiveFeedData): Boolean {
        if (!currentDay(feed)) return false
        if (feed.currentTime.isBlank() || parseDecisionInstant(feed) == null) return false

        val hasLive =
            isValidLive2d(feed.live) &&
                validMoney(feed.liveSet) &&
                validMoney(feed.liveVal)

        val hasMorningFinal = finalValid(feed.morning, feed)
        val hasEveningFinal = finalValid(feed.evening, feed)

        return hasLive ||
            hasMorningFinal ||
            hasEveningFinal ||
            feed.modern930 != LIVE_PENDING ||
            feed.internet930 != LIVE_PENDING ||
            feed.modern200 != LIVE_PENDING ||
            feed.internet200 != LIVE_PENDING
    }

    private fun maybeReferenceFetch(t: LocalTime) {
        val today = currentYangonDate()
        val nowElapsed = monotonicMs()

        if (
            t >= MORNING_REFERENCE &&
            reference930Date != today &&
            nowElapsed >= nextReference930AttemptElapsedMs
        ) {
            nextReference930AttemptElapsedMs = nowElapsed + LIVE_REFERENCE_FETCH_INTERVAL_MS
            launchRequest()
            return
        }

        if (
            t >= AFTERNOON_REFERENCE &&
            reference200Date != today &&
            nowElapsed >= nextReference200AttemptElapsedMs
        ) {
            nextReference200AttemptElapsedMs = nowElapsed + LIVE_REFERENCE_FETCH_INTERVAL_MS
            launchRequest()
        }
    }

    private fun shouldPoll(t: LocalTime): Boolean {
        val f = synchronized(stateLock) {
            primary?.feed?.takeIf(::currentDay)
        }

        return when {
            t >= MORNING_LIVE && t < MORNING_CLOSE -> true
            t >= MORNING_CLOSE && t < MORNING_CATCHUP_END ->
                f?.morning?.finalized != true
            t >= EVENING_LIVE && t < EVENING_CLOSE -> true
            t >= EVENING_CLOSE && t < EVENING_RETRY_END ->
                f?.evening?.finalized != true
            else -> false
        }
    }

    private fun publishLocked() {
        val resolution = resolveLiveState(
            p = primary,
            s = null,
            now = Instant.now(),
            lastLive = lastLive,
            cachedFinal = lastFinal,
            scheduleTime = clock(),
        )

        if (resolution.heroLive) {
            lastLive = resolution.hero
        }

        val hero = resolution.hero
        if (
            !resolution.heroLive &&
            hero != null &&
            canonicalDate(hero.date) == currentYangonDate() &&
            hero != lastFinal
        ) {
            lastFinal = hero
            cacheSaver(hero)
        }

        _state.value = LiveUiState.Data(
            feed = resolution.displayFeed,
            hero = resolution.hero,
            heroLive = resolution.heroLive,
            stale = false,
            secondaryFeed = null,
            sourceMessage = "",
            status = resolution.status,
            staleAgeMs = resolution.staleAgeMs,
        )
    }

    private fun reconcileDateBoundary() {
        val today = currentYangonDate()
        if (today == activeDate) return

        synchronized(stateLock) {
            if (today == activeDate) return

            activeDate = today
            primary = null
            lastLive = null
            // Preserve the just-finished day's final in memory. Before 09:30
            // the resolver may show it as yesterday's context; after 09:30 it
            // is hidden automatically and can never become today's hero.
            reference930Date = null
            reference200Date = null
            nextReference930AttemptElapsedMs = 0L
            nextReference200AttemptElapsedMs = 0L
            latestAppliedSequence.set(requestSequence.get())

            val previousDayFinal = lastFinal?.takeIf {
                canonicalDate(it.date) == today.minusDays(1)
            }
            _state.value = if (clock().isBefore(MORNING_REFERENCE)) {
                LiveUiState.Data(null, previousDayFinal, false, false)
            } else {
                LiveUiState.Data(null, null, false, false)
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return

        fetchCycle()

        schedulerJob = scope.launch {
            while (isActive) {
                reconcileDateBoundary()
                val t = clock()
                maybeReferenceFetch(t)

                if (shouldPoll(t)) {
                    launchRequest()
                }

                delay(NORMAL_POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        @Volatile
        private var shared: LiveCollector? = null

        val instance: LiveCollector
            get() = shared ?: error("LiveCollector not started")

        fun startOnce(context: Context) {
            if (shared != null) return

            synchronized(this) {
                if (shared != null) return

                val collector = LiveCollector(
                    scope = CoroutineScope(
                        kotlinx.coroutines.SupervisorJob() + Dispatchers.Default
                    ),
                    fetcher = {
                        withContext(Dispatchers.IO) {
                            LiveApi.fetch()
                        }
                    },
                    secondaryFetcher = null,
                    clock = { LocalTime.now(YANGON) },
                    cacheLoader = { LiveCacheStore.load(context) },
                    cacheSaver = { LiveCacheStore.save(context, it) },
                )

                shared = collector
                collector.start()
            }
        }
    }
}

internal object LiveCacheStore {
    private const val PREFS_NAME = "live_display_cache"
    private const val KEY = "latest_final"

    fun load(context: Context): LiveHeroSnapshot? {
        return try {
            val raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, null)
                ?: return null

            val o = JSONObject(raw)
            if (o.optString("state") != "FINAL_CONFIRMED") return null

            val snapshot = LiveHeroSnapshot(
                result = o.getString("result"),
                set = o.getString("set"),
                value = o.getString("value"),
                sessionLabel = o.getString("sessionLabel"),
                date = o.getString("date"),
            )

            snapshot
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, snapshot: LiveHeroSnapshot) {
        runCatching {
            val o = JSONObject()
                .put("result", snapshot.result)
                .put("set", snapshot.set)
                .put("value", snapshot.value)
                .put("sessionLabel", snapshot.sessionLabel)
                .put("date", snapshot.date)
                .put("state", "FINAL_CONFIRMED")

            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, o.toString())
                .apply()
        }
    }
}
