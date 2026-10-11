package com.myanmar.ledger2d.feature.live

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Live API endpoint used by the app-scoped collector. */
private const val LIVE_ENDPOINT = "https://luke.2dboss.com/api/luke/twod-result-live"

/** HTTP timeouts in ms; kept identical to the baseline. */
private const val LIVE_CONNECT_TIMEOUT_MS = 2_500
private const val LIVE_READ_TIMEOUT_MS = 3_000

/** Sentinel for a field that has not become available yet. */
private const val PENDING = "--"

// ============================================================================
// Luke API client (single-source runtime path)
// ============================================================================

internal object LiveApi {
    /** Returns the parsed feed, or null on any network/parse failure (never throws). */
    fun fetch(): LiveFeedData? {
        return try {
            val connection = URL(LIVE_ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = LIVE_CONNECT_TIMEOUT_MS
                connection.readTimeout = LIVE_READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(body).optJSONObject("data") ?: return null
                LiveFeedData(
                    date = data.nonBlankString("date", fallback = ""),
                    currentTime = data.nonBlankString("current_time", fallback = ""),
                    live = data.nonBlankString("live"),
                    liveSet = data.nonBlankString("live_set"),
                    liveVal = data.nonBlankString("live_val"),
                    morning = LiveSessionData(
                        result = data.nonBlankString("result_1200"),
                        set = data.nonBlankString("set_1200"),
                        value = data.nonBlankString("val_1200"),
                        finalized = data.nonBlankString("result_1200") != PENDING,
                    ),
                    evening = LiveSessionData(
                        result = data.nonBlankString("result_430"),
                        set = data.nonBlankString("set_430"),
                        value = data.nonBlankString("val_430"),
                        finalized = data.nonBlankString("result_430") != PENDING,
                    ),
                    modern930 = data.nonBlankString("modern_930"),
                    internet930 = data.nonBlankString("internet_930"),
                    modern200 = data.nonBlankString("modern_200"),
                    internet200 = data.nonBlankString("internet_200"),
                    sourceTag = "LUKE",
                    serverTimeEpochMs = parseServerTimeEpoch(data.nonBlankString("current_time")),
                    isCloseDay = data.optInt("is_close_day", 0) == 1,
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseServerTimeEpoch(raw: String): Long? = runCatching { java.time.LocalDateTime.parse(raw.replace(' ', 'T')).atZone(java.time.ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli() }.getOrNull()

    /** Reads a string field, normalizing JSON null / blank / "null" to "--" (or a fallback). */
    private fun JSONObject.nonBlankString(name: String, fallback: String = PENDING): String {
        if (!has(name) || isNull(name)) return fallback
        val value = optString(name, "").trim()
        return if (value.isEmpty() || value == "null") fallback else value
    }
}
