package com.myanmar.ledger2d.feature.live

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Snapshot-only state: never travels through Luke feeds, Room or auto-save. */
internal data class SetMorningUiState(
    val drawDate: LocalDate,
    val selection: SetMorningSelection,
    val fetchedAt: Instant,
)

internal data class SetMorningHeroPresentation(
    val hero: LiveHeroSnapshot,
    val isLive: Boolean,
)

internal const val MAX_SET_LIVE_SOURCE_AGE_MS = 60_000L

/** Prevent an old SET number from lingering on-screen during a network outage. */
internal fun shouldDiscardStaleSetLive(candidate: SetMorningUiState?, now: Instant): Boolean {
    if (candidate?.selection?.phase != SetMorningSelectionPhase.SET_LIVE) return false
    val source = candidate.selection.snapshot?.sourceUpdateYangon?.toInstant() ?: return true
    val ageMs = java.time.Duration.between(source, now).toMillis()
    return ageMs !in 0L..MAX_SET_LIVE_SOURCE_AGE_MS
}

/**
 * Apply SET only to the visible morning LIVE hero.
 * Luke final always wins and SET never becomes a stored or official final.
 */
internal fun setMorningHeroPresentation(
    candidate: SetMorningUiState?,
    feed: LiveFeedData?,
    today: LocalDate,
    now: ZonedDateTime,
    closedDay: Boolean,
): SetMorningHeroPresentation? {
    if (candidate == null || closedDay || !isWorkingDay(today) || candidate.drawDate != today) {
        return null
    }
    val t = now.withZoneSameInstant(YANGON).toLocalTime()
    if (t.isBefore(LocalTime.NOON) || !t.isBefore(LocalTime.of(13, 0))) return null
    if (finalValid(feed?.morning, feed, today)) return null

    val selection = candidate.selection
    val snapshot = selection.snapshot ?: return null
    if (snapshot.sourceUpdateYangon.toLocalDate() != today) return null
    // Do not display a non-trading/off-hours market as morning LIVE, especially
    // when Luke cannot provide a closed-day confirmation yet.
    val market = snapshot.marketStatus?.trim()
    if (market.equals("OffHour", ignoreCase = true) ||
        market.equals("Closed", ignoreCase = true)) return null

    val live = selection.phase == SetMorningSelectionPhase.SET_LIVE
    if (!live && selection.phase != SetMorningSelectionPhase.UI_RESULT_READY) return null

    if (live) {
        // Source time is used for freshness only, never to select the final result.
        val ageMs = java.time.Duration.between(
            snapshot.sourceUpdateYangon.toInstant(), now.toInstant()
        ).toMillis()
        if (ageMs !in 0L..MAX_SET_LIVE_SOURCE_AGE_MS) return null
    }
    return SetMorningHeroPresentation(
        hero = LiveHeroSnapshot(
            result = snapshot.computed2d,
            set = snapshot.index,
            value = snapshot.tradingValueMbaht,
            sessionLabel = LIVE_SESSION_MORNING_LABEL,
            date = today.toString(),
        ),
        isLive = live,
    )
}

/** Show a brief visual correction only when an actual Luke final disagrees. */
internal fun setMorningCorrectionNeeded(
    candidate: SetMorningUiState?,
    feed: LiveFeedData?,
    today: LocalDate,
    now: ZonedDateTime,
): Boolean {
    // The morning correction label must not leak into the afternoon session.
    val time = now.withZoneSameInstant(YANGON).toLocalTime()
    if (time.isBefore(LocalTime.NOON) || !time.isBefore(LocalTime.of(13, 0))) return false
    if (candidate?.drawDate != today ||
        candidate.selection.phase != SetMorningSelectionPhase.UI_RESULT_READY ||
        !finalValid(feed?.morning, feed, today)
    ) return false
    return candidate.selection.snapshot?.computed2d != feed?.morning?.result
}
