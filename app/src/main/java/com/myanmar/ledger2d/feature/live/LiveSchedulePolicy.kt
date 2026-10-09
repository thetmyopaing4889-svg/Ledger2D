package com.myanmar.ledger2d.feature.live

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Shared by Daily Flow and existing date/fallback consumers. Behavior is unchanged.
internal val YANGON = ZoneId.of("Asia/Yangon")

internal val MORNING_REFERENCE = LocalTime.of(9, 30)
internal val MORNING_LIVE = LocalTime.of(11, 30)
internal val MORNING_CLOSE = LocalTime.of(12, 1)
internal val MORNING_CATCHUP_END = LocalTime.of(12, 31)
internal val AFTERNOON_REFERENCE = LocalTime.of(14, 0)
internal val EVENING_LIVE = LocalTime.of(16, 0)
internal val EVENING_CLOSE = LocalTime.of(16, 30)
internal val EVENING_CATCHUP_END = LocalTime.of(17, 0)

internal enum class LiveWindowAction {
    NONE,
    REFERENCE_ONLY,
    LIVE_POLLING,
    FINALIZING,
}

internal enum class LiveSession {
    MORNING,
    EVENING,
}

internal fun liveSessionForTime(t: LocalTime): LiveSession? = when {
    // The session remains "live" through the finalization/catch-up window.
    // At the exact final boundary the provider may still be carrying the
    // last streaming value while it is preparing the verified final result.
    // Keeping the same session owner lets preserveActiveLive() retain that
    // last valid LIVE observation until the final result arrives.
    t >= MORNING_LIVE && t < MORNING_CATCHUP_END -> LiveSession.MORNING
    t >= EVENING_LIVE && t < EVENING_CATCHUP_END -> LiveSession.EVENING
    else -> null
}

internal fun liveWindowAction(t: LocalTime): LiveWindowAction = when {
    t.isBefore(MORNING_REFERENCE) -> LiveWindowAction.NONE
    t.isBefore(MORNING_LIVE) -> LiveWindowAction.REFERENCE_ONLY
    t.isBefore(MORNING_CLOSE) -> LiveWindowAction.LIVE_POLLING
    t.isBefore(MORNING_CATCHUP_END) -> LiveWindowAction.FINALIZING
    t.isBefore(AFTERNOON_REFERENCE) -> LiveWindowAction.NONE
    t.isBefore(AFTERNOON_REFERENCE.plusMinutes(1)) -> LiveWindowAction.REFERENCE_ONLY
    t.isBefore(EVENING_LIVE) -> LiveWindowAction.NONE
    t.isBefore(EVENING_CLOSE) -> LiveWindowAction.LIVE_POLLING
    t.isBefore(EVENING_CATCHUP_END) -> LiveWindowAction.FINALIZING
    else -> LiveWindowAction.NONE
}

internal fun currentYangonDate(): LocalDate = LocalDate.now(YANGON)

internal fun isWorkingDay(date: LocalDate): Boolean =
    date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

internal fun previousWorkingDay(date: LocalDate): LocalDate {
    var d = date.minusDays(1)
    while (!isWorkingDay(d)) {
        d = d.minusDays(1)
    }
    return d
}

internal fun dailyCycleDate(
    date: LocalDate = currentYangonDate(),
    time: LocalTime = LocalTime.now(YANGON),
): LocalDate =
    when {
        !isWorkingDay(date) -> previousWorkingDay(date)
        time.isBefore(MORNING_REFERENCE) -> previousWorkingDay(date)
        else -> date
    }
