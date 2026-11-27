package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession

private val cherrySuccessPale = Color(0xFFFFEDF2)

@Composable
private fun SuccessCard(content: @Composable ColumnScope.() -> Unit) {
    CherryPremiumPanel(content=content)
}
private fun successTabName(tab: CherryTab, en: Boolean): String = when(tab) {
    CherryTab.CURRENT -> if(en) "Current rules" else "လက်ရှိမူ"
    CherryTab.BORN -> if(en) "Born rules" else "မွေးမူ"
    CherryTab.MATH -> if(en) "Math" else "သင်္ချာနည်း"
    CherryTab.SUMMARY -> if(en) "Success" else "အောင်ချက်"
}
private fun successDrawName(target: DrawIdentity, en: Boolean): String =
    target.date.toString()+" • "+when(target.session) {
        DrawSession.MORNING -> if(en) "AM" else "မနက်"
        DrawSession.EVENING -> if(en) "PM" else "ညနေ"
    }

@Composable
internal fun CherrySuccessContent(
    confirmedHistory: List<HistoricDraw>,
    snapshots: List<CherrySuccessSnapshot>,
    selectedDraw: DrawIdentity?,
    onLatest: () -> Unit,
    en: Boolean,
    sourceText: String? = null,
) {
    val confirmed = remember(confirmedHistory) {
        CherryVerifiedHistory.normalize(confirmedHistory).asReversed()
    }
    val byTarget = remember(confirmed) { confirmed.associate { DrawIdentity(it.date,it.session) to it.digit } }
    val completed = remember(snapshots,byTarget) { CherrySuccessEngine.completed(snapshots,byTarget) }
    val byCompleted = remember(completed) { completed.associateBy { it.snapshot.target } }
    val latest = confirmed.firstOrNull()
    val showing = selectedDraw ?: latest?.let { DrawIdentity(it.date,it.session) }
    val winning = showing?.let { byTarget[it] }
    val draw = showing?.let { byCompleted[it] }
    val stored = showing?.let { target -> snapshots.firstOrNull { it.target==target } }
    val tabs = listOf(CherryTab.CURRENT,CherryTab.BORN,CherryTab.MATH)
    var period by rememberSaveable { mutableIntStateOf(10) }
    var openedSection by rememberSaveable(showing) { mutableStateOf<String?>(null) }
    var openedRule by remember { mutableStateOf<CherrySuccessRank?>(null) }
    var openedCheck by remember(showing) { mutableStateOf<CherrySuccessCheck?>(null) }
    var showMisses by remember(showing,openedSection) { mutableStateOf(false) }
    val recentDraws = remember(confirmed,byCompleted,period) {
        confirmed.take(period).mapNotNull { byCompleted[DrawIdentity(it.date,it.session)] }
    }
    val cutChecks=remember(draw) { draw?.let(CherryCutSuccess::checks).orEmpty() }
    val rates=remember(recentDraws) {
        tabs.associateWith { CherrySuccessEngine.tabRate(recentDraws,it,currentVersionOnly=true) }
    }

    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(en) "Success" else "အောင်ချက်",
            color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleSmall)
        SuccessCard {
            Text(if(selectedDraw==null) { if(en) "Latest draw" else "နောက်ဆုံးပွဲစဉ်" }
                else { if(en) "Selected draw" else "ရွေးထားသောပွဲစဉ်" },
                color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
            if(showing==null) Text(if(en) "No confirmed result" else "ပေါက်ဂဏန်း မရှိသေးပါ။",
                style=MaterialTheme.typography.bodySmall)
            else {
                Text(successDrawName(showing,en),style=MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Surface(color=cherrySuccessPale,shape=RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=12.dp),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(winning ?: "—",color=AppColors.PrimaryDeep,
                                fontSize=30.sp,fontWeight=FontWeight.Black)
                            Text(if(en) "Result" else "ပေါက်ဂဏန်း",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        if(draw==null) {
                            Text(if(en) "No pre-draw record" else "ကြိုတင်မှတ်တမ်းမရှိပါ။",
                                style=MaterialTheme.typography.bodySmall)
                        } else {
                        tabs.forEach { tab ->
                            val checks=draw.forTab(tab)
                            SuccessDashboardRow(successTabName(tab,en),
                                successCountLabel(checks.count{it.correct},checks.size,en),en) {
                                openedSection=tab.name
                            }
                        }
                        SuccessDashboardRow(if(en) "Exclusions" else "အသေမူ",
                            successCountLabel(cutChecks.count{it.correct},cutChecks.size,en),en) {
                            openedSection="cuts"
                        }
                        }
                    }
                }
                if(selectedDraw!=null) TextButton(onClick=onLatest) {
                    Text(if(en) "Back to latest draw" else "နောက်ဆုံးပွဲသို့ ပြန်မည်")
                }
            }
        }
        SuccessCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(en) "Draw success" else "ပွဲအလိုက် အောင်ချက်",
                    fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep,modifier=Modifier.weight(1f))
                listOf(10,20).forEach { count ->
                    FilterChip(selected=period==count,onClick={period=count},
                        label={Text(if(en) "$count draws" else "$count ပွဲ",
                            style=MaterialTheme.typography.labelSmall)})
                    Spacer(Modifier.width(3.dp))
                }
            }
            if(rates.values.none{it.evaluated>0}) {
                Text(if(en) "No recorded draws yet." else "ပွဲအောင်ချက်မှတ်တမ်း မရှိသေးပါ။",
                    style=MaterialTheme.typography.bodySmall)
            } else {
            tabs.forEach { tab ->
                val rate = rates.getValue(tab)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(successTabName(tab,en),modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.bodySmall)
                    Text(if(rate.evaluated==0) { if(en) "No record" else "မှတ်တမ်းမရှိ" }
                        else if(en) "${rate.correct}/${rate.evaluated} draws hit"
                        else "${rate.correct}/${rate.evaluated} ပွဲမှန်",
                        fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodySmall)
                }
                if(rate.evaluated>0) LinearProgressIndicator(
                    progress={rate.correct.toFloat()/rate.evaluated},
                    modifier=Modifier.fillMaxWidth().height(6.dp),
                    color=AppColors.Primary,trackColor=cherrySuccessPale)
            }
            Text(if(en) "One or more rules hit; recorded draws only."
                else "မူတစ်ခုနှင့်အထက် မှန်သော မှတ်တမ်းရှိပွဲများ။",
                style=MaterialTheme.typography.labelSmall)
            }
        }
        SuccessCard {
            Text(if(en) "Top 5 by recorded hit rate" else "အောင်ချက်နှုန်း ထိပ်ဆုံးမူ ၅ ခု",
                fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
            val leaders = remember(recentDraws) { CherrySuccessEngine.ranking(recentDraws) }
            if(leaders.isEmpty()) {
                Text(if(en) "Not enough pre-draw evidence yet (at least 3 evaluations per rule)."
                    else "မူတစ်ခုလျှင် ၃ ပွဲစာ မှတ်တမ်းလိုသည်။",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            leaders.forEachIndexed { index,rank ->
                Surface(onClick={openedRule=rank},color=cherrySuccessPale,
                    shape=RoundedCornerShape(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("${index+1}",fontWeight=FontWeight.Bold,
                            color=AppColors.PrimaryDeep)
                        Column(Modifier.weight(1f)) {
                            Text(successRankTitle(rank),style=MaterialTheme.typography.bodySmall,
                                fontWeight=FontWeight.SemiBold)
                            Text(successTabName(rank.tab,en)+" • "+
                                (if(en) "${rank.currentStreak} consecutive qualifying hits"
                                else "အကျုံးဝင် ${rank.currentStreak} ကြိမ်ဆက်မှန်") +
                                (if(rank.criterion==CherrySuccessCriterion.PAIR)
                                    if(en) " • Last: ${rank.pairCount} pairs"
                                    else " • နောက်ဆုံး ${rank.pairCount} ကွက်"
                                else ""),
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if(en) "${rank.correct}/${rank.evaluated} draws" else "${rank.correct}/${rank.evaluated} ပွဲမှန်",
                            fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                    }
                }
            }
            if(leaders.isNotEmpty()) Text(if(en) "Historical results only."
                else "ယခင်ပွဲများ၏ အောင်ချက်သာ ဖြစ်သည်။",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SuccessCard {
            Text(if(en) "Other records" else "အခြားအောင်ချက်မှတ်တမ်းများ",
                fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
            SuccessDashboardRow(if(en) "Born rule lifetimes" else "မွေးမူ သက်တမ်းမှတ်တမ်း","",en) {
                openedSection="birth"
            }
            SuccessDashboardRow(if(en) "Weekly rules" else "အပတ်စဉ်မူ မှတ်တမ်း","",en) {
                openedSection="weekly"
            }
            SuccessDashboardRow(if(en) "Exclusion history" else "အသေမူ အောင်ချက်မှတ်တမ်း","",en) {
                openedSection="cutHistory"
            }
        }
    }
    if(openedSection!=null && openedCheck==null) {
        val section=openedSection.orEmpty()
        val tab=tabs.firstOrNull{it.name==section}
        val title=when(section) {
            "birth"->if(en) "Born rule lifetimes" else "မွေးမူ သက်တမ်းမှတ်တမ်း"
            "weekly"->if(en) "Weekly rules" else "အပတ်စဉ်မူ မှတ်တမ်း"
            "cutHistory"->if(en) "Exclusion history" else "အသေမူ အောင်ချက်မှတ်တမ်း"
            "cuts"->if(en) "Exclusions" else "အသေမူ"
            else->tab?.let{successTabName(it,en)}.orEmpty()
        }
        AlertDialog(onDismissRequest={openedSection=null},title={Text(title,color=AppColors.PrimaryDeep)},
            text={
                Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    when {
                        tab!=null -> {
                            if(showing!=null) Text(successDrawName(showing,en),style=MaterialTheme.typography.labelSmall)
                            val checks=draw?.forTab(tab).orEmpty()
                            Text(successCountLabel(checks.count{it.correct},checks.size,en),fontWeight=FontWeight.Bold)
                            checks.filter{it.correct}.forEach{check->SuccessRuleRow(check,en){openedCheck=check}}
                            val misses=checks.filterNot{it.correct}
                            if(misses.isNotEmpty()) {
                                TextButton(onClick={showMisses=!showMisses}) {
                                    Text(if(en) "Missed rules: ${misses.size}" else "လွဲခဲ့သောမူ ${misses.size} ခု")
                                    Icon(if(showMisses) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                                }
                                if(showMisses) misses.forEach{check->SuccessRuleRow(check,en){openedCheck=check}}
                            }
                        }
                        section=="cuts" || section=="cutHistory" -> {
                            if(section=="cuts") {
                                if(showing!=null) Text(successDrawName(showing,en),style=MaterialTheme.typography.labelSmall)
                                if(cutChecks.isEmpty()) Text(if(en) "No record" else "မှတ်တမ်းမရှိ")
                                cutChecks.forEach{check->
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                        Text(check.rule.name,modifier=Modifier.weight(1f))
                                        Text(if(check.correct) {if(en)"Correct" else "မှန်"} else {if(en)"Miss" else "လွဲ"},
                                            fontWeight=FontWeight.Bold,color=if(check.correct)Color(0xFF16854E) else Color(0xFFC13D56))
                                    }
                                    Text(check.rule.excluded.joinToString(" / "),style=MaterialTheme.typography.labelSmall)
                                    HorizontalDivider()
                                }
                                Text(if(en) "Correct when the winning pair is not excluded."
                                    else "ပေါက်ဂဏန်း အသေတွဲထဲ မပါလျှင် မှန်သည်။",
                                    style=MaterialTheme.typography.labelSmall)
                                val raw=draw?.let(CherryCutSuccess::rawView)
                                if(raw!=null) listOf(CherryTab.CURRENT,CherryTab.BORN).forEach{kind->
                                    val rawChecks=raw.forTab(kind)
                                    if(rawChecks.isNotEmpty()) {
                                        val counts=CherryComparableEvidence.counts(rawChecks,draw?.forTab(kind).orEmpty())
                                        Text(successTabName(kind,en),fontWeight=FontWeight.Bold)
                                        Text((if(en)"Before exclusions: " else "အသေမူ မသုံးမီ — ")+successCountLabel(counts.rawHits,counts.rawCount,en))
                                        Text((if(en)"After exclusions: " else "အသေမူ သုံးပြီး — ")+
                                            if(counts.offeredCount==0) {if(en)"No pairs remain" else "ရွေးကွက်မကျန်"} else
                                                successCountLabel(counts.offeredHits,counts.offeredCount,en))
                                        if(counts.noOffer>0) Text(if(en)"${counts.noOffer} rules without pairs"
                                            else "ရွေးကွက်မကျန်သောမူ ${counts.noOffer} ခု",style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                Text(if(en)"Last $period draws" else "နောက်ဆုံး $period ပွဲ",fontWeight=FontWeight.Bold)
                                val scores=remember(recentDraws){CherryCutSuccess.scores(recentDraws)}
                                if(scores.isEmpty()) Text(if(en)"No record" else "မှတ်တမ်းမရှိ")
                                scores.forEach{score->
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                        Text(score.name,modifier=Modifier.weight(1f))
                                        Text(if(en)"${score.correct}/${score.evaluated} draws" else "${score.correct}/${score.evaluated} ပွဲမှန်",
                                            fontWeight=FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        section=="birth" || section=="weekly" -> {
                            SuccessLifetimeDetails(completed,section=="weekly",en)
                        }
                    }
                }
            },confirmButton={TextButton(onClick={openedSection=null}){Text(if(en)"Close" else "ပိတ်")}})
    }
    openedCheck?.let { check ->
        val rule=check.rule
        val previous=stored?.inputEvidence.orEmpty().substringAfter("previous=","")
            .substringBefore(';').substringAfterLast(':').takeIf{it.matches(Regex("[0-9]{2}"))}
        val detail=rule.detail.ifBlank {
            if(rule.tab==CherryTab.CURRENT && previous!=null) "နောက်ဆုံးထွက် $previous" else ""
        }
        val source=if(rule.criterion==CherrySuccessCriterion.PAIR &&
            (rule.detail.isNotBlank() || rule.id.startsWith("s2-")))
            CherryRuleSources.lookup(sourceText,rule.id,detail) else null
        var showSource by remember(rule.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest={openedCheck=null},
            title={Text(successRuleTitle(rule),color=AppColors.PrimaryDeep)},
            text={
                Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text(check.result+" — "+if(check.correct) (if(en)"Correct" else "မှန်")
                        else (if(en)"Miss" else "လွဲ"),fontWeight=FontWeight.Bold,
                        color=if(check.correct) Color(0xFF16854E) else Color(0xFFC13D56))
                    if(rule.criterion==CherrySuccessCriterion.PAIR) {
                        if(rule.detail.isNotBlank() || (rule.id.startsWith("s2-") && previous!=null)) {
                            Text(CherryRuleWording.why(rule.id,rule.name,detail))
                            if(rule.id.startsWith("s2-"))
                                Text(CherryRuleWording.how(rule.id,rule.name,rule.predictions))
                        }
                        Text(if(en) "Recorded eligible pairs — ${rule.predictions.size}"
                            else "ကြိုတင်မှတ်ထားသော အကျုံးဝင်အတွဲ — ${rule.predictions.size} တွဲ",
                            fontWeight=FontWeight.SemiBold)
                        rule.predictions.chunked(5).forEach { pairs ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                pairs.forEach { pair ->
                                    Surface(modifier=Modifier.weight(1f),shape=RoundedCornerShape(8.dp),
                                        color=if(pair==check.result) Color(0xFFE1F4E8) else cherrySuccessPale) {
                                        Text(pair,modifier=Modifier.padding(7.dp),fontWeight=FontWeight.Bold,
                                            color=if(pair==check.result) Color(0xFF16854E) else AppColors.PrimaryDeep)
                                    }
                                }
                                repeat(5-pairs.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if(source!=null) {
                            TextButton(onClick={showSource=!showSource}) {
                                Text(if(en) "Read source" else "မူရင်းမူ ကြည့်ရန်")
                            }
                            if(showSource) Text(source.quote,style=MaterialTheme.typography.bodySmall)
                        }
                    } else Text(rule.predictions.joinToString(" / "))
                }
            },confirmButton={TextButton(onClick={openedCheck=null}) { Text(if(en)"Close" else "ပိတ်") }})
    }
    openedRule?.let { rank ->
        val proof = CherrySuccessEngine.rankEvidence(recentDraws,rank.id)
        AlertDialog(onDismissRequest={openedRule=null},
            title={Text(successRankTitle(rank),color=AppColors.PrimaryDeep)},
            text={
                Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${rank.correct}/${rank.evaluated} • ${rank.percent}%",
                        fontWeight=FontWeight.Bold)
                    proof.forEach { evidence ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(successDrawName(evidence.target,en),modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.bodySmall)
                            Icon(if(evidence.correct) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                null,tint=if(evidence.correct) Color(0xFF16854E) else Color(0xFFC13D56))
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={openedRule=null}) {
                Text(if(en) "Close" else "ပိတ်")
            }})
    }
}

internal fun successCountLabel(correct:Int,total:Int,en:Boolean):String =
    if(total==0) {if(en)"No record" else "မှတ်တမ်းမရှိ"}
    else if(en) "$correct/$total hit" else "$correct/$total မှန်"

@Composable
private fun SuccessDashboardRow(label:String,value:String,en:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(8.dp),color=cherrySuccessPale.copy(alpha=.55f)) {
        Row(Modifier.fillMaxWidth().heightIn(min=42.dp).padding(horizontal=7.dp,vertical=4.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,
                fontWeight=FontWeight.Medium)
            if(value.isNotEmpty()) Text(value,color=AppColors.PrimaryDeep,fontWeight=FontWeight.SemiBold,
                style=MaterialTheme.typography.labelSmall)
            Icon(Icons.Default.ChevronRight,if(en)"Details" else "အသေးစိတ်",
                tint=AppColors.PrimaryDeep,modifier=Modifier.size(15.dp))
        }
    }
}

@Composable
private fun SuccessLifetimeDetails(completed:List<CherrySuccessDraw>,weeklyMode:Boolean,en:Boolean) {
    val cohort=remember(completed){CherryComparableEvidence.cohort(completed)}
    val compare=cohort.raw.isNotEmpty()
    val offered=if(compare)cohort.offered else completed
    // Only compare matched draw records. Old records remain in draw history.
    if(compare) Text(if(en)"Compared on ${cohort.raw.size} recorded draws."
        else "တူညီသောမှတ်တမ်း ${cohort.raw.size} ပွဲကို နှိုင်းယှဉ်ထားသည်။",style=MaterialTheme.typography.labelSmall)
    else Text(if(en)"Only after-exclusion records available." else "အသေမူသုံးပြီး မှတ်တမ်းသာ ရှိသည်။",
        style=MaterialTheme.typography.labelSmall)
    if(weeklyMode) {
        val actual=remember(offered){CherryWeeklyLifetime.evaluate(offered)}
        val raw=remember(cohort.raw){CherryWeeklyLifetime.evaluate(cohort.raw)}
        val actualBy=remember(actual){actual.associateBy{it.week to it.id}}
        val rawBy=remember(raw){raw.associateBy{it.week to it.id}}
        val keys=remember(actualBy,rawBy){(actualBy.keys+rawBy.keys).sortedWith(
            compareByDescending<Pair<java.time.LocalDate,String>>{it.first}.thenBy{it.second})}
        if(keys.isEmpty()) Text(if(en)"No weekly record" else "အပတ်စဉ်မှတ်တမ်း မရှိသေးပါ။")
        keys.forEach{key->
            val a=actualBy[key]; val r=rawBy[key]; val item=a?:r!!
            Text(CherryRuleWording.title(item.id,item.name),fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
            Text(if(en)"Week of ${item.week}" else "${item.week} စတင်သောအပတ်",style=MaterialTheme.typography.labelSmall)
            if(compare) SuccessWeeklyLine(if(en)"Before exclusions" else "အသေမူ မသုံးမီ",r,en)
            SuccessWeeklyLine(if(en)"After exclusions" else "အသေမူ သုံးပြီး",a,en)
            HorizontalDivider()
        }
        Text(if(en)"Distinct matching pairs, not draw counts. Eight-pair coverage is not a whole-source win."
            else "မတူသော ဒဲ့အတွဲများကို ရေတွက်သည်။ ၈ ကွက်မှာ အုပ်စုထွက်ရှိမှုသာ ဖြစ်သည်။",
            style=MaterialTheme.typography.labelSmall)
    } else {
        val actual=remember(offered){CherryBornLifetime.evaluate(offered)}
        val raw=remember(cohort.raw){CherryBornLifetime.evaluate(cohort.raw)}
        val actualBy=remember(actual){actual.associateBy{it.id}}
        val rawBy=remember(raw){raw.associateBy{it.id}}
        val ruleEvidence=remember(offered,cohort.raw){
            (offered+cohort.raw).flatMap{it.checks}.filter{it.rule.tab==CherryTab.BORN}
                .groupBy{it.rule.id}.mapValues{(_,checks)->checks.first().rule}
        }
        val ids=remember(actualBy,rawBy,ruleEvidence){(actualBy.keys+rawBy.keys).sortedWith(
            compareByDescending<String>{ruleEvidence[it]?.sourceOrigin.orEmpty()}.thenBy{it})}
        if(ids.isEmpty()) Text(if(en)"No born lifetime record" else "မွေးမူသက်တမ်းမှတ်တမ်း မရှိသေးပါ။")
        ids.forEach{id->
            val rule=ruleEvidence[id]
            Text(rule?.let{successRuleTitle(it)}?:id.substringBefore('@'),fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
            if(rule!=null) {
                val first=rule.eligibleSlots.minWithOrNull(compareBy<DrawIdentity>{it.date}.thenBy{it.session.ordinal})
                val last=rule.eligibleSlots.maxWithOrNull(compareBy<DrawIdentity>{it.date}.thenBy{it.session.ordinal})
                if(rule.sourceOrigin.isNotBlank()) Text((if(en)"Origin: " else "အသက်ဝင်စေသောပွဲ — ")+
                    rule.sourceOrigin.replace("/MORNING",if(en)" • AM" else " • မနက်").replace("/EVENING",if(en)" • PM" else " • ညနေ"),
                    style=MaterialTheme.typography.labelSmall)
                if(first!=null && last!=null) Text(successDrawName(first,en)+" – "+successDrawName(last,en),
                    style=MaterialTheme.typography.labelSmall)
            }
            if(compare) SuccessBornLine(if(en)"Before exclusions" else "အသေမူ မသုံးမီ",rawBy[id],en)
            SuccessBornLine(if(en)"After exclusions" else "အသေမူ သုံးပြီး",actualBy[id],en)
            HorizontalDivider()
        }
        Text(if(en)"One hit within the lifetime counts. Missing records are not misses."
            else "သက်တမ်းအတွင်း တစ်ကြိမ်မှန်လျှင် မှန်သည်။ မှတ်တမ်းမပြည့်လျှင် လွဲဟု မတွက်ပါ။",
            style=MaterialTheme.typography.labelSmall)
    }
    if(compare && cohort.withoutRaw.isNotEmpty()) Text(if(en)"Older unpaired records are in draw history."
        else "နှိုင်းယှဉ်ရန် အချက်အလက်မပြည့်သော ပွဲဟောင်းများကို ပွဲစဉ်မှတ်တမ်းတွင် ကြည့်နိုင်သည်။",
        style=MaterialTheme.typography.labelSmall)
}

@Composable
private fun SuccessBornLine(label:String,item:CherryBornLifetime.Episode?,en:Boolean) {
    val status=when(item?.status) {
        CherryBornLifetime.Status.HIT->if(en)"Hit" else "မှန်"
        CherryBornLifetime.Status.MISS->if(en)"Miss" else "လွဲ"
        CherryBornLifetime.Status.UNRESOLVED->if(en)"Not yet established" else "အောင်ချက် မသတ်မှတ်နိုင်သေး"
        null->if(en)"No record" else "မှတ်တမ်းမရှိ"
    }
    Text("$label — $status",style=MaterialTheme.typography.bodySmall)
    if(item!=null) Text(if(en)"Recorded ${item.recorded}/${item.expected} draws"
        else "မှတ်တမ်းရှိ ${item.recorded}/${item.expected} ပွဲ",style=MaterialTheme.typography.labelSmall)
}

@Composable
private fun SuccessWeeklyLine(label:String,item:CherryWeeklyLifetime.Episode?,en:Boolean) {
    Text(if(item==null) "$label — "+(if(en)"No record" else "မှတ်တမ်းမရှိ")
        else if(en)"$label — ${item.distinctHits}/${item.goal} distinct pairs"
        else "$label — ${item.distinctHits}/${item.goal} တွဲမှန်",
        style=MaterialTheme.typography.bodySmall)
    if(item!=null) {
        val status=when(item.status) {
            CherryWeeklyLifetime.Status.COMPLETE->if(en)"Target met" else "သတ်မှတ်ချက်ပြည့်"
            CherryWeeklyLifetime.Status.LOWER_TARGET->if(en)"Lower target met" else "အနည်းဆုံးပြည့်"
            CherryWeeklyLifetime.Status.RECORDED_WINDOW_END->if(en)"Window recorded" else "သက်တမ်းမှတ်တမ်းပြည့်"
            CherryWeeklyLifetime.Status.UNRESOLVED->if(en)"Incomplete evidence" else "မှတ်တမ်းမပြည့်သေး"
        }
        Text((if(en)"Recorded ${item.recorded}/${item.expected} draws" else "မှတ်တမ်းရှိ ${item.recorded}/${item.expected} ပွဲ")+" • "+status,
            style=MaterialTheme.typography.labelSmall)
    }
}

private fun successRuleTitle(rule: CherrySuccessRule): String =
    if(rule.criterion==CherrySuccessCriterion.PAIR) CherryRuleWording.title(rule.id,rule.name)
    else rule.name

private fun successRankTitle(rank: CherrySuccessRank): String =
    if(rank.criterion==CherrySuccessCriterion.PAIR)
        CherryRuleWording.title(rank.id.substringAfter(':'),rank.name) else rank.name

@Composable
private fun SuccessRuleRow(check: CherrySuccessCheck,en:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,color=Color.Transparent,shape=RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(if(check.correct) Icons.Default.CheckCircle else Icons.Default.Cancel,null,
                tint=if(check.correct) Color(0xFF16854E) else Color(0xFFC13D56),modifier=Modifier.size(18.dp))
            Text(successRuleTitle(check.rule),modifier=Modifier.weight(1f),
                fontWeight=FontWeight.Medium,style=MaterialTheme.typography.bodySmall)
            Icon(Icons.Default.ChevronRight,if(en)"Details" else "အသေးစိတ်",modifier=Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun CherrySuccessHistoryDialog(
    confirmedHistory: List<HistoricDraw>,
    snapshots: List<CherrySuccessSnapshot>,
    en: Boolean,
    onSelected: (DrawIdentity) -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmed = remember(confirmedHistory) {
        CherryVerifiedHistory.normalize(confirmedHistory).asReversed()
    }
    val stored = snapshots.associateBy { it.target }
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(if(en) "Draw history" else "ပွဲစဉ်မှတ်တမ်း")},
        text={
            if(confirmed.isEmpty()) {
                Text(if(en) "No confirmed results." else "အတည်ပြု Result မရှိသေးပါ။")
            } else {
                LazyColumn(Modifier.heightIn(max=440.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    items(confirmed,key={it.date.toString()+"_"+it.session.name}) { draw ->
                        val target=DrawIdentity(draw.date,draw.session)
                        Surface(onClick={onSelected(target)},
                            color=cherrySuccessPale,shape=RoundedCornerShape(9.dp)) {
                            Row(Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(successDrawName(target,en),
                                        style=MaterialTheme.typography.bodySmall)
                                    Text(if(target in stored) {
                                        if(en) "Pre-draw snapshot" else "ကြိုတင်မှတ်တမ်းရှိ"
                                    } else {
                                        if(en) "No snapshot" else "ကြိုတင်မှတ်တမ်းမရှိ"
                                    },style=MaterialTheme.typography.labelSmall)
                                }
                                Text(draw.digit,fontWeight=FontWeight.Bold,
                                    color=AppColors.PrimaryDeep,fontSize=20.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton={TextButton(onClick=onDismiss) {
            Text(if(en) "Close" else "ပိတ်")
        }})
}
