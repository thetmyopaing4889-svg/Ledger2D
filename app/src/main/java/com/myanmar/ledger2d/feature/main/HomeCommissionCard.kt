package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.CommissionLedgerCalculator
import com.myanmar.ledger2d.core.domain.DeviceCalendar
import java.text.NumberFormat
import java.time.DayOfWeek
import java.util.Locale

private fun Long.cardMoney(): String = NumberFormat.getIntegerInstance(Locale.US).format(this) + " ကျပ်"

@Composable
internal fun HomeCommissionCard(vm: LedgerViewModel, onDetails: () -> Unit) {
    val today = DeviceCalendar.today()
    val flow = remember(today) { vm.commissionLedger(today) }
    val lines by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val closed by vm.closedDays.collectAsStateWithLifecycle()
    val money = CommissionLedgerCalculator.overview(lines, today)
    val isClosed = today.dayOfWeek == DayOfWeek.SATURDAY ||
        today.dayOfWeek == DayOfWeek.SUNDAY || closed.any { it.date == today }

    Card(
        onClick = onDetails,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, AppColors.Stone),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = AppColors.Blush) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = AppColors.PrimaryDeep,
                        modifier = Modifier.padding(9.dp).size(24.dp))
                }
                Text("ကော်မရှင်ရငွေ", modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                    color = AppColors.Ink)
                Icon(Icons.Default.ChevronRight, "အသေးစိတ်ကြည့်ရန်",
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(23.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ဒီနေ့ ရရှိသော ကော်မရှင်", modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium)
                if (isClosed) Text("ပိတ်ရက်", color = AppColors.PrimaryDeep,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                CommissionSessionTile("မနက်အကြိမ်", money.morning, Modifier.weight(1f))
                CommissionSessionTile("ညနေအကြိမ်", money.evening, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("ဒီနေ့ စုစုပေါင်း", Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, color = AppColors.Ink)
                Text(money.todayTotal.cardMoney(), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End,
                    color = AppColors.PrimaryDeep)
            }
            HorizontalDivider(color = AppColors.Stone)
            Text("ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money.toDateTotal.cardMoney(), fontWeight = FontWeight.Black,
                fontSize = 23.sp, lineHeight = 29.sp, color = AppColors.PrimaryDeep)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                Text("အသေးစိတ်ကြည့်ရန်", style = MaterialTheme.typography.labelMedium,
                    color = AppColors.PrimaryDeep, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, null, tint = AppColors.PrimaryDeep,
                    modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun CommissionSessionTile(label: String, amount: Long, modifier: Modifier) {
    Surface(modifier = modifier, color = AppColors.Blush.copy(alpha = .5f),
        shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = AppColors.PrimaryDeep, fontWeight = FontWeight.SemiBold)
            Text(amount.cardMoney(), fontSize = 17.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.Bold, color = AppColors.Ink)
        }
    }
}
