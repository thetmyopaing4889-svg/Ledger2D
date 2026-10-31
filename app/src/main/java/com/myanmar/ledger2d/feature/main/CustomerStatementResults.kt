package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private fun Long.statementAmount() = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun Long.statementSigned() = (if (this > 0) "+" else "") + statementAmount()
private data class ReportSection(val start: LocalDate, val title: String, val draws: List<StatementDraw>)

private fun statementSections(draws: List<StatementDraw>, period: StatementPeriod, range: StatementRange): List<ReportSection> {
    val days = ChronoUnit.DAYS.between(range.start, range.end) + 1
    val type = when (period) {
        StatementPeriod.YEAR -> "month"
        StatementPeriod.MONTH -> "week"
        StatementPeriod.CUSTOM -> if (days > 120) "month" else if (days > 31) "week" else "day"
        else -> "day"
    }
    return draws.groupBy {
        when (type) {
            "month" -> it.date.withDayOfMonth(1)
            "week" -> it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            else -> it.date
        }
    }.toSortedMap().map { (start, items) ->
        val title = when (type) {
            "month" -> start.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
            "week" -> {
                val first = maxOf(start, range.start)
                val last = minOf(start.plusDays(6), range.end)
                "${first.reviewDate()} – ${last.reviewDate()}"
            }
            else -> start.reviewDate()
        }
        ReportSection(start, title, items)
    }
}

@Composable
private fun ReportLine(label: String, value: String, emph: Boolean = false, tint: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emph) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (emph) FontWeight.Bold else FontWeight.Medium,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun ReportTotalsCard(t: StatementTotals, customerCount: Int?, counts: StatementDrawCounts) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("စာရင်းချုပ်", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (customerCount != null) Text("စာရင်းရှိသော ထိုးသား $customerCount ယောက်",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReportLine(if (customerCount == null) "အကြိမ်အလိုက် ကွက်ပေါင်း" else "ထိုးသား/အကြိမ်အလိုက် ကွက်ပေါင်း", "${t.slots} ကွက်")
            ReportLine("စုစုပေါင်းထိုးကြေး", "${t.totalBet.statementAmount()} ကျပ်", true)
            HorizontalDivider()
            ReportLine("ပေါက်ကြေး", if (counts.completed > 0) "${t.winningStake.statementAmount()} ကျပ်" else "ရလဒ်မထွက်သေး")
            ReportLine("ပြန်လျော်ငွေ", if (counts.completed > 0) "${t.payout.statementAmount()} ကျပ်" else "—")
            ReportLine("ထိုးသားအနိုင်/အရှုံး",
                if (counts.completed > 0) "${t.customerWinLoss.statementSigned()} ကျပ်" else "မတွက်ရသေး",
                true, if (counts.completed == 0) null else if (t.customerWinLoss < 0) MaterialTheme.colorScheme.error else Color(0xFF18794E))
            if (counts.awaiting > 0)
                Text("ရလဒ်ရပြီး ${counts.completed} ကြိမ် • မရသေး ${counts.awaiting} ကြိမ်။ အနိုင်/အရှုံးမှာ ရပြီးသားအကြိမ်များအတွက်သာ ဖြစ်သည်။",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ရက် / အကြိမ်", Modifier.weight(1.4f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("ထိုးကြေး", Modifier.weight(1.05f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("ပေါက်ကြေး", Modifier.weight(1.05f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("လျော်ငွေ", Modifier.weight(1.05f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
    HorizontalDivider()
}
@Composable
private fun TableRow(title: String, t: StatementTotals, known: Boolean, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val style = MaterialTheme.typography.labelSmall
        Text(title, Modifier.weight(1.4f), style = style, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text(t.totalBet.statementAmount(), Modifier.weight(1.05f), textAlign = TextAlign.End, style = style,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text(if (known) t.winningStake.statementAmount() else "—", Modifier.weight(1.05f), textAlign = TextAlign.End, style = style)
        Text(if (known) t.payout.statementAmount() else "—", Modifier.weight(1.05f), textAlign = TextAlign.End, style = style)
    }
}

@Composable
private fun StatementDigitBreakdown(rows: List<StatementStake>, winner: String?) {
    val byDigit = rows.groupBy { it.digit }.toSortedMap()
    if (byDigit.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    Text("ဂဏန်းအလိုက် ထိုးကြေး", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("ဂဏန်း", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("ထိုးကြေး", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("လျော်ငွေ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider()
    byDigit.forEach { (digit, lines) ->
        val bet = lines.sumOf { it.amount }
        val payout = if (winner == digit) lines.sumOf { Math.multiplyExact(it.amount, it.payoutRate) } else 0L
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(digit, style = MaterialTheme.typography.bodySmall,
                fontWeight = if (digit == winner) FontWeight.Bold else FontWeight.Normal)
            Text(bet.statementAmount(), style = MaterialTheme.typography.bodySmall)
            Text(if (winner == null) "—" else payout.statementAmount(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReportDrawDetail(draws: List<StatementDraw>, rows: List<StatementStake>) {
    val first = draws.firstOrNull() ?: return
    val totals = CustomerStatementCalculator.totals(draws)
    val winner = first.winner
    val rates = rows.map { it.payoutRate }.distinct().sorted()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportLine("ပေါက်ဂဏန်း", winner ?: "မထွက်သေး")
            ReportLine("ထိုးကွက်", "${totals.slots} ကွက်")
            ReportLine("လျော်ကြေးအဆ", rates.joinToString("/") { "$it ဆ" }.ifBlank { "—" })
            ReportLine("ထိုးကြေး", "${totals.totalBet.statementAmount()} ကျပ်", true)
            if (winner != null) {
                ReportLine("ပေါက်ကြေး", "${totals.winningStake.statementAmount()} ကျပ်")
                ReportLine("ပြန်လျော်ငွေ", "${totals.payout.statementAmount()} ကျပ်")
                ReportLine("ထိုးသားအနိုင်/အရှုံး", "${totals.customerWinLoss.statementSigned()} ကျပ်", true)
            }
            StatementDigitBreakdown(rows, winner)
        }
    }
}

@Composable
private fun ReportSectionCard(section: ReportSection, stakes: List<StatementStake>) {
    var expanded by rememberSaveable(section.start) { mutableStateOf(false) }
    var openedDraw by rememberSaveable(section.start) { mutableStateOf<String?>(null) }
    val summary = CustomerStatementCalculator.totals(section.draws)
    val counts = statementDrawCounts(section.draws)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(onClick = { expanded = !expanded }, color = Color.Transparent) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    TableRow("စုစုပေါင်း", summary, counts.awaiting == 0, strong = true)
                }
            }
            if (expanded) {
                TableHeader()
                val byDraw = section.draws.groupBy { it.date to it.session }
                    .toList().sortedWith(compareBy({ it.first.first }, { it.first.second.ordinal }))
                byDraw.forEach { (drawKey, draws) ->
                    val key = "${drawKey.first}/${drawKey.second.name}"
                    val known = draws.all { it.resultKnown }
                    Surface(onClick = { openedDraw = if (openedDraw == key) null else key },
                        color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
                            TableRow("${drawKey.first.dayOfMonth} ${if (drawKey.second == DrawSession.MORNING) "မနက်" else "ညနေ"}",
                                CustomerStatementCalculator.totals(draws), known)
                        }
                    }
                    if (openedDraw == key) {
                        ReportDrawDetail(draws, stakes.filter { it.date == drawKey.first && it.session == drawKey.second })
                    }
                }
                if (counts.awaiting > 0) {
                    Text("— သည် ရလဒ်မထွက်သေးသော အကြိမ်များဖြစ်သည်။",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                TableRow("စုစုပေါင်း", summary, counts.awaiting == 0, true)
            }
        }
    }
}

@Composable
fun CustomerStatementResults(
    vm: LedgerViewModel,
    agentId: Long,
    customerId: Long,
    period: StatementPeriod,
    range: StatementRange,
    session: DrawSession?,
    subtitle: String,
    onReselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    val closed by vm.closedDays.collectAsStateWithLifecycle()
    val data by produceState<CustomerStatement?>(null, agentId, customerId, range, session, revision) {
        value = null
        value = vm.customerStatement(agentId, customerId, range, session)
    }
    var view by rememberSaveable(agentId, customerId) { mutableStateOf("customers") }
    var search by rememberSaveable(agentId, customerId) { mutableStateOf("") }
    var openedCustomer by rememberSaveable(agentId, customerId) { mutableLongStateOf(-1L) }
    LazyColumn(modifier, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(subtitle, fontWeight = FontWeight.SemiBold)
                        Text("${range.start.reviewDate()}${if (range.start == range.end) "" else " – ${range.end.reviewDate()}"}" +
                            " • " + (session?.reviewLabel() ?: "နှစ်ကြိမ်လုံး"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onReselect) { Text("ပြန်ရွေးရန်") }
                }
            }
        }
        val statement = data
        if (statement == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
            val counts = statementDrawCounts(statement.draws)
            item { ReportTotalsCard(statement.totals, if (customerId == -1L) statement.customers.size else null, counts) }
            if (statement.draws.isEmpty()) {
                item {
                    Text(if (range.start == range.end && closed.any { it.date == range.start }) "ပိတ်ရက်ဖြစ်သည်။ ထိုးစာရင်းမရှိပါ။"
                        else "ရွေးထားသောကာလအတွက် ထိုးစာရင်းမရှိပါ။")
                }
            } else {
                if (customerId == -1L) item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(view == "customers", { view = "customers" }, label = { Text("ထိုးသားအလိုက်") })
                        FilterChip(view == "total", { view = "total" }, label = { Text("အားလုံးစုစုပေါင်း") })
                    }
                }
                if (customerId == -1L && view == "customers") {
                    item {
                        OutlinedTextField(search, { search = it }, label = { Text("ထိုးသားအမည်ရှာရန်") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    val list = customers.filter {
                        statement.customers.containsKey(it.id) && it.name.contains(search, ignoreCase = true)
                    }
                    items(list.size) { i ->
                        val customer = list[i]
                        val draws = statement.draws.filter { it.customerId == customer.id }
                        val t = statement.customers[customer.id]!!
                        val open = openedCustomer == customer.id
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Surface(onClick = { openedCustomer = if (open) -1L else customer.id }, color = Color.Transparent) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(customer.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                    }
                                }
                                ReportLine("ထိုးကြေး", "${t.totalBet.statementAmount()} ကျပ်")
                                ReportLine("ပေါက်ကြေး", if (t.completedDraws > 0) "${t.winningStake.statementAmount()} ကျပ်" else "—")
                                ReportLine("ပြန်လျော်ငွေ", if (t.completedDraws > 0) "${t.payout.statementAmount()} ကျပ်" else "—")
                                if (open) {
                                    statementSections(draws, period, range).forEach { section ->
                                        ReportSectionCard(section, statement.stakes.filter { it.customerId == customer.id })
                                    }
                                }
                            }
                        }
                    }
                    item {
                        val ids = list.map { it.id }.toSet()
                        val sub = CustomerStatementCalculator.totals(statement.draws.filter { it.customerId in ids })
                        val subCounts = statementDrawCounts(statement.draws.filter { it.customerId in ids })
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("အောက်ဆုံးစုစုပေါင်း", fontWeight = FontWeight.Bold)
                                ReportLine("ထိုးကြေး", "${sub.totalBet.statementAmount()} ကျပ်", true)
                                ReportLine("ပေါက်ကြေး", if (subCounts.completed > 0) sub.winningStake.statementAmount() else "—")
                                ReportLine("ပြန်လျော်ငွေ", if (subCounts.completed > 0) sub.payout.statementAmount() else "—")
                                if (search.isNotBlank()) Text("ရှာဖွေထားသည့် ထိုးသားများအတွက်သာ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    item { Text("ကာလအလိုက် စာရင်း", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    val sections = statementSections(statement.draws, period, range)
                    items(sections.size) { i ->
                        ReportSectionCard(sections[i], statement.stakes)
                    }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text("အောက်ဆုံးစုစုပေါင်း", fontWeight = FontWeight.Bold)
                                TableHeader()
                                TableRow("စုစုပေါင်း", statement.totals, counts.awaiting == 0, true)
                            }
                        }
                    }
                }
            }
        }
    }
}
