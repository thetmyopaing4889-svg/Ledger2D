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

private fun Long.cardAmount() = NumberFormat.getIntegerInstance(Locale.US).format(this)

/**
 * Preserve the familiar compact card at ordinary amounts. Switch only when a
 * value may not fit into a two-column card; never abbreviate monetary values.
 */
internal fun homeCommissionNeedsWideRows(
    morning: Long, evening: Long, todayTotal: Long, toDateTotal: Long,
): Boolean = listOf(morning, evening, todayTotal).any { it.cardAmount().length > 10 } ||
    toDateTotal.cardAmount().length > 9

/** Optional wrap points after thousands separators. All digits remain visible. */
internal fun homeCommissionWrapNumber(amount: Long): String =
    amount.cardAmount().replace(",", ",\u200B")


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
    val language = com.myanmar.ledger2d.core.design.LocalLanguage.current
    val today = DeviceCalendar.today()
    val ledger = remember(today) { vm.commissionLedger(today) }
    val lines by ledger.collectAsStateWithLifecycle(initialValue = emptyList())
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val totals = CommissionLedgerCalculator.overview(lines, today)
    val showWideRows = homeCommissionNeedsWideRows(totals.morning, totals.evening, totals.todayTotal, totals.toDateTotal)
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
                Text(language.translate("ကော်မရှင်ရငွေ"), modifier = Modifier.weight(1f),
                    style = HomeTypography.commissionHeading(language.code),
                    color = AppColors.Ink)
                if (isClosed) Text(language.translate("ပိတ်ရက်"), fontSize = 10.sp,
                    color = AppColors.PrimaryDeep, fontWeight = FontWeight.Bold)
                Text(language.translate("အသေးစိတ်"), style = HomeTypography.commissionAction(language.code),
                    color = AppColors.PrimaryDeep)
                Icon(Icons.Default.ChevronRight, "ကော်မရှင်အသေးစိတ်ကြည့်ရန်",
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(18.dp))
            }
            if (showWideRows) {
                // Rare large-value presentation: use the entire card width.
                // The wording and raw monetary totals are the same as compact mode.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    HomeCommissionWideRow(language.text("မနက်အကြိမ်", "AM"), totals.morning, false)
                    HomeCommissionWideRow(language.text("ညနေအကြိမ်", "PM"), totals.evening, false)
                    HorizontalDivider(color = AppColors.Stone.copy(alpha = .75f))
                    HomeCommissionWideRow(language.text("ဒီနေ့စုစုပေါင်း", "Today total"), totals.todayTotal, true)
                    HomeCommissionWideRow(
                        language.text("ယနေ့အထိ", "Total to date"),
                        totals.toDateTotal, true, cumulative = true,
                        suffix = language.text(" ကျပ်", " MMK"),
                    )
                }
            } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1.12f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HomeCommissionAmountRow(language.text("မနက်အကြိမ်", "AM"), totals.morning, false)
                    HomeCommissionAmountRow(language.text("ညနေအကြိမ်", "PM"), totals.evening, false)
                    HorizontalDivider(color = AppColors.Stone.copy(alpha = .75f))
                    HomeCommissionAmountRow(language.text("ဒီနေ့စုစုပေါင်း", "Today total"), totals.todayTotal, true)
                }
                VerticalDivider(modifier = Modifier.height(62.dp), color = AppColors.Stone)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        language.text("ယနေ့အထိ", "Total to date"),
                        style = HomeTypography.commissionMeta(language.code),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        homeCommissionWrapNumber(totals.toDateTotal) + language.text(" ကျပ်", " MMK"),
                        fontSize = if (totals.toDateTotal.cardAmount().length > 13) 13.sp else 17.sp,
                        lineHeight = 20.sp, fontWeight = FontWeight.Black,
                        color = AppColors.PrimaryDeep, maxLines = 2,
                        softWrap = true,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun HomeCommissionWideRow(
    label: String,
    amount: Long,
    emphasis: Boolean,
    cumulative: Boolean = false,
    suffix: String = "",
) {
    val language = com.myanmar.ledger2d.core.design.LocalLanguage.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = HomeTypography.commissionRowLabel(language.code, emphasis),
            color = if (emphasis) AppColors.PrimaryDeep
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            homeCommissionWrapNumber(amount) + suffix,
            modifier = Modifier.weight(1.5f),
            fontSize = if (cumulative) 15.sp else 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (emphasis) FontWeight.Black else FontWeight.Bold,
            color = if (emphasis) AppColors.PrimaryDeep else AppColors.Ink,
            textAlign = TextAlign.End,
            softWrap = true,
            // No maxLines or ellipsis: money values are never truncated.
        )
    }
}

@Composable
private fun HomeCommissionAmountRow(label: String, amount: Long, emphasis: Boolean) {
    val language = com.myanmar.ledger2d.core.design.LocalLanguage.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = HomeTypography.commissionRowLabel(language.code, emphasis),
            color = if (emphasis) AppColors.PrimaryDeep
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            amount.cardAmount(),
            fontSize = if (amount.cardAmount().length > 11) 11.sp else 14.sp,
            lineHeight = 18.sp,
            color = if (emphasis) AppColors.PrimaryDeep else AppColors.Ink,
            fontWeight = FontWeight.Bold, maxLines = 1,
            softWrap = false,
        )
    }
}
