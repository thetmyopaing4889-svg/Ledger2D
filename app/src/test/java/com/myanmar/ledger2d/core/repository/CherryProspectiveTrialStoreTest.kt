package com.myanmar.ledger2d.core.repository

import android.app.Application
import android.content.Context
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryFormulaDiscoveryEngine
import com.myanmar.ledger2d.core.domain.CherryProspectiveTrialClock
import com.myanmar.ledger2d.core.domain.CherryProspectiveTrialJournal
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
class CherryProspectiveTrialStoreTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private fun preferences() =
        context.getSharedPreferences("cherry_prospective_trial_v1", Context.MODE_PRIVATE)

    @Before fun reset() { assertTrue(preferences().edit().clear().commit()) }
    @After fun cleanup() { preferences().edit().clear().commit() }

    private fun discovered() = run {
        val days = generateSequence(LocalDate.of(2026, 11, 2)) { it.plusDays(1) }
            .filter(DrawSchedule::isWeekday).take(7).toList()
        val history = (0 until 12).map { index ->
            HistoricDraw(
                days[index / 2],
                if (index % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00", HistoricSource.WINNER,
            )
        }
        CherryFormulaDiscoveryEngine.nextRound(
            history, DrawIdentity(days[6], DrawSession.MORNING))
    }

    @Test fun localSnapshotSurvivesNewStoreInstanceAndKeepsFrozenFormula() {
        val session = discovered()
        val cutoff = CherryProspectiveTrialClock.cutoff(session.target)
        val original = CherryProspectiveTrialJournal.empty()
            .lock(session, cutoff.minusSeconds(60))
        CherryProspectiveTrialStore(context).persist(original)
        val reopened = CherryProspectiveTrialStore(context).load()
        assertEquals(original.lockedTrials(), reopened.lockedTrials())
        assertTrue(reopened.verifiedResults().isEmpty())

        val actual = HistoricDraw(session.target.date, session.target.session,
            "23", HistoricSource.WINNER)
        val settled = reopened.evaluate(actual, cutoff.plusSeconds(30))
        CherryProspectiveTrialStore(context).persist(settled)
        val again = CherryProspectiveTrialStore(context).load()
        assertEquals(original.lockedTrials(), again.lockedTrials())
        assertEquals(settled.verifiedResults(), again.verifiedResults())
        for (kind in CherryDiscoveryKind.entries) {
            assertEquals(1, again.stats(kind).completedTargets)
            assertEquals(0, again.stats(kind).hits)
        }
    }

    @Test fun committedLockCannotBeReplacedOrDeleted() {
        val session = discovered()
        val cutoff = CherryProspectiveTrialClock.cutoff(session.target)
        val locked = CherryProspectiveTrialJournal.empty()
            .lock(session, cutoff.minusSeconds(90))
        val store = CherryProspectiveTrialStore(context)
        store.persist(locked)
        val oldLock = locked.lockedTrials().single()
        val changed = oldLock.copy(choices = oldLock.choices.map {
            if (it.kind == CherryDiscoveryKind.LONE) it.copy(outputDigit = 5) else it
        })
        val forged = CherryProspectiveTrialJournal.restore(listOf(changed), emptyList())
        try {
            store.persist(forged)
            fail("An old locked prediction must never be replaced")
        } catch (_: IllegalArgumentException) { /* expected */ }
        try {
            store.persist(CherryProspectiveTrialJournal.empty())
            fail("Removing an old lock must never succeed")
        } catch (_: IllegalArgumentException) { /* expected */ }
        assertEquals(locked.lockedTrials(), store.load().lockedTrials())
    }

    @Test fun restoredOutcomeCannotBeRewrittenAsAHit() {
        val session = discovered()
        val cutoff = CherryProspectiveTrialClock.cutoff(session.target)
        val locked = CherryProspectiveTrialJournal.empty()
            .lock(session, cutoff.minusSeconds(90))
        val evaluated = locked.evaluate(
            HistoricDraw(session.target.date, session.target.session, "23",
                HistoricSource.WINNER),
            cutoff.plusSeconds(30))
        val oldOutcome = evaluated.verifiedResults().single()
        val falsified = oldOutcome.copy(outcomes = oldOutcome.outcomes.map {
            if (it.kind == CherryDiscoveryKind.LONE) it.copy(hit = true) else it
        })
        try {
            CherryProspectiveTrialJournal.restore(
                evaluated.lockedTrials(), listOf(falsified))
            fail("A saved miss must not become a hit")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test fun damagedLocalSnapshotFailsClosedInsteadOfErasingLocks() {
        preferences().edit().putString("snapshot", "{corrupted").commit()
        val store = CherryProspectiveTrialStore(context)
        try {
            store.load()
            fail("Corrupt previous lock must not be silently discarded")
        } catch (_: IllegalStateException) { /* expected */ }
        try {
            store.persist(CherryProspectiveTrialJournal.empty())
            fail("An unreadable journal must not be overwritten")
        } catch (_: IllegalStateException) { /* expected */ }
    }
}
