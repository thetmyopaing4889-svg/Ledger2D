package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.mmk
import com.myanmar.ledger2d.core.domain.AnalysisResult
import com.myanmar.ledger2d.core.domain.DigitViewFilter
import com.myanmar.ledger2d.core.domain.DigitViewSelector
import com.myanmar.ledger2d.core.domain.DigitViewSort
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Display-only customer digit list: never modifies a bet, session setting, or analysis.
 * The selected agent, customer, date, and session are controlled by the existing workspace.
 */
@Composable
internal fun CustomerDigitViewsWorkspace(
    vm: LedgerViewModel,
    agentId: Long,
    customerId: Long,
    date: LocalDate,
    session: DrawSession,
) {
    val totals by (
        if (customerId == -1L) vm.agentTotals(agentId, date, session)
        else vm.customerTotals(customerId, date, session)
    ).collectAsStateWithLifecycle(initialValue = emptyList())
    val agentSpecials by vm.agentSpecialLimits(agentId, date, session)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val customerSpecials by vm.specialLimits(customerId, date, session)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val revision by vm.revision.collectAsStateWithLifecycle()
    val analysis by produceState<AnalysisResult?>(
        null, agentId, customerId, date, session, revision
    ) {
        value = null
        value = if (customerId == -1L) vm.aggregateAnalysis(agentId, date, session)
                else vm.analysis(customerId, date, session)
    }
    val winner by vm.winner(date, session).collectAsStateWithLifecycle(initialValue = null)

    val amounts = remember(totals) { totals.associate { it.digit to it.amount } }
    val specialDigits = remember(agentSpecials, customerSpecials, customerId) {
        agentSpecials.map { it.digit }.toSet() +
            if (customerId == -1L) emptySet() else customerSpecials.map { it.digit }.toSet()
    }
    val scenarios = remember(analysis) { analysis?.scenarios?.associateBy { it.digit }.orEmpty() }
    val stakeCount = amounts.count { it.value > 0L }
    val totalStake = totals.sumOf { it.amount }

    var view by rememberSaveable { mutableStateOf(DigitViewFilter.ALL) }
    var order by rememberSaveable { mutableStateOf(DigitViewSort.STAKE_DESC) }
    var search by rememberSaveable { mutableStateOf("") }
    val rows = remember(amounts, view, order, search) {
        DigitViewSelector.select(amounts, view, order, search)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("00–99 အကွက်စာရင်း",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("ထိုးကြေးစုစုပေါင်း ${totalStake.mmk()}",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DigitViewSummary("ထိုးကြေးရှိ", stakeCount, AppColors.Primary,
                selected = view == DigitViewFilter.STAKED, Modifier.weight(1f)) {
                view = DigitViewFilter.STAKED
            }
            DigitViewSummary("ထိုးကြေးလွတ်", 100 - stakeCount,
                MaterialTheme.colorScheme.onSurfaceVariant,
                selected = view == DigitViewFilter.EMPTY, Modifier.weight(1f)) {
                view = DigitViewFilter.EMPTY
            }
            DigitViewSummary("စုစုပေါင်း", 100, MaterialTheme.colorScheme.onSurface,
                selected = view == DigitViewFilter.ALL, Modifier.weight(1f)) {
                view = DigitViewFilter.ALL
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DigitViewTab("အားလုံး", view == DigitViewFilter.ALL, Modifier.weight(1f)) {
                view = DigitViewFilter.ALL
            }
            DigitViewTab("ထိုးကြေးရှိ", view == DigitViewFilter.STAKED, Modifier.weight(1f)) {
                view = DigitViewFilter.STAKED
            }
            DigitViewTab("ထိုးကြေးလွတ်", view == DigitViewFilter.EMPTY, Modifier.weight(1f)) {
                view = DigitViewFilter.EMPTY
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = DigitViewSelector.normalizeSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ဂဏန်းရှာရန် (3 သို့မဟုတ် 33)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "ရှာဖွေမှုရှင်းရန်")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )

        if (view == DigitViewFilter.STAKED) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("စီရန်", style = MaterialTheme.typography.labelMedium)
                FilterChip(
                    selected = order == DigitViewSort.STAKE_DESC,
                    onClick = { order = DigitViewSort.STAKE_DESC },
                    label = { Text("ငွေအများ → အနည်း") },
                )
                FilterChip(
                    selected = order == DigitViewSort.STAKE_ASC,
                    onClick = { order = DigitViewSort.STAKE_ASC },
                    label = { Text("ငွေအနည်း → အများ") },
                )
                FilterChip(
                    selected = order == DigitViewSort.NUMBER,
                    onClick = { order = DigitViewSort.NUMBER },
                    label = { Text("ဂဏန်းအစဉ်") },
                )
            }
        }

        Text("ပြသနေသောအကွက် ${rows.size} ကွက်",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "အနီ = ထိုးကြေးရှိ • မီးခိုး = မရှိ • SL = Special Limit • " +
                "ပိတ် = ပိတ်ဂဏန်း • ပေါက် = ပေါက်ဂဏန်း",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "ထိုးကြေးလွတ်သည် ထိုးကြေး 0 ကျပ်သာဖြစ်ပြီး လက်ခံလို့ရသောဂဏန်းဟု မဆိုလိုပါ။",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (rows.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("ရွေးထားသော View နှင့် ကိုက်ညီသည့်ဂဏန်း မရှိပါ။",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            rows.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { item ->
                        val scenario = scenarios[item.digit]
                        DigitViewCard(
                            digit = item.digit,
                            amount = item.stake,
                            isWinner = winner?.digit == item.digit,
                            isClosed = scenario?.closed == true,
                            isSpecial = item.digit in specialDigits,
                            percentUsed = scenario?.percentUsed,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Last row must keep exactly the same five-column widths.
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DigitViewSummary(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = color.copy(alpha = if (selected) .16f else .08f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(if (selected) 2.dp else 1.dp, color.copy(alpha = .55f)),
    ) {
        Column(
            Modifier.padding(horizontal = 3.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(count.toString(), fontWeight = FontWeight.Black, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = color, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun DigitViewTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(Modifier.padding(horizontal = 2.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2)
        }
    }
}

@Composable
private fun DigitViewCard(
    digit: String,
    amount: Long,
    isWinner: Boolean,
    isClosed: Boolean,
    isSpecial: Boolean,
    percentUsed: Int?,
    modifier: Modifier,
) {
    val background = when {
        isWinner -> AppColors.GoldSoft
        isClosed -> MaterialTheme.colorScheme.errorContainer
        amount > 0L -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = when {
        isWinner -> AppColors.Wine
        isClosed -> MaterialTheme.colorScheme.error
        isSpecial -> MaterialTheme.colorScheme.tertiary
        amount > 0L -> AppColors.Primary.copy(alpha = .55f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val amountColor = when {
        isWinner -> AppColors.Wine
        amount > 0L -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = background,
        border = BorderStroke(if (isWinner) 2.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(digit, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isWinner) AppColors.Wine
                        else if (isClosed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
            Text(amount.mmk(), style = MaterialTheme.typography.labelSmall,
                fontWeight = if (amount > 0L) FontWeight.Bold else FontWeight.Normal,
                color = amountColor, textAlign = TextAlign.Center, maxLines = 2)
            if (isWinner) Text("ပေါက်", style = MaterialTheme.typography.labelSmall,
                color = AppColors.Wine, fontWeight = FontWeight.Bold)
            if (isClosed) Text("ပိတ်", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            if (isSpecial) Text("SL", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            if (percentUsed != null) Text("$percentUsed%",
                style = MaterialTheme.typography.labelSmall,
                color = if (percentUsed >= 100) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
