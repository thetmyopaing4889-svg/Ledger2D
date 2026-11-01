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
 * All four commission values in a single compact two-column strip.
 * The Header, Weekly Results, buttons and 76dp Analysis/Data tiles keep their old sizes.
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
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, AppColors.Stone),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, null,
                        tint = AppColors.PrimaryDeep, modifier = Modifier.size(14.dp))
                    Text("ကော်မရှင်ရငွေ", color = AppColors.Ink,
                        fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                    if (isClosed) Text("ပိတ်ရက်", color = AppColors.PrimaryDeep,
                        fontSize = 9.sp, maxLines = 1)
                }
                Text(
                    "မနက် ${totals.morning.cardAmount()}  •  ညနေ ${totals.evening.cardAmount()}",
                    fontSize = 10.sp, lineHeight = 13.sp,
                    color = AppColors.Ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "ဒီနေ့စုစုပေါင်း ${totals.todayTotal.cardAmount()}",
                    fontSize = 11.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            VerticalDivider(Modifier.height(40.dp), color = AppColors.Stone)
            Column(Modifier.weight(1.0f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း",
                    fontSize = 10.sp, lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        totals.toDateTotal.cardAmount() + " ကျပ်",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp, lineHeight = 19.sp,
                        fontWeight = FontWeight.Black, color = AppColors.PrimaryDeep,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Default.ChevronRight, "အသေးစိတ်ကြည့်ရန်",
                        tint = AppColors.PrimaryDeep, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
