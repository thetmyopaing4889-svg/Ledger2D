@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.domain.CommissionLedgerCalculator
import com.myanmar.ledger2d.core.domain.CommissionLedgerLine
import com.myanmar.ledger2d.core.domain.DeviceCalendar
import com.myanmar.ledger2d.core.domain.StatementPeriod
import com.myanmar.ledger2d.core.domain.StatementPeriods
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private fun Long.commissionMoney(): String = NumberFormat.getIntegerInstance(Locale.US).format(this) + " ကျပ်"

@Composable
fun CommissionReviewScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val today = DeviceCalendar.today()
    val source = remember(today) { vm.commissionLedger(today) }
    val allRows by source.collectAsStateWithLifecycle(initialValue = emptyList())
    var agentId by rememberSaveable { mutableLongStateOf(-1L) }
    var period by rememberSaveable { mutableStateOf(StatementPeriod.DAY) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    var fromText by rememberSaveable { mutableStateOf(today.toString()) }
    var toText by rememberSaveable { mutableStateOf(today.toString()) }
    var sessionChoice by rememberSaveable { mutableStateOf("both") }
    var resultPage by rememberSaveable { mutableStateOf(false) }
    val anchor = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val customStart = runCatching { LocalDate.parse(fromText) }.getOrNull()
    val customEnd = runCatching { LocalDate.parse(toText) }.getOrNull()
    val range = if (allTime) {
        StatementRange(allRows.minOfOrNull { it.date } ?: today, today)
    } else if (anchor != null && customStart != null && customEnd != null) {
        runCatching { StatementPeriods.range(period, anchor, customStart, customEnd) }.getOrNull()
    } else null
    val draw = when (sessionChoice) {
        "am" -> DrawSession.MORNING
        "pm" -> DrawSession.EVENING
        else -> null
    }
    LaunchedEffect(agentId, agents) {
        if (agentId > 0L && agents.none { it.id == agentId }) agentId = -1L
    }
    val selectedName = if (agentId == -1L) "ဒိုင်အားလုံး"
        else agents.firstOrNull { it.id == agentId }?.name ?: "ဒိုင်ရွေးရန်"
    val valid = range != null && (agentId == -1L || agents.any { it.id == agentId })
    val filtered = if (range != null) CommissionLedgerCalculator.select(allRows, range, agentId, draw)
        else emptyList()

    AppScaffold(
        if (resultPage) "ကော်မရှင်စာရင်းချုပ်" else "ကော်မရှင်အသေးစိတ် ရွေးချယ်ရန်",
        onBack = { if (resultPage) resultPage = false else onBack() },
        includeQuickEntryFab = false,
    ) { padding ->
        if (!resultPage) {
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("အချိန်ကာလ", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            StatementPeriod.DAY to "ရက်", StatementPeriod.WEEK to "ပတ်",
                            StatementPeriod.MONTH to "လ", StatementPeriod.YEAR to "နှစ်",
                            StatementPeriod.CUSTOM to "စိတ်ကြိုက်",
                        ).forEach { (id, title) ->
                            FilterChip(!allTime && period == id, onClick = {
                                period = id; allTime = false
                            }, label = { Text(title) })
                        }
                        FilterChip(allTime, onClick = { allTime = true },
                            label = { Text("အစမှ ယနေ့အထိ") })
                    }
                }
                if (!allTime) item {
                    if (period == StatementPeriod.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewDatePicker(fromText, "စရက်", Modifier.weight(1f)) { fromText = it }
                            ReviewDatePicker(toText, "ဆုံးရက်", Modifier.weight(1f)) { toText = it }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                anchor?.let { dateText = StatementPeriods.move(period, it, -1).toString() }
                            }) { Icon(Icons.Default.ChevronLeft, "ယခင်ကာလ") }
                            Text(range?.let {
                                if (it.start == it.end) it.start.reviewDate()
                                else it.start.reviewDate() + " – " + it.end.reviewDate()
                            } ?: "ရက်စွဲမှားနေသည်", Modifier.weight(1f),
                                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = {
                                anchor?.let { dateText = StatementPeriods.move(period, it, 1).toString() }
                            }) { Icon(Icons.Default.ChevronRight, "နောက်ကာလ") }
                        }
                        ReviewDatePicker(dateText, "ရက်စွဲ") { dateText = it }
                    }
                }
                if (allTime) item {
                    Text("App ထဲမှာ သိမ်းထားသော ပထမဆုံးစာရင်းမှ ယနေ့အထိ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                item {
                    ScopeDropdown("ဒိုင်ရွေးရန်", selectedName,
                        listOf(-1L to "ဒိုင်အားလုံး") + agents.map { it.id to it.name },
                        agentId) { agentId = it }
                }
                item {
                    Text("အကြိမ်ရွေးရန်", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("both" to "နှစ်ကြိမ်လုံး", "am" to "မနက်အကြိမ်", "pm" to "ညနေအကြိမ်")
                            .forEach { (key, name) ->
                                FilterChip(sessionChoice == key, onClick = { sessionChoice = key },
                                    label = { Text(name) })
                            }
                    }
                }
                if (!valid) item {
                    Text("ရက်စွဲ သို့မဟုတ် ဒိုင်ရွေးချယ်မှုကို ပြန်စစ်ပါ",
                        color = MaterialTheme.colorScheme.error)
                }
                item {
                    Button(onClick = { resultPage = true }, enabled = valid,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("စာရင်းကြည့်မည်")
                    }
                }
            }
        } else if (range != null) {
            CommissionReviewResults(filtered, agentId, selectedName, range, draw,
                { resultPage = false }, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun CommissionReviewResults(
    rows: List<CommissionLedgerLine>,
    agentId: Long,
    selectedName: String,
    range: StatementRange,
    draw: DrawSession?,
    onReselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by rememberSaveable { mutableStateOf("agents") }
    val groups = rows.groupBy { it.agentId }.toList().sortedBy { it.second.first().agentName }
    LazyColumn(modifier, contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selectedName, fontWeight = FontWeight.Bold)
                    Text(range.start.reviewDate() + " – " + range.end.reviewDate() + " • " +
                        (draw?.reviewLabel() ?: "နှစ်ကြိမ်လုံး"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onReselect) { Text("ပြန်ရွေးရန်") }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ရွေးထားသောကာလ ကော်မရှင်စုစုပေါင်း",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CommissionLedgerCalculator.total(rows).commissionMoney(),
                        fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                    HorizontalDivider()
                    CommissionMoneyRow("မနက်အကြိမ်", CommissionLedgerCalculator.total(
                        rows.filter { it.session == DrawSession.MORNING }))
                    CommissionMoneyRow("ညနေအကြိမ်", CommissionLedgerCalculator.total(
                        rows.filter { it.session == DrawSession.EVENING }))
                }
            }
        }
        if (rows.isEmpty()) {
            item { Text("ရွေးထားသောကာလအတွက် ကော်မရှင်စာရင်းမရှိပါ။") }
        } else {
            if (agentId == -1L) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(view == "agents", onClick = { view = "agents" },
                        label = { Text("ဒိုင်တစ်ယောက်ချင်း") })
                    FilterChip(view == "combined", onClick = { view = "combined" },
                        label = { Text("အားလုံးစုစုပေါင်း") })
                }
            }
            if (agentId == -1L && view == "agents") {
                items(groups, key = { it.first }) { (_, lines) ->
                    CommissionAgentGroup(lines)
                }
            } else {
                val dates = rows.groupBy { it.date }.toList().sortedByDescending { it.first }
                items(dates, key = { it.first.toString() }) { (day, lines) ->
                    CommissionDayGroup(day, lines, showAgents = agentId == -1L)
                }
            }
            item {
                HorizontalDivider()
                CommissionMoneyRow("အောက်ဆုံးစုစုပေါင်း", CommissionLedgerCalculator.total(rows), bold = true)
                if (agentId == -1L) Text("ဒိုင်အားလုံး၏ သိမ်းထားသော ကော်မရှင်ပေါင်း",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CommissionMoneyRow(label: String, amount: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(amount.commissionMoney(), textAlign = TextAlign.End,
            fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CommissionAgentGroup(lines: List<CommissionLedgerLine>) {
    val first = lines.firstOrNull() ?: return
    var open by rememberSaveable(first.agentId) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { open = !open }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(first.agentName, fontWeight = FontWeight.Bold)
                        Text(CommissionLedgerCalculator.total(lines).commissionMoney(),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (open) {
                lines.groupBy { it.date }.toList().sortedByDescending { it.first }.forEach { (date, dateRows) ->
                    CommissionDayGroup(date, dateRows, showAgents = false)
                }
            }
        }
    }
}

@Composable
private fun CommissionDayGroup(day: LocalDate, lines: List<CommissionLedgerLine>, showAgents: Boolean) {
    var open by rememberSaveable(day, showAgents) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { open = !open }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.reviewDate(), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(CommissionLedgerCalculator.total(lines).commissionMoney(),
                        style = MaterialTheme.typography.bodySmall)
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (open) {
                DrawSession.entries.forEach { session ->
                    val drawRows = lines.filter { it.session == session }
                    if (drawRows.isNotEmpty()) {
                        HorizontalDivider()
                        CommissionMoneyRow(session.reviewLabel(), CommissionLedgerCalculator.total(drawRows), true)
                        drawRows.sortedWith(compareBy<CommissionLedgerLine> { it.agentName }
                            .thenBy { it.customerName }).forEach { line ->
                            Row(Modifier.fillMaxWidth().padding(start = 7.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    if (showAgents) Text(line.agentName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(line.customerName, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(line.amount.commissionMoney(),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }
    }
}
