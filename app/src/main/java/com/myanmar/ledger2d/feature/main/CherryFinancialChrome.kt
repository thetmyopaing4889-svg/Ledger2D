package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Home-first financial chrome: use on outer selectors and AGENT groups only.
 * Never apply translucent color or ruby shadows to individual transaction
 * rows, money tables, limits or semantic success/error/closed states.
 *
 * Read-only visual derivative of approved Home, not a global theme change.
 */
private val financeGlassShape = RoundedCornerShape(22.dp)
private val financeBlush = listOf(
    Color(0xFFF6DCE5), Color(0xFFF9E3EB), Color(0xFFFCEEF3),
)

internal fun Modifier.cherryFinancialBackground(): Modifier =
    this.background(Brush.verticalGradient(financeBlush))

@Composable
internal fun CherryFinancialPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = financeGlassShape,
                ambientColor = Color(0x26880E4F),
                spotColor = Color(0x35880E4F),
            )
            .clip(financeGlassShape)
            .background(Color(0xEBFFFFFF))
            .border(1.5.dp, Color.White.copy(alpha = 0.95f), financeGlassShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}
