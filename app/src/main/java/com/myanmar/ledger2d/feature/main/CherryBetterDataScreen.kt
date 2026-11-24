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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.myanmar.ledger2d.core.repository.CherrySuccessSnapshotStore
import java.time.LocalDateTime
import java.time.ZoneId

private val cherryYangon = ZoneId.of("Asia/Yangon")
private val cherryBackground = Color(0xFFFCEEF3)

@Composable
fun CherryBetterDataScreen(container: AppContainer, onBack: () -> Unit) {
    val en = LocalLanguage.current.code == "en"
    fun tr(mm: String, english: String) = if(en) english else mm
    val context = LocalContext.current
    val originalSource=remember(context){
        runCatching {
            context.assets.open("two_d_rules_source.txt")
                .bufferedReader(Charsets.UTF_8).use{it.readText()}
        }.getOrNull()
    }
    var now by remember { mutableStateOf(LocalDateTime.now(cherryYangon)) }
    LaunchedEffect(Unit) {
        while(true) { delay(60_000); now=LocalDateTime.now(cherryYangon) }
    }
    val hFlow=remember(container){container.history.observeAll()}
    val dFlow=remember(container){container.liveResults.observeAll()}
    val wFlow=remember(container){container.winners.observeAll()}
    val lFlow=remember(container){container.database.manualWinnerLockDao().observeAll()}
    val cFlow=remember(container){container.closedDays.observeAll()}
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
    // Long-running V3 scope lives at SCREEN level, not inside a LazyColumn
    // item: scrolling the Math panel offscreen must not cancel a live search.
    // Service construction does NOT start formula discovery.
    val v3Service=remember(context,container){
        CherryV2DiscoveryService(context,container.database)
    }
    val v3Scope=rememberCoroutineScope()
    val v3Search=remember(v3Service,target,v3Scope){
        CherryV3DiscoveryController(v3Service,target,v3Scope)
    }
    DisposableEffect(v3Search){
        onDispose{v3Search.cancel()}
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
    // A V3 choice is bound to one draw, never silently reused next session.
    // Reading choices does not invoke the expensive discovery engine.
    val v3ChoicePrefs = remember(context) {
        context.getSharedPreferences("cherry_v3_manual_choices", 0)
    }
    val drawKey = target.date.toString() + "_" + target.session.name
    val v3Choices = remember(drawKey, v3ChoicePrefs) {
        mutableStateMapOf<CherryDiscoveryKind, CherryV3FormulaSelection>().apply {
            CherryDiscoveryKind.entries.forEach { kind ->
                val raw = v3ChoicePrefs.getString(drawKey + "_" + kind.name, null)
                val parts = raw?.split("|").orEmpty()
                if (parts.size == 3) {
                    val digit = parts[1].toIntOrNull()
                    val streak = parts[2].toIntOrNull()
                    if (digit != null && digit in 0..9 && streak != null && streak >= 3 &&
                        parts[0].isNotBlank()) {
                        put(kind, CherryV3FormulaSelection(kind, digit, streak, parts[0]))
                    }
                }
            }
        }
    }
    val v3Plan = CherryV3PairPlanner.plan(v3Choices.values)
    var showMathOther by rememberSaveable(drawKey) { mutableStateOf(false) }
    var tab by rememberSaveable{mutableStateOf(CherryTab.SUMMARY)}
    var selectedPick by remember { mutableStateOf<CherryPick?>(null) }
    var selectedOriginal by remember { mutableStateOf<CherryOriginalReference?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showAllCurrent by rememberSaveable { mutableStateOf(false) }
    var showAllBorn by rememberSaveable { mutableStateOf(false) }
    var openedCurrentRule by rememberSaveable { mutableStateOf<String?>(null) }
    var openedBornRule by rememberSaveable { mutableStateOf<String?>(null) }
    // Only pre-result rule projections are recorded; historical results
    // never backfill or silently revise earlier predictions.
    val successStore = remember(context) { CherrySuccessSnapshotStore(context) }
    var successSnapshots by remember(successStore) {
        mutableStateOf(successStore.load())
    }
    val mathForSnapshot = v3Choices.toMap()
    var selectedSuccessDraw by remember { mutableStateOf<DrawIdentity?>(null) }
    LaunchedEffect(target, report, mathForSnapshot) {
        if(report != null) {
            val capturedAt = System.currentTimeMillis()
            if(CherrySuccessEngine.beforeCutoff(target,capturedAt)) {
                val snapshot = CherrySuccessEngine.createSnapshot(
                    report,mathForSnapshot.values,capturedAt,
                    "modern930=" + (modern ?: "none") +
                        ";internet930=" + (internet ?: "none"))
                successSnapshots = withContext(Dispatchers.IO) {
                    successStore.recordBeforeCutoff(snapshot)
                }
            }
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
                navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,tr("နောက်သို့","Back"),tint=AppColors.PrimaryDeep)}},
                actions={IconButton(onClick={showHistory=true}) {
                    Icon(Icons.Default.History,tr("မှတ်တမ်း","History"),tint=AppColors.PrimaryDeep)
                }},
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFFFFF3F7)),
            )
        },
        bottomBar={
            NavigationBar(containerColor=Color(0xFFFFEBF0),tonalElevation=0.dp) {
                val colors=NavigationBarItemDefaults.colors(
                    selectedIconColor=AppColors.PrimaryDeep,
                    selectedTextColor=AppColors.PrimaryDeep,
                    indicatorColor=Color.White,
                    unselectedIconColor=Color(0xFF62545B),
                    unselectedTextColor=Color(0xFF62545B)
                )
                NavigationBarItem(tab==CherryTab.CURRENT,{tab=CherryTab.CURRENT},
                    icon={Icon(Icons.Default.Bolt,null)},
                    label={Text(tr("လက်ရှိမူ","Current"),maxLines=2,textAlign=TextAlign.Center)},colors=colors)
                NavigationBarItem(tab==CherryTab.BORN,{tab=CherryTab.BORN},
                    icon={Icon(Icons.Default.LocalFireDepartment,null)},
                    label={Text(tr("မွေးမူ","Born"),maxLines=2,textAlign=TextAlign.Center)},colors=colors)
                NavigationBarItem(tab==CherryTab.MATH,{tab=CherryTab.MATH},
                    icon={Icon(Icons.Default.Calculate,null)},
                    label={Text(tr("သင်္ချာနည်း","Math"),maxLines=2,textAlign=TextAlign.Center)},colors=colors)
                NavigationBarItem(tab==CherryTab.SUMMARY,{tab=CherryTab.SUMMARY},
                    icon={Icon(Icons.Default.EmojiEvents,null)},
                    label={Text(tr("အောင်ချက်","Success"),maxLines=2,textAlign=TextAlign.Center)},colors=colors)
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
                        item {
                            CherryV3PickOverview(v3Plan, en, showMathOther) {
                                showMathOther = !showMathOther
                            }
                        }
                        item { CherryV3ChosenOverview(v3Choices, en) }
                        // Manual discovery remains the third frame. No auto-search.
                        item {
                            CherryV3DiscoveryPanel(
                                controller = v3Search,
                                target = target,
                                en = en,
                                selected = v3Choices,
                                onFormulaSelected = { formula ->
                                    if (formula.target == target &&
                                        CherrySuccessEngine.beforeCutoff(
                                            target,System.currentTimeMillis())) {
                                        val selection = CherryV3FormulaSelection(
                                            formula.kind, formula.predictedDigit,
                                            formula.historicalStreak, formula.formulaId)
                                        v3Choices[formula.kind] = selection
                                        v3ChoicePrefs.edit().putString(
                                            drawKey + "_" + formula.kind.name,
                                            listOf(selection.formulaId, selection.digit,
                                                selection.streak).joinToString("|")
                                        ).apply()
                                    }
                                },
                                onFormulaDeselected = { formula ->
                                    if (formula.target == target &&
                                        CherrySuccessEngine.beforeCutoff(
                                            target,System.currentTimeMillis()) &&
                                        v3Choices[formula.kind]?.let {
                                            it.formulaId == formula.formulaId &&
                                                it.digit == formula.predictedDigit
                                        } == true) {
                                        if(v3ChoicePrefs.edit().remove(
                                            drawKey + "_" + formula.kind.name).commit()) {
                                            v3Choices.remove(formula.kind)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    CherryTab.SUMMARY->{
                        item {
                            CherrySuccessContent(
                                confirmedHistory=merged,
                                snapshots=successSnapshots,
                                selectedDraw=selectedSuccessDraw,
                                onLatest={selectedSuccessDraw=null},
                                onHistory={showHistory=true},
                                en=en,
                            )
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
                                    Text(if(ref.label.startsWith("ဓာတ်ပုံအကျဉ်းချုပ်"))
                                        tr("ရင်းမြစ် • ${ref.label} · ကြည့်ရန်",
                                            "Source summary • Read")
                                    else tr("မူရင်း • ${ref.label} · ကြည့်ရန်",
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
            title={Text(if(ref.label.startsWith("ဓာတ်ပုံအကျဉ်းချုပ်"))
                    tr("ရင်းမြစ်အကျဉ်းချုပ်", "Source summary")
                else tr("မူရင်းမူကို ဖတ်ရန်","Read original rule"),
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
    if(showHistory) {
        CherrySuccessHistoryDialog(
            confirmedHistory=merged,
            snapshots=successSnapshots,
            en=en,
            onSelected={ draw ->
                selectedSuccessDraw=draw
                showHistory=false
                tab=CherryTab.SUMMARY
            },
            onDismiss={showHistory=false},
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


// V3 is selected manually; only this cheap projection runs during recomposition.
@Composable private fun CherryV3PickOverview(
    plan: CherryV3PairPlan, en: Boolean, expanded: Boolean, onToggle: () -> Unit,
) {
    CherryPanel {
        Text(if(en) "Prioritized pairs" else "ဦးစားပေးရွေးကွက်များ",
            fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
        if (plan.main.isEmpty()) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Surface(modifier=Modifier.weight(1f),
                        color=Color(0xFFFFEDF2),
                        shape=RoundedCornerShape(12.dp)) {
                        Box(Modifier.height(62.dp),contentAlignment=Alignment.Center) {
                            Text("??",fontSize=23.sp,fontWeight=FontWeight.Black,
                                color=AppColors.PrimaryDeep)
                        }
                    }
                }
            }
        } else {
            CherryV3PairTiles(plan.main, true)
        }
        if (plan.remaining.isNotEmpty()) {
            TextButton(onClick=onToggle) {
                Text(if(expanded) {
                    if(en) "Hide other pairs" else "ကျန်အကွက်များ ပိတ်ရန်"
                } else {
                    if(en) "View other pairs" else "ကျန်အကွက်များကြည့်ရန်"
                })
            }
            if(expanded) CherryV3PairTiles(plan.remaining, false)
        }
    }
}

@Composable private fun CherryV3ChosenOverview(
    choices: Map<CherryDiscoveryKind, CherryV3FormulaSelection>, en: Boolean,
) {
    CherryPanel {
        Text(if(en) "Selected formulas" else "ရွေးချယ်ထားသော Formula များ",
            fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            CherryDiscoveryKind.entries.forEach { kind ->
                val choice = choices[kind]
                Surface(modifier=Modifier.weight(1f),
                    color=Color(0xFFFFEDF2),
                    shape=RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(vertical=11.dp,horizontal=2.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(when(kind) {
                            CherryDiscoveryKind.LONE -> if(en) "Lone" else "လုံးပိုင်"
                            CherryDiscoveryKind.WAM -> if(en) "Wam" else "ဝမ်းချိန်း"
                            CherryDiscoveryKind.BREAK -> if(en) "Break" else "ဘရိတ်"
                        },style=MaterialTheme.typography.labelSmall,maxLines=1)
                        Text(choice?.digit?.toString() ?: "–",
                            fontSize=23.sp,fontWeight=FontWeight.Black,
                            color=AppColors.PrimaryDeep)
                        Text(choice?.let {
                            "★".repeat(it.stars) + "☆".repeat(5-it.stars)
                        } ?: "☆☆☆☆☆",
                            fontSize=11.sp,maxLines=1,
                            color=AppColors.PrimaryDeep)
                    }
                }
            }
        }
    }
}

@Composable private fun CherryV3PairTiles(pairs: List<String>, main: Boolean) {
    if (pairs.isEmpty()) return
    val columns = minOf(3, pairs.size)
    pairs.chunked(columns).forEach { line ->
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            line.forEach { pair ->
                Surface(modifier=Modifier.weight(1f),
                    color=if(main) AppColors.Primary else Color(0xFFFFEDF2),
                    shape=RoundedCornerShape(12.dp)) {
                    Box(Modifier.height(57.dp),contentAlignment=Alignment.Center) {
                        Text(pair,fontSize=24.sp,fontWeight=FontWeight.Black,
                            color=if(main) Color.White else AppColors.PrimaryDeep)
                    }
                }
            }
            repeat(columns - line.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
