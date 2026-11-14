package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.*

private val mathEdge=Color(0xFFF4CFDA)
@Composable private fun MathBox(content:@Composable ColumnScope.()->Unit){
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,mathEdge)){
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
    }
}
@Composable private fun MathTitle(t:String){
    Text(t,color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
        style=MaterialTheme.typography.titleMedium)
}
@Composable private fun MathLink(
    id:String,source:String?,en:Boolean,onOriginal:(CherryOriginalReference)->Unit
){
    CherryMathSources.find(source,id)?.let{ref->
        TextButton(onClick={onOriginal(ref)},
            contentPadding=PaddingValues(horizontal=2.dp,vertical=2.dp)){
            Icon(Icons.Default.MenuBook,null,modifier=Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(if(en)"Original · Read" else "မူရင်းတွက်နည်း · ကြည့်ရန်",
                style=MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun MathPairGrid(
    pairs:List<CherryPick>,en:Boolean,onPick:(CherryPick)->Unit
){
    if(pairs.isEmpty()){
        Text(if(en)"No calculated pairs for this method."
            else "ဒီနည်းမှ တွက်ထွက်သောအတွဲ မရှိသေးပါ။",
            style=MaterialTheme.typography.bodySmall)
        return
    }
    pairs.chunked(5).forEach{row->
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
            row.forEach{p->
                Surface(onClick={onPick(p)},modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(8.dp),
                    color=if(p.cuts.isEmpty())Color(0xFFFFEAF1)else Color(0xFFF3EFF0)){
                    Column(Modifier.padding(vertical=7.dp,horizontal=1.dp),
                        horizontalAlignment=Alignment.CenterHorizontally){
                        Text(p.pair,fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                        Text(if(p.cuts.isEmpty())" " else if(en)"cut" else "ရှောင်",
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            repeat(5-row.size){Spacer(Modifier.weight(1f))}
        }
    }
}
private fun producedBy(f:CherryMathFormula,all:List<CherryPick>):List<CherryPick> =
    all.filter{p->p.evidence.any{ev->
        when(f.kind){
            "WAM"->ev.id=="gold-"+f.id
            "LONE"->ev.id.startsWith("cross-"+f.id+"-")
            "BREAK"->ev.id.endsWith("-"+f.id)||ev.id.endsWith("-"+f.id+"-power")
            else->false
        }
    }}

@Composable private fun MathFormulaCard(
    f:CherryMathFormula,all:List<CherryPick>,champion:Boolean,
    source:String?,en:Boolean,onPick:(CherryPick)->Unit,
    onOriginal:(CherryOriginalReference)->Unit
){
    var expanded by rememberSaveable(f.id){mutableStateOf(false)}
    Surface(color=Color.White,shape=RoundedCornerShape(13.dp),
        border=BorderStroke(1.dp,mathEdge)){
        Column{
            Surface(onClick={expanded=!expanded},color=Color.Transparent){
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement=Arrangement.spacedBy(7.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text(f.name,color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
                        Text(if(champion){
                            if(en)"Selected champion" else "လက်ရှိရွေးထားသော Champion"
                        }else{
                            if(en)"Formula result" else "ပုံသေနည်းရလဒ်"
                        },style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(f.result?.toString()?:"—",fontWeight=FontWeight.Black,
                        style=MaterialTheme.typography.titleLarge,color=AppColors.PrimaryDeep)
                    Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                }
            }
            if(expanded){
                HorizontalDivider(color=mathEdge)
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    MathTitle(if(en)"How this formula works" else "ဒီနည်းတွက်ချက်ပုံ")
                    Text(f.explanation.ifEmpty{
                        if(en)"Previous inputs are unavailable for this draw."
                        else "ဒီအကြိမ်အတွက် ရှေ့ပွဲရလဒ် မပြည့်သေးပါ။"
                    },style=MaterialTheme.typography.bodyMedium)
                    f.result?.let{n->
                        if(f.kind=="WAM"||f.kind=="BREAK")
                            Text("ဒဲ့ "+n+"  ·  ပါဝါ "+((n+5)%10),
                                fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
                    }
                    MathTitle(if(en)"Rolling 20-draw streak" else "နောက်ဆုံးပွဲ ၂၀ ဆက်တိုက်စစ်ချက်")
                    Text("စစ်နိုင် "+f.checked+" ပွဲ · ကိုက်ညီ "+f.hits+
                        " ပွဲ · လက်ရှိ "+f.streak+" ပွဲဆက်",
                        style=MaterialTheme.typography.bodySmall)
                    f.checks.takeLast(5).asReversed().forEach{check->
                        val marker=when(check.hit){true->"✓";false->"×";null->"—"}
                        Text(marker+" "+check.date+" "+check.session.name+
                            " · "+check.actual+" / "+(check.predicted?.toString()?:"—"),
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if(en)"Wamchain checks a digit or power digit; Lone checks " +
                        "the exact digit; Break checks the sum modulo 10."
                    else "စစ်ပုံ — ဝမ်းချိန်းက လုံး/ပါဝါလုံးပါဝင်မှု၊ လုံးပိုင်က " +
                        "သတ်မှတ်လုံးပါဝင်မှု၊ ဘရိတ်က ထိပ်ပိတ်ပေါင်းလဒ်ကို စစ်ပါတယ်။",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    MathTitle(if(en)"Every pair from this method" else "ဒီနည်းမှထွက်သော အတွဲအားလုံး")
                    MathPairGrid(producedBy(f,all),en,onPick)
                    MathLink(f.id,source,en,onOriginal)
                }
            }
        }
    }
}

@Composable
internal fun CherryMathFrame(
    report:CherryReport,originalSource:String?,en:Boolean,
    onPick:(CherryPick)->Unit,onOriginal:(CherryOriginalReference)->Unit
){
    var allOpen by rememberSaveable{mutableStateOf(false)}
    var poolOpen by rememberSaveable{mutableStateOf(false)}
    var crossOpen by rememberSaveable{mutableStateOf(false)}
    val analysis=report.mathAnalysis
    val eligible=report.math.filter{it.cuts.isEmpty()}
    val lone=analysis.champion("LONE")
    val br=analysis.champion("BREAK")
    val main=if(lone?.result!=null&&br?.result!=null){
        val digit=lone.result
        val b=br.result
        val pair=digit.toString()+Math.floorMod(b-digit,10)
        eligible.firstOrNull{p->p.pair==pair&&
            p.evidence.any{it.id=="cross-"+lone.id+"-"+br.id}&&
            report.current.any{q->q.pair==pair&&
                q.evidence.any{it.id.startsWith("s2-h-")}}
        }
    }else null
    val championWam=analysis.champions["WAM"]
    val golden=eligible.filter{p->
        p.evidence.any{it.id=="gold-"+championWam}
    }
    val all=(listOfNotNull(main)+golden+eligible).distinctBy{it.pair}+
        report.math.filter{it.cuts.isNotEmpty()}
    val labels=main?.let{mapOf(it.pair to (if(en)"Main direct" else "မိန်းဒဲ့"))}
        ?:emptyMap()
    Column(verticalArrangement=Arrangement.spacedBy(13.dp)){
        MathTitle(if(en)"🧮 Math & Longest Streak" else "🧮 သင်္ချာနည်း")
        CherryRuleFrameOverview(
            heading=if(en)"Priority calculated pairs" else "ဦးစားပေး တွက်ချက်ဂဏန်းများ",
            all=all,topLimit=4,en=en,expanded=allOpen,
            onToggle={allOpen=!allOpen},onPick=onPick,labels=labels
        )
        MathBox{
            MathTitle(if(en)"Champion Formula & Auto-Switch"
                else "Champion Formula နှင့် Auto-Switch")
            Text(if(en)"Champions are chosen from completed historical draws."
            else "အတည်ပြုပြီးသား ပွဲရလဒ်တွေကို ပြန်စစ်ပြီး " +
                "အုပ်စုအလိုက် ဆက်တိုက်ကိုက်ညီမှုအရှည်ဆုံးနည်းကို ရွေးထားပါတယ်။",
                style=MaterialTheme.typography.bodySmall)
            listOf("WAM" to "ဝမ်းချိန်း","LONE" to "လုံးပိုင်","BREAK" to "ဘရိတ်")
                .forEach{(kind,title)->
                    Text(title,fontWeight=FontWeight.SemiBold)
                    val now=analysis.champion(kind)
                    if(now!=null){
                        Text(now.name+" · "+now.streak+" ပွဲဆက်",
                            color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
                        val prior=analysis.previousChampions[kind]
                        if(prior!=null&&prior!=now.id)
                            Text((analysis.byId(prior)?.name?:prior)+
                                " မှ အလိုအလျောက်ပြောင်းရွေးထားပါတယ်။",
                                style=MaterialTheme.typography.bodySmall)
                    }else Text(if(en)"No eligible streak of three consecutive matches."
                        else "ဆက်တိုက် ၃ ပွဲ ကိုက်ညီတဲ့နည်း မရှိသေးပါ။",
                        style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color=mathEdge)
                }
            Text(if(en)"Past streaks do not guarantee future results."
                else "အရင်ပွဲတွေရဲ့ Streak က နောက်ပွဲရလဒ်ကို အာမခံတာ မဟုတ်ပါ။",
                style=MaterialTheme.typography.labelSmall)
        }
        MathTitle(if(en)"Individual formula cards" else "ပုံသေနည်းတစ်ခုချင်းစီ")
        listOf("WAM" to "ဝမ်းချိန်း","LONE" to "လုံးပိုင်","BREAK" to "ဘရိတ်")
            .forEach{(kind,title)->
                Text(title,color=AppColors.PrimaryDeep,fontWeight=FontWeight.SemiBold)
                analysis.formulas.filter{it.kind==kind}.forEach{f->
                    MathFormulaCard(f,report.math,analysis.champions[kind]==f.id,
                        originalSource,en,onPick,onOriginal)
                }
            }
        MathBox{
            MathTitle(if(en)"Single × direct/power break"
                else "လုံးပိုင် × ဒဲ့ဘရိတ် / ပါဝါဘရိတ်")
            TextButton(onClick={crossOpen=!crossOpen}){
                Text(if(crossOpen){if(en)"Hide pairings" else "တွဲစပ်ချက်များ ပိတ်ရန်"}
                    else{if(en)"View every combination" else "တွဲစပ်ချက်အားလုံး ကြည့်ရန်"})
            }
            if(crossOpen){
                report.math.flatMap{it.evidence}.filter{it.id.startsWith("cross-")}
                    .distinctBy{it.id}.forEach{ev->
                        Text(ev.name,fontWeight=FontWeight.SemiBold)
                        Text(ev.detail,style=MaterialTheme.typography.bodySmall)
                        MathPairGrid(report.math.filter{p->p.evidence.any{
                            it.id==ev.id}},en,onPick)
                        HorizontalDivider(color=mathEdge)
                    }
            }
        }
        MathBox{
            MathTitle(if(en)"Gemini research formula pool"
                else "Gemini သုတေသန Formula Pool")
            Text(if(en)"30 experimental variants: sum, nine-transform and " +
                "absolute difference with constants 0..9. These are not DOC formulas."
            else "ထိပ်ပိတ်ပေါင်း၊ ၉ နှုတ်ဓာတ်ပြောင်း၊ ထိပ်ပိတ်ခြားနားနည်း ၃ မျိုးကို " +
                "k=0 မှ 9 အထိ စမ်းထားတဲ့ နည်း ၃၀ ပါ။ မူရင်း DOC က ဒီကိန်းသေတွေ မပေးထားပါ။",
                style=MaterialTheme.typography.bodySmall)
            if(analysis.confluence.isNotEmpty())Text(
                "စမ်းသပ်သုံးနည်း ဘုံဆုံမှတ်: "+analysis.confluence.joinToString(),
                color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
            TextButton(onClick={poolOpen=!poolOpen}){
                Text(if(poolOpen){if(en)"Hide variants" else "စမ်းသပ်နည်းများ ပိတ်ရန်"}
                     else{if(en)"View all 30 variants" else "စမ်းသပ်နည်း ၃၀ ကြည့်ရန်"})
            }
            if(poolOpen)analysis.research.forEach{f->
                Text(f.name+" → "+(f.result?.toString()?:"—")+
                    " · "+f.streak+" ပွဲဆက် ("+f.hits+"/"+f.checked+")",
                    style=MaterialTheme.typography.bodySmall)
            }
            Text(if(en)"Research methods are never promoted as original DOC champions."
                else "စမ်းသပ်နည်းတွေကို မူရင်း Champion အဖြစ် မရောရွေးပါ။",
                style=MaterialTheme.typography.labelSmall)
        }
        if(analysis.watches.isNotEmpty()){
            MathBox{
                MathTitle(if(en)"Other source-backed math rules"
                    else "မူရင်းစာတမ်းပါ အခြေအနေလိုက်မူများ")
                analysis.watches.forEach{w->
                    Text(w.title,color=AppColors.PrimaryDeep,fontWeight=FontWeight.SemiBold)
                    Text(w.description,style=MaterialTheme.typography.bodySmall)
                    if(w.pairs.isNotEmpty())MathPairGrid(report.math.filter{p->
                        p.evidence.any{it.id=="watch-"+w.id}},en,onPick)
                    MathLink(w.id,originalSource,en,onOriginal)
                    HorizontalDivider(color=mathEdge)
                }
            }
        }
    }
}

@Composable internal fun CherryMathPairDetail(
    pair:CherryPick,source:String?,en:Boolean,
    onOriginal:(CherryOriginalReference)->Unit
){
    MathTitle(if(en)"How this pair was calculated"
        else "ဒီဂဏန်းကို ဘယ်လိုတွက်ထားသလဲ")
    pair.evidence.distinctBy{it.id}.forEach{ev->
        Text(ev.name,fontWeight=FontWeight.SemiBold)
        Text(ev.detail,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        val ids=when{
            ev.id.startsWith("gold-")->listOf(ev.id.removePrefix("gold-"))
            ev.id.startsWith("cross-")->ev.id.split("-").drop(1).take(2)
            ev.id.startsWith("watch-")->listOf(ev.id.removePrefix("watch-"))
            else->emptyList()
        }
        ids.forEach{MathLink(it,source,en,onOriginal)}
    }
    if(pair.cuts.isNotEmpty()){
        Text(if(en)"Cut-rule conflicts" else "ရှောင်ရန်မူနှင့် ငြိနေသောအချက်များ",
            color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.SemiBold)
        pair.cuts.forEach{Text(CherryRuleWording.cutReason(it),
            style=MaterialTheme.typography.bodySmall)}
    }
}
