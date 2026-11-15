package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pinned public 2026 2D_history (AM/PM, including market-closed/off days).
 * Source: https://github.com/2d3dthailand/2d3dthailand/blob/main/2D_history
 * Fetched 2026-11-15. This is a historical replay fixture ONLY, not a
 * prediction record or a replacement for the app's operational database.
 */
class CherryDiscovery2026CoverageTest {
    private val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    private fun data(): Pair<List<HistoricDraw>, Set<LocalDate>> {
        val draws = mutableListOf<HistoricDraw>()
        val closedDates = mutableSetOf<LocalDate>()
        val days = HISTORY.trimIndent().lines()
        days.forEach { line ->
            val (date, morning, evening) = line.split(",")
            val d = LocalDate.parse(date, formatter)
            if (!validHistoric2D(morning) && !validHistoric2D(evening)) {
                closedDates.add(d)
            } else {
                if (validHistoric2D(morning)) {
                    draws.add(HistoricDraw(d, DrawSession.MORNING, morning, HistoricSource.HISTORY))
                }
                if (validHistoric2D(evening)) {
                    draws.add(HistoricDraw(d, DrawSession.EVENING, evening, HistoricSource.HISTORY))
                }
            }
        }
        return draws to closedDates
    }

    @Test fun year2026HistoricalWalkForwardWithKnownClosedDays() {
        val (draws, closed) = data()
        assertEquals(420, draws.size)
        assertEquals(17, closed.size)
        val one = CherryDiscoveryWalkForward.replay(
            draws, lastTargets = 80, stages = 1, closedDates = closed)
        val four = CherryDiscoveryWalkForward.replay(
            draws, lastTargets = 80, stages = 4, closedDates = closed)

        assertEquals(240, four.checks.size)
        // Independently calculated historical regression snapshot, 2026 fixture.
        // This does NOT assert future success or any statistical advantage.
        val lone = four.stats(CherryDiscoveryKind.LONE)
        val wam = four.stats(CherryDiscoveryKind.WAM)
        val brk = four.stats(CherryDiscoveryKind.BREAK)
        assertEquals(80, lone.checked)
        assertEquals(9, lone.hits)
        assertEquals(80, wam.checked)
        assertEquals(24, wam.hits)
        assertEquals(65, brk.checked)
        assertEquals(4, brk.hits)
        assertEquals(15, brk.noSelection)
        assertEquals(6, four.checks.filter { it.kind == CherryDiscoveryKind.LONE }
            .mapNotNull { it.historicalFitStreak }.maxOrNull())
        assertEquals(10, four.checks.filter { it.kind == CherryDiscoveryKind.WAM }
            .mapNotNull { it.historicalFitStreak }.maxOrNull())
        assertEquals(5, four.checks.filter { it.kind == CherryDiscoveryKind.BREAK }
            .mapNotNull { it.historicalFitStreak }.maxOrNull())
        for (kind in CherryDiscoveryKind.entries) {
            val a = one.stats(kind)
            val b = four.stats(kind)
            assertEquals(80, a.targetCount)
            assertEquals(80, b.targetCount)
            assertTrue("More search levels must not reduce coverage for $kind",
                b.checked >= a.checked)
            assertEquals(80, b.checked + b.noSelection)
            val left = one.checks.filter { it.kind == kind }
            val right = four.checks.filter { it.kind == kind }
            left.zip(right).forEach { (older, newer) ->
                val earlier = older.historicalFitStreak
                val later = newer.historicalFitStreak
                if (earlier != null) {
                    assertNotNull(later)
                    assertTrue("A new stage must retain the earlier-best fit for $kind",
                        later!! >= earlier)
                }
                assertTrue(newer.heldOutHit == null ||
                    (newer.outputDigit?.let { it in 0..9 } == true))
            }
            println("CHERRY_DISCOVERY_2026 kind=$kind targets=${b.targetCount}" +
                " oneStageChecked=${a.checked} oneStageHits=${a.hits}" +
                " fourStageChecked=${b.checked} fourStageHits=${b.hits}" +
                " fourStageNoSelection=${b.noSelection}")
        }
    }

    companion object {
        private val HISTORY = """
            01-01-2026,off,off
            02-01-2026,off,off
            05-01-2026,38,57
            06-01-2026,15,53
            07-01-2026,15,24
            08-01-2026,70,08
            09-01-2026,45,98
            12-01-2026,24,01
            13-01-2026,73,01
            14-01-2026,28,07
            15-01-2026,04,96
            16-01-2026,72,03
            19-01-2026,45,05
            20-01-2026,31,76
            21-01-2026,71,68
            22-01-2026,42,44
            23-01-2026,43,91
            26-01-2026,70,72
            27-01-2026,54,51
            28-01-2026,60,03
            29-01-2026,87,73
            30-01-2026,50,28
            02-02-2026,59,25
            03-02-2026,99,17
            04-02-2026,16,40
            05-02-2026,15,38
            06-02-2026,49,16
            09-02-2026,63,92
            10-02-2026,50,41
            11-02-2026,42,05
            12-02-2026,88,32
            13-02-2026,05,16
            16-02-2026,17,99
            17-02-2026,28,88
            18-02-2026,26,71
            19-02-2026,60,13
            20-02-2026,50,16
            23-02-2026,70,47
            24-02-2026,33,08
            25-02-2026,38,19
            26-02-2026,14,42
            27-02-2026,41,64
            02-03-2026,79,17
            03-03-2026,off,off
            04-03-2026,92,12
            05-03-2026,46,92
            06-03-2026,83,79
            09-03-2026,51,74
            10-03-2026,79,65
            11-03-2026,86,40
            12-03-2026,55,06
            13-03-2026,66,54
            16-03-2026,61,22
            17-03-2026,21,85
            18-03-2026,17,51
            19-03-2026,44,56
            20-03-2026,10,94
            23-03-2026,26,40
            24-03-2026,97,95
            25-03-2026,52,17
            26-03-2026,13,20
            27-03-2026,55,58
            30-03-2026,20,28
            31-03-2026,68,40
            01-04-2026,07,98
            02-04-2026,38,28
            03-04-2026,15,03
            06-04-2026,off,off
            07-04-2026,65,34
            08-04-2026,96,38
            09-04-2026,12,63
            10-04-2026,53,43
            13-04-2026,off,off
            14-04-2026,off,off
            15-04-2026,off,off
            16-04-2026,07,30
            17-04-2026,39,59
            20-04-2026,52,51
            21-04-2026,97,03
            22-04-2026,51,48
            23-04-2026,17,53
            24-04-2026,88,08
            27-04-2026,13,39
            28-04-2026,13,00
            29-04-2026,21,43
            30-04-2026,54,90
            01-05-2026,off,off
            04-05-2026,off,off
            05-05-2026,98,05
            06-05-2026,54,13
            07-05-2026,21,34
            08-05-2026,26,63
            11-05-2026,56,99
            12-05-2026,70,69
            13-05-2026,55,60
            14-05-2026,14,21
            15-05-2026,08,59
            18-05-2026,14,43
            19-05-2026,33,97
            20-05-2026,43,37
            21-05-2026,67,74
            22-05-2026,20,73
            25-05-2026,05,35
            26-05-2026,38,68
            27-05-2026,87,54
            28-05-2026,88,70
            29-05-2026,24,75
            01-06-2026,off,off
            02-06-2026,42,60
            03-06-2026,off,off
            04-06-2026,02,90
            05-06-2026,65,06
            08-06-2026,96,83
            09-06-2026,45,46
            10-06-2026,96,99
            11-06-2026,28,23
            12-06-2026,98,11
            15-06-2026,82,28
            16-06-2026,35,51
            17-06-2026,26,79
            18-06-2026,36,66
            19-06-2026,24,01
            22-06-2026,60,39
            23-06-2026,98,06
            24-06-2026,68,29
            25-06-2026,66,52
            26-06-2026,62,48
            29-06-2026,59,16
            30-06-2026,73,48
            01-07-2026,30,33
            02-07-2026,29,60
            03-07-2026,38,82
            06-07-2026,01,81
            07-07-2026,79,34
            08-07-2026,83,52
            09-07-2026,66,05
            10-07-2026,75,55
            13-07-2026,48,02
            14-07-2026,17,33
            15-07-2026,80,16
            16-07-2026,76,97
            17-07-2026,53,43
            20-07-2026,66,00
            21-07-2026,57,70
            22-07-2026,58,41
            23-07-2026,03,39
            24-07-2026,15,94
            27-07-2026,85,73
            28-07-2026,off,off
            29-07-2026,off,off
            30-07-2026,30,17
            31-07-2026,16,42
            03-08-2026,20,05
            04-08-2026,71,37
            05-08-2026,87,83
            06-08-2026,31,45
            07-08-2026,75,01
            10-08-2026,27,61
            11-08-2026,56,20
            12-08-2026,off,off
            13-08-2026,54,63
            14-08-2026,13,68
            17-08-2026,23,40
            18-08-2026,19,28
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
            13-10-2026,off,off
            14-10-2026,05,84
            15-10-2026,98,13
            16-10-2026,off,off
            19-10-2026,32,26
            20-10-2026,77,49
            21-10-2026,53,98
            22-10-2026,78,77
            23-10-2026,off,off
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
