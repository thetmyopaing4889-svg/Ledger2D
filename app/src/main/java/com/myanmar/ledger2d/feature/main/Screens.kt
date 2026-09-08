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
import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.design.*
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private fun Long.mmk()="${NumberFormat.getIntegerInstance(Locale.US).format(this)} MMK"
@Composable fun AppScaffold(title:String,onBack:(()->Unit)?=null,action:(@Composable RowScope.() -> Unit)?=null,fab:(@Composable () -> Unit)?=null,content:@Composable (PaddingValues) -> Unit){ Scaffold(topBar={TopAppBar(title={Text(title,fontWeight=FontWeight.SemiBold)},navigationIcon={if(onBack!=null)TextButton(onClick=onBack){Text("‹ Back")}},actions={action?.invoke(this)})},floatingActionButton={fab?.invoke()},content=content) }
@Composable fun WelcomeScreen(onContinue:()->Unit){ Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.SpaceBetween){Column(Modifier.padding(top=80.dp)){Surface(color=MaterialTheme.colorScheme.primary,shape=MaterialTheme.shapes.large){Text("2D",Modifier.padding(horizontal=22.dp,vertical=16.dp),color=MaterialTheme.colorScheme.onPrimary,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black)};Spacer(Modifier.height(28.dp));Text("Myanmar 2D\nAgent Ledger",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Text("Accurate offline records for agents, customers, limits and draw results.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)};Button(onClick=onContinue,modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Get Started")}}} }
@Composable fun AgentListScreen(vm:LedgerViewModel,onAgent:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit,onWinning:()->Unit,onClosedDays:()->Unit){val agents by vm.agents.collectAsStateWithLifecycle();val days by vm.closedDays.collectAsStateWithLifecycle();AppScaffold("Agents",action={TextButton(onClick=onWinning){Text("ထီပေါက်စဉ်")};TextButton(onClick=onClosedDays){Text("ပိတ်ရက်")}},fab={ExtendedFloatingActionButton(onClick=onAdd){Text("+ Add Agent")}}){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){item{ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(18.dp)){Text("GLOBAL",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);Text("Closed Day",style=MaterialTheme.typography.titleLarge);Text(if(days.isEmpty())"No closed dates configured" else "${days.size} closed date(s)",color=MaterialTheme.colorScheme.onSurfaceVariant)}}};if(agents.isEmpty())item{EmptyState("No Agents Yet","Add your first Agent to get started.")}else items(agents,key={it.id}){a->ElevatedCard(onClick={onAgent(a.id)}){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(a.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text("Rate ${a.rate}",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold);if(a.phone.isNotBlank())Text(a.phone,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick={onEdit(a.id)}){Text("Edit")}}}};item{Spacer(Modifier.height(72.dp))}}}}
@Composable fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=52.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center);Spacer(Modifier.height(8.dp));Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)}}
@Composable fun AgentFormScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val existing by vm.agent(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var rate by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;rate=it.rate.toString();remark=it.remark}};AppScaffold(if(id==0L)"Add Agent" else "Edit Agent",onBack){p->FormColumn(p){Input(name,{name=it},"Name",true);Input(address,{address=it},"Address");Input(phone,{phone=it},"Phone",keyboard=KeyboardType.Phone);Input(rate,{rate=it},"Rate",true,KeyboardType.Number);Input(remark,{remark=it},"Remark");FormActions(onBack,{vm.saveAgent(id,name,address,phone,rate,remark,onBack)},name.isNotBlank()&&rate.toLongOrNull()?.let{it>0}==true)}}}
@Composable fun CustomerFormScreen(vm:LedgerViewModel,id:Long,agentId:Long,onBack:()->Unit){val existing by vm.customer(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;remark=it.remark}};AppScaffold(if(id==0L)"Add Customer" else "Edit Customer",onBack){p->FormColumn(p){Input(name,{name=it},"အမည်",true);Input(address,{address=it},"လိပ်စာ");Input(phone,{phone=it},"ဖုန်း",keyboard=KeyboardType.Phone);Input(remark,{remark=it},"မှတ်ချက်");FormActions(onBack,{vm.saveCustomer(id,agentId,name,address,phone,remark,onBack)},name.isNotBlank())}}}
@Composable private fun FormColumn(p:PaddingValues,content:@Composable ColumnScope.() -> Unit)=Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
@Composable private fun Input(value:String,onValue:(String)->Unit,label:String,required:Boolean=false,keyboard:KeyboardType=KeyboardType.Text){OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label+(if(required)" *" else ""))},singleLine=label!="Remark"&&label!="မှတ်ချက်",keyboardOptions=KeyboardOptions(keyboardType=keyboard),shape=MaterialTheme.shapes.medium)}
@Composable private fun FormActions(cancel:()->Unit,save:()->Unit,enabled:Boolean){Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(cancel,Modifier.weight(1f)){Text("Cancel")};Button(save,Modifier.weight(1f),enabled=enabled){Text("Save")}}}
@Composable fun AgentDetailScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit,onRoute:(String)->Unit){val agent by vm.agent(id).collectAsState(initial=null);AppScaffold(agent?.name?:"Agent",onBack,action={TextButton(onClick={onRoute("agentForm/$id")}){Text("Edit")}}){p->LazyVerticalGrid(columns=GridCells.Adaptive(150.dp),modifier=Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){val items=listOf("Customer" to "customers/$id","Total List" to "total/$id","Closed Number" to "closed/$id","Format" to "format","Report" to "report/agent/$id","ထီပေါက်စဉ်" to "winning/agent/$id");items(items){(label,route)->ElevatedCard(onClick={onRoute(route)},modifier=Modifier.height(126.dp)){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text("Open  →",color=MaterialTheme.colorScheme.primary)}}}}}}
@Composable fun CustomerListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit,onCustomer:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit){val list by vm.customers(agentId).collectAsState(initial=emptyList());AppScaffold("Customer",onBack,fab={ExtendedFloatingActionButton(onClick=onAdd){Text("+ Add Customer")}}){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){if(list.isEmpty())item{EmptyState("No Customers Yet","Add a Customer under this Agent.")}else items(list,key={it.id}){c->ElevatedCard(onClick={onCustomer(c.id)}){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);if(c.phone.isNotBlank())Text(c.phone,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton({onEdit(c.id)}){Text("Edit")}}}}}}}
@Composable fun CustomerDetailScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit,onRoute:(String)->Unit){val c by vm.customer(id).collectAsState(initial=null);AppScaffold(c?.name?:"Customer",onBack,action={c?.let{TextButton({onRoute("customerForm/${it.agentId}/$id")}){Text("Edit")}}}){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){val a=c?.agentId?:0;items(listOf("ထီပေါက်စဉ်" to "winning/customer/$id","စာရင်းသွင်းရန်" to "bet/$a/$id","စာရင်းမှတ်တမ်း" to "betHistory/$id","List" to "digitList/$a/$id","သုံးသပ်ချက်" to "analysis/$id","Commission Rate" to "commission/$id","Report" to "report/customer/$id","Limit" to "limit/$id")){(label,route)->ElevatedCard(onClick={onRoute(route)}){Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.titleLarge);Text("→",color=MaterialTheme.colorScheme.primary)}}}}}}
@Composable fun BettingScreen(vm:LedgerViewModel,agentId:Long,customerId:Long,onBack:()->Unit){var dateText by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var session by rememberSaveable{mutableStateOf(DrawSession.MORNING)};var raw by rememberSaveable{mutableStateOf("")};var format by rememberSaveable{mutableStateOf(QuickFormat.MANUAL)};val preview by vm.preview.collectAsStateWithLifecycle();val submit by vm.submit.collectAsStateWithLifecycle();val date=runCatching{LocalDate.parse(dateText)}.getOrNull();LaunchedEffect(raw,format,date,session){date?.let{vm.refreshPreview(customerId,agentId,it,session,raw,format)}};LaunchedEffect(submit){if(submit is SubmitState.Success)onBack()};AppScaffold("စာရင်းသွင်းရန်",onBack){p->Column(Modifier.fillMaxSize().padding(p)){LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=AppDimens.screen,top=AppDimens.screen,end=AppDimens.screen,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Input(dateText,{dateText=it},"Date (YYYY-MM-DD)",true)};item{Text("Draw",style=MaterialTheme.typography.titleLarge);SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){DrawSession.entries.forEachIndexed{i,s->SegmentedButton(selected=session==s,onClick={session=s},shape=SegmentedButtonDefaults.itemShape(i,2)){Text(s.label)}}}};item{OutlinedTextField(raw,{raw=it},Modifier.fillMaxWidth().heightIn(min=130.dp),label={Text("Input Box")},placeholder={Text(if(format==QuickFormat.MANUAL)"10.13.14 100" else "Enter format input")},shape=MaterialTheme.shapes.medium)};item{Text("Quick Format Buttons",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){QuickFormat.entries.forEach{f->FilterChip(selected=format==f,onClick={format=f},label={Text(f.label)})}}};item{PreviewCard(preview)}};Surface(shadowElevation=10.dp){Button(onClick={date?.let{vm.confirm(customerId,agentId,it,session,raw)}},modifier=Modifier.fillMaxWidth().padding(AppDimens.screen).height(54.dp),enabled=preview.canConfirm&&submit !is SubmitState.Working){Text(if(submit is SubmitState.Working)"အတည်ပြုနေသည်…" else "Confirm")}}}}}
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

@Composable fun DigitListScreen(vm:LedgerViewModel,agentId:Long,customerId:Long,onBack:()->Unit){val totals by vm.customerTotals(customerId,LocalDate.now(),DrawSession.MORNING).collectAsState(initial=emptyList());val all by vm.allLimit(customerId).collectAsState(initial=null);val special by vm.specialLimits(customerId).collectAsState(initial=emptyList());val closed by vm.closedNumbers(agentId).collectAsState(initial=emptyList());val amounts=totals.associate{it.digit to it.amount};val specialMap=special.associate{it.digit to it.amount};val closedSet=closed.map{it.digit}.toSet();AppScaffold("List",onBack){p->LazyVerticalGrid(GridCells.Fixed(5),Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){item(span={GridItemSpan(5)}){Text("Limit: ${all?.amount?:"No limit"}",Modifier.padding(10.dp),style=MaterialTheme.typography.titleLarge)};items((0..99).toList()){n->val digit=n.toString().padStart(2,'0');val isClosed=digit in closedSet;val isSpecial=digit in specialMap;Surface(color=when{isClosed->MaterialTheme.colorScheme.errorContainer;isSpecial->MaterialTheme.colorScheme.secondaryContainer;else->MaterialTheme.colorScheme.surfaceVariant},shape=MaterialTheme.shapes.small){Column(Modifier.aspectRatio(.84f).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(digit,fontWeight=FontWeight.Black);Text((amounts[digit]?:0).toString(),style=MaterialTheme.typography.labelSmall);if(isClosed)Text("Closed",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error) else if(isSpecial)Text("S:${specialMap[digit]}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)}}}}}}
@Composable fun TotalListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit){val totals by vm.agentTotals(agentId,LocalDate.now(),DrawSession.MORNING).collectAsState(initial=emptyList());SimpleListScreen("Total List",onBack,"Today • မနက်",if(totals.isEmpty())listOf("No confirmed entries for this draw") else totals.map{"${it.digit}   ${it.amount.mmk()}"})}
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
    AppScaffold("ထီပေါက်စဉ်", onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(AppDimens.screen),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showForm,
                        onClick = { showForm = true },
                        label = { Text("ပေါက်ဂဏန်းထည့်ရန်") },
                    )
                    FilterChip(
                        selected = !showForm,
                        onClick = { showForm = false },
                        label = { Text("ထီပေါက်စဉ်ကြည့်ရန်") },
                    )
                }
            }
            if (showForm) {
                item {
                    Input(date, { date = it }, "Date", true)
                    Row {
                        DrawSession.entries.forEach { draw ->
                            FilterChip(
                                selected = session == draw,
                                onClick = { session = draw },
                                label = { Text(draw.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Input(digit, { digit = it.take(2) }, "Digit 00–99", true, KeyboardType.Number)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { digit = "" }) { Text("Cancel") }
                        Button(
                            onClick = {
                                runCatching { LocalDate.parse(date) }.getOrNull()?.let { parsedDate ->
                                    vm.saveWinner(parsedDate, session, digit) { digit = "" }
                                }
                            },
                            enabled = BetParser.validDigit(digit),
                        ) { Text("Save") }
                    }
                }
            } else if (winners.isEmpty()) {
                item { EmptyState("No winning results", "Use ပေါက်ဂဏန်းထည့်ရန် to add one.") }
            } else {
                    items(winners, key = { it.id }) { winner ->
                        ElevatedCard(onClick={date=winner.date.toString();session=winner.session;digit=winner.digit;showForm=true}) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                Column {
                                Text(winner.date.toString(), style = MaterialTheme.typography.titleLarge)
                                Text(
                                "${winner.session.label}   ${winner.digit}",
                                style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                }
                                TextButton(onClick={vm.deleteWinner(winner)}){Text("Remove")}
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
                Input(dateText, { dateText = it }, "Date", true)
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
                Input(dateText, { dateText = it }, "Date", true)
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
                Input(dateText, { dateText = it }, "Date", true)
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
            Input(dateText, { dateText = it }, "Date", true)
            Button(onClick = { runCatching { LocalDate.parse(dateText) }.getOrNull()?.let(vm::addClosedDay) }) { Text("Save") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(days, key = { it.id }) { day -> ListItem(headlineContent = { Text(day.date.toString()) }, trailingContent = { TextButton(onClick = { vm.removeClosedDay(day) }) { Text("Remove") } }) } }
        }
    }
}
@Composable fun BetHistoryScreen(vm: LedgerViewModel, customerId: Long, onBack: () -> Unit) {
    val entries by vm.customerEntries(customerId).collectAsState(initial = emptyList())
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
                        TextButton(onClick = { vm.deleteBet(record.entry) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}
@Composable fun SimpleListScreen(title:String,onBack:()->Unit,subtitle:String,rows:List<String>){AppScaffold(title,onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text(subtitle,style=MaterialTheme.typography.headlineSmall)};items(rows){OutlinedCard{Text(it,Modifier.fillMaxWidth().padding(16.dp),style=MaterialTheme.typography.bodyLarge)}}}}}
