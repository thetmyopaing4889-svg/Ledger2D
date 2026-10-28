package com.myanmar.ledger2d.feature.live

import java.time.LocalDate
import java.time.LocalTime

/**
 * A display-only guess captured at the close of a draw (or on a late first entry).
 * This is never a Luke final result and must never enter the Room winner pipeline.
 */
internal data class LiveTemporaryWinner(
    val date: LocalDate,
    val session: LiveSession,
    val hero: LiveHeroSnapshot,
    /** Luke observation time; only pre-close candidates compete by this value. */
    val sourceAtMs: Long = 0L,
)

internal object LiveTemporaryWinnerPolicy {
    private fun close(session: LiveSession): LocalTime =
        if (session == LiveSession.MORNING) MORNING_CLOSE else EVENING_CLOSE

    private fun start(session: LiveSession): LocalTime =
        if (session == LiveSession.MORNING) MORNING_LIVE else EVENING_LIVE

    private fun lateObservationEnd(session: LiveSession): LocalTime =
        if (session == LiveSession.MORNING) MORNING_CATCHUP_END else EVENING_CATCHUP_END

    /**
     * Unlike the polling catch-up window, a frozen hero can stay visible until
     * the next live session (or through the rest of the evening).
     */
    fun frozenSessionAt(now: LocalTime): LiveSession? = when {
        now >= EVENING_CLOSE -> LiveSession.EVENING
        now >= MORNING_CLOSE && now < EVENING_LIVE -> LiveSession.MORNING
        else -> null
    }

    fun sessionFinalConfirmed(
        feed: LiveFeedData?,
        date: LocalDate,
        session: LiveSession,
    ): Boolean = when (session) {
        LiveSession.MORNING -> finalValid(feed?.morning, feed, date)
        LiveSession.EVENING -> finalValid(feed?.evening, feed, date)
    }

    /** Validates the raw Luke response before the main reducer masks late LIVE. */
    fun candidate(
        feed: LiveFeedData,
        date: LocalDate,
        session: LiveSession,
    ): LiveTemporaryWinner? {
        if (feed.isCloseDay || canonicalDate(feed.date) != date || !isValidLive2d(feed.live)) {
            return null
        }
        val observed = parseDecisionInstant(feed)?.atZone(YANGON) ?: return null
        if (observed.toLocalDate() != date) return null
        val sourceTime = observed.toLocalTime()
        if (sourceTime < start(session) || sourceTime >= lateObservationEnd(session)) return null
        return LiveTemporaryWinner(
            date = date,
            session = session,
            sourceAtMs = observed.toInstant().toEpochMilli(),
            hero = LiveHeroSnapshot(
                result = feed.live,
                set = feed.liveSet,
                value = feed.liveVal,
                sessionLabel = if (session == LiveSession.MORNING) {
                    LIVE_SESSION_MORNING_LABEL
                } else {
                    LIVE_SESSION_EVENING_LABEL
                },
                date = date.toString(),
            ),
        )
    }

    /**
     * Never replace an already captured temporary number with a later LIVE tick.
     * A confirmed result always suppresses the temporary display instead.
     * A late first entrant can use one valid current-session raw LIVE response.
     */
    fun select(
        existing: LiveTemporaryWinner?,
        preClose: LiveTemporaryWinner?,
        incoming: LiveFeedData?,
        today: LocalDate,
        now: LocalTime,
        finalConfirmed: Boolean,
    ): LiveTemporaryWinner? {
        if (!isWorkingDay(today) || finalConfirmed) return null
        val session = frozenSessionAt(now) ?: return null
        fun matching(value: LiveTemporaryWinner?) = value?.takeIf {
            it.date == today && it.session == session && isValidLive2d(it.hero.result)
        }
        return matching(existing)
            ?: matching(preClose)
            ?: incoming?.let { candidate(it, today, session) }
    }

    fun isBeforeSessionClose(now: LocalTime, session: LiveSession): Boolean =
        now >= start(session) && now < close(session)
}
