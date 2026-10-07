package com.myanmar.ledger2d.feature.live

import java.time.LocalDate
import java.time.LocalTime

/**
 * Fresh-install / cold-start policy only.
 *
 * This layer decides which completed working-day data may seed the initial
 * display for the current app-clock position. It does not perform network
 * requests, mutate Room, or alter the normal Daily Flow schedule.
 */
internal data class FreshInstallBootstrapPlan(
    val cycleDate: LocalDate,
    val fallbackDate: LocalDate?,
    val allowedCacheDates: Set<LocalDate>,
) {
    val recoverCurrentCycle: Boolean
        get() = true

    val allowPreviousWorkingFallback: Boolean
        get() = fallbackDate != null
}

internal fun freshInstallBootstrapPlan(
    today: LocalDate,
    now: LocalTime,
): FreshInstallBootstrapPlan {
    val cycleDate = dailyCycleDate(today, now)
    val fallbackDate =
        if (
            cycleDate == today &&
            isWorkingDay(today) &&
            !now.isBefore(LocalTime.of(9, 30)) &&
            now.isBefore(LocalTime.of(16, 30))
        ) {
            previousWorkingDay(cycleDate)
        } else {
            null
        }

    val previous = previousWorkingDay(cycleDate)
    val allowed = when {
        !isWorkingDay(today) -> setOf(cycleDate)
        now.isBefore(LocalTime.of(9, 30)) -> setOf(cycleDate)
        else -> setOf(cycleDate, previous)
    }

    return FreshInstallBootstrapPlan(
        cycleDate = cycleDate,
        fallbackDate = fallbackDate,
        allowedCacheDates = allowed,
    )
}
