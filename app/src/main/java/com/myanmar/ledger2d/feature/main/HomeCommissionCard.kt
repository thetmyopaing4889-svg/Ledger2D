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

private fun Long.cardAmount() = NumberFormat.getIntegerInstance(Locale.US).format(this)

/**
 * One low-profile card only. Does not change the height or appearance of
 * the Header, Weekly Results, New Agent/Customer or future Analysis/Data tiles.
 */
@Composable
internal fun HomeCommissionCard(
    vm: LedgerViewModel,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = DeviceCalendar.today()
    val ledger = remember(today) { vm.commissionLedger(today) }
    val lines by ledger.collectAsStateWithLifecycle(initialValue = emptyList())
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val totals = CommissionLedgerCalculator.overview(lines, today)
    val closed = today.dayOfWeek == DayOfWeek.SATURDAY ||
        today.dayOfWeek == DayOfWeek.SUNDAY || closedDays.any { it.date == today }

    Card(
        onClick = onDetails,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, AppColors.Stone),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AccountBalanceWallet, null,
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(18.dp))
                Text("ကော်မရှင်ရငွေ", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = AppColors.Ink, maxLines = 1)
                if (closed) Text("ပိတ်ရက်", color = AppColors.PrimaryDeep,
                    fontSize = 10.sp)
                Text("အသေးစိတ်", color = AppColors.PrimaryDeep, fontSize = 10.sp)
                Icon(Icons.Default.ChevronRight, "အသေးစိတ်ကြည့်ရန်",
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(17.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Column(Modifier.weight(1.28f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CommissionMiniAmount("မနက်", totals.morning, Modifier.weight(1f))
                        CommissionMiniAmount("ညနေ", totals.evening, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ဒီနေ့စုစုပေါင်း", Modifier.weight(1f), fontSize = 10.sp,
                            color = AppColors.PrimaryDeep, maxLines = 1)
                        Text(totals.todayTotal.cardAmount(), fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep,
                            maxLines = 1)
                    }
                }
                VerticalDivider(modifier = Modifier.height(43.dp), color = AppColors.Stone)
                Column(Modifier.weight(1.12f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း",
                        fontSize = 10.sp, lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Text(totals.toDateTotal.cardAmount() + " ကျပ်", fontSize = 16.sp,
                        lineHeight = 20.sp, fontWeight = FontWeight.Black,
                        color = AppColors.PrimaryDeep, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CommissionMiniAmount(label: String, amount: Long, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1)
        Text(amount.cardAmount(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
