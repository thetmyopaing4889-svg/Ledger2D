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
    val originalSource by produceState<String?>(null,context) {
        value=withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("two_d_rules_source.txt")
                    .bufferedReader(Charsets.UTF_8).use{it.readText()}
            }.getOrNull()
        }
    }
    var now by remember { mutableStateOf(LocalDateTime.now(cherryYangon).withSecond(0).withNano(0)) }
    LaunchedEffect(Unit) {
        // Cheap boundary check; the state changes only once per minute.
        while(true) { delay(1_000); now=LocalDateTime.now(cherryYangon).withSecond(0).withNano(0) }
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
    val inputsReady=history!=null&&daily!=null&&winners!=null&&locks!=null&&closed!=null
    val closedDates=remember(closed){closed.orEmpty().map{it.date}.toSet()}
    // Freeze the actual Room inputs at request creation. A live reference or
    // updatedAt-only change cannot invalidate the confirmed-result projection.
    val historyInput=history.orEmpty()
    val dailyInput=daily.orEmpty()
    val winnerInput=winners.orEmpty()
    val lockInput=locks.orEmpty()
    val dailyResultKey=remember(dailyInput) { dailyInput.map {
        listOf(it.date,it.morning2d,it.evening2d,it.morningSourceAt,it.eveningSourceAt)
    } }
    val day=now.toLocalDate()
    val boundary=CherryWorkClock.resultBoundary(now)
    val projectionKey:List<Any?> = remember(inputsReady,historyInput,dailyResultKey,
        winnerInput,lockInput,closedDates,day,boundary) {
        listOf(inputsReady,historyInput,dailyResultKey,winnerInput,lockInput,closedDates,day,boundary)
    }
    val projected by produceState<CherryWorkResult<List<Any?>,List<HistoricDraw>>?>(null,projectionKey) {
        if(inputsReady) {
            val result=withContext(Dispatchers.Default) {
                Historic2DDataSource.resolve(
                    liveDaily=dailyInput,history=historyInput,today=day,now=boundary,
                    winners=winnerInput,manualLocks=lockInput,cherryClosedDates=closedDates
                ).draws
            }
            value=CherryWorkResult(projectionKey,result)
        } else value=null
    }
    val freshHistory=projected?.current(projectionKey)
    val ready=inputsReady && freshHistory!=null
    // Keep the previous projection for stable navigation while refreshing;
    // no capture or report computation may use it until the key matches.
    val merged=freshHistory ?: projected?.value.orEmpty()
    // The exact same confirmed result projection is used by Success.
    val successStore = remember(context) { CherrySuccessSnapshotStore(context) }
    var successSnapshots by remember(successStore) {
        mutableStateOf(emptyList<CherrySuccessSnapshot>())
    }
    var archiveReady by remember(successStore) { mutableStateOf(false) }
    var archiveFailed by remember(successStore) { mutableStateOf(false) }
    LaunchedEffect(successStore) {
        try {
            successSnapshots=withContext(Dispatchers.IO){successStore.load()}
            archiveReady=true
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(_:Exception) { archiveFailed=true }
    }
    val confirmedKeys=remember(merged) {
        merged.filter { it.digit.matches(Regex("[0-9]{2}")) }
            .map { DrawIdentity(it.date,it.session) }.toSet()
    }
    val target=remember(now,closedDates,confirmedKeys,successSnapshots) {
        CherryPendingDraw.select(now,closedDates,confirmedKeys,successSnapshots.map { it.target }.toSet())
    }
    val captureOpen=now.isBefore(CherrySuccessEngine.cutoff(target)) && target !in confirmedKeys
    val waitingForResult=CherryPendingDraw.isDue(target,now) && target !in confirmedKeys
    val archived=successSnapshots.firstOrNull { it.target==target }
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
    val todayDaily=daily.orEmpty().firstOrNull{it.date==target.date}
    val fromModern=todayDaily?.modern930?.takeIf{it.matches(Regex("[0-9]{2}"))}
    val fromInternet=todayDaily?.internet930?.takeIf{it.matches(Regex("[0-9]{2}"))}
    var modernOverride by rememberSaveable(target.date.toString()){mutableStateOf("")}
    var internetOverride by rememberSaveable(target.date.toString()){mutableStateOf("")}
    val savedInputs=archived?.inputEvidence.orEmpty().split(';')
        .mapNotNull { val parts=it.split('=',limit=2); if(parts.size==2) parts[0] to parts[1] else null }.toMap()
    fun input(key:String, override:String, fallback:String?):String? =
        if(!captureOpen && key in savedInputs)
            savedInputs[key]?.takeIf { it.matches(Regex("[0-9]{2}")) }
        else override.takeIf{it.length==2}?:fallback
    val modern=input("modern930",modernOverride,fromModern)
    val internet=input("internet930",internetOverride,fromInternet)
    // Display reference values for the target session only. Afternoon references
    // are read-only and never replace the existing morning formula inputs.
    val referenceDisplay=cherrySessionReference(target.session,modern,internet,
        todayDaily?.modern200,todayDaily?.internet200)
    val calculationKey:List<Any?> = remember(merged,target,modern,internet,closedDates) {
        listOf(merged,target,modern,internet,closedDates)
    }
    val calculation by produceState<CherryWorkResult<List<Any?>,CherryReport>?>(null,
        ready,calculationKey) {
        if(ready) {
            val result=withContext(Dispatchers.Default) {
                CherryBetterDataEngine.calculate(merged,target,modern,internet,closedDates)
            }
            value=CherryWorkResult(calculationKey,result)
        }
    }
    val calculated=if(ready)calculation?.current(calculationKey) else null
    // While inputs refresh, the last same-target report keeps fields mounted.
    // It is explicitly marked as refreshing and NEVER used for archive writes.
    val previousReport=calculation?.value?.takeIf{it.target==target}
    var frozen by remember(target) { mutableStateOf<CherryReport?>(null) }
    SideEffect { if(captureOpen && calculated!=null && frozen!=calculated) frozen=calculated }
    val report=if(captureOpen) calculated ?: previousReport else frozen ?: calculated
    val refreshing=ready && calculated==null && (captureOpen || frozen==null)
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
    var edit930 by remember(target,tab) { mutableStateOf(false) }
    var openedRule by remember(target,tab) { mutableStateOf<String?>(null) }
    var openedCut by remember(target,tab) { mutableStateOf<CherryCutKind?>(null) }
    val premiumTab=tab==CherryTab.CURRENT || tab==CherryTab.BORN
    val premiumKey=remember(successSnapshots,merged,target) { Triple(successSnapshots,merged,target) }
    val premiumResult by produceState<CherryWorkResult<Triple<List<CherrySuccessSnapshot>,List<HistoricDraw>,DrawIdentity>,CherryPremiumScores>?>(null,
        premiumTab,ready,archiveReady,premiumKey) {
        if(premiumTab && ready && archiveReady) {
            val result=withContext(Dispatchers.Default) { cherryPremiumScores(successSnapshots,merged,target) }
            value=CherryWorkResult(premiumKey,result)
        }
    }
    val premiumScores=premiumResult?.current(premiumKey) ?: CherryPremiumScores()
    val currentGroups=remember(report){cherryRuleGroups(report?.current.orEmpty())}
    val bornGroups=remember(report){cherryRuleGroups(report?.born.orEmpty())}
    val cutGroups=remember(report){cherryCutGroups(report?.cutEvidence.orEmpty())}
    // Only pre-result rule projections are recorded; historical results
    // never backfill or silently revise earlier predictions.
    val mathForSnapshot = v3Choices.toMap()
    var selectedSuccessDraw by remember { mutableStateOf<DrawIdentity?>(null) }
    LaunchedEffect(target, calculated, calculationKey, mathForSnapshot, captureOpen,archiveReady,archiveFailed,ready) {
        if(calculated != null && ready && captureOpen && archiveReady && !archiveFailed) {
            val capturedAt = System.currentTimeMillis()
            if(CherrySuccessEngine.beforeCutoff(target,capturedAt)) {
                try {
                    val snapshot = withContext(Dispatchers.Default) {
                        CherrySuccessEngine.createSnapshot(
                            calculated,mathForSnapshot.values,capturedAt,
                            "modern930=" + (modern ?: "none") +
                                ";internet930=" + (internet ?: "none"))
                    }
                    successSnapshots = withContext(Dispatchers.IO) {
                        successStore.recordBeforeCutoff(snapshot)
                    }
                } catch(e:kotlinx.coroutines.CancellationException) { throw e }
                catch(_:Exception) { archiveFailed=true }
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
        val compactTab=premiumTab || tab==CherryTab.SUMMARY
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(horizontal=if(compactTab)10.dp else 15.dp,
                vertical=if(compactTab)6.dp else 14.dp),
            verticalArrangement=Arrangement.spacedBy(if(compactTab)6.dp else 12.dp)
        ){
            if(archiveFailed) item {
                Text(tr("အောင်ချက်မှတ်တမ်း ဖတ်/သိမ်း၍မရပါ။","Success archive unavailable."),
                    color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            }
            if(!ready || (!archiveReady && !archiveFailed) || refreshing){
                item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            }
            if(ready && (archiveReady || archiveFailed) && report!=null) {
                if(refreshing) item {
                    Text(tr("ပြန်လည်တွက်ချက်နေသည်","Updating rules"),
                        style=MaterialTheme.typography.labelSmall)
                }
                when(tab){
                    CherryTab.CURRENT,CherryTab.BORN->{
                        val current=tab==CherryTab.CURRENT
                        val all=if(current)report.current else report.born
                        if(waitingForResult) item {
                            Text(tr("ပေါက်ဂဏန်း စောင့်နေသည်","Awaiting confirmed result"),
                                style=MaterialTheme.typography.labelMedium,color=AppColors.PrimaryDeep)
                        }
                        item {
                            val editable=current && target.session==DrawSession.MORNING && captureOpen
                            CherryPremiumPanel {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(referenceDisplay.time,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep,
                                            style=MaterialTheme.typography.titleSmall)
                                        Text(if(target.session==DrawSession.MORNING)tr("မနက်ဒေတာ","AM data")
                                            else tr("ညနေဒေတာ","PM data"),style=MaterialTheme.typography.labelSmall)
                                        if(referenceDisplay.modern==null||referenceDisplay.internet==null)Text(tr("ဒေတာ မစုံသေး","Incomplete"),
                                            style=MaterialTheme.typography.labelSmall)
                                    }
                                    listOf("Modern" to referenceDisplay.modern,"Internet" to referenceDisplay.internet).forEach{(label,value)->
                                        val tileContent: @Composable () -> Unit = {
                                            Column(Modifier.padding(vertical=5.dp,horizontal=3.dp),
                                                horizontalAlignment=Alignment.CenterHorizontally) {
                                                Text(label,style=MaterialTheme.typography.labelSmall)
                                                Text(value ?: "—",fontWeight=FontWeight.Bold,
                                                    color=AppColors.PrimaryDeep,style=MaterialTheme.typography.titleMedium)
                                            }
                                        }
                                        if(editable) Surface(onClick={edit930=true},
                                            modifier=Modifier.weight(1f),color=Color(0xFFFFF1F5),
                                            shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFFF2C8D6)),
                                            content=tileContent)
                                        else Surface(modifier=Modifier.weight(1f),color=Color(0xFFFFF1F5),
                                            shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFFF2C8D6)),
                                            content=tileContent)
                                    }
                                }
                            }
                        }
                        item {
                            CherryPremiumRules(if(current)currentGroups else bornGroups,tab,premiumScores,en) {
                                openedRule=it
                            }
                        }
                        item { CherryPremiumCuts(cutGroups,en){openedCut=it} }
                        item {
                            CherryPremiumPairs(all,en,if(current)showAllCurrent else showAllBorn,
                                onToggle={if(current)showAllCurrent=!showAllCurrent else showAllBorn=!showAllBorn},
                                onPick={selectedPick=it})
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
                                en=en,
                                sourceText=originalSource,
                            )
                        }
                    }
                }
            }
        }
    }
    if(edit930 && captureOpen && tab==CherryTab.CURRENT && target.session==DrawSession.MORNING) {
        AlertDialog(onDismissRequest={edit930=false},
            title={Text("9:30 Modern / Internet",color=AppColors.PrimaryDeep)},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(modernOverride,{modernOverride=it.filter{ch->ch in '0'..'9'}.take(2)},
                        singleLine=true,label={Text("Modern")},placeholder={Text(fromModern?:"—")})
                    OutlinedTextField(internetOverride,{internetOverride=it.filter{ch->ch in '0'..'9'}.take(2)},
                        singleLine=true,label={Text("Internet")},placeholder={Text(fromInternet?:"—")})
                    Text(tr("မူလဒေတာ:","Saved data:")+" ${fromModern?:"—"} / ${fromInternet?:"—"}",
                        style=MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton={TextButton(onClick={modernOverride="";internetOverride="";edit930=false}){
                Text(tr("မူလဒေတာ","Reset"))}},
            confirmButton={TextButton(onClick={edit930=false}){Text(tr("ပိတ်","Close"))}})
    }
    openedRule?.let{id->
        val pool=if(tab==CherryTab.CURRENT)report?.current.orEmpty() else report?.born.orEmpty()
        val rule=(if(tab==CherryTab.CURRENT)currentGroups else bornGroups).firstOrNull{it.id==id}
        if(rule!=null) AlertDialog(onDismissRequest={openedRule=null},
            title={Text(CherryRuleWording.title(rule.id,rule.name),color=AppColors.PrimaryDeep)},
            text={
                Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    CherryPremiumRate(premiumScores.rule(tab,id),en)
                    if(tab==CherryTab.BORN) {
                        premiumScores.births.firstOrNull{it.id==id}?.let{episode->
                            Text(tr("သက်တမ်းမှတ်တမ်း ${episode.recorded}/${episode.expected} ပွဲ",
                                "Lifetime evidence ${episode.recorded}/${episode.expected} draws"),
                                style=MaterialTheme.typography.labelSmall)
                        }
                        premiumScores.weekly.firstOrNull{it.id==id}?.let{week->
                            Text(tr("အပတ်စဉ် ${week.distinctHits}/${week.goal} • ${week.week}",
                                "Weekly coverage ${week.distinctHits}/${week.goal} • ${week.week}"),
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    CherryRuleFrameCard(rule,pool,report?.activeRules?.firstOrNull{it.id==id},
                        en,expanded=true,onToggle={},onPick={openedRule=null;selectedPick=it},
                        sourceText=originalSource,onOriginal={openedRule=null;selectedOriginal=it})
                }
            },
            confirmButton={TextButton(onClick={openedRule=null}){Text(tr("ပိတ်","Close"))}})
    }
    openedCut?.let{kind->
        cutGroups.firstOrNull{it.kind==kind}?.let{group->
            AlertDialog(onDismissRequest={openedCut=null},title={Text(kind.label(en),color=AppColors.PrimaryDeep)},
                text={
                    Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        group.rules.forEach{rule->
                            Text(rule.name,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                            CherryPremiumRate(premiumScores.cuts[rule.id],en)
                            Text("${rule.origin.date} • ${rule.origin.session.label}",style=MaterialTheme.typography.labelSmall)
                            Text(CherryRuleWording.cutReason(rule.detail),style=MaterialTheme.typography.bodySmall)
                            Text(rule.excluded.joinToString(" / "),style=MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(color=AppColors.Blush)
                        }
                    }
                },
                confirmButton={TextButton(onClick={openedCut=null}){Text(tr("ပိတ်","Close"))}})
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
                        val instanceCount=p.evidence.map{it.id}.distinct().size
                        Text(tr("ဒီအတွဲကို မူခွဲ ${instanceCount} ခုက ထောက်ခံထားပါတယ်။",
                            "Supported by ${instanceCount} rule instances."),
                            style=MaterialTheme.typography.bodyMedium)
                        Text(tr("မူအုပ်စုအလိုက် အဆင့်သတ်မှတ်ပြီး ရှောင်ရန်မူငြိတဲ့အတွဲတွေကို ချန်ထားပါတယ်။",
                            "Ranking uses rule families; cut-rule conflicts are excluded."),
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
                                        tr("ရင်းမြစ် ကြည့်ရန်",
                                            "Source summary • Read")
                                    else tr("မူရင်းမူ ကြည့်ရန်",
                                        "Original • Read"),
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


/** Session-specific display only; never use AM as a PM fallback. */
internal data class CherrySessionReference(val time:String,val modern:String?,val internet:String?)
internal fun cherrySessionReference(session:DrawSession,modern930:String?,internet930:String?,
                                   modern200:String?,internet200:String?):CherrySessionReference {
    fun valid(value:String?)=value?.takeIf{it.matches(Regex("[0-9]{2}"))}
    return when(session) {
        DrawSession.MORNING->CherrySessionReference("9:30",valid(modern930),valid(internet930))
        DrawSession.EVENING->CherrySessionReference("2:00",valid(modern200),valid(internet200))
    }
}
