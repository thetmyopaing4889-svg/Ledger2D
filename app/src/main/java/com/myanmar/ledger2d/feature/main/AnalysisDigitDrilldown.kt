package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myanmar.ledger2d.core.domain.AnalysisDigitGroup
import com.myanmar.ledger2d.core.domain.AnalysisDigitGroups
import com.myanmar.ledger2d.core.domain.AnalysisResult
import com.myanmar.ledger2d.core.domain.DigitScenario
import java.text.NumberFormat
import java.util.Locale

private fun AnalysisDigitGroup.mmTitle(): String = when (this) {
    AnalysisDigitGroup.UNSTAKED -> "မထိုးထားသောဂဏန်း"
    AnalysisDigitGroup.STAKED_PROFIT -> "ထိုးထားပြီး မြတ်နိုင်"
    AnalysisDigitGroup.STAKED_LOSS -> "ထိုးထားပြီး ရှုံးနိုင်"
    AnalysisDigitGroup.BREAK_EVEN -> "အရင်းကျေ"
}
private fun Long.analysisAmount(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun Long.analysisSigned(): String = (if (this > 0L) "+" else "") + analysisAmount()

/** A tappable breakdown row, including the count. Zero-count groups remain visible but inert. */
@Composable
internal fun AnalysisDigitGroupRow(
    label: String,
    count: Int,
    open: () -> Unit,
) {
    val enabled = count > 0
    Surface(
        onClick = open,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$count ကွက်", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            if (enabled) Icon(Icons.Default.ChevronRight, contentDescription = "ဂဏန်းစာရင်းကြည့်ရန်",
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Full-height read-only grid. It uses the SAME digit tile as the existing
 * digit-list feature, and the SAME per-digit stake/payout as the risk table.
 * No bet writes, edits, forwarding or permission changes occur here.
 */
@Composable
internal fun AnalysisDigitDrilldown(
    source: AnalysisResult,
    group: AnalysisDigitGroup,
    winningDigit: String?,
    onClose: () -> Unit,
) {
    val rows = remember(source, group) { AnalysisDigitGroups.select(source, group) }
    var selected by remember(source, group) { mutableStateOf<DigitScenario?>(null) }
    val columns = if (group == AnalysisDigitGroup.UNSTAKED) 5 else 3

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(group.mmTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${rows.size} ကွက် • ဂဏန်းကိုနှိပ်၍ ငွေစာရင်းကြည့်နိုင်သည်",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Analysis သို့ပြန်ရန်")
                    }
                }
                HorizontalDivider()
                if (rows.isEmpty()) {
                    Text("ဒီအုပ်စုမှာ ဂဏန်းမရှိပါ။", modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(rows, key = { it.digit }) { item ->
                            DigitViewCard(
                                digit = item.digit,
                                amount = item.stake,
                                isWinner = winningDigit == item.digit,
                                isClosed = item.closed,
                                isSpecial = false,
                                percentUsed = item.percentUsed,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selected = item },
                            )
                        }
                    }
                }
                Text("ထိုးကြေး 0 ဆိုတာ မထိုးထားတာပါ။ လက်ခံလို့ရတဲ့ဂဏန်းဆိုတဲ့ အဓိပ္ပာယ် မဟုတ်ပါ။",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    selected?.let { scenario ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("ဂဏန်း ${scenario.digit}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    AnalysisDigitDetailLine("ထိုးကြေး", "${scenario.stake.analysisAmount()} ကျပ်")
                    AnalysisDigitDetailLine("ပေါက်ရင် ပြန်လျော်ငွေ", "${scenario.payout.analysisAmount()} ကျပ်")
                    HorizontalDivider()
                    AnalysisDigitDetailLine(
                        "မကာဘဲ အမြတ်/အရှုံး",
                        "${scenario.profitLoss.analysisSigned()} ကျပ်",
                        tint = if (scenario.profitLoss < 0L) MaterialTheme.colorScheme.error else Color(0xFF18794E),
                    )
                    if (scenario.closed) Text("ဒီဂဏန်းကို ပိတ်ထားသည်။",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (scenario.limit != null) {
                        AnalysisDigitDetailLine("Limit", "${scenario.limit.analysisAmount()} ကျပ်")
                        scenario.percentUsed?.let {
                            AnalysisDigitDetailLine("Limit သုံးစွဲမှု", "$it%")
                        }
                    }
                    if (winningDigit == scenario.digit) Text("အဲဒီအကြိမ်၏ ပေါက်ဂဏန်းဖြစ်သည်။",
                        style = MaterialTheme.typography.bodySmall)
                    Text("ဤငွေစာရင်းသည် ကော်မရှင်မပါဘဲ မကာထားသော အခြေအနေအတွက် တွက်ထားသည်။",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("ဂဏန်းစာရင်းသို့ပြန်ရန်") }
            }
        )
    }
}

@Composable
private fun AnalysisDigitDetailLine(label: String, value: String, tint: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = tint ?: MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    }
}
