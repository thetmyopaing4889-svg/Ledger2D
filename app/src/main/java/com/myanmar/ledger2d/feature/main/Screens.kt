@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.myanmar.ledger2d.LedgerApplication
import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.design.*
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private fun Long.mmk()="${NumberFormat.getIntegerInstance(Locale.US).format(this)} MMK"
@Composable fun AppScaffold(title:String,onBack:(()->Unit)?=null,action:(@Composable RowScope.() -> Unit)?=null,fab:(@Composable () -> Unit)?=null,content:@Composable (PaddingValues) -> Unit){ Scaffold(topBar={TopAppBar(title={Text(title,fontWeight=FontWeight.SemiBold)},navigationIcon={if(onBack!=null)TextButton(onClick=onBack){Text("‹ Back")}},actions={action?.invoke(this)})},floatingActionButton={fab?.invoke()},content=content) }
@Composable fun WelcomeScreen(onContinue:()->Unit){ Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.SpaceBetween){Column(Modifier.padding(top=80.dp)){Surface(color=MaterialTheme.colorScheme.primary,shape=MaterialTheme.shapes.large){Text("2D",Modifier.padding(horizontal=22.dp,vertical=16.dp),color=MaterialTheme.colorScheme.onPrimary,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black)};Spacer(Modifier.height(28.dp));Text("Myanmar 2D\nAgent Ledger",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Text("Accurate offline records for agents, customers, limits and draw results.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)};Button(onClick=onContinue,modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Get Started")}}} }
@Composable fun AgentListScreen(vm:LedgerViewModel,onAgent:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit,onWinning:()->Unit,onClosedDays:()->Unit,onBackup:()->Unit){
    val agents by vm.agents.collectAsStateWithLifecycle(); val days by vm.closedDays.collectAsStateWithLifecycle()
    AppScaffold("Agents",action={TextButton(onClick=onWinning){Text("ထီပေါက်စဉ်")};TextButton(onClick=onClosedDays){Text("ပိတ်ရက်")};TextButton(onClick=onBackup){Text("Backup")}},fab={ExtendedFloatingActionButton(onClick=onAdd){Text("+ Agent")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Text("အေးဂျင့်များ",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("စာရင်းနှင့် ဖောက်သည်များကို စီမံပါ",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            item{OutlinedCard(onClick=onClosedDays,modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("GLOBAL • ပိတ်ရက်",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);Text(if(days.isEmpty())"ပိတ်ရက် မသတ်မှတ်ရသေးပါ" else "${days.size} ရက် ပိတ်ထားသည်",style=MaterialTheme.typography.titleMedium);if(days.isNotEmpty())Text(days.take(2).joinToString(" • "){it.date.toString()},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text("ကြည့်ရန်",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}}}
            if(agents.isEmpty()) item{EmptyState("အေးဂျင့်မရှိသေးပါ","+ Agent ကိုနှိပ်၍ စတင်ပါ")} else items(agents,key={it.id}){agent->ElevatedCard(onClick={onAgent(agent.id)},modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(agent.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("နှုန်းထား ${agent.rate}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary);if(agent.phone.isNotBlank())Text(agent.phone,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton({onEdit(agent.id)}){Text("ပြင်မည်")}}}}
            item{Spacer(Modifier.height(72.dp))}
        }
    }
}
@Composable fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=52.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center);Spacer(Modifier.height(8.dp));Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)}}
@Composable fun AgentFormScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val existing by vm.agent(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var rate by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;rate=it.rate.toString();remark=it.remark}};AppScaffold(if(id==0L)"Add Agent" else "Edit Agent",onBack){p->FormColumn(p){Input(name,{name=it},"Name",true);Input(address,{address=it},"Address");Input(phone,{phone=it},"Phone",keyboard=KeyboardType.Phone);Input(rate,{rate=it},"Rate",true,KeyboardType.Number);Input(remark,{remark=it},"Remark");FormActions(onBack,{vm.saveAgent(id,name,address,phone,rate,remark,onBack)},name.isNotBlank()&&rate.toLongOrNull()?.let{it>0}==true)}}}
@Composable fun CustomerFormScreen(vm:LedgerViewModel,id:Long,agentId:Long,onBack:()->Unit){val existing by vm.customer(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;remark=it.remark}};AppScaffold(if(id==0L)"Add Customer" else "Edit Customer",onBack){p->FormColumn(p){Input(name,{name=it},"အမည်",true);Input(address,{address=it},"လိပ်စာ");Input(phone,{phone=it},"ဖုန်း",keyboard=KeyboardType.Phone);Input(remark,{remark=it},"မှတ်ချက်");FormActions(onBack,{vm.saveCustomer(id,agentId,name,address,phone,remark,onBack)},name.isNotBlank())}}}
@Composable private fun FormColumn(p:PaddingValues,content:@Composable ColumnScope.() -> Unit)=Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
@Composable private fun Input(value:String,onValue:(String)->Unit,label:String,required:Boolean=false,keyboard:KeyboardType=KeyboardType.Text){OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label+(if(required)" *" else ""))},singleLine=label!="Remark"&&label!="မှတ်ချက်",keyboardOptions=KeyboardOptions(keyboardType=keyboard),shape=MaterialTheme.shapes.medium)}
@Composable private fun DateInput(value:String,onValue:(String)->Unit,label:String){var open by rememberSaveable{mutableStateOf(false)};OutlinedTextField(value=value,onValueChange={},modifier=Modifier.fillMaxWidth(),label={Text(label)},readOnly=true,trailingIcon={TextButton({open=true}){Text("ရွေး")}},shape=MaterialTheme.shapes.medium);if(open){val initial=runCatching{LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull();val state=rememberDatePickerState(initialSelectedDateMillis=initial);DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{onValue(java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString())};open=false}){Text("ရွေးမည်")}},dismissButton={TextButton(onClick={open=false}){Text("မလုပ်ပါ")}}){DatePicker(state)}}}
@Composable private fun FormActions(cancel:()->Unit,save:()->Unit,enabled:Boolean){Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(cancel,Modifier.weight(1f)){Text("Cancel")};Button(save,Modifier.weight(1f),enabled=enabled){Text("Save")}}}
@Composable fun AgentDetailScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onRoute: (String) -> Unit) {
    val agent by vm.agent(id).collectAsState(initial = null)
    val profile = agent ?: return
    AppScaffold(profile.name, onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding=PaddingValues(AppDimens.screen), verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item {
                ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.primaryContainer), shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.Top) {
                            Column(Modifier.weight(1f)) { Text(profile.name, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold); Text("Agent profile", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurfaceVariant) }
                            TextButton(onClick={onRoute("agentForm/$id")}) { Text("Edit") }
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.18f))
                        Text("Rate ${profile.rate}", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.primary)
                        if(profile.phone.isNotBlank()) Text(profile.phone, style=MaterialTheme.typography.bodyMedium)
                        if(profile.address.isNotBlank()) Text(profile.address, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(profile.remark.isNotBlank()) Text(profile.remark, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Text("လုပ်ငန်းဆောင်တာများ", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold) }
            item { DetailActionCard("Customers", "ဖောက်သည်များနှင့် စာရင်းသွင်းမှုများ", "customers/$id", onRoute) }
            item { DetailActionCard("Total list", "အေးဂျင့်အလိုက် စုစုပေါင်းစာရင်း", "total/$id", onRoute) }
            item { Text("ထိန်းချုပ်မှုနှင့် ရလဒ်များ", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) { Column(Modifier.weight(1f)) { DetailActionCard("Closed numbers", "ပိတ်ထားသောဂဏန်း", "closed/$id", onRoute) }; Column(Modifier.weight(1f)) { DetailActionCard("Results", "ထီပေါက်စဉ်", "winning/agent/$id", onRoute) } } }
            item { DetailActionCard("Reports", "နေ့စဉ်နှင့် အပတ်စဉ်သုံးသပ်ချက်", "report/agent/$id", onRoute) }
            item { DetailActionCard("Format guide", "စာရင်းထည့်သွင်းပုံများ", "format", onRoute) }
        }
    }
}
@Composable private fun DetailActionCard(title:String, subtitle:String, route:String, onRoute:(String)->Unit) {
    OutlinedCard(onClick={onRoute(route)}, modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(title, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold); Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}
@Composable fun CustomerListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit,onCustomer:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit){
    val list by vm.customers(agentId).collectAsState(initial=emptyList())
    AppScaffold("Customers",onBack,fab={ExtendedFloatingActionButton(onClick=onAdd){Text("+ Customer")}}){padding->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("ဖောက်သည်များ",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("ဒီအေးဂျင့်အောက်ရှိ ဖောက်သည်များ",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(list.isEmpty())item{EmptyState("ဖောက်သည်မရှိသေးပါ","+ Customer ကိုနှိပ်၍ စတင်ပါ")} else items(list,key={it.id}){customer->OutlinedCard(onClick={onCustomer(customer.id)},modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(customer.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);if(customer.phone.isNotBlank())Text(customer.phone,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton({onEdit(customer.id)}){Text("ပြင်မည်")}}}}
    }}
}
@Composable fun CustomerDetailScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onRoute: (String) -> Unit) {
    val customer by vm.customer(id).collectAsState(initial=null)
    val profile=customer ?: return
    AppScaffold(profile.name,onBack,action={TextButton({onRoute("customerForm/${profile.agentId}/$id")}){Text("Edit")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),shape=MaterialTheme.shapes.large){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(profile.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Customer profile",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);if(profile.phone.isNotBlank())Text(profile.phone);if(profile.address.isNotBlank())Text(profile.address,color=MaterialTheme.colorScheme.onSurfaceVariant);if(profile.remark.isNotBlank())Text(profile.remark,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            item{Text("လုပ်ငန်းဆောင်တာများ",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
            item{DetailActionCard("စာရင်းသွင်းရန်","မနက်/ညနေ ထိုးကြေးစာရင်းထည့်ရန်","bet/${profile.agentId}/$id",onRoute)}
            item{DetailActionCard("စာရင်းမှတ်တမ်း","ထည့်ထားသောစာရင်းများကို ကြည့်ရန်","betHistory/$id",onRoute)}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){DetailActionCard("Digit list","ဂဏန်းအလိုက်စာရင်း","digitList/${profile.agentId}/$id",onRoute)};Column(Modifier.weight(1f)){DetailActionCard("Analysis","အကွက်အခြေအနေသုံးသပ်ချက်","analysis/$id",onRoute)}}}
            item{Text("Report နှင့် setting",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){DetailActionCard("Report","နေ့စဉ်/အပတ်စဉ် report","report/customer/$id",onRoute)};Column(Modifier.weight(1f)){DetailActionCard("Limit","ကန့်သတ်ချက်များ","limit/$id",onRoute)}}}
            item{DetailActionCard("Commission","ကော်မရှင်နှုန်းထား","commission/$id",onRoute)}
        }
    }
}
@Composable fun BettingScreen(vm:LedgerViewModel,agentId:Long,customerId:Long,onBack:()->Unit,entryId:Long=0L){var dateText by rememberSaveable(entryId){mutableStateOf(LocalDate.now().toString())};var session by rememberSaveable(entryId){mutableStateOf(DrawSession.MORNING)};var raw by rememberSaveable(entryId){mutableStateOf("")};var format by rememberSaveable{mutableStateOf(QuickFormat.MANUAL)};val existing by vm.betEntry(entryId).collectAsStateWithLifecycle(initialValue=null);val preview by vm.preview.collectAsStateWithLifecycle();val submit by vm.submit.collectAsStateWithLifecycle();val date=runCatching{LocalDate.parse(dateText)}.getOrNull();LaunchedEffect(existing){existing?.let{dateText=it.entry.drawDate.toString();session=it.entry.drawSession;raw=it.entry.sourceText}};LaunchedEffect(raw,format,date,session){date?.let{vm.refreshPreview(customerId,agentId,it,session,raw,format,entryId)}};LaunchedEffect(submit){if(submit is SubmitState.Success)onBack()};AppScaffold(if(entryId==0L)"စာရင်းသွင်းရန်" else "စာရင်းပြင်ရန်",onBack){p->Column(Modifier.fillMaxSize().padding(p)){LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=AppDimens.screen,top=AppDimens.screen,end=AppDimens.screen,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{DateInput(dateText,{dateText=it},"Date")};item{Text("Draw",style=MaterialTheme.typography.titleLarge);SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){DrawSession.entries.forEachIndexed{i,s->SegmentedButton(selected=session==s,onClick={session=s},shape=SegmentedButtonDefaults.itemShape(i,2)){Text(s.label)}}}};item{OutlinedTextField(raw,{raw=it},Modifier.fillMaxWidth().heightIn(min=130.dp),label={Text("Input Box")},placeholder={Text(if(format==QuickFormat.MANUAL)"10.13.14 100" else "Enter format input")},shape=MaterialTheme.shapes.medium)};item{Text("Quick Format Buttons",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){QuickFormat.entries.forEach{f->FilterChip(selected=format==f,onClick={format=f},label={Text(f.label)})}}};item{PreviewCard(preview)}};Surface(shadowElevation=10.dp){Button(onClick={date?.let{if(entryId==0L)vm.confirm(customerId,agentId,it,session,raw) else existing?.let{item->vm.editConfirm(item,it,session,raw)}}},modifier=Modifier.fillMaxWidth().padding(AppDimens.screen).height(54.dp),enabled=preview.canConfirm&&submit !is SubmitState.Working){Text(if(submit is SubmitState.Working)"အတည်ပြုနေသည်…" else if(entryId==0L)"Confirm" else "Update")}}}}}
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
    message.contains("Enter digit and amount",ignoreCase=true) -> "ဂဏန်းနှင့် ငွေပမာဏ ထည့်ပါ"
    message.contains("Closed Day",ignoreCase=true) -> "ပိတ်ရက်ဖြစ်သောကြောင့် စာရင်းသွင်း၍ မရပါ"
    else -> message
}

@Composable fun DigitListScreen(vm:LedgerViewModel,agentId:Long,customerId:Long,onBack:()->Unit){var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }; var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }; val totals by vm.customerTotals(customerId,selectedDate,session).collectAsState(initial=emptyList());val all by vm.allLimit(customerId).collectAsState(initial=null);val special by vm.specialLimits(customerId).collectAsState(initial=emptyList());val closed by vm.closedNumbers(agentId).collectAsState(initial=emptyList());val amounts=totals.associate{it.digit to it.amount};val specialMap=special.associate{it.digit to it.amount};val closedSet=closed.map{it.digit}.toSet();AppScaffold("List",onBack){p->LazyVerticalGrid(GridCells.Fixed(5),Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){item(span={GridItemSpan(5)}){Column{DateInput(dateText,{dateText=it},"Date");Row{DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)},modifier=Modifier.padding(end=8.dp))}}}};item(span={GridItemSpan(5)}){Text("Limit: ${all?.amount?:"No limit"}",Modifier.padding(10.dp),style=MaterialTheme.typography.titleLarge)};items((0..99).toList()){n->val digit=n.toString().padStart(2,'0');val isClosed=digit in closedSet;val isSpecial=digit in specialMap;Surface(color=when{isClosed->MaterialTheme.colorScheme.errorContainer;isSpecial->MaterialTheme.colorScheme.secondaryContainer;else->MaterialTheme.colorScheme.surfaceVariant},shape=MaterialTheme.shapes.small){Column(Modifier.aspectRatio(.84f).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(digit,fontWeight=FontWeight.Black);Text((amounts[digit]?:0).toString(),style=MaterialTheme.typography.labelSmall);if(isClosed)Text("Closed",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error) else if(isSpecial)Text("S:${specialMap[digit]}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)}}}}}}
@Composable fun TotalListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit){var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }; var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }; val totals by vm.agentTotals(agentId,selectedDate,session).collectAsState(initial=emptyList()); AppScaffold("Total List",onBack){p->Column(Modifier.fillMaxSize().padding(p)){DateInput(dateText,{dateText=it},"Date");Row(Modifier.padding(horizontal=AppDimens.screen),horizontalArrangement=Arrangement.spacedBy(8.dp)){DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)})}};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("${selectedDate} • ${session.label}",style=MaterialTheme.typography.headlineSmall)};if(totals.isEmpty())item{Text("No confirmed entries for this draw")}else items(totals){row->OutlinedCard{Text("${row.digit}   ${row.amount.mmk()}",Modifier.fillMaxWidth().padding(16.dp))}}}}}}
@Composable fun ClosedNumberScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit){val numbers by vm.closedNumbers(agentId).collectAsState(initial=emptyList());var digit by rememberSaveable{mutableStateOf("")};AppScaffold("Closed Number",onBack){p->Column(Modifier.fillMaxSize().padding(p).padding(AppDimens.screen)){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(digit,{digit=it.take(2)},Modifier.weight(1f),label={Text("Digit 00–99")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));Button({vm.addClosedNumber(agentId,digit);digit=""},enabled=BetParser.validDigit(digit)){Text("Save")}};Spacer(Modifier.height(20.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){if(numbers.isEmpty())item{Text("No Closed Number configured.")}else items(numbers,key={it.id}){n->ListItem(headlineContent={Text(n.digit,fontWeight=FontWeight.Black)},supportingContent={Text("All customers under this Agent are blocked")},trailingContent={TextButton({vm.removeClosedNumber(n)}){Text("Remove")}},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.errorContainer))}}}}}
@Composable fun FormatScreen(onBack:()->Unit){val rows=listOf("ပါဝါ" to "05 50 16 61 27 72 38 83 49 94","နက္ခတ်" to "07 70 18 81 24 42 35 53 69 96","အပူး" to "00 11 22 33 44 55 66 77 88 99","ညီအကို" to "20 sibling numbers","အခွေ" to "345.100 → 34 43 45 54 35 53","အခွေပူး" to "အခွေ plus doubles","ပတ်သီး" to "9.100 → 19 unique numbers","ထိပ်စည်း" to "9.100 → 90…99","နောက်ပိတ်" to "9.100 → 09…99","R / reverse" to "10.20R100 → both directions");AppScaffold("Format",onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Input format reference",style=MaterialTheme.typography.headlineSmall)};items(rows){(title,body)->OutlinedCard{Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}}
@Composable fun CommissionScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val c by vm.customer(id).collectAsState(initial=null);var value by rememberSaveable{mutableStateOf("")};LaunchedEffect(c){c?.let{value=(it.commissionRateBasisPoints/100.0).toString().removeSuffix(".0")}};AppScaffold("Commission Rate",onBack){p->FormColumn(p){Input(value,{value=it},"Percentage",true,KeyboardType.Decimal);Text("15 means 15%. Commission uses deterministic basis-point arithmetic.",color=MaterialTheme.colorScheme.onSurfaceVariant);FormActions(onBack,{vm.updateCommission(id,value,onBack)},value.toBigDecimalOrNull()?.let{it>=java.math.BigDecimal.ZERO&&it<=java.math.BigDecimal(100)}==true)}}}
@Composable fun LimitScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val all by vm.allLimit(id).collectAsState(initial=null);val specials by vm.specialLimits(id).collectAsState(initial=emptyList());var allText by rememberSaveable{mutableStateOf("")};var digit by rememberSaveable{mutableStateOf("")};var amount by rememberSaveable{mutableStateOf("")};LaunchedEffect(all){allText=all?.amount?.toString()?:""};AppScaffold("Limit",onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("All Limit",style=MaterialTheme.typography.titleLarge);Input(allText,{allText=it},"Blank means no All Limit",keyboard=KeyboardType.Number);Button({vm.updateAllLimit(id,allText){}}){Text("Save")}};item{HorizontalDivider();Text("Special Limit",style=MaterialTheme.typography.titleLarge);Input(digit,{digit=it.take(2)},"Digit",keyboard=KeyboardType.Number);Input(amount,{amount=it},"Amount",keyboard=KeyboardType.Number);Button({vm.addSpecialLimit(id,digit,amount);digit="";amount=""},enabled=BetParser.validDigit(digit)&&amount.toLongOrNull()?.let{it>0}==true){Text("Save")}};items(specials){limit->Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.shapes.small).padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("${limit.digit}  →  ${limit.amount.mmk()}");TextButton({vm.removeSpecialLimit(limit)}){Text("Remove")}}}}}}
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
                    SegmentedButton(showForm, { showForm = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("အသစ်ထည့်ရန်") }
                    SegmentedButton(!showForm, { showForm = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("မှတ်တမ်းကြည့်ရန်") }
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
                                Text("Update လုပ်ရန် ပေါက်ဂဏန်းအသစ် ရိုက်ထည့်ပါ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Input(digit, { digit = it.filter(Char::isDigit).take(2) }, "ပေါက်ဂဏန်း 00–99", true, KeyboardType.Number)
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
                                ) { Text(if (existing == null) "သိမ်းမည်" else "Update") }
                            }
                        }
                    }
                }
            } else if (winners.isEmpty()) {
                item { EmptyState("မှတ်တမ်းမရှိသေးပါ", "အသစ်ထည့်ရန်မှ ပေါက်ဂဏန်းကို ထည့်ပါ") }
            } else {
                items(winners, key = { it.id }) { winner ->
                    OutlinedCard {
                        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(winner.date.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${winner.session.label}  ${winner.digit}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            Row {
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
@Composable
fun ScopedWinningScreen(vm: LedgerViewModel, scope: String, id: Long, onBack: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val report by produceState<DrawReport?>(null, date, session, id, scope) {
        value = date?.let { if (scope == "agent") vm.agentReport(id, it, session, true) else vm.customerReport(id, it, session, true) }
    }
    AppScaffold("ထီပေါက်စဉ်", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                DateInput(dateText, { dateText = it }, "Date")
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
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val report by produceState<DrawReport?>(null, date, session, id, scope, after) {
        value = date?.let { if (scope == "agent") vm.agentReport(id, it, session, after) else vm.customerReport(id, it, session, after) }
    }
    val customerRows by produceState<List<AgentCustomerReportRow>>(emptyList(), date, session, id, scope, after) {
        value = if (scope == "agent" && date != null) vm.agentCustomerReport(id, date, session, after) else emptyList()
    }
    val weeklyReport by produceState<WeeklyReport?>(null, date, id, after, weekly) {
        value = if (scope == "customer" && weekly && date != null) vm.weeklyCustomerReport(id, date, after) else null
    }
    AppScaffold("Report", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if (scope == "customer") "ရက်အလိုက်" else "Customer-by-customer", style = MaterialTheme.typography.headlineSmall)
                DateInput(dateText, { dateText = it }, "Date")
                Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } }
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(!after, { after = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Before") }
                    SegmentedButton(after, { after = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("After") }
                }
                if (scope == "customer") {
                    Row { FilterChip(!weekly, { weekly = false }, label = { Text("ရက်အလိုက်") }, modifier = Modifier.padding(end = 8.dp)); FilterChip(weekly, { weekly = true }, label = { Text("Weekly အလိုက်") }) }
                }
            }
            if (scope == "customer" && weekly) {
                weeklyReport?.let { report ->
                    items(report.rows) { row ->
                        OutlinedCard { Column(Modifier.padding(12.dp)) { Text("${row.date} • ${row.session.label}"); if (row.calculation == null) Text("အချက်အလက် မရှိသေးပါ") else Text("ထိုးကြေး ${row.calculation.totalBet.mmk()} • လျော် ${row.calculation.payout.mmk()} • P/L ${row.calculation.profitLoss.mmk()}") } }
                    }
                    item { AnalysisMetric("Weekly ထိုးကြေးစုစုပေါင်း", report.totalBet.mmk()); AnalysisMetric("Weekly လျော်ပေးငွေ", report.payout.mmk()); AnalysisMetric("Weekly ရှုံး/မြတ်", report.profitLoss.mmk()) }
                }
            } else if (scope == "agent") {
                items(customerRows) { row ->
                    OutlinedCard {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.customer.name, fontWeight = FontWeight.Bold)
                            Text("ထိုးကြေး ${row.calculation.totalBet.mmk()} • ပေါက်ကြေး ${row.calculation.winningStake.mmk()}")
                            Text("လျော် ${row.calculation.payout.mmk()} • ရှုံး/မြတ် ${row.calculation.profitLoss.mmk()}")
                        }
                    }
                }
                if (customerRows.isNotEmpty()) item {
                    val total = customerRows.map { it.calculation }.reduce { a, b -> DrawCalculation(a.totalBet + b.totalBet, a.distinctSlots + b.distinctSlots, a.winningStake + b.winningStake, a.payout + b.payout, a.commission + b.commission, a.profitLoss + b.profitLoss) }
                    ReportCard(total, null)
                }
            } else {
                item { if (report == null) Text("ရက်စွဲကို စစ်ဆေးပါ") else if (after && !report!!.winnerAvailable) UnavailableState("After အတွက် ထီပေါက်ဂဏန်း မရှိသေးပါ") else ReportCard(report!!.calculation, report!!.winningDigit) }
            }
        }
    }
}

@Composable private fun ReportCard(c: DrawCalculation, winner: String?) {
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (winner == null) "Before" else "ပေါက်ဂဏန်း $winner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("ထိုးကြေးစုစုပေါင်း  ${c.totalBet.mmk()}")
            Text("ပေါက်ကြေး  ${c.winningStake.mmk()}")
            Text("လျော်ပေးငွေ  ${c.payout.mmk()}")
            Text("ကော်မရှင်  ${c.commission.mmk()}")
            Text("ရှုံး/မြတ်  ${c.profitLoss.mmk()}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun UnavailableState(text: String) {
    Text(text, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
}

@Composable
fun AnalysisScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val result by produceState<AnalysisResult?>(null, date, session, id) { value = date?.let { vm.analysis(id, it, session) } }
    AppScaffold("သုံးသပ်ချက်", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("လက်ရှိအကွက်အခြေအနေ သုံးသပ်ချက်", style = MaterialTheme.typography.titleMedium)
                DateInput(dateText, { dateText = it }, "Date")
                Row { DrawSession.entries.forEach { draw -> FilterChip(session == draw, { session = draw }, label = { Text(draw.label) }, modifier = Modifier.padding(end = 8.dp)) } }
            }
            result?.let { analysis ->
                item {
                    AnalysisMetric("လက်ရှိအကွက်အရေအတွက်", analysis.distinctDigits.toString())
                    AnalysisMetric("ထိုးကြေးစုစုပေါင်း", analysis.totalBet.mmk())
                    AnalysisMetric("Limit သတ်မှတ်ထားသောအကွက်", analysis.limitedDigits.toString())
                    AnalysisMetric("80%+ သတိပေး", analysis.warningDigits.toString())
                    AnalysisMetric("90%+ အလွန်နီး", analysis.nearDigits.toString())
                    AnalysisMetric("100% ပြည့်ပြီး / ထပ်မလက်ခံ", analysis.fullDigits.toString())
                    AnalysisMetric("Worst-case payout", analysis.worstCasePayout.mmk())
                    AnalysisMetric("Worst-case P/L", analysis.worstCaseProfitLoss.mmk())
                }
                item {
                    Text("ထိုးကြေးအများဆုံးအကွက်များ", style = MaterialTheme.typography.titleLarge)
                    analysis.highest.forEach { Text("${it.first}  ${it.second.mmk()}") }
                    Text("ပိတ်ထားသောအကွက်များ: ${analysis.closedDigits.sorted().joinToString(", ").ifBlank { "မရှိ" }}")
                    Text("ထပ်မလက်ခံသင့်သောအကွက်များ: ${analysis.rejectDigits.sorted().joinToString(", ").ifBlank { "မရှိ" }}")
                }
                item {
                    Text("အကွက်တစ်ခုချင်းစီအနိုင်ရပါက လက်ရှိ bet scenario", style = MaterialTheme.typography.titleLarge)
                    analysis.scenarios.filter { it.stake > 0 }.forEach { scenario -> Text("${scenario.digit}: stake ${scenario.stake.mmk()} • payout ${scenario.payout.mmk()} • P/L ${scenario.profitLoss.mmk()}") }
                }
            }
        }
    }
}
@Composable private fun AnalysisMetric(label: String, value: String) {
    ElevatedCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable fun ClosedDayScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val days by vm.closedDays.collectAsStateWithLifecycle()
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    AppScaffold("ပိတ်ရက်", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateInput(dateText, { dateText = it }, "Date")
            Button(onClick = { runCatching { LocalDate.parse(dateText) }.getOrNull()?.let(vm::addClosedDay) }) { Text("Save") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(days, key = { it.id }) { day -> ListItem(headlineContent = { Text(day.date.toString()) }, trailingContent = { TextButton(onClick = { vm.removeClosedDay(day) }) { Text("Remove") } }) } }
        }
    }
}
@Composable fun BackupScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LedgerApplication
    var message by rememberSaveable { mutableStateOf("") }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    AppScaffold("Backup / Restore", onBack) { padding ->
        if (confirmRestore) AlertDialog(onDismissRequest={confirmRestore=false},title={Text("Backup ပြန်သွင်းမည်လား")},text={Text("လက်ရှိ local database ကို backup နဲ့ အစားထိုးပါမယ်။")},confirmButton={TextButton(onClick={message=if(app.restoreBackup())"Backup restored successfully" else "Backup file မရှိသေးပါ";confirmRestore=false}){Text("Restore")}},dismissButton={TextButton(onClick={confirmRestore=false}){Text("Cancel")}})
        Column(Modifier.fillMaxSize().padding(padding).padding(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Local database backup", style = MaterialTheme.typography.headlineSmall)
            Text("Backup ကို app ရဲ့ private storage ထဲမှာ သိမ်းထားပြီး app data မဖျက်ဘဲ ပြန် restore လုပ်နိုင်ပါတယ်။")
            Button(onClick = { message = "Backup saved: ${app.createBackup().name}" }, modifier = Modifier.fillMaxWidth()) { Text("Create Backup") }
            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restore Latest Backup") }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable fun BetHistoryScreen(vm: LedgerViewModel, customerId: Long, onEdit: (BetEntryWithLines) -> Unit, onBack: () -> Unit) {
    val entries by vm.customerEntries(customerId).collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<BetEntryEntity?>(null) }
    pendingDelete?.let { entry -> AlertDialog(onDismissRequest={pendingDelete=null}, title={Text("စာရင်းဖျက်မည်လား")}, text={Text("ဒီ betting record ကို အပြီးဖျက်မလား?")}, confirmButton={TextButton(onClick={vm.deleteBet(entry);pendingDelete=null}){Text("ဖျက်မည်")}}, dismissButton={TextButton(onClick={pendingDelete=null}){Text("မလုပ်ပါ")}}) }
    AppScaffold("စာရင်းမှတ်တမ်း", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entries.isEmpty()) item { EmptyState("စာရင်းမရှိသေးပါ", "Confirm လုပ်ထားသော betting record မရှိသေးပါ") }
            items(entries, key = { it.entry.id }) { record ->
                ElevatedCard {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${record.entry.drawDate} • ${record.entry.drawSession.label}", fontWeight = FontWeight.Bold)
                            Text(record.entry.sourceText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${record.lines.sumOf { it.amount }.mmk()} • ${record.lines.size} အကွက်")
                        }
                        Column { TextButton(onClick = { onEdit(record) }) { Text("Edit") }; TextButton(onClick = { pendingDelete = record.entry }) { Text("Remove") } }
                    }
                }
            }
        }
    }
}
@Composable fun SimpleListScreen(title:String,onBack:()->Unit,subtitle:String,rows:List<String>){AppScaffold(title,onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text(subtitle,style=MaterialTheme.typography.headlineSmall)};items(rows){OutlinedCard{Text(it,Modifier.fillMaxWidth().padding(16.dp),style=MaterialTheme.typography.bodyLarge)}}}}}
