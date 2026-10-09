package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultPatch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun buildLiveDailyResultPatches(
    feed: LiveFeedData,
    today: LocalDate,
    now: LocalTime,
): List<LiveDailyResultPatch> {
    val cycleDate = dailyCycleDate(today, now)
    val providerDate = canonicalDate(feed.date)
    val sourceAt = feed.serverTimeEpochMs
        ?: parseDecisionInstant(feed)?.toEpochMilli()
        ?: Instant.now().toEpochMilli()

    val byDate = linkedMapOf<LocalDate, LiveDailyResultPatch>()

    fun mergePatch(
        date: LocalDate,
        update: LiveDailyResultPatch.() -> LiveDailyResultPatch,
    ) {
        val old = byDate[date]
        val base = old ?: LiveDailyResultPatch(date = date)
        byDate[date] = base.update()
    }

    if (
        providerDate != null &&
        isWorkingDay(providerDate) &&
        (providerDate == cycleDate || providerDate == previousWorkingDay(cycleDate))
    ) {
        val morning2d = feed.morning.result.takeIf(::isValidLive2d)
        val morningSet = feed.morning.set.takeIf(::validMoney)
        val morningValue = feed.morning.value.takeIf(::validMoney)
        val evening2d = feed.evening.result.takeIf(::isValidLive2d)
        val eveningSet = feed.evening.set.takeIf(::validMoney)
        val eveningValue = feed.evening.value.takeIf(::validMoney)

        if (morning2d != null || morningSet != null || morningValue != null) {
            mergePatch(providerDate) {
                copy(
                    morning2d = morning2d,
                    morningSet = morningSet,
                    morningValue = morningValue,
                    morningSourceAt = sourceAt,
                )
            }
        }
        if (evening2d != null || eveningSet != null || eveningValue != null) {
            mergePatch(providerDate) {
                copy(
                    evening2d = evening2d,
                    eveningSet = eveningSet,
                    eveningValue = eveningValue,
                    eveningSourceAt = sourceAt,
                )
            }
        }
    }

    // References belong to the app's current working-day cycle, not Luke's
    // completed-result date. Never persist them before their display boundary.
    if (
        isWorkingDay(today) &&
        cycleDate == today &&
        isCurrentCycleReferenceObservation(feed, cycleDate)
    ) {
        val modern930 = if (!now.isBefore(MORNING_REFERENCE)) {
            feed.modern930.takeIf(::isValidLive2d)
        } else null
        val internet930 = if (!now.isBefore(MORNING_REFERENCE)) {
            feed.internet930.takeIf(::isValidLive2d)
        } else null

        val modern200 = if (!now.isBefore(AFTERNOON_REFERENCE)) {
            feed.modern200.takeIf(::isValidLive2d)
        } else null
        val internet200 = if (!now.isBefore(AFTERNOON_REFERENCE)) {
            feed.internet200.takeIf(::isValidLive2d)
        } else null

        if (modern930 != null || internet930 != null || modern200 != null || internet200 != null) {
            mergePatch(today) {
                copy(
                    modern930 = modern930,
                    internet930 = internet930,
                    modern200 = modern200,
                    internet200 = internet200,
                    reference930SourceAt = if (modern930 != null || internet930 != null) sourceAt else null,
                    reference200SourceAt = if (modern200 != null || internet200 != null) sourceAt else null,
                )
            }
        }
    }

    return byDate.values.filter {
        it.modern930 != null ||
            it.internet930 != null ||
            it.modern200 != null ||
            it.internet200 != null ||
            it.morning2d != null ||
            it.morningSet != null ||
            it.morningValue != null ||
            it.evening2d != null ||
            it.eveningSet != null ||
            it.eveningValue != null
    }
}

