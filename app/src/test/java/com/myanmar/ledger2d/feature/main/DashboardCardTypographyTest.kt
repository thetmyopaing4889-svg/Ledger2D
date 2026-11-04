package com.myanmar.ledger2d.feature.main

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DashboardCardTypographyTest {
    @Test fun agentAndCustomerCardsShareOptInLanguageStyles() {
        for (style in listOf(DashboardCardTypography::title, DashboardCardTypography::subtitle)) {
            val en = style("en")
            val my = style("my")
            assertEquals(en.fontSize, my.fontSize)
            assertEquals(en.lineHeight, my.lineHeight)
            assertEquals(FontFamily.SansSerif, en.fontFamily)
            assertNotEquals(FontFamily.SansSerif, my.fontFamily)
        }
    }

    @Test fun titleAndSubtitleAreCompactAndReadable() {
        assertEquals(14.sp, DashboardCardTypography.title("en").fontSize)
        assertEquals(20.sp, DashboardCardTypography.title("my").lineHeight)
        assertEquals(11.sp, DashboardCardTypography.subtitle("en").fontSize)
        assertEquals(16.sp, DashboardCardTypography.subtitle("my").lineHeight)
    }
}
