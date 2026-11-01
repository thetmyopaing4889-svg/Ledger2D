@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import com.myanmar.ledger2d.core.database.DigitCommissionRow
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.AgentDigitGroup
import com.myanmar.ledger2d.core.domain.AgentDigitLine
import com.myanmar.ledger2d.core.domain.AgentDigitTotalsAggregator
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private fun Long.digitMoney() = NumberFormat.getIntegerInstance(Locale.US).format(this)

@Composable
fun AgentDigitTotalsWorkspace(
    vm: LedgerViewModel,
    onBack: () -> Unit,
    initialAgentId: Long? = null,
) {
    val language = LocalLanguage.current
    val agents by vm.agents.collectAsStateWithLifecycle()
    var selectedAgent by rememberSaveable(initialAgentId) {
        mutableLongStateOf(initialAgentId ?: language.defaultAgentId.takeIf { it > 0 } ?: -1L)
    }
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    var allView by rememberSaveable { mutableStateOf("perAgent") }

    LaunchedEffect(language.selectedDate, language.selectedSession) {
        dateText = language.selectedDate
        session = language.selectedSession
    }
    LaunchedEffect(selectedAgent, agents) {
        if (agents.isNotEmpty() && selectedAgent > 0 && agents.none { it.id == selectedAgent }) {
            selectedAgent = -1L
        }
    }

    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    // Existing per-agent Room flows already distribute each saved commission
    // across digits without duplicating a multiline bet's commission.
    // Combine those same rows for the 'all agents' screen.
    val groups by produceState<List<AgentDigitGroup>?>(null, date, session, agents) {
        value = null
        if (date == null || agents.isEmpty()) {
            value = emptyList()
        } else {
            val sources = agents.map { vm.agentTotalsWithCommission(it.id, date, session) }
            combine(sources) { allRows: Array<List<DigitCommissionRow>> ->
                agents.mapIndexed { index, agent ->
                    AgentDigitGroup(
                        agentId = agent.id,
                        agentName = agent.name,
                        digits = allRows[index].map {
                            AgentDigitLine(it.digit, it.amount, it.commission)
                        },
                    )
                }
            }.collect { value = it }
        }
    }
    val selectedName = if (selectedAgent == -1L) "ဒိုင်အားလုံး"
        else agents.firstOrNull { it.id == selectedAgent }?.name ?: "ဒိုင်ရွေးရန်"
    val filteredGroups = groups?.filter { selectedAgent == -1L || it.agentId == selectedAgent }
    val activeGroups = filteredGroups?.filter { it.digits.isNotEmpty() }
    val combined = remember(filteredGroups) {
        filteredGroups?.let { AgentDigitTotalsAggregator.combine(it) }
    }

    AppScaffold("အကွက်စုစုပေါင်း", onBack, includeQuickEntryFab = false) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("ဒိုင်နှင့် အကြိမ်ရွေးရန်", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                ScopeDropdown(
                    "ဒိုင်ရွေးရန်", selectedName,
                    listOf(-1L to "ဒိုင်အားလုံး") + agents.map { it.id to it.name },
                    selectedAgent,
                ) { selectedAgent = it }
                Spacer(Modifier.height(8.dp))
                DateInput(dateText, {
                    dateText = it
                    language.setDate(it)
                }, "ရက်စွဲ")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DrawSession.entries.forEach { draw ->
                        FilterChip(session == draw, {
                            session = draw
                            language.setSession(draw)
                        }, label = { Text(draw.label) })
                    }
                }
            }
            if (date == null) {
                item { Text("ရက်စွဲ မှန်ကန်စွာရွေးပါ", color = MaterialTheme.colorScheme.error) }
            } else if (groups == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else if (agents.isEmpty()) {
                item { Text("ဒိုင်မရှိသေးပါ။ ဒိုင်အသစ်ထည့်ပြီးမှ စာရင်းကြည့်နိုင်မယ်။") }
            } else if (selectedAgent > 0 && agents.none { it.id == selectedAgent }) {
                item { Text("ဒိုင်ရွေးချယ်မှုကို ပြန်စစ်ပါ") }
            } else {
                item {
                    Text("${date} • ${session.label} • ${selectedName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    DigitTotalsSummary(
                        combined.orEmpty(),
                        title = if (selectedAgent == -1L) "ဒိုင်အားလုံး စာရင်းချုပ်" else "ဒိုင်စာရင်းချုပ်",
                    )
                }
                if (selectedAgent == -1L) {
                    item {
                        Text(
                            "ဒီစုစုပေါင်းမှာ Self နှင့် အခြားဒိုင်အောက် ပြန်သွင်းထားသောစာရင်းတွေပါနိုင်ပါတယ်။ " +
                                "မူလရောင်းအားအမှန်လို့ မယူဆပါနဲ့။ ကော်မရှင်က သိမ်းထားတဲ့ Net Commission ပါ။",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(allView == "perAgent", { allView = "perAgent" },
                                label = { Text("ဒိုင်အလိုက်") })
                            FilterChip(allView == "combined", { allView = "combined" },
                                label = { Text("အားလုံးပေါင်း") })
                        }
                    }
                } else item {
                    Text("ကော်မရှင်သည် App ထဲက သိမ်းထားသော Net Commission ဖြစ်ပြီး " +
                        "ဒိုင်ဆီ အမှန်တကယ်ပေး/ရမယ့်ငွေ မဟုတ်ပါ။",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (combined.isNullOrEmpty()) {
                    item { Text("ဒီရက်/အကြိမ်အတွက် ထိုးစာရင်းမရှိသေးပါ။") }
                } else if (selectedAgent == -1L && allView == "perAgent") {
                    item {
                        Text("ဒိုင်အလိုက် ဂဏန်းစုစုပေါင်း (${activeGroups.orEmpty().size} ဒိုင်)",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(activeGroups.orEmpty(), key = { it.agentId }) { group ->
                        DigitTotalsAgentCard(group)
                    }
                    item {
                        DigitTotalsSummary(combined.orEmpty(), "အောက်ဆုံး ဒိုင်အားလုံးပေါင်း")
                    }
                } else {
                    item {
                        Text(if (selectedAgent == -1L) "ဂဏန်းတူရာ အားလုံးပေါင်း"
                            else "ဂဏန်းအလိုက် စုစုပေါင်း",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("ဇယားကို ဘေးတိုက်ဆွဲကြည့်နိုင်သည်",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { DigitTotalsTable(combined.orEmpty()) }
                    item { DigitTotalsSummary(combined.orEmpty(), "အောက်ဆုံးစုစုပေါင်း") }
                }
            }
        }
    }
}

@Composable
private fun DigitTotalsAgentCard(group: AgentDigitGroup) {
    var expanded by rememberSaveable(group.agentId) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = Color.Transparent, onClick = { expanded = !expanded }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(group.agentName, fontWeight = FontWeight.Bold)
                        Text("ဂဏန်း ${group.digits.size} ကွက် • ထိုးကြေး ${group.totalStake.digitMoney()} ကျပ်",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Net ကော်မရှင် ${group.totalNetCommission.digitMoney()} ကျပ်",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "အသေးစိတ်ပိတ်ရန်" else "အသေးစိတ်ကြည့်ရန်")
                }
            }
            if (expanded) DigitTotalsTable(group.digits)
        }
    }
}

@Composable
private fun DigitTotalsSummary(rows: List<AgentDigitLine>, title: String) {
    val stake = rows.fold(0L) { sum, row -> Math.addExact(sum, row.stake) }
    val commission = rows.fold(0L) { sum, row -> Math.addExact(sum, row.netCommission) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            DigitTotalsSummaryLine("စုစုပေါင်းထိုးကြေး", "${stake.digitMoney()} ကျပ်")
            DigitTotalsSummaryLine("Net ကော်မရှင်", "${commission.digitMoney()} ကျပ်")
            DigitTotalsSummaryLine("ရှိသောဂဏန်း", "${rows.size} ကွက်")
        }
    }
}

@Composable
private fun DigitTotalsSummaryLine(label: String, amount: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(amount, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End)
    }
}

@Composable
private fun DigitTotalsTable(rows: List<AgentDigitLine>) {
    val scroll = rememberScrollState()
    val ordered = remember(rows) { rows.sortedBy { it.digit } }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ဂဏန်း", Modifier.width(70.dp), fontWeight = FontWeight.Bold)
            Text("ထိုးကြေး", Modifier.width(146.dp), textAlign = TextAlign.End,
                fontWeight = FontWeight.Bold)
            Text("Net ကော်မရှင်", Modifier.width(146.dp), textAlign = TextAlign.End,
                fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        ordered.forEach { row ->
            Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.digit, Modifier.width(70.dp), fontWeight = FontWeight.SemiBold)
                Text(row.stake.digitMoney(), Modifier.width(146.dp), textAlign = TextAlign.End)
                Text(row.netCommission.digitMoney(), Modifier.width(146.dp), textAlign = TextAlign.End)
            }
            HorizontalDivider()
        }
        val totalStake = ordered.fold(0L) { sum, row -> Math.addExact(sum, row.stake) }
        val commission = ordered.fold(0L) { sum, row -> Math.addExact(sum, row.netCommission) }
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total", Modifier.width(70.dp), fontWeight = FontWeight.Bold)
            Text(totalStake.digitMoney(), Modifier.width(146.dp),
                textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Text(commission.digitMoney(), Modifier.width(146.dp),
                textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }
    }
}
