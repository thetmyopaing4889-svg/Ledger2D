package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.AuroraGlassCard
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.CommissionLedgerCalculator
import com.myanmar.ledger2d.core.domain.DeviceCalendar
import java.text.NumberFormat
import java.time.DayOfWeek
import java.util.Locale

private fun Long.cardAmount() = NumberFormat.getIntegerInstance(Locale.US).format(this)

/**
 * Premium presentation only: retain every original session-snapshot commission amount.
 * The lifetime amount is given a full line so it is never squeezed into a half-width card.
 */
@Composable
internal fun HomeCommissionCard(
    vm: LedgerViewModel,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalLanguage.current
    val today = DeviceCalendar.today()
    val ledger = remember(today) { vm.commissionLedger(today) }
    val lines by ledger.collectAsStateWithLifecycle(initialValue = emptyList())
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val totals = CommissionLedgerCalculator.overview(lines, today)
    val isClosed = today.dayOfWeek == DayOfWeek.SATURDAY ||
        today.dayOfWeek == DayOfWeek.SUNDAY || closedDays.any { it.date == today }

    AuroraGlassCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onDetails),
        animated = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.AccountBalanceWallet, null,
                tint = AppColors.ChampagneGold, modifier = Modifier.size(22.dp))
            Text(
                language.translate("ကော်မရှင်ရငွေ"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            if (isClosed) Text(
                language.translate("ပိတ်ရက်"),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.ChampagneGold,
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = language.translate("ကော်မရှင်အသေးစိတ်ကြည့်ရန်"),
                tint = AppColors.ChampagneGold,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            language.text("ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း", "Total commission to date"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = .80f),
        )
        Text(
            totals.toDateTotal.cardAmount() + language.text(" ကျပ်", " MMK"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            softWrap = true,
        )
        HorizontalDivider(color = Color.White.copy(alpha = .24f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CommissionGlassMetric(
                language.text("မနက်အကြိမ်", "AM"),
                totals.morning,
                modifier = Modifier.weight(1f)
            )
            CommissionGlassMetric(
                language.text("ညနေအကြိမ်", "PM"),
                totals.evening,
                modifier = Modifier.weight(1f)
            )
            CommissionGlassMetric(
                language.text("ဒီနေ့စုစုပေါင်း", "Today total"),
                totals.todayTotal,
                modifier = Modifier.weight(1.1f),
                emphasis = true
            )
        }
    }
}

@Composable
private fun CommissionGlassMetric(
    label: String,
    amount: Long,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (emphasis) AppColors.ChampagneGold else Color.White.copy(alpha = .8f),
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            amount.cardAmount(),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            softWrap = true,
        )
    }
}
