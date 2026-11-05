@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun Long.moneyLabel(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun LocalDate.dateLabel(): String = format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))

private fun selectionLabel(period: StatementPeriod, range: StatementRange): String = when (period) {
    StatementPeriod.DAY -> range.start.dateLabel()
    StatementPeriod.WEEK, StatementPeriod.CUSTOM -> range.start.dateLabel() + " – " + range.end.dateLabel()
    StatementPeriod.MONTH -> range.start.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
    StatementPeriod.YEAR -> range.start.year.toString()
}
private data class CustomerLedgerLine(
    val date: LocalDate,
    val session: DrawSession,
    val draws: List<StatementDraw>,
    val winner: String?,
    val closed: Boolean
) {
    val bet: Boolean get() = draws.isNotEmpty()
    val complete: Boolean get() = winner != null && bet
    val total: StatementTotals get() = CustomerStatementCalculator.totals(draws)
}
private fun buildLedgerLines(
    page: CustomerReportPage, period: StatementPeriod, session: DrawSession?,
    draws: List<StatementDraw>, winners: Map<Pair<LocalDate, DrawSession>, String>, closed: Set<LocalDate>
): List<CustomerLedgerLine> {
    val byKey = draws.groupBy { it.date to it.session }
    return CustomerReportFrame.slots(page, session, includeWeekend = period == StatementPeriod.DAY).map { slot ->
        val matching = byKey[slot.date to slot.session].orEmpty()
        CustomerLedgerLine(slot.date, slot.session, matching,
            matching.firstOrNull()?.winner ?: winners[slot.date to slot.session],
            slot.date in closed || slot.date.dayOfWeek.value > 5)
    }
}

@Composable
private fun StatementDue(draws: List<StatementDraw>, compact: Boolean) {
    val lang = LocalLanguage.current
    if (draws.isEmpty()) {
        Text(lang.text("မရှိ", "None"), fontSize = if (compact) 10.sp else 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (draws.none { it.resultKnown }) {
        Text(lang.text("စောင့်", "Pending"), fontSize = if (compact) 10.sp else 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (receive,pay) = CustomerStatementCalculator.settlementByCustomer(draws)
    Column(horizontalAlignment = if (compact) Alignment.End else Alignment.Start) {
        if (receive > 0L || pay == 0L)
            Text(lang.text(if (compact) "ရ " else "User ရရန် ", if (compact) "In " else "User receives ") + receive.moneyLabel(),
                fontSize = if (compact) 10.sp else 13.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = if (compact) 13.sp else 18.sp)
        if (pay > 0L)
            Text(lang.text(if (compact) "ပေး " else "User ပေးရန် ", if (compact) "Out " else "User pays ") + pay.moneyLabel(),
                fontSize = if (compact) 10.sp else 13.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = if (compact) 13.sp else 18.sp)
    }
}

@Composable
private fun StatementMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.58f),
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal=10.dp,vertical=9.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label, style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize=18.sp, lineHeight=22.sp, fontWeight=FontWeight.Bold, maxLines=2)
        }
    }
}

@Composable
private fun StatementSummary(draws: List<StatementDraw>, customerCount: Int?) {
    val lang = LocalLanguage.current
    val total = CustomerStatementCalculator.totals(draws)
    val count = statementDrawCounts(draws)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(lang.text("စာရင်းချုပ်", "Summary"),fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            if (customerCount != null)
                Text(lang.text("ထိုးသား " + customerCount + " ယောက်", customerCount.toString() + " customers"),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            StatementMetric(lang.text("ထိုးကွက်","Slots"),total.slots.moneyLabel(),Modifier.weight(1f))
            StatementMetric(lang.text("ထိုးကြေး","Stake"),total.totalBet.moneyLabel(),Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            StatementMetric(lang.text("ပေါက်ကြေး","Winning stake"),
                if (count.completed > 0) total.winningStake.moneyLabel() else "—",Modifier.weight(1f))
            StatementMetric(lang.text("လျော်ငွေ","Payout"),
                if (count.completed > 0) total.payout.moneyLabel() else "—",Modifier.weight(1f))
        }
        Surface(color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.38f),
            shape=MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lang.text("ငွေရှင်းရန်","Amount due"),fontWeight=FontWeight.Bold)
                    if (count.awaiting>0)
                        Text(lang.text("ပြီး " + count.completed + " • စောင့် " + count.awaiting,
                            "Confirmed " + count.completed + " • Pending " + count.awaiting),
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatementDue(draws,false)
            }
        }
    }
}

@Composable
private fun RowScope.LedgerText(text: String, weight: Float, header: Boolean=false,
                                  align: TextAlign=TextAlign.Start) {
    Text(text, modifier=Modifier.weight(weight),
        fontSize=if (header) 10.sp else 10.5.sp,
        lineHeight=13.sp, maxLines=2, softWrap=true,
        fontWeight=if (header) FontWeight.Bold else FontWeight.Medium,
        textAlign=align,
        color=if (header) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun LedgerHeader(view: String) {
    val lang=LocalLanguage.current
    Surface(color=MaterialTheme.colorScheme.primary) {
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=9.dp),
            horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
            LedgerText(lang.text("ရက်/ကြိမ်","Date"),1.27f,true)
            LedgerText(if(view=="stake") lang.text("ကွက်","Slots") else lang.text("ဂဏန်း","Digit"),.69f,true,TextAlign.End)
            LedgerText(if(view=="stake") lang.text("ထိုးကြေး","Stake") else lang.text("ပေါက်ကြေး","Win stake"),1.23f,true,TextAlign.End)
            LedgerText(lang.text("လျော်ငွေ","Payout"),1.16f,true,TextAlign.End)
            LedgerText(lang.text("ရှင်းရန်","Due"),1.35f,true,TextAlign.End)
        }
    }
}

@Composable
private fun LedgerEntry(line: CustomerLedgerLine, view: String, shade: Boolean, onClick:()->Unit) {
    val lang=LocalLanguage.current
    val total=line.total
    val color=if(shade) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.30f) else Color.Transparent
    Surface(onClick=onClick,color=color) {
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1.27f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                Text(line.date.dayOfMonth.toString() + " " +
                    line.date.format(DateTimeFormatter.ofPattern("EEE",Locale.US)),
                    fontSize=10.5.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                Text(if(line.session==DrawSession.MORNING) lang.text("မနက်","AM") else lang.text("ညနေ","PM"),
                    fontSize=9.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val isClosed = line.closed && !line.bet
            val n = if(view=="stake")
                if(line.bet) total.slots.moneyLabel() else if(isClosed) lang.text("ပိတ်","Closed") else "—"
                else line.winner ?: if(isClosed) lang.text("ပိတ်","Closed") else "—"
            LedgerText(n,.69f,align=TextAlign.End)
            val amount = if(view=="stake")
                if(line.bet) total.totalBet.moneyLabel() else "—"
                else if(line.complete) total.winningStake.moneyLabel() else "—"
            LedgerText(amount,1.23f,align=TextAlign.End)
            LedgerText(if(line.complete) total.payout.moneyLabel() else "—",1.16f,align=TextAlign.End)
            Box(Modifier.weight(1.35f),contentAlignment=Alignment.CenterEnd) {
                if(line.bet && line.winner == null)
                    Text(lang.text("စောင့်","Pending"),fontSize=10.sp)
                else if(!line.bet) Text("—",fontSize=10.sp)
                else StatementDue(line.draws,true)
            }
        }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
}

@Composable
private fun LedgerTotal(draws: List<StatementDraw>, view: String, monthTotal: Boolean) {
    val lang=LocalLanguage.current
    val totals=CustomerStatementCalculator.totals(draws)
    Surface(color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.65f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=10.dp),
            horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1.27f)) {
                Text(if(monthTotal) lang.text("လချုပ်","Month") else lang.text("စုစုပေါင်း","Total"),
                    fontSize=10.sp,fontWeight=FontWeight.Bold,maxLines=2)
            }
            LedgerText(if(view=="stake") totals.slots.moneyLabel() else "—",.69f,align=TextAlign.End)
            LedgerText(if(view=="stake") totals.totalBet.moneyLabel()
                else if(totals.completedDraws>0) totals.winningStake.moneyLabel() else "—",1.23f,align=TextAlign.End)
            LedgerText(if(totals.completedDraws>0) totals.payout.moneyLabel() else "—",1.16f,align=TextAlign.End)
            Box(Modifier.weight(1.35f),contentAlignment=Alignment.CenterEnd) { StatementDue(draws,true) }
        }
    }
}

@Composable
private fun PeriodLedger(
    page: CustomerReportPage, period: StatementPeriod, session: DrawSession?,
    draws: List<StatementDraw>, winners: Map<Pair<LocalDate,DrawSession>,String>,
    closed: Set<LocalDate>, view: String, onOpen:(LocalDate,DrawSession)->Unit
) {
    val lang=LocalLanguage.current
    val rows=remember(page,period,session,draws,winners,closed) {
        buildLedgerLines(page,period,session,draws,winners,closed)
    }
    Column {
        LedgerHeader(view)
        if(rows.isEmpty()) Text(lang.text("ဤကာလတွင် အလုပ်လုပ်ရက်မရှိပါ","No working days"),
            Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
        val firstWeek=CustomerReportFrame.weekStart(page.start)
        rows.forEach { line ->
            val week=ChronoUnit.DAYS.between(firstWeek,CustomerReportFrame.weekStart(line.date))/7
            LedgerEntry(line,view,week%2!=0L){onOpen(line.date,line.session)}
        }
        val pageDraws=draws.filter { !it.date.isBefore(page.start) && !it.date.isAfter(page.end) }
        LedgerTotal(pageDraws,view,period==StatementPeriod.MONTH||period==StatementPeriod.YEAR||period==StatementPeriod.CUSTOM)
    }
}

@Composable
private fun DetailValue(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun DrawLedgerSheet(
    date:LocalDate,session:DrawSession,draws:List<StatementDraw>,
    stakes:List<StatementStake>,winner:String?,onClose:()->Unit
) {
    val lang=LocalLanguage.current
    val total=CustomerStatementCalculator.totals(draws)
    val rates=stakes.map { it.payoutRate }.distinct().sorted().joinToString("/") { it.toString() }
    val digits=stakes.groupBy { it.digit }.toSortedMap().toList()
    ModalBottomSheet(onDismissRequest=onClose) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=620.dp),
            contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            item {
                Text(date.dateLabel() + " · " +
                    if(session==DrawSession.MORNING) lang.text("မနက်အကြိမ်","Morning")
                    else lang.text("ညနေအကြိမ်","Evening"),
                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            }
            item { DetailValue(lang.text("ပေါက်ဂဏန်း","Winning digit"),winner ?: lang.text("ရလဒ်စောင့်","Pending")) }
            item { DetailValue(lang.text("ထိုးကွက်","Slots"),total.slots.moneyLabel()) }
            item { DetailValue(lang.text("ထိုးကြေး","Stake"),total.totalBet.moneyLabel()) }
            item { DetailValue(lang.text("လျော်ကြေးအဆ","Payout rate"),if(rates.isEmpty()) "—" else rates + " x") }
            item { DetailValue(lang.text("ပေါက်ကြေး","Winning stake"),
                if(winner!=null&&draws.isNotEmpty()) total.winningStake.moneyLabel() else "—") }
            item { DetailValue(lang.text("လျော်ငွေ","Payout"),
                if(winner!=null&&draws.isNotEmpty()) total.payout.moneyLabel() else "—") }
            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(top=9.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(lang.text("ငွေရှင်းရန်","Amount due"),fontWeight=FontWeight.Bold)
                    StatementDue(draws,false)
                }
            }
            if(digits.isEmpty()) item {
                Text(lang.text("ထိုးစာရင်းမရှိ","No entries"),style=MaterialTheme.typography.bodySmall)
            } else {
                item {
                    HorizontalDivider()
                    Text(lang.text("ဂဏန်းအလိုက်စာရင်း","By digit"),
                        style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
                }
                item {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(lang.text("ဂဏန်း","Digit"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)
                        Text(lang.text("ထိုးကြေး / လျော်ငွေ","Stake / Payout"),fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
                items(digits) { pair ->
                    val amount=pair.second.fold(0L){acc,s->Math.addExact(acc,s.amount)}
                    val payout=if(pair.first==winner) pair.second.fold(0L){acc,s->
                        Math.addExact(acc,Math.multiplyExact(s.amount,s.payoutRate))
                    } else 0L
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(pair.first,fontWeight=if(pair.first==winner) FontWeight.Bold else FontWeight.Normal)
                        Text(amount.moneyLabel()+" / "+if(winner==null) "—" else payout.moneyLabel(),
                            fontSize=13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCaption(page:CustomerReportPage,period:StatementPeriod) {
    val lang=LocalLanguage.current
    val monthNames=listOf("ဇန်နဝါရီ","ဖေဖော်ဝါရီ","မတ်","ဧပြီ","မေ","ဇွန်",
        "ဇူလိုင်","ဩဂုတ်","စက်တင်ဘာ","အောက်တိုဘာ","နိုဝင်ဘာ","ဒီဇင်ဘာ")
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        val name=if(period==StatementPeriod.DAY||period==StatementPeriod.WEEK)
            page.start.dateLabel()+if(page.start==page.end) "" else " – "+page.end.dateLabel()
            else lang.text(monthNames[page.start.monthValue-1]+" "+page.start.year,
                page.start.format(DateTimeFormatter.ofPattern("MMM yyyy",Locale.US)))
        Text(name,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold)
        if(period==StatementPeriod.CUSTOM &&
            (page.start.dayOfMonth!=1 || page.end.dayOfMonth!=page.end.lengthOfMonth()))
            Text(page.start.dayOfMonth.toString()+"–"+page.end.dayOfMonth,
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CustomerLedgerReportResults(
    vm:LedgerViewModel,agentId:Long,customerId:Long,period:StatementPeriod,range:StatementRange,
    session:DrawSession?,subtitle:String,onReselect:()->Unit,modifier:Modifier=Modifier
) {
    val lang=LocalLanguage.current
    val revision by vm.revision.collectAsStateWithLifecycle()
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue=emptyList())
    val winners by vm.winners.collectAsStateWithLifecycle()
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val data by produceState<CustomerStatement?>(null,agentId,customerId,range,session,revision) {
        value=null
        value=vm.customerStatement(agentId,customerId,range,session)
    }
    val pages=remember(period,range){CustomerReportFrame.pages(period,range)}
    val initial=remember(period,range) {
        val today=LocalDate.now()
        if(period==StatementPeriod.YEAR && range.contains(today))
            pages.indexOfFirst { !today.isBefore(it.start)&&!today.isAfter(it.end) }.coerceAtLeast(0)
        else 0
    }
    var monthIndex by rememberSaveable(agentId,customerId,period,range.start,range.end) {
        mutableIntStateOf(initial)
    }
    val page=pages[monthIndex.coerceIn(0,pages.lastIndex)]
    var tableView by rememberSaveable(agentId,customerId){mutableStateOf("stake")}
    var groupView by rememberSaveable(agentId,customerId){mutableStateOf("customers")}
    var search by rememberSaveable(agentId,customerId){mutableStateOf("")}
    var expandedCustomer by rememberSaveable(agentId,customerId){mutableLongStateOf(-1L)}
    var detailDate by rememberSaveable(agentId,customerId){mutableStateOf<String?>(null)}
    var detailDraw by rememberSaveable(agentId,customerId){mutableStateOf<String?>(null)}
    var detailCustomer by rememberSaveable(agentId,customerId){mutableLongStateOf(-1L)}
    val winnerMap=remember(winners,range) {
        winners.filter { range.contains(it.date) }.associate { (it.date to it.session) to it.digit }
    }
    val closed=remember(closedDays){closedDays.map {it.date}.toSet()}
    val statement=data

    LazyColumn(modifier,contentPadding=PaddingValues(12.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subtitle,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                    Text(selectionLabel(period,range)+" · "+
                        (session?.reviewLabel() ?: lang.text("နှစ်ကြိမ်လုံး","Both draws")),
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick=onReselect) { Text(lang.text("ပြန်ရွေးရန်","Change")) }
            }
        }
        if(statement==null) {
            item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        } else {
            item { StatementSummary(statement.draws,
                if(customerId==-1L) statement.customers.size else null) }
            if(customerId==-1L) item {
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    FilterChip(groupView=="customers",onClick={groupView="customers"},
                        label={Text(lang.text("ထိုးသားအလိုက်","By customer"))})
                    FilterChip(groupView=="total",onClick={groupView="total"},
                        label={Text(lang.text("အားလုံးစုစုပေါင်း","Combined"))})
                }
            }
            item {
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(lang.text("ကာလအလိုက်စာရင်း","Period ledger"),
                        style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={
                            if(monthIndex>0){monthIndex--;detailDate=null}
                        },enabled=monthIndex>0){
                            Icon(Icons.Default.ChevronLeft,lang.text("ယခင်လ","Previous month"))
                        }
                        PageCaption(page,period)
                        IconButton(onClick={
                            if(monthIndex<pages.lastIndex){monthIndex++;detailDate=null}
                        },enabled=monthIndex<pages.lastIndex){
                            Icon(Icons.Default.ChevronRight,lang.text("နောက်လ","Next month"))
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(tableView=="stake",onClick={tableView="stake"},
                            label={Text(lang.text("ထိုးစာရင်း","Stakes"))},modifier=Modifier.weight(1f))
                        FilterChip(tableView=="win",onClick={tableView="win"},
                            label={Text(lang.text("ပေါက်စာရင်း","Results"))},modifier=Modifier.weight(1f))
                    }
                }
            }
            if(customerId==-1L && groupView=="customers") {
                item {
                    OutlinedTextField(search,onValueChange={search=it},
                        label={Text(lang.text("ထိုးသားရှာရန်","Search customers"))},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                val list=customers.filter {
                    statement.customers.containsKey(it.id)&&it.name.contains(search,ignoreCase=true)
                }
                if(list.isEmpty()) item {
                    Text(lang.text("ထိုးသားစာရင်းမရှိ","No matching customers"),
                        style=MaterialTheme.typography.bodySmall)
                    PeriodLedger(page,period,session,emptyList(),winnerMap,closed,tableView){d,s->
                        detailDate=d.toString();detailDraw=s.name;detailCustomer=-1L
                    }
                }
                items(list,key={it.id}) { customer ->
                    val draws=statement.draws.filter {it.customerId==customer.id}
                    val subtotal=statement.customers[customer.id]!!
                    val open=expandedCustomer==customer.id
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth().clickable {
                                expandedCustomer=if(open) -1L else customer.id
                            }.padding(5.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(customer.name,fontWeight=FontWeight.Bold)
                                    Text(lang.text("ကွက် ","Slots ")+subtotal.slots.moneyLabel()+
                                        " · "+lang.text("ထိုးကြေး ","Stake ")+subtotal.totalBet.moneyLabel(),
                                        style=MaterialTheme.typography.labelSmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatementDue(draws,true)
                                Spacer(Modifier.width(4.dp))
                                Icon(if(open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                            }
                            if(open) PeriodLedger(page,period,session,draws,winnerMap,closed,tableView){d,s->
                                detailDate=d.toString();detailDraw=s.name;detailCustomer=customer.id
                            }
                        }
                    }
                }
                if(list.isNotEmpty()) item {
                    val ids=list.map {it.id}.toSet()
                    val shown=statement.draws.filter {it.customerId in ids}
                    Surface(color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),
                        shape=MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Column {
                                Text(lang.text("စာရင်းစုစုပေါင်း","List total"),fontWeight=FontWeight.Bold)
                                Text(CustomerStatementCalculator.totals(shown).totalBet.moneyLabel(),
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            StatementDue(shown,false)
                        }
                    }
                }
            } else {
                item {
                    PeriodLedger(page,period,session,statement.draws,winnerMap,closed,tableView){d,s->
                        detailDate=d.toString();detailDraw=s.name;detailCustomer=-1L
                    }
                }
            }
        }
    }
    val day=detailDate?.let {runCatching{LocalDate.parse(it)}.getOrNull()}
    val selected=detailDraw?.let {runCatching{DrawSession.valueOf(it)}.getOrNull()}
    if(day!=null&&selected!=null&&statement!=null) {
        val draws=statement.draws.filter {it.date==day&&it.session==selected&&
            (detailCustomer==-1L||it.customerId==detailCustomer)}
        val stakes=statement.stakes.filter {it.date==day&&it.session==selected&&
            (detailCustomer==-1L||it.customerId==detailCustomer)}
        DrawLedgerSheet(day,selected,draws,stakes,
            draws.firstOrNull()?.winner?:winnerMap[day to selected],
            onClose={detailDate=null;detailDraw=null})
    }
}
