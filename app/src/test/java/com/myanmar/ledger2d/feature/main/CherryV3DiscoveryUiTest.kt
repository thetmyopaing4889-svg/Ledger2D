package com.myanmar.ledger2d.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Display-only ratings: do not change original Champion or win odds. */
class CherryV3DiscoveryUiTest {
    @Test fun everyHistoricalStreakRatingIsExactlyFiveSmallStars() {
        for (streak in 0..200) {
            val stars = cherryV3HistoricalStars(streak)
            assertEquals(5, stars.length)
            assertTrue(stars.all { it == '★' || it == '☆' })
        }
    }

    @Test fun tenPlusHistoricalStreakIsFiveStarsWithoutForecastClaim() {
        assertEquals("★☆☆☆☆", cherryV3HistoricalStars(3))
        assertEquals("★★☆☆☆", cherryV3HistoricalStars(4))
        assertEquals("★★★☆☆", cherryV3HistoricalStars(5))
        assertEquals("★★★★☆", cherryV3HistoricalStars(8))
        assertEquals("★★★★★", cherryV3HistoricalStars(10))
        assertEquals("★★★★★", cherryV3HistoricalStars(25))
    }
}
