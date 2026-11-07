package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TwoDAnalysisEngineTest {
    private val am = DrawSession.MORNING
    private val pm = DrawSession.EVENING
    private fun row(date: String, session: DrawSession, value: String) =
        HistoricDraw(LocalDate.parse(date), session, value, HistoricSource.HISTORY)
    private fun target(date: String, session: DrawSession = am) =
        DrawIdentity(LocalDate.parse(date), session)
    private fun week(monday: String, values: List<String>): List<HistoricDraw> {
        require(values.size == 10)
        val day = LocalDate.parse(monday)
        return values.mapIndexed { index, value ->
            HistoricDraw(day.plusDays((index / 2).toLong()),
                if (index % 2 == 0) am else pm, value, HistoricSource.HISTORY)
        }
    }

    @Test fun a02SameWeekdaySameSessionCountsBothDigitPositionsAndSeparatesNeverSeen() {
        val rows = listOf(
            row("2026-10-28", am, "55"),
            row("2026-10-21", am, "15"),
            row("2026-10-28", pm, "99"),
            row("2026-10-27", am, "55"),
        )
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-04")).a02
        assertEquals(2, a.samples)
        assertEquals(3, a.singles.single { it.digit == "5" }.count)
        assertEquals(1, a.singles.single { it.digit == "1" }.count)
        assertEquals(1, a.numbers.single { it.digit == "55" }.count)
        assertTrue("99" in a.unseenNumbers)
        assertEquals(98, a.unseenNumbers.size)
    }

    @Test fun a03RequiresSameMondayAnchoredWeekdaySessionParityAndExcludesClosedWeeks() {
        val current = listOf(
            row("2026-11-02", am, "22"),
            row("2026-11-02", pm, "11"),
            row("2026-11-03", am, "23"),
            row("2026-11-03", pm, "34"),
        )
        val matchedWeek = listOf(
            row("2026-10-26", am, "44"),
            row("2026-10-26", pm, "33"),
            row("2026-10-27", am, "45"),
            row("2026-10-27", pm, "56"),
            row("2026-10-28", am, "77"),
        )
        val wrongOrder = listOf(
            row("2026-10-19", am, "44"),
            row("2026-10-19", pm, "33"),
            row("2026-10-20", am, "56"),
            row("2026-10-20", pm, "45"),
            row("2026-10-21", am, "88"),
        )
        val a = TwoDAnalysisEngine.calculate(
            current + matchedWeek + wrongOrder, target("2026-11-04"),
        ).a03
        assertTrue(a.eligible)
        assertEquals(listOf("77"), a.matched.map { it.digit })
        assertEquals(1, a.nextParity["မမ"])
        val closed = TwoDAnalysisEngine.calculate(
            current + matchedWeek + wrongOrder, target("2026-11-04"),
            setOf(LocalDate.parse("2026-10-29")),
        ).a03
        assertTrue(closed.matched.isEmpty())
        val noFour = TwoDAnalysisEngine.calculate(
            current.take(3), target("2026-11-03", pm),
        ).a03
        assertFalse(noFour.eligible)
    }

    @Test fun a04CountsExactPairRepeatsAndSameDigitTwice() {
        val previous = week("2026-10-26",
            listOf("55", "11", "01", "01", "22", "33", "44", "66", "77", "01"))
        val a = TwoDAnalysisEngine.calculate(previous, target("2026-11-02")).a04
        assertTrue(a.complete)
        assertEquals(10, a.observedDraws)
        assertEquals(2, a.singleDigitFrequency.single { it.digit == "5" }.count)
        assertEquals(2, a.repeatedNumbers.single { it.digit == "01" }.count)
        val partial = TwoDAnalysisEngine.calculate(previous.dropLast(1),
            target("2026-11-02")).a04
        assertFalse(partial.complete)
        assertEquals(10, partial.expectedOpenDraws)
    }

    @Test fun a05TracksNewOldInPositionOrderAndRemainingUniqueDigits() {
        val current = listOf(
            row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "03"),
            row("2026-11-03", am, "02"),
            row("2026-11-03", pm, "35"),
        )
        val a = TwoDAnalysisEngine.calculate(current, target("2026-11-04")).a05
        assertEquals(listOf(true to true, true to true, false to false, false to true),
            a.transitions.map { it.newTens to it.newOnes })
        assertEquals(listOf('4','6','7','8','9'), a.remainingDigits)
        val double = TwoDAnalysisEngine.calculate(
            listOf(row("2026-11-02", am, "11")), target("2026-11-02",pm)).a05
        assertEquals(setOf('1'), double.transitions.single().newlySeenDigits)
    }

    @Test fun a06MorningEveningCombinedStreaksResetIndependently() {
        val current = listOf(
            row("2026-11-02", am, "11"),
            row("2026-11-02", pm, "22"),
            row("2026-11-03", am, "33"),
            row("2026-11-03", pm, "11"),
        )
        val a = TwoDAnalysisEngine.calculate(current, target("2026-11-04")).a06
        fun digit(stream: String) = a.getValue(stream).singleDigits.single { it.value == "1" }
        assertEquals(1, digit("morning").observedDrawsSince)
        assertEquals(0, digit("evening").observedDrawsSince)
        assertEquals(0, digit("combined").observedDrawsSince)
        assertEquals(2, a.getValue("morning").observedDraws)
        assertFalse(a.getValue("combined").hasUnknownOpenSlots)
    }

    @Test fun a07UsesSharedGroupMembershipAndCountsExactRepeatEvents() {
        val a = TwoDAnalysisEngine.calculate(listOf(
            row("2026-11-02", am, "01"),
            row("2026-11-02", pm, "01"),
            row("2026-11-03", am, "35"),
            row("2026-11-03", pm, "05"),
            row("2026-11-04", am, "01"),
        ), target("2026-11-04",pm)).a07
        assertEquals(3, a.groups["ညီကို"])
        assertEquals(1, a.groups["နက္ခတ်"])
        assertEquals(1, a.groups["ပါဝါ"])
        assertEquals(2, a.repeatedNumbers.single { it.digit == "01" }.count)
        assertEquals(listOf("05","50","16","61","27","72","38","83","49","94"), TwoDigitGroups.power)
    }

    @Test fun a08AlwaysRunsPairFirstAndSecondMatchingIndependently() {
        val rows = listOf(
            row("2026-10-30", pm, "34"),
            row("2026-11-02", am, "56"),
            row("2026-10-23", pm, "34"),
            row("2026-10-26", am, "56"),
            row("2026-10-26", pm, "78"),
            row("2026-10-16", pm, "34"),
            row("2026-10-19", am, "10"),
            row("2026-10-19", pm, "99"),
        )
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-02",pm)).a08
        assertEquals("34", a.firstReference?.digit)
        assertEquals("56", a.secondReference?.digit)
        assertEquals(listOf("78"), a.both.matches.map { it.digit })
        assertEquals(2, a.firstOnly.matches.size)
        assertEquals(1, a.secondOnly.matches.size)
        val range = TwoDAnalysisEngine.calculate(
            rows, target("2026-11-02",pm),
            historyRange = TwoDHistoryRange(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-26")),
        ).a08
        assertEquals(1, range.both.matches.size) // references lie before the chosen evidence window
    }

    @Test fun a01FirstReturnCompanionUsesOnePerCompleteHistoricalEpisode() {
        val lastWeek = week("2026-10-26",
            listOf("11", "22", "33", "44", "55", "66", "77", "88", "99", "12"))
        val oldMissingWeek = week("2026-10-05",
            listOf("11", "22", "33", "44", "55", "66", "77", "88", "99", "12"))
        val following = week("2026-10-12",
            listOf("40", "50", "10", "22", "33", "44", "55", "66", "77", "88"))
        val rows = lastWeek + oldMissingWeek + following + row("2026-11-02", am, "03")
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-02",pm)).a01
        assertTrue(a.previousWeekComplete)
        assertTrue('0' in a.missingDigits)
        val zero = a.results.single { it.digit == '0' }
        assertEquals("03", zero.thisWeekFirstSeen?.digit)
        assertTrue(zero.firstSeenVerified)
        assertEquals(1, zero.historyFirstReturnEpisodes)
        assertEquals('4', zero.commonCompanions.single().companion)
    }

    @Test fun futureTargetIsExcludedAndUnverifiedAbsenceIsNotClaimedComplete() {
        val draws = listOf(
            row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "34"),
            row("2026-11-03", am, "56"),
        )
        val report = TwoDAnalysisEngine.calculate(draws, target("2026-11-02",pm))
        assertEquals(1, report.a05.transitions.size)
        assertEquals("12", report.a05.transitions.single().result.digit)
        assertFalse(report.a04.complete)
        assertFalse(report.a01.previousWeekComplete)
    }
}
