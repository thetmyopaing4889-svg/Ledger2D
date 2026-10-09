package com.myanmar.ledger2d.feature.live

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Display-only projection of a snapshot and the current Daily Flow state.
 *
 * This file intentionally does not publish StateFlow updates or write cache/Room.
 * State transitions that remember lastLive/lastFinal remain owned by LiveCollector.
 */

internal fun isDisplayableFeedForSchedule(
    feed: LiveFeedData,
    scheduleTime: LocalTime,
    today: LocalDate = currentYangonDate(),
): Boolean {
    val feedDate = canonicalDate(feed.date) ?: return false
    val cycleDate = dailyCycleDate(today, scheduleTime)

    // dailyCycleDate() already moves a working-day pre-09:30 start onto the
    // previous working day. Do not step back a second time there: that would
    // turn Tuesday morning's Monday hold into Friday, Wednesday morning's
    // Tuesday hold into Monday, and so on.
    //
    // From 09:30 until 11:30, the previous working day may still seed the
    // held display while today's LIVE cycle has not started. Once the
    // morning LIVE session starts, only today's feed may remain displayable.
    // Weekend hold is cycleDate (Friday) only.
    return when {
        !isWorkingDay(today) -> feedDate == cycleDate
        scheduleTime.isBefore(MORNING_REFERENCE) -> feedDate == cycleDate
        scheduleTime.isBefore(MORNING_LIVE) ->
            feedDate == cycleDate || feedDate == previousWorkingDay(cycleDate)
        else -> feedDate == cycleDate
    }
}

internal fun resolveLiveState(
    p: SourceObservation?,
    s: SourceObservation?,
    now: Instant,
    lastLive: LiveHeroSnapshot?,
    cachedFinal: LiveHeroSnapshot?,
    scheduleTime: LocalTime? = null,
    scheduleDate: LocalDate? = null,
    liveClosedDayDate: LocalDate? = null,
    primaryLiveSession: LiveSession? = null,
): LiveResolution {
    val effectiveScheduleTime = scheduleTime ?: now.atZone(YANGON).toLocalTime()
    val today = scheduleDate ?: currentYangonDate()
    val closedHoldDate = liveClosedDayDate
    val cycleDate = if (closedHoldDate != null) {
        previousWorkingDay(closedHoldDate)
    } else {
        dailyCycleDate(today, effectiveScheduleTime)
    }
    val previousWorking = previousWorkingDay(cycleDate)
    val feed = p?.feed?.takeIf {
        if (closedHoldDate != null) {
            canonicalDate(it.date) == cycleDate
        } else {
            isDisplayableFeedForSchedule(it, effectiveScheduleTime, today)
        }
    }
    val cycleFinal =
        feed?.takeIf { canonicalDate(it.date) == cycleDate }?.let(::latestFinalFor)
    val previousWorkingFinal =
        feed?.takeIf { canonicalDate(it.date) == previousWorking }?.let(::latestFinalFor)
    val cachedRelevantFinal = cachedFinal?.takeIf {
        val d = canonicalDate(it.date)
        when {
            closedHoldDate != null -> d == cycleDate
            !isWorkingDay(today) -> d == cycleDate
            effectiveScheduleTime.isBefore(MORNING_REFERENCE) -> d == cycleDate
            else -> d == cycleDate || d == previousWorking
        }
    }
    val heldFinal = cycleFinal ?: previousWorkingFinal ?: cachedRelevantFinal

    if (feed == null) {
        val activeSession = liveSessionForTime(effectiveScheduleTime)
        val currentSessionLive = lastLive?.takeIf {
            activeSession != null &&
                primaryLiveSession == activeSession &&
                canonicalDate(it.date) == cycleDate
        }
        if (currentSessionLive != null) {
            return LiveResolution(
                null,
                currentSessionLive,
                true,
                LiveStatus.LIVE_CONFIRMED,
                "",
                0,
                age(p),
            )
        }
        // Never substitute a previous-day final while an active LIVE/finalization
        // session is waiting for its first current-day Luke response.
        if (activeSession != null) {
            return LiveResolution(
                null,
                null,
                false,
                LiveStatus.WAITING,
                "",
                0,
                age(p),
            )
        }
        return LiveResolution(null, heldFinal, false, LiveStatus.WAITING, "", 0, age(p))
    }

    val activeLiveSession = liveSessionForTime(effectiveScheduleTime)
    val canShowLive =
        activeLiveSession != null &&
            liveValid(p, today) &&
            primaryLiveSession == activeLiveSession
    val morningFinal = finalValid(feed.morning, feed, today)
    val eveningFinal = finalValid(feed.evening, feed, today)

    fun liveHero(): LiveHeroSnapshot? =
        if (canShowLive) {
            LiveHeroSnapshot(
                result = feed.live,
                set = feed.liveSet,
                value = feed.liveVal,
                sessionLabel = feed.currentTime,
                date = feed.date,
            )
        } else {
            null
        }

    fun finalHero(): LiveHeroSnapshot? = latestFinalFor(feed)

    // The app clock controls the daily phase. Luke's date/current_time are
    // provider data only and must never advance the app into another phase.
    val phaseTime = effectiveScheduleTime

    // Saturday/Sunday and a Luke-confirmed Live-only closed day hold the
    // last working-day snapshot for the whole closed interval.
    if (closedHoldDate != null || !isWorkingDay(today)) {
        return LiveResolution(
            feed,
            heldFinal,
            false,
            LiveStatus.WAITING,
            "",
            1,
            age(p),
        )
    }

    return when {
        phaseTime.isBefore(MORNING_LIVE) -> {
            LiveResolution(
                feed,
                heldFinal,
                false,
                LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(MORNING_CLOSE) -> {
            // 11:30–12:01 is one continuous MORNING session. A current-day
            // final, when it appears early, wins immediately; otherwise only
            // this session's validated LIVE value may occupy the hero.
            val live = liveHero()
            val hero = if (morningFinal) finalHero() else live
            LiveResolution(
                feed,
                hero,
                !morningFinal && live != null,
                when {
                    morningFinal -> LiveStatus.FINAL_CONFIRMED
                    live != null -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(LocalTime.of(13, 0)) -> {
            // 12:01 onward remains the same morning LIVE session while Luke
            // is still finalizing. Never fall back to yesterday's/morning-held
            // final before today's 12:01 result is actually present.
            val live = liveHero()
            val hero = if (morningFinal) finalHero() else live
            LiveResolution(
                feed,
                hero,
                !morningFinal && live != null,
                when {
                    morningFinal -> LiveStatus.FINAL_CONFIRMED
                    live != null -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }

        phaseTime.isBefore(EVENING_LIVE) -> {
            // 13:00–16:00 is a frozen/reference phase. A one-time Luke
            // startup/reference response may still contain a live value, but
            // that value must never become the active LIVE hero outside the
            // scheduled evening LIVE window.
            val hero = finalHero() ?: heldFinal
            LiveResolution(
                feed,
                hero,
                false,
                if (hero != null) LiveStatus.FINAL_CONFIRMED else LiveStatus.WAITING,
                "",
                1,
                age(p),
            )
        }

        else -> {
            // 16:00–4:30 is one continuous EVENING session. Until today's
            // evening final exists, only this session's validated LIVE value
            // may occupy the hero; do not substitute the morning final or an
            // older held final on a cold start.
            val live = liveHero()
            val hero = if (eveningFinal) finalHero() else live
            LiveResolution(
                feed,
                hero,
                !eveningFinal && live != null,
                when {
                    eveningFinal -> LiveStatus.FINAL_CONFIRMED
                    live != null -> LiveStatus.LIVE_CONFIRMED
                    else -> LiveStatus.WAITING
                },
                "",
                1,
                age(p),
            )
        }
    }
}
/** Build the screen-facing state from an already-resolved display result. */
internal fun projectLiveUiState(
    resolution: LiveResolution,
    closedDay: Boolean,
): LiveUiState.Data = LiveUiState.Data(
    feed = resolution.displayFeed,
    hero = resolution.hero,
    heroLive = resolution.heroLive,
    stale = false,
    secondaryFeed = null,
    sourceMessage = "",
    status = resolution.status,
    staleAgeMs = resolution.staleAgeMs,
    closedDay = closedDay,
)

/**
 * Immutable reference overlay values for one display projection.
 * This snapshot is display-only; applying it never writes collector state, caches, or Room.
 */
internal data class LiveReferenceProjectionSnapshot(
    val today: LocalDate,
    val scheduleTime: LocalTime,
    val cycleDate: LocalDate,
    val referenceFeed: LiveFeedData?,
    val referenceFeedDate: LocalDate?,
    val closedHold: Boolean,
    val reference930: Pair<String, String>?,
    val reference930Date: LocalDate?,
    val reference930PendingDate: LocalDate?,
    val reference200: Pair<String, String>?,
    val reference200Date: LocalDate?,
    val reference200PendingDate: LocalDate?,
    val referenceResetDate: LocalDate?,
)

/**
 * Apply current-cycle references and Pending session cards to a display feed.
 * All mutable collector inputs are captured in [snapshot] before this pure projection runs.
 */
internal fun projectReferenceFeed(
    base: LiveFeedData?,
    snapshot: LiveReferenceProjectionSnapshot,
): LiveFeedData? {
    val today = snapshot.today
    val t = snapshot.scheduleTime
    val cycleDate = snapshot.cycleDate

    // Prefer a successful independent reference feed when the main poll has no baseline.
    val effectiveBase = base ?: snapshot.referenceFeed?.takeIf {
        snapshot.referenceFeedDate == cycleDate
    }

    if (effectiveBase == null) return null
    if (snapshot.closedHold) return effectiveBase

    var out = effectiveBase

    when {
        snapshot.reference930Date == cycleDate && snapshot.reference930 != null -> {
            val pair = snapshot.reference930!!
            out = out.copy(
                modern930 = pair.first,
                internet930 = pair.second,
            )
        }
        snapshot.reference930PendingDate == cycleDate -> {
            out = out.copy(
                modern930 = LIVE_PENDING,
                internet930 = LIVE_PENDING,
            )
        }
    }

    when {
        snapshot.reference200Date == cycleDate && snapshot.reference200 != null -> {
            val pair = snapshot.reference200!!
            out = out.copy(
                modern200 = pair.first,
                internet200 = pair.second,
            )
        }
        snapshot.reference200PendingDate == cycleDate -> {
            out = out.copy(
                modern200 = LIVE_PENDING,
                internet200 = LIVE_PENDING,
            )
        }
        isWorkingDay(today) &&
            !t.isBefore(MORNING_REFERENCE) &&
            snapshot.reference200Date != cycleDate -> {
            // The 14:00 slot belongs to the new cycle from 09:30, before its retry worker starts.
            out = out.copy(
                modern200 = LIVE_PENDING,
                internet200 = LIVE_PENDING,
            )
        }
    }

    val pendingSessions = LiveSessionData(
        LIVE_PENDING,
        LIVE_PENDING,
        LIVE_PENDING,
        false,
    )
    val workingToday = isWorkingDay(today)

    if (
        workingToday &&
        snapshot.referenceResetDate == cycleDate &&
        !t.isBefore(MORNING_REFERENCE) &&
        t.isBefore(MORNING_LIVE)
    ) {
        out = out.copy(
            morning = pendingSessions,
            evening = pendingSessions,
        )
    } else if (
        workingToday &&
        !t.isBefore(MORNING_LIVE) &&
        (
            t.isBefore(MORNING_CLOSE) ||
                !isDisplayFeedForDay(out, today)
        )
    ) {
        // 11:30 is a display reset only; the raw provider feed remains unchanged.
        out = out.copy(
            morning = pendingSessions,
            evening = pendingSessions,
        )
    }

    if (
        workingToday &&
        !t.isBefore(EVENING_LIVE) &&
        t.isBefore(EVENING_CLOSE) &&
        isDisplayFeedForDay(out, today)
    ) {
        // Preserve the morning final while the evening session is still pending.
        out = out.copy(evening = pendingSessions)
    }

    return out
}

private fun isDisplayFeedForDay(
    feed: LiveFeedData,
    date: LocalDate,
): Boolean = canonicalDate(feed.date) == date

/**
 * Build the synthetic Pending feed used only for display gaps.
 * The caller may apply a separately captured reference overlay afterward.
 */
internal fun projectPendingDisplayFeed(
    sourceFeed: LiveFeedData?,
    scheduleTime: LocalTime,
    today: LocalDate,
): LiveFeedData? {
    val inPendingWindow = scheduleTime >= MORNING_REFERENCE &&
        (
            scheduleTime.isBefore(MORNING_LIVE) ||
                (scheduleTime >= MORNING_LIVE && scheduleTime < MORNING_CATCHUP_END) ||
                (scheduleTime >= AFTERNOON_REFERENCE && scheduleTime < EVENING_CATCHUP_END)
            )
    if (!isWorkingDay(today) || !inPendingWindow) return null

    val pendingSession = LiveSessionData(
        LIVE_PENDING,
        LIVE_PENDING,
        LIVE_PENDING,
        false,
    )

    val source = sourceFeed ?: LiveFeedData(
        date = today.toString(),
        currentTime = scheduleTime.toString(),
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = pendingSession,
        evening = pendingSession,
        modern930 = LIVE_PENDING,
        internet930 = LIVE_PENDING,
        modern200 = LIVE_PENDING,
        internet200 = LIVE_PENDING,
        sourceTag = "LUKE",
        serverTimeEpochMs = null,
        isCloseDay = false,
    )

    // This projected copy never replaces primary, cache, Room history, or financial state.
    return source.copy(
        date = today.toString(),
        currentTime = scheduleTime.toString(),
        live = LIVE_PENDING,
        liveSet = LIVE_PENDING,
        liveVal = LIVE_PENDING,
        morning = pendingSession,
        evening = pendingSession,
    )
}
