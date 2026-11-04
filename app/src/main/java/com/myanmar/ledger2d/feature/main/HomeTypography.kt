package com.myanmar.ledger2d.feature.main

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard

/**
 * Home-specific, opt-in compact type roles.
 *
 * The Home screen has a fixed, non-scrolling layout. Preserve the current text
 * sizes and line heights while applying the approved *candidate* font family:
 * English SansSerif and Noto Sans Myanmar UI. No geometry is changed here.
 *
 * Financial values and the week date/numeric cells are intentionally excluded
 * from this first batch: shrinking or ellipsizing money is not acceptable.
 */
internal object HomeTypography {
    private fun compact(
        role: LedgerTextRole,
        languageCode: String,
        sizeSp: Int,
        lineSp: Int,
        weight: FontWeight,
    ): TextStyle = LedgerTypographyStandard.style(role, languageCode).copy(
        fontSize = sizeSp.sp,
        lineHeight = lineSp.sp,
        fontWeight = weight,
    )

    fun weeklyHeading(code: String) = compact(
        LedgerTextRole.CARD_TITLE, code, 16, 24,
        if (code == "en") FontWeight.Black else FontWeight.Bold,
    )

    fun drawLabel(code: String) = compact(
        LedgerTextRole.TABLE, code, 11, 16, FontWeight.Bold,
    )

    fun commissionHeading(code: String) = compact(
        LedgerTextRole.CARD_TITLE, code, 14, 19, FontWeight.Bold,
    )

    fun commissionAction(code: String) = compact(
        LedgerTextRole.HELPER, code, 11, 15, FontWeight.SemiBold,
    )

    fun commissionMeta(code: String) = compact(
        LedgerTextRole.HELPER, code, 11, 15, FontWeight.Normal,
    )

    fun commissionRowLabel(code: String, emphasized: Boolean) = compact(
        LedgerTextRole.HELPER, code, if (emphasized) 11 else 10, 14,
        if (emphasized) FontWeight.Bold else FontWeight.Normal,
    )

    fun quickAction(code: String) = compact(
        LedgerTextRole.BUTTON, code, 11, 14, FontWeight.SemiBold,
    )

    fun comingSoon(code: String) = compact(
        LedgerTextRole.HELPER, code, 8, 10, FontWeight.SemiBold,
    )

    fun footer(code: String) = compact(
        LedgerTextRole.HELPER, code, 11, 16, FontWeight.Normal,
    )
}
