package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

class CherryV2StreakRegistryTest {
    private val target = DrawIdentity(LocalDate.parse("2026-11-16"), DrawSession.MORNING)
    private fun i(n: Long) = BigInteger.valueOf(n)
    private fun state() = CherryV2StreakRegistry(target, CherryDiscoveryKind.LONE, "history-1")
    private fun match(index: Long, streak: Int, kind: CherryDiscoveryKind = CherryDiscoveryKind.LONE) =
        CherryV2StreakMatch(target, kind, "history-1", i(index),
            "formula$index", index.toInt() % 10, streak)

    @Test fun firstFoundPerExactStreakCanJumpStraightToTenAndKeepLaterFour() {
        val first = state().inspected(i(0), match(0, 3))
        val repeatedThree = first.inspected(i(1), match(1, 3))
        assertEquals(first.firstByStreak, repeatedThree.firstByStreak)
        val immediateTen = repeatedThree.inspected(i(2), match(2, 10))
        assertEquals(10, immediateTen.recommended()!!.historicalStreak)
        val fourAfterTen = immediateTen.inspected(i(3), match(3, 4))
        val sameTen = fourAfterTen.inspected(i(4), match(4, 10))
        assertEquals(listOf(3, 4, 10), sameTen.orderedResults().map { it.historicalStreak })
        assertEquals(i(5), sameTen.nextIndex)
        assertEquals(i(2), sameTen.recommended()!!.formulaIndex)
        assertEquals(i(0), sameTen.firstByStreak[3]!!.formulaIndex)
        assertEquals(i(0), first.firstByStreak[3]!!.formulaIndex)
    }

    @Test fun firstTenPlusHitWinsAndTheEightVisibleTiersNeverExpandIntoHundreds() {
        var saved = state()
        // Index 0 already fits 15: by agreement this qualifies as the
        // FIRST 10+ milestone, not a separate 15th visible result.
        saved = saved.inspected(i(0), match(0, 15))
        assertEquals(listOf(10), saved.orderedResults().map {
            it.historicalStreak
        })
        assertEquals(i(0), saved.recommended()!!.formulaIndex)
        saved = saved.inspected(i(1), match(1, 10))
        saved = saved.inspected(i(2), match(2, 12))
        assertEquals(i(0), saved.recommended()!!.formulaIndex)
        for (streak in 3..9) {
            saved = saved.inspected(i(streak.toLong()), match(streak.toLong(), streak))
        }
        assertEquals((3..10).toList(),
            saved.orderedResults().map { it.historicalStreak })
        assertEquals(8, saved.firstByStreak.size)
        assertEquals(8, saved.orderedResults().size)
        assertEquals(i(0), saved.recommended()!!.formulaIndex)
    }

    @Test fun oldVersionTwoExactStreaksRemainReadableWithoutChangingSavedCheckpoint() {
        // Pre-cap V2 used to store 10, 11, 12... as distinct historical
        // lengths. Preserve the original immutable state, but summarize
        // it using earliest discovered 10+ as the user-facing milestone.
        val old = CherryV2StreakRegistry(
            target, CherryDiscoveryKind.LONE, "history-1",
            nextIndex = i(5),
            firstByStreak = mapOf(
                15 to match(0, 15),
                11 to match(1, 11),
                10 to match(3, 10),
            ),
        )
        assertEquals(3, old.firstByStreak.size)
        assertEquals(10, old.recommended()!!.historicalStreak)
        assertEquals(i(0), old.recommended()!!.formulaIndex)
        assertEquals(listOf(10), old.orderedResults().map {
            it.historicalStreak
        })
        val continued = old.inspected(i(5), match(5, 19))
        assertEquals(old.firstByStreak, continued.firstByStreak)
    }

    @Test fun missesAndShortFitsAdvanceCursorWithoutPollutingResults() {
        val noHit = state().inspected(i(0), null)
        val twoHits = noHit.inspected(i(1), match(1, 2))
        assertTrue(twoHits.firstByStreak.isEmpty())
        assertEquals(i(2), twoHits.nextIndex)
    }

    @Test fun millionsOfMissesDoNotCopySavedStreakMap() {
        val initial = state().inspected(i(0), match(0, 3))
        val retained = initial.firstByStreak
        var cursor = initial
        repeat(5000) {
            cursor = cursor.inspectedBatch(cursor.nextIndex, emptyList())
            assertSame("No-fit advances must reuse the immutable first-found map",
                retained, cursor.firstByStreak)
        }
        assertEquals(i(5001), cursor.nextIndex)
        assertEquals(initial.firstByStreak, cursor.firstByStreak)
        assertEquals(i(0), cursor.firstByStreak.getValue(3).formulaIndex)
    }

    @Test fun refusesStaleCursorCrossCategoryOrHistoryMismatch() {
        val first = state().inspected(i(0), match(0, 5))
        try {
            first.inspected(i(0), match(0, 8))
            fail("Repeated index must not silently advance")
        } catch (_: IllegalArgumentException) {}
        try {
            first.inspected(i(1), match(1, 6, CherryDiscoveryKind.WAM))
            fail("Cross-category match must be rejected")
        } catch (_: IllegalArgumentException) {}
        try {
            first.inspected(i(1), match(1, 7).copy(historySignature = "changed"))
            fail("Corrected history must not reuse this cursor")
        } catch (_: IllegalArgumentException) {}
        assertEquals(listOf(5), first.orderedResults().map { it.historicalStreak })
    }

    @Test fun separateCategoryAndTargetStateCannotShareFirstFound() {
        val lone = state().inspected(i(0), match(0, 6))
        val wam = CherryV2StreakRegistry(target, CherryDiscoveryKind.WAM, "history-1")
        assertTrue(wam.firstByStreak.isEmpty())
        assertEquals(6, lone.recommended()!!.historicalStreak)
        val otherTarget = CherryV2StreakRegistry(
            DrawIdentity(LocalDate.parse("2026-11-16"), DrawSession.EVENING),
            CherryDiscoveryKind.LONE, "history-1")
        assertTrue(otherTarget.firstByStreak.isEmpty())
    }
}
