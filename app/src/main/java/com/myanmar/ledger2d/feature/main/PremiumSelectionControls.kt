@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors

/** Explicitly opt in at a selection call site, never restyle protected forms
 * or unrelated uses of ScopeDropdown/DateInput through the global theme. */
internal val LocalPremiumSelector = compositionLocalOf { false }
internal val premiumSelectorShape = RoundedCornerShape(16.dp)

@Composable
internal fun premiumSelectorColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFFFFF9FC),
    unfocusedContainerColor = Color(0xEBFFFFFF),
    disabledContainerColor = Color(0x66FFFFFF),
    focusedBorderColor = AppColors.PrimaryDeep,
    unfocusedBorderColor = Color.White,
    disabledBorderColor = Color(0x88FFFFFF),
    focusedLabelColor = AppColors.PrimaryDeep,
    unfocusedLabelColor = AppColors.PrimaryDeep,
    focusedTrailingIconColor = AppColors.PrimaryDeep,
    unfocusedTrailingIconColor = AppColors.PrimaryDeep,
)

@Composable
internal fun PremiumScopeDropdown(label: String, selected: String, options: List<Pair<Long, String>>,
    value: Long, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = true,
    avatar: (@Composable (Long) -> Unit)? = null, onSelect: (Long) -> Unit) {
    CompositionLocalProvider(LocalPremiumSelector provides true) {
        ScopeDropdown(label, selected, options, value, modifier, enabled, compact, avatar, onSelect)
    }
}

@Composable
internal fun PremiumSelectionChip(selected: Boolean, onClick: () -> Unit,
    label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0xEBFFFFFF),
            labelColor = AppColors.PrimaryDeep,
            selectedContainerColor = AppColors.PrimaryDeep,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
internal fun PremiumSelectionButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(19.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryDeep, contentColor = Color.White),
        border = if (enabled) BorderStroke(1.dp, Color.White.copy(alpha = .85f)) else null,
        content = content)
}

@Composable
internal fun PremiumDateInput(value: String, onValue: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    CompositionLocalProvider(LocalPremiumSelector provides true) {
        DateInput(value, onValue, label, modifier, enabled)
    }
}
