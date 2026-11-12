package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.TwoDCount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchAtlasPresentationTest {
    @Test fun equalFrequencyOutcomesPreferCompactTiles() {
        val sixSingleOutcomes = listOf("24", "63", "65", "67", "82", "95")
            .map { TwoDCount(it, 1) }
        assertTrue(useCompactFrequencyGrid(sixSingleOutcomes))
    }

    @Test fun unequalFrequencyOutcomesPreferComparativeBars() {
        assertFalse(useCompactFrequencyGrid(listOf(
            TwoDCount("24", 4), TwoDCount("63", 2), TwoDCount("65", 1))))
    }

    @Test fun emptyAndSingleOutcomeDoNotProduceUnhelpfulGrids() {
        assertFalse(useCompactFrequencyGrid(emptyList()))
        assertFalse(useCompactFrequencyGrid(listOf(TwoDCount("24", 1))))
    }

    @Test fun equalRepeatedFrequenciesStillUseCompactTiles() {
        assertTrue(useCompactFrequencyGrid(listOf(
            TwoDCount("24", 3), TwoDCount("63", 3), TwoDCount("65", 3))))
    }
}
