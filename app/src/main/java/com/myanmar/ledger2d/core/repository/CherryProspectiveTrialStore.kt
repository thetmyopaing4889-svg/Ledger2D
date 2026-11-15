package com.myanmar.ledger2d.core.repository

import android.content.Context
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryProspectiveChoice
import com.myanmar.ledger2d.core.domain.CherryProspectiveEvaluation
import com.myanmar.ledger2d.core.domain.CherryProspectiveLockedTrial
import com.myanmar.ledger2d.core.domain.CherryProspectiveOutcome
import com.myanmar.ledger2d.core.domain.CherryProspectiveTrialJournal
import com.myanmar.ledger2d.core.domain.HistoricSource
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, single-process persistence for the experimental prospective trial.
 *
 * A save is a synchronous atomic SharedPreferences replacement, performed by
 * the future caller on Dispatchers.IO (never from Compose/Main). Before saving,
 * all previous locks and evaluations must remain byte-for-byte equivalent at
 * the model level; corrections cannot rewrite an already recorded prediction.
 *
 * Limit: this is local storage, NOT a tamper-proof or independently timestamped
 * journal. The device owner can erase app data or alter the system clock.
 * Do not advertise a verified real-time prediction history from this alone.
 *
 * Not connected to the UI or live winner ingestion.
 */
class CherryProspectiveTrialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cherry_prospective_trial_v1", Context.MODE_PRIVATE)

    companion object {
        // Serialize read/compare/write even when callers create two store
        // objects. This remains single-process protection, not notarization.
        private val processLock = Any()
    }

    fun load(): CherryProspectiveTrialJournal = synchronized(processLock) {
        val saved = prefs.getString("snapshot", null)
            ?: return@synchronized CherryProspectiveTrialJournal.empty()
        try {
            CherryProspectiveTrialCodec.decode(saved)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Corrupt trial journal: refusing to silently erase pre-result locks", error)
        }
    }

    /**
     * Append-only at the logical journal level. Rejects replacing any existing
     * locked prediction, erasing a settled result, or rewriting a past hit/miss.
     */
    fun persist(next: CherryProspectiveTrialJournal) = synchronized(processLock) {
        val prior = load()
        val oldLocks = prior.lockedTrials()
        val oldResults = prior.verifiedResults()
        val newLocks = next.lockedTrials()
        val newResults = next.verifiedResults()
        require(newLocks.take(oldLocks.size) == oldLocks &&
            newResults.take(oldResults.size) == oldResults) {
            "Existing pre-result locks and settled outcomes are immutable"
        }
        // Restoring also verifies all outcomes against their frozen choices.
        val valid = CherryProspectiveTrialJournal.restore(newLocks, newResults)
        val data = CherryProspectiveTrialCodec.encode(valid)
        check(prefs.edit().putString("snapshot", data).commit()) {
            "Could not persist prospective trial journal"
        }
    }
}

/** Versioned, auditable local snapshot format; invalid snapshots fail closed. */
internal object CherryProspectiveTrialCodec {
    private const val VERSION = 1

    private fun target(value: DrawIdentity): JSONObject = JSONObject()
        .put("date", value.date.toString())
        .put("session", value.session.name)

    private fun readTarget(value: JSONObject) = DrawIdentity(
        LocalDate.parse(value.getString("date")),
        DrawSession.valueOf(value.getString("session")),
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.nullableInt(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (isNull(key)) null else getBoolean(key)

    fun encode(journal: CherryProspectiveTrialJournal): String {
        val locks = JSONArray()
        for (lock in journal.lockedTrials()) {
            val choices = JSONArray()
            for (choice in lock.choices) {
                choices.put(JSONObject()
                    .put("kind", choice.kind.name)
                    .put("formulaId", choice.formulaId ?: JSONObject.NULL)
                    .put("expression", choice.expression ?: JSONObject.NULL)
                    .put("offset", choice.offset ?: JSONObject.NULL)
                    .put("outputDigit", choice.outputDigit ?: JSONObject.NULL)
                    .put("historicalFitStreak", choice.historicalFitStreak ?: JSONObject.NULL)
                    .put("discoveredAtStage", choice.discoveredAtStage ?: JSONObject.NULL))
            }
            locks.put(JSONObject()
                .put("target", target(lock.target))
                .put("lockedAt", lock.lockedAt.toString())
                .put("cutoffAt", lock.cutoffAt.toString())
                .put("historyFingerprint", lock.historyFingerprint)
                .put("completedStages", lock.completedStages)
                .put("evidenceLabel", lock.evidenceLabel)
                .put("choices", choices))
        }
        val evaluated = JSONArray()
        for (result in journal.verifiedResults()) {
            val outcomes = JSONArray()
            for (outcome in result.outcomes) {
                outcomes.put(JSONObject()
                    .put("kind", outcome.kind.name)
                    .put("hit", outcome.hit ?: JSONObject.NULL))
            }
            evaluated.put(JSONObject()
                .put("target", target(result.target))
                .put("actualPair", result.actualPair)
                .put("resultSource", result.resultSource.name)
                .put("evaluatedAt", result.evaluatedAt.toString())
                .put("outcomes", outcomes))
        }
        return JSONObject()
            .put("version", VERSION)
            .put("locks", locks)
            .put("evaluations", evaluated)
            .toString()
    }

    fun decode(raw: String): CherryProspectiveTrialJournal {
        val root = JSONObject(raw)
        require(root.getInt("version") == VERSION) { "Unknown trial format" }
        val locks = root.getJSONArray("locks")
        val locked = (0 until locks.length()).map { index ->
            val item = locks.getJSONObject(index)
            val choices = item.getJSONArray("choices")
            CherryProspectiveLockedTrial(
                target = readTarget(item.getJSONObject("target")),
                lockedAt = Instant.parse(item.getString("lockedAt")),
                cutoffAt = Instant.parse(item.getString("cutoffAt")),
                historyFingerprint = item.getString("historyFingerprint"),
                completedStages = item.getInt("completedStages"),
                choices = (0 until choices.length()).map { n ->
                    val c = choices.getJSONObject(n)
                    CherryProspectiveChoice(
                        kind = CherryDiscoveryKind.valueOf(c.getString("kind")),
                        formulaId = c.nullableString("formulaId"),
                        expression = c.nullableString("expression"),
                        offset = c.nullableInt("offset"),
                        outputDigit = c.nullableInt("outputDigit"),
                        historicalFitStreak = c.nullableInt("historicalFitStreak"),
                        discoveredAtStage = c.nullableInt("discoveredAtStage"),
                    )
                },
                evidenceLabel = item.getString("evidenceLabel"),
            )
        }
        val evaluations = root.getJSONArray("evaluations")
        val settled = (0 until evaluations.length()).map { index ->
            val item = evaluations.getJSONObject(index)
            val outcomes = item.getJSONArray("outcomes")
            CherryProspectiveEvaluation(
                target = readTarget(item.getJSONObject("target")),
                actualPair = item.getString("actualPair"),
                resultSource = HistoricSource.valueOf(item.getString("resultSource")),
                evaluatedAt = Instant.parse(item.getString("evaluatedAt")),
                outcomes = (0 until outcomes.length()).map { n ->
                    val o = outcomes.getJSONObject(n)
                    CherryProspectiveOutcome(
                        CherryDiscoveryKind.valueOf(o.getString("kind")),
                        o.nullableBoolean("hit"),
                    )
                },
            )
        }
        return CherryProspectiveTrialJournal.restore(locked, settled)
    }
}
