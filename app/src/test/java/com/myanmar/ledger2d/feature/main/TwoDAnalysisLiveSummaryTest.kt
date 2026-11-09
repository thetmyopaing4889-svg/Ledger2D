package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.TwoDCount
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
}
