package com.myanmar.ledger2d.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.R

/**
 * Opt-in typography tokens for gradual EN/MM rollout.
 *
 * Deliberately does NOT replace MaterialTheme.typography or affect existing screens.
 * Each screen must explicitly opt in, so appearance changes can be visually verified
 * without accidentally rebuilding the home, reports, financial flows or JPEG renderers.
 */
enum class LedgerTextRole {
    APP_HEADER,
    SECTION_TITLE,
    CARD_TITLE,
    BODY,
    HELPER,
    DROPDOWN_LABEL,
    DROPDOWN_VALUE,
    BUTTON,
    MONEY,
    TABLE,
}

data class LedgerTypeToken(
    val sizeSp: Int,
    val englishLineHeightSp: Int,
    val myanmarLineHeightSp: Int,
    val weight: FontWeight,
)

object LedgerTypographyStandard {
    private val notoMyanmar = FontFamily(
        Font(R.font.noto_sans_myanmar_variable, weight = FontWeight.Normal),
        Font(R.font.noto_sans_myanmar_variable, weight = FontWeight.Medium),
        Font(R.font.noto_sans_myanmar_variable, weight = FontWeight.SemiBold),
        Font(R.font.noto_sans_myanmar_variable, weight = FontWeight.Bold),
    )

    // Candidate standard v1: real-device visual sign-off is still required.
    val tokens: Map<LedgerTextRole, LedgerTypeToken> = mapOf(
        LedgerTextRole.APP_HEADER to LedgerTypeToken(19, 25, 27, FontWeight.SemiBold),
        LedgerTextRole.SECTION_TITLE to LedgerTypeToken(17, 23, 26, FontWeight.Bold),
        LedgerTextRole.CARD_TITLE to LedgerTypeToken(16, 23, 25, FontWeight.SemiBold),
        LedgerTextRole.BODY to LedgerTypeToken(14, 21, 23, FontWeight.Normal),
        LedgerTextRole.HELPER to LedgerTypeToken(13, 19, 21, FontWeight.Normal),
        LedgerTextRole.DROPDOWN_LABEL to LedgerTypeToken(13, 19, 20, FontWeight.Normal),
        LedgerTextRole.DROPDOWN_VALUE to LedgerTypeToken(16, 24, 26, FontWeight.Normal),
        LedgerTextRole.BUTTON to LedgerTypeToken(14, 20, 22, FontWeight.Medium),
        LedgerTextRole.MONEY to LedgerTypeToken(18, 24, 26, FontWeight.Bold),
        LedgerTextRole.TABLE to LedgerTypeToken(12, 18, 20, FontWeight.Medium),
    )

    /** The configured language alone controls the requested font; the screen layout does not change. */
    fun style(role: LedgerTextRole, languageCode: String): TextStyle {
        val token = requireNotNull(tokens[role]) { "Missing typography role: $role" }
        val isMyanmar = languageCode != "en"
        return TextStyle(
            fontFamily = if (isMyanmar) notoMyanmar else FontFamily.SansSerif,
            fontSize = token.sizeSp.sp,
            lineHeight = (if (isMyanmar) token.myanmarLineHeightSp else token.englishLineHeightSp).sp,
            fontWeight = token.weight,
        )
    }
}
