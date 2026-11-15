package com.myanmar.ledger2d.core.repository

import android.app.Application
import android.content.Context
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryDiscoverySearchMore
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.domain.HistoricDraw
import com.myanmar.ledger2d.core.domain.HistoricSource
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
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
class CherryDiscoveryCursorStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun prefs() = context.getSharedPreferences(
        "cherry_discovery_cursors_v1", Context.MODE_PRIVATE)
    private val dates = generateSequence(LocalDate.of(2026, 11, 2)) {
        it.plusDays(1)
    }.filter(DrawSchedule::isWeekday).take(12).toList()
    private val target = DrawIdentity(dates[10], DrawSession.MORNING)
    private fun rows() = (0 until 20).map { i ->
        HistoricDraw(dates[i / 2],
            if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
            "00", HistoricSource.WINNER)
    }

    @Before fun start() { assertTrue(prefs().edit().clear().commit()) }
    @After fun end() { prefs().edit().clear().commit() }

    @Test fun restartResumesSameUnseenFormulaAndBest() {
        val history = rows()
        val store = CherryDiscoveryCursorStore(context)
        val first = CherryDiscoverySearchMore.next(
            history, target, CherryDiscoveryKind.LONE)
        val second = CherryDiscoverySearchMore.next(
            history, target, CherryDiscoveryKind.LONE, first)
        store.persist(first)
        store.persist(second)
        val reopened = CherryDiscoveryCursorStore(context)
        val restored = reopened.resume(
            CherryDiscoveryKind.LONE, target, history)
        assertEquals(second, restored)
        val next = CherryDiscoverySearchMore.next(
            history, target, CherryDiscoveryKind.LONE, restored)
        assertNotEquals(first.newCandidate?.id, next.newCandidate?.id)
        assertNotEquals(second.newCandidate?.id, next.newCandidate?.id)
        assertTrue(next.offeredIds.containsAll(second.offeredIds))
        reopened.persist(next)
        assertEquals(next, store.resume(
            CherryDiscoveryKind.LONE, target, history))
    }

    @Test fun staleWriterCannotEraseOrRollBackProgress() {
        val history = rows()
        val store1 = CherryDiscoveryCursorStore(context)
        val store2 = CherryDiscoveryCursorStore(context)
        val first = CherryDiscoverySearchMore.next(
            history, target, CherryDiscoveryKind.WAM)
        store1.persist(first)
        val second = CherryDiscoverySearchMore.next(
            history, target, CherryDiscoveryKind.WAM, first)
        store2.persist(second)
        try {
            store1.persist(first)
            fail("Stale checkpoint must be rejected")
        } catch (_: IllegalArgumentException) { /* expected */ }
        assertEquals(second, store1.resume(
            CherryDiscoveryKind.WAM, target, history))
    }

    @Test fun persistedOfferPositionsAndBehaviorsCannotBeRewritten() {
        val history = rows()
        val store = CherryDiscoveryCursorStore(context)
        val first = CherryDiscoverySearchMore.next(
            history, target, CherryDiscoveryKind.LONE)
        assertEquals(first.offeredIds, first.offeredPositions.keys)
        assertEquals(first.offeredIds, first.offeredBehaviors.keys)
        store.persist(first)
        val id = requireNotNull(first.newCandidate).id
        val forged = first.copy(
            offeredPositions = first.offeredPositions + (id to 827))
        try {
            store.persist(forged)
            fail("A stored formula position must be immutable")
        } catch (_: IllegalArgumentException) { /* expected */ }
        assertEquals(first, store.resume(
            CherryDiscoveryKind.LONE, target, history))
    }

    @Test fun correctedHistoryInvalidatesCursorWithoutChangingFrozenResults() {
        val history = rows()
        val store = CherryDiscoveryCursorStore(context)
        val first = CherryDiscoverySearchMore.next(
            history, target, CherryDiscoveryKind.BREAK)
        store.persist(first)
        val corrected = history.toMutableList()
        corrected[corrected.lastIndex] =
            corrected.last().copy(digit = "98")
        assertNull(store.resume(CherryDiscoveryKind.BREAK, target, corrected))
        val reset = CherryDiscoverySearchMore.next(
            corrected, target, CherryDiscoveryKind.BREAK)
        store.persist(reset)
        assertEquals(reset, store.resume(
            CherryDiscoveryKind.BREAK, target, corrected))
        assertNull(store.resume(CherryDiscoveryKind.BREAK, target, history))
    }

    @Test fun corruptedCheckpointCannotBeSilentlyDiscarded() {
        prefs().edit().putString("cursor_LONE", "{bad").commit()
        try {
            CherryDiscoveryCursorStore(context).resume(
                CherryDiscoveryKind.LONE, target, rows())
            fail("Damaged cursor must fail closed")
        } catch (_: IllegalStateException) { /* expected */ }
    }
}
