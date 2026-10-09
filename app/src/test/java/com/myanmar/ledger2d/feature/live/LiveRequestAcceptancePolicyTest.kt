package com.myanmar.ledger2d.feature.live

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRequestAcceptancePolicyTest {
    private val earlier = Instant.parse("2026-10-05T04:00:00Z")
    private val same = Instant.parse("2026-10-05T04:00:03Z")
    private val later = Instant.parse("2026-10-05T04:00:06Z")

    @Test
    fun provider_timestamp_wins_over_request_sequence() {
        assertTrue(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = later,
                previousTime = same,
                sequence = 2L,
                latestAppliedSequence = 10L,
            ),
        )
        assertFalse(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = earlier,
                previousTime = same,
                sequence = 20L,
                latestAppliedSequence = 10L,
            ),
        )
    }

    @Test
    fun equal_provider_timestamp_uses_sequence_as_tie_breaker() {
        assertFalse(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = same,
                previousTime = same,
                sequence = 10L,
                latestAppliedSequence = 10L,
            ),
        )
        assertFalse(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = same,
                previousTime = same,
                sequence = 9L,
                latestAppliedSequence = 10L,
            ),
        )
        assertTrue(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = same,
                previousTime = same,
                sequence = 11L,
                latestAppliedSequence = 10L,
            ),
        )
    }

    @Test
    fun unknown_incoming_timestamp_cannot_replace_known_previous_timestamp() {
        assertFalse(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = null,
                previousTime = same,
                sequence = 100L,
                latestAppliedSequence = 10L,
            ),
        )
    }

    @Test
    fun when_both_timestamps_are_unknown_sequence_controls_acceptance() {
        assertFalse(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = null,
                previousTime = null,
                sequence = 10L,
                latestAppliedSequence = 10L,
            ),
        )
        assertTrue(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = null,
                previousTime = null,
                sequence = 11L,
                latestAppliedSequence = 10L,
            ),
        )
    }

    @Test
    fun timestamped_incoming_is_accepted_when_previous_has_no_timestamp() {
        // The baseline treats provider time as authoritative when there is no comparable prior time.
        assertTrue(
            shouldAcceptLiveSnapshotByOrdering(
                incomingTime = earlier,
                previousTime = null,
                sequence = 1L,
                latestAppliedSequence = 10L,
            ),
        )
    }
}
