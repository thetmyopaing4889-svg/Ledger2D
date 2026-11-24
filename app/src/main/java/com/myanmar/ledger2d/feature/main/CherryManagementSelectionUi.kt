package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.myanmar.ledger2d.core.design.AppColors

/**
 * Home-first presentation ONLY for the Management selection screens.
 * Do not alter the original Home glass helpers, form UI, selectors, data or routes.
 * Static Compose elevation rather than per-frame blur keeps scrolling lightweight.
 */
internal fun Modifier.cherryManagementScreenBackground(): Modifier = this.background(
    Brush.verticalGradient(
        listOf(Color(0xFFF6DCE5), Color(0xFFF9E3EB), Color(0xFFFCEEF3))
    )
)

private val managementSelectionShape = RoundedCornerShape(22.dp)
private val managementSummaryShape = RoundedCornerShape(16.dp)

@Composable
internal fun CherryManagementSelectionPane(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 14.dp,
    itemSpacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .shadow(
                elevation = 5.dp, shape = managementSelectionShape,
                ambientColor = Color(0x26880E4F), spotColor = Color(0x35880E4F),
            )
            .clip(managementSelectionShape)
            .background(Color(0xEBFFFFFF))
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.95f)), managementSelectionShape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content,
    )
}

/** A low-contrast selected-person summary: no additional opaque Card layers. */
@Composable
internal fun CherryManagementSelectionSummary(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(managementSummaryShape)
            .background(Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0xBFFFF0F6))))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.9f)), managementSummaryShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun CherryManagementPrimaryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.PrimaryDeep,
            contentColor = Color.White,
        ),
        content = content,
    )
}
