package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Engine-only regression: longer dedup must remain checkpoint-size bounded. */
class CherryV31FingerprintWindowTest {
    private val target = DrawIdentity(
        LocalDate.of(2026, 11, 17), DrawSession.MORNING)
    private val signature = "v31-history-locked"

    @Test fun fourThousandNinetySixCompactFingerprintsRoundTripWithinCheckpointBudget() {
        val keys = (0 until CherryV2StreakRegistry.BEHAVIOR_WINDOW).map { i ->
            i.toString(16).padStart(32, '0')
        }
        val original = CherryV2StreakRegistry(
            target, CherryDiscoveryKind.BREAK, signature)
            .withRecentBehaviors(keys)
        val encoded = CherryV2CheckpointCodec.encode(original)
        val decoded = CherryV2CheckpointCodec.decode(
            encoded, target, CherryDiscoveryKind.BREAK, signature)
        assertEquals(original, decoded)
        assertEquals(4096, decoded!!.recentBehaviorKeys.size)
        assertTrue("A full checkpoint must fit within the 256KB raw format",
            encoded.length < 256_000 * 2)
    }

    @Test fun duplicateFingerprintAndUnboundedWindowAreRejected() {
        val initial = CherryV2StreakRegistry(
            target, CherryDiscoveryKind.LONE, signature)
        val fingerprint = "0123456789abcdef0123456789abcdef"
        try {
            initial.withRecentBehaviors(listOf(fingerprint, fingerprint))
            fail("Repeated fingerprint must not be accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            initial.withRecentBehaviors(
                (0..CherryV2StreakRegistry.BEHAVIOR_WINDOW).map {
                    it.toString(16).padStart(32, '0')
                })
            fail("Unlimited fingerprint set must not be accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun v31UsesNewScheduleVersionWithoutTouchingUi() {
        assertEquals(4, CherryV2GuidedSchedule.VERSION)
        assertEquals(4, CherryV2ExpandableGrammar.VERSION)
        assertEquals(10, CherryV2StreakRegistry.MAX_TARGET_STREAK)
        assertEquals(30_000_000_000L, CherryV2TimedSearch.DEFAULT_BUDGET_NANOS)
    }
}
