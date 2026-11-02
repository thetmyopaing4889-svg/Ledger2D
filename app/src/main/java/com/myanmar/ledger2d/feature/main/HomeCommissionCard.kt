package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.clickable
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
import com.myanmar.ledger2d.core.design.AuroraGlassCard
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

    AuroraGlassCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onDetails),
        animated = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null,
                    tint = AppColors.ChampagneGold, modifier = Modifier.size(20.dp))
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ကော်မရှင်ရငွေ"), modifier = Modifier.weight(1f),
                    fontSize = 16.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
                if (isClosed) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပိတ်ရက်"), fontSize = 10.sp,
                    color = AppColors.ChampagneGold, fontWeight = FontWeight.Bold)
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အသေးစိတ်"), fontSize = 11.sp,
                    color = AppColors.ChampagneGold, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, "ကော်မရှင်အသေးစိတ်ကြည့်ရန်",
                    tint = AppColors.ChampagneGold, modifier = Modifier.size(18.dp))
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
                    HomeCommissionAmountRow(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("မနက်အကြိမ်", "AM"), totals.morning, false)
                    HomeCommissionAmountRow(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ညနေအကြိမ်", "PM"), totals.evening, false)
                    HorizontalDivider(color = Color.White.copy(alpha = .20f))
                    HomeCommissionAmountRow(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ဒီနေ့စုစုပေါင်း", "Today total"), totals.todayTotal, true)
                }
                VerticalDivider(modifier = Modifier.height(80.dp), color = Color.White.copy(alpha = .25f))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း", "Total to date"),
                        fontSize = 12.sp, lineHeight = 19.sp,
                        color = Color.White.copy(alpha = .78f),
                        maxLines = 2,
                    )
                    Text(
                        totals.toDateTotal.cardAmount() + com.myanmar.ledger2d.core.design.LocalLanguage.current.text(" ကျပ်", " MMK"),
                        fontSize = if (totals.toDateTotal.cardAmount().length > 13) 16.sp else 21.sp,
                        lineHeight = 29.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, maxLines = 3, softWrap = true,
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
            fontSize = if (emphasis) 13.sp else 12.sp,
            lineHeight = 19.sp, color = if (emphasis) AppColors.ChampagneGold
                else Color.White.copy(alpha = .78f),
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            amount.cardAmount(),
            fontSize = if (amount.cardAmount().length > 11) 14.sp else 16.sp,
            lineHeight = 23.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold, maxLines = 2, softWrap = true,
        )
    }
}
