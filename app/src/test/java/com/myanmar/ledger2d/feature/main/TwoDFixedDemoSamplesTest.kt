package com.myanmar.ledger2d.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard preview intentionally has complete, obviously artificial
 * output for every analysis, including cases real history cannot show yet.
 */
class TwoDFixedDemoSamplesTest {
    @Test fun allThreeMissingStreamsHaveBothPriorities() {
        val rows = TwoDFixedDemoSamples.absent
        assertEquals(listOf("AM", "PM", "ALL"), rows.map { it.session })
        rows.forEach { row ->
            assertEquals(1, row.digit.value.length)
            assertEquals(2, row.pair.value.length)
            assertTrue((row.digit.count.removeSuffix("+").toIntOrNull() ?: 0) > 0)
            assertTrue(row.digit.count.endsWith("+"))
            assertTrue(row.pair.count.endsWith("+"))
        }
    }

    @Test fun allDemoSectionsArePopulatedWithoutHistory() {
        assertEquals(3, TwoDFixedDemoSamples.previous.size)
        assertEquals(5, TwoDFixedDemoSamples.special.size)
        assertTrue(TwoDFixedDemoSamples.missingDigits.isNotEmpty())
        assertTrue(TwoDFixedDemoSamples.firstSeen.isNotEmpty())
        assertTrue(TwoDFixedDemoSamples.lastWeekMissing.isNotEmpty())
        assertEquals(3, TwoDFixedDemoSamples.returns.size)
        assertEquals("02·46", TwoDFixedDemoSamples.previous[1].second.value)
    }
}
