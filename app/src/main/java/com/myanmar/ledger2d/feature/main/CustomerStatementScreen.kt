@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.database.CustomerEntity
import com.myanmar.ledger2d.core.design.*
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

private fun StatementPeriod.nameMm(): String = when (this) {
    StatementPeriod.DAY -> "ရက်"
    StatementPeriod.WEEK -> "ပတ်"
    StatementPeriod.MONTH -> "လ"
    StatementPeriod.YEAR -> "နှစ်"
    StatementPeriod.CUSTOM -> "စိတ်ကြိုက်"
}
private fun DrawSession.nameMm(): String = if (this == DrawSession.MORNING) "မနက်အကြိမ်" else "ညနေအကြိမ်"
private fun LocalDate.statementDate() = format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
private fun Long.statementMoney(): String = mmk()
private data class StatementDisplayGroup(val key: LocalDate, val title: String, val draws: List<StatementDraw>)

private fun displayGroups(draws: List<StatementDraw>, period: StatementPeriod, range: StatementRange): List<StatementDisplayGroup> {
    val days = ChronoUnit.DAYS.between(range.start, range.end) + 1
    val by = when (period) {
        StatementPeriod.YEAR -> "month"
        StatementPeriod.MONTH -> "week"
        StatementPeriod.CUSTOM -> if (days <= 31) "day" else if (days <= 120) "week" else "month"
        else -> "day"
    }
    return draws.groupBy { draw ->
        when (by) {
            "month" -> draw.date.withDayOfMonth(1)
            "week" -> draw.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            else -> draw.date
        }
    }.toSortedMap().map { (key, grouped) ->
        val label = when (by) {
            "month" -> key.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            "week" -> {
                val first = if (key.isBefore(range.start)) range.start else key
                val last = if (key.plusDays(6).isAfter(range.end)) range.end else key.plusDays(6)
                "${first.statementDate()} – ${last.statementDate()}"
            }
            else -> key.statementDate()
        }
        StatementDisplayGroup(key, label, grouped)
    }
}

/** Customer-facing, read-only statement. The existing agent/commission screens are untouched. */
@Composable
fun CustomerStatementScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val context = LocalLanguage.current
    val agents by vm.agents.collectAsStateWithLifecycle()
    var agentId by rememberSaveable { mutableLongStateOf(context.defaultAgentId) }
    var customerId by rememberSaveable { mutableLongStateOf(-1L) }
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    var period by rememberSaveable { mutableStateOf(StatementPeriod.WEEK) }
    var anchorText by rememberSaveable { mutableStateOf(context.selectedDate) }
    var startText by rememberSaveable { mutableStateOf(context.selectedDate) }
    var endText by rememberSaveable { mutableStateOf(context.selectedDate) }
    var selectedDraw by rememberSaveable { mutableStateOf("both") }
    var view by rememberSaveable { mutableStateOf("summary") }
    var search by rememberSaveable { mutableStateOf("") }
    val revision by vm.revision.collectAsStateWithLifecycle()

    LaunchedEffect(agents, agentId) {
        if (agentId <= 0L || agents.none { it.id == agentId }) {
            agentId = agents.firstOrNull()?.id ?: 0L
            customerId = -1L
        }
    }
    LaunchedEffect(agentId, customers, customerId) {
        if (customerId > 0 && customers.none { it.id == customerId }) customerId = -1L
    }
    val anchor = runCatching { LocalDate.parse(anchorText) }.getOrNull()
    val start = runCatching { LocalDate.parse(startText) }.getOrNull()
    val end = runCatching { LocalDate.parse(endText) }.getOrNull()
    val range = if (anchor != null && start != null && end != null)
        runCatching { StatementPeriods.range(period, anchor, start, end) }.getOrNull()
    else null
    val drawFilter = when (selectedDraw) {
        "am" -> DrawSession.MORNING
        "pm" -> DrawSession.EVENING
        else -> null
    }

    AppScaffold("အစီရင်ခံစာ", onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ထိုးသား စာရင်းရှင်းတမ်း", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    ScopeDropdown(
                        "Agent ရွေးရန်",
                        agents.firstOrNull { it.id == agentId }?.name ?: "Agent ရွေးရန်",
                        agents.map { it.id to it.name }, agentId,
                    ) { agentId = it; customerId = -1L; view = "summary"; search = "" }
                    ScopeDropdown(
                        "ထိုးသား ရွေးရန်",
                        if (customerId == -1L) "Customer အားလုံး" else customers.firstOrNull { it.id == customerId }?.name ?: "ထိုးသား ရွေးရန်",
                        listOf(-1L to "Customer အားလုံး") + customers.map { it.id to it.name },
                        customerId,
                        enabled = agentId > 0L,
                    ) { customerId = it; search = "" }
                }
            }
            item {
                OutlinedCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ကြည့်ရှုမည့်ကာလ", fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatementPeriod.entries.forEach { option ->
                                FilterChip(period == option, { period = option }, label = { Text(option.nameMm()) })
                            }
                        }
                        if (period == StatementPeriod.CUSTOM) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateInput(startText, { startText = it }, "စရက်", Modifier.weight(1f))
                                DateInput(endText, { endText = it }, "ဆုံးရက်", Modifier.weight(1f))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                IconButton(onClick = {
                                    if (anchor != null) anchorText = StatementPeriods.move(period, anchor, -1).toString()
                                }) { Icon(Icons.Default.ChevronLeft, "ယခင်ကာလ") }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (range == null) "ရက်စွဲမှားနေသည်"
                                        else if (range.start == range.end) range.start.statementDate()
                                        else "${range.start.statementDate()} – ${range.end.statementDate()}",
                                        textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                IconButton(onClick = {
                                    if (anchor != null) anchorText = StatementPeriods.move(period, anchor, 1).toString()
                                }) { Icon(Icons.Default.ChevronRight, "နောက်ကာလ") }
                            }
                            DateInput(anchorText, { anchorText = it }, "ရက်စွဲရွေးရန်")
                        }
                        if (range == null) Text("စရက်၊ ဆုံးရက်နှင့် ရက်စွဲကို ပြန်စစ်ပါ", color = MaterialTheme.colorScheme.error)
                        Text("ကြည့်မည့်အကြိမ်", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("both" to "နှစ်ကြိမ်လုံး", "am" to "မနက်အကြိမ်", "pm" to "ညနေအကြိမ်").forEach { (id, label) ->
                                FilterChip(selectedDraw == id, { selectedDraw = id }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
            if (agentId <= 0L || range == null) {
                item { Text("Agent နှင့် မှန်ကန်သော ရက်စွဲကို ရွေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                // Recreate the producer when the selection changes: old customer's totals
                // cannot flash under the new customer's name while a query is running.
                item {
                    key(agentId, customerId, range, drawFilter) {
                        val report by produceState<CustomerStatement?>(null, agentId, customerId, range, drawFilter, revision) {
                            value = null
                            value = vm.customerStatement(agentId, customerId, range, drawFilter)
                        }
                        if (report == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("စာရင်းတွက်နေသည်…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            CustomerStatementContent(report!!, customers, customerId, period, range, view, { view = it }, search, { search = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementMoneyLine(label: String, amount: Long, strong: Boolean = false, positiveGood: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text(
            (if (positiveGood && amount > 0) "+" else "") + amount.statementMoney(),
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            color = if (positiveGood) {
                if (amount < 0) MaterialTheme.colorScheme.error else AppColors.Success
            } else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StatementTotalCard(t: StatementTotals, title: String, customerCount: Int? = null, distinctDraws: Pair<Int, Int>? = null) {
    val present = distinctDraws?.first ?: t.drawsWithBets
    val completed = distinctDraws?.second ?: t.completedDraws
    val waiting = present - completed
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (customerCount != null) Text("စာရင်းရှိသော ထိုးသား ${customerCount} ယောက်", color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatementMoneyLine("စုစုပေါင်းထိုးကြေး", t.totalBet, true)
            StatementMoneyLine("ရလဒ်ထွက်ပြီးသော အကြိမ်များ၏ ထိုးကြေး", t.completedBet)
            StatementMoneyLine("ပေါက်ကြေး", t.winningStake, true)
            StatementMoneyLine("ပြန်လျော်ငွေ", t.payout, true)
            Text("အကြိမ်/ထိုးသားအလိုက် ထိုးကွက်ပေါင်း ${t.slots} ကွက်", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("ထိုးသား အနိုင်/အရှုံး (ရလဒ်ထွက်ပြီးသော အကြိမ်များအတွက်)", style = MaterialTheme.typography.labelMedium)
            StatementMoneyLine("စုစုပေါင်း", t.customerWinLoss, strong = true, positiveGood = true)
            Text(
                "စာရင်းရှိသော အကြိမ် ${present} ခု • ရလဒ်ရပြီး ${completed} ခု • ရလဒ်မရသေး ${waiting} ခု",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            if (!t.isComplete) {
                Text("သတိပြုရန် — အနိုင်/အရှုံးမှာ ရလဒ်ရပြီးသား အကြိမ်များအတွက်သာဖြစ်ပြီး ကျန်အကြိမ်များ မပါသေးပါ။", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatementDrawCard(draws: List<StatementDraw>) {
    if (draws.isEmpty()) return
    val first = draws.first()
    val totals = CustomerStatementCalculator.totals(draws)
    val winner = first.winner
    val payoutRates = draws.flatMap { it.payoutRates }.toSet()
    OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(first.session.nameMm(), fontWeight = FontWeight.Bold)
                Text(if (winner == null) "ရလဒ်မရသေး" else "ပေါက်ဂဏန်း $winner",
                    color = if (winner == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium)
            }
            Text("ထိုးကွက်ပေါင်း ${totals.slots} ကွက်", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatementMoneyLine("ထိုးကြေး", totals.totalBet)
            if (winner == null) {
                Text("ပေါက်ကြေး၊ ပြန်လျော်ငွေ၊ အနိုင်/အရှုံးကို ရလဒ်ရမှ တွက်မည်။", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                StatementMoneyLine("ပေါက်ကြေး", totals.winningStake)
                Text("လျော်ကြေးအဆ: " + when {
                    payoutRates.isEmpty() -> "—"
                    payoutRates.size == 1 -> "${payoutRates.first()} ဆ"
                    else -> payoutRates.sorted().joinToString(" / ") { "$it ဆ" }
                }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                StatementMoneyLine("ပြန်လျော်ငွေ", totals.payout, true)
                HorizontalDivider()
                StatementMoneyLine("ထိုးသား အနိုင်/အရှုံး", totals.customerWinLoss, true, true)
            }
        }
    }
}

@Composable
private fun StatementGroupRows(group: StatementDisplayGroup) {
    val byDate = group.draws.groupBy { it.date }.toSortedMap()
    var openDay by remember(group.key) { mutableStateOf<LocalDate?>(null) }
    if (byDate.size == 1) {
        byDate.values.first().groupBy { it.session }.toSortedMap(compareBy { it.ordinal }).values.forEach { StatementDrawCard(it) }
    } else {
        byDate.forEach { (date, draws) ->
            val t = CustomerStatementCalculator.totals(draws)
            OutlinedCard(onClick = { openDay = if (openDay == date) null else date }) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(date.statementDate(), fontWeight = FontWeight.Medium)
                        Icon(if (openDay == date) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    StatementMoneyLine("ထိုးကြေး", t.totalBet)
                    if (t.awaitingDraws == 0) StatementMoneyLine("ထိုးသား အနိုင်/အရှုံး", t.customerWinLoss, positiveGood = true)
                    else Text("ရလဒ်မရသေး ${t.awaitingDraws} ကြိမ်", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (openDay == date) {
                        draws.groupBy { it.session }.toSortedMap(compareBy { it.ordinal }).values.forEach { StatementDrawCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementBucketList(groups: List<StatementDisplayGroup>) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    groups.forEach { group ->
        val id = group.key.toString()
        val totals = CustomerStatementCalculator.totals(group.draws)
        val unfinishedDrawCount = group.draws.groupBy { it.date to it.session }
            .count { (_, items) -> items.any { !it.resultKnown } }
        OutlinedCard(onClick = { expanded = if (expanded == id) null else id }) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(if (expanded == id) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                StatementMoneyLine("ထိုးကြေး", totals.totalBet)
                if (unfinishedDrawCount == 0) StatementMoneyLine("ထိုးသား အနိုင်/အရှုံး", totals.customerWinLoss, positiveGood = true)
                else Text("ရလဒ်မရသေး ${unfinishedDrawCount} ကြိမ် • ရပြီးသားအကြိမ်များ အနိုင်/အရှုံး ${totals.customerWinLoss.statementMoney()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (expanded == id) {
                    StatementMoneyLine("ပေါက်ကြေး (ရလဒ်ရပြီး)", totals.winningStake)
                    StatementMoneyLine("ပြန်လျော်ငွေ (ရလဒ်ရပြီး)", totals.payout)
                    HorizontalDivider()
                    StatementGroupRows(group)
                }
            }
        }
    }
}

@Composable
private fun CustomerStatementContent(
    report: CustomerStatement,
    customers: List<CustomerEntity>,
    selectedCustomerId: Long,
    period: StatementPeriod,
    range: StatementRange,
    view: String,
    onView: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
) {
    val all = selectedCustomerId == -1L
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val uniqueDraws = report.draws.groupBy { it.date to it.session }
        val distinctCounts = if (all) uniqueDraws.size to uniqueDraws.count { (_, items) -> items.all { it.resultKnown } } else null
        StatementTotalCard(
            report.totals,
            if (all) "Customer အားလုံး — စုစုပေါင်းစာရင်း" else "ထိုးသား၏ စုစုပေါင်းစာရင်း",
            if (all) report.customers.size else null,
            distinctCounts,
        )
        if (report.draws.isEmpty()) {
            Text("ရွေးထားသောကာလအတွက် ထိုးစာရင်းမရှိပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (all) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(view == "summary", { onView("summary") }, label = { Text("ကာလအလိုက် စုစုပေါင်း") })
                    FilterChip(view == "customers", { onView("customers") }, label = { Text("ထိုးသားတစ်ယောက်ချင်း") })
                }
            }
            if (all && view == "customers") {
                OutlinedTextField(
                    value = search, onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("ထိုးသားအမည် ရှာရန်") },
                )
                var expandedCustomer by rememberSaveable { mutableLongStateOf(0L) }
                val people = customers.filter { report.customers.containsKey(it.id) && it.name.contains(search, ignoreCase = true) }
                if (people.isEmpty()) Text("ကိုက်ညီသော ထိုးသားမရှိပါ")
                people.forEach { customer ->
                    val totals = report.customers[customer.id] ?: return@forEach
                    val expanded = expandedCustomer == customer.id
                    OutlinedCard(onClick = { expandedCustomer = if (expanded) 0L else customer.id }) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(customer.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            StatementMoneyLine("ထိုးကြေး", totals.totalBet)
                            StatementMoneyLine("ပေါက်ကြေး", totals.winningStake)
                            StatementMoneyLine("ပြန်လျော်ငွေ", totals.payout)
                            if (totals.awaitingDraws == 0) StatementMoneyLine("ထိုးသား အနိုင်/အရှုံး", totals.customerWinLoss, true, true)
                            else Text("ရလဒ်မရသေး ${totals.awaitingDraws} ကြိမ် • ရပြီးသားအနိုင်/အရှုံး ${totals.customerWinLoss.statementMoney()}", style = MaterialTheme.typography.bodySmall)
                            if (expanded) {
                                HorizontalDivider()
                                val groups = displayGroups(report.draws.filter { it.customerId == customer.id }, period, range)
                                StatementBucketList(groups)
                            }
                        }
                    }
                }
                if (people.isNotEmpty()) {
                    val visibleIds = people.map { it.id }.toSet()
                    val shown = CustomerStatementCalculator.totals(report.draws.filter { it.customerId in visibleIds })
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("ဖော်ပြထားသော ထိုးသားများ၏ အောက်ဆုံးစုစုပေါင်း", fontWeight = FontWeight.Bold)
                            StatementMoneyLine("ထိုးကြေး", shown.totalBet, true)
                            StatementMoneyLine("ပေါက်ကြေး", shown.winningStake)
                            StatementMoneyLine("ပြန်လျော်ငွေ", shown.payout, true)
                            if (shown.awaitingDraws == 0) StatementMoneyLine("ထိုးသား အနိုင်/အရှုံး", shown.customerWinLoss, true, true)
                            else Text("ရလဒ်မရသေးသော အကြိမ်များ မပါဝင်သည့် အနိုင်/အရှုံး: ${shown.customerWinLoss.statementMoney()}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (search.isNotBlank()) Text("အမည်ရှာဖွေမှုနှင့် ကိုက်ညီသော ထိုးသားများကိုသာ ပေါင်းထားသည်။",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Text("ကာလအလိုက် စာရင်းအသေးစိတ်", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val groups = displayGroups(report.draws, period, range)
                StatementBucketList(groups)
            }
        }
    }
}
