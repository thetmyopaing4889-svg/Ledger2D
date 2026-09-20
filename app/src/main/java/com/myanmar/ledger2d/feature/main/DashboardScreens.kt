@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.database.AgentEntity
import com.myanmar.ledger2d.core.database.CustomerEntity
import com.myanmar.ledger2d.core.database.BetEntryEntity
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.database.ClosedNumberEntity
import com.myanmar.ledger2d.core.database.AgentSpecialLimitEntity
import com.myanmar.ledger2d.core.design.*
import com.myanmar.ledger2d.core.domain.AnalysisResult
import com.myanmar.ledger2d.core.domain.WeeklyReport
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

private data class DashboardAction(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val key: String)

private val agentActions = listOf(
    DashboardAction("Agent List", "Add Agent ဖြင့် ဖန်တီးထားသော Agent များ", Icons.Default.List, "agentList"),
    DashboardAction("စုစုပေါင်းစာရင်း", "Agent အလိုက် စာရင်းစုစုပေါင်း", Icons.Default.ReceiptLong, "total"),
    DashboardAction("အစီရင်ခံစာ", "Agent အလိုက် report", Icons.Default.Assessment, "report"),
    DashboardAction("ပိတ်ဂဏန်း", "Agent အလိုက် လက်မခံမည့်ဂဏန်း", Icons.Default.Lock, "closed"),
    DashboardAction("ထီပေါက်စဉ်", "Global result မှတွက်ထားသော Agent result", Icons.Default.EmojiEvents, "winning"),
    DashboardAction("ကန့်သတ်ပမာဏ", "Agent-wide limits", Icons.Default.Tune, "limit")
)

private val customerActions = listOf(
    DashboardAction("Customer List", "ရွေးထားသော Agent အောက်ရှိ Customer များ", Icons.Default.List, "customerList"),
    DashboardAction("စာရင်းမှတ်တမ်း", "Customer ၏ စာရင်းများ", Icons.Default.ReceiptLong, "history"),
    DashboardAction("အစီရင်ခံစာ", "Customer အလိုက် report", Icons.Default.Assessment, "report"),
    DashboardAction("အမြန်သုံးသပ်ချက်", "လက်ရှိစာရင်းအခြေအနေ", Icons.Default.Insights, "analysis"),
    DashboardAction("အကွက်စာရင်း", "00–99 အကွက်များ", Icons.Default.GridView, "digits"),
    DashboardAction("ကော်မရှင်", "Customer commission", Icons.Default.Percent, "commission"),
    DashboardAction("ထီပေါက်စဉ်", "Global result မှတွက်ထားသော Customer result", Icons.Default.EmojiEvents, "winning")
)

@Composable
fun AgentDashboardScreen(vm: LedgerViewModel, onBack: () -> Unit, onAgentInfo: (Long) -> Unit, onAddAgent: () -> Unit, onFeature: (String) -> Unit) {
    val l = LocalLanguage.current
    AppScaffold(l.translate("Agent Dashboard"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(agentActions) { action ->
                ElevatedCard(onClick = { onFeature(action.key) }, modifier = Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.medium, colors=CardDefaults.elevatedCardColors(containerColor=Color.White), elevation=CardDefaults.elevatedCardElevation(defaultElevation=1.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(color=AppColors.GoldSoft, shape=MaterialTheme.shapes.small, border=BorderStroke(1.dp, AppColors.Stone)) { Icon(action.icon, null, tint=AppColors.PrimaryDeep, modifier=Modifier.padding(9.dp).size(22.dp)) }
                        Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)) { Text(l.translate(action.title), fontWeight=FontWeight.Bold); Text(l.translate(action.subtitle), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1) }
                        Icon(Icons.Default.ChevronRight, null, tint=AppColors.Primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AgentScopeScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit, onOpen: (String, Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var selected by rememberSaveable { mutableStateOf(language.defaultAgentId) }
    LaunchedEffect(agents, language.defaultAgentId) { if (selected != -1L && agents.none { it.id == selected }) selected = agents.firstOrNull { it.id == language.defaultAgentId }?.id ?: agents.firstOrNull()?.id ?: 0L }
    val selectedLabel = if (selected == -1L) "ဒိုင်အားလုံး" else agents.firstOrNull { it.id == selected }?.name ?: "ဒိုင်ရွေးရန်"
    val featureTitle = agentActions.firstOrNull { it.key == feature }?.title ?: "Agent feature"
    AppScaffold(featureTitle, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Agent ရွေးရန်", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ScopeDropdown("Agent ရွေးရန်", selectedLabel, listOf(-1L to "ဒိုင်အားလုံး") + agents.map { it.id to it.name }, selected) { selected = it }
            if (selected == 0L) {
                UnavailableState("Agent တစ်ယောက် သို့မဟုတ် ဒိုင်အားလုံးကို ရွေးပါ")
            } else {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Text("ရွေးထားသည် • $selectedLabel", Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Bold) }
                Button(onClick = { onOpen(feature, selected) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("ဆက်သွားရန်") }
            }
        }
    }
}

@Composable
fun CustomerDashboardScreen(vm: LedgerViewModel, onBack: () -> Unit, onCustomerInfo: (Long) -> Unit, onFeature: (String) -> Unit) {
    val l = LocalLanguage.current
    AppScaffold(l.translate("Customer Dashboard"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(customerActions) { action ->
                ElevatedCard(onClick = { onFeature(action.key) }, modifier = Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.medium, colors=CardDefaults.elevatedCardColors(containerColor=AppColors.Champagne), elevation=CardDefaults.elevatedCardElevation(defaultElevation=1.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = AppColors.GoldSoft, shape = MaterialTheme.shapes.small) { Icon(action.icon, null, tint = AppColors.PrimaryDeep, modifier = Modifier.padding(8.dp).size(22.dp)) }
                        Text(l.translate(action.title), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ChevronRight, null, tint = AppColors.Primary)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerScopeScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit, onOpen: (String, Long, Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var agentId by rememberSaveable { mutableStateOf(language.defaultAgentId) }
    var customerId by rememberSaveable { mutableStateOf(language.defaultCustomerId) }
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(agentId) { customerId = 0L }
    val agentLabel = agents.firstOrNull { it.id == agentId }?.name ?: "ဒိုင်ရွေးရန်"
    val customerLabel = when { agentId == 0L -> "ဒိုင်ရွေးပြီးမှ ရွေးပါ"; customerId == -1L -> "Customer အားလုံး"; else -> customers.firstOrNull { it.id == customerId }?.name ?: "Customer ရွေးရန်" }
    val featureTitle = customerActions.firstOrNull { it.key == feature }?.title ?: "Customer feature"
    AppScaffold(featureTitle, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Agent ရွေးရန် နှင့် Customer ရွေးရန်", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ScopeDropdown("Agent ရွေးရန်", agentLabel, agents.map { it.id to it.name }, agentId) { agentId = it }
            ScopeDropdown("Customer ရွေးရန်", customerLabel, if (agentId == 0L) emptyList() else listOf(-1L to "Customer အားလုံး") + customers.map { it.id to it.name }, customerId, enabled = agentId != 0L) { customerId = it }
            if (agentId == 0L || customerId == 0L) UnavailableState("Agent တစ်ယောက်နှင့် Customer အားလုံး သို့မဟုတ် Customer တစ်ယောက်ကို ရွေးပါ")
            else {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Text("$agentLabel • $customerLabel", Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Bold) }
                Button(onClick = { onOpen(feature, agentId, customerId) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("ဆက်သွားရန်") }
            }
        }
    }
}

@Composable
fun AgentFeatureWorkspaceScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var selected by rememberSaveable { mutableStateOf(language.defaultAgentId) }
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    LaunchedEffect(language.selectedDate, language.selectedSession) { dateText = language.selectedDate; session = language.selectedSession }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val summaries by produceState<List<ScopeSummary>>(emptyList(), date, session, feature, revision) { value = vm.allAgentSummaries(date, session, feature == "winning" || feature == "report") }
    val winners by vm.winners.collectAsStateWithLifecycle()
    val winnerAvailable = winners.any { it.date == date && it.session == session }
    val visible = if (selected == -1L) summaries else summaries.filter { it.id == selected }
    AppScaffold(agentActions.firstOrNull { it.key == feature }?.title ?: "Agent feature", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SelectionCard {
                ScopeDropdown("Agent ရွေးရန်", if (selected == -1L) "ဒိုင်အားလုံး" else agents.firstOrNull { it.id == selected }?.name ?: "ဒိုင်ရွေးရန်", listOf(-1L to "ဒိုင်အားလုံး") + agents.map { it.id to it.name }, selected) { selected = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ", Modifier.weight(1f))
                    DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(draw.label) }) }
                }
            } }
            if (selected == 0L) item { UnavailableState("Agent တစ်ယောက် သို့မဟုတ် ဒိုင်အားလုံးကို ရွေးပါ") }
            else if (selected == -1L && feature in setOf("closed", "limit")) item { UnavailableState("ပိတ်ဂဏန်းနှင့် ကန့်သတ်ပမာဏအတွက် Agent တစ်ယောက်ကို ရွေးပါ") }
            else when (feature) {
                "closed" -> item { AgentClosedWorkspace(vm, selected, onBack) }
                "limit" -> item { AgentLimitWorkspace(vm, selected, onBack) }
                "report" -> item { if (selected == -1L) SummaryRows(visible) else AgentReportWorkspace(vm, selected, date, session) }
                "winning" -> item { if (!winnerAvailable) UnavailableState("ထီပေါက်ဂဏန်း မရှိသေးပါ") else if (selected == -1L) SummaryRows(visible) else AgentReportWorkspace(vm, selected, date, session, winningOnly = true) }
                else -> {
                    if (feature == "total" && selected > 0L) item { AgentTotalTableWorkspace(vm, selected, date, session) }
                    else { if (visible.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "ရွေးထားသော အခြေအနေအတွက် အချက်အလက်မရှိသေးပါ") }; items(visible, key = { it.id }) { summary -> ScopeSummaryCard(summary) } }
                }
            }
        }
    }
}

@Composable
fun CustomerFeatureWorkspaceScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit, onEditEntry: (Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var agentId by rememberSaveable { mutableStateOf(language.defaultAgentId) }
    var customerId by rememberSaveable { mutableStateOf(language.defaultCustomerId) }
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    LaunchedEffect(language.selectedDate, language.selectedSession) { dateText = language.selectedDate; session = language.selectedSession }
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(agents, language.defaultAgentId) { if (agents.none { it.id == agentId }) agentId = agents.firstOrNull { it.id == language.defaultAgentId }?.id ?: agents.firstOrNull()?.id ?: 0L }
    LaunchedEffect(customers, agentId, language.defaultCustomerId) { if (customers.none { it.id == customerId }) customerId = customers.firstOrNull { it.id == language.defaultCustomerId }?.id ?: customers.firstOrNull()?.id ?: 0L }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val summaries by produceState<List<ScopeSummary>>(emptyList(), date, session, feature, agentId, revision) { value = if (agentId > 0L) vm.allCustomerSummaries(agentId, date, session, feature == "winning" || feature == "report") else emptyList() }
    val winners by vm.winners.collectAsStateWithLifecycle()
    val winnerAvailable = winners.any { it.date == date && it.session == session }
    val visible = if (customerId == -1L) summaries else summaries.filter { it.id == customerId }
    AppScaffold(customerActions.firstOrNull { it.key == feature }?.title ?: "Customer feature", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SelectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScopeDropdown("Agent ရွေးရန်", agents.firstOrNull { it.id == agentId }?.name ?: "ဒိုင်ရွေးရန်", agents.map { it.id to it.name }, agentId, Modifier.weight(1f)) { agentId = it; customerId = if (it == language.defaultAgentId) language.defaultCustomerId else 0L }
                    ScopeDropdown("Customer ရွေးရန်", when { agentId == 0L -> "ဒိုင်ရွေးပြီးမှ ရွေးပါ"; customerId == -1L -> "Customer အားလုံး"; else -> customers.firstOrNull { it.id == customerId }?.name ?: "Customer ရွေးရန်" }, if (agentId == 0L) emptyList() else listOf(-1L to "Customer အားလုံး") + customers.map { it.id to it.name }, customerId, Modifier.weight(1f), enabled = agentId > 0L) { customerId = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ", Modifier.weight(1f))
                    DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(draw.label) }) }
                }
            } }
            if (agentId == 0L || customerId == 0L) item { UnavailableState("Agent နှင့် Customer ကို ရွေးပါ") }
            else when (feature) {
                "history" -> item { CustomerHistoryWorkspace(vm, customerId, onEditEntry) }
                "analysis" -> item { CustomerAnalysisWorkspace(vm, customerId, date, session) }
                "digits" -> item { CustomerDigitsWorkspace(vm, customerId, date, session) }
                "commission" -> item { CustomerCommissionWorkspace(vm, customerId, date, session) }
                "report" -> item { if (customerId == -1L) SummaryRows(visible) else CustomerReportWorkspace(vm, customerId, date, session) }
                "winning" -> item { if (!winnerAvailable) UnavailableState("ထီပေါက်ဂဏန်း မရှိသေးပါ") else if (customerId == -1L) SummaryRows(visible) else CustomerReportWorkspace(vm, customerId, date, session, winningOnly = true) }
                else -> { if (visible.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "ရွေးထားသော Customer အတွက် အချက်အလက်မရှိသေးပါ") }; items(visible, key = { it.id }) { summary -> ScopeSummaryCard(summary) } }
            }
        }
    }
}

@Composable private fun SelectionCard(content: @Composable ColumnScope.() -> Unit) { Surface(Modifier.fillMaxWidth(), color=AppColors.Champagne, shape=MaterialTheme.shapes.medium, border=BorderStroke(1.dp, AppColors.Gold.copy(alpha=.35f))) { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content=content) } }

@Composable
fun ScopeDropdown(label: String, selected: String, options: List<Pair<Long, String>>, value: Long, modifier: Modifier = Modifier, enabled: Boolean = true, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(selected, {}, Modifier.fillMaxWidth().menuAnchor(), enabled = enabled, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) })
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) { options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(id); expanded = false }) } }
    }
}

@Composable private fun ScopeSummaryCard(summary: ScopeSummary) { ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(summary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("ထိုးကြေး ${summary.totalBet.mmk()} • ပေါက်ကြေး ${summary.winningStake.mmk()}"); Text("လျော် ${summary.payout.mmk()} • ကော်မရှင် ${summary.commission.mmk()} • ရှုံး/မြတ် ${summary.profitLoss.mmk()}") } } }
@Composable private fun SummaryRows(rows: List<ScopeSummary>) { if (rows.isEmpty()) EmptyState("စာရင်းမရှိသေးပါ", "ရွေးထားသော ရက်နှင့်အချိန်အတွက် စာရင်းမရှိသေးပါ") else rows.forEach { ScopeSummaryCard(it) } }

@Composable private fun AgentReportWorkspace(vm: LedgerViewModel, agentId: Long, date: LocalDate, session: DrawSession, winningOnly: Boolean = false) { var after by rememberSaveable { mutableStateOf(winningOnly) }; val revision by vm.revision.collectAsStateWithLifecycle(); val report by produceState<DrawReport?>(null, agentId, date, session, after, revision) { value = vm.agentReport(agentId, date, session, after) }; val rows by produceState<List<AgentCustomerReportRow>>(emptyList(), agentId, date, session, after, revision) { value = vm.agentCustomerReport(agentId, date, session, after) }; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (!winningOnly) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!after, { after = false }, label = { Text("Before") }); FilterChip(after, { after = true }, label = { Text("After") }) }; if (after && report?.winnerAvailable != true) UnavailableState("ထီပေါက်ပြီးချိန်အတွက် ရလဒ်မရှိသေးပါ") else { report?.let { ReportCard(it.calculation, it.winningDigit) }; Text("Customer တစ်ယောက်ချင်း breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); rows.forEach { row -> ElevatedCard { ListItem(headlineContent = { Text(row.customer.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("ထိုးကြေး ${row.calculation.totalBet.mmk()} • ပေါက်ကြေး ${row.calculation.winningStake.mmk()} • လျော် ${row.calculation.payout.mmk()}") }, trailingContent = { Text(row.calculation.profitLoss.mmk(), fontWeight = FontWeight.Bold) }) } } } } }

@Composable private fun CustomerReportWorkspace(vm: LedgerViewModel, customerId: Long, date: LocalDate, session: DrawSession, winningOnly: Boolean = false) { var after by rememberSaveable { mutableStateOf(winningOnly) }; var weekly by rememberSaveable { mutableStateOf(false) }; val revision by vm.revision.collectAsStateWithLifecycle(); val report by produceState<DrawReport?>(null, customerId, date, session, after, revision) { value = vm.customerReport(customerId, date, session, after) }; val week by produceState<WeeklyReport?>(null, customerId, date, after, weekly, revision) { value = if (weekly) vm.weeklyCustomerReport(customerId, date, after) else null }; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!weekly, { weekly = false }, label = { Text("နေ့စဉ်") }); FilterChip(weekly, { weekly = true }, label = { Text("အပတ်စဉ်") }) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!after, { after = false }, label = { Text("Before") }); FilterChip(after, { after = true }, label = { Text("After") }) }; if (weekly) week?.let { weeklyReport -> weeklyReport.rows.forEach { row -> OutlinedCard { Column(Modifier.padding(10.dp)) { Text("${row.date.displayDate()} • ${row.session.label}", fontWeight = FontWeight.Bold); Text("ထွက်ဂဏန်း: ${row.winningDigit ?: "—"}"); row.calculation?.let { Text("ထိုးကြေး ${it.totalBet.mmk()} • ပေါက်ကြေး ${it.winningStake.mmk()} • လျော် ${it.payout.mmk()} • ကော်မရှင် ${it.commission.mmk()} • ရှုံး/မြတ် ${it.profitLoss.mmk()}") } ?: Text("အချက်အလက် မရှိသေးပါ") } } }; AnalysisMetric("အပတ်စဉ် ထိုးကြေးစုစုပေါင်း", weeklyReport.totalBet.mmk()); AnalysisMetric("အပတ်စဉ် ကော်မရှင်", weeklyReport.commission.mmk()); AnalysisMetric("အပတ်စဉ် လျော်ပေးငွေ", weeklyReport.payout.mmk()); AnalysisMetric("အပတ်စဉ် ရှုံး/မြတ်", weeklyReport.profitLoss.mmk()) } else if (after && report?.winnerAvailable != true) UnavailableState("ထီပေါက်ပြီးချိန်အတွက် ရလဒ်မရှိသေးပါ") else report?.let { ReportCard(it.calculation, it.winningDigit) } } }

@Composable private fun AgentClosedWorkspace(vm: LedgerViewModel, agentId: Long, onBack: () -> Unit) { val numbers by vm.closedNumbers(agentId).collectAsStateWithLifecycle(initialValue = emptyList()); var digit by rememberSaveable { mutableStateOf("") }; var pending by remember { mutableStateOf<ClosedNumberEntity?>(null) }; pending?.let { value -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("ပိတ်ဂဏန်းဖယ်ရှားမည်လား") }, text = { Text("${value.digit} ကို ဒီဒိုင်အောက်က ထိုးသားများအားလုံးအတွက် ပြန်ဖွင့်မည်လား?") }, confirmButton = { TextButton({ vm.removeClosedNumber(value); pending = null }) { Text("ဖျက်မည်") } }, dismissButton = { TextButton({ pending = null }) { Text("မလုပ်ပါ") } }) }; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("ဒီဒိုင်အောက်က ထိုးသားအားလုံးအတွက် ပိတ်ဂဏန်း", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(digit, { digit = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("ဂဏန်း 00–99") }); Button({ vm.addClosedNumber(agentId, digit); digit = "" }, enabled = com.myanmar.ledger2d.core.domain.BetParser.validDigit(digit)) { Text("ပိတ်မည်") } }; numbers.forEach { number -> ListItem(headlineContent = { Text(number.digit, fontWeight = FontWeight.Bold) }, supportingContent = { Text("ပိတ်ထားသည်") }, trailingContent = { TextButton({ pending = number }) { Text("ဖယ်ရှားမည်") } }) } } }

@Composable private fun AgentLimitWorkspace(vm: LedgerViewModel, agentId: Long, onBack: () -> Unit) { val all by vm.agentAllLimit(agentId).collectAsStateWithLifecycle(initialValue = null); val specials by vm.agentSpecialLimits(agentId).collectAsStateWithLifecycle(initialValue = emptyList()); var allText by rememberSaveable { mutableStateOf("") }; var digit by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }; var pending by remember { mutableStateOf<AgentSpecialLimitEntity?>(null) }; LaunchedEffect(all) { allText = all?.amount?.toString() ?: "" }; pending?.let { value -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("အထူးကန့်သတ်ချက်ဖယ်ရှားမည်လား") }, text = { Text("${value.digit} အတွက် limit ကို ဖယ်ရှားမည်လား?") }, confirmButton = { TextButton({ vm.removeAgentSpecialLimit(value); pending = null }) { Text("ဖျက်မည်") } }, dismissButton = { TextButton({ pending = null }) { Text("မလုပ်ပါ") } }) }; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Agent-wide limit", style = MaterialTheme.typography.titleMedium); OutlinedTextField(allText, { allText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("အကွက်အားလုံးအတွက် limit") }); Button({ vm.updateAgentAllLimit(agentId, allText) {} }) { Text("သိမ်းမည်") }; HorizontalDivider(); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(digit, { digit = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("ဂဏန်း") }); OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("ပမာဏ") }); Button({ vm.addAgentSpecialLimit(agentId, digit, amount); digit = ""; amount = "" }, enabled = com.myanmar.ledger2d.core.domain.BetParser.validDigit(digit) && (amount.toLongOrNull()?.let { value -> value > 0 } == true)) { Text("သိမ်းမည်") } }; specials.forEach { limit -> ListItem(headlineContent = { Text(limit.digit) }, supportingContent = { Text(limit.amount.mmk()) }, trailingContent = { TextButton({ pending = limit }) { Text("ဖယ်ရှားမည်") } }) } } }

@Composable private fun CustomerHistoryWorkspace(vm: LedgerViewModel, customerId: Long, onEditEntry: (Long) -> Unit) {
    val language = LocalLanguage.current
    val selectedDate = runCatching { LocalDate.parse(language.selectedDate) }.getOrElse { LocalDate.now() }
    val entries by vm.customerEntries(customerId, selectedDate, language.selectedSession).collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingDelete by remember { mutableStateOf<BetEntryEntity?>(null) }
    var expandedEntry by remember { mutableStateOf<BetEntryWithLines?>(null) }
    var actionEntry by remember { mutableStateOf<BetEntryWithLines?>(null) }
    expandedEntry?.let { record ->
        AlertDialog(onDismissRequest = { expandedEntry = null }, confirmButton = { TextButton({ expandedEntry = null }) { Text("ပိတ်မည်") } }, title = { Text("အကွက်အသေးစိတ်") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(transactionDisplayText(record.entry.inputFormat, record.entry.sourceText), fontWeight = FontWeight.Bold)
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(record.lines, key = { it.id }) { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(line.digit, fontWeight = FontWeight.Bold); Text(line.amount.mmk()) }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", fontWeight = FontWeight.Bold); Text(record.lines.sumOf { it.amount }.mmk(), fontWeight = FontWeight.Black) }
                Text("${record.lines.size} ကွက်", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        })
    }
    pendingDelete?.let { entry -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("စာရင်းဖျက်မည်လား") }, text = { Text("ဒီစာရင်းကို အပြီးဖျက်မလား?") }, confirmButton = { TextButton({ vm.deleteBet(entry); pendingDelete = null }) { Text("ဖျက်မည်") } }, dismissButton = { TextButton({ pendingDelete = null }) { Text("မလုပ်ပါ") } }) }
    actionEntry?.let { entry -> AlertDialog(onDismissRequest = { actionEntry = null }, title = { Text("စာရင်းလုပ်ဆောင်ချက်") }, text = { Text(transactionDisplayText(entry.entry.inputFormat, entry.entry.sourceText)) }, confirmButton = { TextButton({ actionEntry = null; onEditEntry(entry.entry.id) }) { Text("ပြင်မည်") } }, dismissButton = { TextButton({ actionEntry = null; pendingDelete = entry.entry }) { Text("ဖျက်မည်") } }) }
    if (entries.isEmpty()) EmptyState("စာရင်းမရှိသေးပါ", "အတည်ပြုထားသော စာရင်းမရှိသေးပါ") else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("စဉ်", Modifier.weight(.45f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold); Text("အကွက်", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold); Text("အကွက်အရေအတွက်", Modifier.weight(1.25f), style = MaterialTheme.typography.labelSmall, color = AppColors.Primary, fontWeight = FontWeight.SemiBold); Text("ငွေပမာဏ", Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End) }
            HorizontalDivider()
            entries.forEachIndexed { index, entry ->
                val amount = entry.lines.sumOf { it.amount }
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { expandedEntry = entry }, onLongClick = { actionEntry = entry }).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", Modifier.weight(.45f), style = MaterialTheme.typography.bodySmall)
                    Text(transactionDisplayText(entry.entry.inputFormat, entry.entry.sourceText), Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text("${entry.lines.size} ကွက်", Modifier.weight(1.25f), color = AppColors.Primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(amount.mmk(), Modifier.weight(1.1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
            Row(Modifier.fillMaxWidth().background(AppColors.Blush.copy(alpha = .45f), MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("စုစုပေါင်း", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold); Text("${entries.sumOf { it.lines.size }} ကွက်   ${entries.sumOf { it.lines.sumOf { line -> line.amount } }.mmk()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AppColors.Primary) }
        }
    }
}

@Composable private fun CustomerAnalysisWorkspace(vm: LedgerViewModel, customerId: Long, date: LocalDate, session: DrawSession) { val revision by vm.revision.collectAsStateWithLifecycle(); val result by produceState<AnalysisResult?>(null, customerId, date, session, revision) { value = vm.analysis(customerId, date, session) }; result?.let { analysis -> AnalysisMetric("လက်ရှိအကွက်အရေအတွက်", analysis.distinctDigits.toString()); AnalysisMetric("ထိုးကြေးစုစုပေါင်း", analysis.totalBet.mmk()); AnalysisMetric("ကန့်သတ်ထားသောအကွက်", analysis.limitedDigits.toString()); AnalysisMetric("80%+ သတိပေး", analysis.warningDigits.toString()); AnalysisMetric("90%+ အလွန်နီး", analysis.nearDigits.toString()); AnalysisMetric("100% ပြည့်ပြီး", analysis.fullDigits.toString()); AnalysisMetric("အဆိုးဆုံးလျော်ပေးရနိုင်မှု", analysis.worstCasePayout.mmk()); AnalysisMetric("အဆိုးဆုံး ရှုံး/မြတ်", analysis.worstCaseProfitLoss.mmk()); Text("ထိုးကြေးအများဆုံးအကွက်များ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); analysis.highest.forEach { Text("${it.first} • ${it.second.mmk()}") }; Text("ပိတ်ထားသောအကွက်များ: ${analysis.closedDigits.sorted().joinToString(", ").ifBlank { "မရှိ" }}"); Text("ထပ်မလက်ခံသင့်သောအကွက်များ: ${analysis.rejectDigits.sorted().joinToString(", ").ifBlank { "မရှိ" }}"); Text("အကွက်အနိုင်ရ scenario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); analysis.scenarios.filter { it.stake > 0 }.forEach { scenario -> Text("${scenario.digit}: ထိုး ${scenario.stake.mmk()} • လျော် ${scenario.payout.mmk()} • ရှုံး/မြတ် ${scenario.profitLoss.mmk()}") } } }

@Composable private fun CustomerDigitsWorkspace(vm: LedgerViewModel, customerId: Long, date: LocalDate, session: DrawSession) {
    val totals by vm.customerTotals(customerId, date, session).collectAsStateWithLifecycle(initialValue = emptyList())
    val specialLimits by vm.specialLimits(customerId).collectAsStateWithLifecycle(initialValue = emptyList())
    val amounts = totals.associate { it.digit to it.amount }
    val specialDigits = specialLimits.map { it.digit }.toSet()
    val revision by vm.revision.collectAsStateWithLifecycle()
    val analysis by produceState<AnalysisResult?>(null, customerId, date, session, revision) { value = vm.analysis(customerId, date, session) }
    val scenarios = analysis?.scenarios?.associateBy { it.digit }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("00–99 အကွက်စာရင်း", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("အနီ = ထိုးကြေးရှိ • မီးခိုး = မရှိ • အဝါ = special limit • အနီရင့် = ပိတ်ဂဏန်း", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..99).chunked(5).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { n ->
            val digit = n.toString().padStart(2, '0'); val scenario = scenarios[digit]; val amount = amounts[digit] ?: 0L
            val amountColor = when { scenario?.closed == true -> MaterialTheme.colorScheme.error; digit in specialDigits -> MaterialTheme.colorScheme.tertiary; amount > 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f) }
            Surface(Modifier.weight(1f), color = if (scenario?.closed == true) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { Column(Modifier.padding(5.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(digit, fontWeight = FontWeight.Bold, color = if (scenario?.closed == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); Text(amount.mmk(), style = MaterialTheme.typography.labelSmall, color = amountColor, fontWeight = if (amount > 0) FontWeight.Bold else FontWeight.Normal); scenario?.percentUsed?.let { Text("$it%", style = MaterialTheme.typography.labelSmall, color = amountColor) } } }
        } } }
    }
}

@Composable private fun CustomerCommissionWorkspace(vm: LedgerViewModel, customerId: Long, date: LocalDate, session: DrawSession) {
    val customer by vm.customer(customerId).collectAsStateWithLifecycle(initialValue = null)
    var value by rememberSaveable { mutableStateOf("") }
    var saved by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(customer) { customer?.let { value = java.math.BigDecimal(it.commissionRateBasisPoints).movePointLeft(2).stripTrailingZeros().toPlainString() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ဒီ Customer ၏ ကော်မရှင်နှုန်းထား", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("တွက်ချက်မည့်ကာလ • ${date.displayDate()} • ${session.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text("စုစုပေါင်းထိုးကြေး၏ ရာခိုင်နှုန်းအဖြစ် တွက်ပြီး စာရင်းတစ်ခုချင်း snapshot သိမ်းထားသည်။ Rate ပြောင်းလဲပါက ရှိပြီးသားစာရင်းများကိုလည်း ပြန်တွက်မည်။", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value, { saved = false; value = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, Modifier.fillMaxWidth(), label = { Text("ရာခိုင်နှုန်း") })
        Button({ vm.updateCommission(customerId, value) { saved = true } }, enabled = value.toBigDecimalOrNull()?.let { it >= java.math.BigDecimal.ZERO && it <= java.math.BigDecimal(100) } == true) { Text("သိမ်းမည်") }
        if (saved) Text("သိမ်းပြီးပါပြီ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AddCustomerFromHomeScreen(vm: LedgerViewModel, onBack: () -> Unit, onCreate: (Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(0L) }
    AppScaffold("ထိုးသားအသစ်ထည့်ရန်", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Customer ကို ဘယ် Agent အောက်မှာထည့်မလဲ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ScopeDropdown("Agent ရွေးရန်", agents.firstOrNull { it.id == selected }?.name ?: "ဒိုင်ရွေးရန်", agents.map { it.id to it.name }, selected) { selected = it }
            Button(onClick = { onCreate(selected) }, enabled = selected > 0, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Customer form သို့သွားရန်") }
        }
    }
}

@Composable
private fun AgentTotalTableWorkspace(vm: LedgerViewModel, agentId: Long, date: LocalDate, session: DrawSession) {
    val totals by vm.agentTotalsWithCommission(agentId, date, session).collectAsStateWithLifecycle(initialValue = emptyList())
    val totalBet = totals.sumOf { it.amount }
    val totalCommission = totals.sumOf { it.commission }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${date.displayDate()} • ${session.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(Modifier.fillMaxWidth(), color = AppColors.Wine, shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("စုစုပေါင်းအနှစ်ချုပ်", color = AppColors.GoldSoft, fontWeight = FontWeight.Bold)
                SummaryAmount("စုစုပေါင်းထိုးကြေး", totalBet, Color.White)
                SummaryAmount("ကော်မရှင်", totalCommission, Color.White)
                HorizontalDivider(color = AppColors.GoldSoft.copy(alpha = .45f))
                SummaryAmount("ကော်မရှင်ပြီးနောက်ကျန်ငွေ", totalBet - totalCommission, AppColors.GoldSoft)
            }
        }
        Row(Modifier.fillMaxWidth().background(AppColors.Wine, MaterialTheme.shapes.small).padding(10.dp)) {
            Text("ဂဏန်း", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
            Text("ထိုးကြေး", Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("ကော်မရှင်", Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("ကော်မရှင်ပြီးနောက်", Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (totals.isEmpty()) Text("ဒီအချိန်အတွက် အတည်ပြုထားသော စာရင်းမရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else totals.forEach { row -> Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) { Row(Modifier.fillMaxWidth()) {
            Text(row.digit, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(row.amount.mmk(), Modifier.weight(1.2f), fontWeight = FontWeight.Bold, color = AppColors.Primary, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(row.commission.mmk(), Modifier.weight(1.2f), fontWeight = FontWeight.Bold, color = AppColors.Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text((row.amount - row.commission).mmk(), Modifier.weight(1.2f), fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }; HorizontalDivider() } }
    }
}

@Composable private fun SummaryAmount(label: String, amount: Long, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = color); Text(amount.mmk(), color = color, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End) }
}

@Composable
fun AllAgentFeatureScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit) {
    val language = LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    LaunchedEffect(language.selectedDate, language.selectedSession) { dateText = language.selectedDate; session = language.selectedSession }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val summaries by produceState<List<ScopeSummary>>(emptyList(), date, session, feature, revision) { value = vm.allAgentSummaries(date, session, feature == "winning" || feature == "report") }
    AppScaffold("ဒိုင်အားလုံး", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("ဒိုင်အားလုံး • ${agentActions.firstOrNull { it.key == feature }?.title ?: feature}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ"); Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } } }
            if (summaries.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "ရွေးထားသော ရက်နှင့်အချိန်အတွက် ဒိုင်စာရင်းမရှိသေးပါ") }
            items(summaries, key = { it.id }) { summary ->
                ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(summary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("ထိုးကြေး ${summary.totalBet.mmk()} • ပေါက်ကြေး ${summary.winningStake.mmk()}"); Text("လျော် ${summary.payout.mmk()} • ကော်မရှင် ${summary.commission.mmk()} • ရှုံး/မြတ် ${summary.profitLoss.mmk()}") } }
            }
        }
    }
}

@Composable
fun AllCustomerScopeScreen(vm: LedgerViewModel, agentId: Long, feature: String, onBack: () -> Unit) {
    val language = LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    LaunchedEffect(language.selectedDate, language.selectedSession) { dateText = language.selectedDate; session = language.selectedSession }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    val summaries by produceState<List<ScopeSummary>>(emptyList(), date, session, feature, agentId, revision) { value = vm.allCustomerSummaries(agentId, date, session, feature == "winning" || feature == "report") }
    AppScaffold("Customer အားလုံး", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("ရွေးထားသော Agent အောက်က Customer အားလုံး • $feature", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ"); Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } } }
            if (customers.isEmpty()) item { EmptyState("Customer မရှိသေးပါ", "ဒီ Agent အောက်မှာ Customer ထည့်ပါ") }
            else if (summaries.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "ရွေးထားသော ရက်နှင့်အချိန်အတွက် Customer စာရင်းမရှိသေးပါ") }
            items(summaries, key = { it.id }) { summary -> ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(summary.name, fontWeight = FontWeight.Bold); Text("ထိုးကြေး ${summary.totalBet.mmk()} • ပေါက်ကြေး ${summary.winningStake.mmk()}"); Text("လျော် ${summary.payout.mmk()} • ကော်မရှင် ${summary.commission.mmk()} • ရှုံး/မြတ် ${summary.profitLoss.mmk()}") } } }
        }
    }
}
