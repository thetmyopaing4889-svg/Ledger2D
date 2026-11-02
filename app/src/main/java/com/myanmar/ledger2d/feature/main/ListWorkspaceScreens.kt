package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.database.AgentEntity
import com.myanmar.ledger2d.core.database.CustomerEntity
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.model.DrawIdentity
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Search the existing agent records only; never merges the same customer name
 * across independent agents and never changes stored payout rates.
 */
internal fun filterAgentsForList(agents: List<AgentEntity>, rawQuery: String): List<AgentEntity> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return agents
    return agents.filter { agent ->
        agent.name.contains(query, ignoreCase = true) ||
            agent.phone.contains(query, ignoreCase = true) ||
            agent.address.contains(query, ignoreCase = true)
    }
}

@Composable
fun AgentListWorkspaceScreen(
    vm: LedgerViewModel, onBack: () -> Unit, onInfo: (Long) -> Unit, onAdd: () -> Unit,
) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val l = LocalLanguage.current
    var search by rememberSaveable { mutableStateOf("") }
    val visible = remember(agents, search) { filterAgentsForList(agents, search) }

    AppScaffold(l.translate("Agent List"), onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("ဒိုင်စာရင်း • ${agents.size} ယောက်",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (agents.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အမည် / ဖုန်း / လိပ်စာ ရှာရန်")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "ရှာဖွေမှုရှင်းရန်")
                            }
                        },
                    )
                }
                if (search.isNotBlank()) item {
                    Text("တွေ့ရှိသောဒိုင် ${visible.size} ယောက်",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                agents.isEmpty() -> item {
                    EmptyState(l.translate("ဒိုင်မရှိသေးပါ"),
                        l.translate("ဒိုင်အသစ်ထည့်ရန်ကို အသုံးပြုပါ"))
                }
                visible.isEmpty() -> item {
                    EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရှာဖွေမှုနှင့်ကိုက်ညီသောဒိုင် မရှိပါ"),
                        "ဒိုင်အမည်၊ ဖုန်း သို့မဟုတ် လိပ်စာ ပြန်စစ်ပါ")
                }
                else -> items(visible, key = { it.id }) { agent ->
                    AgentListRow(vm, agent, onInfo = { onInfo(agent.id) })
                }
            }
            item {
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(l.translate("ဒိုင်အသစ်ထည့်ရန်"))
                }
            }
        }
    }
}

@Composable
private fun AgentListRow(vm: LedgerViewModel, agent: AgentEntity, onInfo: () -> Unit) {
    val customers by vm.customers(agent.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    ElevatedCard(onClick = onInfo, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(agent.name, fontWeight = FontWeight.Bold) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("သတ်မှတ်ထားသော လျော်ကြေးအဆ ${agent.rate} ဆ" +
                        if (agent.phone.isNotBlank()) " • ${agent.phone}" else "")
                    Text("အောက်ရှိထိုးသား ${customers.size} ယောက်",
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            leadingContent = {
                Icon(Icons.Default.Store, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Icon(Icons.Default.ChevronRight, contentDescription = "ဒိုင်အချက်အလက်ကြည့်ရန်")
            },
        )
    }
}

@Composable
fun CustomerListWorkspaceScreen(
    vm: LedgerViewModel, onBack: () -> Unit, onInfo: (Long) -> Unit,
    onAdd: (Long) -> Unit, initialAgentId: Long? = null,
) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var selectedAgent by rememberSaveable(initialAgentId) {
        mutableStateOf(initialAgentId ?: language.defaultAgentId)
    }
    LaunchedEffect(agents, language.defaultAgentId, initialAgentId) {
        // Keep the requested agent while the list is still loading. When opened
        // from Agent Information, never switch silently to the global default.
        if (agents.isNotEmpty() &&
            (selectedAgent == 0L || agents.none { it.id == selectedAgent })) {
            selectedAgent = agents.firstOrNull { it.id == initialAgentId }?.id
                ?: agents.firstOrNull { it.id == language.defaultAgentId }?.id
                ?: agents.first().id
        }
    }
    val customers by vm.customers(selectedAgent).collectAsStateWithLifecycle(initialValue = emptyList())
    val agentName = agents.firstOrNull { it.id == selectedAgent }?.name ?: "ဒိုင်ရွေးရန်"
    val l = LocalLanguage.current
    AppScaffold(l.translate("Customer List"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(l.translate("Agent ကိုအရင်ရွေးပါ"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { ScopeDropdown(l.translate("Agent ရွေးရန်"), agentName, agents.map { it.id to "${it.name} • ${it.rate} Rate" }, selectedAgent) { selectedAgent = it } }
            if (selectedAgent == 0L) item { UnavailableState(l.translate("Customer List ကြည့်ရန် Agent တစ်ယောက်ကို ရွေးပါ")) }
            else {
                item { Text("$agentName ${l.translate("အောက်ရှိ Customer များ")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (customers.isEmpty()) item { EmptyState(l.translate("Customer မရှိသေးပါ"), l.translate("ဒီ Agent အောက်မှာ Customer ထည့်ပါ")) }
                else items(customers, key = { it.id }) { customer ->
                    ElevatedCard(onClick = { onInfo(customer.id) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(customer.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text(customer.phone) }, leadingContent = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) })
                    }
                }
                item { OutlinedButton(onClick = { onAdd(selectedAgent) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(l.translate("ထိုးသားအသစ်ထည့်ရန်")) } }
            }
        }
    }
}

/** Preview the next active draw without scheduling a rate change or touching old sessions. */
internal fun nextAgentListDraw(now: LocalDateTime, holidays: Set<LocalDate>): DrawIdentity =
    DrawSchedule.defaultWorkingContext(now, holidays)

@Composable
fun AgentInformationScreen(
    vm: LedgerViewModel, id: Long, onBack: () -> Unit,
    onEdit: (Long) -> Unit, onReport: (Long) -> Unit, onCustomers: (Long) -> Unit,
) {
    val agent by vm.agent(id).collectAsStateWithLifecycle(initialValue = null)
    val customers by vm.customers(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val revision by vm.revision.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // Refresh while the information screen remains open so the "next draw"
    // cannot stay on a completed session after the time boundary.
    LaunchedEffect(id) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }
    val upcoming = remember(now, closedDays) {
        nextAgentListDraw(now, closedDays.map { it.date }.toSet())
    }
    val upcomingRate by produceState<Long?>(null, id, upcoming.date, upcoming.session, revision) {
        value = vm.agentRateForDraw(id, upcoming.date, upcoming.session)
    }
    val l = LocalLanguage.current
    val profile = agent
    if (profile == null) {
        AppScaffold(l.translate("Agent Information"), onBack) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }
    AppScaffold(
        l.translate("Agent Information"), onBack,
        action = {
            IconButton(onClick = { onEdit(id) }) {
                Icon(Icons.Default.Edit, contentDescription = l.translate("ပြင်ရန်"))
            }
        },
    ) { padding ->
        InformationCard(
            padding, profile.name,
            listOf(
                "သတ်မှတ်ထားသောအဆ" to "${profile.rate} ဆ",
                "Phone" to profile.phone,
                "Address" to profile.address,
                "Remark" to profile.remark,
            ),
            scrollContent = true,
        ) {
            HorizontalDivider()
            Text("အောက်ရှိထိုးသား ${customers.size} ယောက်",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "နောက်အကြိမ် • ${upcoming.date} • ${upcoming.session.label} • " +
                    (upcomingRate?.let { "${it} ဆ" } ?: "အဆကိုဖတ်နေသည်"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (upcomingRate != null && upcomingRate != profile.rate) {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အကြိမ်အလိုက်သက်ရောက်သည့် အဆနှင့် သတ်မှတ်ထားသောအဆ ကွာခြားနေသည်။"),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = { onReport(id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်စာရင်းရှင်းတမ်းကြည့်ရန်"))
            }
            OutlinedButton(onClick = { onCustomers(id) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.People, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီဒိုင်အောက်ရှိ ထိုးသားများ"))
            }
        }
    }
}

@Composable
fun CustomerInformationScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onEdit: (Long, Long) -> Unit) {
    val customer by vm.customer(id).collectAsStateWithLifecycle(initialValue = null)
    val profile = customer ?: return
    val l = LocalLanguage.current
    AppScaffold(l.translate("Customer Information"), onBack, action = { IconButton({ onEdit(profile.agentId, id) }) { Icon(Icons.Default.Edit, l.translate("ပြင်ရန်")) } }) { padding ->
        InformationCard(padding, profile.name, listOf("Phone" to profile.phone, "Address" to profile.address, "Remark" to profile.remark))
    }
}

@Composable
private fun InformationCard(
    padding: PaddingValues, title: String,
    fields: List<Pair<String, String>>,
    scrollContent: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val base = Modifier.fillMaxSize().padding(padding)
    val contentModifier = if (scrollContent) base.verticalScroll(rememberScrollState()).padding(16.dp)
        else base.padding(16.dp)
    Column(contentModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold) }
                }
                footer()
            }
        }
    }
}
