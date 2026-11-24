package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage

internal data class RiskControlOption(val id: String, val label: String, val description: String = label)

private val controlRuby = listOf(Color(0xFFE45A87), Color(0xFFC42A60), Color(0xFF98123F))
private val controlGlass = listOf(Color.White, Color(0xFFFFF7FA), Color(0xFFFBE6EE))

/** Main tabs share one glass rail; no extra panel or new state. */
@Composable
internal fun PremiumRiskTabs(options: List<RiskControlOption>, selected: String, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().shadow(3.dp, shape, ambientColor = Color(0x22880E4F), spotColor = Color(0x33880E4F))
            .clip(shape).background(Brush.verticalGradient(controlGlass))
            .border(1.5.dp, Color.White, shape).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            RiskControlTile(option, selected == option.id, onClick = { onSelect(option.id) },
                modifier = Modifier.weight(1f), icon = null, segmented = true)
        }
    }
}

/** Four ordinary options on one line. Preserve every real planner option,
 * including the conditional middle plan; reflow only for extra options,
 * very narrow screens, or accessibility font scale. */
@Composable
internal fun PremiumRiskOptions(options: List<RiskControlOption>, selected: String, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val compact = maxWidth >= 264.dp && LocalDensity.current.fontScale <= 1.3f
        val columns = if (compact) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.chunked(columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    line.forEach { option ->
                        val icon = when (option.id) {
                            "low" -> Icons.Default.TrendingDown
                            "balance" -> Icons.Default.Balance
                            "growth" -> Icons.Default.AccountBalanceWallet
                            "budget" -> Icons.Default.Tune
                            else -> Icons.Default.HorizontalRule
                        }
                        RiskControlTile(option, selected == option.id, onClick = { onSelect(option.id) },
                            modifier = Modifier.weight(1f), icon = icon, segmented = false)
                    }
                    // Keep the same cell width for a short last row.
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RiskControlTile(option: RiskControlOption, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier, icon: ImageVector?, segmented: Boolean) {
    val shape = RoundedCornerShape(if (segmented) 12.dp else 13.dp)
    val ink = if (selected) Color.White else AppColors.PrimaryDeep
    Box(
        modifier.heightIn(min = 48.dp)
            .shadow(if (selected || !segmented) 2.dp else 0.dp, shape,
                ambientColor = Color(0x22880E4F), spotColor = Color(0x33880E4F))
            .clip(shape)
            .then(if (selected || !segmented) Modifier.background(
                Brush.verticalGradient(if (selected) controlRuby else controlGlass)
            ).border(1.dp, Color.White.copy(alpha = .85f), shape) else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = option.description }
            .padding(horizontal = 3.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
            Text(option.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                color = ink, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun riskPlanControlOption(id: String, title: String): RiskControlOption {
    val lang = LocalLanguage.current
    val label = when (id) {
        "low" -> lang.text("အရှုံးနည်း", "Low loss")
        "balance" -> lang.text("မျှခြေနီးစပ်", "Balance")
        "middle" -> lang.text("အလယ်အလတ်", "Middle")
        "growth" -> lang.text("ငွေပိုကျန်", "Keep more")
        "budget" -> lang.text("စိတ်ကြိုက်", "Custom")
        else -> title
    }
    return RiskControlOption(id, label, lang.translate(title))
}
