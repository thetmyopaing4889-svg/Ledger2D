package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
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

private val cherrySuccessEdge = Color(0xFFF4CFDA)
private val cherrySuccessPale = Color(0xFFFFEDF2)

@Composable
private fun SuccessCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape=RoundedCornerShape(18.dp), color=Color.White,
        border=BorderStroke(1.dp,cherrySuccessEdge)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
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
    onHistory: () -> Unit,
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
    var expandedTab by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableIntStateOf(10) }
    var openedRule by remember { mutableStateOf<CherrySuccessRank?>(null) }
    var openedCheck by remember(showing) { mutableStateOf<CherrySuccessCheck?>(null) }
    var showMisses by remember(showing,expandedTab) { mutableStateOf(false) }
    val recentDraws = remember(confirmed,byCompleted,period) {
        confirmed.take(period).mapNotNull { byCompleted[DrawIdentity(it.date,it.session)] }
    }
    val cohort=remember(completed) { CherryComparableEvidence.cohort(completed) }
    val canCompare=cohort.raw.isNotEmpty()
    // If raw evidence is absent, show the prior offered archive as such, never
    // pretend it has a corresponding original score.
    val offeredHistory=if(canCompare)cohort.offered else completed
    val episodes=remember(offeredHistory) { CherryBornLifetime.evaluate(offeredHistory) }
    val weekly=remember(offeredHistory) { CherryWeeklyLifetime.evaluate(offeredHistory) }
    var expandedWeek by rememberSaveable { mutableStateOf(false) }
    var rawWeek by rememberSaveable { mutableStateOf(false) }
    var expandedCuts by rememberSaveable { mutableStateOf(false) }
    val rawCompleted=cohort.raw
    val rawEpisodes=remember(rawCompleted) { CherryBornLifetime.evaluate(rawCompleted) }
    val rawWeekly=remember(rawCompleted) { CherryWeeklyLifetime.evaluate(rawCompleted) }
    val cutScores=remember(recentDraws) { CherryCutSuccess.scores(recentDraws) }

    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(if(en) "🏆 Success" else "🏆 အောင်ချက်",
                color=AppColors.PrimaryDeep,fontWeight=FontWeight.Black,
                style=MaterialTheme.typography.titleLarge)
            Text(if(en) "Verified results of pre-draw predictions"
                else "ပွဲမထွက်ခင် မှတ်ထားသောမူများ၏ အောင်ချက်",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SuccessCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(en) "Latest draw result" else "နောက်ဆုံးပွဲစဉ်",
                    color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                TextButton(onClick=onHistory) {
                    Icon(Icons.Default.History,null,Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if(en) "History" else "မှတ်တမ်း")
                }
            }
            if(showing==null) {
                Text(if(en) "No confirmed draw yet."
                    else "အတည်ပြုပေါက်ဂဏန်း မရှိသေးပါ။")
            } else {
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(color=cherrySuccessPale,
                        shape=RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(horizontal=20.dp,vertical=13.dp),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(winning ?: "??",color=AppColors.PrimaryDeep,
                                fontSize=30.sp,fontWeight=FontWeight.Black)
                            Text(if(en) "Winning" else "ပေါက်ဂဏန်း",
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text(successDrawName(showing,en),
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        tabs.forEach { tab ->
                            val results = draw?.forTab(tab).orEmpty()
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(successTabName(tab,en),
                                    style=MaterialTheme.typography.bodySmall,
                                    modifier=Modifier.weight(1f))
                                Text(if(results.isEmpty()) "—" else
                                    if(en) "${results.count { it.correct }} hit / ${results.count { !it.correct }} miss"
                                    else "${results.count { it.correct }} မှန် / ${results.count { !it.correct }} လွဲ",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=if(results.any { it.correct }) AppColors.PrimaryDeep
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if(selectedDraw!=null) {
                    TextButton(onClick=onLatest) {
                        Text(if(en) "Back to latest draw" else "နောက်ဆုံးပွဲသို့ ပြန်မည်")
                    }
                }
                if(winning!=null && stored==null) {
                    Text(if(en) "No pre-draw snapshot for this session. Not counted as a loss."
                        else "ဒီပွဲအတွက် ကြိုတင်မှတ်တမ်းမရှိပါ။ အရှုံးမတွက်ပါ။",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else if(winning!=null) {
                    tabs.forEach { tab ->
                        val checks=draw?.forTab(tab).orEmpty()
                        val isOpen=expandedTab==tab.name
                        Surface(onClick={expandedTab=if(isOpen)null else tab.name},
                            color=cherrySuccessPale,shape=RoundedCornerShape(10.dp)) {
                            Row(Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text(successTabName(tab,en),modifier=Modifier.weight(1f),
                                    fontWeight=FontWeight.SemiBold,
                                    color=AppColors.PrimaryDeep)
                                Text(if(checks.isEmpty()) "—" else
                                    "${checks.count { it.correct }} / ${checks.size}",
                                    style=MaterialTheme.typography.labelSmall)
                                Icon(if(isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,tint=AppColors.PrimaryDeep)
                            }
                        }
                        if(isOpen) {
                            if(checks.isEmpty()) {
                                Text(if(en) "No recorded rule for this tab."
                                    else "ဒီကဏ္ဍအတွက် မူမှတ်တမ်း မရှိပါ။",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            val hits=checks.filter { it.correct }
                            val misses=checks.filterNot { it.correct }
                            hits.forEach { check -> SuccessRuleRow(check,en) { openedCheck=check } }
                            if(misses.isNotEmpty()) {
                                TextButton(onClick={showMisses=!showMisses},modifier=Modifier.fillMaxWidth()) {
                                    Text(if(en) "Missed rules — ${misses.size}"
                                        else "လွဲခဲ့သောမူများ — ${misses.size} မူခွဲ")
                                    Icon(if(showMisses) Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                                }
                                if(showMisses) misses.forEach { check ->
                                    SuccessRuleRow(check,en) { openedCheck=check }
                                }
                            }
                        }
                    }
                }
            }
        }

        SuccessCard {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(en) "📊 Category success" else "📊 ကဏ္ဍအလိုက် အောင်ချက်နှုန်း",
                    fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep,
                    modifier=Modifier.weight(1f))
                listOf(10,20).forEach { count ->
                    FilterChip(selected=period==count,onClick={period=count},
                        label={Text(if(en) "$count draws" else "$count ပွဲ")})
                    Spacer(Modifier.width(5.dp))
                }
            }
            tabs.forEach { tab ->
                val rate = CherrySuccessEngine.tabRate(recentDraws,tab,currentVersionOnly=true)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(successTabName(tab,en),modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.bodySmall)
                    Text(if(rate.evaluated==0) "—" else
                        "${rate.percent}% (${rate.correct}/${rate.evaluated})",
                        fontWeight=FontWeight.SemiBold,
                        style=MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress={if(rate.evaluated==0) 0f else rate.correct.toFloat()/rate.evaluated},
                    modifier=Modifier.fillMaxWidth().height(7.dp),
                    color=AppColors.Primary,trackColor=cherrySuccessPale)
            }
            Text(if(en) "Current version, per draw. Math history unchanged; missing snapshots excluded."
                else "လက်ရှိဗားရှင်း တစ်ပွဲချင်းအောင်ချက်။ သင်္ချာနည်းမှတ်တမ်း မပြောင်းပါ။ မှတ်တမ်းမရှိသောပွဲ မပါဝင်ပါ။",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SuccessCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(en) "Exclusion checks" else "အသေမူ အောင်ချက်",
                    modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                TextButton(onClick={expandedCuts=!expandedCuts}) {
                    Text(if(expandedCuts) { if(en) "Hide" else "ပိတ်" }
                        else { if(en) "Details" else "အသေးစိတ်" })
                }
            }
            val cutChecks=draw?.let(CherryCutSuccess::checks).orEmpty()
            if(cutChecks.isEmpty()) Text(if(en) "No recorded exclusions for this draw."
                else "ဤပွဲ အသေမူ ကြိုတင်မှတ်တမ်း မရှိသေးပါ။",style=MaterialTheme.typography.bodySmall)
            cutChecks.forEach { check ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(check.rule.name,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    Text(if(check.correct) { if(en) "Correct" else "မှန်" }
                        else { if(en) "Miss" else "လွဲ" },fontWeight=FontWeight.Bold,
                        color=if(check.correct) Color(0xFF16854E) else Color(0xFFC13D56))
                }
                if(expandedCuts) {
                    Text(check.rule.excluded.joinToString(" / "),style=MaterialTheme.typography.labelSmall)
                }
            }
            val raw=draw?.let(CherryCutSuccess::rawView)
            if(raw!=null) listOf(CherryTab.CURRENT,CherryTab.BORN).forEach { tab ->
                val rawChecks=raw.forTab(tab)
                val offered=draw?.forTab(tab).orEmpty()
                val counts=CherryComparableEvidence.counts(rawChecks,offered)
                if(rawChecks.isNotEmpty()) {
                    val offeredText=if(counts.offeredCount==0) {
                        if(en) "No offer" else "ရွေးကွက်မကျန်"
                    } else "${counts.offeredHits}/${counts.offeredCount}"
                    Text(successTabName(tab,en)+" • "+
                        (if(en) "Raw ${counts.rawHits}/${counts.rawCount} · Offered $offeredText"
                         else "မူရင်း ${counts.rawHits}/${counts.rawCount} · ဖြတ်ပြီး $offeredText"),
                        style=MaterialTheme.typography.bodySmall)
                    if(counts.noOffer>0) Text(if(en) "${counts.noOffer} rules without offered pairs"
                        else "ရွေးကွက်မကျန်သောမူ ${counts.noOffer} ခု",
                        style=MaterialTheme.typography.labelSmall)
                }
            }
            if(expandedCuts) {
                Text(if(en) "Recent $period draws" else "နောက်ဆုံး $period ပွဲ",
                    fontWeight=FontWeight.SemiBold)
                cutScores.forEach { score ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(score.name,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        Text("${score.correct}/${score.evaluated}",fontWeight=FontWeight.SemiBold)
                    }
                }
            }
            Text(if(en) "Correct when the confirmed result is outside the recorded exclusion set."
                else "ပေါက်ဂဏန်း အသေတွဲထဲ မပါလျှင် မှန်သည်။",
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SuccessCard {
            Text(if(en) "Born lifetime • recorded instances" else "မွေးမူ သက်တမ်းအောင်ချက်",
                fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
            val hit=episodes.count { it.status==CherryBornLifetime.Status.HIT }
            val miss=episodes.count { it.status==CherryBornLifetime.Status.MISS }
            val unresolved=episodes.size-hit-miss
            Text(if(en) "Offered: $hit hit / $miss miss / $unresolved unresolved"
                else "ဖြတ်ပြီး • $hit မှန် / $miss လွဲ / $unresolved မဆုံးဖြတ်ရသေး",
                fontWeight=FontWeight.SemiBold)
            if(rawEpisodes.isNotEmpty()) {
                val rawHit=rawEpisodes.count{it.status==CherryBornLifetime.Status.HIT}
                val rawMiss=rawEpisodes.count{it.status==CherryBornLifetime.Status.MISS}
                val rawUnknown=rawEpisodes.size-rawHit-rawMiss
                Text(if(en) "Raw: $rawHit hit / $rawMiss miss / $rawUnknown unresolved"
                    else "မူရင်း • $rawHit မှန် / $rawMiss လွဲ / $rawUnknown မဆုံးဖြတ်ရသေး",
                    fontWeight=FontWeight.SemiBold)
            }
            val offeredIds=episodes.map{it.id}.toSet()
            val rawOnly=rawEpisodes.count{it.id !in offeredIds}
            if(rawOnly>0) Text(if(en) "$rawOnly original instances without offered evidence"
                else "ဖြတ်ပြီးမှတ်တမ်းမရှိသော မူရင်းသက်တမ်း $rawOnly ခု",
                style=MaterialTheme.typography.labelSmall)
            Text(if(canCompare) {
                val dates=cohort.raw.map{it.snapshot.target.date}
                val range="${dates.minOrNull()} – ${dates.maxOrNull()}"
                if(en) "${cohort.raw.size} common recorded draws • $range. Missing slots are not losses."
                else "မှတ်တမ်းတူ ${cohort.raw.size} ပွဲ • $range။ မှတ်တမ်းမပြည့်လျှင် အရှုံးမတွက်ပါ။"
            } else {
                if(en) "Offered archive only; no original evidence yet."
                else "ဖြတ်ပြီးမှတ်တမ်းသာ ရှိသေးသည်။"
            },
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SuccessCard {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(en) "Weekly evidence" else "အပတ်စဉ်မူ အောင်ချက်",
                    modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                TextButton(onClick={expandedWeek=!expandedWeek}) {
                    Text(if(expandedWeek) { if(en) "Hide" else "ပိတ်" }
                        else { if(en) "Details" else "အသေးစိတ်" })
                }
            }
            if(canCompare) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=!rawWeek,onClick={rawWeek=false},
                    label={Text(if(en) "Offered" else "ဖြတ်ပြီး")})
                FilterChip(selected=rawWeek,onClick={rawWeek=true},
                    label={Text(if(en) "Raw" else "မူရင်း")})
            }
            val weekEvidence=if(rawWeek && canCompare)rawWeekly else weekly
            if(weekEvidence.isEmpty()) Text(if(en) "No recorded weekly evidence yet."
                else "အပတ်စဉ် ကြိုတင်မှတ်တမ်း မရှိသေးပါ။",
                style=MaterialTheme.typography.bodySmall)
            else {
                val latest=(weekly+rawWeekly).maxOf{it.week}
                val visible=if(expandedWeek)weekEvidence else weekEvidence.filter{it.week==latest}
                if(visible.isEmpty()) Text(if(en) "No evidence for the common latest week."
                    else "ဤအပတ်အတွက် မှတ်တမ်းမရှိသေးပါ။",
                    style=MaterialTheme.typography.bodySmall)
                visible.forEach { item ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(CherryRuleWording.title(item.id,item.name),
                                fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodySmall)
                            Text(item.week.toString()+" • "+
                                (if(en) "${item.recorded}/${item.expected} recorded draws"
                                 else "မှတ်တမ်း ${item.recorded}/${item.expected} ပွဲ"),
                                style=MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment=Alignment.End) {
                            Text("${item.distinctHits}/${item.goal}",fontWeight=FontWeight.Bold,
                                color=AppColors.PrimaryDeep)
                            Text(when(item.status) {
                                CherryWeeklyLifetime.Status.COMPLETE -> if(en) "Target met" else "သတ်မှတ်ချက်ပြည့်"
                                CherryWeeklyLifetime.Status.LOWER_TARGET -> if(en) "Lower target met" else "အနည်းဆုံးပြည့်"
                                CherryWeeklyLifetime.Status.RECORDED_WINDOW_END -> if(en) "Window recorded" else "သက်တမ်းမှတ်တမ်းပြည့်"
                                CherryWeeklyLifetime.Status.UNRESOLVED -> if(en) "Incomplete evidence" else "မဆုံးဖြတ်ရသေး"
                            },style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if(canCompare && cohort.withoutRaw.isNotEmpty()) Text(
                if(en) "Older / unpaired records remain in draw history."
                else "မူရင်းမပါသောမှတ်တမ်းများကို ပွဲစဉ်မှတ်တမ်းတွင် ကြည့်နိုင်သည်။",
                style=MaterialTheme.typography.labelSmall)
            Text(if(en) "Distinct recorded matches; eight-pair coverage is not a whole-source win. Missing draws are not losses."
                else "မှတ်တမ်းရှိ ဒဲ့အတွဲများကိုသာ ရေတွက်သည်။ ၈ ကွက်မှာ အုပ်စုထွက်ရှိမှုသာ ဖြစ်သည်။ မှတ်တမ်းမပြည့်လျှင် အရှုံးမတွက်ပါ။",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SuccessCard {
            Text(if(en) "🔥 Top 5 performing rules" else "🔥 အောင်ချက်အကောင်းဆုံး မူ ၅ ခု",
                fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
            val leaders = remember(recentDraws) { CherrySuccessEngine.ranking(recentDraws) }
            if(leaders.isEmpty()) {
                Text(if(en) "Not enough pre-draw evidence yet (at least 3 evaluations per rule)."
                    else "မူတစ်ခုချင်းစီအတွက် အနည်းဆုံး ၃ ပွဲ မှတ်တမ်းမပြည့်သေးပါ။",
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
                                    if(en) " • ${rank.pairCount} pairs"
                                    else " • ${rank.pairCount} ကွက်"
                                else ""),
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${rank.correct}/${rank.evaluated}",
                            fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                    }
                }
            }
            Text(if(en) "Current rule version only (Math unchanged). Historical checks do not guarantee the next result."
                else "လက်ရှိမူဗားရှင်း၏ အောင်ချက်သာ (သင်္ချာနည်း မပြောင်းပါ)။ နောက်ပွဲကို အာမမခံပါ။",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
