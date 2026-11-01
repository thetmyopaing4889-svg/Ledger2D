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
 * Home-only commission presentation; saved snapshot totals and detail reports
 * remain unchanged. Two columns keep the four sums legible without a tall card.
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
    val isClosed = today.dayOfWeek == DayOfWeek.SATURDAY ||
        today.dayOfWeek == DayOfWeek.SUNDAY || closedDays.any { it.date == today }

    Card(
        onClick = onDetails,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, AppColors.Stone),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null,
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(20.dp))
                Text("ကော်မရှင်ရငွေ", modifier = Modifier.weight(1f),
                    fontSize = 14.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold, color = AppColors.Ink)
                if (isClosed) Text("ပိတ်ရက်", fontSize = 10.sp,
                    color = AppColors.PrimaryDeep, fontWeight = FontWeight.Bold)
                Text("အသေးစိတ်", fontSize = 11.sp,
                    color = AppColors.PrimaryDeep, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, "ကော်မရှင်အသေးစိတ်ကြည့်ရန်",
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(18.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1.12f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HomeCommissionAmountRow("မနက်အကြိမ်", totals.morning, false)
                    HomeCommissionAmountRow("ညနေအကြိမ်", totals.evening, false)
                    HorizontalDivider(color = AppColors.Stone.copy(alpha = .75f))
                    HomeCommissionAmountRow("ဒီနေ့စုစုပေါင်း", totals.todayTotal, true)
                }
                VerticalDivider(modifier = Modifier.height(62.dp), color = AppColors.Stone)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း",
                        fontSize = 11.sp, lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        totals.toDateTotal.cardAmount() + " ကျပ်",
                        fontSize = if (totals.toDateTotal.cardAmount().length > 13) 13.sp else 17.sp,
                        lineHeight = 20.sp, fontWeight = FontWeight.Black,
                        color = AppColors.PrimaryDeep, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCommissionAmountRow(label: String, amount: Long, emphasis: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = if (emphasis) 11.sp else 10.sp,
            lineHeight = 14.sp, color = if (emphasis) AppColors.PrimaryDeep
                else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            amount.cardAmount(),
            fontSize = if (amount.cardAmount().length > 11) 11.sp else 14.sp,
            lineHeight = 18.sp,
            color = if (emphasis) AppColors.PrimaryDeep else AppColors.Ink,
            fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
