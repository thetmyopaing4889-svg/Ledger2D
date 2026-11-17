package com.myanmar.ledger2d.core.repository

import android.content.Context
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable, draw-keyed locked evidence. Only writes while a target remains
 * strictly before its Yangon cutoff. Not a retrospective backfill mechanism.
 * Existing operational winner/history tables are never modified.
 */
internal class CherrySuccessSnapshotStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cherry_success_pre_result_snapshots_v1", Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): List<CherrySuccessSnapshot> = synchronized(lock) {
        val raw = prefs.getString("draws", null) ?: return@synchronized emptyList()
        runCatching {
            val array = JSONArray(raw)
            require(array.length() <= 420)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val target = DrawIdentity(LocalDate.parse(item.getString("date")),
                    DrawSession.valueOf(item.getString("session")))
                val rulesArray = item.getJSONArray("rules")
                require(rulesArray.length() <= 100)
                val rules = (0 until rulesArray.length()).map { n ->
                    val rule = rulesArray.getJSONObject(n)
                    val predictions = rule.getJSONArray("predictions")
                    CherrySuccessRule(rule.getString("id"), rule.getString("name"),
                        CherryTab.valueOf(rule.getString("tab")),
                        CherrySuccessCriterion.valueOf(rule.getString("criterion")),
                        (0 until predictions.length()).map { predictions.getString(it) })
                }
                CherrySuccessSnapshot(target, item.getLong("time"), rules)
            }.distinctBy { it.target }
        }.getOrElse {
            // Do not overwrite a damaged evidence archive. The caller may
            // display an empty state, but attempts to save will fail closed.
            throw IllegalStateException("Corrupt Cherry success snapshots", it)
        }
    }

    /** Preserve first timestamp if nothing changed; update only before close. */
    fun recordBeforeCutoff(candidate: CherrySuccessSnapshot): List<CherrySuccessSnapshot> =
        synchronized(lock) {
            if (!CherrySuccessEngine.beforeCutoff(candidate.target, candidate.capturedAtMillis)) {
                return@synchronized load()
            }
            val existing = load()
            val previous = existing.firstOrNull { it.target == candidate.target }
            if (previous != null && previous.rules == candidate.rules) return@synchronized existing
            if (previous != null && candidate.capturedAtMillis < previous.capturedAtMillis) {
                return@synchronized existing
            }
            val updated = (existing.filterNot { it.target == candidate.target } + candidate)
                .sortedWith(compareByDescending<CherrySuccessSnapshot> { it.target.date }
                    .thenByDescending { it.target.session.ordinal })
                .take(400)
            val array = JSONArray()
            updated.forEach { snap ->
                val rules = JSONArray()
                snap.rules.forEach { rule ->
                    rules.put(JSONObject().put("id", rule.id).put("name", rule.name)
                        .put("tab", rule.tab.name).put("criterion", rule.criterion.name)
                        .put("predictions", JSONArray(rule.predictions)))
                }
                array.put(JSONObject().put("date", snap.target.date.toString())
                    .put("session", snap.target.session.name)
                    .put("time", snap.capturedAtMillis).put("rules", rules))
            }
            check(prefs.edit().putString("draws", array.toString()).commit()) {
                "Could not persist Cherry success snapshot"
            }
            updated
        }
}
