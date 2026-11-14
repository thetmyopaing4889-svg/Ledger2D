package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.CherryBirth
import com.myanmar.ledger2d.core.domain.CherryPick
import com.myanmar.ledger2d.core.model.DrawIdentity

/** Cards are constructed from the same traces used to rank candidates. */
internal data class CherryRuleGroup(
    val id: String,
    val name: String,
    val detail: String,
    val origin: DrawIdentity?,
    val pairs: List<String>,
)

internal fun cherryRuleGroups(picks:List<CherryPick>):List<CherryRuleGroup> =
    picks.flatMap { it.evidence }
        .distinctBy { it.id to it.pair }
        .groupBy { it.id }
        .map { (id, traces) ->
            CherryRuleGroup(id, traces.first().name, traces.first().detail,
                traces.first().origin, traces.map { it.pair }.distinct().sorted())
        }

/** The top list stays short; expanding it reveals every pair, including rule conflicts. */
@Composable
internal fun CherryRuleFrameOverview(
    heading:String,
    all:List<CherryPick>,
    topLimit:Int,
    en:Boolean,
    expanded:Boolean,
    onToggle:()->Unit,
    onPick:(CherryPick)->Unit,
){
    val eligible=all.filter { it.cuts.isEmpty() }
    val conflicted=all.size-eligible.size
    Surface(color=Color.White,shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,Color(0xFFF4CFDA))){
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Text(heading,color=AppColors.PrimaryDeep,
                fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            if(eligible.isEmpty()){
                Text(if(en)"No eligible pairs in this draw"
                    else "ဒီအကြိမ်အတွက် ဖြတ်မူမငြိသော ရွေးကွက်မရှိသေးပါ။",
                    style=MaterialTheme.typography.bodySmall)
            }
            eligible.take(topLimit).chunked(3).forEachIndexed { line, row ->
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    row.forEachIndexed { index, pick ->
                        val rank=line*3+index+1
                        Surface(onClick={onPick(pick)},modifier=Modifier.weight(1f),
                            shape=RoundedCornerShape(13.dp),
                            color=if(rank==1)AppColors.Primary else Color(0xFFFFE7EF),
                            border=if(rank==1)null else BorderStroke(1.dp,Color(0xFFF4C6D4))){
                            Column(Modifier.padding(vertical=10.dp,horizontal=2.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text(if(en)"Rank $rank" else "အဆင့် $rank",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=if(rank==1)Color.White else AppColors.PrimaryDeep)
                                Text(pick.pair,fontWeight=FontWeight.Black,fontSize=25.sp,
                                    color=if(rank==1)Color.White else AppColors.PrimaryDeep)
                                Text(if(en)"${pick.familyCount} rules"
                                     else "${pick.familyCount} မူ",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=if(rank==1)Color.White else AppColors.PrimaryDeep)
                            }
                        }
                    }
                    repeat(3-row.size){Spacer(Modifier.weight(1f))}
                }
            }
            TextButton(onClick=onToggle,modifier=Modifier.fillMaxWidth()){
                Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                Spacer(Modifier.width(5.dp))
                Text(if(expanded){
                    if(en)"Hide all generated pairs" else "ဂဏန်းအားလုံး ပိတ်ရန်"
                }else{
                    if(en)"View details — all ${all.size} pairs"
                    else "အသေးစိတ်ကြည့်ရန် — ${all.size} တွဲအားလုံး"
                },fontWeight=FontWeight.SemiBold)
            }
            if(expanded){
                HorizontalDivider(color=AppColors.Blush)
                Text(if(en)"${eligible.size} eligible • $conflicted cut-rule conflicts"
                    else "စုစုပေါင်း ${all.size} တွဲ • ${eligible.size} တွဲ မူအရမငြိ • " +
                        "$conflicted တွဲ ဖြတ်မူငြိ",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
                CherryRulePairGrid(all,en,onPick)
                Text(if(en)"Tap any pair to inspect every supporting rule and its ranking."
                    else "အတွဲကိုနှိပ်ပြီး ဘယ်မူက ထုတ်ခဲ့သလဲ၊ ဘာကြောင့် အဆင့်ရသလဲ စစ်ကြည့်နိုင်သည်။",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CherryRulePairGrid(
    picks:List<CherryPick>,en:Boolean,onPick:(CherryPick)->Unit,
){
    if(picks.isEmpty())return
    picks.chunked(5).forEach { row ->
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
            row.forEach { pick ->
                val conflict=pick.cuts.isNotEmpty()
                Surface(onClick={onPick(pick)},modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(9.dp),
                    color=if(conflict)Color(0xFFF4EEF0) else Color(0xFFFFEDF2),
                    border=BorderStroke(1.dp,
                        if(conflict)Color(0xFFD5C3C7) else Color(0xFFF2C5D1))){
                    Column(Modifier.padding(horizontal=1.dp,vertical=7.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(1.dp)){
                        Text(pick.pair,fontWeight=FontWeight.Bold,fontSize=17.sp,
                            color=if(conflict)Color(0xFF96616C) else AppColors.PrimaryDeep)
                        Text(if(conflict){if(en)"cut" else "ဖြတ်"}
                             else {if(en)"${pick.familyCount} rules"
                                   else "${pick.familyCount} မူ"},
                            maxLines=1,style=MaterialTheme.typography.labelSmall,
                            color=if(conflict)Color(0xFF96616C)
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            repeat(5-row.size){Spacer(Modifier.weight(1f))}
        }
    }
}

/** Each rule has a separately expandable explanation and COMPLETE pair set. */
@Composable
internal fun CherryRuleFrameCard(
    rule:CherryRuleGroup,
    all:List<CherryPick>,
    bornEvent:CherryBirth?,
    en:Boolean,
    expanded:Boolean,
    onToggle:()->Unit,
    onPick:(CherryPick)->Unit,
){
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,Color(0xFFF4CFDA))){
        Column{
            Surface(onClick=onToggle,color=Color.Transparent){
                Row(Modifier.fillMaxWidth().padding(13.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(rule.name,color=AppColors.PrimaryDeep,
                            fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium)
                        Text(if(en)"${rule.pairs.size} pairs"
                             else "${rule.pairs.size} တွဲ ထုတ်ပေးထားသည်",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription=null,tint=AppColors.PrimaryDeep)
                }
            }
            if(expanded){
                HorizontalDivider(color=Color(0xFFF4CFDA))
                Column(Modifier.padding(13.dp),
                    verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text(if(en)"Why this rule is applicable" else "ဘာကြောင့် အကျုံးဝင်သလဲ",
                        fontWeight=FontWeight.SemiBold,color=AppColors.PrimaryDeep)
                    Text(rule.detail,style=MaterialTheme.typography.bodyMedium)
                    rule.origin?.let { source ->
                        Text(if(en)"Trigger: ${source.date} • ${source.session.name}"
                            else "အစပြုအကြိမ်: ${source.date} • ${source.session.label}",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    bornEvent?.let { event ->
                        Text(if(en)"Active until ${event.expires} (working days)"
                            else "မူသက်တမ်းကုန်ရက်: ${event.expires} (အလုပ်ဖွင့်ရက်အလိုက်)",
                            style=MaterialTheme.typography.bodySmall,
                            color=AppColors.PrimaryDeep)
                    }
                    Text(if(en)"How the pairs are calculated" else "ဂဏန်းအတွဲ ချဲ့တွက်ပုံ",
                        fontWeight=FontWeight.SemiBold,color=AppColors.PrimaryDeep)
                    Text(cherryRuleExpansionDescription(rule),style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(en)"All ${rule.pairs.size} pairs from this rule"
                        else "ဒီမူမှထွက်သမျှ ${rule.pairs.size} တွဲ",
                        fontWeight=FontWeight.SemiBold,color=AppColors.PrimaryDeep)
                    val lookup=all.associateBy { it.pair }
                    CherryRulePairGrid(rule.pairs.mapNotNull{lookup[it]},en,onPick)
                }
            }
        }
    }
}

private fun cherryRuleExpansionDescription(rule:CherryRuleGroup):String=when{
    rule.id.startsWith("s5-mon@") ->
        "မူရင်း တနင်္လာမနက် ဘရိတ်ဇယားအရ ဘရိတ် ၃ ခုကို ရွေးပြီး " +
        "ဘရိတ်တစ်ခုစီအတွက် (ထိပ် + ပိတ်) mod 10 = B ဖြစ်သော ၁၀ တွဲစီ ချဲ့သည်။"
    rule.id.startsWith("s8-00-bk@") ->
        "00 အပူးထွက်ပြီးနောက် မူရင်းအပိုင်း (၈ခ) မှ သတ်မှတ်ထားသော " +
        "၅ ဘရိတ် ဒဲ့အတွဲ 05, 14, 23, 69, 78 ကို တိုက်ရိုက်ယူသည်။"
    rule.id.startsWith("s1-930") ->
        "9:30 Modern / Internet ထိပ်၊ ပိတ် သို့မဟုတ် ဘရိတ်တူညီမှုကို စစ်ပြီး " +
        "မူရင်းအပိုင်း (၁ခ) အလိုက် 0 ပါဝင်သောအတွဲများနှင့် အပူးများကို ချဲ့သည်။"
    rule.id=="s3-ten" ->
        "ဆယ်ပြည့်/ဘူအုပ်စုအရ (ထိပ် + ပိတ်) mod 10 = 0 ဖြစ်သော အတွဲ ၁၀ တွဲကို ထုတ်သည်။"
    rule.id=="s8-48" ->
        "48 R အပြီး 6 ပိတ်မူအရ 06, 16, 26 ... 96 အထိ ၁၀ တွဲ ချဲ့သည်။"
    rule.id.startsWith("s2-p-") ->
        "နောက်ဆုံးထွက်ဂဏန်းထဲက သက်ဆိုင်ရာလုံးကို ၄ ပေါင်း၍ နောက်ဆုံးတစ်လုံးယူသည်။ " +
        "ရလာသော ပတ်သီးကို ထိပ်နှင့်ပိတ် နှစ်နေရာစလုံးတွင် ချဲ့သည် (၁၉ တွဲ)။"
    rule.id.startsWith("s2-h-") ->
        "အပိုင်း (၂) ထိပ်စီးဇယားအရ သက်ဆိုင်ရာထိပ်ကို ရွေး၍ H0 မှ H9 အထိ ၁၀ တွဲ ချဲ့သည်။"
    rule.name.contains("ခွေ") ->
        "ခွေဂဏန်းများကို အချင်းချင်း ဒဲ့/R တွဲစပ်သည်။ " +
        "မူရင်းက အပူးပါဟု သတ်မှတ်လျှင် အပူးများကိုပါ ထည့်တွက်သည်။"
    rule.name.contains("ဘရိတ်")||rule.name.contains("BK") ->
        "ဘရိတ် B အတွက် (ထိပ် + ပိတ်) mod 10 = B ဖြစ်သော အတွဲ ၁၀ တွဲ ချဲ့သည်။"
    rule.name.contains("ပတ်သီး") ->
        "သတ်မှတ်ပတ်သီး ပါဝင်သော ထိပ်/ပိတ်အတွဲ ၁၉ တွဲကို ချဲ့သည်။"
    rule.name.contains("ထိပ်") ->
        "သတ်မှတ်ထိပ်စီး H ကို H0 မှ H9 အထိ ၁၀ တွဲ ချဲ့သည်။"
    rule.id=="s3-week"||rule.id=="s3-six" ->
        "ယခုအပတ် ထွက်ပြီးသားဂဏန်းများကို မူရင်းအပတ်စဉ်အုပ်စုမှ ချန်၍ " +
        "မထွက်ရသေးသောအတွဲများကို ပြသည်။ သက်တမ်းရှိ မွေးမူနှင့် သီးခြားဖြစ်သည်။"
    else ->
        "မူရင်းစာတမ်း၏ အလိုက်မူ/ဇယားတွင် သတ်မှတ်ထားသောအတွဲများကို " +
        "မူအစပြုအကြိမ်နှင့် တိုက်စစ်၍ ထုတ်ထားသည်။"
}
