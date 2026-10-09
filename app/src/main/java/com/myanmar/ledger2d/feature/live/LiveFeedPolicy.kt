package com.myanmar.ledger2d.feature.live

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun canonicalDate(raw: String): LocalDate? = runCatching {
    LocalDate.parse(raw.trim())
}.getOrElse {
    runCatching {
        LocalDate.parse(
            raw.trim(),
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        )
    }.getOrNull()
}

internal fun parseDecisionInstant(f: LiveFeedData): Instant? {
    f.serverTimeEpochMs
        ?.takeIf { it >= 946684800000L }
        ?.let { return Instant.ofEpochMilli(it) }
    val raw = f.currentTime.trim()
    if (raw.isBlank() || raw == LIVE_PENDING) return null

    return try {
        when {
            raw.contains("T") -> Instant.parse(raw)
            raw.contains(" ") -> java.time.LocalDateTime
                .parse(raw.replace(' ', 'T'))
                .atZone(YANGON)
                .toInstant()
            else -> {
                val d = canonicalDate(f.date) ?: return null
                java.time.LocalDateTime
                    .of(d, LocalTime.parse(raw.take(8)))
                    .atZone(YANGON)
                    .toInstant()
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun isValidLive2d(v: String): Boolean =
    v.matches(Regex("^[0-9]{2}$"))

internal fun validMoney(v: String): Boolean =
    v.isNotBlank() && v != LIVE_PENDING

private fun currentDay(
    f: LiveFeedData,
    date: LocalDate = currentYangonDate(),
): Boolean =
    canonicalDate(f.date) == date

internal fun age(o: SourceObservation?): Long =
    if (o == null) Long.MAX_VALUE
    else maxOf(0L, monotonicMs() - o.fetchedAtElapsedMs)

internal fun hasValidLive(
    feed: LiveFeedData?,
    date: LocalDate = currentYangonDate(),
): Boolean {
    val f = feed ?: return false
    return currentDay(f, date) &&
        isValidLive2d(f.live) &&
        validMoney(f.liveSet) &&
        validMoney(f.liveVal) &&
        parseDecisionInstant(f) != null
}

internal fun liveValid(
    o: SourceObservation?,
    date: LocalDate = currentYangonDate(),
): Boolean = hasValidLive(o?.feed, date)

internal fun finalValid(
    session: LiveSessionData?,
    feed: LiveFeedData?,
    date: LocalDate = currentYangonDate(),
): Boolean {
    if (session == null || feed == null || !currentDay(feed, date)) return false
    // Luke's result_1200 / result_430 is the final-result trigger.
    // SET/VALUE are supplementary metadata and may arrive slightly later.
    return isValidLive2d(session.result)
}

internal fun latestFinalFor(feed: LiveFeedData): LiveHeroSnapshot? = when {
    feed.evening.finalized -> LiveHeroSnapshot(
        feed.evening.result,
        feed.evening.set,
        feed.evening.value,
        LIVE_SESSION_EVENING_LABEL,
        feed.date,
    )
    feed.morning.finalized -> LiveHeroSnapshot(
        feed.morning.result,
        feed.morning.set,
        feed.morning.value,
        LIVE_SESSION_MORNING_LABEL,
        feed.date,
    )
    else -> null
}

internal fun isCurrentCycleReferenceObservation(
    feed: LiveFeedData,
    cycleDate: LocalDate,
): Boolean {
    val epoch = feed.serverTimeEpochMs
    // Unit fixtures and some legacy provider responses may omit a real epoch
    // and use a tiny sentinel. Treat those as "no absolute server date" so
    // #403's split history-date/current-reference behavior stays valid.
    if (epoch == null || epoch < 946684800000L) return true

    val serverDate = Instant.ofEpochMilli(epoch)
        .atZone(YANGON)
        .toLocalDate()

    // Some Luke responses carry the current reference values while the
    // provider's result-date field still names the previous completed day.
    // When an absolute server timestamp is available, require that timestamp
    // to belong to the current working-day cycle so stale snapshots cannot
    // complete a reference retry.
    return serverDate == cycleDate
}

