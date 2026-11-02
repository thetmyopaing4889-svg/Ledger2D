@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.ScrollState
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
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Long.agentMoney(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun Long.agentSignedMoney(): String = if (this > 0L) "+${agentMoney()}" else agentMoney()
private fun Long?.agentResultMoney(): String = this?.let { "${it.agentSignedMoney()} ကျပ်" } ?: "—"

@Composable
fun AgentStatementWorkspace(
    vm: LedgerViewModel,
    onBack: () -> Unit,
    initialAgentId: Long? = null,
) {
    val language = LocalLanguage.current
    val agents by vm.agents.collectAsStateWithLifecycle()
    var agentId by rememberSaveable(initialAgentId) {
        mutableLongStateOf(initialAgentId ?: language.defaultAgentId.takeIf { it > 0 } ?: -1L)
    }
    var period by rememberSaveable { mutableStateOf(StatementPeriod.DAY) }
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var startText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var endText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var drawChoice by rememberSaveable { mutableStateOf("both") }
    var results by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(agents, agentId) {
        if (agentId > 0L && agents.isNotEmpty() && agents.none { it.id == agentId }) {
            agentId = -1L
        }
    }
    val anchor = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val start = runCatching { LocalDate.parse(startText) }.getOrNull()
    val end = runCatching { LocalDate.parse(endText) }.getOrNull()
    val range = if (anchor != null && start != null && end != null) {
        runCatching { StatementPeriods.range(period, anchor, start, end) }.getOrNull()
    } else null
    val selectedSession = when (drawChoice) {
        "am" -> DrawSession.MORNING
        "pm" -> DrawSession.EVENING
        else -> null
    }
    val chosenAgent = if (agentId == -1L) com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး")
        else agents.firstOrNull { it.id == agentId }?.name ?: "ဒိုင်ရွေးရန်"
    val valid = agents.isNotEmpty() && (agentId == -1L || agents.any { it.id == agentId }) && range != null

    AppScaffold(
        title = if (results) "ဒိုင်စာရင်းရှင်းတမ်း" else "စာရင်းရွေးချယ်ရန်",
        onBack = { if (results) results = false else onBack() },
        includeQuickEntryFab = false,
    ) { padding ->
        if (!results) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်စာရင်းရှင်းတမ်း ရွေးချယ်ရန်"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item {
                    ScopeDropdown(
                        "ဒိုင်ရွေးရန်", chosenAgent,
                        listOf(-1L to com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး")) + agents.map { it.id to it.name },
                        agentId,
                    ) { agentId = it }
                }
                item {
                    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အချိန်ကာလ"), fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            StatementPeriod.DAY to "ရက်", StatementPeriod.WEEK to "ပတ်",
                            StatementPeriod.MONTH to "လ", StatementPeriod.YEAR to "နှစ်",
                            StatementPeriod.CUSTOM to "စိတ်ကြိုက်",
                        ).forEach { (p, label) ->
                            FilterChip(period == p, onClick = { period = p }, label = { Text(label) })
                        }
                    }
                }
                item {
                    if (period == StatementPeriod.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewDatePicker(startText, "စရက်", Modifier.weight(1f)) { startText = it }
                            ReviewDatePicker(endText, "ဆုံးရက်", Modifier.weight(1f)) { endText = it }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { anchor?.let { dateText = StatementPeriods.move(period, it, -1).toString() } }) {
                                Icon(Icons.Default.ChevronLeft, "ယခင်ကာလ")
                            }
                            Text(
                                if (range == null) "ရက်စွဲမှားနေသည်"
                                else if (range.start == range.end) range.start.reviewDate()
                                else "${range.start.reviewDate()} – ${range.end.reviewDate()}",
                                Modifier.weight(1f), textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(onClick = { anchor?.let { dateText = StatementPeriods.move(period, it, 1).toString() } }) {
                                Icon(Icons.Default.ChevronRight, "နောက်ကာလ")
                            }
                        }
                        ReviewDatePicker(dateText, "ရက်စွဲ") { dateText = it }
                    }
                }
                item {
                    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အကြိမ်ရွေးရန်"), fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("both" to "နှစ်ကြိမ်လုံး", "am" to "မနက်အကြိမ်", "pm" to "ညနေအကြိမ်")
                            .forEach { (id, label) ->
                                FilterChip(drawChoice == id, onClick = { drawChoice = id }, label = { Text(label) })
                            }
                    }
                }
                if (range == null) item {
                    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရွေးထားတဲ့ရက်စွဲတွေကို ပြန်စစ်ပါ"), color = MaterialTheme.colorScheme.error)
                }
                item {
                    Button(onClick = { results = true }, enabled = valid,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းကြည့်မည်"))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        } else if (valid && range != null) {
            AgentStatementResults(
                vm = vm, agentId = agentId, period = period, range = range,
                session = selectedSession, subtitle = chosenAgent,
                onReselect = { results = false },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

private data class AgentReportSection(val start: LocalDate, val title: String, val draws: List<AgentStatementDraw>)

private fun agentReportSections(draws: List<AgentStatementDraw>, period: StatementPeriod, range: StatementRange): List<AgentReportSection> {
    val days = ChronoUnit.DAYS.between(range.start, range.end) + 1L
    val kind = when (period) {
        StatementPeriod.YEAR -> "month"
        StatementPeriod.MONTH -> "week"
        StatementPeriod.CUSTOM -> if (days > 120) "month" else if (days > 31) "week" else "day"
        else -> "day"
    }
    return draws.groupBy {
        when (kind) {
            "month" -> it.date.withDayOfMonth(1)
            "week" -> it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            else -> it.date
        }
    }.toSortedMap().map { (start, rows) ->
        val label = when (kind) {
            "month" -> start.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
            "week" -> "${maxOf(start, range.start).reviewDate()} – ${minOf(start.plusDays(6), range.end).reviewDate()}"
            else -> start.reviewDate()
        }
        AgentReportSection(start, label, rows)
    }
}

@Composable
private fun AgentStatementValue(label: String, amount: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.End)
    }
}

@Composable
private fun AgentStatementSummary(totals: AgentStatementTotals, agentCount: Int?, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (!compact) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းချုပ်"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (agentCount != null) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းရှိသောဒိုင် $agentCount ယောက်"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AgentStatementValue("စုစုပေါင်းထိုးကြေး", "${totals.totalStake.agentMoney()} ကျပ်", true)
        AgentStatementValue("ကော်မရှင် (သိမ်းထားသော Net)", "${totals.savedNetCommission.agentMoney()} ကျပ်")
        if (totals.pendingDraws > 0) {
            AgentStatementValue("ရလဒ်မထွက်သေးသော ထိုးကြေး", "${totals.pendingStake.agentMoney()} ကျပ်")
            if (totals.completedDraws > 0)
                AgentStatementValue("ရလဒ်ရပြီး ထိုးကြေး", "${totals.completedStake.agentMoney()} ကျပ်")
        }
        HorizontalDivider()
        AgentStatementValue("ပေါက်ကြေး",
            if (totals.draws == 0) "—" else if (totals.completedDraws > 0)
                "${totals.winningStake.agentMoney()} ကျပ်" else "ရလဒ်မထွက်သေး")
        AgentStatementValue("လျော်ပေးငွေ", if (totals.completedDraws > 0)
            "${totals.payout.agentMoney()} ကျပ်" else "—", true)
        AgentStatementValue("ရှုံး/မြတ် (ကော်မရှင်မပါ)", totals.profitLoss.agentResultMoney())
        AgentStatementValue("အသားတင်ရှုံး/မြတ် (Net ပါ)", totals.netProfitLoss.agentResultMoney(), true)
        if (totals.pendingDraws > 0) {
            Text(
                "ရလဒ်ရပြီး ${totals.completedDraws} ဒိုင်/အကြိမ် • မရသေး ${totals.pendingDraws} ဒိုင်/အကြိမ်။ " +
                    "ပေါက်ကြေး၊ လျော်ငွေ၊ ရှုံး/မြတ်တို့မှာ ရလဒ်ရပြီးသားအကြိမ်များအတွက်သာဖြစ်ပြီး " +
                    "အသားတင်ရှုံး/မြတ်တွင် Pending Session ကော်မရှင် မပါဝင်ပါ။",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgentStatementTableHeader(scroll: ScrollState) {
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ရက်/အကြိမ်", "ထိုးကြေး", "ကော်မရှင်", "ပေါက်ကြေး", "လျော်ငွေ",
            "ရှုံး/မြတ်", "အသားတင်ရှုံး/မြတ်").forEachIndexed { i, name ->
            Text(name, Modifier.width(if (i == 0) 120.dp else 132.dp),
                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End, maxLines = 2)
        }
    }
    HorizontalDivider()
}
@Composable
private fun AgentStatementTableRow(
    label: String, totals: AgentStatementTotals, scroll: ScrollState, bold: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val style = MaterialTheme.typography.labelSmall
        Text(label, Modifier.width(120.dp), style = style,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(totals.totalStake.agentMoney(), Modifier.width(132.dp), textAlign = TextAlign.End, style = style)
        Text(totals.savedNetCommission.agentMoney(), Modifier.width(132.dp), textAlign = TextAlign.End, style = style)
        Text(if (totals.completedDraws > 0) totals.winningStake.agentMoney() else "—",
            Modifier.width(132.dp), textAlign = TextAlign.End, style = style)
        Text(if (totals.completedDraws > 0) totals.payout.agentMoney() else "—",
            Modifier.width(132.dp), textAlign = TextAlign.End, style = style,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(totals.profitLoss.agentResultMoney(), Modifier.width(132.dp),
            textAlign = TextAlign.End, style = style)
        Text(totals.netProfitLoss.agentResultMoney(), Modifier.width(132.dp),
            textAlign = TextAlign.End, style = style,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun AgentStatementDigitDetails(draw: AgentStatementDraw) {
    val rows = draw.stakes.groupBy { it.digit }.toSortedMap()
    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဂဏန်းအလိုက် ထိုးကြေး"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    rows.forEach { (digit, stakes) ->
        val stake = stakes.sumOf { it.stake }
        val payout = if (draw.winner == digit) stakes.sumOf { Math.multiplyExact(it.stake, it.savedPayoutRate) } else 0L
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(digit, style = MaterialTheme.typography.bodySmall,
                fontWeight = if (digit == draw.winner) FontWeight.Bold else FontWeight.Normal)
            Text(stake.agentMoney(), style = MaterialTheme.typography.bodySmall)
            Text(if (draw.winner == null) "—" else payout.agentMoney(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AgentStatementDrawDetail(
    draw: AgentStatementDraw,
    agentName: String,
    customerNames: Map<Long, String>,
    showAgent: Boolean,
) {
    var selectedCustomer by rememberSaveable(draw.agentId, draw.date, draw.session) { mutableLongStateOf(-1L) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (showAgent) Text(agentName, fontWeight = FontWeight.Bold)
            AgentStatementValue("ပေါက်ဂဏန်း", draw.winner ?: "မထွက်သေး")
            val savedRates = draw.stakes.map { it.savedPayoutRate }.distinct().sorted()
            AgentStatementValue("မူလသိမ်းထားသော လျော်ကြေးအဆ",
                savedRates.joinToString("/") { "${it} ဆ" }.ifEmpty { "—" })
            AgentStatementValue("ထိုးကြေး", "${draw.totalStake.agentMoney()} ကျပ်", true)
            AgentStatementValue("ကော်မရှင် (Net)", "${draw.commission.agentMoney()} ကျပ်")
            AgentStatementValue("ပေါက်ကြေး", draw.winningStake?.let { "${it.agentMoney()} ကျပ်" } ?: "—")
            AgentStatementValue("လျော်ပေးငွေ", draw.payout?.let { "${it.agentMoney()} ကျပ်" } ?: "—", true)
            AgentStatementValue("ရှုံး/မြတ် (ကော်မရှင်မပါ)", draw.profitLoss.agentResultMoney())
            AgentStatementValue("အသားတင်ရှုံး/မြတ် (Net ပါ)", draw.netProfitLoss.agentResultMoney(), true)
            val customerIds = (draw.stakes.map { it.customerId } + draw.commissions.map { it.customerId }).distinct()
            if (customerIds.isNotEmpty()) {
                HorizontalDivider()
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ထိုးသားအလိုက် အသေးစိတ်"), fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall)
                customerIds.sortedBy { customerNames[it] ?: it.toString() }.forEach { customerId ->
                    val customerDraw = draw.forCustomer(customerId)
                    val open = selectedCustomer == customerId
                    Surface(onClick = { selectedCustomer = if (open) -1L else customerId },
                        color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(customerNames[customerId] ?: "Customer #$customerId",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(customerDraw.totalStake.agentMoney(), style = MaterialTheme.typography.bodySmall)
                            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                    }
                    if (open) {
                        AgentStatementValue("ထိုးကြေး", "${customerDraw.totalStake.agentMoney()} ကျပ်")
                        AgentStatementValue("ကော်မရှင် (Net)", "${customerDraw.commission.agentMoney()} ကျပ်")
                        AgentStatementValue("ပေါက်ကြေး", customerDraw.winningStake?.let { "${it.agentMoney()} ကျပ်" } ?: "—")
                        AgentStatementValue("လျော်ပေးငွေ", customerDraw.payout?.let { "${it.agentMoney()} ကျပ်" } ?: "—")
                        AgentStatementValue("ရှုံး/မြတ် (ကော်မရှင်မပါ)", customerDraw.profitLoss.agentResultMoney())
                        AgentStatementValue("အသားတင်ရှုံး/မြတ် (Net ပါ)", customerDraw.netProfitLoss.agentResultMoney(), true)
                        AgentStatementDigitDetails(customerDraw)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentStatementSectionCard(
    section: AgentReportSection,
    customerNames: Map<Long, String>,
    agentNames: Map<Long, String>,
    showAgents: Boolean,
) {
    var open by rememberSaveable(section.start) { mutableStateOf(false) }
    var openedDraw by rememberSaveable(section.start) { mutableStateOf<String?>(null) }
    val tableScroll = rememberScrollState()
    val totals = AgentStatementCalculator.summarize(section.draws)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(onClick = { open = !open }, color = Color.Transparent) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    AgentStatementTableRow("စုစုပေါင်း", totals, tableScroll, true)
                }
            }
            if (open) {
                if (showAgents) {
                    val realSessions = section.draws.map { it.date to it.session }.toSet().size
                    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("လုပ်ငန်း Session ${realSessions} ကြိမ် • ဒိုင်အလိုက်စာရင်း ${totals.draws} ကြိမ်"),
                        style = MaterialTheme.typography.labelSmall)
                }
                AgentStatementTableHeader(tableScroll)
                val byDraw = section.draws.groupBy { it.date to it.session }
                    .toList().sortedWith(compareBy({ it.first.first }, { it.first.second.ordinal }))
                byDraw.forEach { (key, draws) ->
                    val id = "${key.first}/${key.second.name}"
                    val selected = openedDraw == id
                    Surface(onClick = { openedDraw = if (selected) null else id },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
                            AgentStatementTableRow(
                                "${key.first.dayOfMonth} ${if (key.second == DrawSession.MORNING) "မနက်" else "ညနေ"}",
                                AgentStatementCalculator.summarize(draws), tableScroll,
                            )
                        }
                    }
                    if (selected) {
                        draws.forEach { draw ->
                            AgentStatementDrawDetail(draw,
                                agentNames[draw.agentId] ?: "Agent #${draw.agentId}",
                                customerNames, showAgents)
                        }
                    }
                }
                HorizontalDivider()
                AgentStatementTableRow("စုစုပေါင်း", totals, tableScroll, true)
            }
        }
    }
}

@Composable
fun AgentStatementResults(
    vm: LedgerViewModel,
    agentId: Long,
    period: StatementPeriod,
    range: StatementRange,
    session: DrawSession?,
    subtitle: String,
    onReselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val statement by produceState<AgentStatement?>(null, agentId, range, session, revision) {
        value = null
        value = vm.agentStatement(agentId, range, session)
    }
    val customers by produceState<Map<Long, String>>(emptyMap(), statement?.agentIds) {
        value = statement?.let { vm.agentStatementCustomerNames(it.agentIds) }.orEmpty()
    }
    val agentNames = agents.associate { it.id to it.name }
    var view by rememberSaveable(agentId) { mutableStateOf("agents") }
    var search by rememberSaveable(agentId) { mutableStateOf("") }
    var openedAgent by rememberSaveable(agentId) { mutableLongStateOf(-1L) }

    LazyColumn(modifier, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subtitle, fontWeight = FontWeight.SemiBold)
                    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("${range.start.reviewDate()}${if (range.start == range.end) ")" else " – ${range.end.reviewDate()}"}" +
                        " • " + (session?.reviewLabel() ?: "နှစ်ကြိမ်လုံး"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onReselect) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပြန်ရွေးရန်")) }
            }
        }
        val data = statement
        if (data == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        AgentStatementSummary(data.totals, if (agentId == -1L) data.agentIds.size else null)
                    }
                }
            }
            item {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ကော်မရှင်သည် App မှာ သိမ်းထားသော Net Commission ဖြစ်သည်။ ") +
                    "ဒိုင်မှပေးရမည့် Gross Commission သို့မဟုတ် လက်တွေ့ငွေလွှဲပြီးကြောင်း အတည်ပြုချက် မဟုတ်ပါ။",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (agentId == -1L) item {
                val realSessions = data.draws.map { it.date to it.session }.toSet().size
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("လုပ်ငန်း Session ${realSessions} ကြိမ် • ဒိုင်အလိုက်စာရင်း ${data.totals.draws} ကြိမ်"),
                    style = MaterialTheme.typography.labelSmall)
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး စာရင်းပေါင်းသည် Agent အလိုက်သိမ်းထားသော စာရင်းများပေါင်းသာဖြစ်သည်။ ") +
                    "Self/အခြားဒိုင်ဆီ ပြန်တင်ထားသောစာရင်းများ ပါနိုင်သဖြင့် မူလရောင်းအားအမှန်ဟု မသတ်မှတ်ပါ။",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (data.draws.isEmpty()) {
                item { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရွေးထားသောကာလအတွက် ထိုးစာရင်းမရှိပါ။")) }
            } else {
                if (agentId == -1L) item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(view == "agents", { view = "agents" }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်တစ်ယောက်ချင်း")) })
                        FilterChip(view == "total", { view = "total" }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အားလုံးစုစုပေါင်း")) })
                    }
                }
                if (agentId == -1L && view == "agents") {
                    item {
                        OutlinedTextField(search, { search = it }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အမည်ရှာရန်")) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    val shown = data.agentIds.sortedBy { agentNames[it] ?: it.toString() }
                        .filter { (agentNames[it] ?: "Agent #$it").contains(search, ignoreCase = true) }
                    items(shown, key = { it }) { id ->
                        val draws = data.draws.filter { it.agentId == id }
                        val totals = AgentStatementCalculator.summarize(draws)
                        val open = openedAgent == id
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Surface(onClick = { openedAgent = if (open) -1L else id }, color = Color.Transparent) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(agentNames[id] ?: "Agent #$id", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                    }
                                }
                                AgentStatementSummary(totals, null, compact = true)
                                if (open) agentReportSections(draws, period, range).forEach {
                                    AgentStatementSectionCard(it, customers, agentNames, showAgents = false)
                                }
                            }
                        }
                    }
                    item {
                        val included = shown.toSet()
                        val totals = AgentStatementCalculator.summarize(data.draws.filter { it.agentId in included })
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အောက်ဆုံးစုစုပေါင်း"), fontWeight = FontWeight.Bold)
                                AgentStatementSummary(totals, null, compact = true)
                                if (search.isNotBlank()) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရှာထားသည့်ဒိုင်များအတွက်သာ"),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    item { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ကာလအလိုက် စာရင်း"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    val sections = agentReportSections(data.draws, period, range)
                    items(sections, key = { it.start }) { section ->
                        AgentStatementSectionCard(section, customers, agentNames, showAgents = agentId == -1L)
                    }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အောက်ဆုံးစုစုပေါင်း"), fontWeight = FontWeight.Bold)
                                val bottomScroll = rememberScrollState()
                                AgentStatementTableHeader(bottomScroll)
                                AgentStatementTableRow("စုစုပေါင်း", data.totals, bottomScroll, true)
                            }
                        }
                    }
                }
            }
        }
    }
}
