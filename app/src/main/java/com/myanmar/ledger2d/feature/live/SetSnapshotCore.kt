package com.myanmar.ledger2d.feature.live

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TreeMap
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
    // SET sometimes concatenates a nearby digit (e.g. Open2) into its visible
    // text. Recognize only known status words; never guess Intermission.
    private val status = Regex(
        """\bMarket\s+Status\s*:\s*(Intermission|Off\s*Hour|Closed|Open)(?=\s|\d|$)""",
        RegexOption.IGNORE_CASE,
    )
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
                    marketStatus = status.find(panel)?.groupValues?.get(1)?.trim()
                        ?.replace(Regex("""\s+"""), ""),
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

/** Only an explicitly parsed SET Intermission status qualifies for a UI result. */
internal fun isSetIntermission(status: String?): Boolean =
    status?.trim()?.equals("Intermission", ignoreCase = true) == true

/**
 * Select the *closest* unambiguous INTERMISSION observation at or before
 * Yangon 12:01:00 (from 12:00 onward), and never a post-cutoff source stamp.
 *
 * Source HTML can regress (newer timestamp followed by an older cached page)
 * or show different SET/Value pairs with the SAME source timestamp. Keep a
 * small time-indexed ledger so a stale page never overrides newer evidence,
 * a contradictory timestamp is quarantined, and a previous unambiguous
 * Intermission candidate can be used as a safe fallback.
 *
 * The result is UI-only; Luke remains the sole official final authority.
 */
internal class SetMorningSnapshotSelector(private val drawDate: LocalDate) {
    private val firstAcceptableTime = LocalTime.of(11, 59)
    private val minimumResultTime = LocalTime.NOON
    private val cutoff = LocalTime.of(12, 1)
    private val noonInstant = drawDate.atTime(minimumResultTime)
        .atZone(YANGON).toInstant()
    private val observations = TreeMap<Instant, SetMarketSnapshot?>()
    private var pastCutoffObserved = false
    private var contradictoryStampSeen = false

    fun observe(snapshot: SetMarketSnapshot): SetMorningSelection {
        val source = snapshot.sourceUpdateYangon
        if (source.toLocalDate() != drawDate) return current()
        val localTime = source.toLocalTime()

        if (localTime.isAfter(cutoff)) {
            pastCutoffObserved = true
            return current()
        }
        // An actual source stamp beyond 12:01 seals the observed evidence.
        // Do not retroactively promote an older cached HTML response.
        if (pastCutoffObserved || localTime.isBefore(firstAcceptableTime)) {
            return current()
        }

        val stamp = source.toInstant()
        if (!observations.containsKey(stamp)) {
            observations[stamp] = snapshot
        } else {
            val first = observations[stamp]
            if (first != null && (
                    first.index != snapshot.index ||
                    first.tradingValueMbaht != snapshot.tradingValueMbaht ||
                    first.marketStatus?.trim()?.lowercase() !=
                        snapshot.marketStatus?.trim()?.lowercase()
                )
            ) {
                // Never use either version of a mixed same-stamp SET/Value pair.
                observations[stamp] = null
                contradictoryStampSeen = true
            }
        }

        // Five-minute diagnostic window is <100 requests at 3s; remain bounded.
        while (observations.size > 160) observations.pollFirstEntry()
        return current()
    }

    fun current(): SetMorningSelection {
        val latestEntry = observations.lastEntry()
        if (pastCutoffObserved) {
            val nearestIntermission = observations.descendingMap().entries
                .firstOrNull { (stamp, candidate) ->
                    !stamp.isBefore(noonInstant) &&
                        candidate != null && isSetIntermission(candidate.marketStatus)
                }?.value
            if (nearestIntermission != null) {
                return SetMorningSelection(
                    SetMorningSelectionPhase.UI_RESULT_READY, nearestIntermission,
                )
            }
            return SetMorningSelection(
                if (contradictoryStampSeen) SetMorningSelectionPhase.CONFLICT
                else SetMorningSelectionPhase.WAITING,
                null,
            )
        }

        return if (latestEntry == null) {
            SetMorningSelection(SetMorningSelectionPhase.WAITING, null)
        } else if (latestEntry.value == null) {
            // The latest time stamp is contradictory; fall back to Luke LIVE
            // rather than displaying one of two conflicting prices.
            SetMorningSelection(SetMorningSelectionPhase.CONFLICT, null)
        } else {
            SetMorningSelection(SetMorningSelectionPhase.SET_LIVE, latestEntry.value)
        }
    }
}
