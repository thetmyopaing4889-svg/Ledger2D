package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.R
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity

/** Row-major, six columns; pages of at most 24, never filler tokens. */
internal fun <T> cherryOrbPages(items:List<T>):List<List<List<T>>> =
    items.chunked(24).map{it.chunked(6)}

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
private fun CherryPremiumTitle(title:String,count:Int?=null,countLabel:String?=null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        if(count!=null)Text(countLabel ?: count.toString(),color=AppColors.PrimaryDeep,
            style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CherryRuleOrb(rule:CherryRuleGroup,tone:CherryOrbTone,weekly:Boolean=false,onClick:()->Unit) {
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
    // The hit target is not clipped: the orb casts its shadow outside its
    // own circle, and the weekly marker stays fully visible at the corner.
    val rim=when(tone){
        CherryOrbTone.PEARL->Color(0xFFC3AAB8)
        CherryOrbTone.ROSE->Color(0xFFB87996)
        CherryOrbTone.RUBY->Color(0xFF9A3655)
    }
    Box(Modifier.size(48.dp).clickable(interactionSource=interactions,
        indication=null,onClick={
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }),contentAlignment=Alignment.Center) {
        Box(Modifier.matchParentSize().background(
            Brush.radialGradient(listOf(rim.copy(alpha=if(pressed).12f else .30f),
                Color.Transparent)),CircleShape))
        Box(Modifier.size(44.dp).graphicsLayer{scaleX=scale;scaleY=scale}
            .shadow(if(pressed)1.dp else 4.dp,CircleShape,clip=false)
            .clip(CircleShape).background(Color.White)
            .border(1.dp,rim,CircleShape)) {
            Image(painterResource(asset),
                contentDescription=CherryRuleWording.title(rule.id,rule.name),
                modifier=Modifier.fillMaxSize().padding(1.dp).clip(CircleShape))
        }
        if(weekly) Box(Modifier.align(Alignment.BottomEnd).padding(1.dp).size(9.dp)
            .background(AppColors.PrimaryDeep,CircleShape).border(1.5.dp,Color.White,CircleShape))
    }
}

@Composable
internal fun CherryPremiumRules(
    groups:List<CherryRuleGroup>,tab:CherryTab,scores:CherryPremiumScores,en:Boolean,
    onRule:(String)->Unit,
) {
    // Weekly rules remain a distinct, marked group at the end. A shared
    // 24-token page budget prevents a fifth row when both groups are present.
    val sorted=remember(groups){groups.sortedBy{it.id}}
    val (weekly,regular)=remember(sorted){sorted.partition{isCherryWeeklyWatch(it.id)}}
    val ordered=remember(regular,weekly){regular+weekly}
    val pages=remember(ordered){cherryOrbPages(ordered)}
    CherryPremiumPanel {
        CherryPremiumTitle(if(tab==CherryTab.CURRENT) {
            if(en)"Applicable rules" else "အကျုံးဝင်မူများ"
        } else {if(en)"Active born rules" else "အသက်ဝင်မွေးမူများ"},ordered.size,
            if(en)"${ordered.size} rules" else "${ordered.size} မူ")
        if(weekly.isNotEmpty()) Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(8.dp).background(AppColors.PrimaryDeep,CircleShape))
            Text(if(en)"Weekly watches · ${weekly.size}" else "အပတ်စဉ်မူ ${weekly.size} ခု",
                color=AppColors.PrimaryDeep,style=MaterialTheme.typography.labelSmall)
        }
        if(ordered.isEmpty())Text(if(en)"No applicable rules" else "အကျုံးဝင်မူ မရှိသေးပါ။",
            style=MaterialTheme.typography.labelMedium)
        else BoxWithConstraints(Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF5EDF2),
                Color(0xFFEDE2EA),Color(0xFFF2E9EF))))
            .border(1.dp,Color(0xFFE8DAE3),RoundedCornerShape(12.dp))
            .padding(horizontal=3.dp,vertical=4.dp)) {
            // Neutral, muted blush-lilac zone, not a saturated pink card.
            // Keep six 48dp touch targets even on narrow screens.
            val pageWidth=maxOf(maxWidth,288.dp)
            val scroll=rememberScrollState()
            val scrollNeeded=pages.size>1 || maxWidth<288.dp
            Row(Modifier.fillMaxWidth().then(if(scrollNeeded)Modifier.horizontalScroll(scroll) else Modifier),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                pages.forEach{page->
                    Column(Modifier.width(pageWidth),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        page.forEach{line->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                line.forEach{rule->key(rule.id) {
                                    CherryRuleOrb(rule,cherryOrbTone(scores.rule(tab,rule.id)),isCherryWeeklyWatch(rule.id)){
                                        onRule(rule.id)
                                    }
                                }}
                                repeat(6-line.size){Spacer(Modifier.size(48.dp))}
                            }
                        }
                    }
                }
            }
        }
        if(ordered.size>24)Text(if(en)"Swipe for remaining ${ordered.size-24} rules"
            else "ကျန် ${ordered.size-24} မူကို ဘေးဆွဲကြည့်ရန်",
            style=MaterialTheme.typography.labelSmall,color=AppColors.PrimaryDeep)
    }
}

@Composable
internal fun CherryPremiumCuts(cuts:List<CherryCutGroup>,en:Boolean,onCut:(CherryCutKind)->Unit) {
    CherryPremiumPanel {
        CherryPremiumTitle(if(en)"Exclusions" else "အသေမူ")
        if(cuts.isEmpty())Text(if(en)"No applicable exclusions" else "အသေမူ မရှိသေးပါ။",
            style=MaterialTheme.typography.labelMedium)
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            cuts.forEach{group->
                val shape=RoundedCornerShape(15.dp)
                Surface(onClick={onCut(group.kind)},modifier=Modifier
                    .weight(if(group.kind==CherryCutKind.PAIR)1.85f else 1f)
                    .fillMaxHeight().heightIn(min=86.dp),
                    color=Color.Transparent,shape=shape,shadowElevation=4.dp,
                    border=BorderStroke(1.dp,Color(0xFFE4B8CA))) {
                    Column(Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.White,
                            Color(0xFFFFF5F8),Color(0xFFF2D7E1))))
                        .padding(horizontal=6.dp,vertical=7.dp),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        // All cut headings share the same top baseline; only
                        // the value area is centered in the remaining height.
                        Text(group.kind.label(en),style=MaterialTheme.typography.labelMedium,
                            fontWeight=FontWeight.SemiBold,color=AppColors.PrimaryDeep,
                            textAlign=TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                            if(group.kind==CherryCutKind.PAIR) Column(Modifier.fillMaxWidth()){
                                group.values.chunked(3).forEach{line->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                                        line.forEach{pair->
                                            Box(Modifier.weight(1f).padding(vertical=2.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Color.White.copy(alpha=.92f))
                                                .border(1.dp,Color(0xFFEBD1DA),RoundedCornerShape(7.dp))
                                                .padding(vertical=3.dp),contentAlignment=Alignment.Center) {
                                                Text(pair,fontSize=14.sp,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                                            }
                                        }
                                        repeat(3-line.size){Spacer(Modifier.weight(1f))}
                                    }
                                }
                            } else Text(group.values.joinToString(" / "),fontSize=30.sp,
                                fontWeight=FontWeight.Black,color=AppColors.PrimaryDeep,textAlign=TextAlign.Center)
                        }
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
        CherryPremiumTitle(if(en)"Pairs" else "ရွေးကွက်များ",eligible.size,
            if(en)"${eligible.size} eligible pairs" else "အကျုံးဝင် ${eligible.size} တွဲ")
        if(eligible.isEmpty())Text(if(en)"No eligible pairs" else "ရွေးကွက် မရှိသေးပါ။",
            style=MaterialTheme.typography.labelMedium)
        eligible.take(6).chunked(3).forEach{line->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                line.forEach{pick->
                    Surface(onClick={onPick(pick)},modifier=Modifier.weight(1f).heightIn(min=48.dp),
                        color=Color.Transparent,shape=RoundedCornerShape(12.dp),
                        shadowElevation=2.dp,border=BorderStroke(1.dp,Color(0xFFE6BDCD))) {
                        Column(Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.White,
                                Color(0xFFFFF2F6),Color(0xFFF4D9E3))))
                            .padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.Center) {
                            Text(pick.pair,fontSize=21.sp,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                            Text(if(en)"${pick.evidence.map{it.id}.distinct().size} rules"
                                else "${pick.evidence.map{it.id}.distinct().size} မူခွဲ",
                                style=MaterialTheme.typography.labelSmall,color=AppColors.PrimaryDeep)
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
                        color=Color.Transparent,shape=RoundedCornerShape(9.dp),
                        shadowElevation=1.dp,border=BorderStroke(1.dp,
                            if(pick.cuts.isEmpty())Color(0xFFE6BDCD) else Color(0xFFD9CDD2))) {
                        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(
                            if(pick.cuts.isEmpty())listOf(Color.White,Color(0xFFF5DDE6))
                            else listOf(Color(0xFFF9F6F7),Color(0xFFE9DFE3))))
                            .padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally) {
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
