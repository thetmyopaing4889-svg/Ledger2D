package com.myanmar.ledger2d.feature.live

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

internal object LiveClosedDayStore {
    private const val PREFS_NAME = "live_display_cache"
    private const val KEY = "observed_closed_day"

    fun load(context: Context): LocalDate? = runCatching {
        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return null
        LocalDate.parse(raw)
    }.getOrNull()

    fun save(context: Context, date: LocalDate) {
        runCatching {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, date.toString())
                .apply()
        }
    }

    fun clear(context: Context) {
        runCatching {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply()
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

            val today = currentYangonDate()
            val cycleDate = dailyCycleDate(today, LocalTime.now(YANGON))
            val previousWorking = previousWorkingDay(cycleDate)
            snapshot.takeIf {
                val d = canonicalDate(it.date)
                d == cycleDate || d == previousWorking
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
                isCloseDay = o.optBoolean("isCloseDay", false),
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
                .put("isCloseDay", feed.isCloseDay)
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
