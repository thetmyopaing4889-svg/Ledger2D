package com.myanmar.ledger2d.feature.live

import android.content.Context
import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
import com.myanmar.ledger2d.core.repository.ClosedDayRepository
import com.myanmar.ledger2d.core.repository.HistorySync
import com.myanmar.ledger2d.core.repository.LiveDailyResultRepository
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class HistoricalCoverageStatus {
    COMPLETE,
    PARTIAL,
    MISSING,
    NOT_EXPECTED,
}

internal sealed interface LiveRoomKeeperOutcome {
    data object Complete : LiveRoomKeeperOutcome
    data class Pending(val date: LocalDate) : LiveRoomKeeperOutcome
}

/**
 * Durable pointer to the next historical date that still needs coverage.
 *
 * The value is deliberately kept outside Room so the existing Room schema and
 * LiveDailyResult table remain untouched by the Keeper feature.
 */
internal interface HistoricalCheckpointStore {
    fun get(): LocalDate?
    fun set(value: LocalDate)
}

internal class SharedPrefsHistoricalCheckpointStore(context: Context) : HistoricalCheckpointStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): LocalDate? =
        prefs.getString(KEY_NEXT_DATE, null)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    override fun set(value: LocalDate) {
        prefs.edit().putString(KEY_NEXT_DATE, value.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "live_room_keeper"
        const val KEY_NEXT_DATE = "next_historical_date"
    }
}

internal interface HistoricalBackfillSource {
    suspend fun fetch(date: LocalDate): HistoryResultEntity?
}

/** Primary source: existing GitHub 2D_history parser, filtered to one date. */
internal class GitHubHistoricalBackfillSource : HistoricalBackfillSource {
    override suspend fun fetch(date: LocalDate): HistoryResultEntity? =
        HistorySync.fetch2DHistory(date, date).firstOrNull()
}

/**
 * Backup source: ThaiStock2D's date-scoped 2D result endpoint.
 *
 * ThaiStock2D supplies SET/VALUE/2D final rows but not Modern/Internet, so its
 * output can complete a row only when those reference fields already exist in
 * Room (for example from the existing LIVE collector).
 */
internal class ThaiStockHistoricalBackfillSource : HistoricalBackfillSource {
    override suspend fun fetch(date: LocalDate): HistoryResultEntity? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val queryDate = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                val url = "https://api.thaistock2d.com/2d_result?date=$queryDate"
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    ThaiStockHistoricalBackfillParser.parse(body, date)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
}

internal object ThaiStockHistoricalBackfillParser {
    fun parse(body: String, expectedDate: LocalDate): HistoryResultEntity? {
        val root = runCatching { JSONArray(body) }.getOrNull() ?: return null
        if (root.length() == 0) return null

        val day = runCatching { root.getJSONObject(0) }.getOrNull() ?: return null
        val returnedDate = day.optString("date").trim()
        if (returnedDate.isNotBlank()) {
            val parsedReturnedDate = runCatching { LocalDate.parse(returnedDate) }.getOrNull()
            if (parsedReturnedDate != null && parsedReturnedDate != expectedDate) return null
        }

        val children = day.optJSONArray("child") ?: return null
        var morning: SourceRow? = null
        var evening: SourceRow? = null

        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            val time = child.optString("time").trim()
            when {
                morning == null && time.startsWith("12:01") -> {
                    morning = SourceRow(
                        twod = child.optString("twod").trim(),
                        set = child.optString("set").trim(),
                        value = child.optString("value").trim(),
                    )
                }
                evening == null && time.startsWith("16:30") -> {
                    evening = SourceRow(
                        twod = child.optString("twod").trim(),
                        set = child.optString("set").trim(),
                        value = child.optString("value").trim(),
                    )
                }
            }
        }

        if (morning == null && evening == null) return null

        return HistoryResultEntity(
            date = expectedDate,
            morning2d = morning?.twod.ifBlankOrDash(),
            morningSet = morning?.set.ifBlankOrDash(),
            morningValue = morning?.value.ifBlankOrDash(),
            evening2d = evening?.twod.ifBlankOrDash(),
            eveningSet = evening?.set.ifBlankOrDash(),
            eveningValue = evening?.value.ifBlankOrDash(),
            // ThaiStock2D does not supply Modern/Internet. Keep those fields
            // explicitly unavailable so this source alone cannot be marked
            // COMPLETE unless the Room row already has them.
            modern930 = "-",
            internet930 = "-",
            modern200 = "-",
            internet200 = "-",
        )
    }

    private data class SourceRow(
        val twod: String,
        val set: String,
        val value: String,
    )

    private fun String?.ifBlankOrDash(): String =
        this?.takeIf { it.isNotBlank() } ?: "-"
}

/**
 * Checks historical Room coverage strictly before today and fills only fields
 * that are still missing. Today remains owned by the existing LiveCollector.
 */
internal class LiveRoomKeeper(
    private val liveResults: LiveDailyResultRepository,
    private val closedDays: ClosedDayRepository,
    private val primary: HistoricalBackfillSource,
    private val backup: HistoricalBackfillSource,
    private val checkpointStore: HistoricalCheckpointStore,
    private val todayProvider: () -> LocalDate = { currentYangonDate() },
) {
    suspend fun runOnce(): LiveRoomKeeperOutcome {
        val today = todayProvider()
        val historicalEnd = today.minusDays(1)

        var cursor = checkpointStore.get() ?: previousWorkingDay(today).also {
            checkpointStore.set(it)
        }

        if (cursor.isAfter(historicalEnd)) return LiveRoomKeeperOutcome.Complete

        while (!cursor.isAfter(historicalEnd)) {
            if (!isExpectedHistoricalDay(cursor)) {
                cursor = cursor.plusDays(1)
                checkpointStore.set(cursor)
                continue
            }

            if (coverageStatus(liveResults.get(cursor)) == HistoricalCoverageStatus.COMPLETE) {
                cursor = cursor.plusDays(1)
                checkpointStore.set(cursor)
                continue
            }

            runCatching { primary.fetch(cursor) }
                .getOrNull()
                ?.let { source -> fillMissing(cursor, liveResults.get(cursor), source) }

            var current = liveResults.get(cursor)

            if (coverageStatus(current) != HistoricalCoverageStatus.COMPLETE) {
                runCatching { backup.fetch(cursor) }
                    .getOrNull()
                    ?.let { source -> fillMissing(cursor, current, source) }
                current = liveResults.get(cursor)
            }

            if (coverageStatus(current) == HistoricalCoverageStatus.COMPLETE) {
                cursor = cursor.plusDays(1)
                checkpointStore.set(cursor)
                continue
            }

            return LiveRoomKeeperOutcome.Pending(cursor)
        }

        return LiveRoomKeeperOutcome.Complete
    }

    private suspend fun isExpectedHistoricalDay(date: LocalDate): Boolean =
        isWorkingDay(date) && !closedDays.isClosed(date)

    private suspend fun fillMissing(
        date: LocalDate,
        existing: LiveDailyResultEntity?,
        source: HistoryResultEntity,
    ) {
        val patch = missingFieldsOnly(date, existing, source) ?: return
        liveResults.apply(listOf(patch))
    }

    private fun missingFieldsOnly(
        date: LocalDate,
        existing: LiveDailyResultEntity?,
        source: HistoryResultEntity,
    ): LiveDailyResultPatch? {
        val patch = LiveDailyResultPatch(
            date = date,
            modern930 = source.modern930.takeIf { isMissing2d(existing?.modern930) && isValid2d(it) },
            internet930 = source.internet930.takeIf { isMissing2d(existing?.internet930) && isValid2d(it) },
            modern200 = source.modern200.takeIf { isMissing2d(existing?.modern200) && isValid2d(it) },
            internet200 = source.internet200.takeIf { isMissing2d(existing?.internet200) && isValid2d(it) },
            morning2d = source.morning2d.takeIf { isMissing2d(existing?.morning2d) && isValid2d(it) },
            morningSet = source.morningSet.takeIf { isMissingMetric(existing?.morningSet) && isValidMetric(it) },
            morningValue = source.morningValue.takeIf { isMissingMetric(existing?.morningValue) && isValidMetric(it) },
            evening2d = source.evening2d.takeIf { isMissing2d(existing?.evening2d) && isValid2d(it) },
            eveningSet = source.eveningSet.takeIf { isMissingMetric(existing?.eveningSet) && isValidMetric(it) },
            eveningValue = source.eveningValue.takeIf { isMissingMetric(existing?.eveningValue) && isValidMetric(it) },
        )

        return patch.takeUnless {
            listOf(
                it.modern930, it.internet930, it.modern200, it.internet200,
                it.morning2d, it.morningSet, it.morningValue,
                it.evening2d, it.eveningSet, it.eveningValue,
            ).all { value -> value == null }
        }
    }

    private fun coverageStatus(row: LiveDailyResultEntity?): HistoricalCoverageStatus {
        if (row == null) return HistoricalCoverageStatus.MISSING

        val complete = isValid2d(row.modern930) &&
            isValid2d(row.internet930) &&
            isValid2d(row.modern200) &&
            isValid2d(row.internet200) &&
            isValid2d(row.morning2d) &&
            isValidMetric(row.morningSet) &&
            isValidMetric(row.morningValue) &&
            isValid2d(row.evening2d) &&
            isValidMetric(row.eveningSet) &&
            isValidMetric(row.eveningValue)

        if (complete) return HistoricalCoverageStatus.COMPLETE

        val anyValid = listOf(
            row.modern930?.let(::isValid2d),
            row.internet930?.let(::isValid2d),
            row.modern200?.let(::isValid2d),
            row.internet200?.let(::isValid2d),
            row.morning2d?.let(::isValid2d),
            row.morningSet?.let(::isValidMetric),
            row.morningValue?.let(::isValidMetric),
            row.evening2d?.let(::isValid2d),
            row.eveningSet?.let(::isValidMetric),
            row.eveningValue?.let(::isValidMetric),
        ).any { it == true }

        return if (anyValid) HistoricalCoverageStatus.PARTIAL else HistoricalCoverageStatus.MISSING
    }

    private fun isMissing2d(value: String?): Boolean =
        value == null || !isValid2d(value)

    private fun isMissingMetric(value: String?): Boolean =
        value == null || !isValidMetric(value)

    private fun isValid2d(value: String?): Boolean =
        value?.matches(TWO_DIGIT_REGEX) == true

    private fun isValidMetric(value: String?): Boolean =
        !value.isNullOrBlank() && value != "-" && value != "--" && value != "null"

    private companion object {
        val TWO_DIGIT_REGEX = Regex("^[0-9]{2}$")

        fun previousWorkingDay(date: LocalDate): LocalDate {
            var cursor = date.minusDays(1)
            while (!isWorkingDay(cursor)) cursor = cursor.minusDays(1)
            return cursor
        }

        fun isWorkingDay(date: LocalDate): Boolean =
            date.dayOfWeek.value in 1..5
    }
}
