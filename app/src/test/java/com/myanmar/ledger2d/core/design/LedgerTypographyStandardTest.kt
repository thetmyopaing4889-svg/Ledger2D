package com.myanmar.ledger2d.core.design

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTypographyStandardTest {
    @Test fun allMaterialRolesUseLanguageFontWithoutChangingTextGeometry() {
        val english = LedgerTypographyStandard.materialTypography(AppTypography, "en")
        val myanmar = LedgerTypographyStandard.materialTypography(AppTypography, "my")
        val pairs = listOf(
            english.displayLarge to myanmar.displayLarge,
            english.displayMedium to myanmar.displayMedium,
            english.displaySmall to myanmar.displaySmall,
            english.headlineLarge to myanmar.headlineLarge,
            english.headlineMedium to myanmar.headlineMedium,
            english.headlineSmall to myanmar.headlineSmall,
            english.titleLarge to myanmar.titleLarge,
            english.titleMedium to myanmar.titleMedium,
            english.titleSmall to myanmar.titleSmall,
            english.bodyLarge to myanmar.bodyLarge,
            english.bodyMedium to myanmar.bodyMedium,
            english.bodySmall to myanmar.bodySmall,
            english.labelLarge to myanmar.labelLarge,
            english.labelMedium to myanmar.labelMedium,
            english.labelSmall to myanmar.labelSmall,
        )
        pairs.forEach { (en, my) ->
            assertEquals(en.fontSize, my.fontSize)
            assertEquals(en.lineHeight, my.lineHeight)
            assertEquals(en.fontWeight, my.fontWeight)
            assertEquals(en.letterSpacing, my.letterSpacing)
            assertEquals(FontFamily.SansSerif, en.fontFamily)
            assertNotEquals(FontFamily.SansSerif, my.fontFamily)
        }
    }
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
