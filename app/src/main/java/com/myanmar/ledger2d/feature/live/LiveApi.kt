package com.myanmar.ledger2d.feature.live

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Live API endpoint used by the app-scoped collector. */
private const val LIVE_ENDPOINT = "https://luke.2dboss.com/api/luke/twod-result-live"

/** Bounded timeouts allow slow responses while ensuring a hung request eventually returns. */
internal const val LIVE_CONNECT_TIMEOUT_MS = 5_000
internal const val LIVE_READ_TIMEOUT_MS = 8_000

/** Best-effort diagnostics only; logger failure must never affect LIVE behavior. */
internal object LiveDiagnostics {
    private const val TAG = "Ledger2D.LIVE"

    fun info(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    fun warn(message: String, error: Throwable? = null) {
        runCatching {
            if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
        }
    }
}

/** Sentinel for a field that has not become available yet. */
private const val PENDING = "--"

// ============================================================================
// Luke API client (single-source runtime path)
// ============================================================================

internal object LiveApi {
    /** Returns the parsed feed, or null on any network/parse failure (never throws). */
    fun fetch(): LiveFeedData? {
        val startedAtNanos = System.nanoTime()
        val startedAtWallMs = System.currentTimeMillis()
        return try {
            val connection = URL(LIVE_ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = LIVE_CONNECT_TIMEOUT_MS
                connection.readTimeout = LIVE_READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                if (status !in 200..299) {
                    LiveDiagnostics.warn(
                        "Luke HTTP failure status=$status startedAtMs=$startedAtWallMs " +
                            "finishedAtMs=${System.currentTimeMillis()} latencyMs=${elapsedMs(startedAtNanos)}",
                    )
                    return null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(body).optJSONObject("data")
                if (data == null) {
                    LiveDiagnostics.warn(
                        "Luke response missing data object startedAtMs=$startedAtWallMs " +
                            "finishedAtMs=${System.currentTimeMillis()} latencyMs=${elapsedMs(startedAtNanos)}",
                    )
                    return null
                }
                val morningResult = data.nonBlankString("result_1200")
                val eveningResult = data.nonBlankString("result_430")
                val feed = LiveFeedData(
                    date = data.nonBlankString("date", fallback = ""),
                    currentTime = data.nonBlankString("current_time", fallback = ""),
                    live = data.nonBlankString("live"),
                    liveSet = data.nonBlankString("live_set"),
                    liveVal = data.nonBlankString("live_val"),
                    morning = LiveSessionData(
                        result = morningResult,
                        set = data.nonBlankString("set_1200"),
                        value = data.nonBlankString("val_1200"),
                        finalized = isValidLive2d(morningResult),
                    ),
                    evening = LiveSessionData(
                        result = eveningResult,
                        set = data.nonBlankString("set_430"),
                        value = data.nonBlankString("val_430"),
                        finalized = isValidLive2d(eveningResult),
                    ),
                    modern930 = data.nonBlankString("modern_930"),
                    internet930 = data.nonBlankString("internet_930"),
                    modern200 = data.nonBlankString("modern_200"),
                    internet200 = data.nonBlankString("internet_200"),
                    sourceTag = "LUKE",
                    serverTimeEpochMs = parseServerTimeEpoch(data.nonBlankString("current_time")),
                    isCloseDay = parseCloseDayFlag(data.opt("is_close_day")),
                )
                LiveDiagnostics.info(
                    "Luke response status=$status startedAtMs=$startedAtWallMs " +
                        "finishedAtMs=${System.currentTimeMillis()} latencyMs=${elapsedMs(startedAtNanos)} " +
                        "date=${feed.date} current_time=${feed.currentTime} live=${feed.live} " +
                        "result_1200=${feed.morning.result} result_430=${feed.evening.result} " +
                        "modern_930=${feed.modern930} internet_930=${feed.internet930} " +
                        "modern_200=${feed.modern200} internet_200=${feed.internet200} closeDay=${feed.isCloseDay}",
                )
                feed
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            LiveDiagnostics.warn(
                "Luke request failure type=${error.javaClass.simpleName} startedAtMs=$startedAtWallMs " +
                    "finishedAtMs=${System.currentTimeMillis()} latencyMs=${elapsedMs(startedAtNanos)}",
                error,
            )
            null
        }
    }

    private fun elapsedMs(startedAtNanos: Long): Long =
        ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)

    private fun parseServerTimeEpoch(raw: String): Long? = runCatching { java.time.LocalDateTime.parse(raw.replace(' ', 'T')).atZone(java.time.ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli() }.getOrNull()

    internal fun parseCloseDayFlag(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.trim().lowercase(java.util.Locale.ROOT) in setOf("1", "true", "yes", "y", "on")
        else -> false
    }

    /** Reads a string field, normalizing JSON null / blank / "null" to "--" (or a fallback). */
    private fun JSONObject.nonBlankString(name: String, fallback: String = PENDING): String {
        if (!has(name) || isNull(name)) return fallback
        val value = optString(name, "").trim()
        return if (value.isEmpty() || value == "null") fallback else value
    }
}
