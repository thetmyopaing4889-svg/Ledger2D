package com.myanmar.ledger2d.feature.main

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeTypographyTest {
    @Test fun compactHomeRolesPreserveSameSizingAcrossLanguages() {
        val roles = listOf<(String) -> androidx.compose.ui.text.TextStyle>(
            HomeTypography::weeklyHeading,
            HomeTypography::drawLabel,
            HomeTypography::commissionHeading,
            HomeTypography::commissionAction,
            HomeTypography::commissionMeta,
            HomeTypography::quickAction,
            HomeTypography::comingSoon,
            HomeTypography::footer,
        )
        for (style in roles) {
            val english = style("en")
            val myanmar = style("my")
            assertEquals(english.fontSize, myanmar.fontSize)
            assertEquals(english.lineHeight, myanmar.lineHeight)
            assertEquals(FontFamily.SansSerif, english.fontFamily)
            assertNotEquals(FontFamily.SansSerif, myanmar.fontFamily)
        }
    }

    @Test fun denseLabelsDoNotGrowPastExistingHomeSizes() {
        assertEquals(16.sp, HomeTypography.weeklyHeading("my").fontSize)
        assertEquals(24.sp, HomeTypography.weeklyHeading("my").lineHeight)
        assertEquals(14.sp, HomeTypography.commissionHeading("my").fontSize)
        assertEquals(10.sp, HomeTypography.commissionRowLabel("my", false).fontSize)
        assertEquals(11.sp, HomeTypography.commissionRowLabel("my", true).fontSize)
        assertEquals(11.sp, HomeTypography.quickAction("my").fontSize)
        assertEquals(8.sp, HomeTypography.comingSoon("my").fontSize)
    }
}
