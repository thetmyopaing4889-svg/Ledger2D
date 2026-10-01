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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val YANGON_ZONE: ZoneId = ZoneId.of("Asia/Yangon")

internal const val NORMAL_POLL_INTERVAL_MS = 3_000L
internal const val CLOSING_POLL_INTERVAL_MS = 1_500L
internal const val CYCLE_DEADLINE_MS = 3_500L
internal const val FINAL_GRACE_MS = 30_000L
internal const val SOURCE_FRESHNESS_MS = 5_000L
internal const val SOURCE_TIME_SKEW_MS = 2_000L
internal const val LIVE_REFERENCE_FETCH_INTERVAL_MS = 60_000L

private val MORNING_LIVE_START = LocalTime.of(11, 0)
private val MORNING_CLOSING_START = LocalTime.of(11, 59)
private val MORNING_FINAL_AT = LocalTime.of(12, 1)
private val EVENING_LIVE_START = LocalTime.of(16, 0)
private val EVENING_CLOSING_START = LocalTime.of(16, 29)
private val EVENING_FINAL_AT = LocalTime.of(16, 30)

internal const val LIVE_PENDING = "--"
internal const val LIVE_SESSION_MORNING_LABEL = "12:01 PM"
internal const val LIVE_SESSION_EVENING_LABEL = "4:30 PM"

internal enum class LiveWindowAction { NONE, REFERENCE_ONLY, LIVE_POLLING, FINALIZING }

internal enum class LiveStatus {
    WAITING, LIVE_CONFIRMED, LIVE_DEGRADED, WAITING_FOR_ALIGNMENT, LIVE_CONFLICT,
    STALE, FINALIZING, WAITING_FOR_PRIMARY, DRAW_FREEZE, RESULT_AVAILABLE,
    FINAL_CONFIRMED, DEGRADED_FINAL, FINAL_CONFLICT, STALE_PRIMARY,
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

internal fun liveWindowAction(time: LocalTime): LiveWindowAction = when {
    time.isBefore(MORNING_LIVE_START) -> LiveWindowAction.NONE
    time.isBefore(MORNING_CLOSING_START) -> LiveWindowAction.LIVE_POLLING
    time <= MORNING_FINAL_AT -> LiveWindowAction.FINALIZING
    time.isBefore(EVENING_LIVE_START) -> LiveWindowAction.NONE
    time.isBefore(EVENING_CLOSING_START) -> LiveWindowAction.LIVE_POLLING
    time <= EVENING_FINAL_AT -> LiveWindowAction.FINALIZING
    else -> LiveWindowAction.NONE
}

internal fun currentYangonDate(): LocalDate = LocalDate.now(YANGON_ZONE)

internal fun canonicalDate(raw: String): LocalDate? {
    val value = raw.trim()
    return runCatching { LocalDate.parse(value) }.getOrElse {
        runCatching { LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull()
    }
}

internal fun parseDecisionInstant(feed: LiveFeedData): Instant? {
    feed.serverTimeEpochMs?.let { return Instant.ofEpochMilli(it) }
    val raw = feed.currentTime.trim()
    if (raw.isBlank() || raw == LIVE_PENDING) return null
    return runCatching {
        if (raw.contains("T")) Instant.parse(raw)
        else if (raw.contains(" ")) LocalDateTime.parse(raw.replace(' ', 'T')).atZone(YANGON_ZONE).toInstant()
        else LocalDateTime.of(canonicalDate(feed.date) ?: return null, LocalTime.parse(raw.take(8)))
            .atZone(YANGON_ZONE).toInstant()
    }.getOrNull()
}

internal fun isValidLive2d(value: String): Boolean = value.matches(Regex("^[0-9]{2}$"))
private fun validMoneyField(value: String): Boolean = value.isNotBlank() && value != LIVE_PENDING
private fun currentDay(feed: LiveFeedData): Boolean = canonicalDate(feed.date) == currentYangonDate()

private fun validLiveSample(observation: SourceObservation): Boolean {
    val feed = observation.feed
    val instant = parseDecisionInstant(feed) ?: return false
    if (!currentDay(feed)) return false
    if (!isValidLive2d(feed.live)) return false
    if (!validMoneyField(feed.liveSet) || !validMoneyField(feed.liveVal)) return false
    val age = System.nanoTime() / 1_000_000L - observation.fetchedAtElapsedMs
    return age <= SOURCE_FRESHNESS_MS && instant.isAfter(Instant.EPOCH)
}

private fun validFinalCandidate(observation: SourceObservation, session: LiveSessionData, requireHistoryId: Boolean): Boolean {
    if (!session.finalized || !currentDay(observation.feed)) return false
    if (!isValidLive2d(session.result) || !validMoneyField(session.set) || !validMoneyField(session.value)) return false
    if (requireHistoryId && session.historyId.isNullOrBlank()) return false
    val decision = parseDecisionInstant(observation.feed) ?: return false
    val candidateTime = session.providerOpenTime?.let(::parseOpenTime)
    return candidateTime == null || candidateTime <= decision.plusSeconds(2)
}

private fun parseOpenTime(raw: String): Instant? {
    val t = runCatching { LocalTime.parse(raw.take(8)) }.getOrNull() ?: return null
    return LocalDateTime.of(currentYangonDate(), t).atZone(YANGON_ZONE).toInstant()
}

private fun timeAligned(a: SourceObservation, b: SourceObservation): Boolean {
    val ai = parseDecisionInstant(a.feed) ?: return false
    val bi = parseDecisionInstant(b.feed) ?: return false
    return kotlin.math.abs(ai.toEpochMilli() - bi.toEpochMilli()) <= SOURCE_TIME_SKEW_MS
}

private fun sessionLabel(time: LocalTime): String =
    if (!time.isBefore(EVENING_LIVE_START)) LIVE_SESSION_EVENING_LABEL else LIVE_SESSION_MORNING_LABEL

private fun latestFinal(feed: LiveFeedData): LiveHeroSnapshot? = when {
    feed.evening.finalized -> LiveHeroSnapshot(feed.evening.result, feed.evening.set, feed.evening.value, LIVE_SESSION_EVENING_LABEL, feed.date)
    feed.morning.finalized -> LiveHeroSnapshot(feed.morning.result, feed.morning.set, feed.morning.value, LIVE_SESSION_MORNING_LABEL, feed.date)
    else -> null
}

internal fun resolveLiveState(
    primary: SourceObservation?,
    secondary: SourceObservation?,
    now: Instant,
    lastConfirmedLive: LiveHeroSnapshot?,
    cachedConfirmedFinal: LiveHeroSnapshot?,
): LiveResolution {
    val primaryValid = primary?.let(::validLiveSample) == true
    val secondaryValid = secondary?.let(::validLiveSample) == true
    val sourceCount = listOf(primaryValid, secondaryValid).count { it }
    val decisionTime = primary?.let(::parseDecisionInstant)
    val localTime = decisionTime?.atZone(YANGON_ZONE)?.toLocalTime()

    if (localTime == null) {
        return LiveResolution(primary?.feed ?: secondary?.feed, lastConfirmedLive ?: cachedConfirmedFinal, false, LiveStatus.WAITING_FOR_ALIGNMENT, "WAITING_FOR_PRIMARY • CLOCK", sourceCount, maxOf(age(primary), age(secondary)))
    }

    when (liveWindowAction(localTime)) {
        LiveWindowAction.FINALIZING -> {
            val evening = !localTime.isBefore(EVENING_LIVE_START)
            val pSession = primary?.let { if (evening) it.feed.evening else it.feed.morning }
            val sSession = secondary?.let { if (evening) it.feed.evening else it.feed.morning }
            val pFinal = primary?.let { pSession?.let { s -> validFinalCandidate(it, s, false) } == true } == true
            val sFinal = secondary?.let { sSession?.let { s -> validFinalCandidate(it, s, true) } == true } == true

            if (!primaryValid) return LiveResolution(primary?.feed, lastConfirmedLive, false, LiveStatus.WAITING_FOR_PRIMARY, "FINALIZING • WAITING_FOR_PRIMARY", sourceCount, maxOf(age(primary), age(secondary)))

            if (pFinal && sFinal) {
                if (pSession!!.result == sSession!!.result) {
                    val hero = LiveHeroSnapshot(pSession.result, pSession.set, pSession.value, sessionLabel(localTime), primary.feed.date)
                    return LiveResolution(primary.feed, hero, false, LiveStatus.FINAL_CONFIRMED, "FINAL_CONFIRMED • CROSS-SOURCE MATCH", 2, 0)
                }
                return LiveResolution(
                    if (evening) primary.feed.copy(evening = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false))
                    else primary.feed.copy(morning = LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false)),
                    lastConfirmedLive, false, LiveStatus.FINAL_CONFLICT, "FINAL_CONFLICT • DATA MISMATCH", 2, maxOf(age(primary), age(secondary))
                )
            }

            if (pFinal) {
                val hero = LiveHeroSnapshot(pSession!!.result, pSession.set, pSession.value, sessionLabel(localTime), primary.feed.date)
                val elapsed = now.toEpochMilli() - decisionTime.toEpochMilli()
                return if (elapsed >= FINAL_GRACE_MS) {
                    LiveResolution(primary.feed, hero, false, LiveStatus.DEGRADED_FINAL, "DEGRADED_FINAL • UNVERIFIED LUKE", 1, age(primary))
                } else {
                    LiveResolution(primary.feed, hero, false, LiveStatus.RESULT_AVAILABLE, "RESULT_AVAILABLE • LUKE UNVERIFIED", 1, age(primary))
                }
            }

            return LiveResolution(primary.feed, lastConfirmedLive, false, LiveStatus.DRAW_FREEZE, "DRAW_FREEZE • FINALIZING", sourceCount, age(primary))
        }

        LiveWindowAction.LIVE_POLLING -> {
            if (!primaryValid && !secondaryValid) {
                return LiveResolution(primary?.feed ?: secondary?.feed, lastConfirmedLive, false, LiveStatus.STALE, "STALE • NO FRESH SOURCE", 0, maxOf(age(primary), age(secondary)))
            }
            if (primaryValid && secondaryValid) {
                if (!timeAligned(primary!!, secondary!!)) {
                    return LiveResolution(primary.feed, lastConfirmedLive, false, LiveStatus.WAITING_FOR_ALIGNMENT, "SYNCING • TIME ALIGNMENT", 2, maxOf(age(primary), age(secondary)))
                }
                if (primary.feed.live == secondary.feed.live) {
                    val hero = LiveHeroSnapshot(primary.feed.live, primary.feed.liveSet, primary.feed.liveVal, primary.feed.currentTime, primary.feed.date)
                    return LiveResolution(primary.feed, hero, true, LiveStatus.LIVE_CONFIRMED, "LIVE_CONFIRMED • CROSS-SOURCE MATCH", 2, 0)
                }
                return LiveResolution(primary.feed, lastConfirmedLive, false, LiveStatus.LIVE_CONFLICT, "LIVE_CONFLICT • DATA MISMATCH", 2, maxOf(age(primary), age(secondary)))
            }
            val single = if (primaryValid) primary!! else secondary!!
            val hero = LiveHeroSnapshot(single.feed.live, single.feed.liveSet, single.feed.liveVal, single.feed.currentTime, single.feed.date)
            val label = if (primaryValid) "LIVE_DEGRADED • LUKE" else "LIVE_DEGRADED • THAISTOCK2D"
            return LiveResolution(single.feed, hero, true, LiveStatus.LIVE_DEGRADED, label, 1, age(single))
        }

        LiveWindowAction.NONE, LiveWindowAction.REFERENCE_ONLY -> {
            val feed = primary?.feed ?: secondary?.feed
            val final = feed?.let(::latestFinal)
            if (final != null && currentDay(feed)) return LiveResolution(feed, final, false, LiveStatus.FINAL_CONFIRMED, "FINAL_CONFIRMED", sourceCount, 0)
            return LiveResolution(feed, cachedConfirmedFinal?.takeIf { canonicalDate(it.date) == currentYangonDate() }, false, LiveStatus.WAITING, "WAITING", sourceCount, maxOf(age(primary), age(secondary)))
        }
    }
}

private fun age(observation: SourceObservation?): Long {
    if (observation == null) return Long.MAX_VALUE
    return maxOf(0L, System.nanoTime() / 1_000_000L - observation.fetchedAtElapsedMs)
}

internal class LiveCollector(
    private val scope: CoroutineScope,
    private val fetcher: suspend () -> LiveFeedData?,
    private val secondaryFetcher: (suspend () -> LiveFeedData?)? = null,
    private val clock: () -> LocalTime = { LocalTime.now(YANGON_ZONE) },
    cacheLoader: () -> LiveHeroSnapshot? = { null },
    private val cacheSaver: (LiveHeroSnapshot) -> Unit = {},
) {
    private val _state = MutableStateFlow<LiveUiState>(LiveUiState.Loading)
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private var cycleJob: Job? = null
    private var lastReferenceFetchMs = 0L
    private var primaryObservation: SourceObservation? = null
    private var secondaryObservation: SourceObservation? = null
    private var lastConfirmedLive: LiveHeroSnapshot? = null
    private var lastConfirmedFinal: LiveHeroSnapshot? = null

    init {
        val cached = cacheLoader()?.takeIf { canonicalDate(it.date) == currentYangonDate() }
        if (cached != null) {
            lastConfirmedFinal = cached
            _state.value = LiveUiState.Data(null, cached, false, false, status = LiveStatus.FINAL_CONFIRMED, sourceMessage = "FINAL_CONFIRMED • CACHED")
        }
    }

    fun fetchCycle() {
        if (cycleJob?.isActive == true) return
        cycleJob = scope.launch {
            val primaryJob = launchSource(fetcher, true)
            val secondaryJob = secondaryFetcher?.let { launchSource(it, false) }
            withTimeoutOrNull(CYCLE_DEADLINE_MS) {
                while (primaryJob.isActive || secondaryJob?.isActive == true) delay(10)
            }
            if (primaryJob.isActive) primaryJob.cancel()
            secondaryJob?.cancel()
            publishCycle()
        }
    }

    private fun launchSource(source: suspend () -> LiveFeedData?, primary: Boolean): Job = scope.launch {
        val started = System.nanoTime() / 1_000_000L
        val feed = try { source() } catch (_: Exception) { null }
        if (feed != null) {
            val finished = System.nanoTime() / 1_000_000L
            val obs = SourceObservation(feed, finished, started, finished - started)
            if (primary) primaryObservation = obs else secondaryObservation = obs
        }
    }

    private fun publishCycle() {
        val resolved = resolveLiveState(primaryObservation, secondaryObservation, Instant.now(), lastConfirmedLive, lastConfirmedFinal)
        if (resolved.status == LiveStatus.LIVE_CONFIRMED && resolved.hero != null) lastConfirmedLive = resolved.hero
        if (resolved.status == LiveStatus.FINAL_CONFIRMED && resolved.hero != null && resolved.hero != lastConfirmedFinal) {
            lastConfirmedFinal = resolved.hero
            cacheSaver(resolved.hero)
        }
        val any = primaryObservation != null || secondaryObservation != null
        _state.value = LiveUiState.Data(
            feed = resolved.displayFeed,
            hero = resolved.hero,
            heroLive = resolved.heroLive,
            stale = !any || resolved.status == LiveStatus.STALE || resolved.status == LiveStatus.STALE_PRIMARY,
            secondaryFeed = secondaryObservation?.feed,
            sourceMessage = resolved.message,
            status = resolved.status,
            staleAgeMs = resolved.staleAgeMs,
        )
    }

    fun start() {
        scope.launch {
            while (isActive) {
                val now = clock()
                when (liveWindowAction(now)) {
                    LiveWindowAction.LIVE_POLLING, LiveWindowAction.FINALIZING -> fetchCycle()
                    LiveWindowAction.REFERENCE_ONLY -> {
                        val wall = System.currentTimeMillis()
                        if (wall - lastReferenceFetchMs >= LIVE_REFERENCE_FETCH_INTERVAL_MS) {
                            lastReferenceFetchMs = wall
                            fetchCycle()
                        }
                    }
                    LiveWindowAction.NONE -> Unit
                }
                delay(if (liveWindowAction(now) == LiveWindowAction.FINALIZING) CLOSING_POLL_INTERVAL_MS else NORMAL_POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        @Volatile private var shared: LiveCollector? = null
        val instance: LiveCollector get() = shared ?: error("LiveCollector not started")

        fun startOnce(context: Context) {
            if (shared != null) return
            synchronized(this) {
                if (shared != null) return
                val collector = LiveCollector(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                    fetcher = { withContext(Dispatchers.IO) { LiveApi.fetch() } },
                    secondaryFetcher = { withContext(Dispatchers.IO) { LiveApi.fetchThaiStock() } },
                    cacheLoader = { LiveCacheStore.load(context) },
                    cacheSaver = { LiveCacheStore.save(context, it) },
                )
                shared = collector
                collector.start()
                collector.fetchCycle()
            }
        }
    }
}

internal object LiveCacheStore {
    private const val PREFS_NAME = "live_display_cache"
    private const val KEY_LATEST_FINAL = "latest_final"

    fun load(context: Context): LiveHeroSnapshot? = try {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LATEST_FINAL, null) ?: return null
        val obj = JSONObject(raw)
        if (obj.optString("state") != "FINAL_CONFIRMED") return null
        LiveHeroSnapshot(obj.getString("result"), obj.getString("set"), obj.getString("value"), obj.getString("sessionLabel"), obj.getString("date"))
    } catch (_: Exception) { null }

    fun save(context: Context, snapshot: LiveHeroSnapshot) {
        runCatching {
            val obj = JSONObject()
                .put("result", snapshot.result)
                .put("set", snapshot.set)
                .put("value", snapshot.value)
                .put("sessionLabel", snapshot.sessionLabel)
                .put("date", snapshot.date)
                .put("state", "FINAL_CONFIRMED")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LATEST_FINAL, obj.toString()).apply()
        }
    }
}
