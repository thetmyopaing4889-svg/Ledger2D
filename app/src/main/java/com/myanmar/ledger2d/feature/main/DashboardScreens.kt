@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.sp
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
    DashboardAction("အကွက်စုစုပေါင်း", "ဒိုင်အလိုက်နှင့် ဂဏန်းတူရာ အားလုံးပေါင်း", Icons.Default.GridView, "total"),
    DashboardAction("အစီရင်ခံစာ", "Agent အလိုက် report", Icons.Default.Assessment, "report"),
    DashboardAction("စာရင်းရှင်းတမ်းထုတ်ရန်", "Agent money statement JPEG", Icons.Default.ReceiptLong, "statementJpeg"),
    DashboardAction("ပိတ်ဂဏန်း", "Agent အလိုက် လက်မခံမည့်ဂဏန်း", Icons.Default.Lock, "closed"),
    DashboardAction("ကန့်သတ်ပမာဏ", "Agent-wide limits", Icons.Default.Tune, "limit")
)

private val customerActions = listOf(
    DashboardAction("Customer List", "ရွေးထားသော Agent အောက်ရှိ Customer များ", Icons.Default.List, "customerList"),
    DashboardAction("သွင်းထားသောစာရင်း", "Bet Entries • Messenger စာရင်းများ တိုက်စစ်ရန်", Icons.Default.ReceiptLong, "history"),
    DashboardAction("အစီရင်ခံစာ", "Customer အလိုက် report", Icons.Default.Assessment, "report"),
    DashboardAction("စာရင်းရှင်းတမ်းထုတ်ရန်", "Customer money statement JPEG", Icons.Default.ReceiptLong, "statementJpeg"),
    DashboardAction("အမြန်သုံးသပ်ချက်", "အရှုံး/အမြတ်၊ ကာနည်းနှင့် အကြံပြုချက်", Icons.Default.Insights, "analysis"),
    DashboardAction("အကွက်စာရင်း", "00–99 • View, Sorting နှင့် Search", Icons.Default.GridView, "digits"),
    DashboardAction("ကော်မရှင်", "Customer commission", Icons.Default.Percent, "commission")
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
    LaunchedEffect(agents, language.defaultAgentId) { if (selected != -1L && agents.none { it.id == selected }) selected = agents.firstOrNull { it.id == language.defaultAgentId }?.id ?: 0L }
    val selectedLabel = if (selected == -1L) com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး") else agents.firstOrNull { it.id == selected }?.name ?: com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်ရွေးရန်")
    val featureTitle = agentActions.firstOrNull { it.key == feature }?.title ?: "Agent feature"
    AppScaffold(featureTitle, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent ရွေးရန်"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ScopeDropdown(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent ရွေးရန်"), selectedLabel, listOf(-1L to com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး")) + agents.map { it.id to it.name }, selected) { selected = it }
            if (selected == 0L) {
                UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent တစ်ယောက် သို့မဟုတ် ဒိုင်အားလုံးကို ရွေးပါ"))
            } else {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ရွေးထားသည် • $selectedLabel", "Selected: $selectedLabel"), Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Bold) }
                Button(onClick = { onOpen(feature, selected) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဆက်သွားရန်")) }
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
                        Text(if (action.key == "statementJpeg") l.text("စာရင်းရှင်းတမ်းထုတ်ရန်", "Export customer statement") else l.translate(action.title), Modifier.weight(1f), fontWeight = FontWeight.Bold)
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
    LaunchedEffect(agents, language.defaultAgentId) { if (agents.none { it.id == agentId }) agentId = agents.firstOrNull { it.id == language.defaultAgentId }?.id ?: 0L }
    LaunchedEffect(customers, agentId, language.defaultCustomerId) { if (customers.none { it.id == customerId }) customerId = if (agentId == language.defaultAgentId) customers.firstOrNull { it.id == language.defaultCustomerId }?.id ?: 0L else 0L }
    val agentLabel = agents.firstOrNull { it.id == agentId }?.name ?: com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်ရွေးရန်")
    val customerLabel = when { agentId == 0L -> com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်ရွေးပြီးမှ ရွေးပါ"); customerId == -1L -> com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အားလုံး"); else -> customers.firstOrNull { it.id == customerId }?.name ?: com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer ရွေးရန်") }
    val featureTitle = customerActions.firstOrNull { it.key == feature }?.title ?: "Customer feature"
    AppScaffold(featureTitle, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent ရွေးရန် နှင့် Customer ရွေးရန်"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ScopeDropdown(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent ရွေးရန်"), agentLabel, agents.map { it.id to it.name }, agentId) { agentId = it }
            ScopeDropdown(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer ရွေးရန်"), customerLabel, if (agentId == 0L) emptyList() else listOf(-1L to com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အားလုံး")) + customers.map { it.id to it.name }, customerId, enabled = agentId != 0L) { customerId = it }
            if (agentId == 0L || customerId == 0L) UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent တစ်ယောက်နှင့် Customer အားလုံး သို့မဟုတ် Customer တစ်ယောက်ကို ရွေးပါ"))
            else {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Text("$agentLabel • $customerLabel", Modifier.fillMaxWidth().padding(14.dp), fontWeight = FontWeight.Bold) }
                Button(onClick = { onOpen(feature, agentId, customerId) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဆက်သွားရန်")) }
            }
        }
    }
}

@Composable
fun AgentFeatureWorkspaceScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit) {
    if (feature == "total") {
        AgentDigitTotalsWorkspace(vm, onBack)
        return
    }
    if (feature == "report") {
        AgentStatementWorkspace(vm, onBack)
        return
    }
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
    val aggregate by produceState<ScopeSummary?>(null, date, session, feature, selected, revision) { value = if (selected == -1L) vm.aggregateAgentSummary(date, session, feature == "winning" || feature == "report") else null }
    val winnerAvailable = winners.any { it.date == date && it.session == session }
    val visible = if (selected == -1L) summaries else summaries.filter { it.id == selected }
    AppScaffold(agentActions.firstOrNull { it.key == feature }?.title ?: "Agent feature", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SelectionCard {
                ScopeDropdown(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent ရွေးရန်"), if (selected == -1L) com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး") else agents.firstOrNull { it.id == selected }?.name ?: com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်ရွေးရန်"), listOf(-1L to com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး")) + agents.map { it.id to it.name }, selected) { selected = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ", Modifier.weight(1f))
                    DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(draw.label)) }) }
                }
            } }
            if (selected == 0L) item { UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent တစ်ယောက် သို့မဟုတ် ဒိုင်အားလုံးကို ရွေးပါ")) }
            else if (selected == -1L && feature in setOf("closed", "limit")) item { UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပိတ်ဂဏန်းနှင့် ကန့်သတ်ပမာဏအတွက် Agent တစ်ယောက်ကို ရွေးပါ")) }
            else when (feature) {
                "closed" -> item { AgentClosedWorkspace(vm, selected, date, session, onBack) }
                "limit" -> item { AgentLimitWorkspace(vm, selected, date, session, onBack) }
                "report" -> item { if (selected == -1L) aggregate?.let { ScopeSummaryCard(it) } ?: EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော အချိန်အတွက် စုစုပေါင်းစာရင်းမရှိသေးပါ") else AgentReportWorkspace(vm, selected, date, session) }
                "winning" -> item { if (!winnerAvailable) UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ထီပေါက်ဂဏန်း မရှိသေးပါ")) else if (selected == -1L) aggregate?.let { ScopeSummaryCard(it) } ?: EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော အချိန်အတွက် စုစုပေါင်းစာရင်းမရှိသေးပါ") else AgentReportWorkspace(vm, selected, date, session, winningOnly = true) }
                else -> {
                    if (feature == "total" && selected > 0L) item { AgentTotalTableWorkspace(vm, selected, date, session) }
                    else if (selected == -1L) item { aggregate?.let { ScopeSummaryCard(it) } ?: EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော အခြေအနေအတွက် စုစုပေါင်းစာရင်းမရှိသေးပါ") }
                    else { if (visible.isEmpty()) item { EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော အခြေအနေအတွက် အချက်အလက်မရှိသေးပါ") }; items(visible, key = { it.id }) { summary -> ScopeSummaryCard(summary) } }
                }
            }
        }
    }
}

@Composable
fun CustomerFeatureWorkspaceScreen(
    vm: LedgerViewModel,
    feature: String,
    onBack: () -> Unit,
    onEditEntry: (Long) -> Unit,
    onVoucherPreview: (Long, LocalDate, DrawSession, List<Long>) -> Unit,
) {
    // Customer-facing statements have their own date-range and all-customer navigation.
    // Keep the established selection and other workspaces unchanged.
    if (feature == "report" || feature == "analysis") {
        CustomerReviewWorkspace(vm, feature, onBack)
        return
    }
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var agentId by rememberSaveable { mutableStateOf(language.defaultAgentId) }
    var customerId by rememberSaveable { mutableStateOf(language.defaultCustomerId) }
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    LaunchedEffect(language.selectedDate, language.selectedSession) { dateText = language.selectedDate; session = language.selectedSession }
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(agents, language.defaultAgentId) { if (agents.none { it.id == agentId }) agentId = agents.firstOrNull { it.id == language.defaultAgentId }?.id ?: 0L }
    LaunchedEffect(customers, agentId, language.defaultCustomerId) { if (customerId != -1L && customers.none { it.id == customerId }) customerId = if (agentId == language.defaultAgentId) customers.firstOrNull { it.id == language.defaultCustomerId }?.id ?: 0L else 0L }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val summaries by produceState<List<ScopeSummary>>(emptyList(), date, session, feature, agentId, revision) { value = if (agentId > 0L) vm.allCustomerSummaries(agentId, date, session, feature == "winning" || feature == "report") else emptyList() }
    val winners by vm.winners.collectAsStateWithLifecycle()
    val aggregate by produceState<ScopeSummary?>(null, date, session, feature, agentId, customerId, revision) { value = if (agentId > 0L && customerId == -1L) vm.aggregateCustomerSummary(agentId, date, session, feature == "winning" || feature == "report") else null }
    val winnerAvailable = winners.any { it.date == date && it.session == session }
    val visible = if (customerId == -1L) summaries else summaries.filter { it.id == customerId }
    AppScaffold(customerActions.firstOrNull { it.key == feature }?.title ?: "Customer feature", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SelectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScopeDropdown(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent ရွေးရန်"), agents.firstOrNull { it.id == agentId }?.name ?: com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်ရွေးရန်"), agents.map { it.id to it.name }, agentId, Modifier.weight(1f)) { agentId = it; customerId = if (it == language.defaultAgentId) language.defaultCustomerId else 0L }
                    ScopeDropdown(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer ရွေးရန်"), when { agentId == 0L -> com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်ရွေးပြီးမှ ရွေးပါ"); customerId == -1L -> com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အားလုံး"); else -> customers.firstOrNull { it.id == customerId }?.name ?: com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer ရွေးရန်") }, if (agentId == 0L) emptyList() else listOf(-1L to com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အားလုံး")) + customers.map { it.id to it.name }, customerId, Modifier.weight(1f), enabled = agentId > 0L) { customerId = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ", Modifier.weight(1f))
                    DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(draw.label)) }) }
                }
            } }
            if (agentId == 0L || customerId == 0L) item { UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent နှင့် Customer ကို ရွေးပါ")) }
            else when (feature) {
                "history" -> item { key(agentId, customerId, date, session) { CustomerBetEntriesWorkspace(vm, agentId, customerId, date, session, onEditEntry) { selectedIds ->
                    onVoucherPreview(customerId, date, session, selectedIds)
                } } }
                "analysis" -> item {
                    // Reset remembered content and any running analysis when scope changes.
                    key(agentId, customerId, date, session) {
                        CustomerAnalysisWorkspace(vm, agentId, customerId, date, session)
                    }
                }
                "digits" -> item { CustomerDigitsWorkspace(vm, agentId, customerId, date, session) }
                "commission" -> item { CustomerCommissionWorkspace(vm, agentId, customerId, date, session) }
                "report" -> item { CustomerReportWorkspace(vm, agentId, customerId, date, session) }
                "winning" -> item { if (!winnerAvailable) UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ထီပေါက်ဂဏန်း မရှိသေးပါ")) else CustomerReportWorkspace(vm, agentId, customerId, date, session, winningOnly = true) }
                else -> if (customerId == -1L) item { aggregate?.let { ScopeSummaryCard(it) } ?: EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော Customer အားလုံးအတွက် စာရင်းမရှိသေးပါ") } else { if (visible.isEmpty()) item { EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော Customer အတွက် အချက်အလက်မရှိသေးပါ") }; items(visible, key = { it.id }) { summary -> ScopeSummaryCard(summary) } }
            }
        }
    }
}

@Composable private fun SelectionCard(content: @Composable ColumnScope.() -> Unit) { Surface(Modifier.fillMaxWidth(), color=AppColors.Champagne, shape=MaterialTheme.shapes.medium, border=BorderStroke(1.dp, AppColors.Gold.copy(alpha=.35f))) { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content=content) } }

@Composable
fun ScopeDropdown(label: String, selected: String, options: List<Pair<Long, String>>, value: Long, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = modifier) {
        if (compact) {
            // Settings-only: one line + explicit height so both languages have
            // identical field geometry. Menu items still show the full names.
            val language = LocalLanguage.current
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().height(68.dp).menuAnchor(),
                enabled = enabled,
                readOnly = true,
                singleLine = true,
                textStyle = LedgerTypographyStandard.style(LedgerTextRole.DROPDOWN_VALUE, language.code).copy(
                    fontSize = 14.sp, lineHeight = 20.sp
                ),
                label = {
                    Text(
                        language.translate(label),
                        style = LedgerTypographyStandard.style(LedgerTextRole.DROPDOWN_LABEL, language.code).copy(
                            fontSize = 12.sp, lineHeight = 18.sp
                        )
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) }
            )
        } else {
        OutlinedTextField(selected, {}, Modifier.fillMaxWidth().menuAnchor(), enabled = enabled, readOnly = true, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(label)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) })
        }
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) { options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(id); expanded = false }) } }
    }
}

@Composable private fun ScopeSummaryCard(summary: ScopeSummary) { ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(summary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ထိုးကြေး ${summary.totalBet.mmk()} • ပေါက်ကြေး ${summary.winningStake.mmk()}", "Stake: ${summary.totalBet.mmk()} • Winning stake: ${summary.winningStake.mmk()}")); Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("လျော် ${summary.payout.mmk()} • ကော်မရှင် ${summary.commission.mmk()} • ရှုံး/မြတ် ${summary.profitLoss.mmk()}", "Payout: ${summary.payout.mmk()} • Commission: ${summary.commission.mmk()} • P/L: ${summary.profitLoss.mmk()}")) } } }
@Composable private fun SummaryRows(rows: List<ScopeSummary>) { if (rows.isEmpty()) EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော ရက်နှင့်အချိန်အတွက် စာရင်းမရှိသေးပါ") else rows.forEach { ScopeSummaryCard(it) } }

@Composable private fun AgentReportWorkspace(vm: LedgerViewModel, agentId: Long, date: LocalDate, session: DrawSession, winningOnly: Boolean = false) { var after by rememberSaveable { mutableStateOf(winningOnly) }; val revision by vm.revision.collectAsStateWithLifecycle(); val report by produceState<DrawReport?>(null, agentId, date, session, after, revision) { value = vm.agentReport(agentId, date, session, after) }; val rows by produceState<List<AgentCustomerReportRow>>(emptyList(), agentId, date, session, after, revision) { value = vm.agentCustomerReport(agentId, date, session, after) }; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { if (!winningOnly) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!after, { after = false }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Before")) }); FilterChip(after, { after = true }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("After")) }) }; if (after && report?.winnerAvailable != true) UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ထီပေါက်ပြီးချိန်အတွက် ရလဒ်မရှိသေးပါ")) else { report?.let { ReportCard(it.calculation, it.winningDigit) }; Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer တစ်ယောက်ချင်း breakdown"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); rows.forEach { row -> ElevatedCard { ListItem(headlineContent = { Text(row.customer.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ထိုးကြေး ${row.calculation.totalBet.mmk()} • ပေါက်ကြေး ${row.calculation.winningStake.mmk()} • လျော် ${row.calculation.payout.mmk()}", "Stake: ${row.calculation.totalBet.mmk()} • Winning: ${row.calculation.winningStake.mmk()} • Payout: ${row.calculation.payout.mmk()}")) }, trailingContent = { Text(row.calculation.profitLoss.mmk(), fontWeight = FontWeight.Bold) }) } } } } }

@Composable
private fun CustomerReportWorkspace(vm: LedgerViewModel, agentId: Long, customerId: Long, date: LocalDate, session: DrawSession, winningOnly: Boolean = false) {
    var after by rememberSaveable { mutableStateOf(winningOnly) }
    var weekly by rememberSaveable { mutableStateOf(false) }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val report by produceState<DrawReport?>(null, customerId, date, session, after, revision) {
        value = if (customerId == -1L) vm.aggregateCustomerReport(agentId, date, session, after) else vm.customerReport(customerId, date, session, after)
    }
    val week by produceState<WeeklyReport?>(null, customerId, date, after, weekly, revision) {
        value = if (weekly) { if (customerId == -1L) vm.aggregateWeeklyCustomerReport(agentId, date, after) else vm.weeklyCustomerReport(customerId, date, after) } else null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!weekly, { weekly = false }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("နေ့စဉ်")) })
            FilterChip(weekly, { weekly = true }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အပတ်စဉ်")) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!after, { after = false }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Before")) })
            FilterChip(after, { after = true }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("After")) })
        }
        if (weekly) {
            week?.let { WeeklyReportTable(it) }
        } else if (after && report?.winnerAvailable != true) {
            UnavailableState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ထီပေါက်ပြီးချိန်အတွက် ရလဒ်မရှိသေးပါ"))
        } else {
            report?.let { ReportCard(it.calculation, it.winningDigit) }
        }
    }
}

@Composable
private fun WeeklyReportTable(report: WeeklyReport) {
    val scroll = rememberScrollState()
    val rows = report.rows
    val totalSlots = rows.sumOf { it.calculation?.distinctSlots ?: 0 }
    val totalBet = rows.sumOf { it.calculation?.totalBet ?: 0L }
    val totalWinning = rows.sumOf { it.calculation?.winningStake ?: 0L }
    val totalPayout = rows.sumOf { it.calculation?.payout ?: 0L }
    val totalCommission = rows.sumOf { it.calculation?.commission ?: 0L }
    val totalProfitLoss = rows.sumOf { it.calculation?.profitLoss ?: 0L }

    val widths = listOf(48.dp, 120.dp, 72.dp, 86.dp, 78.dp, 126.dp, 110.dp, 126.dp, 112.dp, 120.dp)
    @Composable fun cell(text: String, width: androidx.compose.ui.unit.Dp, align: TextAlign = TextAlign.Left, bold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
        Text(text, Modifier.width(width).padding(horizontal = 8.dp), textAlign = align, maxLines = 2, style = MaterialTheme.typography.labelSmall, fontWeight = if (bold) FontWeight.Black else FontWeight.Normal, color = color)
    }
    val header = if (com.myanmar.ledger2d.core.design.LocalLanguage.current.code == "en")
        listOf("No.", "Date", "Session", "Winner", "Digits", "Stake", "Winning stake", "Payout", "Commission", "Profit / Loss")
    else listOf("စဉ်", "ရက်စွဲ", "အချိန်", "ထွက်ဂဏန်း", "အကွက်", "ထိုးကြေး", "ပေါက်ကြေး", "လျော်ပေးငွေ", "ကော်မရှင်", "အရှုံး/အမြတ်")
    Column(Modifier.fillMaxWidth().horizontalScroll(scroll).background(Color.White, MaterialTheme.shapes.medium).padding(vertical = 8.dp)) {
        Row(Modifier.width(998.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            header.forEachIndexed { index, value -> cell(value, widths[index], if (index >= 4) TextAlign.End else TextAlign.Left, bold = true, color = AppColors.PrimaryDeep) }
        }
        HorizontalDivider(color = AppColors.Stone)
        rows.forEachIndexed { index, row ->
            val calc = row.calculation
            val closedOrEmpty = calc == null
            Row(Modifier.width(998.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                cell("${index + 1}", widths[0])
                cell(row.date.displayDate(), widths[1])
                cell(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(row.session.label), widths[2])
                cell(row.winningDigit ?: "—", widths[3], TextAlign.Center, bold = row.winningDigit != null, color = if (row.winningDigit != null) AppColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                cell(if (closedOrEmpty) "0" else "${calc!!.distinctSlots}", widths[4], TextAlign.End)
                cell((calc?.totalBet ?: 0L).mmk(), widths[5], TextAlign.End)
                cell((calc?.winningStake ?: 0L).mmk(), widths[6], TextAlign.End)
                cell((calc?.payout ?: 0L).mmk(), widths[7], TextAlign.End)
                cell((calc?.commission ?: 0L).mmk(), widths[8], TextAlign.End)
                cell((calc?.profitLoss ?: 0L).mmk(), widths[9], TextAlign.End, bold = true, color = if ((calc?.profitLoss ?: 0L) < 0) MaterialTheme.colorScheme.error else AppColors.Success)
            }
            HorizontalDivider(color = AppColors.Stone.copy(alpha = .65f))
        }
        Row(Modifier.width(998.dp).background(AppColors.Blush).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            cell("", widths[0])
            cell(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စုစုပေါင်း"), widths[1], bold = true)
            cell("", widths[2])
            cell("", widths[3])
            cell("$totalSlots", widths[4], TextAlign.End, bold = true)
            cell(totalBet.mmk(), widths[5], TextAlign.End, bold = true)
            cell(totalWinning.mmk(), widths[6], TextAlign.End, bold = true)
            cell(totalPayout.mmk(), widths[7], TextAlign.End, bold = true)
            cell(totalCommission.mmk(), widths[8], TextAlign.End, bold = true)
            cell(totalProfitLoss.mmk(), widths[9], TextAlign.End, bold = true, color = if (totalProfitLoss < 0) MaterialTheme.colorScheme.error else AppColors.Success)
        }
    }
}

@Composable
private fun AgentClosedWorkspace(
    vm: LedgerViewModel, agentId: Long,
    date: LocalDate, session: DrawSession, onBack: () -> Unit,
) = AgentClosedPatternWorkspace(vm, agentId, date, session)

@Composable
private fun AgentLimitWorkspace(
    vm: LedgerViewModel, agentId: Long,
    date: LocalDate, session: DrawSession, onBack: () -> Unit,
) = AgentLimitPatternWorkspace(vm, agentId, date, session)

@Composable private fun CustomerAnalysisWorkspace(vm: LedgerViewModel, agentId: Long, customerId: Long, date: LocalDate, session: DrawSession) { CustomerRiskAnalysisWorkspace(vm, agentId, customerId, date, session) }

@Composable
private fun CustomerDigitsWorkspace(
    vm: LedgerViewModel,
    agentId: Long,
    customerId: Long,
    date: LocalDate,
    session: DrawSession,
) {
    // Reset remembered filters and subscriptions when the drawing scope changes.
    // This prevents temporarily mixing the previous customer's totals and markers.
    key(agentId, customerId, date, session) {
        CustomerDigitViewsWorkspace(vm, agentId, customerId, date, session)
    }
}

@Composable private fun CustomerCommissionWorkspace(vm: LedgerViewModel, agentId: Long, customerId: Long, date: LocalDate, session: DrawSession) {
    if(customerId == -1L) {
        CustomerBulkCommissionWorkspace(vm, agentId, date, session)
        return
    }
    val customer by vm.customer(customerId).collectAsStateWithLifecycle(initialValue = null)
    val revision by vm.revision.collectAsStateWithLifecycle()
    val drawRate by produceState<Int?>(null,customerId,date,session,revision) {
        value=if(customerId>0L)vm.commissionForDraw(customerId,date,session)else null
    }
    // The normal rate is scheduled prospectively; a draw correction only changes
    // the selected customer's selected morning/evening session.
    var mode by rememberSaveable(customerId) { mutableStateOf("FORWARD") }
    val next=remember { com.myanmar.ledger2d.core.domain.DrawRatePolicy.currentSession(java.time.LocalDateTime.now()) }
    var effectiveDate by rememberSaveable(customerId) { mutableStateOf(next.date.toString()) }
    var effectiveSession by rememberSaveable(customerId) { mutableStateOf(next.session) }
    var value by rememberSaveable(customerId) { mutableStateOf("") }
    var saved by rememberSaveable(customerId) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var pendingOverride by remember { mutableStateOf(false) }
    var pendingForward by remember { mutableStateOf(false) }
    LaunchedEffect(customer?.commissionRateBasisPoints,drawRate,mode,date,session) {
        value=java.math.BigDecimal(if(mode=="DRAW")drawRate?:customer?.commissionRateBasisPoints?:0
            else customer?.commissionRateBasisPoints?:0).movePointLeft(2).stripTrailingZeros().toPlainString()
    }
    val parsed=value.toBigDecimalOrNull()
    val validRate=parsed!=null && parsed>=java.math.BigDecimal.ZERO && parsed<=java.math.BigDecimal(100) &&
        parsed.movePointRight(2).stripTrailingZeros().scale()<=0
    val startDate=runCatching { LocalDate.parse(effectiveDate) }.getOrNull()
    val earliest=com.myanmar.ledger2d.core.domain.DrawRatePolicy.currentSession(java.time.LocalDateTime.now())
    val validStart=startDate!=null && com.myanmar.ledger2d.core.domain.DrawSchedule.isWeekday(startDate) &&
        com.myanmar.ledger2d.core.domain.DrawRatePolicy.key(startDate,effectiveSession)>=
        com.myanmar.ledger2d.core.domain.DrawRatePolicy.key(earliest.date,earliest.session)
    if(pendingOverride)AlertDialog(onDismissRequest={pendingOverride=false},
        title={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီ Session ၏ ကော်မရှင်ကို ပြန်တွက်မည်လား"))},
        text={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("${date.displayDate()} • ${session.label} အတွက် ဒီ Customer ၏ ထိုးကြေးအားလုံးကို ${value}% နဲ့ ပြန်တွက်မည်။ တခြား Session နဲ့ Customer တွေ မပြောင်းပါ။", "${date.displayDate()} • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)}: Recalculate this customer's entries at ${value}%. Other sessions and customers stay unchanged."))},
        confirmButton={TextButton({
            pendingOverride=false
            vm.overrideSessionCommission(customerId,date,session,value,{saved=true;error=""},{error=it})
        }){Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပြန်တွက်မည်"))}},
        dismissButton={TextButton({pendingOverride=false}){Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("မလုပ်တော့ပါ"))}})
    if(pendingForward)AlertDialog(onDismissRequest={pendingForward=false},
        title={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပုံမှန်ကော်မရှင် ပြောင်းမည်လား"))},
        text={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("$effectiveDate • ${effectiveSession.label} ကစပြီး နောက်ထပ်သတ်မှတ်ထားသောနှုန်းမရောက်ခင်အထိ $value% သုံးမည်။ အဲဒီကာလအတွက် ကြိုသွင်းထားပြီးသား Entry တွေကိုပါ ပြန်တွက်မည်။ အတိတ် Session မထိပါ။", "From $effectiveDate • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(effectiveSession.label)}, apply $value% until the next change. Recalculate entries from then on; past sessions are unaffected."))},
        confirmButton={TextButton({
            pendingForward=false
            if(startDate!=null)vm.scheduleCommission(customerId,value,startDate,effectiveSession,
                {saved=true;error=""},{error=it})
        }){Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပြောင်းမည်"))}},
        dismissButton={TextButton({pendingForward=false}){Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("မလုပ်တော့ပါ"))}})
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(if(customerId==-1L) com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အားလုံး၏ ကော်မရှင်") else com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီ Customer ၏ ကော်မရှင်နှုန်းထား"),
            style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ရွေးထားသော Session • ${date.displayDate()} • ${session.label}", "Selected session • ${date.displayDate()} • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)}"),
            style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        if(customerId==-1L) {
            val total by produceState(0L,agentId,date,session,revision) {
                this.value=vm.agentReport(agentId,date,session,true).calculation.commission
            }
            AnalysisMetric("စုစုပေါင်းကော်မရှင်",total.mmk())
        } else {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(mode=="FORWARD",{mode="FORWARD";saved=false;error=""},label={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပုံမှန်နှုန်း ပြောင်းရန်"))})
                FilterChip(mode=="DRAW",{mode="DRAW";saved=false;error=""},label={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီ Session သီးသန့်"))})
            }
            if(mode=="DRAW") {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ဒီ Session လက်ရှိနှုန်း: ${java.math.BigDecimal(drawRate?:0).movePointLeft(2).stripTrailingZeros().toPlainString()}%", "Current session rate: ${java.math.BigDecimal(drawRate?:0).movePointLeft(2).stripTrailingZeros().toPlainString()}%"),
                    style=MaterialTheme.typography.bodyMedium)
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီ Customer ၏ ရွေးထားသော Session တစ်ခုတည်းကို Recalculate လုပ်မယ်။ အတိတ်/အနာဂတ် အခြား Session မပြောင်းပါ။"),
                    style=MaterialTheme.typography.bodySmall)
            } else {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("နှုန်းအသစ် စတင်မည့် Session ကို ရွေးပါ။ အဲဒီ Session ကစပြီး ကြိုသွင်းထားပြီးသားစာရင်းတွေအပါအဝင် ပြန်တွက်မည်။ အတိတ် Session မပြောင်းပါ။"),
                    style=MaterialTheme.typography.bodySmall)
                DateInput(effectiveDate,{effectiveDate=it},"နှုန်းအသစ် စတင်မည့်ရက်")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    DrawSession.entries.forEach { draw->
                        FilterChip(effectiveSession==draw,{effectiveSession=draw},label={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(draw.label))})
                    }
                }
            }
            OutlinedTextField(value,{value=it.filter{ch->ch.isDigit()||ch=='.'}.take(6);saved=false;error=""},
                Modifier.fillMaxWidth(),label={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ကော်မရှင် %"))})
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            Button(onClick={
                if(mode=="DRAW")pendingOverride=true
                else if(startDate!=null)pendingForward=true
            },enabled=validRate&&(mode=="DRAW"||validStart)) {
                Text(if(mode=="DRAW") com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီ Session ကို ပြန်တွက်မည်") else com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("နှုန်းအသစ် သိမ်းမည်"))
            }
            if(saved)Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("သိမ်းပြီးပါပြီ"),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable
fun AddCustomerFromHomeScreen(vm: LedgerViewModel, onBack: () -> Unit, onCreate: (Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var selected by rememberSaveable { mutableStateOf(language.defaultAgentId) }
    LaunchedEffect(agents, language.defaultAgentId) { if (agents.none { it.id == selected }) selected = agents.firstOrNull { it.id == language.defaultAgentId }?.id ?: 0L }
    AppScaffold(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ထိုးသားအသစ်ထည့်ရန်"), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer ကို ဘယ် Agent အောက်မှာထည့်မလဲ"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ScopeDropdown(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Agent ရွေးရန်"), agents.firstOrNull { it.id == selected }?.name ?: com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်ရွေးရန်"), agents.map { it.id to it.name }, selected) { selected = it }
            Button(onClick = { onCreate(selected) }, enabled = selected > 0, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer form သို့သွားရန်")) }
        }
    }
}

@Composable
private fun AgentTotalTableWorkspace(vm: LedgerViewModel, agentId: Long, date: LocalDate, session: DrawSession) {
    val totals by vm.agentTotalsWithCommission(agentId, date, session).collectAsStateWithLifecycle(initialValue = emptyList())
    val totalBet = totals.sumOf { it.amount }
    val totalCommission = totals.sumOf { it.commission }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${date.displayDate()} • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(Modifier.fillMaxWidth(), color = AppColors.Wine, shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စုစုပေါင်းအနှစ်ချုပ်"), color = AppColors.GoldSoft, fontWeight = FontWeight.Bold)
                SummaryAmount("စုစုပေါင်းထိုးကြေး", totalBet, Color.White)
                SummaryAmount("ကော်မရှင်", totalCommission, Color.White)
                HorizontalDivider(color = AppColors.GoldSoft.copy(alpha = .45f))
                SummaryAmount("ကော်မရှင်ပြီးနောက်ကျန်ငွေ", totalBet - totalCommission, AppColors.GoldSoft)
            }
        }
        Row(Modifier.fillMaxWidth().background(AppColors.Wine, MaterialTheme.shapes.small).padding(10.dp)) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဂဏန်း"), Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ထိုးကြေး"), Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ကော်မရှင်"), Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ကော်မရှင်ပြီးနောက်"), Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (totals.isEmpty()) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီအချိန်အတွက် အတည်ပြုထားသော စာရင်းမရှိသေးပါ"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (feature == "total") {
        AgentDigitTotalsWorkspace(vm, onBack, initialAgentId = -1L)
        return
    }
    if (feature == "report") {
        AgentStatementWorkspace(vm, onBack, initialAgentId = -1L)
        return
    }
    val language = LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    LaunchedEffect(language.selectedDate, language.selectedSession) { dateText = language.selectedDate; session = language.selectedSession }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val aggregate by produceState<ScopeSummary?>(null, date, session, feature, revision) { value = vm.aggregateAgentSummary(date, session, feature == "winning" || feature == "report") }
    AppScaffold(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အားလုံး"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ဒိုင်အားလုံး • ","All agents • ") + com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(agentActions.firstOrNull { it.key == feature }?.title ?: feature), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ"); Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(draw.label)) }, modifier = Modifier.padding(end = 8.dp)) } } }
            item { aggregate?.let { ScopeSummaryCard(it) } ?: EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော ရက်နှင့်အချိန်အတွက် ဒိုင်အားလုံးစာရင်းမရှိသေးပါ") }
        }
    }
}

@Composable
fun AllCustomerScopeScreen(vm: LedgerViewModel, agentId: Long, feature: String, onBack: () -> Unit) {
    if (feature == "report" || feature == "analysis") {
        CustomerReviewWorkspace(vm, feature, onBack, initialAgentId = agentId)
        return
    }
    val language = LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    LaunchedEffect(language.selectedDate, language.selectedSession) { dateText = language.selectedDate; session = language.selectedSession }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    val aggregate by produceState<ScopeSummary?>(null, date, session, feature, agentId, revision) { value = vm.aggregateCustomerSummary(agentId, date, session, feature == "winning" || feature == "report") }
    AppScaffold(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အားလုံး"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ရွေးထားသော Agent အောက်က Customer အားလုံး • ","All customers under selected agent • ") + com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(customerActions.firstOrNull { it.key == feature }?.title ?: feature), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); DateInput(dateText, { dateText = it; language.setDate(it) }, "ရက်စွဲ"); Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw; language.setSession(draw) }, label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(draw.label)) }, modifier = Modifier.padding(end = 8.dp)) } } }
            if (customers.isEmpty()) item { EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer မရှိသေးပါ"), "ဒီ Agent အောက်မှာ Customer ထည့်ပါ") }
            else item { aggregate?.let { ScopeSummaryCard(it) } ?: EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော ရက်နှင့်အချိန်အတွက် Customer အားလုံးစာရင်းမရှိသေးပါ") }
        }
    }
}
