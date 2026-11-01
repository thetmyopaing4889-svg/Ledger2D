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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.CommissionLedgerCalculator
import com.myanmar.ledger2d.core.domain.DeviceCalendar
import java.text.NumberFormat
import java.time.DayOfWeek
import java.util.Locale

private fun Long.cardNumber(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun Long.cardMoney(): String = cardNumber() + " ကျပ်"

/** Compact by design: makes every Home feature visible without shrinking the bottom tiles. */
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
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, AppColors.Stone),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(shape = RoundedCornerShape(9.dp), color = AppColors.Blush) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = AppColors.PrimaryDeep,
                        modifier = Modifier.padding(5.dp).size(20.dp))
                }
                Text("ကော်မရှင်ရငွေ", Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                    color = AppColors.Ink)
                if (isClosed) Text("ပိတ်ရက်", color = AppColors.PrimaryDeep,
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text("အသေးစိတ်", fontSize = 10.sp, color = AppColors.PrimaryDeep, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, "ကော်မရှင်အသေးစိတ်ကြည့်ရန်",
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(21.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CommissionSessionTile("မနက်အကြိမ်", money.morning, Modifier.weight(1f))
                CommissionSessionTile("ညနေအကြိမ်", money.evening, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("ဒီနေ့ စုစုပေါင်း", Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, color = AppColors.Ink)
                Text(money.todayTotal.cardMoney(), fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, textAlign = TextAlign.End, color = AppColors.PrimaryDeep)
            }
            HorizontalDivider(color = AppColors.Stone)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money.toDateTotal.cardMoney(), fontWeight = FontWeight.Black,
                    fontSize = 21.sp, lineHeight = 24.sp, color = AppColors.PrimaryDeep)
            }
        }
    }
}

@Composable
private fun CommissionSessionTile(label: String, amount: Long, modifier: Modifier) {
    Surface(modifier = modifier, color = AppColors.Blush.copy(alpha = .5f),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 10.sp,
                color = AppColors.PrimaryDeep, fontWeight = FontWeight.SemiBold)
            Text(amount.cardNumber(), fontSize = 16.sp, lineHeight = 19.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = AppColors.Ink)
        }
    }
}
