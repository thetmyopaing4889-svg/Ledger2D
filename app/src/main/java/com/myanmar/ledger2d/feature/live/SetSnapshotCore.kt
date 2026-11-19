package com.myanmar.ledger2d.feature.live

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.jsoup.Jsoup

/** Experimental SET observation for UI only; Luke is the only official final. */
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

internal data class SetHtmlParseResult(
    val snapshot: SetMarketSnapshot?,
    /** Safe stage name, not raw website HTML (which may be large or sensitive). */
    val failureStage: String? = null,
)

/**
 * Match the successful Colab HTMLParser behavior: decode HTML entities and
 * read *visible* text, excluding script/style/noscript. Do not guess numbers
 * from JSON scripts, SET50, or other index panels.
 */
internal object SetSnapshotHtmlParser {
    private val whitespace = Regex("""\s+""")
    private val index = Regex("""\bIndex\s+((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})\b""", RegexOption.IGNORE_CASE)
    private val value = Regex("""\bValue\s*\(\s*M\.?\s*Baht\s*\)\s*((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})\b""", RegexOption.IGNORE_CASE)
    private val update = Regex("""\bLast\s+Update\s*:?\s*(\d{1,2}\s+[A-Za-z]{3}\s+\d{4}\s+\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val status = Regex("""\bMarket\s+Status\s*:\s*([A-Za-z][A-Za-z0-9 ]{0,30}?)(?=\s+Last\s+Update|\s+Open\s+|\s+Index\s+|$)""", RegexOption.IGNORE_CASE)
    private val set50 = Regex("""\bSET50\b""", RegexOption.IGNORE_CASE)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM uuuu HH:mm:ss", Locale.ENGLISH)
    private val bangkok = ZoneId.of("Asia/Bangkok")

    fun parse(html: String): SetMarketSnapshot? = parseDetailed(html).snapshot

    fun parseDetailed(html: String): SetHtmlParseResult {
        return try {
            val doc = Jsoup.parse(html)
            doc.select("script, style, noscript, template").remove()
            val visible = whitespace.replace(doc.body().text(), " ")
            val marker = visible.indexOf("SET Index Series", ignoreCase = true)
            if (marker < 0) return SetHtmlParseResult(null, "SET_PANEL_MISSING")
            val section = visible.substring(marker, minOf(marker + 5_000, visible.length))
            val otherIndex = set50.find(section)?.range?.first ?: section.length
            val panel = section.substring(0, otherIndex)
            val idx = index.find(panel)?.groupValues?.get(1)
                ?: return SetHtmlParseResult(null, "SET_INDEX_MISSING")
            val valText = value.find(panel)?.groupValues?.get(1)
                ?: return SetHtmlParseResult(null, "TRADING_VALUE_MISSING")
            val stamp = update.find(panel)?.groupValues?.get(1)
                ?: return SetHtmlParseResult(null, "SOURCE_TIME_MISSING")

            val instant = try {
                LocalDateTime.parse(stamp, dateFormat).atZone(bangkok)
            } catch (_: Exception) {
                return SetHtmlParseResult(null, "SOURCE_TIME_INVALID")
            }
            val digitA = idx.substringAfterLast('.').last()
            val digitB = valText.substringBefore('.').filter(Char::isDigit).last()
            SetHtmlParseResult(
                snapshot = SetMarketSnapshot(
                    index = idx,
                    tradingValueMbaht = valText,
                    sourceUpdateBangkok = instant,
                    marketStatus = status.find(panel)?.groupValues?.get(1)?.trim(),
                    computed2d = "$digitA$digitB",
                )
            )
        } catch (_: Exception) {
            SetHtmlParseResult(null, "HTML_PARSE_EXCEPTION")
        }
    }
}

internal enum class SetMorningSelectionPhase {
    WAITING, SET_LIVE, UI_RESULT_READY, CONFLICT
}

internal data class SetMorningSelection(
    val phase: SetMorningSelectionPhase,
    val snapshot: SetMarketSnapshot?,
)

/**
 * A source timestamp > 12:01:00 Yangon seals the last unambiguous snapshot
 * observed at source time >= 12:00:00 and <= 12:01:00. Never use a post-cutoff
 * or invented snapshot as the result; Luke remains the sole official final.
 *
 * If the *same* source timestamp has different prices, quarantine that
 * timestamp. A later pre-cutoff source update can recover the selector.
 */
internal class SetMorningSnapshotSelector(private val drawDate: LocalDate) {
    private val firstAcceptableTime = LocalTime.of(11, 59)
    private val minimumResultTime = LocalTime.NOON
    private val cutoff = LocalTime.of(12, 1)
    private var latest: SetMarketSnapshot? = null
    private var pastCutoffObserved = false
    private var conflictAt: Instant? = null

    fun observe(snapshot: SetMarketSnapshot): SetMorningSelection {
        val source = snapshot.sourceUpdateYangon
        if (source.toLocalDate() != drawDate) return current()
        val time = source.toLocalTime()

        if (time.isAfter(cutoff)) {
            pastCutoffObserved = true
            return current()
        }
        if (pastCutoffObserved || time.isBefore(firstAcceptableTime)) return current()

        val sourceInstant = source.toInstant()
        val blocked = conflictAt
        if (blocked != null) {
            // A different value at exactly the same source stamp is ambiguous.
            // A newer *pre-cutoff* stamp can recover safely.
            if (!sourceInstant.isAfter(blocked)) return current()
            conflictAt = null
            latest = snapshot
            return current()
        }

        val previous = latest
        when {
            previous == null ||
                sourceInstant.isAfter(previous.sourceUpdateYangon.toInstant()) -> latest = snapshot
            sourceInstant == previous.sourceUpdateYangon.toInstant() &&
                (snapshot.index != previous.index ||
                    snapshot.tradingValueMbaht != previous.tradingValueMbaht) -> {
                conflictAt = sourceInstant
                latest = null
            }
        }
        return current()
    }

    fun current(): SetMorningSelection = when {
        conflictAt != null -> SetMorningSelection(SetMorningSelectionPhase.CONFLICT, null)
        pastCutoffObserved -> {
            val chosen = latest?.takeIf {
                !it.sourceUpdateYangon.toLocalTime().isBefore(minimumResultTime)
            }
            if (chosen != null)
                SetMorningSelection(SetMorningSelectionPhase.UI_RESULT_READY, chosen)
            else SetMorningSelection(SetMorningSelectionPhase.WAITING, null)
        }
        latest != null -> SetMorningSelection(SetMorningSelectionPhase.SET_LIVE, latest)
        else -> SetMorningSelection(SetMorningSelectionPhase.WAITING, null)
    }
}
