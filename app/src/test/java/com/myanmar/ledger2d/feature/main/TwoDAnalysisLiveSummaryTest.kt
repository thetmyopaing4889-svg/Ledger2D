package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.TwoDCount
import com.myanmar.ledger2d.core.domain.TwoDConditional
import com.myanmar.ledger2d.core.domain.TwoDMatch
import com.myanmar.ledger2d.core.domain.TwoDPreviousResults
import com.myanmar.ledger2d.core.domain.TwoDStreakStream
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import com.myanmar.ledger2d.core.domain.TwoDStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real-data dashboard selection: absence, ties, unavailable vs observed zero. */
class TwoDAnalysisLiveSummaryTest {
    @Test fun unavailableNeverUsesPlaceholderDash() {
        assertEquals("အချက်အလက် မရရှိနိုင်သေးပါ",
            TwoDLiveSummary.unavailable(en = false))
        assertEquals("Information not yet available",
            TwoDLiveSummary.unavailable(en = true))
        assertFalse(TwoDLiveSummary.peak(emptyList<TwoDCount>()).present)
        assertFalse(TwoDLiveSummary.peak(mapOf("စုံစုံ" to 0)).present)
    }

    @Test fun maximumFrequencyAndTiesUseActualVerifiedCounts() {
        val samples = listOf(
            TwoDCount("52", 6), TwoDCount("34", 2), TwoDCount("93", 6))
        val top = TwoDLiveSummary.peak(samples)
        assertEquals(6, top.count)
        assertEquals(listOf("52", "93"), top.values)
        assertEquals("52·93", top.display(en = false))
        assertEquals("3 မျိုးတူ",
            TwoDLiveSummary.Peak(listOf("1", "3", "5"), 10)
                .display(en = false))
    }

    @Test fun tiedCompanionsExposeActualDigitsAndBoundOverflow() {
        val peak = TwoDLiveSummary.Peak(listOf("8", "3", "5"), 4)
        assertEquals("3 မျိုးတူ", peak.display(en = false))
        assertEquals(listOf("3", "5", "8"), peak.visibleDigits())
        assertEquals(0, peak.hiddenDigitCount())

        val all = TwoDLiveSummary.Peak(
            (0..9).map { it.toString() }, 6)
        assertEquals(listOf("0", "1", "2"), all.visibleDigits())
        assertEquals(7, all.hiddenDigitCount())
        assertEquals(listOf("0", "1"), all.visibleDigits(limit = 2))
        assertEquals(8, all.hiddenDigitCount(limit = 2))
    }

    @Test fun missingSingleDigitAndFullPairRankSeparately() {
        val digit = TwoDLiveSummary.missing(
            listOf(
                TwoDStreak("3", 11, null, true),
                TwoDStreak("6", 8, null, true),
            ), unknown = false)
        val pair = TwoDLiveSummary.missing(
            listOf(
                TwoDStreak("34", 423, null, false),
                TwoDStreak("93", 417, null, false),
            ), unknown = true)
        assertEquals("3", digit.top.display(en = false))
        assertEquals(11, digit.top.count)
        assertTrue(digit.lowerBound)
        assertEquals("34", pair.top.display(en = false))
        assertEquals(423, pair.top.count)
        assertTrue(pair.lowerBound)
    }

    @Test fun missingStreamsRenderOnlyThoseWithObservedDraws() {
        val rows = listOf(TwoDStreak("3", 2, null, false))
        val pm = TwoDStreakStream(2, true, rows, rows)
        val empty = TwoDStreakStream(0, false, emptyList(), emptyList())
        val streams = mapOf("morning" to empty, "evening" to pm, "combined" to pm)
        assertEquals(listOf("evening", "combined"), TwoDLiveSummary.observedStreams(streams))
        assertTrue(TwoDLiveSummary.observedStreams(mapOf("morning" to empty)).isEmpty())
    }

    @Test fun conditionalResultsStayVisibleIfOnlyOneReferenceCanBeChecked() {
        val known = TwoDMatch(LocalDate.parse("2026-10-27"), DrawSession.MORNING, "35")
        val hit = TwoDConditional("89", listOf(known), listOf(TwoDCount("35", 1)), 0)
        val empty = TwoDConditional(null, emptyList(), emptyList(), 0)
        val report = TwoDPreviousResults(null, known, empty, empty, hit)
        assertEquals(listOf("second"), TwoDLiveSummary.observedConditions(report))
        assertTrue(TwoDLiveSummary.observedConditions(
            TwoDPreviousResults(null, null, empty, empty, empty)).isEmpty())
    }
}
