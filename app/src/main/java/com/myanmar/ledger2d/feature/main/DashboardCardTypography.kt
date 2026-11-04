package com.myanmar.ledger2d.feature.main

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard

/**
 * Opt-in, shared Agent/Customer dashboard landing-card typography only.
 * Feature screens, selector components, reports and other UI are unaffected.
 */
internal object DashboardCardTypography {
    fun title(languageCode: String): TextStyle =
        LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, languageCode).copy(
            fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
        )

    fun subtitle(languageCode: String): TextStyle =
        LedgerTypographyStandard.style(LedgerTextRole.HELPER, languageCode).copy(
            fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal,
        )
}
