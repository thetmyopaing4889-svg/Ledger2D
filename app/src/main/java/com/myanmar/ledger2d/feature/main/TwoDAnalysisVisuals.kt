package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard

/**
 * A-06 reference design: the number lives on its own elevated contrast well.
 * Count and supporting text remain outside the well. Never change the app's
 * Noto Sans Myanmar UI / English Sans Serif font selection or font-role policy.
 */
@Composable
internal fun TwoDNumberFocus(
    digits: String,
    en: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 11.dp else 15.dp),
        color = c.secondaryContainer,
        border = BorderStroke(1.dp, c.primary.copy(alpha = .24f)),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Box(
            Modifier.background(Brush.verticalGradient(listOf(
                c.secondaryContainer.copy(alpha = .90f),
                c.primaryContainer.copy(alpha = .93f),
            ))).padding(
                horizontal = if (compact) 11.dp else 15.dp,
                vertical = if (compact) 8.dp else 11.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                digits,
                style = LedgerTypographyStandard.style(LedgerTextRole.MONEY,
                    if (en) "en" else "my").copy(
                    fontSize = (if (compact || digits.length > 9) 20 else 25).sp,
                    lineHeight = (if (compact || digits.length > 9) 27 else 32).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = c.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TwoDResultFrame(
    label: String,
    digits: String,
    countText: String,
    en: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(19.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .28f)),
        shadowElevation = 5.dp,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, modifier = Modifier.fillMaxWidth(),
                style = LedgerTypographyStandard.style(LedgerTextRole.HELPER,
                    if (en) "en" else "my"),
                color = c.onSurfaceVariant, maxLines = 2)
            TwoDNumberFocus(digits, en, modifier = Modifier.fillMaxWidth(), compact = true)
            Text(countText, modifier = Modifier.fillMaxWidth(),
                style = LedgerTypographyStandard.style(LedgerTextRole.TABLE,
                    if (en) "en" else "my"),
                color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
