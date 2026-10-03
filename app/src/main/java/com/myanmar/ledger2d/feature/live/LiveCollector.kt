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
 * - one scheduled request every 3 seconds while a live/final result is expected
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

internal fun dailyCycleDate(
    date: LocalDate = currentYangonDate(),
    time: LocalTime = LocalTime.now(YANGON),
): LocalDate =
    if (time.isBefore(MORNING_REFERENCE)) date.minusDays(1) else date

private fun isDisplayableFeedForSchedule(
    feed: LiveFeedData,
    _scheduleTime: LocalTime,
): Boolean {
    val feedDate = canonicalDate(feed.date) ?: return false
    val today = currentYangonDate()

    // Luke's "date" is the latest completed draw date, not the app's daily
    // cycle date. Keep yesterday's completed snapshot available as a fallback
    // throughout today's cycle; the daily flow is controlled by Yangon time.
    return feedDate == today || feedDate == today.minusDays(1)
}

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
    val effectiveScheduleTime = scheduleTime ?: now.atZone(YANGON).toLocalTime()
    val feed = p?.feed?.takeIf {
        isDisplayableFeedForSchedule(it, effectiveScheduleTime)
    }

    val today = currentYangonDate()
    val previousDayFinal =
        feed?.takeIf { canonicalDate(it.date) == today.minusDays(1) }?.let(::latestFinalFor)
            ?: cachedFinal?.takeIf { canonicalDate(it.date) == today.minusDays(1) }

    if (feed == null) {
        val cached = cachedFinal?.takeIf {
            val d = canonicalDate(it.date)
            d == today || d == today.minusDays(1)
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

    // The app clock controls the daily phase. Luke's date/current_time are
    // provider data only and must never advance the app into another phase.
    val phaseTime = effectiveScheduleTime

    return when {
        phaseTime.isBefore(MORNING_LIVE) -> {
            LiveResolution(
                feed,
                previousDayFinal ?: cachedFinal?.takeIf { canonicalDate(it.date) == today },
                false,
                LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(MORNING_CLOSE) -> {
            val hero = liveHero() ?: finalHero() ?: previousDayFinal
            LiveResolution(
                feed,
                hero,
                liveHero() != null,
                if (liveHero() != null) LiveStatus.LIVE_CONFIRMED else LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(LocalTime.of(13, 0)) -> {
            val hero = if (morningFinal) finalHero() else liveHero() ?: previousDayFinal
            LiveResolution(
                feed,
                hero,
                !morningFinal && liveHero() != null,
                when {
                    morningFinal -> LiveStatus.FINAL_CONFIRMED
                    canShowLive -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(EVENING_CLOSE) -> {
            val live = liveHero()
            val hero = live ?: finalHero() ?: previousDayFinal
            LiveResolution(
                feed,
                hero,
                live != null,
                if (live != null) LiveStatus.LIVE_CONFIRMED else LiveStatus.FINAL_CONFIRMED,
                "",
                1,
                age(p),
            )
        }

        else -> {
            val hero = if (eveningFinal) finalHero() else liveHero() ?: finalHero() ?: previousDayFinal
            LiveResolution(
                feed,
                hero,
                !eveningFinal && canShowLive,
                when {
                    eveningFinal -> LiveStatus.FINAL_CONFIRMED
                    canShowLive -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
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
    private val cacheFeedLoader: () -> LiveFeedData? = { null },
    private val cacheFeedSaver: (LiveFeedData) -> Unit = {},
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
    private var reference930Cycle: Job? = null
    private var reference930CycleDate: LocalDate? = null
    private var reference200Cycle: Job? = null
    private var reference200CycleDate: LocalDate? = null
    private var reference930CompleteDate: LocalDate? = null
    private var reference200CompleteDate: LocalDate? = null
    private var referenceResetDate: LocalDate? = null
    private var reference930: Pair<String, String>? = null
    private var reference930Date: LocalDate? = null
    private var reference200: Pair<String, String>? = null
    private var reference200Date: LocalDate? = null
    private var reference930PendingDate: LocalDate? = null
    private var reference200PendingDate: LocalDate? = null

    private var primary: SourceObservation? = null
    private var lastLive: LiveHeroSnapshot? = null
    private var lastFinal: LiveHeroSnapshot? = null

    init {
        val scheduleTime = clock()
        val cachedFeed = cacheFeedLoader()
            ?.takeIf { isDisplayableFeedForSchedule(it, scheduleTime) }

        if (cachedFeed != null) {
            val startedAt = monotonicMs()
            primary = SourceObservation(cachedFeed, startedAt, startedAt, 0L)
            lastFinal = latestFinalFor(cachedFeed)
            publishLocked()
        } else {
            val cached = cacheLoader()
                ?.takeIf { canonicalDate(it.date) == currentYangonDate() }

            if (cached != null) {
                lastFinal = cached
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

            if (feed == null || !isUsableLukeSnapshot(feed)) return@launch

            synchronized(stateLock) {
                if (sequence <= latestAppliedSequence.get()) return@synchronized

                val previous = primary?.feed
                val incomingTime = parseDecisionInstant(feed)
                val previousTime = previous?.let(::parseDecisionInstant)

                if (
                    incomingTime != null &&
                    previousTime != null &&
                    incomingTime.isBefore(previousTime)
                ) {
                    return@synchronized
                }

                val protected = protectFinalSessions(previous, feed)
                latestAppliedSequence.set(sequence)
                primary = SourceObservation(
                    protected,
                    finishedAt,
                    startedAt,
                    finishedAt - startedAt,
                )
                cacheFeedSaver(protected)
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
        )
    }

    private fun isUsableLukeSnapshot(feed: LiveFeedData): Boolean {
        if (!isDisplayableFeedForSchedule(feed, clock())) return false
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

    private fun validReferencePair(
        feed: LiveFeedData?,
        modern: String,
        internet: String,
    ): Boolean =
        feed != null &&
            isValidLive2d(modern) &&
            isValidLive2d(internet)

    private fun mergeReferenceIntoFeed(base: LiveFeedData?): LiveFeedData? {
        if (base == null) return null

        val today = currentYangonDate()
        val t = clock()
        val cycleDate = dailyCycleDate(today, t)
        var out = base

        // 09:30 starts the new cycle. A successful pair is applied directly;
        // after a failed attempt, pending masks the provider's previous-day
        // values. The 14:00 slot is also reset at 09:30.
        when {
            reference930Date == cycleDate && reference930 != null -> {
                val pair = reference930!!
                out = out.copy(
                    modern930 = pair.first,
                    internet930 = pair.second,
                )
            }
            reference930PendingDate == cycleDate -> {
                out = out.copy(
                    modern930 = LIVE_PENDING,
                    internet930 = LIVE_PENDING,
                )
            }
        }

        when {
            reference200Date == cycleDate && reference200 != null -> {
                val pair = reference200!!
                out = out.copy(
                    modern200 = pair.first,
                    internet200 = pair.second,
                )
            }
            reference200PendingDate == cycleDate -> {
                out = out.copy(
                    modern200 = LIVE_PENDING,
                    internet200 = LIVE_PENDING,
                )
            }
        }

        val pendingSessions = LiveSessionData(
            LIVE_PENDING,
            LIVE_PENDING,
            LIVE_PENDING,
            false,
        )

        // A successful 09:30 reference resets the session cards immediately.
        // When 09:30 has not succeeded yet, old cards may remain temporarily.
        // At 11:30, the cards must reset regardless of reference success.
        if (
            referenceResetDate == cycleDate &&
            !t.isBefore(MORNING_REFERENCE) &&
            t.isBefore(MORNING_LIVE)
        ) {
            out = out.copy(
                morning = pendingSessions,
                evening = pendingSessions,
            )
        } else if (
            !t.isBefore(MORNING_LIVE) &&
            !currentDay(out)
        ) {
            // Luke can still return yesterday's completed feed after today's
            // live boundary. Keep it only as the previous-day hero fallback;
            // today's session cards stay Pending until today's feed arrives.
            out = out.copy(
                morning = pendingSessions,
                evening = pendingSessions,
            )
        }

        return out
    }

    private suspend fun fetchReferencePair(
        isMorning: Boolean,
        cycleDate: LocalDate,
    ): Boolean {
        val feed = try {
            fetcher()
        } catch (_: Exception) {
            null
        }

        synchronized(stateLock) {
            if (currentYangonDate() != cycleDate) return false

            if (isMorning) {
                val valid = validReferencePair(
                    feed,
                    feed?.modern930 ?: LIVE_PENDING,
                    feed?.internet930 ?: LIVE_PENDING,
                )

                if (valid) {
                    reference930 =
                        feed!!.modern930 to feed.internet930
                    reference930Date = cycleDate
                    reference930PendingDate = null
                    reference930CompleteDate = cycleDate
                    referenceResetDate = cycleDate
                } else {
                    // Do not clear before the first request. If the first
                    // request succeeds, the UI transitions old -> new
                    // directly. Only after an invalid/failed attempt do we
                    // enter the explicit pending state.
                    reference930 = null
                    reference930Date = null
                    reference930PendingDate = cycleDate
                }
            } else {
                val valid = validReferencePair(
                    feed,
                    feed?.modern200 ?: LIVE_PENDING,
                    feed?.internet200 ?: LIVE_PENDING,
                )

                if (valid) {
                    reference200 =
                        feed!!.modern200 to feed.internet200
                    reference200Date = cycleDate
                    reference200PendingDate = null
                    reference200CompleteDate = cycleDate
                } else {
                    reference200 = null
                    reference200Date = cycleDate
                    reference200PendingDate = cycleDate
                }
            }

            publishLocked()
        }

        return if (isMorning) {
            reference930CompleteDate == cycleDate
        } else {
            reference200CompleteDate == cycleDate
        }
    }

    private fun fetchReference930Cycle() {
        val today = currentYangonDate()
        if (reference930CompleteDate == today) {
            return
        }
        if (reference930Cycle?.isActive == true) {
            if (reference930CycleDate == today) return
            reference930Cycle?.cancel()
            reference930Cycle = null
        }

        reference930CycleDate = today
        reference930Cycle = scope.launch {
            val cycleDate = today

            while (isActive) {
                val now = clock()

                // Keep retrying across midnight. The prior 09:30 cycle owns
                // the retry window until the next 09:30 boundary.
                if (currentYangonDate() != cycleDate && !now.isBefore(MORNING_REFERENCE)) {
                    return@launch
                }

                if (fetchReferencePair(true, cycleDate)) return@launch

                delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
            }
        }
    }

    private fun fetchReference200Cycle() {
        val today = currentYangonDate()
        if (reference200CompleteDate == today) {
            return
        }
        if (reference200Cycle?.isActive == true) {
            if (reference200CycleDate == today) return
            reference200Cycle?.cancel()
            reference200Cycle = null
        }

        reference200CycleDate = today
        reference200Cycle = scope.launch {
            val cycleDate = today

            while (isActive) {
                val now = clock()

                // The 14:00 cycle is independent and keeps retrying through
                // midnight until the next day's 09:29:59 boundary.
                if (currentYangonDate() != cycleDate && !now.isBefore(MORNING_REFERENCE)) {
                    return@launch
                }
                if (currentYangonDate() == cycleDate && now.isBefore(AFTERNOON_REFERENCE)) {
                    return@launch
                }

                if (fetchReferencePair(false, cycleDate)) return@launch

                delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
            }
        }
    }

    private fun maybeReferenceFetch(t: LocalTime) {
        val today = currentYangonDate()

        if (t >= MORNING_REFERENCE) {
            synchronized(stateLock) {
                if (reference200CompleteDate != today && reference200PendingDate != today) {
                    // The new daily cycle owns the 14:00 reference slot from
                    // 09:30 onward.
                    reference200 = null
                    reference200Date = null
                    reference200PendingDate = today
                    publishLocked()
                }
            }

            if (reference930CompleteDate != today) {
                fetchReference930Cycle()
            }
        }

        if (t >= AFTERNOON_REFERENCE && reference200CompleteDate != today) {
            fetchReference200Cycle()
        }

        // If 09:30 has not succeeded by 11:30, force the session-card reset
        // exactly once. This only changes the display projection; the raw
        // provider snapshot and existing LIVE/final engine remain untouched.
        if (
            t >= MORNING_LIVE &&
            reference930CompleteDate != today &&
            referenceResetDate != today
        ) {
            synchronized(stateLock) {
                if (reference930CompleteDate != today && referenceResetDate != today) {
                    referenceResetDate = today
                    publishLocked()
                }
            }
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
            t >= EVENING_CLOSE && t < EVENING_CATCHUP_END ->
                f?.evening?.finalized != true
            else -> false
        }
    }

    private fun publishLocked() {
        val displayPrimary = primary?.let { observation ->
            mergeReferenceIntoFeed(observation.feed)?.let { merged ->
                observation.copy(feed = merged)
            } ?: observation
        }

        val resolution = resolveLiveState(
            p = displayPrimary,
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

    fun start() {
        if (!started.compareAndSet(false, true)) return

        fetchCycle()

        schedulerJob = scope.launch {
            while (isActive) {
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
                    cacheFeedLoader = { LiveCacheStore.loadFeed(context) },
                    cacheFeedSaver = { LiveCacheStore.saveFeed(context, it) },
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
    private const val FEED_KEY = "latest_feed"

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

            snapshot.takeIf {
                canonicalDate(it.date) == currentYangonDate()
            }
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

    fun loadFeed(context: Context): LiveFeedData? {
        return runCatching {
            val raw = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(FEED_KEY, null)
                ?: return null

            val o = JSONObject(raw)
            LiveFeedData(
                date = o.optString("date", ""),
                currentTime = o.optString("currentTime", ""),
                live = o.optString("live", LIVE_PENDING),
                liveSet = o.optString("liveSet", LIVE_PENDING),
                liveVal = o.optString("liveVal", LIVE_PENDING),
                morning = loadSession(o.optJSONObject("morning")),
                evening = loadSession(o.optJSONObject("evening")),
                modern930 = o.optString("modern930", LIVE_PENDING),
                internet930 = o.optString("internet930", LIVE_PENDING),
                modern200 = o.optString("modern200", LIVE_PENDING),
                internet200 = o.optString("internet200", LIVE_PENDING),
                sourceTag = o.optString("sourceTag", "LUKE"),
                serverTimeEpochMs = if (o.has("serverTimeEpochMs") && !o.isNull("serverTimeEpochMs")) {
                    o.optLong("serverTimeEpochMs")
                } else {
                    null
                },
            )
        }.getOrNull()
    }

    private fun loadSession(o: JSONObject?): LiveSessionData {
        if (o == null) return LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false)
        return LiveSessionData(
            result = o.optString("result", LIVE_PENDING),
            set = o.optString("set", LIVE_PENDING),
            value = o.optString("value", LIVE_PENDING),
            finalized = o.optBoolean("finalized", false),
            historyId = o.optString("historyId").ifBlank { null },
            providerOpenTime = o.optString("providerOpenTime").ifBlank { null },
        )
    }

    fun saveFeed(context: Context, feed: LiveFeedData) {
        runCatching {
            val o = JSONObject()
                .put("date", feed.date)
                .put("currentTime", feed.currentTime)
                .put("live", feed.live)
                .put("liveSet", feed.liveSet)
                .put("liveVal", feed.liveVal)
                .put("morning", saveSession(feed.morning))
                .put("evening", saveSession(feed.evening))
                .put("modern930", feed.modern930)
                .put("internet930", feed.internet930)
                .put("modern200", feed.modern200)
                .put("internet200", feed.internet200)
                .put("sourceTag", feed.sourceTag)
                .apply {
                    feed.serverTimeEpochMs?.let { put("serverTimeEpochMs", it) }
                }

            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(FEED_KEY, o.toString())
                .apply()
        }
    }

    private fun saveSession(session: LiveSessionData): JSONObject =
        JSONObject()
            .put("result", session.result)
            .put("set", session.set)
            .put("value", session.value)
            .put("finalized", session.finalized)
            .apply {
                session.historyId?.let { put("historyId", it) }
                session.providerOpenTime?.let { put("providerOpenTime", it) }
            }
}
