package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.CherryMathGrade
import org.junit.Assert.assertEquals
import org.junit.Test

/** Presentation-only regression: DO NOT modify grading or selection logic. */
class CherryMathUiPresentationTest {
    @Test fun mathEvidenceGradesAppearAsOneToFiveStars() {
        assertEquals("★★★★★", cherryMathStars(CherryMathGrade.A))
        assertEquals("★★★☆☆", cherryMathStars(CherryMathGrade.B))
        assertEquals("★☆☆☆☆", cherryMathStars(CherryMathGrade.C))
    }

    @Test fun everyStarRatingUsesExactlyFiveGlyphs() {
        CherryMathGrade.entries.forEach { grade ->
            val stars = cherryMathStars(grade)
            assertEquals(5, stars.length)
            assertEquals(true, stars.all { it == '★' || it == '☆' })
        }
    }
}
