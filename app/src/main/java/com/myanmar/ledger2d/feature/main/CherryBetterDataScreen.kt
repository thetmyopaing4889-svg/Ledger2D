@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

private val cherryYangon = ZoneId.of("Asia/Yangon")
private val cherryBackground = Color(0xFFFFF8FA)

@Composable
fun CherryBetterDataScreen(container: AppContainer, onBack: () -> Unit) {
    val en = LocalLanguage.current.code == "en"
    fun tr(mm: String, english: String) = if(en) english else mm
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val originalSource=remember(context){
        runCatching {
            context.assets.open("two_d_rules_source.txt")
                .bufferedReader(Charsets.UTF_8).use{it.readText()}
        }.getOrNull()
    }
    val prefs = remember(context) { context.getSharedPreferences("cherry_better_data_tickets",0) }
    var saved by remember { mutableStateOf(prefs.getString("tickets","").orEmpty()) }
    var now by remember { mutableStateOf(LocalDateTime.now(cherryYangon)) }
    LaunchedEffect(Unit) {
        while(true) { delay(60_000); now=LocalDateTime.now(cherryYangon) }
    }
    val hFlow=remember(container){container.history.observeAll()}
    val dFlow=remember(container){container.liveResults.observeAll()}
    val wFlow=remember(container){container.winners.observeAll()}
    val lFlow=remember(container){container.database.manualWinnerLockDao().observeAll()}
    val cFlow=remember(container){container.closedDays.observeAll()}
    val aFlow=remember(container){container.agents.observeAll()}
    val agents by aFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    val history by hFlow.collectAsStateWithLifecycle(initialValue=null)
    val daily by dFlow.collectAsStateWithLifecycle(initialValue=null)
    val winners by wFlow.collectAsStateWithLifecycle(initialValue=null)
    val locks by lFlow.collectAsStateWithLifecycle(initialValue=null)
    val closed by cFlow.collectAsStateWithLifecycle(initialValue=null)
    val ready=history!=null&&daily!=null&&winners!=null&&locks!=null&&closed!=null
    val closedDates=remember(closed){closed.orEmpty().map{it.date}.toSet()}
    val target=remember(now,closedDates) {
        var next=DrawSchedule.nextDraw(now)
        while(!DrawSchedule.isWorkingDate(next.date,closedDates))
            next=DrawIdentity(next.date.plusDays(1),DrawSession.MORNING)
        next
    }
    val merged=remember(history,daily,winners,locks,now,closedDates) {
        Historic2DDataSource.resolve(
            liveDaily=daily.orEmpty(),history=history.orEmpty(),
            today=now.toLocalDate(),now=now.toLocalTime(),
            winners=winners.orEmpty(),manualLocks=locks.orEmpty()
        ).draws.filterNot{it.date in closedDates}
    }
    val todayDaily=daily.orEmpty().firstOrNull{it.date==target.date}
    val fromModern=todayDaily?.modern930?.takeIf{it.matches(Regex("[0-9]{2}"))}
    val fromInternet=todayDaily?.internet930?.takeIf{it.matches(Regex("[0-9]{2}"))}
    var modernOverride by rememberSaveable(target.date.toString()){mutableStateOf("")}
    var internetOverride by rememberSaveable(target.date.toString()){mutableStateOf("")}
    val modern=modernOverride.takeIf{it.length==2}?:fromModern
    val internet=internetOverride.takeIf{it.length==2}?:fromInternet
    val report=remember(ready,merged,target,modern,internet,closedDates) {
        if(ready) CherryBetterDataEngine.calculate(merged,target,modern,internet,closedDates)
        else null
    }
    var tab by rememberSaveable{mutableStateOf(CherryTab.SUMMARY)}
    var selectedPick by remember { mutableStateOf<CherryPick?>(null) }
    var selectedOriginal by remember { mutableStateOf<CherryOriginalReference?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showAllCurrent by rememberSaveable { mutableStateOf(false) }
    var showAllBorn by rememberSaveable { mutableStateOf(false) }
    var openedCurrentRule by rememberSaveable { mutableStateOf<String?>(null) }
    var openedBornRule by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDigit by rememberSaveable { mutableStateOf<Int?>(null) }
    var savedOk by remember { mutableStateOf(false) }
    var ledgerRevision by remember { mutableIntStateOf(0) }
    val ledgerPayouts by produceState<Map<String, Long>>(
        initialValue=emptyMap(),target,agents,tab,ledgerRevision
    ) {
        if(tab != CherryTab.SUMMARY) {
            value=emptyMap()
        } else {
            val byDigit=mutableMapOf<String,Long>()
            agents.forEach{agent->
                runCatching {
                    container.bets.getAgentScenarioPayouts(agent.id,target.date,target.session)
                }.getOrNull()?.forEach{(pair,payout)->
                    byDigit[pair]=runCatching {
                        Math.addExact(byDigit[pair]?:0L,payout)
                    }.getOrDefault(Long.MAX_VALUE)
                }
            }
            value=byDigit
        }
    }
    val subtitle="${target.date} • "+tr(
        if(target.session==DrawSession.MORNING)"မနက်" else "ညနေ",
        if(target.session==DrawSession.MORNING)"AM" else "PM")
    Scaffold(
        containerColor=cherryBackground,
        topBar={
            TopAppBar(
                title={
                    Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        Text("BETTER DATA",fontWeight=FontWeight.Black,
                            color=AppColors.PrimaryDeep,style=MaterialTheme.typography.titleLarge)
                        Text(subtitle,style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}},
                actions={IconButton(onClick={showHistory=true}) {
                    Icon(Icons.Default.History,tr("မှတ်တမ်း","History"),tint=AppColors.PrimaryDeep)
                }},
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.White),
            )
        },
        bottomBar={
            NavigationBar(containerColor=Color(0xFFFFEBF0),tonalElevation=0.dp) {
                val colors=NavigationBarItemDefaults.colors(
                    selectedIconColor=AppColors.PrimaryDeep,
                    selectedTextColor=AppColors.PrimaryDeep,
                    indicatorColor=Color.White,
                    unselectedIconColor=Color(0xFF777077),
                    unselectedTextColor=Color(0xFF777077)
                )
                NavigationBarItem(tab==CherryTab.CURRENT,{tab=CherryTab.CURRENT},
                    icon={Icon(Icons.Default.Bolt,null)},
                    label={Text(tr("လက်ရှိမူ","Current"),maxLines=1)},colors=colors)
                NavigationBarItem(tab==CherryTab.BORN,{tab=CherryTab.BORN},
                    icon={Icon(Icons.Default.LocalFireDepartment,null)},
                    label={Text(tr("မွေးမူ","Born"),maxLines=1)},colors=colors)
                NavigationBarItem(tab==CherryTab.MATH,{tab=CherryTab.MATH},
                    icon={Icon(Icons.Default.Calculate,null)},
                    label={Text(tr("သင်္ချာနည်း","Math"),maxLines=1)},colors=colors)
                NavigationBarItem(tab==CherryTab.SUMMARY,{tab=CherryTab.SUMMARY},
                    icon={Icon(Icons.Default.WorkspacePremium,null)},
                    label={Text(tr("အချုပ်","Summary"),maxLines=1)},colors=colors)
            }
        }
    ){padding->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(horizontal=15.dp,vertical=14.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ){
            if(!ready){
                item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            } else if(report!=null) {
                when(tab){
                    CherryTab.CURRENT->{
                        item {
                            CherryHeading(tr("⚡ လက်ရှိအကြိမ်မူများ","⚡ Current draw rules"),
                                tr("နောက်ဆုံးအတည်ပြုဂဏန်း: ${report.last?.digit?:"—"}",
                                    "Latest confirmed: ${report.last?.digit?:"—"}"))
                        }
                        item {
                            CherryRuleFrameOverview(
                                heading=tr("ဦးစားပေး ရွေးကွက်များ","Prioritized pairs"),
                                all=report.current,topLimit=6,en=en,
                                expanded=showAllCurrent,
                                onToggle={showAllCurrent=!showAllCurrent},
                                onPick={selectedPick=it}
                            )
                        }
                        if(target.session==DrawSession.MORNING){
                            item {
                                CherryPanel {
                                    Text(tr("9:30 Modern / Internet","9:30 Modern / Internet"),
                                        fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                                    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                                        OutlinedTextField(
                                            modernOverride,
                                            {modernOverride=it.filter{ch->ch in '0'..'9'}.take(2)},
                                            modifier=Modifier.weight(1f),singleLine=true,
                                            label={Text("Modern")},placeholder={Text(fromModern?:"—")}
                                        )
                                        OutlinedTextField(
                                            internetOverride,
                                            {internetOverride=it.filter{ch->ch in '0'..'9'}.take(2)},
                                            modifier=Modifier.weight(1f),singleLine=true,
                                            label={Text("Internet")},placeholder={Text(fromInternet?:"—")}
                                        )
                                    }
                                    if(!report.has930){
                                        Text(tr("9:30 ဒေတာမစုံသေး၍ အပေါ်ကရွေးကွက်များသည် ယာယီဖြစ်သည်။",
                                            "Selections remain provisional until both 9:30 values arrive."),
                                            color=MaterialTheme.colorScheme.onSurfaceVariant,
                                            style=MaterialTheme.typography.bodySmall)
                                    }
                                    if(modernOverride.isNotEmpty()||internetOverride.isNotEmpty()){
                                        TextButton(onClick={modernOverride="";internetOverride=""}){
                                            Text(tr("မူလဒေတာပြန်သုံးရန်","Use saved data"))}
                                    }
                                }
                            }
                        }
                        item {
                            CherryHeading(tr("အကျုံးဝင်သော လက်ရှိမူများ","Applicable current rules"),
                                tr("မူတစ်ခုကိုနှိပ်ပြီး အကျုံးဝင်ပုံ၊ ချဲ့တွက်နည်းနှင့် အတွဲအားလုံး ကြည့်ရန်",
                                    "Tap a rule for its trigger, expansion and all generated pairs"))
                        }
                        val currentRules=cherryRuleGroups(report.current)
                        if(currentRules.isEmpty())item{CherryEmpty(en)}
                        items(currentRules,key={it.id}){rule->
                            CherryRuleFrameCard(
                                rule=rule,all=report.current,
                                bornEvent=null,en=en,
                                sourceText=originalSource,
                                onOriginal={selectedOriginal=it},
                                expanded=openedCurrentRule==rule.id,
                                onToggle={openedCurrentRule=
                                    if(openedCurrentRule==rule.id)null else rule.id},
                                onPick={selectedPick=it}
                            )
                        }
                        item {
                            CherryPanel {
                                Text(tr("🚫 မူအရ ဖြတ်ထားသောဂဏန်းများ",
                                    "🚫 Rule-based exclusions"),
                                    fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                                val last=report.last?.digit
                                if(last!=null && report.current.isNotEmpty()){
                                    val dead=(last[1].digitToInt()+7)%10
                                    Text(tr("နောက်ပိတ် +7 ဖြတ်မူ: ${dead} ပိတ်",
                                        "+7 rule excludes endings in $dead"),
                                        style=MaterialTheme.typography.bodyMedium)
                                    val triple=report.cuts.filter{(_,v)->
                                        v.any{it.contains("အပိုင်း (၈)")}}.keys.sorted()
                                    if(triple.isNotEmpty())
                                        Text(tr("နောက်ပိတ်အလိုက် ရှောင်ရန် ဒဲ့/ပြန်: ",
                                            "Ending-based direct/reverse cuts: ")+
                                            triple.joinToString("  •  "),
                                            style=MaterialTheme.typography.bodyMedium)
                                    Text(tr("ဖြတ်မူငြိနေသောအတွဲများကိုလည်း ဂဏန်းအားလုံးစာရင်းထဲတွင် ခွဲပြထားသည်။",
                                        "Conflicted pairs remain visible in the expanded list."),
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }else{
                                    Text(tr("လိုအပ်သောရှေ့အကြိမ်ရလဒ် မပြည့်သေးပါ။",
                                        "Previous draw inputs are unavailable."))
                                }
                            }
                        }
                    }
                    CherryTab.BORN->{
                        item {
                            CherryHeading(tr("🔥 အသက်ဝင်နေသော မွေးမူများ",
                                "🔥 Active born rules"),
                                tr("မွေးမူများ၏ ဘုံတူညီမှု","Shared results across born rules"))
                        }
                        item {
                            CherryRuleFrameOverview(
                                heading=tr("ဦးစားပေး မွေးမူရွေးကွက်များ","Prioritized born pairs"),
                                all=report.born,topLimit=5,en=en,
                                expanded=showAllBorn,
                                onToggle={showAllBorn=!showAllBorn},
                                onPick={selectedPick=it}
                            )
                        }
                        val groups=cherryRuleGroups(report.born)
                        val actualBorn=groups.filterNot{
                            it.id=="s3-week"||it.id=="s3-six"
                        }
                        val weekly=groups.filter{
                            it.id=="s3-week"||it.id=="s3-six"
                        }
                        item {
                            CherryHeading(tr("အကျုံးဝင်နေသော မွေးမူတစ်ခုချင်း","Individual active born rules"),
                                tr("မူကိုနှိပ်ပြီး စတင်ချိန်၊ သက်တမ်း၊ တွက်ချက်ပုံနှင့် အတွဲအားလုံးကို ကြည့်ရန်",
                                    "Tap each rule to see its trigger, expiry, method and all pairs"))
                        }
                        if(actualBorn.isEmpty())item{CherryEmpty(en)}
                        items(actualBorn,key={it.id}){rule->
                            CherryRuleFrameCard(
                                rule=rule,all=report.born,
                                bornEvent=report.activeRules.firstOrNull{it.id==rule.id},
                                en=en,sourceText=originalSource,
                                onOriginal={selectedOriginal=it},
                                expanded=openedBornRule==rule.id,
                                onToggle={openedBornRule=
                                    if(openedBornRule==rule.id)null else rule.id},
                                onPick={selectedPick=it}
                            )
                        }
                        if(weekly.isNotEmpty()){
                            item {
                                CherryHeading(tr("အပတ်စဉ် စောင့်ကြည့်မူများ","Weekly watch groups"),
                                    tr("သက်တမ်းရှိမွေးမူနှင့် မရောဘဲ သီးခြားပြထားသော အပတ်စဉ်အုပ်စုများ",
                                        "Weekly groups are distinct from active born rules"))
                            }
                            items(weekly,key={it.id}){rule->
                                CherryRuleFrameCard(
                                    rule=rule,all=report.born,bornEvent=null,en=en,
                                    sourceText=originalSource,
                                    onOriginal={selectedOriginal=it},
                                    expanded=openedBornRule==rule.id,
                                    onToggle={openedBornRule=
                                        if(openedBornRule==rule.id)null else rule.id},
                                    onPick={selectedPick=it}
                                )
                            }
                        }
                    }
                    CherryTab.MATH->{
                        item{
                            CherryMathFrame(
                                report=report,
                                originalSource=originalSource,
                                en=en,
                                onPick={selectedPick=it},
                                onOriginal={selectedOriginal=it}
                            )
                        }
                    }
                    CherryTab.SUMMARY->{
                        item{CherryHeading(tr("👑 ဒီအကြိမ်အတွက် နောက်ဆုံးအချုပ်",
                            "👑 Final selections"),
                            tr("မူနှင့်တွက်နည်းများမှ စစ်ထုတ်ရွေးချယ်မှု",
                                "Selected across rule groups"))}
                        item {
                            CherryPanel {
                                Text(tr("ရွေးချယ်ထားသော ဂဏန်းတွဲများ",
                                    "Selected number pairs"),
                                    fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                                CherryBalls(report.visible(CherryTab.SUMMARY,3),
                                    {selectedPick=it},en)
                                val chosen=report.visible(CherryTab.SUMMARY,3)
                                if(chosen.isNotEmpty()){
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                        OutlinedButton(
                                            onClick={clipboard.setText(
                                                AnnotatedString(chosen.joinToString(" • "){it.pair}))},
                                            modifier=Modifier.weight(1f)
                                        ){
                                            Icon(Icons.Default.ContentCopy,null,Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr("ကူးမည်","Copy"))
                                        }
                                        Button(onClick={
                                            val snapshot=now.toString()
                                            val entry="${target.date} ${target.session.name} | " +
                                                chosen.joinToString(","){it.pair} + " | $snapshot"
                                            val next=(listOf(entry)+saved.lines().filter{it.isNotBlank()})
                                                .distinct().take(40).joinToString("\n")
                                            val done=prefs.edit().putString("tickets",next).commit()
                                            if(done){saved=next;savedOk=true}
                                        },modifier=Modifier.weight(1f)){
                                            Icon(Icons.Default.BookmarkBorder,null,Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(tr("သိမ်းမည်","Save"))
                                        }
                                    }
                                    if(savedOk)Text(tr("မှတ်တမ်းသိမ်းပြီးပါပြီ။",
                                        "Saved to history."),
                                        style=MaterialTheme.typography.bodySmall,
                                        color=AppColors.PrimaryDeep)
                                }
                            }
                        }
                        item{
                            CherryPanel {
                                Text(tr("🔎 ဘာကြောင့် ရွေးထားသလဲ","🔎 Winning DNA"),
                                    fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                                report.visible(CherryTab.SUMMARY,3).forEach{p->
                                    Surface(onClick={selectedPick=p},color=AppColors.Blush,
                                        shape=RoundedCornerShape(10.dp)){
                                        Row(Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment=Alignment.CenterVertically){
                                            Text(p.pair,fontWeight=FontWeight.Black,
                                                fontSize=24.sp,color=AppColors.PrimaryDeep)
                                            Spacer(Modifier.width(10.dp))
                                            Text(tr("${p.familyCount} မူအုပ်စု  •  ${p.sourceCount} ကဏ္ဍ",
                                                "${p.familyCount} rule families  •  ${p.sourceCount} sections"),
                                                modifier=Modifier.weight(1f),
                                                style=MaterialTheme.typography.bodySmall)
                                            Icon(Icons.Default.ChevronRight,null,
                                                tint=AppColors.PrimaryDeep)
                                        }
                                    }
                                }
                                if(report.summary.isEmpty())CherryEmpty(en)
                            }
                        }
                        item {
                            CherryPanel {
                                Text(tr("🎛️ ကိုယ်ပိုင်ဂဏန်းရွေးကြည့်ရန်",
                                    "🎛️ Personal digit filter"),
                                    fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                                Row(Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                    (0..9).forEach{d->
                                        FilterChip(
                                            selected=selectedDigit==d,
                                            onClick={selectedDigit=if(selectedDigit==d)null else d},
                                            label={Text("$d")}
                                        )
                                    }
                                }
                                if(selectedDigit!=null){
                                    val filtered=report.ranked.filter{
                                        it.pair.contains(selectedDigit.toString())}.take(4)
                                    CherryBalls(filtered,{selectedPick=it},en)
                                } else Text(tr("ဂဏန်းတစ်လုံးကို ရွေးပြီး စာရင်းထဲမှ စစ်ကြည့်နိုင်သည်။",
                                    "Choose a digit to filter the ranked list."),
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            CherryPanel {
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Text(tr("🛡️ ဒိုင်စာရင်းနှင့် နှိုင်းယှဉ်မှု",
                                        "🛡️ Ledger payout exposure"),
                                        fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep,
                                        modifier=Modifier.weight(1f))
                                    IconButton(onClick={ledgerRevision++}) {
                                        Icon(Icons.Default.Refresh,null,
                                            tint=AppColors.PrimaryDeep)
                                    }
                                }
                                val selected=report.summary.filter{
                                    (ledgerPayouts[it.pair]?:0L)>0L
                                }
                                if(ledgerPayouts.isEmpty()) {
                                    Text(tr("ဒီအကြိမ်အတွက် ဒိုင်စာရင်းထိုးငွေ မရှိသေးပါ။",
                                        "No ledger stake entries for this draw."),
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if(selected.isEmpty()) {
                                    Text(tr("ရွေးထားသောဂဏန်းများပေါ် ထိုးငွေမရှိသေးပါ။",
                                        "No stakes on selected numbers."),
                                        style=MaterialTheme.typography.bodySmall)
                                } else {
                                    selected.forEach{p->
                                        Row(Modifier.fillMaxWidth()){
                                            Text(p.pair,fontWeight=FontWeight.Black,
                                                color=AppColors.PrimaryDeep,
                                                modifier=Modifier.weight(1f))
                                            Text(tr("ပေါက်လျှင်ပေးရန် ","Payout if drawn ") +
                                                String.format("%,d",ledgerPayouts[p.pair]?:0L),
                                                style=MaterialTheme.typography.bodySmall)
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
    selectedPick?.let{p->
        AlertDialog(
            onDismissRequest={selectedPick=null},
            title={Text(
                if(tab==CherryTab.CURRENT||tab==CherryTab.BORN)
                    tr("${p.pair} အတွဲအကြောင်း","${p.pair} • Pair details")
                else tr("${p.pair} • ရွေးချယ်ရသည့်အကြောင်း",
                    "${p.pair} • Why this pair"),
                color=AppColors.PrimaryDeep)},
            text={
                Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(10.dp)){
                    if(tab==CherryTab.CURRENT||tab==CherryTab.BORN){
                        val pool=if(tab==CherryTab.CURRENT)report?.current.orEmpty()
                            else report?.born.orEmpty()
                        val eligible=pool.filter{it.cuts.isEmpty()}
                        val rank=eligible.indexOfFirst{it.pair==p.pair}
                        if(p.cuts.isNotEmpty()){
                            Text(tr("ဒီအတွဲကို ရှောင်ရန်မူက ဖြတ်ထားလို့ " +
                                    "ဦးစားပေးစာရင်းမှာ မထည့်ထားပါ။",
                                "A rule excludes this pair from the priority list."),
                                color=MaterialTheme.colorScheme.error,
                                fontWeight=FontWeight.SemiBold)
                        }else if(rank>=0){
                            Text(tr("ဦးစားပေး အဆင့် ${rank+1}",
                                "Priority rank ${rank+1}"),
                                color=AppColors.PrimaryDeep,
                                fontWeight=FontWeight.Bold)
                        }
                        Text(tr("ဒီဂဏန်းကို မူ ${p.familyCount} ခုက ထောက်ခံထားပါတယ်။",
                            "Supported by ${p.familyCount} rules."),
                            style=MaterialTheme.typography.bodyMedium)
                        Text(tr("ကိုက်ညီတဲ့မူတွေ ပိုများတဲ့ဂဏန်းကို ဦးစားပေးထားပြီး " +
                                "ရှောင်ရန်မူနဲ့ ငြိတဲ့အတွဲတွေကို ချန်ထားပါတယ်။",
                            "More matching rules rank higher; cut-rule conflicts are excluded."),
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color=AppColors.Blush)
                        Text(tr("ဘယ်မူတွေကြောင့် ပါလာသလဲ","Supporting rules"),
                            color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
                        p.evidence.distinctBy{it.id to it.origin}.forEach{ev->
                            Text("• "+CherryRuleWording.title(ev.id,ev.name),
                                fontWeight=FontWeight.SemiBold,
                                style=MaterialTheme.typography.bodyMedium)
                            Text(CherryRuleWording.pairReason(ev,p.pair),
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            CherryRuleSources.lookup(originalSource,ev.id,ev.detail)?.let{ref->
                                TextButton(
                                    onClick={
                                        selectedPick=null
                                        selectedOriginal=ref
                                    },
                                    contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)
                                ){
                                    Icon(Icons.Default.MenuBook,null,Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(tr("မူရင်း • ${ref.label} · ကြည့်ရန်",
                                        "Original • ${ref.label} · Read"),
                                        style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if(p.cuts.isNotEmpty()){
                            HorizontalDivider(color=AppColors.Blush)
                            Text(tr("ရှောင်ရန်မူနှင့် ငြိနေသောအကြောင်း","Cut-rule conflicts"),
                                color=MaterialTheme.colorScheme.error,
                                fontWeight=FontWeight.SemiBold)
                            p.cuts.forEach{reason->
                                Text(CherryRuleWording.cutReason(reason),
                                    color=MaterialTheme.colorScheme.error,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }else if(tab==CherryTab.MATH){
                        CherryMathPairDetail(p,originalSource,en,
                            onOriginal={selectedPick=null;selectedOriginal=it})
                    }else{
                        p.evidence.distinctBy{it.id to it.origin}.forEach{ev->
                            Text("• ${ev.name}",fontWeight=FontWeight.Bold,
                                style=MaterialTheme.typography.bodyMedium)
                            Text(ev.detail,style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(p.cuts.isNotEmpty())Text(p.cuts.joinToString("\n"),
                            color=MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton={TextButton(onClick={selectedPick=null}){Text(tr("ပိတ်","Close"))}}
        )
    }
    selectedOriginal?.let{ref->
        AlertDialog(
            onDismissRequest={selectedOriginal=null},
            title={Text(tr("မူရင်းမူကို ဖတ်ရန်","Read original rule"),
                color=AppColors.PrimaryDeep)},
            text={
                Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text(ref.label,color=AppColors.PrimaryDeep,
                        style=MaterialTheme.typography.labelMedium,
                        fontWeight=FontWeight.SemiBold)
                    Text(ref.quote,style=MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton={
                TextButton(onClick={selectedOriginal=null}){Text(tr("ပိတ်","Close"))}
            }
        )
    }
    if(showHistory){
        AlertDialog(
            onDismissRequest={showHistory=false},
            title={Text(tr("သိမ်းထားသော ခန့်မှန်းချက်များ","Saved selections"))},
            text={
                Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)){
                    val entries=saved.lines().filter{it.isNotBlank()}
                    if(entries.isEmpty())Text(tr("မှတ်တမ်းမရှိသေးပါ။","No saved selections yet."))
                    entries.take(30).forEach{entry->
                        Text(entry,style=MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color=AppColors.Blush)
                    }
                }
            },
            confirmButton={TextButton(onClick={showHistory=false}){Text(tr("ပိတ်","Close"))}}
        )
    }
}

@Composable private fun CherryPanel(content:@Composable ColumnScope.()->Unit){
    Surface(
        color=Color.White,
        shape=RoundedCornerShape(20.dp),
        border=BorderStroke(1.dp,Color(0xFFF4CFDA))
    ){
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable private fun CherryHeading(title:String,description:String){
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(title,color=AppColors.PrimaryDeep,
            fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge)
        Text(description,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun CherryEmpty(en:Boolean) {
    Text(if(en)"No matching pair for this draw." else "ဒီအကြိမ်အတွက် ကိုက်ညီသောဂဏန်းမတွေ့ပါ။",
        style=MaterialTheme.typography.bodyMedium,
        color=MaterialTheme.colorScheme.onSurfaceVariant,
        modifier=Modifier.padding(vertical=10.dp))
}
@Composable private fun CherryBalls(
    picks:List<CherryPick>,onTap:(CherryPick)->Unit,en:Boolean
){
    if(picks.isEmpty()){CherryEmpty(en);return}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
        picks.forEachIndexed{index,p->
            Surface(onClick={onTap(p)},modifier=Modifier.weight(1f),
                shape=RoundedCornerShape(15.dp),
                color=if(index==0)AppColors.Primary else Color(0xFFFFE8F0),
                border=if(index==0)null else BorderStroke(1.dp,Color(0xFFF4C6D4))){
                Column(Modifier.padding(vertical=12.dp,horizontal=1.dp),
                    verticalArrangement=Arrangement.spacedBy(3.dp),
                    horizontalAlignment=Alignment.CenterHorizontally){
                    Text(if(index==0)(if(en)"Main" else "အဓိက")
                        else if(en)"Other" else "အရံ",
                        style=MaterialTheme.typography.labelSmall,
                        color=if(index==0)Color.White else AppColors.PrimaryDeep,
                        maxLines=1)
                    Text(p.pair,fontWeight=FontWeight.Black,fontSize=25.sp,
                        color=if(index==0)Color.White else AppColors.PrimaryDeep)
                }
            }
        }
    }
}
@Composable private fun CherryFormulaRow(value:CherryValue){
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text(value.name,fontWeight=FontWeight.SemiBold,
                modifier=Modifier.weight(1f),
                style=MaterialTheme.typography.bodyMedium)
            Text(value.digit.toString(),fontWeight=FontWeight.Black,
                style=MaterialTheme.typography.titleLarge,color=AppColors.PrimaryDeep)
        }
        Text(value.formula,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color=Color(0xFFFFE5EC))
    }
}
