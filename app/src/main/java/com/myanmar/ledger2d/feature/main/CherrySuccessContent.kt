package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
) {
    val confirmed = confirmedHistory
        .filter { it.digit.matches(Regex("[0-9]{2}")) }
        .distinctBy { it.date to it.session }
        .sortedWith(compareByDescending<HistoricDraw> { it.date }
            .thenByDescending { it.session.ordinal })
    val byTarget = confirmed.associate { DrawIdentity(it.date,it.session) to it.digit }
    val completed = CherrySuccessEngine.completed(snapshots,byTarget)
    val byCompleted = completed.associateBy { it.snapshot.target }
    val latest = confirmed.firstOrNull()
    val showing = selectedDraw ?: latest?.let { DrawIdentity(it.date,it.session) }
    val winning = showing?.let { byTarget[it] }
    val draw = showing?.let { byCompleted[it] }
    val stored = showing?.let { target -> snapshots.firstOrNull { it.target==target } }
    val tabs = listOf(CherryTab.CURRENT,CherryTab.BORN,CherryTab.MATH)
    var expandedTab by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableIntStateOf(10) }
    var openedRule by remember { mutableStateOf<CherrySuccessRank?>(null) }
    val recentDraws = confirmed.take(period).mapNotNull {
        byCompleted[DrawIdentity(it.date,it.session)]
    }

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
                            checks.forEach { check ->
                                Row(Modifier.fillMaxWidth(),
                                    verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Icon(if(check.correct) Icons.Default.CheckCircle else
                                        Icons.Default.Cancel,null,
                                        tint=if(check.correct) Color(0xFF16854E)
                                            else Color(0xFFC13D56),modifier=Modifier.size(18.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(check.rule.name,
                                            fontWeight=FontWeight.Medium,
                                            style=MaterialTheme.typography.bodySmall)
                                        Text(if(check.rule.criterion==CherrySuccessCriterion.PAIR)
                                            check.rule.predictions.take(8).joinToString(" / ") +
                                            (if(check.rule.predictions.size>8)
                                                " (+${check.rule.predictions.size-8})" else "")
                                            else check.rule.predictions.joinToString(" / "),
                                            style=MaterialTheme.typography.labelSmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        if(check.rule.sourceOrigin.isNotEmpty()) {
                                            Text(check.rule.sourceOrigin,
                                                style=MaterialTheme.typography.labelSmall,
                                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
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
                val rate = CherrySuccessEngine.tabRate(recentDraws,tab)
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
            Text(if(en) "Per draw: at least one recorded rule matched. Draws without a snapshot are excluded."
                else "ပွဲတစ်ပွဲတွင် အနည်းဆုံး မူတစ်ခုမှန်လျှင် ထိုကဏ္ဍအောင်ချက်အဖြစ်တွက်သည်။ မှတ်တမ်းမရှိသောပွဲ မပါဝင်ပါ။",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SuccessCard {
            Text(if(en) "🔥 Top 5 performing rules" else "🔥 အောင်ချက်အကောင်းဆုံး မူ ၅ ခု",
                fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
            val leaders = CherrySuccessEngine.ranking(recentDraws)
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
                            Text(rank.name,style=MaterialTheme.typography.bodySmall,
                                fontWeight=FontWeight.SemiBold)
                            Text(successTabName(rank.tab,en)+" • "+
                                (if(en) "${rank.currentStreak} consecutive qualifying hits"
                                else "အကျုံးဝင် ${rank.currentStreak} ကြိမ်ဆက်မှန်"),
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${rank.correct}/${rank.evaluated}",
                            fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                    }
                }
            }
            Text(if(en) "Historical checks, not a guarantee of the next result."
                else "ယခင်မှတ်တမ်းအောင်ချက်သာဖြစ်ပြီး နောက်ပွဲကို အာမမခံပါ။",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    openedRule?.let { rank ->
        val proof = recentDraws.flatMap { draw ->
            draw.checks.filter { rank.id==it.rule.tab.name+":"+it.rule.id }
                .map { draw.snapshot.target to it }
        }
        AlertDialog(onDismissRequest={openedRule=null},
            title={Text(rank.name,color=AppColors.PrimaryDeep)},
            text={
                Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${rank.correct}/${rank.evaluated} • ${rank.percent}%",
                        fontWeight=FontWeight.Bold)
                    proof.forEach { (draw,check) ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(successDrawName(draw,en),modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.bodySmall)
                            Icon(if(check.correct) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                null,tint=if(check.correct) Color(0xFF16854E) else Color(0xFFC13D56))
                        }
                    }
                }
            },
            confirmButton={TextButton(onClick={openedRule=null}) {
                Text(if(en) "Close" else "ပိတ်")
            }})
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
    val confirmed = confirmedHistory
        .filter { it.digit.matches(Regex("[0-9]{2}")) }
        .distinctBy { it.date to it.session }
        .sortedWith(compareByDescending<HistoricDraw> { it.date }
            .thenByDescending { it.session.ordinal })
    val stored = snapshots.associateBy { it.target }
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(if(en) "Draw history" else "ပွဲစဉ်မှတ်တမ်း")},
        text={
            Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                if(confirmed.isEmpty()) Text(if(en) "No confirmed results."
                    else "အတည်ပြု Result မရှိသေးပါ။")
                confirmed.take(50).forEach { draw ->
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
        },
        confirmButton={TextButton(onClick=onDismiss) {
            Text(if(en) "Close" else "ပိတ်")
        }})
}
