package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CherryDiscoveryHistoryNormalizerTest {
    private val day = LocalDate.of(2026, 11, 9)

    @Test fun verifiedWinnerAlwaysBeatsDailyAndImportedHistory() {
        val history = HistoricDraw(day, DrawSession.EVENING, "12", HistoricSource.HISTORY)
        val daily = history.copy(digit = "34", source = HistoricSource.DAILY)
        val winner = history.copy(digit = "56", source = HistoricSource.WINNER)
        for (set in listOf(
            listOf(history, daily, winner),
            listOf(winner, history, daily),
            listOf(daily, winner, history),
        )) {
            assertEquals(listOf(winner), CherryDiscoveryHistoryNormalizer.normalize(set))
        }
        assertEquals(listOf(daily),
            CherryDiscoveryHistoryNormalizer.normalize(listOf(history, daily)))
    }

    @Test fun normalizedSourcePrecedencePropagatesToEngineAndReplay() {
        val weekdays = generateSequence(LocalDate.of(2026, 11, 2)) {
            it.plusDays(1)
        }.filter { DrawSchedule.isWeekday(it) }.take(7).toList()
        val records = (0 until 12).map { i ->
            HistoricDraw(weekdays[i / 2],
                if (i % 2 == 0) DrawSession.MORNING else DrawSession.EVENING,
                "00", HistoricSource.HISTORY)
        }
        val authoritative = records.last().copy(digit = "57",
            source = HistoricSource.WINNER)
        val clean = records.dropLast(1) + authoritative
        val conflicted = listOf(authoritative) + records
        val target = DrawIdentity(weekdays[6], DrawSession.MORNING)

        assertEquals(
            CherryFormulaDiscoveryEngine.nextRound(clean, target),
            CherryFormulaDiscoveryEngine.nextRound(conflicted, target),
        )
        assertEquals(
            CherryDiscoveryWalkForward.replay(clean, lastTargets = 3),
            CherryDiscoveryWalkForward.replay(conflicted, lastTargets = 3),
        )
    }
}
