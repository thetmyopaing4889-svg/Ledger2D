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
fun TodayLedgerScreen(vm:LedgerViewModel,onBack:()->Unit,onNavigate:(String)->Unit={}){
    val l=com.myanmar.ledger2d.core.design.LocalLanguage.current
    var dateText by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var session by rememberSaveable{mutableStateOf(DrawSession.MORNING)}
    val date=runCatching{LocalDate.parse(dateText)}.getOrElse{LocalDate.now()}
    val agents by vm.agents.collectAsStateWithLifecycle()
    OperationalScaffold(l.translate("ယနေ့စာရင်း"),onBack,content={padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{DateInput(dateText,{dateText=it},"ရက်စွဲ");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)})}}}
            if(agents.isEmpty()) item{EmptyState(l.translate("ဒိုင်မရှိသေးပါ"),l.translate("ဒိုင်ထည့်ပြီးမှ ယနေ့စာရင်းကို ကြည့်နိုင်ပါမည်"))}
            items(agents,key={it.id}){agent->
                val customers by vm.customers(agent.id).collectAsStateWithLifecycle(initialValue=emptyList())
                val totals by vm.agentTotals(agent.id,date,session).collectAsStateWithLifecycle(initialValue=emptyList())
                ElevatedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(agent.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(totals.sumOf{it.amount}.mmk(),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}
                        HorizontalDivider()
                        if(customers.isEmpty())Text("ထိုးသားမရှိသေးပါ",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        else customers.forEach{customer->
                            val entries by vm.customerEntries(customer.id).collectAsStateWithLifecycle(initialValue=emptyList())
                            val amount=entries.filter{it.entry.drawDate==date&&it.entry.drawSession==session}.flatMap{it.lines}.sumOf{it.amount}
                            if(amount>0L)ListItem(headlineContent={Text(customer.name,fontWeight=FontWeight.SemiBold)},supportingContent={Text("${entries.count{it.entry.drawDate==date&&it.entry.drawSession==session}} စာရင်း")},trailingContent={Text(amount.mmk(),fontWeight=FontWeight.Bold)})
                        }
                    }
                }
            }
        }
    },bottomBar={OperationalBottomBar("ledger",onNavigate,{onNavigate("quick")})})
}

@Composable
fun SettlementScreen(vm:LedgerViewModel,onBack:()->Unit,onNavigate:(String)->Unit={}){
    val l=com.myanmar.ledger2d.core.design.LocalLanguage.current
    var dateText by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    var session by rememberSaveable{mutableStateOf(DrawSession.MORNING)}
    val date=runCatching{LocalDate.parse(dateText)}.getOrElse{LocalDate.now()}
    val revision by vm.revision.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val reports by produceState<Map<Long,DrawReport>>(emptyMap(),date,session,revision,agents){value=agents.associate{it.id to vm.agentReport(it.id,date,session,true)}}
    OperationalScaffold(l.translate("ရှင်းတမ်း"),onBack,content={padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{DateInput(dateText,{dateText=it},"ရက်စွဲ");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)})}}}
            if(agents.isEmpty())item{EmptyState(l.translate("ဒိုင်မရှိသေးပါ"),l.translate("ဒိုင်ထည့်ပြီးမှ ရှင်းတမ်းတွက်နိုင်ပါမည်"))}
            items(agents,key={it.id}){agent->
                val report=reports[agent.id]
                val settled by produceState<Boolean?>(null,agent.id,date,session,revision){value=vm.isSettled(agent.id,date,session)}
                report?.let{r->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(agent.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);if(settled==true)Text("ပြီး",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)};SettlementMetric("ထိုးကြေး",r.calculation.totalBet.mmk());SettlementMetric("ပေါက်ကြေး",r.calculation.winningStake.mmk());SettlementMetric("လျော်ပေးငွေ",r.calculation.payout.mmk());SettlementMetric("ကော်မရှင်",r.calculation.commission.mmk());HorizontalDivider();SettlementMetric("Net settlement",r.calculation.netSettlement.mmk(),true);if(settled!=true)Button({vm.settleAgent(agent.id,date,session)},enabled=r.winnerAvailable,modifier=Modifier.fillMaxWidth()){Text(if(r.winnerAvailable)l.translate("ရှင်းတမ်းအတည်ပြုမည်") else "ပေါက်ဂဏန်းမရှိသေးပါ")}}}}
            }
        }
    },bottomBar={OperationalBottomBar("settlement",onNavigate,{onNavigate("quick")})})
}

@Composable private fun SettlementMetric(label:String,value:String,emphasis:Boolean=false){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontWeight=if(emphasis)FontWeight.Black else FontWeight.SemiBold,color=if(emphasis)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}}
