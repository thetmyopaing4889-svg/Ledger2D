package com.myanmar.ledger2d.feature.live

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SET webpage observation for display-only research.
 *
 * NEVER convert this model to LiveSessionData, LiveFeedData.morning, Room results,
 * winning-number auto-save, or any Luke-final side effect.
 */
internal data class SetMarketSnapshot(
    val index: String,
    val tradingValueMbaht: String,
    val sourceUpdateBangkok: ZonedDateTime,
    val marketStatus: String?,
    val computed2d: String,
) {
    val sourceUpdateYangon: ZonedDateTime
        get() = sourceUpdateBangkok.withZoneSameInstant(ZoneId.of("Asia/Yangon"))
}

/** Parses labeled SET Index panel data, never another index or unlabeled numbers. */
internal object SetSnapshotHtmlParser {
    private val ignored = Regex("""(?is)<(script|style|noscript)\b[^>]*>.*?</\1\s*>""")
    private val comments = Regex("""(?s)<!--.*?-->""")
    private val tags = Regex("""(?s)<[^>]*>""")
    private val whitespace = Regex("""\s+""")
    private val index = Regex("""\bIndex\s+((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})\b""", RegexOption.IGNORE_CASE)
    private val value = Regex("""\bValue\s*\(\s*M\.?\s*Baht\s*\)\s*((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})\b""", RegexOption.IGNORE_CASE)
    private val update = Regex("""\bLast\s+Update\s*:?\s*(\d{1,2}\s+[A-Za-z]{3}\s+\d{4}\s+\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val status = Regex("""\bMarket\s+Status\s*:\s*([A-Za-z][A-Za-z0-9 ]{0,30}?)(?=\s+Last\s+Update|\s+Open\s+|\s+Index\s+|$)""", RegexOption.IGNORE_CASE)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM uuuu HH:mm:ss", Locale.ENGLISH)
    private val bangkok = ZoneId.of("Asia/Bangkok")

    fun parse(html: String): SetMarketSnapshot? = runCatching {
        val visible = whitespace.replace(
            tags.replace(
                comments.replace(
                    ignored.replace(html, " "), " "
                ).replace("&nbsp;", " ").replace("&#160;", " "),
                " "
            ),
            " "
        )
        val marker = visible.indexOf("SET Index Series", ignoreCase = true)
        require(marker >= 0) { "SET Index Series panel missing" }
        val panel = visible.substring(marker, minOf(marker + 5_000, visible.length))
            .substringBefore("SET50")
        val indexText = requireNotNull(index.find(panel)?.groupValues?.get(1))
        val valueText = requireNotNull(value.find(panel)?.groupValues?.get(1))
        val timestampText = requireNotNull(update.find(panel)?.groupValues?.get(1))
        val indexLastDecimal = indexText.substringAfterLast('.').last()
        val valueLastInteger = valueText.substringBefore('.').filter(Char::isDigit).last()
        val timestamp = LocalDateTime.parse(timestampText, dateFormat).atZone(bangkok)
        SetMarketSnapshot(
            index = indexText,
            tradingValueMbaht = valueText,
            sourceUpdateBangkok = timestamp,
            marketStatus = status.find(panel)?.groupValues?.get(1)?.trim(),
            computed2d = "$indexLastDecimal$valueLastInteger",
        )
    }.getOrNull()
}

internal enum class SetMorningSelectionPhase {
    WAITING, SET_LIVE, UI_RESULT_READY, CONFLICT
}

internal data class SetMorningSelection(
    val phase: SetMorningSelectionPhase,
    val snapshot: SetMarketSnapshot?,
)

/**
 * Pure cutoff selector. Caller serializes incoming requests; polling and UI handoff
 * will be integrated separately. Phone time is never used for result selection.
 *
 * The first observed SET source timestamp strictly after 12:01 Yangon seals the
 * latest eligible source timestamp <= 12:01:00. No state is persisted as final.
 */
internal class SetMorningSnapshotSelector(private val drawDate: LocalDate) {
    private val firstAcceptableTime = LocalTime.of(11, 59)
    private val minimumResultTime = LocalTime.of(12, 0)
    private val cutoff = LocalTime.of(12, 1)
    private var latest: SetMarketSnapshot? = null
    private var pastCutoffObserved = false
    private var conflictingSourceTimestamp = false

    fun observe(snapshot: SetMarketSnapshot): SetMorningSelection {
        val source = snapshot.sourceUpdateYangon
        if (source.toLocalDate() != drawDate) return current()
        val sourceTime = source.toLocalTime()

        if (sourceTime.isAfter(cutoff)) {
            pastCutoffObserved = true
            return current()
        }

        if (pastCutoffObserved || conflictingSourceTimestamp || sourceTime.isBefore(firstAcceptableTime)) {
            return current()
        }

        val previous = latest
        when {
            previous == null || source.toInstant().isAfter(previous.sourceUpdateYangon.toInstant()) -> {
                latest = snapshot
            }
            source.toInstant() == previous.sourceUpdateYangon.toInstant() &&
                (snapshot.index != previous.index || snapshot.tradingValueMbaht != previous.tradingValueMbaht) -> {
                // Last Update may be page-wide, not an atomic timestamp for both fields.
                // Reject ambiguous equal-timestamp data rather than claim a final result.
                conflictingSourceTimestamp = true
            }
            // Duplicates and out-of-order older observations cannot replace the latest.
        }
        return current()
    }

    fun current(): SetMorningSelection = when {
        conflictingSourceTimestamp -> SetMorningSelection(SetMorningSelectionPhase.CONFLICT, null)
        pastCutoffObserved -> {
            val chosen = latest?.takeIf {
                !it.sourceUpdateYangon.toLocalTime().isBefore(minimumResultTime)
            }
            if (chosen != null) {
                SetMorningSelection(SetMorningSelectionPhase.UI_RESULT_READY, chosen)
            } else {
                SetMorningSelection(SetMorningSelectionPhase.WAITING, null)
            }
        }
        latest != null -> SetMorningSelection(SetMorningSelectionPhase.SET_LIVE, latest)
        else -> SetMorningSelection(SetMorningSelectionPhase.WAITING, null)
    }
}
