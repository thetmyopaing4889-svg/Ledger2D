package com.myanmar.ledger2d.core.repository

import android.content.Context
import com.myanmar.ledger2d.core.domain.CherryDiscoveredFormula
import com.myanmar.ledger2d.core.domain.CherryDiscoveryCheckpoint
import com.myanmar.ledger2d.core.domain.CherryDiscoveryCheckpoints
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryDiscoverySearchCursor
import com.myanmar.ledger2d.core.domain.CherryFormulaDiscoveryEngine
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Engine-only, local search progress. Stores one current session per category,
 * not the whole leaderboard nor the result archive. Search continuation can
 * survive process restart by re-evaluating at most four bounded grammar stages.
 *
 * This store does NOT persist time-locked prospective predictions. They live in
 * CherryProspectiveTrialStore and are never deleted when a new search starts.
 * Explicitly call persist() off Main after each user-triggered search response.
 */
class CherryDiscoveryCursorStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cherry_discovery_cursors_v1", Context.MODE_PRIVATE)

    companion object {
        private val processLock = Any()
        private const val VERSION = 2
        private fun key(kind: CherryDiscoveryKind): String =
            "cursor_" + kind.name
    }

    fun persist(cursor: CherryDiscoverySearchCursor) = synchronized(processLock) {
        val next = CherryDiscoveryCheckpoints.capture(cursor)
        val previous = decodeIfPresent(next.kind)
        if (previous != null && previous.target == next.target &&
            previous.historySignature == next.historySignature) {
            require(next.nextPosition >= previous.nextPosition &&
                next.completedStages >= previous.completedStages &&
                next.offeredIds.containsAll(previous.offeredIds) &&
                previous.offeredPositions.all { (id, pos) ->
                    next.offeredPositions[id] == pos
                } &&
                previous.offeredBehaviors.all { (id, key) ->
                    next.offeredBehaviors[id] == key
                } &&
                (!previous.exhausted || next.exhausted)) {
                "A search checkpoint cannot erase previously offered formulas"
            }
            val oldBest = previous.bestCandidate
            require(oldBest == null || next.bestCandidate != null &&
                next.bestCandidate.historicalStreak >= oldBest.historicalStreak) {
                "A stronger historical fit must not be discarded"
            }
        }
        check(prefs.edit().putString(key(next.kind), encode(next)).commit()) {
            "Could not persist discovery progress"
        }
    }

    /**
     * Old targets and corrected histories safely start a fresh search.
     * Malformed JSON fails loudly instead of silently removing progress.
     */
    fun resume(
        kind: CherryDiscoveryKind,
        target: DrawIdentity,
        history: List<HistoricDraw>,
        closedDates: Set<LocalDate> = emptySet(),
        checkCancellation: () -> Unit = {},
    ): CherryDiscoverySearchCursor? {
        val snapshot = synchronized(processLock) { decodeIfPresent(kind) }
            ?: return null
        if (snapshot.target != target) return null
        return CherryDiscoveryCheckpoints.restore(
            snapshot, history, closedDates, checkCancellation)
    }

    private fun decodeIfPresent(kind: CherryDiscoveryKind): CherryDiscoveryCheckpoint? {
        val raw = prefs.getString(key(kind), null) ?: return null
        return try {
            decode(raw).also { require(it.kind == kind) }
        } catch (error: Exception) {
            throw IllegalStateException(
                "Corrupt discovery cursor: refusing to silently erase progress", error)
        }
    }

    private fun draw(value: DrawIdentity): JSONObject = JSONObject()
        .put("date", value.date.toString())
        .put("session", value.session.name)

    private fun readDraw(obj: JSONObject): DrawIdentity = DrawIdentity(
        LocalDate.parse(obj.getString("date")),
        DrawSession.valueOf(obj.getString("session")))

    private fun formula(value: CherryDiscoveredFormula?): Any {
        if (value == null) return JSONObject.NULL
        val matched = JSONArray()
        value.matchedDraws.forEach { matched.put(draw(it)) }
        return JSONObject()
            .put("kind", value.kind.name)
            .put("id", value.id)
            .put("expression", value.expression)
            .put("offset", value.offset)
            .put("currentDigit", value.currentDigit)
            .put("historicalStreak", value.historicalStreak)
            .put("matchedDraws", matched)
            .put("discoveredAtStage", value.discoveredAtStage)
            .put("evidenceLabel", value.evidenceLabel)
    }

    private fun readFormula(obj: JSONObject?): CherryDiscoveredFormula? {
        if (obj == null) return null
        val matched = obj.getJSONArray("matchedDraws")
        return CherryDiscoveredFormula(
            kind = CherryDiscoveryKind.valueOf(obj.getString("kind")),
            id = obj.getString("id"),
            expression = obj.getString("expression"),
            offset = obj.getInt("offset"),
            currentDigit = obj.getInt("currentDigit"),
            historicalStreak = obj.getInt("historicalStreak"),
            matchedDraws = (0 until matched.length())
                .map { readDraw(matched.getJSONObject(it)) },
            discoveredAtStage = obj.getInt("discoveredAtStage"),
            evidenceLabel = obj.getString("evidenceLabel"))
    }

    private fun encode(value: CherryDiscoveryCheckpoint): String {
        val seen = JSONArray()
        value.offeredIds.sorted().forEach { id ->
            seen.put(JSONObject()
                .put("id", id)
                .put("position", value.offeredPositions.getValue(id))
                .put("behavior", value.offeredBehaviors.getValue(id)))
        }
        return JSONObject()
            .put("version", VERSION)
            .put("target", draw(value.target))
            .put("kind", value.kind.name)
            .put("historySignature", value.historySignature)
            .put("completedStages", value.completedStages)
            .put("nextPosition", value.nextPosition)
            .put("offeredIds", seen)
            .put("newCandidate", formula(value.newCandidate))
            .put("bestCandidate", formula(value.bestCandidate))
            .put("exhausted", value.exhausted)
            .toString()
    }

    private fun decode(raw: String): CherryDiscoveryCheckpoint {
        val obj = JSONObject(raw)
        require(obj.getInt("version") == VERSION)
        val offered = obj.getJSONArray("offeredIds")
        val offers = (0 until offered.length())
            .map { offered.getJSONObject(it) }
        val ids = offers.map { it.getString("id") }
        val positions = offers.associate { it.getString("id") to
            it.getInt("position") }
        val behaviors = offers.associate { it.getString("id") to
            it.getString("behavior") }
        require(ids.size == ids.toSet().size) {
            "Duplicate IDs in stored discovery checkpoint"
        }
        val snapshot = CherryDiscoveryCheckpoint(
            target = readDraw(obj.getJSONObject("target")),
            kind = CherryDiscoveryKind.valueOf(obj.getString("kind")),
            historySignature = obj.getString("historySignature"),
            completedStages = obj.getInt("completedStages"),
            nextPosition = obj.getInt("nextPosition"),
            offeredIds = ids.toSet(),
            newCandidate = readFormula(obj.optJSONObject("newCandidate")),
            bestCandidate = readFormula(obj.optJSONObject("bestCandidate")),
            exhausted = obj.getBoolean("exhausted"),
            offeredPositions = positions,
            offeredBehaviors = behaviors,
        )
        CherryDiscoveryCheckpoints.validate(snapshot)
        return snapshot
    }
}
