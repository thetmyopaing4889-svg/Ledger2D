package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoDDetailPresentationTest {
    @Test fun leadersPreserveTiesAndExcludeZeroSamples() {
        val rows = listOf(TwoDCount("34", 3), TwoDCount("12", 3), TwoDCount("56", 0))
        assertEquals(listOf("12", "34"), TwoDDetailSelection.leaders(rows).map { it.digit })
        assertTrue(TwoDDetailSelection.leaders(listOf(TwoDCount("00", 0))).isEmpty())
    }

    @Test fun onlyRecordedStreamsRender() {
        val hit = TwoDStreak("3", 2, null, false)
        val observed = TwoDStreakStream(2, true, listOf(hit), listOf(hit))
        val empty = TwoDStreakStream(0, false, emptyList(), emptyList())
        assertEquals(listOf("evening", "combined"), TwoDDetailSelection.streams(mapOf(
            "morning" to empty, "evening" to observed, "combined" to observed)))
    }

    @Test fun oneAvailableHistoricalReferenceStillAppears() {
        val row = TwoDMatch(LocalDate.parse("2026-10-27"), DrawSession.MORNING, "35")
        val found = TwoDConditional("89", listOf(row), listOf(TwoDCount("35", 1)), 0)
        val missing = TwoDConditional(null, emptyList(), emptyList(), 0)
        val report = TwoDPreviousResults(null, row, missing, missing, found)
        assertEquals(listOf("both", "first", "second"),
            TwoDDetailSelection.conditions(report).map { it.first })
        assertTrue(TwoDDetailSelection.conditions(report)[0].second.matches.isEmpty())
        assertTrue(TwoDDetailSelection.conditions(report)[1].second.matches.isEmpty())
        assertEquals(1, TwoDDetailSelection.conditions(report)[2].second.matches.size)
    }

    @Test fun longestStreakTiesRemainVisible() {
        val rows = listOf(TwoDStreak("7", 11, null, true),
            TwoDStreak("3", 11, null, true), TwoDStreak("1", 7, null, false))
        assertEquals(listOf("3", "7"), TwoDDetailSelection.longest(rows).map { it.value })
    }

    @Test fun userEditedBurmeseTitlesAndLabelsArePreserved() {
        assertEquals("ပျောက်ဂဏန်းနှင့် ပျောက်ဂဏန်းတွဲများ",
            TwoDAnalysisText.title("06", false))
        assertEquals("ညီအကို", twoDGroupLabel("ညီကို", false))
    }

    @Test fun perDrawUnseenListStaysStableAcrossRecompositions() {
        val first = com.myanmar.ledger2d.core.domain.TwoDTransition(
            com.myanmar.ledger2d.core.domain.TwoDMatch(
                LocalDate.parse("2026-11-02"), DrawSession.MORNING, "12"),
            true, true, setOf('1','2'), true)
        val second = com.myanmar.ledger2d.core.domain.TwoDTransition(
            com.myanmar.ledger2d.core.domain.TwoDMatch(
                LocalDate.parse("2026-11-02"), DrawSession.EVENING, "33"),
            true, true, setOf('3'), true)
        val list = listOf(first,second)
        val firstRender = TwoDDetailSelection.remainingAfter(list)
        assertEquals(firstRender, TwoDDetailSelection.remainingAfter(list))
        assertTrue('3' in firstRender[0])
        assertTrue('3' !in firstRender[1])
        assertEquals(8, firstRender[0].split(" ").size)
    }

    @Test fun digitHistoryRespectsSelectedTensOnesAndBoth() {
        val draw = com.myanmar.ledger2d.core.domain.TwoDMatch(
            LocalDate.parse("2026-10-28"), DrawSession.MORNING, "55")
        assertEquals(2, TwoDDetailSelection.positionCount("55", "5", "both"))
        assertEquals(1, TwoDDetailSelection.positionCount("55", "5", "tens"))
        assertEquals(1, TwoDDetailSelection.positionCount("55", "5", "ones"))
        assertEquals(0, TwoDDetailSelection.positionCount("15", "5", "tens"))
        assertEquals(1, TwoDDetailSelection.positionCount("15", "5", "ones"))
        assertEquals(1, TwoDDetailSelection.positionEvidence(
            listOf(draw), "5", "ones").size)
        assertTrue(TwoDDetailSelection.positionEvidence(
            listOf(draw), "1", "ones").isEmpty())
    }

    @Test fun allTagMustBeFirstEvenWhenNoHistoricResultWasRecorded() {
        val absent = TwoDConditional(null, emptyList(), emptyList(), 0)
        val first = TwoDMatch(LocalDate.parse("2026-11-09"), DrawSession.MORNING, "67")
        val second = TwoDMatch(LocalDate.parse("2026-11-09"), DrawSession.EVENING, "74")
        val a = TwoDPreviousResults(first, second, absent, absent, absent)
        assertEquals(listOf("both", "first", "second"),
            TwoDDetailSelection.conditions(a).map { it.first })
        assertTrue(TwoDDetailSelection.conditions(a).all { it.second.matches.isEmpty() })
    }

    @Test fun flowPriorityUsesActualPositionalNewOldStatusNotJustResult() {
        fun row(pair: String, tensNew: Boolean, onesNew: Boolean, verified: Boolean) =
            TwoDTransition(
                TwoDMatch(LocalDate.parse("2026-11-09"), DrawSession.MORNING, pair),
                tensNew, onesNew, emptySet(), verified)
        assertEquals("အသစ် + အသစ်",
            TwoDDetailSelection.flowPattern(row("67", true, true, true), false))
        assertEquals("အဟောင်း + အသစ်",
            TwoDDetailSelection.flowPattern(row("74", false, true, true), false))
        assertEquals(listOf('7' to false, '4' to true),
            TwoDDetailSelection.flowComponents(row("74", false, true, true)))
        assertEquals("အဟောင်း + အသစ်",
            TwoDDetailSelection.flowPattern(row("13", false, true, true), false))
        assertEquals(listOf('1' to false, '3' to true),
            TwoDDetailSelection.flowComponents(row("13", false, true, true)))
        assertEquals(listOf('7' to true, '4' to false),
            TwoDDetailSelection.flowComponents(row("74", true, false, true)))
        assertEquals("New + Old",
            TwoDDetailSelection.flowPattern(row("74", true, false, true), true))
        assertEquals("အဟောင်း + အဟောင်း",
            TwoDDetailSelection.flowPattern(row("67", false, false, true), false))
        assertEquals("အသစ် + အသစ်",
            TwoDDetailSelection.flowPattern(row("11", true, true, true), false))
        assertEquals(null,
            TwoDDetailSelection.flowPattern(row("74", false, true, false), false))
    }
    @Test fun leastObservedChangesWithCombinedTensOnesWithoutCountingZeroes() {
        val combined = listOf(
            TwoDCount("0", 9), TwoDCount("1", 2),
            TwoDCount("2", 2), TwoDCount("3", 0),
        )
        val tens = listOf(
            TwoDCount("0", 1), TwoDCount("1", 6),
            TwoDCount("2", 4), TwoDCount("3", 0),
        )
        val ones = listOf(
            TwoDCount("0", 6), TwoDCount("1", 3),
            TwoDCount("2", 1), TwoDCount("3", 0),
        )
        fun select(position: String) = TwoDDetailSelection.leastObservedByPosition(
            position, combined, tens, ones)
        assertEquals(listOf("1", "2"), select("both").map { it.digit })
        assertEquals(listOf("0"), select("tens").map { it.digit })
        assertEquals(listOf("2"), select("ones").map { it.digit })
        assertEquals(emptyList<TwoDCount>(),
            TwoDDetailSelection.leastObservedByPosition(
                "both", listOf(TwoDCount("0", 0)), emptyList(), emptyList()))
    }

}
