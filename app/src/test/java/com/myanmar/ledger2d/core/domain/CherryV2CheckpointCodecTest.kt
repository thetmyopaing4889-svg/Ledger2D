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

    @Test fun previouslySavedV2ScheduleCannotBeResumedAsV3Formulas() {
        val current = CherryV2CheckpointCodec.encode(sample())
        val parts = current.removePrefix("CHERRY-V2:").split(':')
        val bytes = java.util.Base64.getUrlDecoder().decode(parts[0])
        val stream = java.io.ByteArrayInputStream(bytes)
        val input = java.io.DataInputStream(stream)
        input.readInt() // magic
        input.readInt() // codec format
        repeat(5) { input.readUTF() } // target date/session, kind, hash, cursor
        val grammarOffset = bytes.size - stream.available()
        // Simulate a correctly checksummed, incompatible V2 schedule cursor.
        java.nio.ByteBuffer.wrap(bytes, grammarOffset, 4).putInt(2)
        val checksum = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") {
                (it.toInt() and 255).toString(16).padStart(2, '0')
            }
        val savedV2 = "CHERRY-V2:" +
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytes) + ":" + checksum
        assertNull("V2 schedule slots must never decode as V3 meanings",
            CherryV2CheckpointCodec.decode(
                savedV2, target, kind, signature))
        // The current grammar checkpoint must still decode normally.
        assertEquals(sample(), CherryV2CheckpointCodec.decode(
            current, target, kind, signature))
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
