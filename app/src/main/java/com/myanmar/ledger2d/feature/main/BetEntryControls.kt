package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.QuickFormat

/** UI-only grouping; enum identities and parser remain unchanged. */
internal val betEntryFormatRows = listOf(
    listOf(QuickFormat.MANUAL),
    listOf(QuickFormat.POWER, QuickFormat.ASTROLOGY, QuickFormat.DOUBLES),
    listOf(QuickFormat.SIBLINGS, QuickFormat.COMBINATION, QuickFormat.COMBINATION_DOUBLES),
    listOf(QuickFormat.ROUND, QuickFormat.HEAD, QuickFormat.TAIL),
    listOf(QuickFormat.EVEN_ODD, QuickFormat.ODD_EVEN, QuickFormat.EVEN_EVEN, QuickFormat.ODD_ODD),
)

@Composable
internal fun BetEntryFormatGrid(selected: QuickFormat, enabled: Boolean, onSelect: (QuickFormat) -> Unit) {
    val language = LocalLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(language.translate("အမြန်ထည့်သွင်းပုံများ"), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xEBFFFFFF),
            border = BorderStroke(1.dp, Color.White)) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                betEntryFormatRows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { format ->
                            val active = selected == format
                            val shape = RoundedCornerShape(12.dp)
                            Box(
                                Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                                    .background(if (active) AppColors.PrimaryDeep else Color(0xFFFFF5F8))
                                    .border(1.dp, if (active) Color.White else Color(0xFFEFC4D2), shape)
                                    .selectable(selected = active, enabled = enabled, role = Role.RadioButton,
                                        onClick = { onSelect(format) })
                                    .padding(horizontal = 3.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (format == QuickFormat.MANUAL) language.text("အထူး", "Special") else language.translate(format.label),
                                    color = if (active) Color.White else AppColors.PrimaryDeep,
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Exact approved profile-form Save surface, scoped to the betting action. */
@Composable
internal fun BetEntryConfirmButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, saving: Boolean) {
    val saveEnabled = enabled && !saving
    val actionShape = RoundedCornerShape(27.dp)
    Button(
        onClick = onClick, enabled = saveEnabled,
        modifier = modifier.height(54.dp)
            .homeRubyShadow(radius = 27.dp, blur = 10.dp, offsetY = 4.dp,
                tint = if (saveEnabled) Color(0x52880E4F) else Color(0x16880E4F))
            .clip(actionShape)
            .background(Brush.verticalGradient(
                if (saveEnabled) listOf(Color(0xFFF44982), Color(0xFFBC0C50), Color(0xFF820A38))
                else listOf(Color(0xFFE9E1E5), Color(0xFFDAD1D6), Color(0xFFCAC0C7))))
            .border(1.dp, if (saveEnabled) Color(0xBBFFD7E5) else Color(0xA6FFFFFF), actionShape),
        shape = actionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
            contentColor = Color.White, disabledContentColor = Color(0xFF857782)),
        elevation = null,
    ) {
        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(LocalLanguage.current.translate("Confirm"), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black)
    }
}
