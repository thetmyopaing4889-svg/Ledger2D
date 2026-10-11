package com.myanmar.ledger2d.feature.live

import java.time.Instant

/**
 * Pure request-ordering decision extracted from the collector's serialized state commit.
 *
 * When both provider timestamps exist, provider time wins and sequence is only a tie-breaker.
 * A timestamped prior snapshot cannot be replaced by an incoming snapshot with unknown time.
 * The remaining unknown-time case uses request sequence, preserving the baseline acceptance rules.
 */
internal fun shouldAcceptLiveSnapshotByOrdering(
    incomingTime: Instant?,
    previousTime: Instant?,
    sequence: Long,
    latestAppliedSequence: Long,
): Boolean {
    if (incomingTime != null && previousTime != null) {
        return when {
            incomingTime.isBefore(previousTime) -> false
            incomingTime == previousTime && sequence <= latestAppliedSequence -> false
            else -> true
        }
    }

    if (incomingTime == null && previousTime != null) return false
    if (incomingTime == null && sequence <= latestAppliedSequence) return false

    return true
}
