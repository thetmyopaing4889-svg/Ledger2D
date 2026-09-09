@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.design.*
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

private fun Long.mmk()="${NumberFormat.getIntegerInstance(Locale.US).format(this)} MMK"
private fun LocalDate.displayDate(): String = "${dayOfMonth}.${monthValue}.${year.toString().takeLast(2)}"
@Composable private fun BilingualText(text:String, modifier:Modifier=Modifier, primaryStyle:androidx.compose.ui.text.TextStyle=MaterialTheme.typography.titleMedium, secondaryStyle:androidx.compose.ui.text.TextStyle=MaterialTheme.typography.labelMedium, color:Color=MaterialTheme.colorScheme.onSurface){
    val parts=text.split("\n", limit=2)
    val language=LocalContext.current.getSharedPreferences("ledger_settings",0).getString("language","my") ?: "my"
    if(language=="en" && parts.size>1) Text(parts[1], modifier, style=primaryStyle, color=color)
    else Text(parts.first(), modifier, style=primaryStyle, color=color)
}
@Composable fun AppScaffold(title:String,onBack:(()->Unit)?=null,action:(@Composable RowScope.() -> Unit)?=null,fab:(@Composable () -> Unit)?=null,content:@Composable (PaddingValues) -> Unit){ Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),title={BilingualText(title, primaryStyle=MaterialTheme.typography.titleLarge, secondaryStyle=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurface)},navigationIcon={if(onBack!=null)TextButton(onClick=onBack){Text("‹",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onSurface)}},actions={action?.invoke(this)})},floatingActionButton={fab?.invoke()},content=content) }
@Composable fun WelcomeScreen(onContinue:()->Unit){ Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.SpaceBetween){Column(Modifier.padding(top=80.dp)){Surface(color=MaterialTheme.colorScheme.primary,shape=MaterialTheme.shapes.large){Text("2D",Modifier.padding(horizontal=22.dp,vertical=16.dp),color=MaterialTheme.colorScheme.onPrimary,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Black)};Spacer(Modifier.height(28.dp));Text("Myanmar 2D\nAgent Ledger",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(14.dp));Text("အေးဂျင့်၊ ဖောက်သည်၊ ထိုးကြေးနှင့် ထီပေါက်စဉ်များကို တိကျစွာ စီမံပါ",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)};Button(onClick=onContinue,modifier=Modifier.fillMaxWidth().height(56.dp)){Text("စတင်အသုံးပြုမည်")}}} }
@Composable fun AgentListScreen(vm:LedgerViewModel,onAgent:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit,onWinning:()->Unit,onClosedDays:()->Unit,onSettings:()->Unit){
    val agents by vm.agents.collectAsStateWithLifecycle(); val days by vm.closedDays.collectAsStateWithLifecycle()
    AppScaffold("ဒိုင်များ",action={IconButton(onClick=onWinning){Icon(Icons.Default.EmojiEvents, "ထီပေါက်စဉ်")};IconButton(onClick=onClosedDays){Icon(Icons.Default.EventBusy, "ပိတ်ရက်")};IconButton(onClick=onSettings){Icon(Icons.Default.Settings, "ဆက်တင်များ")};},fab={ExtendedFloatingActionButton(onClick=onAdd, modifier=Modifier.padding(bottom=8.dp)){BilingualText("ဒိုင်အသစ်ထည့်ရန်", primaryStyle=MaterialTheme.typography.labelLarge, secondaryStyle=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onPrimary)}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(14.dp)){
            item{Text("စာရင်းနှင့် ဖောက်သည်များကို စီမံပါ",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            item{OutlinedCard(onClick=onClosedDays,modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){BilingualText("အားလုံးဆိုင်ရာ • ပိတ်ရက်", primaryStyle=MaterialTheme.typography.labelLarge, secondaryStyle=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary);Text(if(days.isEmpty())"ပိတ်ရက် မသတ်မှတ်ရသေးပါ" else "${days.size} ရက် ပိတ်ထားသည်",style=MaterialTheme.typography.titleMedium);if(days.isNotEmpty())Text(days.take(2).joinToString(" • "){it.date.displayDate()},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text("ကြည့်ရန်",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}}}
            if(agents.isEmpty()) item{EmptyState("အေးဂျင့်မရှိသေးပါ","ဒိုင်အသစ်ထည့်ရန်\nAdd Agent ကိုနှိပ်ပါ")} else items(agents,key={it.id}){agent->ListItem(headlineContent={Text(agent.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}, supportingContent={Text("လျော်ကြေးနှုန်းထား ${agent.rate}" + if(agent.phone.isNotBlank()) " • ${agent.phone}" else "",style=MaterialTheme.typography.bodySmall)}, leadingContent={Icon(Icons.Default.Store, null, tint=MaterialTheme.colorScheme.primary)}, trailingContent={IconButton({onEdit(agent.id)}){Icon(Icons.Default.Edit, "ပြင်မည်")}}, modifier=Modifier.fillMaxWidth().clickable{onAgent(agent.id)})}
            item{Spacer(Modifier.height(72.dp))}
        }
    }
}
@Composable fun EmptyState(title:String,subtitle:String){Column(Modifier.fillMaxWidth().padding(vertical=52.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center);Spacer(Modifier.height(8.dp));Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)}}
@Composable fun AgentFormScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val existing by vm.agent(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var rate by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;rate=it.rate.toString();remark=it.remark}};AppScaffold(if(id==0L)"ဒိုင်အသစ်ထည့်ရန်" else "ဒိုင်အချက်အလက်ပြင်ရန်\nEdit Agent",onBack){p->FormColumn(p){Input(name,{name=it},"အမည်\nName",true);Input(address,{address=it},"လိပ်စာ\nAddress");Input(phone,{phone=it},"ဖုန်း\nPhone",keyboard=KeyboardType.Phone);Input(rate,{rate=it},"လျော်ကြေးနှုန်းထား\nRate",true,KeyboardType.Number);Input(remark,{remark=it},"မှတ်ချက်\nRemark");FormActions(onBack,{vm.saveAgent(id,name,address,phone,rate,remark,onBack)},name.isNotBlank()&&rate.toLongOrNull()?.let{it>0}==true)}}}
@Composable fun CustomerFormScreen(vm:LedgerViewModel,id:Long,agentId:Long,onBack:()->Unit){val existing by vm.customer(id).collectAsState(initial=null);var name by rememberSaveable(id){mutableStateOf("")};var address by rememberSaveable(id){mutableStateOf("")};var phone by rememberSaveable(id){mutableStateOf("")};var remark by rememberSaveable(id){mutableStateOf("")};LaunchedEffect(existing){existing?.let{name=it.name;address=it.address;phone=it.phone;remark=it.remark}};AppScaffold(if(id==0L)"Add Customer" else "ထိုးသားအချက်အလက်ပြင်ရန်\nEdit Customer",onBack){p->FormColumn(p){Input(name,{name=it},"အမည်",true);Input(address,{address=it},"လိပ်စာ");Input(phone,{phone=it},"ဖုန်း",keyboard=KeyboardType.Phone);Input(remark,{remark=it},"မှတ်ချက်");FormActions(onBack,{vm.saveCustomer(id,agentId,name,address,phone,remark,onBack)},name.isNotBlank())}}}
@Composable private fun FormColumn(p:PaddingValues,content:@Composable ColumnScope.() -> Unit)=Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
@Composable private fun Input(value:String,onValue:(String)->Unit,label:String,required:Boolean=false,keyboard:KeyboardType=KeyboardType.Text){OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label+(if(required)" *" else ""))},singleLine=label!="မှတ်ချက်\nRemark"&&label!="မှတ်ချက်",keyboardOptions=KeyboardOptions(keyboardType=keyboard),shape=MaterialTheme.shapes.medium)}
@Composable private fun DateInput(value:String,onValue:(String)->Unit,label:String,enabled:Boolean=true){var open by rememberSaveable{mutableStateOf(false)};OutlinedTextField(value=value,onValueChange={},modifier=Modifier.fillMaxWidth(),label={Text(label)},readOnly=true,enabled=enabled,trailingIcon={if(enabled)TextButton({open=true}){Text("ရွေး")}},shape=MaterialTheme.shapes.medium);if(open){val initial=runCatching{LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull();val state=rememberDatePickerState(initialSelectedDateMillis=initial);DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{onValue(java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString())};open=false}){Text("ရွေးမည်")}},dismissButton={TextButton(onClick={open=false}){Text("မလုပ်ပါ")}}){DatePicker(state)}}}
@Composable private fun FormActions(cancel:()->Unit,save:()->Unit,enabled:Boolean){Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(cancel,Modifier.weight(1f)){Text("Cancel")};Button(save,Modifier.weight(1f),enabled=enabled){Text("Save")}}}
@Composable fun AgentDetailScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onRoute: (String) -> Unit) {
    val agent by vm.agent(id).collectAsState(initial = null); val profile=agent ?: return
    AppScaffold(profile.name,onBack,action={IconButton({onRoute("agentForm/$id")}){Icon(Icons.Default.Edit,"ပြင်ရန်")}}){p->
        LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.shapes.medium).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.Store,null,tint=MaterialTheme.colorScheme.primary);Column{Text(profile.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("လျော်ကြေးနှုန်းထား ${profile.rate}",style=MaterialTheme.typography.bodySmall);if(profile.phone.isNotBlank())Text(profile.phone,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            item{Button(onClick={onRoute("customers/$id")},modifier=Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Default.People,null);Spacer(Modifier.width(8.dp));Text("ထိုးသားများ")}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.ReceiptLong,"စုစုပေါင်းစာရင်း","total/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.Assessment,"အစီရင်ခံစာ","report/agent/$id",onRoute)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.Lock,"ပိတ်ဂဏန်း","closed/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.EmojiEvents,"ထီပေါက်စဉ်","winning/agent/$id",onRoute)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.Tune,"ကန့်သတ်ပမာဏ","limit/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.MenuBook,"လမ်းညွှန်","format",onRoute)}}}
        }
    }
}
@Composable private fun DetailActionCard(title:String, subtitle:String, route:String, onRoute:(String)->Unit) {
    OutlinedCard(onClick={onRoute(route)}, modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal=14.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.ChevronRight, null, tint=MaterialTheme.colorScheme.primary); Column { BilingualText(title, primaryStyle=MaterialTheme.typography.titleMedium, secondaryStyle=MaterialTheme.typography.labelMedium); Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) } } }
}
@Composable private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title:String, route:String, onRoute:(String)->Unit) {
    Column(Modifier.fillMaxWidth().clickable{onRoute(route)}.padding(vertical=8.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(6.dp)) { Surface(shape=androidx.compose.foundation.shape.CircleShape, color=MaterialTheme.colorScheme.primaryContainer, modifier=Modifier.size(48.dp)) { Box(contentAlignment=Alignment.Center) { Icon(icon, null, tint=MaterialTheme.colorScheme.primary) } }; Text(title, style=MaterialTheme.typography.labelLarge, textAlign=TextAlign.Center) }
}
@Composable fun CustomerListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit,onCustomer:(Long)->Unit,onAdd:()->Unit,onEdit:(Long)->Unit){
    val list by vm.customers(agentId).collectAsState(initial=emptyList())
    AppScaffold("ထိုးသားများ",onBack,fab={ExtendedFloatingActionButton(onClick=onAdd){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("ထိုးသားအသစ်ထည့်ရန်")}}){padding->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("ဒီအေးဂျင့်အောက်ရှိ ထိုးသားများ",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(list.isEmpty())item{EmptyState("ထိုးသားမရှိသေးပါ","အောက်က + ခလုတ်ကိုနှိပ်၍ စတင်ပါ")} else items(list,key={it.id}){customer->ListItem(headlineContent={Text(customer.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}, supportingContent={if(customer.phone.isNotBlank()) Text(customer.phone,style=MaterialTheme.typography.bodySmall)}, leadingContent={Icon(Icons.Default.Person, null, tint=MaterialTheme.colorScheme.primary)}, trailingContent={IconButton({onEdit(customer.id)}){Icon(Icons.Default.Edit, "ပြင်မည်")}}, modifier=Modifier.fillMaxWidth().clickable{onCustomer(customer.id)})}
    }}
}
@Composable fun CustomerDetailScreen(vm: LedgerViewModel, id: Long, onBack: () -> Unit, onRoute: (String) -> Unit) {
    val customer by vm.customer(id).collectAsState(initial=null)
    val profile=customer ?: return
    AppScaffold(profile.name,onBack,action={IconButton({onRoute("customerForm/${profile.agentId}/$id")}){Icon(Icons.Default.Edit,"ပြင်ရန်")}}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{ElevatedCard(colors=CardDefaults.elevatedCardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),shape=MaterialTheme.shapes.large){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(profile.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("ဖောက်သည်အချက်အလက်",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);if(profile.phone.isNotBlank())Text(profile.phone);if(profile.address.isNotBlank())Text(profile.address,color=MaterialTheme.colorScheme.onSurfaceVariant);if(profile.remark.isNotBlank())Text(profile.remark,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            item{Button(onClick={onRoute("bet/${profile.agentId}/$id")},modifier=Modifier.fillMaxWidth().height(52.dp)){Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("စာရင်းသွင်းရန်")}}
            item{Text("လုပ်ငန်းဆောင်တာများ",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.ReceiptLong,"စာရင်းမှတ်တမ်း","betHistory/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.Insights,"အမြန်သုံးသပ်ချက်","analysis/$id",onRoute)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){ActionTile(Icons.Default.Assessment,"အစီရင်ခံစာ","report/customer/$id",onRoute)};Column(Modifier.weight(1f)){ActionTile(Icons.Default.Tune,"ကန့်သတ်ပမာဏ","limit/$id",onRoute)}}}
            item{ActionTile(Icons.Default.EmojiEvents,"ထီပေါက်စဉ်","winning/customer/$id",onRoute)}
            item{Text("အချက်အလက်",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
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
    val existing by vm.betEntry(entryId).collectAsStateWithLifecycle(initialValue = null)
    val preview by vm.preview.collectAsStateWithLifecycle()
    val submit by vm.submit.collectAsStateWithLifecycle()
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val now = LocalDateTime.now()
    val pastDraw = date?.let {
        it.isBefore(now.toLocalDate()) ||
            (it == now.toLocalDate() && session == DrawSession.MORNING && !now.toLocalTime().isBefore(DrawSchedule.morningResultTime)) ||
            (it == now.toLocalDate() && session == DrawSession.EVENING && !now.toLocalTime().isBefore(DrawSchedule.eveningResultTime))
    } ?: false
    val visiblePreview = if (pastDraw && !backdated) null else preview
    LaunchedEffect(existing) { existing?.let { backdated = true; dateText = it.entry.drawDate.toString(); session = it.entry.drawSession; raw = it.entry.sourceText } }
    LaunchedEffect(raw, format, date, session, backdated) { date?.let { vm.refreshPreview(customerId, agentId, it, session, raw, format, entryId) } }
    LaunchedEffect(submit) { if (submit is SubmitState.Success) onBack() }
    if (showLateError) AlertDialog(
        onDismissRequest = { showLateError = false },
        title = { Text("စာရင်းသွင်း၍ မရပါ") },
        text = { Text("သင်ထည့်သွင်းသော အချက်အလက်သည် ပေါက်ဂဏန်းထွက်ပြီးဖြစ်ပါသဖြင့် ထည့်သွင်း၍မရနိုင်ပါ။ အကယ်၍ ထည့်သွင်းရန်လိုအပ်ပါက နောက်ကြောင်းပြန်စာရင်းသွင်းခြင်းကို အမှန်ခြစ်နှိပ်၍ ဖြည့်သွင်းနိုင်ပါသည်။") },
        confirmButton = { TextButton({ showLateError = false }) { Text("OK") } },
        dismissButton = { TextButton({ showLateError = false }) { Text("Cancel") } }
    )
    AppScaffold(if (entryId == 0L) "2D Form\nစာရင်းသွင်းရန်" else "2D Form\nစာရင်းပြင်ရန်", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { DateInput(dateText, { dateText = it }, "ရက်စွဲ\nDate") }
                item { Text("ထိုးမည့်အချိန်\nDraw", style = MaterialTheme.typography.titleLarge); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { DrawSession.entries.forEachIndexed { i, draw -> SegmentedButton(session == draw, { session = draw }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(draw.label) } } } }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = backdated, onCheckedChange = { backdated = it })
                        Column { Text("နောက်ကြောင်းပြန်စာရင်းသွင်းခြင်း", fontWeight = FontWeight.SemiBold); Text("Backdated Entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                item { OutlinedTextField(raw, { raw = it }, Modifier.fillMaxWidth().heightIn(min = 130.dp), label = { Text("စာရင်းထည့်ရန်\nInput Box") }, placeholder = { Text(if (format == QuickFormat.MANUAL) "ဥပမာ 10.13.14 100" else "ပုံစံအတိုင်း ထည့်ပါ") }, shape = MaterialTheme.shapes.medium) }
                item { Text("အမြန်ထည့်သွင်းပုံများ\nQuick Format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickFormat.entries.forEach { f -> FilterChip(format == f, { format = f }, label = { Text(f.label) }) } } }
                item { if (visiblePreview == null && pastDraw && !backdated) UnavailableState("ဂဏန်းထွက်ပြီးချိန်ဖြစ်ပါသဖြင့် Preview မပြနိုင်သေးပါ။\nနောက်ကြောင်းပြန်စာရင်းသွင်းခြင်းကို အမှန်ခြစ်ပါ။") else PreviewCard(visiblePreview ?: BetPreview()) }
            }
            Surface(shadowElevation = 10.dp) {
                Button(onClick = { if (pastDraw && !backdated) showLateError = true else date?.let { if (entryId == 0L) vm.confirm(customerId, agentId, it, session, raw) else existing?.let { item -> vm.editConfirm(item, it, session, raw) } } }, modifier = Modifier.fillMaxWidth().padding(AppDimens.screen).height(54.dp), enabled = (pastDraw && !backdated && raw.isNotBlank()) || (!pastDraw || backdated) && preview.canConfirm && submit !is SubmitState.Working) { Text(if (submit is SubmitState.Working) "အတည်ပြုနေသည်…" else "Confirm") }
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
    message.contains("Enter digit and amount",ignoreCase=true) -> "ဂဏန်းနှင့် ငွေပမာဏ ထည့်ပါ"
    message.contains("Closed Day",ignoreCase=true) -> "ပိတ်ရက်ဖြစ်သောကြောင့် စာရင်းသွင်း၍ မရပါ"
    else -> message
}

@Composable fun DigitListScreen(vm:LedgerViewModel,agentId:Long,customerId:Long,onBack:()->Unit){var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }; var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }; val totals by vm.customerTotals(customerId,selectedDate,session).collectAsState(initial=emptyList());val all by vm.allLimit(customerId).collectAsState(initial=null);val special by vm.specialLimits(customerId).collectAsState(initial=emptyList());val closed by vm.closedNumbers(agentId).collectAsState(initial=emptyList());val amounts=totals.associate{it.digit to it.amount};val specialMap=special.associate{it.digit to it.amount};val closedSet=closed.map{it.digit}.toSet();AppScaffold("List",onBack){p->LazyVerticalGrid(GridCells.Fixed(5),Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){item(span={GridItemSpan(5)}){Column{DateInput(dateText,{dateText=it},"ရက်စွဲ");Row{DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)},modifier=Modifier.padding(end=8.dp))}}}};item(span={GridItemSpan(5)}){Text("Limit: ${all?.amount?:"No limit"}",Modifier.padding(10.dp),style=MaterialTheme.typography.titleLarge)};items((0..99).toList()){n->val digit=n.toString().padStart(2,'0');val isClosed=digit in closedSet;val isSpecial=digit in specialMap;Surface(color=when{isClosed->MaterialTheme.colorScheme.errorContainer;isSpecial->MaterialTheme.colorScheme.secondaryContainer;else->MaterialTheme.colorScheme.surfaceVariant},shape=MaterialTheme.shapes.small){Column(Modifier.aspectRatio(.84f).padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(digit,fontWeight=FontWeight.Black);Text((amounts[digit]?:0).toString(),style=MaterialTheme.typography.labelSmall);if(isClosed)Text("Closed",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error) else if(isSpecial)Text("S:${specialMap[digit]}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary)}}}}}}
@Composable fun TotalListScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit){var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }; var session by rememberSaveable { mutableStateOf(DrawSession.MORNING) }; val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }; val totals by vm.agentTotals(agentId,selectedDate,session).collectAsState(initial=emptyList()); AppScaffold("စုစုပေါင်းစာရင်း",onBack){p->Column(Modifier.fillMaxSize().padding(p)){DateInput(dateText,{dateText=it},"ရက်စွဲ");Row(Modifier.padding(horizontal=AppDimens.screen),horizontalArrangement=Arrangement.spacedBy(8.dp)){DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw},label={Text(draw.label)})}};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("${selectedDate.displayDate()} • ${session.label}",style=MaterialTheme.typography.headlineSmall)};if(totals.isEmpty())item{Text("ဒီအချိန်အတွက် အတည်ပြုထားသော စာရင်းမရှိသေးပါ",color=MaterialTheme.colorScheme.onSurfaceVariant)}else items(totals){row->OutlinedCard{Text("${row.digit}   ${row.amount.mmk()}",Modifier.fillMaxWidth().padding(16.dp))}}}}}}
@Composable fun ClosedNumberScreen(vm:LedgerViewModel,agentId:Long,onBack:()->Unit){val numbers by vm.closedNumbers(agentId).collectAsState(initial=emptyList());var digit by rememberSaveable{mutableStateOf("")};AppScaffold("Closed Number",onBack){p->Column(Modifier.fillMaxSize().padding(p).padding(AppDimens.screen)){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(digit,{digit=it.take(2)},Modifier.weight(1f),label={Text("Digit 00–99")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));Button({vm.addClosedNumber(agentId,digit);digit=""},enabled=BetParser.validDigit(digit)){Text("Save")}};Spacer(Modifier.height(20.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){if(numbers.isEmpty())item{Text("ပိတ်ထားသောဂဏန်း မသတ်မှတ်ရသေးပါ")}else items(numbers,key={it.id}){n->ListItem(headlineContent={Text(n.digit,fontWeight=FontWeight.Black)},supportingContent={Text("All customers under this Agent are blocked")},trailingContent={TextButton({vm.removeClosedNumber(n)}){Text("Remove")}},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.errorContainer))}}}}}
@Composable fun FormatScreen(onBack:()->Unit){val rows=listOf("ပါဝါ" to "05 50 16 61 27 72 38 83 49 94","နက္ခတ်" to "07 70 18 81 24 42 35 53 69 96","အပူး" to "00 11 22 33 44 55 66 77 88 99","ညီအကို" to "20 sibling numbers","အခွေ" to "345.100 → 34 43 45 54 35 53","အခွေပူး" to "အခွေ plus doubles","ပတ်သီး" to "9.100 → 19 unique numbers","ထိပ်စည်း" to "9.100 → 90…99","နောက်ပိတ်" to "9.100 → 09…99","R / reverse" to "10.20R100 → both directions");AppScaffold("Format",onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("Input format reference",style=MaterialTheme.typography.headlineSmall)};items(rows){(title,body)->OutlinedCard{Column(Modifier.padding(16.dp)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}}}
@Composable fun CommissionScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val c by vm.customer(id).collectAsState(initial=null);var value by rememberSaveable{mutableStateOf("")};LaunchedEffect(c){c?.let{value=(it.commissionRateBasisPoints/100.0).toString().removeSuffix(".0")}};AppScaffold("Commission Rate",onBack){p->FormColumn(p){Input(value,{value=it},"Percentage",true,KeyboardType.Decimal);Text("15 means 15%. Commission uses deterministic basis-point arithmetic.",color=MaterialTheme.colorScheme.onSurfaceVariant);FormActions(onBack,{vm.updateCommission(id,value,onBack)},value.toBigDecimalOrNull()?.let{it>=java.math.BigDecimal.ZERO&&it<=java.math.BigDecimal(100)}==true)}}}
@Composable fun LimitScreen(vm:LedgerViewModel,id:Long,onBack:()->Unit){val all by vm.allLimit(id).collectAsState(initial=null);val specials by vm.specialLimits(id).collectAsState(initial=emptyList());var allText by rememberSaveable{mutableStateOf("")};var digit by rememberSaveable{mutableStateOf("")};var amount by rememberSaveable{mutableStateOf("")};LaunchedEffect(all){allText=all?.amount?.toString()?:""};AppScaffold("ကန့်သတ်ပမာဏ",onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("ကန့်သတ်ပမာဏ\nAll Limit",style=MaterialTheme.typography.titleLarge);Input(allText,{allText=it},"Blank means no All Limit",keyboard=KeyboardType.Number);Button({vm.updateAllLimit(id,allText){}}){Text("Save")}};item{HorizontalDivider();Text("ရွေးပြီးကန့်သတ်ပမာဏ\nSpecial Limit",style=MaterialTheme.typography.titleLarge);Input(digit,{digit=it.take(2)},"Digit",keyboard=KeyboardType.Number);Input(amount,{amount=it},"Amount",keyboard=KeyboardType.Number);Button({vm.addSpecialLimit(id,digit,amount);digit="";amount=""},enabled=BetParser.validDigit(digit)&&amount.toLongOrNull()?.let{it>0}==true){Text("Save")}};items(specials){limit->Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer,MaterialTheme.shapes.small).padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("${limit.digit}  →  ${limit.amount.mmk()}");TextButton({vm.removeSpecialLimit(limit)}){Text("Remove")}}}}}}
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
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val report by produceState<DrawReport?>(null, date, session, id, scope) {
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
                    OutlinedCard {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.customer.name, Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold)
                            Text(row.calculation.totalBet.mmk(), Modifier.weight(1f))
                            Text(row.calculation.payout.mmk(), Modifier.weight(1f))
                            Text(row.calculation.profitLoss.mmk(), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        }
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
    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BilingualText(if (winner == null) "ဂဏန်းမထွက်ခင်\nBefore" else "ပေါက်ဂဏန်း $winner", primaryStyle=MaterialTheme.typography.titleLarge)
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
            DateInput(dateText, { dateText = it }, "ရက်စွဲ")
            Button(onClick = { runCatching { LocalDate.parse(dateText) }.getOrNull()?.let(vm::addClosedDay) }) { Text("Save") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(days, key = { it.id }) { day -> ListItem(headlineContent = { Text(day.date.displayDate()) }, trailingContent = { TextButton(onClick = { vm.removeClosedDay(day) }) { Text("Remove") } }) } }
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
@Composable fun SettingsScreen(onBack:()->Unit){
    val context=LocalContext.current
    var language by rememberSaveable { mutableStateOf(context.getSharedPreferences("ledger_settings",0).getString("language","my") ?: "my") }
    AppScaffold("ဆက်တင်များ",onBack){p->Column(Modifier.fillMaxSize().padding(p).padding(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("ဘာသာစကား",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        OutlinedCard{Column{ListItem(headlineContent={Text("မြန်မာ")},leadingContent={RadioButton(language=="my",{language="my";context.getSharedPreferences("ledger_settings",0).edit().putString("language","my").apply()})});ListItem(headlineContent={Text("English")},leadingContent={RadioButton(language=="en",{language="en";context.getSharedPreferences("ledger_settings",0).edit().putString("language","en").apply()})})}}
        Text("ဘာသာစကားကို ရွေးချယ်ပြီး app ကို ပြန်ဝင်ပါ။",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}
@Composable fun SimpleListScreen(title:String,onBack:()->Unit,subtitle:String,rows:List<String>){AppScaffold(title,onBack){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(AppDimens.screen),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text(subtitle,style=MaterialTheme.typography.headlineSmall)};items(rows){OutlinedCard{Text(it,Modifier.fillMaxWidth().padding(16.dp),style=MaterialTheme.typography.bodyLarge)}}}}}
