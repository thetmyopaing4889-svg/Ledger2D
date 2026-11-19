package com.myanmar.ledger2d.feature.live

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Independent, bounded diagnostic data. NEVER Room/History/WinningNumber:
 * a SET cutoff observation remains UI-only even if it looks like a final.
 */
internal object SetMorningAuditStore {
    private const val PREFERENCES = "set_ui_display_only_v1"
    private const val KEY_RESULT = "selected_ui_result"
    private const val LOG_FILE = "set_ui_audit.log"
    private const val MAX_BYTES = 128 * 1024
    private const val RETAIN_BYTES = 64 * 1024
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private val lock = Any()
    private var lastSnapshotKey: String? = null
    private var lastComparisonKey: String? = null

    /** Every attempt is retained, even when HTTP, parsing or selection fails. */
    fun recordRequestStart(context: Context, date: LocalDate) = synchronized(lock) {
        appendRaw(context, listOf(Instant.now(), "REQUEST_START", date, "SET_HTML_GET").joinToString(" | "))
    }

    fun recordRequestEnd(
        context: Context,
        date: LocalDate,
        result: SetProbeOutcome,
        selection: SetMorningSelection? = null,
    ) = synchronized(lock) {
        val e = when (result) {
            is SetProbeOutcome.Success -> result.evidence
            is SetProbeOutcome.Failure -> result.evidence
        }
        val status = when (result) {
            is SetProbeOutcome.Success -> result.httpStatus.toString()
            is SetProbeOutcome.Failure -> result.httpStatus?.toString() ?: "NONE"
        }
        val duration = when (result) {
            is SetProbeOutcome.Success -> result.observation.roundTripMs
            is SetProbeOutcome.Failure -> result.roundTripMs
        }
        val outcome = when (result) {
            is SetProbeOutcome.Success -> "PARSE_OK"
            is SetProbeOutcome.Failure -> "FAIL_" + result.reason
        }
        val source = when (result) {
            is SetProbeOutcome.Success -> result.observation.snapshot.sourceUpdateYangon.toString()
            is SetProbeOutcome.Failure -> "NONE"
        }
        val marketStatus = when (result) {
            is SetProbeOutcome.Success -> result.observation.snapshot.marketStatus ?: "NONE"
            is SetProbeOutcome.Failure -> "NONE"
        }
        val detail = listOf(
            "status=" + status,
            "durationMs=" + duration,
            "bytes=" + e.responseBytes,
            "source=" + source,
            "market=" + marketStatus,
            "phase=" + (selection?.phase?.name ?: "NONE"),
            "dateHeader=" + (e.serverDate ?: "NONE"),
            "age=" + (e.age ?: "NONE"),
            "cache=" + (e.cacheControl ?: "NONE"),
            "type=" + (e.contentType ?: "NONE"),
            "retryAfter=" + (e.retryAfter ?: "NONE"),
            "stage=" + (e.parserStage ?: "NONE"),
            "outcome=" + outcome,
        ).joinToString(" | ")
        appendRaw(context, listOf(e.startedAt, "REQUEST_END", date, detail).joinToString(" | "))
    }

    fun recordCoordinatorEvent(context: Context, date: LocalDate, event: String) = synchronized(lock) {
        appendRaw(
            context,
            listOf(Instant.now(), "COORDINATOR", date, event).joinToString(" | "),
        )
    }

    private fun appendRaw(context: Context, line: String) {
        runCatching {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists() && file.length() > MAX_BYTES) {
                val kept = file.readText().takeLast(RETAIN_BYTES).substringAfter('\n', "")
                file.writeText(kept)
            }
            val safe = line.replace(Regex("[\r\n]"), " ").take(1600)
            file.appendText(safe + "\n")
        }
    }

    fun recordObservation(
        context: Context,
        date: LocalDate,
        observation: SetFetchObservation,
        selection: SetMorningSelection,
    ) = synchronized(lock) {
        val snapshot = observation.snapshot
        val key = listOf(
            date.toString(), snapshot.sourceUpdateYangon.toInstant().toString(),
            snapshot.index, snapshot.tradingValueMbaht,
        ).joinToString("|")
        if (key != lastSnapshotKey) {
            lastSnapshotKey = key
            append(
                context, "SNAPSHOT", date, snapshot,
                "phase=" + selection.phase + ",durationMs=" + observation.roundTripMs +
                    ",market=" + (snapshot.marketStatus ?: "NONE"),
            )
        }
        if (selection.phase == SetMorningSelectionPhase.UI_RESULT_READY) {
            saveResult(context, date, selection.snapshot)
        } else if (selection.phase == SetMorningSelectionPhase.CONFLICT) {
            append(context, "CONFLICT", date, snapshot, "ambiguous source update")
        }
    }

    private fun saveResult(context: Context, date: LocalDate, snapshot: SetMarketSnapshot?) {
        if (snapshot == null || snapshot.sourceUpdateYangon.toLocalDate() != date) return
        val time = snapshot.sourceUpdateYangon.toLocalTime()
        if (time.isBefore(LocalTime.NOON) || time.isAfter(LocalTime.of(12, 1))) return
        val json = JSONObject()
            .put("date", date.toString())
            .put("index", snapshot.index)
            .put("value", snapshot.tradingValueMbaht)
            .put("sourceEpochMs", snapshot.sourceUpdateBangkok.toInstant().toEpochMilli())
            .put("status", snapshot.marketStatus ?: "")
            .put("result", snapshot.computed2d)
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val encoded = json.toString()
        if (prefs.getString(KEY_RESULT, null) == encoded) return
        if (prefs.edit().putString(KEY_RESULT, encoded).commit()) {
            append(context, "UI_RESULT", date, snapshot, "display_only")
        }
    }

    fun restoreUiResult(context: Context, date: LocalDate): SetMorningUiState? = synchronized(lock) {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_RESULT, null) ?: return@synchronized null
        runCatching {
            val json = JSONObject(raw)
            require(json.getString("date") == date.toString())
            val source = Instant.ofEpochMilli(json.getLong("sourceEpochMs")).atZone(bangkok)
            val local = source.withZoneSameInstant(YANGON)
            require(local.toLocalDate() == date)
            require(!local.toLocalTime().isBefore(LocalTime.NOON))
            require(!local.toLocalTime().isAfter(LocalTime.of(12, 1)))
            val digit = json.getString("result")
            require(isValidLive2d(digit))
            val snapshot = SetMarketSnapshot(
                index = json.getString("index"),
                tradingValueMbaht = json.getString("value"),
                sourceUpdateBangkok = source,
                marketStatus = json.optString("status"),
                computed2d = digit,
            )
            SetMorningUiState(
                date, SetMorningSelection(SetMorningSelectionPhase.UI_RESULT_READY, snapshot),
                Instant.now(),
            )
        }.getOrNull()
    }

    fun recordLukeFinal(context: Context, date: LocalDate, lukeResult: String) = synchronized(lock) {
        if (!isValidLive2d(lukeResult)) return@synchronized
        val selected = restoreUiResult(context, date)?.selection?.snapshot ?: return@synchronized
        val key = date.toString() + "|" + selected.computed2d + "|" + lukeResult
        if (key == lastComparisonKey) return@synchronized
        lastComparisonKey = key
        append(
            context, "LUKE_COMPARE", date, selected,
            "luke=" + lukeResult + ",match=" + (selected.computed2d == lukeResult),
        )
    }

    fun recentLog(context: Context, maxChars: Int = 24000): String = synchronized(lock) {
        val content = runCatching {
            File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText().orEmpty()
        }.getOrDefault("")
        val today = LocalDate.now(YANGON).toString()
        val daily = content.lineSequence().filter { it.contains(" | " + today + " | ") }
            .joinToString("\n")
        val selected = daily.ifEmpty { content }
        when {
            selected.isEmpty() -> "No SET observations recorded yet"
            selected.length <= maxChars -> selected
            else -> selected.take(maxChars / 2) +
                "\n... middle entries omitted in preview ...\n" +
                selected.takeLast(maxChars / 2)
        }
    }

    private fun append(
        context: Context,
        kind: String,
        date: LocalDate,
        snapshot: SetMarketSnapshot,
        note: String,
    ) {
        runCatching {
            val file = File(context.filesDir, LOG_FILE)
            if (file.exists() && file.length() > MAX_BYTES) {
                val retained = file.readText().takeLast(RETAIN_BYTES).substringAfter('\n', "")
                file.writeText(retained)
            }
            fun clean(s: String) = s.replace(Regex("[\r\n|]"), " ").take(120)
            val values = listOf(
                Instant.now().toString(), kind, date.toString(),
                snapshot.sourceUpdateYangon.toString(),
                clean(snapshot.index), clean(snapshot.tradingValueMbaht),
                snapshot.computed2d, clean(note),
            )
            file.appendText(values.joinToString(" | ") + "\n")
        }
    }
}
