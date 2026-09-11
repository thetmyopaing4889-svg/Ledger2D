@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.database.AgentEntity
import com.myanmar.ledger2d.core.database.CustomerEntity
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

private data class DashboardAction(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val key: String)

private val agentActions = listOf(
    DashboardAction("စုစုပေါင်းစာရင်း", "Agent အလိုက် စာရင်းစုစုပေါင်း", Icons.Default.ReceiptLong, "total"),
    DashboardAction("အစီရင်ခံစာ", "Agent အလိုက် report", Icons.Default.Assessment, "report"),
    DashboardAction("ပိတ်ဂဏန်း", "Agent အလိုက် လက်မခံမည့်ဂဏန်း", Icons.Default.Lock, "closed"),
    DashboardAction("ထီပေါက်စဉ်", "Global result မှတွက်ထားသော Agent result", Icons.Default.EmojiEvents, "winning"),
    DashboardAction("ကန့်သတ်ပမာဏ", "Agent-wide limits", Icons.Default.Tune, "limit")
)

private val customerActions = listOf(
    DashboardAction("စာရင်းမှတ်တမ်း", "Customer ၏ စာရင်းများ", Icons.Default.ReceiptLong, "history"),
    DashboardAction("အစီရင်ခံစာ", "Customer အလိုက် report", Icons.Default.Assessment, "report"),
    DashboardAction("အမြန်သုံးသပ်ချက်", "လက်ရှိစာရင်းအခြေအနေ", Icons.Default.Insights, "analysis"),
    DashboardAction("အကွက်စာရင်း", "00–99 အကွက်များ", Icons.Default.GridView, "digits"),
    DashboardAction("ကော်မရှင်", "Customer commission", Icons.Default.Percent, "commission"),
    DashboardAction("ထီပေါက်စဉ်", "Global result မှတွက်ထားသော Customer result", Icons.Default.EmojiEvents, "winning")
)

@Composable
fun AgentDashboardScreen(vm: LedgerViewModel, onBack: () -> Unit, onAgentInfo: (Long) -> Unit, onAddAgent: () -> Unit, onFeature: (String) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    AppScaffold("Agent Dashboard", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Agent List", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Add Agent နဲ့ ဖန်တီးထားသော Agent များကို ကြည့်ရန်", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (agents.isEmpty()) item { EmptyState("ဒိုင်မရှိသေးပါ", "Home မှ ဒိုင်အသစ်ထည့်ရန်ကို အသုံးပြုပါ") }
            else items(agents, key = { it.id }) { agent ->
                ElevatedCard(onClick = { onAgentInfo(agent.id) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(agent.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("${agent.rate} Rate" + if (agent.phone.isNotBlank()) " • ${agent.phone}" else "") },
                        leadingContent = { Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, "ကြည့်ရန်") }
                    )
                }
            }
            item { Text("Agent feature များ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(agentActions) { action ->
                ElevatedCard(onClick = { onFeature(action.key) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(action.title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(action.subtitle) }, leadingContent = { Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) })
                }
            }
        }
    }
}

@Composable
fun AgentScopeScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit, onOpen: (String, Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(0L) }
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
    val agents by vm.agents.collectAsStateWithLifecycle()
    var selectedAgent by rememberSaveable { mutableStateOf(0L) }
    val customers by vm.customers(selectedAgent).collectAsStateWithLifecycle(initialValue = emptyList())
    val agentName = agents.firstOrNull { it.id == selectedAgent }?.name ?: "ဒိုင်ရွေးရန်"
    AppScaffold("Customer Dashboard", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Customer List", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Agent ရွေးပြီး ထို Agent အောက်က Customer များကို ကြည့်ပါ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { ScopeDropdown("Agent ရွေးရန်", agentName, agents.map { it.id to "${it.name} • ${it.rate} Rate" }, selectedAgent) { selectedAgent = it } }
            if (selectedAgent == 0L) item { UnavailableState("Customer List ကြည့်ရန် Agent တစ်ယောက်ကို ရွေးပါ") }
            else if (customers.isEmpty()) item { EmptyState("Customer မရှိသေးပါ", "ဒီ Agent အောက်မှာ Customer ထည့်ပါ") }
            else items(customers, key = { it.id }) { customer ->
                ElevatedCard(onClick = { onCustomerInfo(customer.id) }, modifier = Modifier.fillMaxWidth()) { ListItem(headlineContent = { Text(customer.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("$agentName • ${customer.phone}") }, leadingContent = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }) }
            }
            item { Text("Customer feature များ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(customerActions) { action ->
                ElevatedCard(onClick = { onFeature(action.key) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(action.title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(action.subtitle) }, leadingContent = { Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) })
                }
            }
        }
    }
}

@Composable
fun CustomerScopeScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit, onOpen: (String, Long, Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    var agentId by rememberSaveable { mutableStateOf(0L) }
    var customerId by rememberSaveable { mutableStateOf(0L) }
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
private fun ScopeDropdown(label: String, selected: String, options: List<Pair<Long, String>>, value: Long, enabled: Boolean = true, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(selected, {}, Modifier.fillMaxWidth().menuAnchor(), enabled = enabled, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) })
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) { options.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(id); expanded = false }) } }
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
fun AllAgentFeatureScreen(vm: LedgerViewModel, feature: String, onBack: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val summaries by produceState<List<ScopeSummary>>(emptyList(), date, session, feature, revision) { value = vm.allAgentSummaries(date, session, feature == "winning" || feature == "report") }
    AppScaffold("ဒိုင်အားလုံး", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("ဒိုင်အားလုံး • ${agentActions.firstOrNull { it.key == feature }?.title ?: feature}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); DateInput(dateText, { dateText = it }, "ရက်စွဲ"); Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } } }
            if (summaries.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "ရွေးထားသော ရက်နှင့်အချိန်အတွက် ဒိုင်စာရင်းမရှိသေးပါ") }
            items(summaries, key = { it.id }) { summary ->
                ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(summary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("ထိုးကြေး ${summary.totalBet.mmk()} • ပေါက်ကြေး ${summary.winningStake.mmk()}"); Text("လျော် ${summary.payout.mmk()} • ကော်မရှင် ${summary.commission.mmk()} • ရှုံး/မြတ် ${summary.profitLoss.mmk()}") } }
            }
        }
    }
}

@Composable
fun AllCustomerScopeScreen(vm: LedgerViewModel, agentId: Long, feature: String, onBack: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    val summaries by produceState<List<ScopeSummary>>(emptyList(), date, session, feature, agentId, revision) { value = vm.allCustomerSummaries(agentId, date, session, feature == "winning" || feature == "report") }
    AppScaffold("Customer အားလုံး", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("ရွေးထားသော Agent အောက်က Customer အားလုံး • $feature", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); DateInput(dateText, { dateText = it }, "ရက်စွဲ"); Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } } }
            if (customers.isEmpty()) item { EmptyState("Customer မရှိသေးပါ", "ဒီ Agent အောက်မှာ Customer ထည့်ပါ") }
            else if (summaries.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "ရွေးထားသော ရက်နှင့်အချိန်အတွက် Customer စာရင်းမရှိသေးပါ") }
            items(summaries, key = { it.id }) { summary -> ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(summary.name, fontWeight = FontWeight.Bold); Text("ထိုးကြေး ${summary.totalBet.mmk()} • ပေါက်ကြေး ${summary.winningStake.mmk()}"); Text("လျော် ${summary.payout.mmk()} • ကော်မရှင် ${summary.commission.mmk()} • ရှုံး/မြတ် ${summary.profitLoss.mmk()}") } } }
        }
    }
}
