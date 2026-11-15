package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigInteger
import java.time.LocalDate
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CherryV2ForwardValidationTest {
    private val dates = generateSequence(LocalDate.of(2026, 7, 6)) {
        it.plusDays(1)
    }.filter(DrawSchedule::isWeekday).take(28).toList()
    private val selection = DrawIdentity(dates[18], DrawSession.MORNING)

    private fun archive(): List<HistoricDraw> {
        val digits = mutableListOf("12", "34")
        repeat(50) {
            val next = (digits.last()[0].digitToInt() +
                digits[digits.lastIndex - 1][0].digitToInt()) % 10
            digits.add("$next${(next + 1) % 10}")
        }
        return digits.mapIndexed { index, digit ->
            HistoricDraw(
                dates[index / 2],
                if (index % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                digit, HistoricSource.WINNER,
            )
        }
    }

    private fun chosen(rows: List<HistoricDraw>): CherryV2StreakMatch {
        val context = CherryV2HistoricalEvaluator.prepare(rows, selection)
        return CherryV2HistoricalEvaluator.evaluate(
            context, CherryDiscoveryKind.LONE, BigInteger.TEN,
            CherryV2GuidedSchedule.expressionAt(BigInteger.TEN),
        ).single { it.offset == 0 && it.historicalStreak >= 10 }
    }

    @Test fun fixedNonconstantPatternPredictsLaterReplayWithoutRetuning() {
        val rows = archive()
        val initial = chosen(rows)
        val actuals = rows.filter { it.date > selection.date ||
            (it.date == selection.date && it.session == DrawSession.EVENING) }
            .take(10)
        val result = CherryV2ForwardValidation.replay(initial, rows, actuals)
        assertEquals(10, result.checked)
        assertEquals(10, result.hits)
        assertEquals(0, result.misses)
        assertEquals(0, result.abstentions)
        assertEquals(1.0, result.hitRate!!, 0.00001)
        assertEquals(0.19, result.uniformChanceReference, 0.00001)
        assertEquals("HOLDOUT_REPLAY_NOT_PROSPECTIVE", result.evidenceLabel)
        assertTrue(result.trials.all { it.predictedDigit != null })
    }

    @Test fun targetOutcomeCannotLeakBackIntoPredictionForThatTarget() {
        val rows = archive()
        val frozen = chosen(rows)
        val firstActual = rows[37] // the first AFTER training target 36.
        val report = CherryV2ForwardValidation.replay(
            frozen, rows, listOf(firstActual))
        val altered = rows.toMutableList()
        altered[37] = firstActual.copy(digit = "99")
        val changed = CherryV2ForwardValidation.replay(
            frozen, altered, listOf(altered[37]))
        assertEquals(report.trials.single().predictedDigit,
            changed.trials.single().predictedDigit)
        assertEquals(report.checked, changed.checked)
    }

    @Test fun absentImmediatePriorSlotAbstainsRatherThanBridgingMissingDraw() {
        val rows = archive()
        val frozen = chosen(rows)
        val missing = rows.filterIndexed { index, _ -> index != 36 }
        val heldout = rows[37]
        val report = CherryV2ForwardValidation.replay(
            frozen, missing, listOf(heldout))
        assertEquals(0, report.checked)
        assertEquals(1, report.abstentions)
        assertNull(report.hitRate)
        assertNull(report.trials.single().predictedDigit)
    }

    @Test fun rejectedTargetsAndCancellationDoNotChangeChosenFormula() {
        val rows = archive()
        val frozen = chosen(rows)
        val before = rows[35]
        try {
            CherryV2ForwardValidation.replay(frozen, rows, listOf(before))
            fail("Training or earlier slot must never count as holdout")
        } catch (_: IllegalArgumentException) {}
        try {
            CherryV2ForwardValidation.replay(frozen, rows,
                listOf(rows[37], rows[37]))
            fail("No duplicate target outcomes")
        } catch (_: IllegalArgumentException) {}
        try {
            CherryV2ForwardValidation.replay(frozen, rows,
                listOf(rows[37]), checkCancellation = {
                    throw CancellationException("stop")
                })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {}
        assertEquals("(top1+top2)", frozen.expression)
        assertEquals(0, frozen.offset)
    }
    @Test fun correctedTrainingHistoryRejectsStaleFrozenFormula() {
        val rows = archive()
        val frozen = chosen(rows)
        val corrected = rows.toMutableList()
        corrected[32] = corrected[32].copy(digit = "99")
        assertNotEquals(rows[32].digit, corrected[32].digit)
        try {
            CherryV2ForwardValidation.replay(
                frozen, corrected, listOf(corrected[37]))
            fail("Training correction must invalidate previous formula evidence")
        } catch (_: IllegalArgumentException) {}
    }

}
