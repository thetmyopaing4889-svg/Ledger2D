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

@Composable
fun AgentListWorkspaceScreen(vm: LedgerViewModel, onBack: () -> Unit, onInfo: (Long) -> Unit, onAdd: () -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val l = LocalLanguage.current
    AppScaffold(l.translate("Agent List"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(l.translate("Add Agent နဲ့ ဖန်တီးထားသော Agent များ"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (agents.isEmpty()) item { EmptyState(l.translate("ဒိုင်မရှိသေးပါ"), l.translate("Home မှ ဒိုင်အသစ်ထည့်ရန်ကို အသုံးပြုပါ")) }
            else items(agents, key = { it.id }) { agent ->
                ElevatedCard(onClick = { onInfo(agent.id) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(agent.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("${agent.rate} Rate" + if (agent.phone.isNotBlank()) " • ${agent.phone}" else "") },
                        leadingContent = { Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, l.translate("ကြည့်ရန်")) }
                    )
                }
            }
            item { OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(l.translate("ဒိုင်အသစ်ထည့်ရန်")) } }
        }
    }
}

@Composable
fun CustomerListWorkspaceScreen(vm: LedgerViewModel, onBack: () -> Unit, onInfo: (Long) -> Unit, onAdd: (Long) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    var selectedAgent by rememberSaveable { mutableStateOf(language.defaultAgentId) }
    LaunchedEffect(agents, language.defaultAgentId) {
        if (selectedAgent == 0L || agents.none { it.id == selectedAgent }) {
            selectedAgent = agents.firstOrNull { it.id == language.defaultAgentId }?.id ?: 0L
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

@Composable
fun AgentInformationScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val agent by vm.agent(id).collectAsStateWithLifecycle(initialValue = null)
    val profile = agent ?: return
    val l = LocalLanguage.current
    AppScaffold(l.translate("Agent Information"), onBack, action = { IconButton({ onEdit(id) }) { Icon(Icons.Default.Edit, l.translate("ပြင်ရန်")) } }) { padding ->
        InformationCard(padding, profile.name, listOf("Rate" to profile.rate.toString(), "Phone" to profile.phone, "Address" to profile.address, "Remark" to profile.remark))
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
private fun InformationCard(padding: PaddingValues, title: String, fields: List<Pair<String, String>>) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
