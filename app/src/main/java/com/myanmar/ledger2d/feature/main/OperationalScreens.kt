package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

@Composable
fun TodayLedgerScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val l = com.myanmar.ledger2d.core.design.LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val agents by vm.agents.collectAsStateWithLifecycle()
    AppScaffold(l.translate("ယနေ့စာရင်း"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateInput(dateText, { dateText = it }, "ရက်စွဲ"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }) } } }
            if (agents.isEmpty()) item { EmptyState(l.translate("ဒိုင်မရှိသေးပါ"), l.translate("ဒိုင်ထည့်ပြီးမှ ယနေ့စာရင်းကို ကြည့်နိုင်ပါမည်")) }
            items(agents, key = { it.id }) { agent ->
                val totals by vm.agentTotals(agent.id, date, session).collectAsStateWithLifecycle(initialValue = emptyList())
                ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(agent.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (totals.isEmpty()) Text(l.translate("စာရင်းမရှိသေးပါ"), color = MaterialTheme.colorScheme.onSurfaceVariant) else totals.forEach { row -> ListItem(headlineContent = { Text(row.digit, fontWeight = FontWeight.Bold) }, trailingContent = { Text(row.amount.mmk(), fontWeight = FontWeight.Bold) }) } ; HorizontalDivider(); Text("${l.translate("စုစုပေါင်း")} ${totals.sumOf { it.amount }.mmk()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

@Composable
fun SettlementScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val l = com.myanmar.ledger2d.core.design.LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val reports by produceState<Map<Long, DrawReport>>(emptyMap(), date, session, revision, agents) { value = agents.associate { it.id to vm.agentReport(it.id, date, session, true) } }
    AppScaffold(l.translate("ရှင်းတမ်း"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateInput(dateText, { dateText = it }, "ရက်စွဲ"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }) } } }
            if (reports.isEmpty()) item { EmptyState(l.translate("ဒိုင်မရှိသေးပါ"), l.translate("ဒိုင်ထည့်ပြီးမှ ရှင်းတမ်းတွက်နိုင်ပါမည်")) }
            items(agents, key = { it.id }) { agent -> val report = reports[agent.id]; val settled by produceState<Boolean?>(null, agent.id, date, session, revision) { value = vm.isSettled(agent.id, date, session) }; report?.let { r -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(agent.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${l.translate("ထိုးကြေး")} ${r.calculation.totalBet.mmk()}"); Text("${l.translate("ပေါက်ကြေး")} ${r.calculation.winningStake.mmk()}"); Text("${l.translate("လျော်ပေးငွေ")} ${r.calculation.payout.mmk()}"); Text("${l.translate("ကော်မရှင်")} ${r.calculation.commission.mmk()}"); HorizontalDivider(); Text("Net ${r.calculation.netSettlement.mmk()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); if (settled == true) Text(l.translate("ရှင်းတမ်းအတည်ပြုပြီး"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) else Button(onClick = { vm.settleAgent(agent.id, date, session) }, enabled = r.winnerAvailable) { Text(l.translate("ရှင်းတမ်းအတည်ပြုမည်")) } } } } }
        }
    }
}
