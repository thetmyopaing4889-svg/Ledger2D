@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.design.*
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

fun Long.mmk()="${NumberFormat.getIntegerInstance(Locale.US).format(this)} MMK"
private fun LocalDate.displayDate(): String = "${dayOfMonth}.${monthValue}.${year.toString().takeLast(2)}"
@Composable private fun UiText(my:String, en:String){ Text(if(LocalLanguage.current.code=="en") en else my) }
@Composable private fun BilingualText(text:String, modifier:Modifier=Modifier, primaryStyle:androidx.compose.ui.text.TextStyle=MaterialTheme.typography.titleMedium, secondaryStyle:androidx.compose.ui.text.TextStyle=MaterialTheme.typography.labelMedium, color:Color=MaterialTheme.colorScheme.onSurface){
    val parts=text.split("\n", limit=2)
    val language=LocalLanguage.current
    val value = if(language.code=="en") if(parts.size>1) parts[1] else language.translate(parts.first()) else parts.first()
    Text(value, modifier, style=primaryStyle, color=color)
}
@Composable fun AppScaffold(title:String,onBack:(()->Unit)?=null,action:(@Composable RowScope.() -> Unit)?=null,fab:(@Composable () -> Unit)?=null,content:@Composable (PaddingValues) -> Unit){
    OperationalScaffold(title,onBack,action,fab,content,null)
}
@Composable fun OperationalScaffold(title:String,onBack:(()->Unit)?=null,action:(@Composable RowScope.() -> Unit)?=null,fab:(@Composable () -> Unit)?=null,content:@Composable (PaddingValues) -> Unit,bottomBar:(@Composable () -> Unit)?=null){
    Scaffold(
        containerColor=MaterialTheme.colorScheme.background,
        topBar={
            CenterAlignedTopAppBar(
                colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=MaterialTheme.colorScheme.background),
                title={BilingualText(title, primaryStyle=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.onBackground)},
                navigationIcon={if(onBack!=null){ IconButton(onClick=onBack){ Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription="နောက်သို့") } }},
                actions={action?.invoke(this)}
            )
        },
        floatingActionButton={fab?.invoke()},
        bottomBar={bottomBar?.invoke()},
        content={padding->AnimatedVisibility(visible=true,enter=fadeIn(tween(AppMotion.Medium))+slideInVertically(tween(AppMotion.Medium)){it/12}){content(padding)}}
    )
}
@Composable fun WelcomeScreen(onContinue:()->Unit){
    val l=LocalLanguage.current
    Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background){
        Column(Modifier.fillMaxSize().padding(horizontal=24.dp, vertical=28.dp), verticalArrangement=Arrangement.SpaceBetween){
            Column(Modifier.padding(top=44.dp)){
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Surface(color=MaterialTheme.colorScheme.primary, shape=MaterialTheme.shapes.large, shadowElevation=4.dp){
                        Text("2D", Modifier.padding(horizontal=18.dp, vertical=12.dp), color=MaterialTheme.colorScheme.onPrimary, style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Black)
                    }
                    Column{
                        Text("စာရင်း", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary, fontWeight=FontWeight.Bold)
                        Text("မြန်မာ 2D", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(34.dp))
                Text(l.text("မြန်မာ 2D စာရင်း", "Myanmar 2D Ledger"), style=MaterialTheme.typography.displaySmall, fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(l.text("ဒိုင်၊ ထိုးသား၊ ထိုးကြေးနှင့် ထီပေါက်စဉ်များကို တစ်နေရာတည်းတွင် စီမံပါ", "Manage agents, customers, bets and results in one place"), style=MaterialTheme.typography.bodyLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    WelcomeStat("အော့ဖ်လိုင်း", "လုံခြုံ")
                    WelcomeStat("မြန်ဆန်", "လွယ်ကူ")
                    WelcomeStat("ရှင်းလင်း", "နားလည်လွယ်")
                }
            }
            Button(onClick=onContinue, modifier=Modifier.fillMaxWidth().height(56.dp), shape=MaterialTheme.shapes.medium){
                Text(l.text("စတင်အသုံးပြုမည်", "Get started"), style=MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }
}

@Composable private fun RowScope.WelcomeStat(value:String, label:String){
    Surface(color=MaterialTheme.colorScheme.surfaceVariant, shape=MaterialTheme.shapes.medium, modifier=Modifier.weight(1f)){
        Column(Modifier.padding(vertical=12.dp, horizontal=8.dp), horizontalAlignment=Alignment.CenterHorizontally){
            Text(value, style=MaterialTheme.typography.labelLarge, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
            Text(label, style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun HomeScreen(vm:LedgerViewModel,onQuickEntry:()->Unit,onAgents:()->Unit,onWinning:()->Unit,onClosedDays:()->Unit,onSettings:()->Unit,onLedger:()->Unit,onSettlement:()->Unit,onAddAgent:()->Unit,onAddCustomer:()->Unit,onAgentDashboard:()->Unit,onCustomerDashboard:()->Unit,onNavigate:(String)->Unit={}){
    val l=LocalLanguage.current; val agents by vm.agents.collectAsStateWithLifecycle(); val days by vm.closedDays.collectAsStateWithLifecycle(); val today=LocalDate.now(); val closed=days.any{it.date==today}
    OperationalScaffold("Home",content={p->
        LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Column(Modifier.padding(horizontal=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Welcome to",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold);Text("Myanmar 2D Ledger",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.CalendarMonth,null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(today.displayDate(),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            item{HomeWeeklyResults(vm)}
            item{Button(onClick=onQuickEntry,enabled=!closed,modifier=Modifier.fillMaxWidth().height(58.dp),shape=MaterialTheme.shapes.medium){Icon(Icons.Default.AddCircle,null);Spacer(Modifier.width(10.dp));Text(l.translate("အမြန်စာရင်းသွင်းရန်"),style=MaterialTheme.typography.titleMedium)}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){HomeAction(Icons.Default.PersonAdd,"ဒိုင်အသစ်ထည့်ရန်",onAddAgent,Modifier.weight(1f));HomeAction(Icons.Default.GroupAdd,"ထိုးသားအသစ်ထည့်ရန်",onAddCustomer,Modifier.weight(1f))}}
        }
    },bottomBar={HomeBottomBar(onNavigate,onAgentDashboard,onCustomerDashboard,onClosedDays,onWinning,onSettings)})
}
@Composable private fun HomeWeeklyResults(vm:LedgerViewModel){
    val agents by vm.agents.collectAsStateWithLifecycle()
    val winners by vm.winners.collectAsStateWithLifecycle()
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val monday=LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val winnerMap=winners.associateBy{it.date to it.session}
    val closedSet=closedDays.map{it.date}.toSet()
    val totals by produceState(emptyMap<Pair<LocalDate,DrawSession>,Pair<Long,Long>>(),agents,monday,vm.revision.collectAsStateWithLifecycle().value){
        value=(0L..4L).flatMap { offset -> DrawSession.entries.map { session ->
            val date=monday.plusDays(offset); var stake=0L; var commission=0L
            agents.forEach { agent -> val report=vm.agentReport(agent.id,date,session,true); stake+=report.calculation.totalBet; commission+=report.calculation.commission }
            (date to session) to (stake to commission)
        }}.toMap()
    }
    ElevatedCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text("ယခုတစ်ပတ် ထွက်ဂဏန်းများ",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("မနက် • ညနေ  |  တနင်္လာမှ သောကြာအထိ",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                (0L..4L).forEach { offset ->
                    val date=monday.plusDays(offset)
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant),shape=MaterialTheme.shapes.medium){
                        Column(Modifier.padding(horizontal=9.dp,vertical=5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.CalendarMonth,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(15.dp));Spacer(Modifier.width(5.dp));Text("${date.dayOfMonth}.${date.monthValue}.${date.year}  ${date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH)}",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));if(date in closedSet) Text("ပိတ်ရက်",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)}
                            DrawSession.entries.forEach { session ->
                                val stats=totals[date to session] ?: (0L to 0L)
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                    Surface(Modifier.size(width=45.dp,height=30.dp),shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surface){Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Text(session.label,style=MaterialTheme.typography.labelSmall);Spacer(Modifier.width(3.dp));Text(winnerMap[date to session]?.digit ?: "—",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary)}}
                                    Column(Modifier.weight(1f)){Text("Total ထိုးကြေး",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(stats.first.mmk(),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)}
                                    Column(Modifier.weight(1f)){Text("Total ကော်မရှင်",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(stats.second.mmk(),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun HomeBottomBar(onNavigate:(String)->Unit,onAgentDashboard:()->Unit,onCustomerDashboard:()->Unit,onClosedDays:()->Unit,onWinning:()->Unit,onSettings:()->Unit){
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
        NavigationBarItem(true,{onNavigate("home")},icon={Icon(Icons.Default.Home,null)},label={Text("Home")})
        NavigationBarItem(false,onAgentDashboard,icon={Icon(Icons.Default.Store,null)},label={Text("Agent")})
        NavigationBarItem(false,onCustomerDashboard,icon={Icon(Icons.Default.People,null)},label={Text("Customer")})
        NavigationBarItem(false,onClosedDays,icon={Icon(Icons.Default.EventBusy,null)},label={Text("ပိတ်ရက်")})
        NavigationBarItem(false,onWinning,icon={Icon(Icons.Default.EmojiEvents,null)},label={Text("ထီပေါက်စဉ်")})
        NavigationBarItem(false,onSettings,icon={Icon(Icons.Default.Settings,null)},label={Text("ဆက်တင်")})
    }
}
@Composable private fun HomeMetric(label:String,value:String,modifier:Modifier=Modifier){Surface(modifier,color=MaterialTheme.colorScheme.surface.copy(alpha=.72f),shape=MaterialTheme.shapes.medium){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}}}
@Composable private fun HomeAction(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,onClick:()->Unit,modifier:Modifier=Modifier){ElevatedCard(onClick=onClick,modifier=modifier,shape=MaterialTheme.shapes.large){Column(Modifier.fillMaxWidth().padding(vertical=16.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center)}}}
@Composable fun QuickEntryScreen(vm:LedgerViewModel,onBet:(Long,Long)->Unit,onBack:()->Unit){
    val l=LocalLanguage.current; val agents by vm.agents.collectAsStateWithLifecycle(); var agentId by rememberSaveable{mutableStateOf(0L)}; var customerId by rememberSaveable{mutableStateOf(0L)}; val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue=emptyList())
    LaunchedEffect(agentId){customerId=0L}
    AppScaffold(l.translate("အမြန်စာရင်းသွင်းရန်"),onBack){p->Column(Modifier.fillMaxSize().padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(16.dp)){Text(l.translate("အရင် ဒိုင်ကိုရွေးပါ။ ရွေးထားသောဒိုင်အောက်က ထိုးသားများပဲ ပြပါမည်။"),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);SelectionField(l.translate("ဒိုင်ရွေးပါ"),agents.firstOrNull{it.id==agentId}?.name?:l.translate("ဒိုင်မရွေးရသေးပါ"),agents.map{it.id to "${it.name} • ${it.rate} ဆ"},agentId,true){agentId=it};SelectionField(l.translate("ထိုးသားရွေးပါ"),if(agentId==0L)l.translate("ဒိုင်ရွေးပြီးမှ ထိုးသားရွေးပါ") else customers.firstOrNull{it.id==customerId}?.name?:l.translate("ထိုးသားရွေးပါ"),customers.map{it.id to it.name},customerId,agentId>0){customerId=it};if(agentId>0&&customerId>0)Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium){Text("${agents.firstOrNull{it.id==agentId}?.name} • ${customers.firstOrNull{it.id==customerId}?.name} • မနက်",Modifier.fillMaxWidth().padding(14.dp),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)};Spacer(Modifier.weight(1f));Button(onClick={onBet(agentId,customerId)},enabled=agentId>0&&customerId>0,modifier=Modifier.fillMaxWidth().height(56.dp),shape=MaterialTheme.shapes.medium){Text(l.translate("အကွက်နှင့် ထိုးကြေးထည့်ရန်"),style=MaterialTheme.typography.titleMedium);Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null)}}}
}
@Composable private fun SelectionField(label:String,selected:String,options:List<Pair<Long,String>>,value:Long,enabled:Boolean=true,onSelect:(Long)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded=expanded&&enabled,onExpandedChange={if(enabled)expanded=!expanded}){OutlinedTextField(selected,{},Modifier.fillMaxWidth().menuAnchor(),enabled=enabled,readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded&&enabled)},shape=MaterialTheme.shapes.medium);ExposedDropdownMenu(expanded=expanded&&enabled,onDismissRequest={expanded=false}){options.forEach{(id,title)->DropdownMenuItem(text={Text(title)},onClick={onSelect(id);expanded=false})}}}}
@Composable fun AgentListScreen(vm:LedgerViewModel,onAgent:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit,onWinning:()->Unit,onClosedDays:()->Unit,onSettings:()->Unit){
    val agents by vm.agents.collectAsStateWithLifecycle()
    AppScaffold("ဒိုင်များ", action={
        IconButton(onClick=onSettings){Icon(Icons.Default.Settings, "ဆက်တင်များ")}
    }, fab={ExtendedFloatingActionButton(onClick=onAdd, modifier=Modifier.padding(horizontal=8.dp, vertical=8.dp), shape=MaterialTheme.shapes.medium){Icon(Icons.Default.Add, null);Spacer(Modifier.width(8.dp));Text("ဒိုင်အသစ်ထည့်ရန်", style=MaterialTheme.typography.labelLarge)}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding=PaddingValues(horizontal=16.dp, vertical=10.dp), verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Column(Modifier.padding(horizontal=4.dp)){Text("လုပ်ငန်းအခြေအနေ", style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold);Text("ဒိုင်များနှင့် စာရင်းများကို စီမံပါ", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            if(agents.isEmpty()) item{Surface(color=MaterialTheme.colorScheme.surface, shape=MaterialTheme.shapes.large, tonalElevation=2.dp){EmptyState("ဒိုင်မရှိသေးပါ", "အောက်က ခလုတ်ကိုနှိပ်၍ ပထမဆုံးဒိုင်ကို စတင်ထည့်ပါ")}} else {
                item{Text("ဒိုင်စာရင်း", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)}
                items(agents,key={it.id}){agent->ElevatedCard(onClick={onAgent(agent.id)}, modifier=Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large){ListItem(headlineContent={Text(agent.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}, supportingContent={Text("နှုန်းထား ${agent.rate}" + if(agent.phone.isNotBlank()) "  •  ${agent.phone}" else "",style=MaterialTheme.typography.bodySmall)}, leadingContent={Surface(color=MaterialTheme.colorScheme.primaryContainer, shape=MaterialTheme.shapes.medium){Icon(Icons.Default.Store, null, tint=MaterialTheme.colorScheme.primary, modifier=Modifier.padding(10.dp))}}, trailingContent={IconButton({onEdit(agent.id)}){Icon(Icons.Default.Edit, "ပြင်မည်")}})}}
            }
            item{Spacer(Modifier.height(84.dp))}
        }
    }
}
@Composable fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=52.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center);Spacer(Modifier.height(8.dp));Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)}}
@Composable fun AgentFormScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val existing by vm.agent(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var rate by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;rate=it.rate.toString();remark=it.remark}};AppScaffold(if(id==0L)"ဒိုင်အသစ်ထည့်ရန်" else "ဒိုင်အချက်အလက်ပြင်ရန်",onBack){p->FormColumn(p){Input(name,{name=it},"အမည်",true);Input(address,{address=it},"လိပ်စာ");Input(phone,{phone=it},"ဖုန်း",keyboard=KeyboardType.Phone);Input(rate,{rate=it},"နှုန်းထား",true,KeyboardType.Number);Input(remark,{remark=it},"မှတ်ချက်");FormActions(onBack,{vm.saveAgent(id,name,address,phone,rate,remark,onBack)},name.isNotBlank()&&rate.toLongOrNull()?.let{it>0}==true)}}}
@Composable fun CustomerFormScreen(vm:LedgerViewModel,id:Long,agentId:Long,onBack:()->Unit){val existing by vm.customer(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;remark=it.remark}};AppScaffold(if(id==0L)"ထိုးသားအသစ်ထည့်ရန်" else "ထိုးသားအချက်အလက်ပြင်ရန်",onBack){p->FormColumn(p){Input(name,{name=it},"အမည်",true);Input(address,{address=it},"လိပ်စာ");Input(phone,{phone=it},"ဖုန်း",keyboard=KeyboardType.Phone);Input(remark,{remark=it},"မှတ်ချက်");FormActions(onBack,{vm.saveCustomer(id,agentId,name,address,phone,remark,onBack)},name.isNotBlank())}}}
@Composable private fun FormColumn(p:PaddingValues,content:@Composable ColumnScope.() -> Unit)=Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
@Composable fun Input(value:String,onValue:(String)->Unit,label:String,required:Boolean=false,keyboard:KeyboardType=KeyboardType.Text){val display=LocalLanguage.current.translate(label);OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(display+(if(required)" *" else ""))},singleLine=label!="မှတ်ချက်",keyboardOptions=KeyboardOptions(keyboardType=keyboard),shape=MaterialTheme.shapes.medium)}
@Composable fun DateInput(value:String,onValue:(String)->Unit,label:String,enabled:Boolean=true){
    var open by rememberSaveable{mutableStateOf(false)}
    val display=runCatching{LocalDate.parse(value).displayDate()}.getOrElse{value}
    OutlinedTextField(value=display,onValueChange={},modifier=Modifier.fillMaxWidth(),label={Text(label)},readOnly=true,enabled=enabled,trailingIcon={if(enabled)TextButton({open=true}){Text("ရွေး")}},shape=MaterialTheme.shapes.medium)
    if(open){val initial=runCatching{LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull();val state=rememberDatePickerState(initialSelectedDateMillis=initial);DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{onValue(java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString())};open=false}){Text("ရွေးမည်")}},dismissButton={TextButton(onClick={open=false}){Text("မလုပ်ပါ")}}){DatePicker(state)}}
}
@Composable private fun FormActions(cancel:()->Unit,save:()->Unit,enabled:Boolean){val l=LocalLanguage.current;Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(cancel,Modifier.weight(1f)){Text(l.text("မလုပ်တော့ပါ","Cancel"))};Button(save,Modifier.weight(1f),enabled=enabled){Text(l.text("သိမ်းမည်","Save"))}}}
@Composable fun AgentDetailScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onRoute: (String) -> Unit) {
    val agent by vm.agent(id).collectAsState(initial = null); val profile=agent ?: return
    AppScaffold(profile.name,onBack,action={IconButton({onRoute("agentForm/$id")}){Icon(Icons.Default.Edit,"ပြင်ရန်")}}){p->
        LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Surface(color=MaterialTheme.colorScheme.primary,shape=MaterialTheme.shapes.medium){Icon(Icons.Default.Store,null,tint=MaterialTheme.colorScheme.onPrimary,modifier=Modifier.padding(14.dp))};Column{Text(profile.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("လျော်ကြေးနှုန်းထား ${profile.rate}",style=MaterialTheme.typography.bodyMedium);if(profile.phone.isNotBlank())Text(profile.phone,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
            item{Button(onClick={onRoute("customers/$id")},modifier=Modifier.fillMaxWidth().height(54.dp),shape=MaterialTheme.shapes.medium){Icon(Icons.Default.People,null);Spacer(Modifier.width(10.dp));Text("ထိုးသားများ",style=MaterialTheme.typography.titleMedium)}}
            item{Text("စီမံခန့်ခွဲရန်",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.ReceiptLong,"စုစုပေါင်းစာရင်း","total/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.Assessment,"အစီရင်ခံစာ","report/agent/$id",onRoute)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.Lock,"ပိတ်ဂဏန်း","closed/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.EmojiEvents,"ထီပေါက်စဉ်","winning/agent/$id",onRoute)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.Tune,"ကန့်သတ်ပမာဏ","agentLimit/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.MenuBook,"လမ်းညွှန်","format",onRoute)}}}
            item{Spacer(Modifier.height(16.dp))}
        }
    }
}
@Composable private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title:String, route:String, onRoute:(String)->Unit) {
    val interaction=remember{MutableInteractionSource()}; val pressed by interaction.collectIsPressedAsState(); val scale by animateFloatAsState(if(pressed) .965f else 1f, tween(AppMotion.Short), label="actionTileScale")
    ElevatedCard(onClick={onRoute(route)}, interactionSource=interaction, modifier=Modifier.fillMaxWidth().graphicsLayer{scaleX=scale;scaleY=scale}, shape=MaterialTheme.shapes.large, elevation=CardDefaults.elevatedCardElevation(defaultElevation=AppDimens.cardElevation, pressedElevation=AppDimens.featuredElevation)){
        Column(Modifier.fillMaxWidth().padding(vertical=16.dp,horizontal=10.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(shape=MaterialTheme.shapes.medium, color=MaterialTheme.colorScheme.secondaryContainer){Box(Modifier.size(46.dp),contentAlignment=Alignment.Center){Icon(icon, null, tint=MaterialTheme.colorScheme.secondary)}}
            Text(title, style=MaterialTheme.typography.labelLarge, textAlign=TextAlign.Center, maxLines=2)
        }
    }
}
@Composable fun CustomerListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit,onCustomer:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit){
    val list by vm.customers(agentId).collectAsState(initial=emptyList())
    AppScaffold("ထိုးသားများ",onBack,fab={ExtendedFloatingActionButton(onClick=onAdd){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("ထိုးသားအသစ်ထည့်ရန်")}}){padding->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("ဒီအေးဂျင့်အောက်ရှိ ထိုးသားများ",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(list.isEmpty())item{EmptyState("ထိုးသားမရှိသေးပါ","အောက်က + ခလုတ်ကိုနှိပ်၍ စတင်ပါ")} else items(list,key={it.id}){customer->ListItem(headlineContent={Text(customer.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}, supportingContent={if(customer.phone.isNotBlank()) Text(customer.phone,style=MaterialTheme.typography.bodySmall)}, leadingContent={Icon(Icons.Default.Person, null, tint=MaterialTheme.colorScheme.primary)}, trailingContent={IconButton({onEdit(customer.id)}){Icon(Icons.Default.Edit, "ပြင်မည်")}}, modifier=Modifier.fillMaxWidth().clickable{onCustomer(customer.id)})}
    }}
}
@Composable fun CustomerDetailScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onRoute: (String) -> Unit) {
    val customer by vm.customer(id).collectAsState(initial=null); val profile=customer ?: return
    AppScaffold(profile.name,onBack,action={IconButton({onRoute("customerForm/${profile.agentId}/$id")}){Icon(Icons.Default.Edit,"ပြင်ရန်")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{ElevatedCard(shape=MaterialTheme.shapes.large){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(profile.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("ဖောက်သည်အချက်အလက်",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);if(profile.phone.isNotBlank())Text(profile.phone,style=MaterialTheme.typography.bodyMedium);if(profile.address.isNotBlank())Text(profile.address,color=MaterialTheme.colorScheme.onSurfaceVariant);if(profile.remark.isNotBlank())Text(profile.remark,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            item{Button(onClick={onRoute("bet/${profile.agentId}/$id")},modifier=Modifier.fillMaxWidth().height(56.dp),shape=MaterialTheme.shapes.medium){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("စာရင်းသွင်းရန်",style=MaterialTheme.typography.titleMedium)}}
            item{Text("လုပ်ငန်းဆောင်တာများ",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.ReceiptLong,"စာရင်းမှတ်တမ်း","betHistory/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.Insights,"အမြန်သုံးသပ်ချက်","analysis/$id",onRoute)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.Assessment,"အစီရင်ခံစာ","report/customer/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.Tune,"ကန့်သတ်ပမာဏ","limit/$id",onRoute)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.GridView,"အကွက်စာရင်း","digitList/${profile.agentId}/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.Percent,"ကော်မရှင်","commission/$id",onRoute)}}}
            item{ActionTile(Icons.Default.EmojiEvents,"ထီပေါက်စဉ်","winning/customer/$id",onRoute)}
            item{Spacer(Modifier.height(16.dp))}
        }
    }
}
@Composable
fun BettingScreen(vm: LedgerViewModel, agentId: Long, customerId: Long, onBack: () -> Unit, entryId: Long = 0L) {
    val nextDraw = remember { DrawSchedule.nextDraw(LocalDateTime.now()) }
    var dateText by rememberSaveable(entryId) { mutableStateOf(nextDraw.date.toString()) }
    var session by rememberSaveable(entryId) { mutableStateOf(nextDraw.session) }
    var raw by rememberSaveable(entryId) { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(QuickFormat.MANUAL) }
    var backdated by rememberSaveable(entryId) { mutableStateOf(entryId > 0L) }
    var showLateError by rememberSaveable { mutableStateOf(false) }
    var receiptId by rememberSaveable { mutableStateOf(0L) }
    val existing by vm.betEntry(entryId).collectAsStateWithLifecycle(initialValue = null)
    val preview by vm.preview.collectAsStateWithLifecycle()
    val submit by vm.submit.collectAsStateWithLifecycle()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val now = LocalDateTime.now()
    val pastDraw = date?.let {
        it.isBefore(now.toLocalDate()) ||
            (it == now.toLocalDate() && session == DrawSession.MORNING && !now.toLocalTime().isBefore(DrawSchedule.morningResultTime))
    } ?: false
    val visiblePreview = if (pastDraw && !backdated) null else preview
    LaunchedEffect(existing) { existing?.let { backdated = true; dateText = it.entry.drawDate.toString(); session = it.entry.drawSession; raw = it.entry.sourceText; format = runCatching { QuickFormat.valueOf(it.entry.inputFormat) }.getOrDefault(QuickFormat.MANUAL) } }
    LaunchedEffect(raw, format, date, session, backdated) { date?.let { vm.refreshPreview(customerId, agentId, it, session, raw, format, entryId) } }
    LaunchedEffect(submit) { if (submit is SubmitState.Success) { receiptId=(submit as SubmitState.Success).id; vm.resetSubmit() } }
    if (showLateError) AlertDialog(
        onDismissRequest = { showLateError = false },
        title = { Text("စာရင်းသွင်း၍ မရပါ") },
        text = { Text("သင်ထည့်သွင်းသော အချက်အလက်သည် ပေါက်ဂဏန်းထွက်ပြီးဖြစ်ပါသဖြင့် ထည့်သွင်း၍မရနိုင်ပါ။ အကယ်၍ ထည့်သွင်းရန်လိုအပ်ပါက နောက်ကြောင်းပြန်စာရင်းသွင်းခြင်းကို အမှန်ခြစ်နှိပ်၍ ဖြည့်သွင်းနိုင်ပါသည်။") },
        confirmButton = { TextButton({ showLateError = false }) { Text("OK") } },
        dismissButton = { TextButton({ showLateError = false }) { Text("မလုပ်တော့ပါ") } }
    )
    if (receiptId>0L) AlertDialog(onDismissRequest={receiptId=0L;onBack()},title={Text("စာရင်းသွင်းပြီးပါပြီ")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("ဒိုင် • ထိုးသား • ${session.label}",fontWeight=FontWeight.Bold);Text("ရက်စွဲ • $dateText");Text("စာရင်းစုစုပေါင်း • " + ((preview.parse as? ParseResult.Success)?.total?.mmk() ?: "-"));Text("Entry #$receiptId",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},confirmButton={TextButton({receiptId=0L;raw=""}){Text("ဒီထိုးသားအတွက် ဆက်သွင်းရန်")}},dismissButton={TextButton({receiptId=0L;onBack()}){Text("ပြီးပါပြီ")}})
    AppScaffold(if (entryId == 0L) "2D Form\nစာရင်းသွင်းရန်" else "2D Form\nစာရင်းပြင်ရန်", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { DateInput(dateText, { dateText = it }, "ရက်စွဲ") }
                item { Text("ထိုးမည့်အချိန်", style = MaterialTheme.typography.titleLarge); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { DrawSession.entries.forEachIndexed { i, draw -> SegmentedButton(session == draw, { session = draw }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(draw.label) } } } }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = backdated, onCheckedChange = { backdated = it })
                        Column { Text("နောက်ကြောင်းပြန်စာရင်းသွင်းခြင်း", fontWeight = FontWeight.SemiBold); Text("အချိန်လွန်စာရင်းသွင်းခြင်း", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                item { OutlinedTextField(raw, { raw = it }, Modifier.fillMaxWidth().heightIn(min = 130.dp), label = { Text("စာရင်းထည့်ရန်") }, placeholder = { Text(if (format == QuickFormat.MANUAL) "ဥပမာ 10.13.14 100" else "ပုံစံအတိုင်း ထည့်ပါ") }, shape = MaterialTheme.shapes.medium) }
                item { Text("အမြန်ထည့်သွင်းပုံများ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickFormat.entries.forEach { f -> FilterChip(format == f, { format = f }, label = { Text(f.label) }) } } }
                item { if (visiblePreview == null && pastDraw && !backdated) UnavailableState("ဂဏန်းထွက်ပြီးချိန်ဖြစ်ပါသဖြင့် Preview မပြနိုင်သေးပါ။\nနောက်ကြောင်းပြန်စာရင်းသွင်းခြင်းကို အမှန်ခြစ်ပါ။") else PreviewCard(visiblePreview ?: BetPreview()) }
                item { (submit as? SubmitState.Error)?.let { UnavailableState(it.message) } }
            }
            Surface(shadowElevation = 10.dp) {
                Button(onClick = { if (pastDraw && !backdated) showLateError = true else date?.let { if (entryId == 0L) vm.confirm(customerId, agentId, it, session, raw, format) else existing?.let { item -> vm.editConfirm(item, it, session, raw, format) } } }, modifier = Modifier.fillMaxWidth().padding(AppDimens.screen).height(54.dp), enabled = (pastDraw && !backdated && raw.isNotBlank()) || (!pastDraw || backdated) && preview.canConfirm && submit !is SubmitState.Working) { Text(if (submit is SubmitState.Working) "အတည်ပြုနေသည်…" else "Confirm") }
            }
        }
    }
}
@Composable private fun PreviewCard(preview:BetPreview){
    ElevatedCard{
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Text("အကြိုကြည့်ရှုရန်",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                when(val parsed=preview.parse){
                    is ParseResult.Error -> Text("မပြည့်စုံသေးပါ",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium)
                    is ParseResult.Success -> Text("${parsed.bets.size} ကွက် • ${parsed.total.mmk()}",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                }
            }
            when(val parsed=preview.parse){
                is ParseResult.Error -> Text(previewError(parsed.message),color=MaterialTheme.colorScheme.error)
                is ParseResult.Success -> {
                    if(preview.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    preview.validation?.rows?.let { rows ->
                        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp),maxItemsInEachRow=2){
                            rows.forEach{r->
                                val bad=r.isClosed||r.exceedsLimit
                                Surface(modifier=Modifier.fillMaxWidth(.487f),color=if(bad)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.small){
                                    Column(Modifier.padding(horizontal=9.dp,vertical=7.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                                            Text(r.digit,fontWeight=FontWeight.Black)
                                            Text(if(r.isClosed)"ပိတ်" else if(r.exceedsLimit)"ကျော်" else "အဆင်ပြေ",color=if(bad)MaterialTheme.colorScheme.error else AppColors.Success,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                                        }
                                        Text("လက်ရှိ ${r.currentTotal} • ယခု ${r.thisInput}",style=MaterialTheme.typography.bodySmall)
                                        Text("ကန့်သတ် ${r.limit?.toString()?:"မရှိ"} • ပြီးနောက် ${r.afterInput}",style=MaterialTheme.typography.labelSmall)
                                        r.remaining?.let{Text("ကျန် ${it}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun previewError(message:String):String = when{
    message.contains("Input is required",ignoreCase=true) -> "ထည့်သွင်းရန် လိုအပ်ပါသည်"
    message.contains("positive MMK integer",ignoreCase=true) -> "ငွေပမာဏကို အပေါင်းကိန်းပြည့်ဖြင့် ထည့်ပါ"
    message.contains("Digit must be exactly",ignoreCase=true) -> "ဂဏန်းကို 00 မှ 99 အတွင်း ထည့်ပါ"
    message.contains("ဂဏန်းနှင့် ပမာဏ ထည့်ပါ",ignoreCase=true) -> "ဂဏန်းနှင့် ငွေပမာဏ ထည့်ပါ"
    message.contains("Closed Day",ignoreCase=true) -> "ပိတ်ရက်ဖြစ်သောကြောင့် စာရင်းသွင်း၍ မရပါ"
    else -> message
}

@Composable fun DigitListScreen(vm:LedgerViewModel,agentId:Long,customerId:Long,onBack:()->Unit){var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }; var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }; val totals by vm.customerTotals(customerId,selectedDate,session).collectAsState(initial=emptyList());val all by vm.allLimit(customerId).collectAsState(initial=null);val special by vm.specialLimits(customerId).collectAsState(initial=emptyList());val closed by vm.closedNumbers(agentId).collectAsState(initial=emptyList());val amounts=totals.associate{it.digit to it.amount};val specialMap=special.associate{it.digit to it.amount};val closedSet=closed.map{it.digit}.toSet();AppScaffold("အကွက်စာရင်း",onBack){p->LazyVerticalGrid(GridCells.Fixed(5),Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){item(span={GridItemSpan(5)}){Column{DateInput(dateText,{dateText=it},"ရက်စွဲ");Row{DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)},modifier=Modifier.padding(end=8.dp))}}}};item(span={GridItemSpan(5)}){Text("ကန့်သတ်ချက်: ${all?.amount?:"မရှိ"}",Modifier.padding(10.dp),style=MaterialTheme.typography.titleLarge)};items((0..99).toList()){n->val digit=n.toString().padStart(2,'0');val isClosed=digit in closedSet;val isSpecial=digit in specialMap;Surface(color=when{isClosed->MaterialTheme.colorScheme.errorContainer;isSpecial->MaterialTheme.colorScheme.secondaryContainer;else->MaterialTheme.colorScheme.surfaceVariant},shape=MaterialTheme.shapes.small){Column(Modifier.aspectRatio(.84f).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(digit,fontWeight=FontWeight.Black);Text((amounts[digit]?:0L).mmk(),style=MaterialTheme.typography.labelSmall);if(isClosed)Text("ပိတ်",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error) else if(isSpecial)Text("အထူး ${specialMap[digit]!!.mmk()}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)}}}}}}
@Composable fun TotalListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit){var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }; var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }; val totals by vm.agentTotals(agentId,selectedDate,session).collectAsState(initial=emptyList()); AppScaffold("စုစုပေါင်းစာရင်း",onBack){p->Column(Modifier.fillMaxSize().padding(p)){DateInput(dateText,{dateText=it},"ရက်စွဲ");Row(Modifier.padding(horizontal=AppDimens.screen),horizontalArrangement=Arrangement.spacedBy(8.dp)){DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)})}};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("${selectedDate.displayDate()} • ${session.label}",style=MaterialTheme.typography.headlineSmall)};if(totals.isEmpty())item{Text("ဒီအချိန်အတွက် အတည်ပြုထားသော စာရင်းမရှိသေးပါ",color=MaterialTheme.colorScheme.onSurfaceVariant)}else items(totals){row->OutlinedCard{Text("${row.digit}   ${row.amount.mmk()}",Modifier.fillMaxWidth().padding(16.dp))}}}}}}
@Composable fun ClosedNumberScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit){
    val numbers by vm.closedNumbers(agentId).collectAsStateWithLifecycle(initialValue=emptyList())
    var digit by rememberSaveable{mutableStateOf("")}
    var pendingDelete by remember{mutableStateOf<ClosedNumberEntity?>(null)}
    pendingDelete?.let { value -> AlertDialog(onDismissRequest={pendingDelete=null},title={Text("ပိတ်ဂဏန်းဖျက်မည်လား")},text={Text("${value.digit} ကို ဒီဒိုင်အောက်က ထိုးသားများအားလုံးအတွက် ပြန်ဖွင့်မည်လား?")},confirmButton={TextButton({vm.removeClosedNumber(value);pendingDelete=null}){Text("ဖျက်မည်")}},dismissButton={TextButton({pendingDelete=null}){Text("မလုပ်ပါ")}}) }
    AppScaffold("ပိတ်ဂဏန်း",onBack){p->Column(Modifier.fillMaxSize().padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("ဒိုင်အောက်ရှိ ထိုးသားအားလုံးအတွက် မလက်ခံမည့်ဂဏန်းများ",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(digit,{digit=it.filter{ch->ch in '0'..'9'}.take(2)},Modifier.weight(1f),label={Text("ဂဏန်း 00–99")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=MaterialTheme.shapes.medium);Button({vm.addClosedNumber(agentId,digit);digit=""},enabled=BetParser.validDigit(digit),shape=MaterialTheme.shapes.medium){Text("ပိတ်မည်")}}
        if(numbers.isEmpty()) EmptyState("ပိတ်ဂဏန်း မရှိသေးပါ","ဂဏန်းထည့်လိုက်သည်နှင့် ဒီဒိုင်အောက်ရှိ ထိုးသားများအားလုံးအတွက် ပိတ်သွားပါမည်") else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(numbers,key={it.id}){n->ElevatedCard(shape=MaterialTheme.shapes.medium){ListItem(headlineContent={Text(n.digit,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge)},supportingContent={Text("ဒီဒိုင်အောက်ရှိ ထိုးသားအားလုံးအတွက် ပိတ်ထားသည်")},trailingContent={TextButton({pendingDelete=n}){Text("ဖယ်ရှားမည်")}})}}}
    }}
}
@Composable fun FormatScreen(onBack:()->Unit){val rows=listOf("ပါဝါ" to "05 50 16 61 27 72 38 83 49 94","နက္ခတ်" to "07 70 18 81 24 42 35 53 69 96","အပူး" to "00 11 22 33 44 55 66 77 88 99","ညီအကို" to "20 နှင့် ဆက်စပ်အတွဲဂဏန်းများ","အခွေ" to "345.100 → 34 43 45 54 35 53","အခွေပူး" to "အခွေ နှင့် အပူးဂဏန်းများ","ပတ်သီး" to "9.100 → 19 unique numbers","ထိပ်စည်း" to "9.100 → 90…99","နောက်ပိတ်" to "9.100 → 09…99","R / ပြောင်းပြန်" to "10.20R100 → both directions");AppScaffold("ထည့်သွင်းပုံ",onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("ထည့်သွင်းပုံ လမ်းညွှန်",style=MaterialTheme.typography.headlineSmall)};items(rows){(title,body)->OutlinedCard{Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}}
@Composable fun CommissionScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val c by vm.customer(id).collectAsState(initial=null);var value by rememberSaveable{mutableStateOf("")};LaunchedEffect(c){c?.let{value=(it.commissionRateBasisPoints/100.0).toString().removeSuffix(".0")}};AppScaffold("ကော်မရှင်နှုန်းထား",onBack){p->FormColumn(p){Text("ဒီထိုးသားအတွက် ကော်မရှင်ရာခိုင်နှုန်းကို သတ်မှတ်ပါ",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Input(value,{value=it.filter{ch->ch.isDigit()||ch=='.'}.take(6)},"ရာခိုင်နှုန်း",true,KeyboardType.Decimal);Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.medium){Text("ဥပမာ 15 ဆိုလျှင် 15% ဖြစ်ပါသည်။ ငွေတွက်ချက်မှုကို တိကျသော basis-point စနစ်ဖြင့် ပြုလုပ်ပါသည်။",Modifier.padding(14.dp),color=MaterialTheme.colorScheme.onSecondaryContainer,style=MaterialTheme.typography.bodySmall)};FormActions(onBack,{vm.updateCommission(id,value,onBack)},value.toBigDecimalOrNull()?.let{it>=java.math.BigDecimal.ZERO&&it<=java.math.BigDecimal(100)}==true)}}}
@Composable fun LimitScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val all by vm.allLimit(id).collectAsState(initial=null);val specials by vm.specialLimits(id).collectAsState(initial=emptyList());var allText by rememberSaveable{mutableStateOf("")};var digit by rememberSaveable{mutableStateOf("")};var amount by rememberSaveable{mutableStateOf("")};var pendingSpecial by remember{mutableStateOf<SpecialLimitEntity?>(null)};LaunchedEffect(all){allText=all?.amount?.toString()?:""};pendingSpecial?.let{value->AlertDialog(onDismissRequest={pendingSpecial=null},title={Text("အထူးကန့်သတ်ချက်ဖျက်မည်လား")},text={Text("${value.digit} အတွက် ${value.amount.mmk()} ကန့်သတ်ချက်ကို ဖျက်မည်လား?")},confirmButton={TextButton({vm.removeSpecialLimit(value);pendingSpecial=null}){Text("ဖျက်မည်")}},dismissButton={TextButton({pendingSpecial=null}){Text("မလုပ်ပါ")}})};AppScaffold("ကန့်သတ်ပမာဏ",onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("ကန့်သတ်ပမာဏ\nအားလုံးအတွက် ကန့်သတ်ချက်",style=MaterialTheme.typography.titleLarge);Input(allText,{allText=it},"မထည့်ပါက အားလုံးအတွက် ကန့်သတ်ချက်မရှိပါ",keyboard=KeyboardType.Number);Button({vm.updateAllLimit(id,allText){}}){Text("သိမ်းမည်")}};item{HorizontalDivider();Text("ရွေးချယ်ထားသောဂဏန်းအတွက် ကန့်သတ်ချက်",style=MaterialTheme.typography.titleLarge);Input(digit,{digit=it.take(2)},"ဂဏန်း",keyboard=KeyboardType.Number);Input(amount,{amount=it},"ပမာဏ",keyboard=KeyboardType.Number);Button({vm.addSpecialLimit(id,digit,amount);digit="";amount=""},enabled=BetParser.validDigit(digit)&&amount.toLongOrNull()?.let{it>0}==true){Text("သိမ်းမည်")}};items(specials){limit->Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.shapes.small).padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("${limit.digit}  →  ${limit.amount.mmk()}");TextButton({pendingSpecial=limit}){Text("ဖယ်ရှားမည်")}}}}}}
@Composable
fun WinningNumberScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val winners by vm.winners.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    var digit by rememberSaveable { mutableStateOf("") }
    var showForm by rememberSaveable { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<WinningNumberEntity?>(null) }
    var notice by rememberSaveable { mutableStateOf("") }
    val existing = winners.firstOrNull { it.date.toString() == date && it.session == session }

    pendingDelete?.let { winner ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("ပေါက်ဂဏန်းဖျက်မည်လား") },
            text = { Text("${winner.date} ${winner.session.label} မှ ${winner.digit} ကို ဖျက်မလား?") },
            confirmButton = { TextButton(onClick = { vm.deleteWinner(winner); pendingDelete = null }) { Text("ဖျက်မည်") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("မလုပ်ပါ") } },
        )
    }

    AppScaffold("ထီပေါက်စဉ်", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ထီပေါက်စဉ်", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("ရက်စွဲနှင့် အချိန်အလိုက် ရလဒ်များကို စီမံပါ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(showForm, { showForm = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("ပေါက်ဂဏန်းထည့်ရန်") }
                    SegmentedButton(!showForm, { showForm = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("ထီပေါက်စဉ်ကြည့်ရန်") }
                }
            }
            if (showForm) {
                item {
                    ElevatedCard {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Text(if (existing == null) "ပေါက်ဂဏန်းအသစ်" else "ပေါက်ဂဏန်းပြင်ရန်", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            DateInput(date, { date = it }, "ရက်စွဲ")
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                DrawSession.entries.forEachIndexed { index, draw ->
                                    SegmentedButton(session == draw, { session = draw }, SegmentedButtonDefaults.itemShape(index, 2)) { Text(draw.label) }
                                }
                            }
                            if (existing != null) {
                                Text("${existing.date} ${existing.session.label} • လက်ရှိ ${existing.digit}", color = MaterialTheme.colorScheme.primary)
                                Text("ပေါက်ဂဏန်းအသစ် ထည့်ပြီး ပြင်ဆင်ပါ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Input(digit, { digit = it.filter { ch -> ch in '0'..'9' }.take(2) }, "ပေါက်ဂဏန်း 00–99", true, KeyboardType.Number)
                            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton({ digit = "" }, Modifier.weight(1f)) { Text("ရှင်းမည်") }
                                Button(
                                    onClick = {
                                        runCatching { LocalDate.parse(date) }.getOrNull()?.let { parsedDate ->
                                            if (existing == null) vm.saveWinner(parsedDate, session, digit) { digit = ""; notice = "သိမ်းပြီးပါပြီ" }
                                            else vm.updateWinner(existing, digit) { digit = ""; notice = "ပြင်ဆင်ပြီးပါပြီ" }
                                        }
                                    },
                                    enabled = BetParser.validDigit(digit),
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (existing == null) "သိမ်းမည်" else "ပြင်ဆင်မည်") }
                            }
                        }
                    }
                }
            } else if (winners.isEmpty()) {
                item { EmptyState("မှတ်တမ်းမရှိသေးပါ", "အသစ်ထည့်ရန်မှ ပေါက်ဂဏန်းကို ထည့်ပါ") }
            } else {
                winners.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, dayWinners) ->
                    item {
                        ElevatedCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(day.displayDate(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                DrawSession.entries.forEach { draw ->
                                    val winner = dayWinners.firstOrNull { it.session == draw }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(draw.label, fontWeight = FontWeight.SemiBold)
                                            Text(if (winner == null) "မထည့်ရသေးပါ" else "ပေါက်ဂဏန်း ${winner.digit}", color = if (winner == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                                        }
                                        if (winner != null) {
                                            TextButton({ date = winner.date.toString(); session = winner.session; digit = winner.digit; showForm = true }) { Text("ပြင်မည်") }
                                            TextButton({ pendingDelete = winner }) { Text("ဖျက်မည်") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ScopedWinningScreen(vm: LedgerViewModel, scope: String, id: Long, onBack: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val report by produceState<DrawReport?>(null, date, session, id, scope, revision) {
        value = date?.let { if (scope == "agent") vm.agentReport(id, it, session, true) else vm.customerReport(id, it, session, true) }
    }
    AppScaffold("ထီပေါက်စဉ်", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                DateInput(dateText, { dateText = it }, "ရက်စွဲ")
                Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } }
            }
            item { if (report == null) Text("ရက်စွဲကို စစ်ဆေးပါ") else if (!report!!.winnerAvailable) UnavailableState("ထီပေါက်ဂဏန်း မရှိသေးပါ") else ReportCard(report!!.calculation, report!!.winningDigit) }
        }
    }
}

@Composable
fun ReportScreen(vm: LedgerViewModel, scope: String, id: Long, onBack: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    var after by rememberSaveable { mutableStateOf(false) }
    var weekly by rememberSaveable { mutableStateOf(false) }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val report by produceState<DrawReport?>(null, date, session, id, scope, after, revision) {
        value = date?.let { if (scope == "agent") vm.agentReport(id, it, session, after) else vm.customerReport(id, it, session, after) }
    }
    val customerRows by produceState<List<AgentCustomerReportRow>>(emptyList(), date, session, id, scope, after, revision) {
        value = if (scope == "agent" && date != null) vm.agentCustomerReport(id, date, session, after) else emptyList()
    }
    val weeklyReport by produceState<WeeklyReport?>(null, date, id, after, weekly, revision) {
        value = if (scope == "customer" && weekly && date != null) vm.weeklyCustomerReport(id, date, after) else null
    }
    AppScaffold("အစီရင်ခံစာ", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                BilingualText(if (scope == "customer") "ရက်အလိုက်" else "ထိုးသားအလိုက်\nCustomer-by-customer", primaryStyle=MaterialTheme.typography.headlineSmall)
                DateInput(dateText, { dateText = it }, "ရက်စွဲ")
                Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=!after, onClick={after=false}, label={BilingualText("ဂဏန်းမထွက်ခင်\nBefore", primaryStyle=MaterialTheme.typography.labelLarge, secondaryStyle=MaterialTheme.typography.labelMedium)})
                    FilterChip(selected=after, onClick={after=true}, label={BilingualText("ဂဏန်းထွက်ပြီးချိန်\nAfter", primaryStyle=MaterialTheme.typography.labelLarge, secondaryStyle=MaterialTheme.typography.labelMedium)})
                }
                if (scope == "customer") {
                    Row { FilterChip(!weekly, { weekly = false }, label = { Text("ရက်အလိုက်") }, modifier = Modifier.padding(end = 8.dp)); FilterChip(weekly, { weekly = true }, label = { Text("အပတ်စဉ်") }) }
                }
            }
            if (scope == "customer" && weekly) {
                weeklyReport?.let { report ->
                    items(report.rows) { row ->
                        OutlinedCard { Column(Modifier.padding(12.dp)) { Text("${row.date.displayDate()} • ${row.session.label}"); if (row.calculation == null) Text("အချက်အလက် မရှိသေးပါ") else Text("ထိုးကြေး ${row.calculation.totalBet.mmk()} • လျော် ${row.calculation.payout.mmk()} • ရှုံး/မြတ် ${row.calculation.profitLoss.mmk()}") } }
                    }
                    item { AnalysisMetric("အပတ်စဉ် ထိုးကြေးစုစုပေါင်း", report.totalBet.mmk()); AnalysisMetric("အပတ်စဉ် လျော်ပေးငွေ", report.payout.mmk()); AnalysisMetric("အပတ်စဉ် ရှုံး/မြတ်", report.profitLoss.mmk()) }
                }
            } else if (scope == "agent" && after && report?.winnerAvailable != true) {
                item { UnavailableState("ထီပေါက်ပြီးချိန်အတွက် ရလဒ်မရှိသေးပါ") }
            } else if (scope == "agent") {
                item {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ဖောက်သည်", Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                        Text("ထိုးကြေး", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("လျော်", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("ရှုံး/မြတ်", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                }
                items(customerRows) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.customer.name, Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold)
                            Text(row.calculation.totalBet.mmk(), Modifier.weight(1f))
                            Text(row.calculation.payout.mmk(), Modifier.weight(1f))
                            Text(row.calculation.profitLoss.mmk(), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        }
                }
                if (customerRows.isNotEmpty()) item {
                    val total = customerRows.map { it.calculation }.reduce { a, b -> DrawCalculation(Math.addExact(a.totalBet,b.totalBet), 0, Math.addExact(a.winningStake,b.winningStake), Math.addExact(a.payout,b.payout), Math.addExact(a.commission,b.commission), Math.addExact(a.profitLoss,b.profitLoss)) }
                    ReportCard(total, null)
                }
            } else {
                item { if (report == null) Text("ရက်စွဲကို စစ်ဆေးပါ") else if (after && !report!!.winnerAvailable) UnavailableState("ထီပေါက်ပြီးချိန်အတွက် ရလဒ်မရှိသေးပါ") else ReportCard(report!!.calculation, report!!.winningDigit) }
            }
        }
    }
}

@Composable private fun ReportCard(c: DrawCalculation, winner: String?) {
    Surface(shape=MaterialTheme.shapes.medium, color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BilingualText(if (winner == null) "ဂဏန်းမထွက်ခင်\nBefore" else "ပေါက်ဂဏန်း $winner", primaryStyle=MaterialTheme.typography.titleLarge)
            Text("ထိုးကြေးစုစုပေါင်း  ${c.totalBet.mmk()}")
            Text("ပေါက်ကြေး  ${c.winningStake.mmk()}")
            Text("လျော်ပေးငွေ  ${c.payout.mmk()}")
            Text("ကော်မရှင်  ${c.commission.mmk()}")
            Text("ရှုံး/မြတ်  ${c.profitLoss.mmk()}", fontWeight = FontWeight.Bold)
            Text("Net ရှင်းတမ်း  ${c.netSettlement.mmk()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable fun UnavailableState(text: String) {
    Text(text, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
}

@Composable
fun AnalysisScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val revision by vm.revision.collectAsStateWithLifecycle()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val result by produceState<AnalysisResult?>(null, date, session, id, revision) { value = date?.let { vm.analysis(id, it, session) } }
    AppScaffold("သုံးသပ်ချက်", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("လက်ရှိအကွက်အခြေအနေ သုံးသပ်ချက်", style = MaterialTheme.typography.titleMedium)
                DateInput(dateText, { dateText = it }, "ရက်စွဲ")
                Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } }
            }
            result?.let { analysis ->
                item {
                    AnalysisMetric("လက်ရှိအကွက်အရေအတွက်", analysis.distinctDigits.toString())
                    AnalysisMetric("ထိုးကြေးစုစုပေါင်း", analysis.totalBet.mmk())
                    AnalysisMetric("ကန့်သတ်ထားသောအကွက်", analysis.limitedDigits.toString())
                    AnalysisMetric("80%+ သတိပေး", analysis.warningDigits.toString())
                    AnalysisMetric("90%+ အလွန်နီး", analysis.nearDigits.toString())
                    AnalysisMetric("100% ပြည့်ပြီး / ထပ်မလက်ခံ", analysis.fullDigits.toString())
                    AnalysisMetric("အဆိုးဆုံးလျော်ပေးရနိုင်မှု", analysis.worstCasePayout.mmk())
                    AnalysisMetric("အဆိုးဆုံး ရှုံး/မြတ်", analysis.worstCaseProfitLoss.mmk())
                }
                item {
                    Text("ထိုးကြေးအများဆုံးအကွက်များ", style = MaterialTheme.typography.titleLarge)
                    analysis.highest.forEach { Text("${it.first}  ${it.second.mmk()}") }
                    Text("ပိတ်ထားသောအကွက်များ: ${analysis.closedDigits.sorted().joinToString(", ").ifBlank { "မရှိ" }}")
                    Text("ထပ်မလက်ခံသင့်သောအကွက်များ: ${analysis.rejectDigits.sorted().joinToString(", ").ifBlank { "မရှိ" }}")
                }
                item {
                    Text("အကွက်တစ်ခုချင်းစီအနိုင်ရပါက လက်ရှိ bet scenario", style = MaterialTheme.typography.titleLarge)
                    analysis.scenarios.filter { it.stake > 0 }.forEach { scenario -> Text("${scenario.digit}: ထိုးကြေး ${scenario.stake.mmk()} • လျော် ${scenario.payout.mmk()} • ရှုံး/မြတ် ${scenario.profitLoss.mmk()}") }
                }
            }
        }
    }
}
@Composable private fun AnalysisMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical=10.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Text(label, style=MaterialTheme.typography.bodyMedium); Text(value, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary) }
}
@Composable fun ClosedDayScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val days by vm.closedDays.collectAsStateWithLifecycle()
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pendingDelete by remember { mutableStateOf<ClosedDayEntity?>(null) }
    pendingDelete?.let { day -> AlertDialog(onDismissRequest={pendingDelete=null}, title={Text("ပိတ်ရက်ဖျက်မည်လား")}, text={Text("${day.date.displayDate()} ကို ပြန်ဖွင့်မည်လား?")}, confirmButton={TextButton({vm.removeClosedDay(day);pendingDelete=null}){Text("ဖျက်မည်")}}, dismissButton={TextButton({pendingDelete=null}){Text("မလုပ်ပါ")}}) }
    AppScaffold("ပိတ်ရက်", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("သတ်မှတ်ထားသော ပိတ်ရက်များတွင် စာရင်းအသစ် လက်မခံပါ", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
            DateInput(dateText, { dateText = it }, "ရက်စွဲ")
            Button(onClick = { runCatching { LocalDate.parse(dateText) }.getOrNull()?.let(vm::addClosedDay) }, modifier=Modifier.fillMaxWidth(), shape=MaterialTheme.shapes.medium) { Text("ပိတ်ရက်သတ်မှတ်မည်") }
            if(days.isEmpty()) EmptyState("ပိတ်ရက် မရှိသေးပါ", "ရက်စွဲတစ်ခုရွေးပြီး ပိတ်ရက်သတ်မှတ်ပါ") else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { items(days, key = { it.id }) { day -> ElevatedCard(shape=MaterialTheme.shapes.medium) { ListItem(headlineContent = { Text(day.date.displayDate(), fontWeight=FontWeight.Bold) }, supportingContent={Text("စာရင်းအသစ် လက်မခံပါ")}, trailingContent = { TextButton(onClick = { pendingDelete = day }) { Text("ဖယ်ရှားမည်") } }) } } }
        }
    }
}
@Composable fun BetHistoryScreen(vm: LedgerViewModel, customerId: Long, onEdit: (BetEntryWithLines) -> Unit, onBack: () -> Unit) {
    val entries by vm.customerEntries(customerId).collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<BetEntryEntity?>(null) }
    pendingDelete?.let { entry -> AlertDialog(onDismissRequest={pendingDelete=null}, title={Text("စာရင်းဖျက်မည်လား")}, text={Text("ဒီစာရင်းကို အပြီးဖျက်မလား?")}, confirmButton={TextButton(onClick={vm.deleteBet(entry);pendingDelete=null}){Text("ဖျက်မည်")}}, dismissButton={TextButton(onClick={pendingDelete=null}){Text("မလုပ်ပါ")}}) }
    AppScaffold("စာရင်းမှတ်တမ်း", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entries.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "အတည်ပြုထားသော စာရင်းမရှိသေးပါ") }
            items(entries, key = { it.entry.id }) { record ->
                ElevatedCard {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${record.entry.drawDate} • ${record.entry.drawSession.label}", fontWeight = FontWeight.Bold)
                            Text(record.entry.sourceText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${record.lines.sumOf { it.amount }.mmk()} • ${record.lines.size} အကွက်")
                        }
                        Column { TextButton(onClick = { onEdit(record) }) { Text("ပြင်မည်") }; TextButton(onClick = { pendingDelete = record.entry }) { Text("ဖယ်ရှားမည်") } }
                    }
                }
            }
        }
    }
}
@Composable fun SettingsScreen(onBack:()->Unit){
    val language=LocalLanguage.current
    AppScaffold("ဆက်တင်များ",onBack){p->Column(Modifier.fillMaxSize().padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("ဘာသာစကား",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        OutlinedCard{Column{ListItem(headlineContent={Text("မြန်မာ")},leadingContent={RadioButton(language.code=="my",{language.set("my")})});ListItem(headlineContent={Text("English")},leadingContent={RadioButton(language.code=="en",{language.set("en")})})}}
        Text("ဘာသာစကားပြောင်းလဲမှုသည် ချက်ချင်းအကျိုးသက်ရောက်ပါမည်။",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
@Composable fun SimpleListScreen(title:String,onBack:()->Unit,subtitle:String,rows:List<String>){AppScaffold(title,onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text(subtitle,style=MaterialTheme.typography.headlineSmall)};items(rows){OutlinedCard{Text(it,Modifier.fillMaxWidth().padding(16.dp),style=MaterialTheme.typography.bodyLarge)}}}}}

@Composable fun OperationalBottomBar(selected:String,onNavigate:(String)->Unit,onQuickEntry:()->Unit){
    NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
        NavigationBarItem(selected=="home",{onNavigate("home")},icon={Icon(Icons.Default.Home,null)},label={Text("ယနေ့")})
        NavigationBarItem(selected=="ledger",{onNavigate("ledger")},icon={Icon(Icons.Default.ListAlt,null)},label={Text("စာရင်း")})
        NavigationBarItem(selected=="quick",onQuickEntry,icon={Icon(Icons.Default.AddCircle,null)},label={Text("သွင်းရန်")})
        NavigationBarItem(selected=="settlement",{onNavigate("settlement")},icon={Icon(Icons.Default.AccountBalanceWallet,null)},label={Text("ရှင်းတမ်း")})
        NavigationBarItem(selected=="manage",{onNavigate("manage")},icon={Icon(Icons.Default.Settings,null)},label={Text("စီမံရန်")})
    }
}
