package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

class CherryV2CheckpointCodecTest {
    private val target = DrawIdentity(LocalDate.parse("2026-11-16"),
        DrawSession.MORNING)
    private val kind = CherryDiscoveryKind.LONE
    private val signature = "fixture-history-signature"

    private fun sample(): CherryV2StreakRegistry {
        fun formula(n: Long, streak: Int, offset: Int) =
            CherryV2StreakMatch(target, kind, signature, BigInteger.valueOf(n),
                "expr$n", n.toInt() % 10, streak, offset)
        return CherryV2StreakRegistry(target, kind, signature)
            .inspectedBatch(BigInteger.ZERO, listOf(formula(0, 3, 0)))
            .inspectedBatch(BigInteger.ONE, listOf(formula(1, 10, 5)))
            .withRecentBehaviors(listOf("18:103", "18:209"))
    }

    @Test fun roundTripRecoversExactCursorFirstFoundAndRecentDedupWindow() {
        val original = sample()
        val saved = CherryV2CheckpointCodec.encode(original)
        val restored = CherryV2CheckpointCodec.decode(
            saved, target, kind, signature)
        assertNotNull(restored)
        assertEquals(original, restored)
        assertEquals(saved, CherryV2CheckpointCodec.encode(restored!!))
        assertEquals(BigInteger.valueOf(2), restored.nextIndex)
        assertEquals(10, restored.recommended()!!.historicalStreak)
        assertEquals("v3:1+5@LONE", restored.recommended()!!.formulaId)
        assertEquals(listOf("18:103", "18:209"), restored.recentBehaviorKeys)
    }

    @Test fun doesNotResumeWrongKindTargetOrRevisedSourceHistory() {
        val saved = CherryV2CheckpointCodec.encode(sample())
        assertNull(CherryV2CheckpointCodec.decode(
            saved, target, CherryDiscoveryKind.WAM, signature))
        assertNull(CherryV2CheckpointCodec.decode(
            saved, DrawIdentity(target.date, DrawSession.EVENING),
            kind, signature))
        assertNull(CherryV2CheckpointCodec.decode(
            saved, target, kind, "new-winner-correction"))
    }

    @Test fun tamperingCorruptionAndOverlongPayloadsFailClosed() {
        val saved = CherryV2CheckpointCodec.encode(sample())
        val middle = saved.length / 3
        val swapped = if (saved[middle] == 'A') 'B' else 'A'
        val damaged = saved.replaceRange(middle, middle + 1, "$swapped")
        assertNull(CherryV2CheckpointCodec.decode(
            damaged, target, kind, signature))
        assertNull(CherryV2CheckpointCodec.decode(
            "CHERRY-V2:garbage:bad", target, kind, signature))
        assertNull(CherryV2CheckpointCodec.decode(
            "not a checkpoint", target, kind, signature))
        assertNull(CherryV2CheckpointCodec.decode(
            "x".repeat(520_000), target, kind, signature))
    }

    @Test fun emptyCheckpointIsValidAndDoesNotInventFoundFormulas() {
        val original = CherryV2StreakRegistry(target, CherryDiscoveryKind.BREAK,
            signature)
        val restored = CherryV2CheckpointCodec.decode(
            CherryV2CheckpointCodec.encode(original), target,
            CherryDiscoveryKind.BREAK, signature)
        assertEquals(original, restored)
        assertTrue(restored!!.orderedResults().isEmpty())
    }
}
