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
        assertEquals(listOf("second"), TwoDDetailSelection.conditions(report).map { it.first })
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
}
