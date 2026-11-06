package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Historic2DStatisticsTest {
    private fun draw(
        day: String, session: DrawSession, digit: String,
        source: HistoricSource = HistoricSource.DAILY,
    ) = HistoricDraw(LocalDate.parse(day), session, digit, source)
    private val am = DrawSession.MORNING
    private val pm = DrawSession.EVENING

    @Test fun primaryWinsPerDrawNotPerMonthAndConflictsAreVisible() {
        val result = HistoricDrawMerger.merge(
            listOf(draw("2026-11-02", am, "64"), draw("2026-11-04", pm, "05")),
            listOf(
                draw("2026-11-02", am, "12", HistoricSource.HISTORY),
                draw("2026-11-02", pm, "21", HistoricSource.HISTORY),
                draw("2026-11-04", pm, "05", HistoricSource.HISTORY),
            ),
            LocalDate.parse("2026-11-05"),
        )
        assertEquals(3, result.draws.size)
        assertEquals(listOf("64", "21", "05"), result.draws.map { it.digit })
        assertEquals(2, result.dailyCount)
        assertEquals(1, result.historyCount)
        assertEquals(1, result.conflicts.size)
        assertEquals("64", result.conflicts.single().primaryDigit)
        assertEquals("12", result.conflicts.single().historyDigit)
    }

    @Test fun invalidPrimaryFallsBackToHistoryAndTodayIsExcluded() {
        val result = HistoricDrawMerger.merge(
            listOf(draw("2026-11-04", am, "-"),
                draw("2026-11-05", am, "35"), draw("2026-11-06", am, "42")),
            listOf(draw("2026-11-04", am, "07", HistoricSource.HISTORY)),
            LocalDate.parse("2026-11-05"),
        )
        assertEquals(listOf("07", "35"), result.draws.map { it.digit })
        assertEquals(HistoricSource.HISTORY, result.draws.first().source)
        assertFalse(validHistoric2D("0"))
        assertFalse(validHistoric2D("--"))
        assertFalse(validHistoric2D("1a"))
        assertTrue(validHistoric2D("00"))
    }

    @Test fun frequencyTensOnesMissingAndDoublesAreCorrect() {
        val draws = listOf(
            draw("2026-10-05", am, "64"),
            draw("2026-10-05", pm, "46"),
            draw("2026-10-06", am, "64"),
            draw("2026-10-06", pm, "00"),
        )
        val r = HistoricStatisticsCalculator.calculate(
            draws, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
        )
        assertEquals(4, r.totalDraws)
        assertEquals(3, r.distinctCount)
        assertEquals(97, r.missing.size)
        assertEquals(2, r.counts.first { it.digit == "64" }.count)
        assertEquals(2, r.tens[6])
        assertEquals(1, r.tens[4])
        assertEquals(1, r.tens[0])
        assertEquals(2, r.ones[4])
        assertEquals(1, r.ones[6])
        assertEquals(1, r.doubleCount)
        assertEquals("64", r.topNumbers.first().digit)
        assertEquals(2, r.dailyPairs.size)
        assertEquals("64", r.dailyPairs.first().morning)
        assertEquals("46", r.dailyPairs.first().evening)
    }

    @Test fun lastSeenUsesHistoryBeforeStartAndAnchorsAtEnd() {
        val draws = listOf(
            draw("2026-09-30", pm, "25"),
            draw("2026-10-05", am, "64"),
            draw("2026-10-05", pm, "46"),
            draw("2026-10-06", am, "64"),
        )
        val r = HistoricStatisticsCalculator.calculate(
            draws, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"),
        )
        val twentyFive = r.counts.first { it.digit == "25" }
        assertEquals(0, twentyFive.count)
        assertEquals(LocalDate.parse("2026-09-30"), twentyFive.lastSeen?.date)
        assertEquals(2, twentyFive.observedDrawsSince)
        assertEquals(2, r.totalDraws)
        assertEquals(1, r.dailyPairs.size)
    }

    @Test fun sessionFilterAndReversePairCounts() {
        val draws = listOf(
            draw("2026-10-05", am, "12"), draw("2026-10-05", pm, "21"),
            draw("2026-10-06", am, "12"), draw("2026-10-06", pm, "21"),
        )
        val both = HistoricStatisticsCalculator.calculate(
            draws, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        val onlyMorning = HistoricStatisticsCalculator.calculate(
            draws, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), am)
        val pair = both.reversePairs.first { it.first == "12" }
        assertEquals(2, pair.firstCount)
        assertEquals(2, pair.reverseCount)
        assertEquals(2, onlyMorning.totalDraws)
        assertEquals(2, onlyMorning.counts.first { it.digit == "12" }.count)
        assertEquals(0, onlyMorning.counts.first { it.digit == "21" }.count)
        assertEquals(0, onlyMorning.dailyPairs.size)
    }

    @Test fun emptyPeriodDoesNotPretendAllHundredNumbersMissing() {
        val result = HistoricStatisticsCalculator.calculate(
            emptyList(), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        assertEquals(0, result.totalDraws)
        assertTrue(result.missing.isEmpty())
        assertTrue(result.topNumbers.isEmpty())
        assertNull(result.lastResult)
    }

    @Test fun fiveYearsOfDrawsRemainDeterministic() {
        val start = LocalDate.parse("2021-01-01")
        val draws = (0 until 2600).map { i ->
            HistoricDraw(start.plusDays(i.toLong() / 2), if (i % 2 == 0) am else pm,
                (i % 100).toString().padStart(2, '0'), HistoricSource.HISTORY)
        }
        val result = HistoricStatisticsCalculator.calculate(
            draws, start, start.plusDays(1400))
        assertEquals(2600, result.totalDraws)
        assertEquals(100, result.distinctCount)
        assertTrue(result.missing.isEmpty())
        assertEquals(2600, result.counts.sumOf { it.count })
    }
}
