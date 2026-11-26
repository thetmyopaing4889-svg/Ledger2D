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

    private val decoded=CherryArchiveDecodeCache<CherrySuccessSnapshot>()

    companion object {
        private val processLock = Any()
        private const val PREFIX = "draw_"
        private fun key(target: DrawIdentity): String =
            PREFIX + target.date + "_" + target.session.name
    }

    private fun encodeRules(input:List<CherrySuccessRule>):JSONArray {
        val rules=JSONArray()
        input.forEach { rule ->
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
        return rules
    }

    private fun serialize(snap: CherrySuccessSnapshot): String {
        val cuts=JSONArray()
        snap.cutRules.forEach { cut ->
            cuts.put(JSONObject().put("id",cut.id).put("name",cut.name)
                .put("excluded",JSONArray(cut.excluded))
                .put("originDate",cut.origin.date.toString())
                .put("originSession",cut.origin.session.name).put("detail",cut.detail))
        }
        return JSONObject().put("date", snap.target.date.toString())
            .put("session", snap.target.session.name)
            .put("time", snap.capturedAtMillis).put("rules", encodeRules(snap.rules))
            .put("rawRules",encodeRules(snap.rawRules)).put("cutRules",cuts)
            .put("version",snap.sourceVersion)
            .put("inputs",snap.inputEvidence).toString()
    }

    private fun decodeRules(rows:JSONArray):List<CherrySuccessRule> {
        require(rows.length() <= 1000)
        return (0 until rows.length()).map { index ->
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
    }

    private fun deserialize(encoded: String): CherrySuccessSnapshot {
        val item = JSONObject(encoded)
        val target = DrawIdentity(LocalDate.parse(item.getString("date")),
            DrawSession.valueOf(item.getString("session")))
        val cuts=item.optJSONArray("cutRules")?.let { rows ->
            require(rows.length()<=16)
            (0 until rows.length()).map { i ->
                val cut=rows.getJSONObject(i)
                val pairs=cut.getJSONArray("excluded")
                require(pairs.length()<=100)
                CherryCutEvidence(cut.getString("id"),cut.getString("name"),
                    (0 until pairs.length()).map{pairs.getString(it)},
                    DrawIdentity(LocalDate.parse(cut.getString("originDate")),
                        DrawSession.valueOf(cut.getString("originSession"))),
                    cut.getString("detail"))
            }
        }.orEmpty()
        return CherrySuccessSnapshot(target, item.getLong("time"), decodeRules(item.getJSONArray("rules")),
            item.optString("version","cherry-success-snapshot-v1"),
            item.optString("inputs",""),
            item.optJSONArray("rawRules")?.let(::decodeRules).orEmpty(),cuts)
    }

    fun load(): List<CherrySuccessSnapshot> = synchronized(processLock) {
        decoded.read(prefs.all.filterKeys{it.startsWith(PREFIX)}) { archiveKey, encoded ->
            val snap = runCatching { deserialize(encoded) }.getOrElse {
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
            // Validate the full persisted archive before any write, including
            // changes from another store instance / restore.
            val existing=load()
            val previous=existing.firstOrNull{it.target==candidate.target}
            if (previous != null && previous.rules == candidate.rules &&
                previous.inputEvidence == candidate.inputEvidence &&
                previous.sourceVersion == candidate.sourceVersion &&
                previous.rawRules == candidate.rawRules &&
                previous.cutRules == candidate.cutRules) {
                return@synchronized existing
            }
            if (previous != null &&
                previous.capturedAtMillis > candidate.capturedAtMillis) {
                return@synchronized existing
            }
            val encoded=serialize(candidate)
            // Recheck after validation/serialization, not only before it.
            if(!CherrySuccessEngine.beforeCutoff(candidate.target,System.currentTimeMillis()))
                return@synchronized existing
            check(prefs.edit().putString(key(candidate.target),encoded).commit()) {
                "Could not persist Cherry success snapshot"
            }
            load()
        }
}
