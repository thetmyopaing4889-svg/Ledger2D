package com.myanmar.ledger2d.core.repository

import android.content.Context
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Each draw has its own durable, pre-result evidence entry. Unlike a bounded
 * discovery cursor, a confirmed draw archive must NOT silently evict older
 * rows. Writing one new choice never reserializes the entire archive.
 *
 * A pre-cutoff update replaces ONLY that draw's previous provisional choice.
 * No snapshot is backfilled after close and existing winner tables are read-only.
 */
internal class CherrySuccessSnapshotStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cherry_success_draw_archive_v1", Context.MODE_PRIVATE)

    companion object {
        private val processLock = Any()
        private const val PREFIX = "draw_"
        private fun key(target: DrawIdentity): String =
            PREFIX + target.date + "_" + target.session.name
    }

    private fun serialize(snap: CherrySuccessSnapshot): String {
        val rules = JSONArray()
        snap.rules.forEach { rule ->
            rules.put(JSONObject().put("id", rule.id).put("name", rule.name)
                .put("tab", rule.tab.name).put("criterion", rule.criterion.name)
                .put("predictions", JSONArray(rule.predictions))
                .put("origin",rule.sourceOrigin).put("detail",rule.detail)
                .put("eligibleSlots",JSONArray().apply {
                    rule.eligibleSlots.forEach { slot ->
                        put(JSONObject().put("date",slot.date.toString()).put("session",slot.session.name))
                    }
                }))
        }
        return JSONObject().put("date", snap.target.date.toString())
            .put("session", snap.target.session.name)
            .put("time", snap.capturedAtMillis).put("rules", rules)
            .put("version",snap.sourceVersion)
            .put("inputs",snap.inputEvidence).toString()
    }

    private fun deserialize(encoded: String): CherrySuccessSnapshot {
        val item = JSONObject(encoded)
        val target = DrawIdentity(LocalDate.parse(item.getString("date")),
            DrawSession.valueOf(item.getString("session")))
        val rows = item.getJSONArray("rules")
        require(rows.length() <= 1000)
        val rules = (0 until rows.length()).map { index ->
            val rule = rows.getJSONObject(index)
            val predictions = rule.getJSONArray("predictions")
            require(predictions.length() <= 100)
            CherrySuccessRule(rule.getString("id"), rule.getString("name"),
                CherryTab.valueOf(rule.getString("tab")),
                CherrySuccessCriterion.valueOf(rule.getString("criterion")),
                (0 until predictions.length()).map { predictions.getString(it) },
                rule.optString("origin", ""), rule.optString("detail", ""),
                rule.optJSONArray("eligibleSlots")?.let { slots ->
                    require(slots.length()<=366)
                    (0 until slots.length()).map { i ->
                        val slot=slots.getJSONObject(i)
                        DrawIdentity(LocalDate.parse(slot.getString("date")),
                            DrawSession.valueOf(slot.getString("session")))
                    }
                }.orEmpty())
        }
        return CherrySuccessSnapshot(target, item.getLong("time"), rules,
            item.optString("version","cherry-success-snapshot-v1"),
            item.optString("inputs",""))
    }

    fun load(): List<CherrySuccessSnapshot> = synchronized(processLock) {
        prefs.all.entries.filter { it.key.startsWith(PREFIX) }.map { (archiveKey, raw) ->
            val snap = runCatching { deserialize(raw as String) }.getOrElse {
                // Fail closed. NEVER overwrite unreadable pre-draw evidence
                // with fabricated or empty records.
                throw IllegalStateException("Invalid Cherry success archive entry", it)
            }
            require(archiveKey == key(snap.target)) { "Snapshot draw key mismatch" }
            snap
        }.sortedWith(compareByDescending<CherrySuccessSnapshot> { it.target.date }
            .thenByDescending { it.target.session.ordinal })
    }

    fun recordBeforeCutoff(candidate: CherrySuccessSnapshot): List<CherrySuccessSnapshot> =
        synchronized(processLock) {
            // Both event timestamp and actual commit-time wall-clock must be
            // before close. Protect against delayed coroutine writes.
            if (!CherrySuccessEngine.beforeCutoff(candidate.target,candidate.capturedAtMillis) ||
                !CherrySuccessEngine.beforeCutoff(candidate.target,System.currentTimeMillis())) {
                return@synchronized load()
            }
            val previous = prefs.getString(key(candidate.target), null)?.let {
                deserialize(it)
            }
            if (previous != null && previous.rules == candidate.rules &&
                previous.inputEvidence == candidate.inputEvidence &&
                previous.sourceVersion == candidate.sourceVersion) {
                return@synchronized load()
            }
            if (previous != null &&
                previous.capturedAtMillis > candidate.capturedAtMillis) {
                return@synchronized load()
            }
            check(prefs.edit().putString(key(candidate.target),serialize(candidate)).commit()) {
                "Could not persist Cherry success snapshot"
            }
            load()
        }
}
