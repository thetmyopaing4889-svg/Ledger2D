package com.myanmar.ledger2d.feature.live

/**
 * Immutable completion event for a successful Luke LIVE request.
 *
 * Fetch failures and null responses intentionally remain no-ops, matching the
 * existing behavior. A successful event carries the request sequence and
 * monotonic timing needed by the state-acceptance rules.
 */
internal data class LiveRequestResultEvent(
    val sequence: Long,
    val feed: LiveFeedData,
    val requestStartedAtElapsedMs: Long,
    val requestFinishedAtElapsedMs: Long,
)
