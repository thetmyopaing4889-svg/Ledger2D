package com.myanmar.ledger2d.core.design

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTypographyStandardTest {
    @Test fun everyRoleHasAReadableCandidateToken() {
        assertEquals(LedgerTextRole.entries.toSet(), LedgerTypographyStandard.tokens.keys)
        assertTrue(LedgerTypographyStandard.tokens.values.all {
            it.sizeSp in 12..19 &&
            it.englishLineHeightSp >= it.sizeSp &&
            it.myanmarLineHeightSp >= it.sizeSp
        })
    }

    @Test fun enAndMyanmarShareFontSizeButUseTheirOwnLineHeightAndFamily() {
        for (role in LedgerTextRole.entries) {
            val en = LedgerTypographyStandard.style(role, "en")
            val my = LedgerTypographyStandard.style(role, "my")
            assertEquals(LedgerTypographyStandard.tokens.getValue(role).sizeSp.sp, en.fontSize)
            assertEquals((LedgerTypographyStandard.tokens.getValue(role).myanmarSizeSp ?: LedgerTypographyStandard.tokens.getValue(role).sizeSp).sp, my.fontSize)
            assertEquals(FontFamily.SansSerif, en.fontFamily)
            assertNotEquals(FontFamily.SansSerif, my.fontFamily)
            assertEquals(en.lineHeight, my.lineHeight)
        }
    }

    @Test fun settingsPilotKeepsExistingEnglishSizesAndWeights() {
        val enSection = LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE, "en")
        val enHelper = LedgerTypographyStandard.style(LedgerTextRole.HELPER, "en")
        assertEquals(17.sp, enSection.fontSize)
        val mySection = LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE, "my")
        assertEquals(16.sp, mySection.fontSize)
        assertEquals(FontWeight.SemiBold, mySection.fontWeight)
        assertEquals(23.sp, enSection.lineHeight)
        assertEquals(FontWeight.Bold, enSection.fontWeight)
        assertEquals(13.sp, enHelper.fontSize)
        assertEquals(19.sp, enHelper.lineHeight)
        assertEquals(FontWeight.Normal, enHelper.fontWeight)
    }

    @Test fun myanmarPilotMatchesEnglishLineHeightAtBaseline() {
        val mySection = LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE, "my")
        val myHelper = LedgerTypographyStandard.style(LedgerTextRole.HELPER, "my")
        assertEquals(23.sp, mySection.lineHeight)
        assertEquals(19.sp, myHelper.lineHeight)
    }
}
