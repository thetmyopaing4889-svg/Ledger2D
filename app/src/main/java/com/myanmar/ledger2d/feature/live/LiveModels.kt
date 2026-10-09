package com.myanmar.ledger2d.feature.live

// In-memory models shared by Daily Flow, the LIVE screen, and the existing cache integration.
// Keep fields and defaults aligned with the baseline; these types are not Room entities.

/** Full live feed snapshot. */
data class LiveFeedData(
    val date: String,
    val currentTime: String,
    /** LIVE 2D value currently streaming. */
    val live: String,
    /** LIVE SET / VALUE accompanying the streaming 2D. */
    val liveSet: String,
    val liveVal: String,
    /** Finalized-or-pending 12:01 morning session. */
    val morning: LiveSessionData,
    /** Finalized-or-pending 4:30 evening session. */
    val evening: LiveSessionData,
    /** 09:30 reference values. */
    val modern930: String,
    val internet930: String,
    /** 02:00 reference values. */
    val modern200: String,
    val internet200: String,
    val sourceTag: String = "LUKE",
    val serverTimeEpochMs: Long? = null,
    /** Luke's current-day market-closed observation; never stored in Ledger Room. */
    val isCloseDay: Boolean = false,
)

/** One result session (12:01 morning / 4:30 evening). */
data class LiveSessionData(
    val result: String,
    val set: String,
    val value: String,
    /** True only when the verified result field is no longer "--". */
    val finalized: Boolean,
    val historyId: String? = null,
    val providerOpenTime: String? = null,
)

/**
 * Explicit UI states for the LIVE screen. The hero snapshot is carried
 * separately from the raw feed so the top hero can keep showing the latest
 * known value even when today's feed is empty (new day before 09:30 data).
 */
sealed interface LiveUiState {
    /** First fetch has not completed yet (or no cached hero exists). */
    data object Loading : LiveUiState

    /** Latest known state (feed may be null until the first successful fetch). */
    data class Data(
        val feed: LiveFeedData?,
        val hero: LiveHeroSnapshot?,
        val heroLive: Boolean,
        val stale: Boolean,
        val secondaryFeed: LiveFeedData? = null,
        val sourceMessage: String = "",
        val status: LiveStatus = LiveStatus.WAITING,
        val staleAgeMs: Long = 0L,
        /** True only when Luke has confirmed today's 2D market is closed. */
        val closedDay: Boolean = false,
    ) : LiveUiState

    /** No data yet and the latest fetch failed. */
    data class Error(val retrying: Boolean) : LiveUiState
}

// Shared LIVE display and state models/constants. These are not Room entities.

internal const val LIVE_PENDING = "--"
internal const val LIVE_SESSION_MORNING_LABEL = "12:01 PM"
internal const val LIVE_SESSION_EVENING_LABEL = "4:30 PM"

data class LiveHeroSnapshot(
    val result: String,
    val set: String,
    val value: String,
    val sessionLabel: String,
    val date: String,
)

enum class LiveStatus {
    WAITING,
    LIVE_CONFIRMED,
    LIVE_DEGRADED,
    WAITING_FOR_ALIGNMENT,
    LIVE_CONFLICT,
    STALE,
    FINALIZING,
    WAITING_FOR_PRIMARY,
    DRAW_FREEZE,
    RESULT_AVAILABLE,
    FINAL_CONFIRMED,
    DEGRADED_FINAL,
    FINAL_CONFLICT,
    STALE_PRIMARY,
}

internal data class SourceObservation(
    val feed: LiveFeedData,
    val fetchedAtElapsedMs: Long,
    val requestStartedElapsedMs: Long,
    val roundTripMs: Long,
)

internal data class LiveResolution(
    val displayFeed: LiveFeedData?,
    val hero: LiveHeroSnapshot?,
    val heroLive: Boolean,
    val status: LiveStatus,
    val message: String,
    val sourceCount: Int,
    val staleAgeMs: Long,
)
