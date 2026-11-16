package com.myanmar.ledger2d.core.repository

import android.app.Application
import android.content.Context
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryV2StreakMatch
import com.myanmar.ledger2d.core.domain.CherryV2StreakRegistry
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigInteger
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class CherryV2CheckpointStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val target = DrawIdentity(LocalDate.parse("2026-11-16"),
        DrawSession.MORNING)
    private val signature = "historical-hash-a"
    private fun prefs() = context.getSharedPreferences(
        "cherry_v2_discovery_cursors_v3", Context.MODE_PRIVATE)

    @Before fun prepare() { assertTrue(prefs().edit().clear().commit()) }
    @After fun cleanup() { prefs().edit().clear().commit() }

    private fun start(kind: CherryDiscoveryKind = CherryDiscoveryKind.LONE,
        hash: String = signature): CherryV2StreakRegistry =
        CherryV2StreakRegistry(target, kind, hash)

    private fun candidate(index: Long, streak: Int,
        kind: CherryDiscoveryKind = CherryDiscoveryKind.LONE,
        hash: String = signature) = CherryV2StreakMatch(
        target, kind, hash, BigInteger.valueOf(index),
        "formula$index", 4, streak, offset = 1,
    )

    @Test fun separateStoreInstancesResumeExactCursorAfterRestart() {
        val store = CherryV2CheckpointStore(context)
        val first = start().inspected(BigInteger.ZERO, candidate(0, 3))
        val second = first.inspected(BigInteger.ONE, candidate(1, 10))
            .withRecentBehaviors(listOf("12:1234", "12:5678"))
        store.persist(first)
        store.persist(second)
        val reopened = CherryV2CheckpointStore(context)
        assertEquals(second, reopened.resume(
            CherryDiscoveryKind.LONE, target, signature))
        assertEquals(10, reopened.resume(CherryDiscoveryKind.LONE,
            target, signature)!!.recommended()!!.historicalStreak)
        assertEquals(BigInteger.valueOf(2),
            reopened.resume(CherryDiscoveryKind.LONE,
                target, signature)!!.nextIndex)
    }

    @Test fun staleWriterAndSameIndexReplacementCannotEraseFirstFormula() {
        val old = start().inspected(BigInteger.ZERO, candidate(0, 3))
        val upgraded = old.inspected(BigInteger.ONE, candidate(1, 10))
        val firstStore = CherryV2CheckpointStore(context)
        val secondStore = CherryV2CheckpointStore(context)
        firstStore.persist(old)
        secondStore.persist(upgraded)
        try {
            firstStore.persist(old)
            fail("Older cursor must not overwrite newer state")
        } catch (_: IllegalArgumentException) {}

        val forged = upgraded.copy(firstByStreak =
            upgraded.firstByStreak + (3 to candidate(1, 3)))
        try {
            secondStore.persist(forged)
            fail("Earlier first-found candidate must not be replaced")
        } catch (_: IllegalArgumentException) {}
        assertEquals(upgraded, firstStore.resume(
            CherryDiscoveryKind.LONE, target, signature))
    }

    @Test fun correctedHistoryInvalidatesCursorAndCanStartFreshWithoutReusing() {
        val store = CherryV2CheckpointStore(context)
        val initial = start().inspected(BigInteger.ZERO, candidate(0, 3))
        store.persist(initial)
        assertNull(store.resume(CherryDiscoveryKind.LONE, target, "corrected"))
        val revised = start(hash = "corrected").inspected(
            BigInteger.ZERO, candidate(0, 4, hash = "corrected"))
        store.persist(revised)
        assertNull(store.resume(CherryDiscoveryKind.LONE, target, signature))
        assertEquals(revised, store.resume(
            CherryDiscoveryKind.LONE, target, "corrected"))
    }

    @Test fun categoriesAreIsolatedAndNoFormulaIsAutoSelected() {
        val store = CherryV2CheckpointStore(context)
        val lone = start().inspected(BigInteger.ZERO, candidate(0, 3))
        val breakKind = CherryDiscoveryKind.BREAK
        val breakState = start(breakKind).inspected(
            BigInteger.ZERO, candidate(0, 5, kind = breakKind))
        store.persist(lone)
        store.persist(breakState)
        assertEquals(lone, store.resume(CherryDiscoveryKind.LONE,
            target, signature))
        assertEquals(breakState, store.resume(breakKind, target, signature))
        assertNull(store.resume(CherryDiscoveryKind.WAM, target, signature))
    }

    @Test fun corruptedCheckpointCannotBeSilentlyReplaced() {
        val store = CherryV2CheckpointStore(context)
        val good = start().inspected(BigInteger.ZERO, candidate(0, 3))
        store.persist(good)
        assertTrue(prefs().edit().putString("cursor_LONE", "corrupt").commit())
        try {
            store.resume(CherryDiscoveryKind.LONE, target, signature)
            fail("A corrupted checkpoint must be rejected")
        } catch (_: IllegalStateException) {}
        try {
            store.persist(good)
            fail("Cannot silently erase an unreadable previous checkpoint")
        } catch (_: IllegalStateException) {}
    }
}
