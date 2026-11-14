package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pinned real 2D_history fixture (AM/PM only) sampled from:
 * https://github.com/2d3dthailand/2d3dthailand/blob/main/2D_history
 * Source inspected 2026-11-14. It is historical test input, not a
 * guarantee of the next draw and not a replacement for DB/live results.
 */
class CherryDiscoveryWalkForwardTest {
    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    private fun realHistory(): List<HistoricDraw> = SOURCE.trimIndent().lines()
        .flatMap { line ->
            val (date, morning, evening) = line.trim().split(",")
            val day = LocalDate.parse(date, dateFormat)
            listOf(
                HistoricDraw(day, DrawSession.MORNING, morning, HistoricSource.HISTORY),
                HistoricDraw(day, DrawSession.EVENING, evening, HistoricSource.HISTORY),
            )
        }

    @Test fun realHistoryWalkForwardEvaluatesOnlyHeldOutTargets() {
        val data = realHistory()
        assertEquals(120, data.size)
        val report = CherryDiscoveryWalkForward.replay(data, lastTargets = 24)
        assertEquals(72, report.checks.size)
        assertEquals(CherryFormulaDiscoveryEngine.STAGE_COUNT, report.stagesEvaluated)
        assertTrue(report.checks.any { it.discoveredFormulaId != null })
        for (kind in CherryDiscoveryKind.entries) {
            val stats = report.stats(kind)
            assertEquals(24, stats.targetCount)
            assertEquals(stats.targetCount, stats.checked + stats.noSelection)
            assertTrue(stats.hits in 0..stats.checked)
            assertTrue(stats.rate == null || stats.rate in 0.0..1.0)
            assertTrue(report.checks.filter { it.kind == kind }.all {
                it.historicalFitStreak == null ||
                    it.historicalFitStreak >= CherryFormulaDiscoveryEngine.MIN_STREAK
            })
        }
    }

    @Test fun alteringHeldOutResultCannotChangeFrozenFormulaOrOutput() {
        val data = realHistory()
        val original = CherryDiscoveryWalkForward.replay(data, lastTargets = 1)
        val last = data.last()
        val mutated = data.dropLast(1) + last.copy(digit = "99")
        val modified = CherryDiscoveryWalkForward.replay(mutated, lastTargets = 1)
        for (kind in CherryDiscoveryKind.entries) {
            val a = original.checks.single { it.kind == kind }
            val b = modified.checks.single { it.kind == kind }
            assertEquals(a.target, b.target)
            assertEquals(a.discoveredFormulaId, b.discoveredFormulaId)
            assertEquals(a.historicalFitStreak, b.historicalFitStreak)
            assertEquals(a.outputDigit, b.outputDigit)
        }
    }

    @Test fun duplicateOrOutOfOrderHistoryIsNormalizedDeterministically() {
        val data = realHistory()
        val duplicated = data.reversed() + data.take(5)
        val base = CherryDiscoveryWalkForward.replay(data, lastTargets = 10)
        val alternate = CherryDiscoveryWalkForward.replay(duplicated, lastTargets = 10)
        assertEquals(base, alternate)
    }

    companion object {
        private val SOURCE = """
            19-08-2026,53,60
            20-08-2026,69,92
            21-08-2026,60,80
            24-08-2026,22,53
            25-08-2026,11,13
            26-08-2026,78,29
            27-08-2026,35,04
            28-08-2026,85,20
            31-08-2026,79,64
            01-09-2026,87,83
            02-09-2026,17,77
            03-09-2026,71,23
            04-09-2026,26,81
            07-09-2026,59,22
            08-09-2026,38,98
            09-09-2026,96,90
            10-09-2026,75,78
            11-09-2026,69,23
            14-09-2026,82,79
            15-09-2026,13,68
            16-09-2026,57,31
            17-09-2026,04,41
            18-09-2026,98,57
            21-09-2026,01,34
            22-09-2026,71,46
            23-09-2026,36,04
            24-09-2026,63,87
            25-09-2026,89,36
            28-09-2026,08,77
            29-09-2026,72,08
            30-09-2026,30,13
            01-10-2026,99,18
            02-10-2026,22,25
            05-10-2026,00,57
            06-10-2026,86,29
            07-10-2026,07,38
            08-10-2026,61,14
            09-10-2026,50,52
            12-10-2026,01,44
            14-10-2026,05,84
            15-10-2026,98,13
            19-10-2026,32,26
            20-10-2026,77,49
            21-10-2026,53,98
            22-10-2026,78,77
            26-10-2026,52,32
            27-10-2026,49,20
            28-10-2026,89,03
            29-10-2026,91,02
            30-10-2026,90,90
            02-11-2026,97,08
            03-11-2026,64,14
            04-11-2026,66,91
            05-11-2026,65,90
            06-11-2026,51,05
            09-11-2026,67,74
            10-11-2026,17,17
            11-11-2026,24,13
            12-11-2026,00,60
            13-11-2026,32,80
        """
    }
}
