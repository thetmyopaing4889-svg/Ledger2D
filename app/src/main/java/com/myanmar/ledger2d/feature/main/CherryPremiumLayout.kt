package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.R
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity

internal enum class CherryOrbTone { RUBY, ROSE, PEARL }
internal fun cherryOrbTone(rate:CherrySuccessRate?):CherryOrbTone {
    if(rate==null || rate.evaluated<5)return CherryOrbTone.PEARL
    val hits=rate.correct.toLong()
    val total=rate.evaluated.toLong()
    return when {
        hits*100>=total*80->CherryOrbTone.RUBY
        hits*100>total*50->CherryOrbTone.ROSE
        else->CherryOrbTone.PEARL
    }
}

internal data class CherryPremiumScores(
    val rules:Map<String,CherrySuccessRate> = emptyMap(),
    val cuts:Map<String,CherrySuccessRate> = emptyMap(),
    val weekly:List<CherryWeeklyLifetime.Episode> = emptyList(),
    val births:List<CherryBornLifetime.Episode> = emptyList(),
) {
    fun rule(tab:CherryTab,id:String)=rules[tab.name+":"+if(tab==CherryTab.BORN)id.substringBefore('@') else id]
}

internal fun cherryPremiumScores(
    snapshots:List<CherrySuccessSnapshot>,history:List<HistoricDraw>,target:DrawIdentity,
):CherryPremiumScores {
    val prior=CherryVerifiedHistory.results(history).filterKeys{
        it.date<target.date || (it.date==target.date && it.session.ordinal<target.session.ordinal)
    }
    val draws=CherrySuccessEngine.completed(snapshots,prior)
    // Exactly the existing offered-pair unit: one rule family/ID per draw.
    // Do not inherit aggregate library or other subrule scores.
    val records=draws.flatMap{d->d.checks.filter{
        it.rule.tab!=CherryTab.MATH && it.rule.criterion==CherrySuccessCriterion.PAIR &&
            CherrySuccessEngine.isCurrentRuleEvidence(d,it)
    }.map{Triple(CherrySuccessEngine.rankKey(it.rule),d.snapshot.target,it.correct)}}
    val rates=records.groupBy{it.first}.mapValues{(_,r)->
        val byDraw=r.groupBy{it.second}.values
        CherrySuccessRate(byDraw.count{checks->checks.any{it.third}},byDraw.size)
    }
    val cuts=CherryCutSuccess.scores(draws).associate{it.id to CherrySuccessRate(it.correct,it.evaluated)}
    return CherryPremiumScores(rates,cuts,CherryWeeklyLifetime.evaluate(draws),CherryBornLifetime.evaluate(draws))
}

internal enum class CherryCutKind { ENDING, DIGIT, PAIR }
internal data class CherryCutGroup(val kind:CherryCutKind,val values:List<String>,val rules:List<CherryCutEvidence>)
internal fun cherryCutGroups(cuts:List<CherryCutEvidence>):List<CherryCutGroup> =
    cuts.groupBy{when(it.id){
        "cut-tail-plus7"->CherryCutKind.ENDING
        "cut-double-digit","cut-morning-head"->CherryCutKind.DIGIT
        else->CherryCutKind.PAIR
    }}.entries.sortedBy{it.key.ordinal}.map{(kind,rs)->
        val values=rs.flatMap{r->when(kind) {
            CherryCutKind.ENDING->r.excluded.map{it.last().toString()}.distinct()
            CherryCutKind.DIGIT->('0'..'9').filter{d->r.excluded.toSet()==
                CherryPairExpander.any(d.digitToInt()).toSet()}.map{it.toString()}
            CherryCutKind.PAIR->r.excluded
        }}.distinct().sorted()
        CherryCutGroup(kind,values,rs)
    }
internal fun CherryCutKind.label(en:Boolean)=when(this) {
    CherryCutKind.ENDING->if(en)"Ending" else "အပိတ်သေ"
    CherryCutKind.DIGIT->if(en)"Digit" else "တစ်လုံးသေ"
    CherryCutKind.PAIR->if(en)"Pairs" else "အတွဲသေ"
}

@Composable
internal fun CherryPremiumPanel(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(18.dp),color=Color.White.copy(alpha=.96f),
        shadowElevation=3.dp,border=BorderStroke(1.dp,Color(0xFFF4CFDA))) {
        Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=7.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
    }
}
@Composable
private fun CherryPremiumTitle(title:String,count:Int?=null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        if(count!=null)Text(count.toString(),color=AppColors.PrimaryDeep,
            style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CherryRuleOrb(rule:CherryRuleGroup,tone:CherryOrbTone,onClick:()->Unit) {
    val interactions=remember{MutableInteractionSource()}
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).90f else 1f,
        animationSpec=tween(if(pressed)85 else 180),label="ruleOrbPress")
    val haptic=LocalHapticFeedback.current
    val asset=when(tone){
        CherryOrbTone.RUBY->R.drawable.cherry_rule_ruby
        CherryOrbTone.ROSE->R.drawable.cherry_rule_rose
        CherryOrbTone.PEARL->R.drawable.cherry_rule_pearl
    }
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(interactionSource=interactions,
        indication=null,onClick={
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }),contentAlignment=Alignment.Center) {
        Image(painterResource(asset),
            contentDescription=CherryRuleWording.title(rule.id,rule.name),
            modifier=Modifier.size(44.dp).graphicsLayer{scaleX=scale;scaleY=scale}
                .shadow(if(pressed)1.dp else 4.dp,CircleShape).clip(CircleShape))
    }
}

@Composable
internal fun CherryPremiumRules(
    groups:List<CherryRuleGroup>,tab:CherryTab,scores:CherryPremiumScores,en:Boolean,
    onRule:(String)->Unit,
) {
    // Stable ID order independent of historical tone, without changing rank.
    val sorted=remember(groups){groups.sortedBy{it.id}}
    val (weekly,regular)=remember(sorted){sorted.partition{isCherryWeeklyWatch(it.id)}}
    CherryPremiumPanel {
        CherryPremiumTitle(if(tab==CherryTab.CURRENT) {
            if(en)"Applicable rules" else "အကျုံးဝင်မူများ"
        } else {if(en)"Active born rules" else "အသက်ဝင်မွေးမူများ"},regular.size)
        if(regular.isEmpty())Text(if(en)"No applicable rules" else "အကျုံးဝင်မူ မရှိသေးပါ။",
            style=MaterialTheme.typography.labelMedium)
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            regular.chunked(if(regular.size>6)2 else 1).forEach{column->
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    column.forEach{rule->CherryRuleOrb(rule,cherryOrbTone(scores.rule(tab,rule.id))){onRule(rule.id)}}
                }
            }
        }
        if(weekly.isNotEmpty()) {
            HorizontalDivider(color=AppColors.Blush)
            CherryPremiumTitle(if(en)"Weekly watches" else "အပတ်စဉ်မူ",weekly.size)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                weekly.forEach{rule->CherryRuleOrb(rule,cherryOrbTone(scores.rule(tab,rule.id))){onRule(rule.id)}}
            }
        }
    }
}

@Composable
internal fun CherryPremiumCuts(cuts:List<CherryCutGroup>,en:Boolean,onCut:(CherryCutKind)->Unit) {
    CherryPremiumPanel {
        CherryPremiumTitle(if(en)"Exclusions" else "အသေမူ")
        if(cuts.isEmpty())Text(if(en)"No applicable exclusions" else "အသေမူ မရှိသေးပါ။",
            style=MaterialTheme.typography.labelMedium)
        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp),
            verticalAlignment=Alignment.CenterVertically) {
            cuts.forEach{group->
                Surface(onClick={onCut(group.kind)},modifier=Modifier.weight(if(group.kind==CherryCutKind.PAIR)1.7f else 1f),
                    color=Color(0xFFFFEDF2),shape=RoundedCornerShape(10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=3.dp,vertical=4.dp),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(group.kind.label(en),style=MaterialTheme.typography.labelSmall,color=AppColors.PrimaryDeep)
                        if(group.kind==CherryCutKind.PAIR)group.values.chunked(3).forEach{line->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                line.forEach{Text(it,fontSize=13.sp,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)}
                            }
                        } else Text(group.values.joinToString(" / "),fontSize=20.sp,
                            fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CherryPremiumPairs(all:List<CherryPick>,en:Boolean,expanded:Boolean,
                                onToggle:()->Unit,onPick:(CherryPick)->Unit) {
    val eligible=remember(all){all.filter{it.cuts.isEmpty()}}
    CherryPremiumPanel {
        CherryPremiumTitle(if(en)"Pairs" else "ရွေးကွက်များ",eligible.size)
        if(eligible.isEmpty())Text(if(en)"No eligible pairs" else "ရွေးကွက် မရှိသေးပါ။",
            style=MaterialTheme.typography.labelMedium)
        eligible.take(6).chunked(3).forEach{line->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                line.forEach{pick->
                    Surface(onClick={onPick(pick)},modifier=Modifier.weight(1f).heightIn(min=48.dp),
                        color=Color(0xFFFFE7EF),shape=RoundedCornerShape(10.dp),
                        border=BorderStroke(1.dp,Color(0xFFF4C6D4))) {
                        Column(Modifier.padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.Center) {
                            Text(pick.pair,fontSize=21.sp,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                            Text(if(en)"${pick.evidence.map{it.id}.distinct().size} rules"
                                else "${pick.evidence.map{it.id}.distinct().size} မူခွဲ",
                                style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                repeat(3-line.size){Spacer(Modifier.weight(1f))}
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(en)"Excluded ${all.size-eligible.size}" else "ဖြတ်ထား ${all.size-eligible.size} တွဲ",
                style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
            TextButton(onClick=onToggle,contentPadding=PaddingValues(horizontal=5.dp)) {
                Text(if(expanded){if(en)"Hide" else "ပိတ်"} else {if(en)"All ${all.size}" else "အားလုံး ${all.size} တွဲ"},
                    style=MaterialTheme.typography.labelMedium)
            }
        }
        if(expanded)all.chunked(4).forEach{line->
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                line.forEach{pick->
                    Surface(onClick={onPick(pick)},modifier=Modifier.weight(1f),
                        color=if(pick.cuts.isEmpty())Color(0xFFFFEDF2) else Color(0xFFF1E9EB),
                        shape=RoundedCornerShape(9.dp)) {
                        Column(Modifier.padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(pick.pair,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                            if(pick.cuts.isNotEmpty())Text(if(en)"Cut" else "ဖြတ်",style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                repeat(4-line.size){Spacer(Modifier.weight(1f))}
            }
        }
    }
}

@Composable
internal fun CherryPremiumRate(rate:CherrySuccessRate?,en:Boolean) {
    Text(if(rate==null || rate.evaluated==0) {
        if(en)"No recorded evidence" else "အောင်ချက်မှတ်တမ်း မရှိသေးပါ။"
    } else if(en)"Recorded hits: ${rate.correct}/${rate.evaluated} draws"
    else "မှတ်တမ်းအောင်ချက် ${rate.correct}/${rate.evaluated} ပွဲ",
        fontWeight=FontWeight.SemiBold,color=AppColors.PrimaryDeep,style=MaterialTheme.typography.bodyMedium)
}
