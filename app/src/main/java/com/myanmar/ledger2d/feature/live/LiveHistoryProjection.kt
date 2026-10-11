package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.HistoryResultEntity
import java.time.LocalDate
import java.time.LocalTime

private fun historySession(
    result: String,
    set: String,
    value: String,
): LiveSessionData {
    val normalizedResult = result.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    val normalizedSet = set.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    val normalizedValue = value.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    return LiveSessionData(
        result = normalizedResult,
        set = normalizedSet,
        value = normalizedValue,
        finalized = isValidLive2d(normalizedResult),
    )
}

/**
 * Reconstruct the complete held working-day display from the same historical
 * 2D source already used by the Room Keeper. This is display-only cold-start
 * state before the next working-day 09:30 boundary; it does not fabricate a
 * LIVE value.
 */
internal fun historyRowToFeed(row: HistoryResultEntity): LiveFeedData {
    val morning = historySession(row.morning2d, row.morningSet, row.morningValue)
    val evening = historySession(row.evening2d, row.eveningSet, row.eveningValue)
    val lastFinalTime = when {
        evening.finalized -> LocalTime.of(16, 30)
        morning.finalized -> LocalTime.of(12, 1)
        else -> LocalTime.MIDNIGHT
    }

    return LiveFeedData(
        date = row.date.toString(),
        currentTime = lastFinalTime.toString(),
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = morning,
        evening = evening,
        modern930 = row.modern930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        internet930 = row.internet930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        modern200 = row.modern200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        internet200 = row.internet200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING,
        sourceTag = "HISTORY",
        serverTimeEpochMs = row.date.atTime(lastFinalTime).atZone(YANGON).toInstant().toEpochMilli(),
    )
}

internal fun historyRowToFeed(
    row: HistoryResultEntity,
    today: LocalDate = currentYangonDate(),
    now: LocalTime = LocalTime.now(YANGON),
): LiveFeedData {
    val cycleDate = dailyCycleDate(today, now)
    val isCurrentCycleRow = row.date == cycleDate
    val isHeldFallbackRow =
        row.date != today &&
            (now.isBefore(MORNING_LIVE) || !isWorkingDay(today))

    fun session(
        result: String,
        set: String,
        value: String,
        allowed: Boolean,
    ): LiveSessionData {
        if (!allowed) return LiveSessionData(LIVE_PENDING, LIVE_PENDING, LIVE_PENDING, false)
        val normalizedResult = result.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
        val normalizedSet = set.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
        val normalizedValue = value.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
        return LiveSessionData(
            result = normalizedResult,
            set = normalizedSet,
            value = normalizedValue,
            finalized = isValidLive2d(normalizedResult),
        )
    }

    // When the historical row is the held cycleDate itself (Monday-Friday
    // pre-09:30, or the weekend-held Friday row), the whole completed row is
    // valid because that cycle has already finished. For today's cycle,
    // future session/reference values must stay masked until their boundaries.
    val heldCompletedCycle = cycleDate != today

    val showMorningFinal = heldCompletedCycle || !now.isBefore(MORNING_CLOSE)
    val showEveningFinal = heldCompletedCycle || !now.isBefore(EVENING_CLOSE)
    val morning = session(
        row.morning2d,
        row.morningSet,
        row.morningValue,
        (isCurrentCycleRow && showMorningFinal) || isHeldFallbackRow,
    )
    val evening = session(
        row.evening2d,
        row.eveningSet,
        row.eveningValue,
        (isCurrentCycleRow && showEveningFinal) || isHeldFallbackRow,
    )

    val show930Reference =
        heldCompletedCycle || !now.isBefore(MORNING_REFERENCE)
    val show200Reference =
        heldCompletedCycle || !now.isBefore(AFTERNOON_REFERENCE)

    val modern930 = if ((isCurrentCycleRow || isHeldFallbackRow) && show930Reference) {
        row.modern930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }
    val internet930 = if ((isCurrentCycleRow || isHeldFallbackRow) && show930Reference) {
        row.internet930.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }
    val modern200 = if ((isCurrentCycleRow || isHeldFallbackRow) && show200Reference) {
        row.modern200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }
    val internet200 = if ((isCurrentCycleRow || isHeldFallbackRow) && show200Reference) {
        row.internet200.takeUnless { it == "-" || it.isBlank() } ?: LIVE_PENDING
    } else {
        LIVE_PENDING
    }

    val projectedTime = if (!isCurrentCycleRow) {
        EVENING_CLOSE
    } else {
        when {
            now.isBefore(MORNING_REFERENCE) -> MORNING_REFERENCE
            now.isBefore(MORNING_LIVE) -> MORNING_REFERENCE
            now.isBefore(MORNING_CLOSE) -> MORNING_LIVE
            now.isBefore(AFTERNOON_REFERENCE) -> MORNING_CLOSE
            now.isBefore(EVENING_LIVE) -> AFTERNOON_REFERENCE
            now.isBefore(EVENING_CLOSE) -> EVENING_LIVE
            else -> EVENING_CLOSE
        }
    }

    return LiveFeedData(
        date = row.date.toString(),
        currentTime = projectedTime.toString(),
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = morning,
        evening = evening,
        modern930 = modern930,
        internet930 = internet930,
        modern200 = modern200,
        internet200 = internet200,
        sourceTag = "HISTORY",
        serverTimeEpochMs = row.date.atTime(projectedTime).atZone(YANGON).toInstant().toEpochMilli(),
    )
}

internal fun historyRowToFinal(row: HistoryResultEntity): LiveHeroSnapshot? =
    historyRowToFinal(row, LocalTime.of(23, 59, 59))

internal fun historyRowToFinal(
    row: HistoryResultEntity,
    now: LocalTime,
): LiveHeroSnapshot? {
    val result: String
    val set: String
    val value: String
    val label: String

    when {
        !now.isBefore(EVENING_CLOSE) && isValidLive2d(row.evening2d) -> {
            result = row.evening2d
            set = row.eveningSet
            value = row.eveningValue
            label = LIVE_SESSION_EVENING_LABEL
        }

        !now.isBefore(MORNING_CLOSE) && isValidLive2d(row.morning2d) -> {
            result = row.morning2d
            set = row.morningSet
            value = row.morningValue
            label = LIVE_SESSION_MORNING_LABEL
        }

        else -> return null
    }

    return LiveHeroSnapshot(
        result = result,
        set = set.takeUnless { it == "-" }.orEmpty().ifBlank { LIVE_PENDING },
        value = value.takeUnless { it == "-" }.orEmpty().ifBlank { LIVE_PENDING },
        sessionLabel = label,
        date = row.date.toString(),
    )
}

