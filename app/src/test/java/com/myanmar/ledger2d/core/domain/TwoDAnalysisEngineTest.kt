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
        assertEquals(1, a.tens.single { it.digit == "5" }.count)
        assertEquals(2, a.ones.single { it.digit == "5" }.count)
        assertEquals(1, a.singles.single { it.digit == "1" }.count)
        assertEquals(1, a.numbers.single { it.digit == "55" }.count)
        assertTrue("99" in a.unseenNumbers)
        assertEquals(98, a.unseenNumbers.size)
    }

    @Test fun a02PairEvidenceRespectsWeekdaySessionRangeClosureAndTargetCutoff() {
        val rows = listOf(
            row("2026-10-07", am, "52"),
            row("2026-10-14", am, "52"),
            row("2026-10-21", am, "70"),
            row("2026-10-28", am, "52"),
            row("2026-10-28", pm, "52"), // wrong session
            row("2026-10-27", am, "52"), // wrong weekday
            row("2026-11-04", am, "52"), // target itself is not evidence
            row("2026-11-11", am, "52"), // future result
        )
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-04")).a02
        assertEquals(4, a.samples)
        assertEquals(3, a.numbers.single { it.digit == "52" }.count)
        assertEquals(
            listOf("2026-10-07", "2026-10-14", "2026-10-28"),
            a.numberOccurrences.getValue("52").map { it.date.toString() },
        )
        assertTrue(a.numberOccurrences.getValue("52").all { it.session == am && it.digit == "52" })
        assertTrue(a.numberOccurrences["99"].isNullOrEmpty())
        assertEquals(a.samples, a.numberOccurrences.values.sumOf { it.size })

        val restricted = TwoDAnalysisEngine.calculate(
            rows, target("2026-11-04"),
            verifiedClosedDays = setOf(LocalDate.parse("2026-10-14")),
            historyRange = TwoDHistoryRange(
                LocalDate.parse("2026-10-14"), LocalDate.parse("2026-11-04")),
        ).a02
        assertEquals(2, restricted.samples)
        assertEquals(listOf("2026-10-28"),
            restricted.numberOccurrences.getValue("52").map { it.date.toString() })
        assertEquals(1, restricted.numberOccurrences.getValue("70").size)
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
        assertEquals(3, a.groupOccurrences.getValue("ညီကို").size)
        assertEquals("35", a.groupOccurrences.getValue("နက္ခတ်").single().digit)
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

    @Test fun a08MatchingHistoricalSourcesWithMissingTargetAreReportedSeparately() {
        val rows = listOf(
            row("2026-11-02", am, "02"),
            row("2026-11-02", pm, "89"),
            row("2026-10-26", am, "02"),
            row("2026-10-26", pm, "89"),
            // Tuesday Oct 27 morning target is missing from imported history.
        )
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-03", am)).a08
        assertEquals("02", a.firstReference?.digit)
        assertEquals("89", a.secondReference?.digit)
        assertTrue(a.both.matches.isEmpty())
        assertEquals(1, a.both.missingTargetCount)
        assertEquals(1, a.firstOnly.missingTargetCount)
        assertEquals(1, a.secondOnly.missingTargetCount)
        assertTrue(a.both.frequencies.isEmpty())
    }

    @Test fun a08CanCrossFridayMondayWithoutCompressingSessions() {
        val rows = listOf(
            row("2026-10-30", am, "25"),
            row("2026-10-30", pm, "78"),
            row("2026-10-23", am, "25"),
            row("2026-10-23", pm, "78"),
            row("2026-10-26", am, "36"),
        )
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-02", am)).a08
        assertEquals("25", a.firstReference?.digit)
        assertEquals("78", a.secondReference?.digit)
        assertEquals(listOf("36"), a.both.matches.map { it.digit })
        assertEquals(listOf("36"), a.firstOnly.matches.map { it.digit })
        assertEquals(listOf("36"), a.secondOnly.matches.map { it.digit })
    }

    @Test fun a08VerifiedClosedDaySkipsToEarlierTradingDayButUnknownGapDoesNot() {
        val rows = listOf(
            row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "34"),
            row("2026-10-26", am, "12"),
            row("2026-10-26", pm, "34"),
            row("2026-10-28", am, "78"),
        )
        val verifiedClosures = setOf(
            LocalDate.parse("2026-11-03"),
            LocalDate.parse("2026-10-27"),
        )
        val good = TwoDAnalysisEngine.calculate(
            rows, target("2026-11-04", am), verifiedClosures,
        ).a08
        assertEquals("12", good.firstReference?.digit)
        assertEquals("34", good.secondReference?.digit)
        assertEquals(listOf("78"), good.both.matches.map { it.digit })
        val unknownIsNotHoliday = TwoDAnalysisEngine.calculate(
            rows, target("2026-11-04", am),
        ).a08
        assertNull(unknownIsNotHoliday.firstReference)
        assertNull(unknownIsNotHoliday.secondReference)
        assertTrue(unknownIsNotHoliday.both.matches.isEmpty())
    }

    @Test fun a06VerifiedClosedDayIsNotAFalseUnknownDrawSlot() {
        val rows = listOf(
            row("2026-11-02", am, "11"),
            row("2026-11-02", pm, "22"),
            row("2026-11-04", am, "33"),
        )
        val a = TwoDAnalysisEngine.calculate(
            rows, target("2026-11-04", pm),
            setOf(LocalDate.parse("2026-11-03")),
        ).a06
        assertFalse(a.getValue("combined").hasUnknownOpenSlots)
        assertFalse(a.getValue("morning").hasUnknownOpenSlots)
        assertEquals(1, a.getValue("morning").fullNumbers.single { it.value == "11" }.observedDrawsSince)
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
        assertEquals(1, zero.historicalMissingWeeks)
        assertEquals(0, zero.historicalNoReturnWeeks)
        assertEquals(0, zero.historicalUnknownNextWeeks)
        assertEquals('4', zero.commonCompanions.single().companion)
        assertEquals("40", zero.historicalFirstReturns.single().digit)
    }

    @Test fun a01SeparatesConfirmedNonReturnFromUnknownFollowup() {
        val previous = week("2026-10-26",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        val oldMissing = week("2026-10-05",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        val noReturn = week("2026-10-12",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        val report = TwoDAnalysisEngine.calculate(
            previous + oldMissing + noReturn,
            target("2026-11-02", am),
        )
        val zero = report.a01.results.single { it.digit == '0' }
        // Oct 05's fully observed following week had no 0.
        // Oct 12's following week is missing data: unknown, not a false non-return.
        assertEquals(2, zero.historicalMissingWeeks)
        assertEquals(1, zero.historicalNoReturnWeeks)
        assertEquals(1, zero.historicalUnknownNextWeeks)
        assertEquals(0, zero.historyFirstReturnEpisodes)
    }

    @Test fun a04NoObservedResultsMustNotClaimEveryDigitIsMissing() {
        val a = TwoDAnalysisEngine.calculate(emptyList(), target("2026-11-02")).a04
        assertEquals(0, a.observedDraws)
        assertFalse(a.complete)
        assertTrue(a.remainingDigits.isEmpty())
    }

    @Test fun a06SelectedHistoryEndIsNotMistakenForMissingLaterDraws() {
        val rows = listOf(
            row("2026-11-02", am, "12"),
            row("2026-11-02", pm, "34"),
        )
        val report = TwoDAnalysisEngine.calculate(
            rows, target("2026-11-04"),
            historyRange = TwoDHistoryRange(
                LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-02"),
            ),
        )
        assertFalse(report.a06.getValue("combined").hasUnknownOpenSlots)
        assertEquals(2, report.a06.getValue("combined").observedDraws)
        assertFalse(report.a06.getValue("morning").hasUnknownOpenSlots)
        assertFalse(report.a06.getValue("evening").hasUnknownOpenSlots)
    }

    @Test fun a05AfterUnknownEarlierDrawIsNotPresentedAsVerifiedNewOld() {
        val rows = listOf(
            row("2026-11-02", am, "12"),
            // Monday PM is missing; Tuesday AM could have been seen before.
            row("2026-11-03", am, "35"),
        )
        val result = TwoDAnalysisEngine.calculate(rows, target("2026-11-03", pm))
        assertFalse(result.a05.completeThroughTarget)
        assertTrue(result.a05.transitions[0].classificationVerified)
        assertFalse(result.a05.transitions[1].classificationVerified)
        assertFalse(result.a07.completeThroughTarget)
        assertEquals(2, result.a07.observedDraws)
    }

    @Test fun a01IncompletePreviousWeekStillTracksRecordedFirstReturn() {
        val prior = week("2026-10-26",
            listOf("11","22","33","44","55","66","77","88","99","12")).dropLast(1)
        val today = row("2026-11-02", am, "03")
        val result = TwoDAnalysisEngine.calculate(prior + today, target("2026-11-02", pm))
        assertFalse(result.a01.previousWeekComplete)
        val zero = result.a01.results.single { it.digit == '0' }
        assertEquals("03", zero.thisWeekFirstSeen?.digit)
        assertTrue(zero.firstSeenVerified)
    }

    @Test fun a06SingleDigitStreaksCountTensAndOnesInAllThreeStreams() {
        val rows = listOf(
            row("2026-11-02", am, "56"),
            row("2026-11-02", pm, "70"),
            row("2026-11-03", am, "12"),
            row("2026-11-03", pm, "45"),
        )
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-04")).a06
        fun digit(stream: String, value: String) =
            a.getValue(stream).singleDigits.single { it.value == value }
        // 6 and 0 appeared only in the ones position.
        assertEquals(1, digit("morning", "6").observedDrawsSince)
        assertEquals(1, digit("evening", "0").observedDrawsSince)
        assertEquals(3, digit("combined", "6").observedDrawsSince)
        assertEquals(2, digit("combined", "0").observedDrawsSince)
        assertEquals(0, digit("combined", "5").observedDrawsSince)
        assertEquals(4, a.getValue("combined").fullNumbers.single { it.value == "55" }.observedDrawsSince)
        assertEquals(10, a.getValue("combined").singleDigits.size)
        assertEquals(100, a.getValue("combined").fullNumbers.size)
    }

    @Test fun a01PartialWeeksUseRecordedResultsForMissingAndReturnCompanions() {
        val previous = week("2026-10-26",
            listOf("11", "22", "33", "44", "66", "77", "88", "99", "12", "34")).dropLast(2)
        // Historical missing week has only two known draws; 5 did not occur.
        val historical = listOf(
            row("2026-10-05", am, "11"),
            row("2026-10-06", am, "22"),
            // Historical following week also has gaps, but 5 returned in 59.
            row("2026-10-12", pm, "59"),
            row("2026-11-02", am, "50"),
        )
        val report = TwoDAnalysisEngine.calculate(previous + historical, target("2026-11-02", pm))
        assertEquals(8, report.a04.observedDraws)
        assertFalse(report.a04.complete)
        assertTrue('5' in report.a04.remainingDigits)
        val five = report.a01.results.single { it.digit == '5' }
        assertEquals("50", five.thisWeekFirstSeen?.digit)
        assertTrue(five.firstSeenVerified)
        // An incomplete historical source week cannot establish that 5
        // was absent for the entire week; no first-return companion is proven.
        assertEquals(0, five.historicalMissingWeeks)
        assertEquals(0, five.historyFirstReturnEpisodes)
        assertTrue(five.commonCompanions.isEmpty())
        assertTrue(five.historicalFirstReturns.isEmpty())
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

    @Test fun detailEvidencePreservesA02DigitPositionCountsWithoutDuplicatingDates() {
        val rows = listOf(row("2026-10-28", am, "55"), row("2026-10-21", am, "15"))
        val a = TwoDAnalysisEngine.calculate(rows, target("2026-11-04")).a02
        assertEquals(3, a.singles.single { it.digit == "5" }.count)
        assertEquals(2, a.digitOccurrences.getValue("5").size)
        assertEquals(3, a.digitOccurrences.getValue("5").sumOf { it.digit.count { c -> c == '5' } })
        assertEquals(2, a.recordedResults.size)
    }

    @Test fun detailEvidenceKeepsExactPatternPrefixSlots() {
        val current = listOf(row("2026-11-02", am, "22"), row("2026-11-02", pm, "11"),
            row("2026-11-03", am, "23"), row("2026-11-03", pm, "34"))
        val history = listOf(row("2026-10-26", am, "44"), row("2026-10-26", pm, "33"),
            row("2026-10-27", am, "45"), row("2026-10-27", pm, "56"),
            row("2026-10-28", am, "77"))
        val a = TwoDAnalysisEngine.calculate(current + history, target("2026-11-04")).a03
        assertEquals(4, a.currentPreceding.size)
        assertEquals(1, a.matchingWeeks.size)
        assertEquals("77", a.matchingWeeks.single().following.digit)
        assertEquals(a.matched, a.matchingWeeks.map { it.following })
        assertEquals(4, a.matchingWeeks.single().preceding.size)
    }

    @Test fun detailEvidenceExposesPreviousWeekAndRepeatChronology() {
        val history = listOf(row("2026-11-02", am, "12"),row("2026-11-03", am, "12"),
            row("2026-10-29", am, "33"))
        val report = TwoDAnalysisEngine.calculate(history,target("2026-11-04"))
        assertEquals(1,report.a04.recordedResults.size)
        assertEquals(listOf("12","12"),report.a07.repeatedOccurrences.getValue("12").map { it.digit })
        assertEquals(1,report.a07.repeatedNumbers.single().count)
    }

    @Test fun conditionalEvidenceIncludesExactHistoricPrecedingResult() {
        val rows = listOf(
            row("2026-10-30", pm, "05"), row("2026-11-02", am, "67"),
            row("2026-11-02", pm, "41"), row("2026-11-06", pm, "05"),
            row("2026-11-09", am, "67"),
        )
        val report = TwoDAnalysisEngine.calculate(rows,target("2026-11-09",pm))
        val hit = report.a08.secondOnly.referenceChains.first { it.following.digit == "41" }
        assertEquals("67",hit.precedingSecond?.digit)
        assertEquals("41",hit.following.digit)
    }
    @Test fun a01PartialFollowingWeekCannotProveNonReturn() {
        val source = week("2026-10-05",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        // Missing nine next-week draws is NOT proof that 0 did not return.
        val nextWeek = listOf(row("2026-10-12", am, "11"))
        val previous = week("2026-10-26",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        val result = TwoDAnalysisEngine.calculate(
            source + nextWeek + previous, target("2026-11-02")
        ).a01.results.single { it.digit == '0' }
        assertEquals(1, result.historicalMissingWeeks)
        assertEquals(0, result.historicalNoReturnWeeks)
        assertEquals(1, result.historicalUnknownNextWeeks)
        assertTrue(result.commonCompanions.isEmpty())
    }

    @Test fun a01GapBeforeObservedReturnDoesNotProduceVerifiedCompanion() {
        val source = week("2026-10-05",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        // Monday AM unknown; Monday PM=03 could be the SECOND return.
        val nextWeek = week("2026-10-12",
            listOf("11","03","22","33","44","55","66","77","88","99")).drop(1)
        val previous = week("2026-10-26",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        val result = TwoDAnalysisEngine.calculate(
            source + nextWeek + previous, target("2026-11-02")
        ).a01.results.single { it.digit == '0' }
        assertEquals(1, result.historicalMissingWeeks)
        assertEquals(0, result.historicalNoReturnWeeks)
        assertEquals(1, result.historicalUnknownNextWeeks)
        assertEquals(0, result.historyFirstReturnEpisodes)
        assertTrue(result.commonCompanions.isEmpty())
    }

    @Test fun a01GapsAfterFirstReturnDoNotInvalidateVerifiedCompanion() {
        val source = week("2026-10-05",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        // Monday AM is provably the first return; later results may be absent.
        val nextWeek = listOf(row("2026-10-12", am, "03"))
        val previous = week("2026-10-26",
            listOf("11","22","33","44","55","66","77","88","99","12"))
        val result = TwoDAnalysisEngine.calculate(
            source + nextWeek + previous, target("2026-11-02")
        ).a01.results.single { it.digit == '0' }
        assertEquals(1, result.historicalMissingWeeks)
        assertEquals(1, result.historyFirstReturnEpisodes)
        assertEquals('3', result.commonCompanions.single().companion)
        assertEquals(0, result.historicalUnknownNextWeeks)
    }

    @Test fun a03DistinguishesMatchedPrefixWithUnknownFollowingFromNoMatch() {
        val current = listOf(
            row("2026-11-02", am, "22"),
            row("2026-11-02", pm, "11"),
            row("2026-11-03", am, "23"),
            row("2026-11-03", pm, "34"),
        )
        val samePrefixWithoutOutcome = listOf(
            row("2026-10-26", am, "44"),
            row("2026-10-26", pm, "33"),
            row("2026-10-27", am, "45"),
            row("2026-10-27", pm, "56"),
            // Wednesday AM is NOT recorded.
        )
        val unknown = TwoDAnalysisEngine.calculate(
            current + samePrefixWithoutOutcome, target("2026-11-04")
        ).a03
        assertTrue(unknown.eligible)
        assertTrue(unknown.matched.isEmpty())
        assertEquals(1, unknown.missingFollowingCount)
        assertTrue(unknown.nextParity.values.all { it == 0 })
        val noMatch = TwoDAnalysisEngine.calculate(
            current + listOf(
                row("2026-10-26", am, "11"),
                row("2026-10-26", pm, "33"),
                row("2026-10-27", am, "45"),
                row("2026-10-27", pm, "56"),
            ), target("2026-11-04")
        ).a03
        assertEquals(0, noMatch.missingFollowingCount)
        assertTrue(noMatch.matched.isEmpty())
    }

    @Test fun a03OnlyVerifiedFollowingOutcomesCountButUnknownAreRetained() {
        val current = listOf(
            row("2026-11-02", am, "22"), row("2026-11-02", pm, "11"),
            row("2026-11-03", am, "23"), row("2026-11-03", pm, "34"),
        )
        val known = listOf(
            row("2026-10-26", am, "44"), row("2026-10-26", pm, "33"),
            row("2026-10-27", am, "45"), row("2026-10-27", pm, "56"),
            row("2026-10-28", am, "77"),
        )
        val unknown = listOf(
            row("2026-10-19", am, "44"), row("2026-10-19", pm, "33"),
            row("2026-10-20", am, "45"), row("2026-10-20", pm, "56"),
        )
        val result = TwoDAnalysisEngine.calculate(
            current + known + unknown, target("2026-11-04")
        ).a03
        assertEquals(listOf("77"), result.matched.map { it.digit })
        assertEquals(1, result.missingFollowingCount)
        assertEquals(1, result.nextParity["မမ"])
    }

}
