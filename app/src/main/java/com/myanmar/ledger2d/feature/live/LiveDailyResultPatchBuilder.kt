package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
import java.time.LocalDate

/**
 * Build Room updates from a current-day Luke observation.
 *
 * The provider fields identify their own checkpoint (09:30 reference,
 * morning final, 14:00 reference, evening final). Eligibility is based on
 * the values actually present, not exact equality with the app clock:
 * Luke can publish a checkpoint a few seconds before its scheduled label.
 *
 * This is deliberately separate from the UI/Daily Flow cycle date. Historical
 * fallback and cached feeds must never be re-labelled as fresh Room data for
 * today.
 */
internal fun buildLiveDailyResultPatches(
    feed: LiveFeedData,
    today: LocalDate,
): List<LiveDailyResultPatch> {
    if (!isWorkingDay(today) || feed.isCloseDay) return emptyList()

    val providerDate = canonicalDate(feed.date) ?: return emptyList()
    val observationInstant = parseDecisionInstant(feed) ?: return emptyList()
    val observationDate = observationInstant.atZone(YANGON).toLocalDate()

    // Luke's live endpoint represents the current calendar date. Require both
    // the payload date and observation date to match the app's Yangon date.
    if (providerDate != today || observationDate != today) return emptyList()

    val sourceAt = observationInstant.toEpochMilli()

    // Reference values are one atomic pair. A partially available pair is
    // not stored as a completed checkpoint.
    val modern930 = if (
        isValidLive2d(feed.modern930) && isValidLive2d(feed.internet930)
    ) feed.modern930 else null
    val internet930 = if (modern930 != null) feed.internet930 else null

    val modern200 = if (
        isValidLive2d(feed.modern200) && isValidLive2d(feed.internet200)
    ) feed.modern200 else null
    val internet200 = if (modern200 != null) feed.internet200 else null

    // Result number and SET/VALUE can become available at slightly different
    // times. Keep every valid field and let the repository merge later updates
    // without clearing fields that are not present in this observation.
    val morning2d = feed.morning.result.takeIf(::isValidLive2d)
    val morningSet = feed.morning.set.takeIf(::validMoney)
    val morningValue = feed.morning.value.takeIf(::validMoney)
    val evening2d = feed.evening.result.takeIf(::isValidLive2d)
    val eveningSet = feed.evening.set.takeIf(::validMoney)
    val eveningValue = feed.evening.value.takeIf(::validMoney)

    val patch = LiveDailyResultPatch(
        date = today,
        modern930 = modern930,
        internet930 = internet930,
        modern200 = modern200,
        internet200 = internet200,
        morning2d = morning2d,
        morningSet = morningSet,
        morningValue = morningValue,
        evening2d = evening2d,
        eveningSet = eveningSet,
        eveningValue = eveningValue,
        reference930SourceAt = sourceAt.takeIf { modern930 != null },
        reference200SourceAt = sourceAt.takeIf { modern200 != null },
        morningSourceAt = sourceAt.takeIf {
            morning2d != null || morningSet != null || morningValue != null
        },
        eveningSourceAt = sourceAt.takeIf {
            evening2d != null || eveningSet != null || eveningValue != null
        },
    )

    val hasData = modern930 != null ||
        internet930 != null ||
        modern200 != null ||
        internet200 != null ||
        morning2d != null ||
        morningSet != null ||
        morningValue != null ||
        evening2d != null ||
        eveningSet != null ||
        eveningValue != null

    return if (hasData) listOf(patch) else emptyList()
}
