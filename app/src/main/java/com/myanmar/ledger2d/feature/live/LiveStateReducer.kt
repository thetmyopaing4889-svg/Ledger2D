package com.myanmar.ledger2d.feature.live

import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure transition policy for an accepted normal LIVE response.
 *
 * It decides which parts of the incoming snapshot are allowed to replace the
 * current session data. It does not fetch data, mutate collector state, publish
 * UI state, or perform cache/Room writes; LiveCollector commits the returned
 * transition and then performs its existing side effects in the same order.
 */
internal data class LiveStateReduction(
    val feed: LiveFeedData,
    val primaryLiveSession: LiveSession?,
)


internal data class LiveStartupRecoveryReduction(
    val primary: SourceObservation?,
    val lastFinal: LiveHeroSnapshot?,
    val references: LiveReferenceState,
)

internal data class LiveClosedDayReduction(
    val closedDayDate: LocalDate,
    val primary: SourceObservation?,
    val lastLive: LiveHeroSnapshot?,
    val lastFinal: LiveHeroSnapshot?,
    val references: LiveReferenceState,
)

internal object LiveStateReducer {
    fun reduceAcceptedLiveSnapshot(
        previous: LiveFeedData?,
        incoming: LiveFeedData,
        scheduleDate: LocalDate,
        scheduleTime: LocalTime,
        previousPrimaryLiveSession: LiveSession?,
    ): LiveStateReduction {
        val protectedFinals = protectFinalSessions(previous, incoming, scheduleDate)
        val protectedFeed = preserveActiveLive(
            previous = previous,
            incoming = protectedFinals,
            scheduleDate = scheduleDate,
            scheduleTime = scheduleTime,
            previousPrimaryLiveSession = previousPrimaryLiveSession,
        )

        val activeSession = liveSessionForTime(scheduleTime)
        val nextPrimaryLiveSession = when {
            activeSession != null && hasValidLive(protectedFeed, scheduleDate) -> activeSession
            activeSession != null -> null
            else -> previousPrimaryLiveSession
        }

        return LiveStateReduction(
            feed = protectedFeed,
            primaryLiveSession = nextPrimaryLiveSession,
        )
    }

    /**
     * Decide the state changes for a Luke-confirmed Closed Day before the
     * collector commits them and runs its existing persistence/UI effects.
     */

    /**
     * Resolve only the in-memory state transition for a completed cold-start
     * recovery. Fetching history stays in LiveCollector; this method performs
     * no I/O, cache writes, Room writes, or UI publication.
     */
    fun reduceStartupRecovery(
        today: LocalDate,
        scheduleTime: LocalTime,
        cycleDate: LocalDate,
        recoveredFeed: LiveFeedData?,
        currentFinal: LiveHeroSnapshot?,
        fallbackFinal: LiveHeroSnapshot?,
        previousPrimary: SourceObservation?,
        previousLastFinal: LiveHeroSnapshot?,
        references: LiveReferenceState,
        elapsedRealtimeMs: Long,
    ): LiveStartupRecoveryReduction {
        var nextLastFinal = currentFinal ?: fallbackFinal ?: previousLastFinal
        var nextPrimary = previousPrimary
        var nextReferences = references

        if (recoveredFeed != null) {
            nextPrimary = SourceObservation(
                feed = recoveredFeed,
                fetchedAtElapsedMs = elapsedRealtimeMs,
                requestStartedElapsedMs = elapsedRealtimeMs,
                roundTripMs = 0L,
            )

            val recoveredDate = canonicalDate(recoveredFeed.date)
            val completedHeldCycle =
                recoveredDate == cycleDate &&
                    (cycleDate != today || !scheduleTime.isBefore(EVENING_CLOSE))

            if (completedHeldCycle) {
                nextReferences = LiveReferenceStateReducer.restoreCompletedHeldCycle(
                    state = nextReferences,
                    feed = recoveredFeed,
                    cycleDate = cycleDate,
                )
            }

            latestFinalFor(recoveredFeed)?.let { historicalFinal ->
                if (
                    currentFinal == null &&
                    canonicalDate(historicalFinal.date) == recoveredDate
                ) {
                    nextLastFinal = historicalFinal
                }
            }
        }

        return LiveStartupRecoveryReduction(
            primary = nextPrimary,
            lastFinal = nextLastFinal,
            references = nextReferences,
        )
    }

    fun reduceConfirmedClosedDay(
        cycleDate: LocalDate,
        heldFeed: LiveFeedData?,
        elapsedRealtimeMs: Long,
        previousPrimary: SourceObservation?,
        previousLastLive: LiveHeroSnapshot?,
        previousLastFinal: LiveHeroSnapshot?,
        references: LiveReferenceState,
    ): LiveClosedDayReduction {
        val nextPrimary = heldFeed?.let {
            SourceObservation(
                feed = it,
                fetchedAtElapsedMs = elapsedRealtimeMs,
                requestStartedElapsedMs = elapsedRealtimeMs,
                roundTripMs = 0L,
            )
        } ?: previousPrimary
        val nextLastFinal = heldFeed?.let(::latestFinalFor) ?: previousLastFinal

        return LiveClosedDayReduction(
            closedDayDate = cycleDate,
            primary = nextPrimary,
            lastLive = null,
            lastFinal = nextLastFinal,
            references = LiveReferenceStateReducer.clearForClosedDay(references),
        )
    }

    fun inferLiveSessionFromSourceTime(feed: LiveFeedData): LiveSession? {
        val sourceTime = parseDecisionInstant(feed)
            ?.atZone(YANGON)
            ?.toLocalTime()
            ?: return null

        return when {
            sourceTime >= MORNING_LIVE && sourceTime < MORNING_CATCHUP_END ->
                LiveSession.MORNING
            sourceTime >= EVENING_LIVE && sourceTime < EVENING_CATCHUP_END ->
                LiveSession.EVENING
            else -> null
        }
    }

    private fun protectFinalSessions(
        previous: LiveFeedData?,
        incoming: LiveFeedData,
        scheduleDate: LocalDate,
    ): LiveFeedData {
        if (
            previous == null ||
            canonicalDate(previous.date) != scheduleDate ||
            canonicalDate(incoming.date) != scheduleDate
        ) {
            return incoming
        }

        return incoming.copy(
            morning = if (previous.morning.finalized && !incoming.morning.finalized) {
                previous.morning
            } else {
                incoming.morning
            },
            evening = if (previous.evening.finalized && !incoming.evening.finalized) {
                previous.evening
            } else {
                incoming.evening
            },
        )
    }

    private fun preserveActiveLive(
        previous: LiveFeedData?,
        incoming: LiveFeedData,
        scheduleDate: LocalDate,
        scheduleTime: LocalTime,
        previousPrimaryLiveSession: LiveSession?,
    ): LiveFeedData {
        val activeSession = liveSessionForTime(scheduleTime) ?: return incoming
        if (
            previousPrimaryLiveSession != activeSession ||
            !hasValidLive(previous, scheduleDate)
        ) {
            return incoming
        }
        if (hasValidLive(incoming, scheduleDate)) return incoming

        return incoming.copy(
            live = previous!!.live,
            liveSet = previous.liveSet,
            liveVal = previous.liveVal,
        )
    }
}
