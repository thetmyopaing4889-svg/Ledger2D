package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Pure-domain tests: these locks are intentionally LOCAL/unattested and
 * never claim to prove a real-time pre-draw commitment.
 */
class CherryProspectiveTrialTest {
    private fun prepared(): CherryDiscoverySession {
        val days = generateSequence(LocalDate.parse("2026-11-02")) {
            it.plusDays(1)
        }.filter(DrawSchedule::isWeekday).take(7).toList()
        val history = (0 until 12).map { index ->
            HistoricDraw(
                days[index / 2],
                if (index % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00",
                HistoricSource.WINNER,
            )
        }
        val target = DrawIdentity(days[6], DrawSession.MORNING)
        return CherryFormulaDiscoveryEngine.nextRound(history, target)
    }

    private fun lockedAt(session: CherryDiscoverySession): Instant =
        CherryProspectiveTrialClock.cutoff(session.target).minusSeconds(120)

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected rejection of invalid prospective trial operation")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    private fun actual(session: CherryDiscoverySession, pair: String = "00",
                       source: HistoricSource = HistoricSource.WINNER) =
        HistoricDraw(session.target.date, session.target.session, pair, source)

    @Test fun freezeAllKindsThenEvaluateOnlyAfterCutoff() {
        val discovered = prepared()
        val original = CherryProspectiveTrialJournal.empty()
        val locked = original.lock(discovered, lockedAt(discovered))
        assertEquals(0, original.lockedTrials().size)
        val entry = locked.lockedTrials().single()
        assertEquals(discovered.target, entry.target)
        assertEquals("LOCAL_PRE_RESULT_LOCK_UNVERIFIED", entry.evidenceLabel)
        assertEquals(64, entry.historyFingerprint.length)
        assertEquals(3, entry.choices.size)
        assertEquals(CherryDiscoveryKind.entries, entry.choices.map { it.kind })
        assertTrue(entry.choices.all { it.outputDigit == 0 })
        assertTrue(entry.choices.all { !it.expression.isNullOrBlank() })
        assertTrue(entry.choices.all { it.offset?.let { n -> n in 0..9 } == true })
        assertTrue(entry.choices.all { it.historicalFitStreak == 11 })
        for (kind in CherryDiscoveryKind.entries) {
            val pending = locked.stats(kind)
            assertEquals(1, pending.pendingTargets)
            assertEquals(0, pending.checked)
            assertEquals(0, pending.hits)
            assertEquals(0, pending.abstentions)
        }
        val confirmed = locked.evaluate(
            actual(discovered), CherryProspectiveTrialClock.cutoff(discovered.target))
        assertTrue(locked.verifiedResults().isEmpty())
        assertEquals(1, confirmed.verifiedResults().size)
        assertTrue(confirmed.verifiedResults().single().outcomes.all { it.hit == true })
        for (kind in CherryDiscoveryKind.entries) {
            val result = confirmed.stats(kind)
            assertEquals(1, result.lockedTargets)
            assertEquals(1, result.completedTargets)
            assertEquals(0, result.pendingTargets)
            assertEquals(1, result.checked)
            assertEquals(1, result.hits)
            assertEquals(1.0, result.checkedHitRate!!, 0.0)
        }
    }

    @Test fun locksSecondSearchMoreAlternativeWithoutLeaderBoardPromotion() {
        val days = generateSequence(LocalDate.parse("2026-11-02")) {
            it.plusDays(1)
        }.filter(DrawSchedule::isWeekday).take(7).toList()
        val rows = (0 until 12).map { i ->
            HistoricDraw(
                days[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00", HistoricSource.WINNER)
        }
        val target = DrawIdentity(days[6], DrawSession.MORNING)
        val first = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE)
        val second = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE, first)
        val chosen = requireNotNull(second.newCandidate)
        assertNotEquals(first.newCandidate?.id, chosen.id)
        assertTrue(chosen.id !in second.engine.formulas.map { it.id })
        val selections = CherryDiscoveryKind.entries.associateWith {
            if (it == CherryDiscoveryKind.LONE) chosen.id else null
        }
        val cursors = CherryDiscoveryKind.entries.associateWith {
            if (it == CherryDiscoveryKind.LONE) second else null
        }
        val locked = CherryProspectiveTrialJournal.empty()
            .lockFromSearchCursors(
                cursors, selections,
                CherryProspectiveTrialClock.cutoff(target).minusSeconds(90))
        val choices = locked.lockedTrials().single().choices
        assertEquals(chosen.id, choices.single {
            it.kind == CherryDiscoveryKind.LONE }.formulaId)
        assertEquals(chosen.currentDigit, choices.single {
            it.kind == CherryDiscoveryKind.LONE }.outputDigit)
        assertTrue(choices.filter { it.kind != CherryDiscoveryKind.LONE }
            .all { it.formulaId == null })
        val settled = locked.evaluate(
            HistoricDraw(target.date, target.session, "00",
                HistoricSource.WINNER),
            CherryProspectiveTrialClock.cutoff(target).plusSeconds(20))
        assertEquals(1, settled.stats(CherryDiscoveryKind.LONE).checked)
        assertEquals(1, settled.stats(CherryDiscoveryKind.LONE).hits)
        assertEquals(1, settled.stats(CherryDiscoveryKind.WAM).abstentions)
        assertEquals(1, settled.stats(CherryDiscoveryKind.BREAK).abstentions)
    }

    @Test fun previouslyOfferedNonbestFormulaCanBeLockedAfterMoreSearch() {
        val days = generateSequence(LocalDate.parse("2026-11-02")) {
            it.plusDays(1)
        }.filter(DrawSchedule::isWeekday).take(7).toList()
        val rows = (0 until 12).map { i ->
            HistoricDraw(days[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00", HistoricSource.WINNER)
        }
        val target = DrawIdentity(days[6], DrawSession.MORNING)
        val first = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE)
        val second = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE, first)
        val third = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE, second)
        val wanted = requireNotNull(second.newCandidate)
        assertNotEquals(wanted.id, third.newCandidate?.id)
        assertNotEquals(wanted.id, third.bestCandidate?.id)
        val cursors = CherryDiscoveryKind.entries.associateWith {
            if (it == CherryDiscoveryKind.LONE) third else null
        }
        val selected = CherryDiscoveryKind.entries.associateWith {
            if (it == CherryDiscoveryKind.LONE) wanted.id else null
        }
        val committed = CherryProspectiveTrialJournal.empty()
            .lockFromSearchCursors(
                cursors, selected,
                CherryProspectiveTrialClock.cutoff(target).minusSeconds(60),
                history = rows)
        val chosen = committed.lockedTrials().single().choices
            .single { it.kind == CherryDiscoveryKind.LONE }
        assertEquals(wanted.id, chosen.formulaId)
        assertEquals(wanted.currentDigit, chosen.outputDigit)
        assertEquals(wanted.offset, chosen.offset)
        assertEquals(wanted.historicalStreak, chosen.historicalFitStreak)
    }

    @Test fun refusesSearchMoreLocksFromMixedHistoryOrUnseenIds() {
        val days = generateSequence(LocalDate.parse("2026-11-02")) {
            it.plusDays(1)
        }.filter(DrawSchedule::isWeekday).take(7).toList()
        val rows = (0 until 12).map { i ->
            HistoricDraw(days[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00", HistoricSource.WINNER)
        }
        val target = DrawIdentity(days[6], DrawSession.MORNING)
        val lone = CherryDiscoverySearchMore.next(
            rows, target, CherryDiscoveryKind.LONE)
        val changed = rows.toMutableList().also {
            it[it.lastIndex] = it.last().copy(digit = "91")
        }
        val wam = CherryDiscoverySearchMore.next(
            changed, target, CherryDiscoveryKind.WAM)
        val badMix = mapOf(
            CherryDiscoveryKind.LONE to lone,
            CherryDiscoveryKind.WAM to wam,
            CherryDiscoveryKind.BREAK to null)
        val validChoice = CherryDiscoveryKind.entries.associateWith {
            if (it == CherryDiscoveryKind.LONE)
                lone.newCandidate?.id else null
        }
        val cutoff = CherryProspectiveTrialClock.cutoff(target)
        expectInvalid {
            CherryProspectiveTrialJournal.empty().lockFromSearchCursors(
                badMix, validChoice, cutoff.minusSeconds(60))
        }
        val single = mapOf(
            CherryDiscoveryKind.LONE to lone,
            CherryDiscoveryKind.WAM to null,
            CherryDiscoveryKind.BREAK to null)
        val unseenChoice = validChoice +
            (CherryDiscoveryKind.LONE to "UNSEEN_FORMULA")
        expectInvalid {
            CherryProspectiveTrialJournal.empty().lockFromSearchCursors(
                single, unseenChoice, cutoff.minusSeconds(60))
        }
    }

    @Test fun cannotLockAtOrAfterResultCutoffOrOnClosedDay() {
        val discovered = prepared()
        val cutoff = CherryProspectiveTrialClock.cutoff(discovered.target)
        expectInvalid { CherryProspectiveTrialJournal.empty().lock(discovered, cutoff) }
        expectInvalid { CherryProspectiveTrialJournal.empty().lock(
            discovered, cutoff.plusSeconds(1)) }
        expectInvalid { CherryProspectiveTrialJournal.empty().lock(
            discovered, cutoff.minusSeconds(1),
            closedDates = setOf(discovered.target.date)) }
    }

    @Test fun recordCannotBeOverwrittenOrSettledTwice() {
        val session = prepared()
        val journal = CherryProspectiveTrialJournal.empty()
            .lock(session, lockedAt(session))
        expectInvalid { journal.lock(session, lockedAt(session).minusSeconds(120)) }
        val cutoff = CherryProspectiveTrialClock.cutoff(session.target)
        val settled = journal.evaluate(actual(session), cutoff.plusSeconds(2))
        expectInvalid { settled.evaluate(actual(session), cutoff.plusSeconds(5)) }
        expectInvalid { settled.evaluate(actual(session, "99"), cutoff.plusSeconds(6)) }
        assertEquals("00", settled.verifiedResults().single().actualPair)
        assertEquals(0, journal.verifiedResults().size)
    }

    @Test fun cannotSettleWrongTargetUnverifiedSourceOrBeforeCutoff() {
        val session = prepared()
        val journal = CherryProspectiveTrialJournal.empty()
            .lock(session, lockedAt(session))
        val cutoff = CherryProspectiveTrialClock.cutoff(session.target)
        expectInvalid { journal.evaluate(actual(session), cutoff.minusSeconds(1)) }
        expectInvalid { journal.evaluate(actual(session, "00", HistoricSource.HISTORY),
            cutoff.plusSeconds(30)) }
        expectInvalid { journal.evaluate(actual(session, "abc"),
            cutoff.plusSeconds(30)) }
        expectInvalid { journal.evaluate(actual(session).copy(
            session = DrawSession.EVENING), cutoff.plusSeconds(30)) }
        assertTrue(journal.verifiedResults().isEmpty())
    }

    @Test fun noSelectionIsRecordedExplicitlyAsAbstentionNotAsHitOrMiss() {
        val session = prepared()
        val abstain = CherryDiscoveryKind.entries.associateWith { null as String? }
        val journal = CherryProspectiveTrialJournal.empty().lock(
            session, lockedAt(session), selectedIds = abstain)
        assertEquals(3, journal.lockedTrials().single().choices.size)
        val settled = journal.evaluate(actual(session, "42"),
            CherryProspectiveTrialClock.cutoff(session.target).plusSeconds(2))
        for (kind in CherryDiscoveryKind.entries) {
            val s = settled.stats(kind)
            assertEquals(0, s.checked)
            assertEquals(0, s.hits)
            assertEquals(1, s.abstentions)
            assertNull(s.checkedHitRate)
        }
    }

    @Test fun cannotSubmitUnknownFormulaOrOmitCategoryFromLock() {
        val session = prepared()
        val choices = CherryDiscoveryKind.entries.associateWith {
            session.longest(it)?.id
        }
        val bad = choices + (CherryDiscoveryKind.LONE to "FAKE_FORMULA")
        expectInvalid { CherryProspectiveTrialJournal.empty().lock(
            session, lockedAt(session), selectedIds = bad) }
        expectInvalid { CherryProspectiveTrialJournal.empty().lock(
            session, lockedAt(session), selectedIds = choices -
                CherryDiscoveryKind.BREAK) }
    }

    @Test fun outcomeListIsNotExternallyMutable() {
        val session = prepared()
        val journal = CherryProspectiveTrialJournal.empty()
            .lock(session, lockedAt(session))
        val frozen = journal.lockedTrials().single().choices
        try {
            @Suppress("UNCHECKED_CAST")
            (frozen as MutableList<CherryProspectiveChoice>).clear()
            fail("Frozen choices must not be externally mutable")
        } catch (_: UnsupportedOperationException) { /* expected */ }
        assertEquals(3, journal.lockedTrials().single().choices.size)
    }

    @Test fun cutoffUsesYangonTimeNotDeviceTimezone() {
        val target = DrawIdentity(LocalDate.parse("2026-11-10"),
            DrawSession.MORNING)
        assertEquals(Instant.parse("2026-11-10T05:30:00Z"),
            CherryProspectiveTrialClock.cutoff(target))
        assertEquals(Instant.parse("2026-11-10T10:00:00Z"),
            CherryProspectiveTrialClock.cutoff(
                DrawIdentity(target.date, DrawSession.EVENING)))
    }
}
