package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A small early-arrival tolerance is needed because Luke can publish a
 * checkpoint a few seconds before its scheduled boundary (for example,
 * 09:29:53 for the 09:30 reference). It must not enable later checkpoints.
 */
private const val ROOM_CHECKPOINT_EARLY_GRACE_SECONDS = 10L

/**
 * Build Room updates from a current-day Luke observation.
 *
 * Room ingestion is independent from Daily Flow's cycle date and display
 * acceptance. Both the provider's date/time and the app clock are checked:
 * only fields whose own checkpoint is available are included in the patch.
 */
internal fun buildLiveDailyResultPatches(
    feed: LiveFeedData,
    today: LocalDate,
    now: LocalTime,
): List<LiveDailyResultPatch> {
    if (!isWorkingDay(today) || feed.isCloseDay) return emptyList()

    val providerDate = canonicalDate(feed.date) ?: return emptyList()
    val observationInstant = parseDecisionInstant(feed) ?: return emptyList()
    val observationDateTime = observationInstant.atZone(YANGON).toLocalDateTime()
    val observationDate = observationDateTime.toLocalDate()
    if (providerDate != today || observationDate != today) return emptyList()

    val appDateTime = LocalDateTime.of(today, now)
    // Do not accept a provider snapshot whose timestamp is materially ahead of
    // the app clock. A small difference is tolerated for clock/network jitter.
    if (observationDateTime.isAfter(appDateTime.plusSeconds(ROOM_CHECKPOINT_EARLY_GRACE_SECONDS))) {
        return emptyList()
    }

    val sourceTime = observationDateTime.toLocalTime()
    fun checkpointAvailable(boundary: LocalTime): Boolean {
        val earliest = boundary.minusSeconds(ROOM_CHECKPOINT_EARLY_GRACE_SECONDS)
        return !sourceTime.isBefore(earliest) && !now.isBefore(earliest)
    }

    val sourceAt = observationInstant.toEpochMilli()

    // A reference checkpoint is complete only when Modern and Internet are
    // both valid, and only after its own time boundary (with the small grace).
    val reference930Available = checkpointAvailable(MORNING_REFERENCE)
    val modern930 = if (
        reference930Available &&
        isValidLive2d(feed.modern930) &&
        isValidLive2d(feed.internet930)
    ) feed.modern930 else null
    val internet930 = if (modern930 != null) feed.internet930 else null

    val reference200Available = checkpointAvailable(AFTERNOON_REFERENCE)
    val modern200 = if (
        reference200Available &&
        isValidLive2d(feed.modern200) &&
        isValidLive2d(feed.internet200)
    ) feed.modern200 else null
    val internet200 = if (modern200 != null) feed.internet200 else null

    // Result fields can arrive progressively. Keep valid fields, but don't let
    // populated morning/evening values leak into Room before their checkpoint.
    val morningAvailable = checkpointAvailable(MORNING_CLOSE)
    val morning2d = feed.morning.result.takeIf { morningAvailable && isValidLive2d(it) }
    val morningSet = feed.morning.set.takeIf { morningAvailable && validMoney(it) }
    val morningValue = feed.morning.value.takeIf { morningAvailable && validMoney(it) }

    val eveningAvailable = checkpointAvailable(EVENING_CLOSE)
    val evening2d = feed.evening.result.takeIf { eveningAvailable && isValidLive2d(it) }
    val eveningSet = feed.evening.set.takeIf { eveningAvailable && validMoney(it) }
    val eveningValue = feed.evening.value.takeIf { eveningAvailable && validMoney(it) }

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
