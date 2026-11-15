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
import androidx.compose.ui.unit.sp
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
/**
 * Same clickable white-card header, border, spacing and expansion behavior
 * used by the CURRENT and BORN rule frames.
 */
@Composable private fun MathFoldFrame(
    title:String,summary:String?,expanded:Boolean,onToggle:()->Unit,
    content:@Composable ColumnScope.()->Unit
){
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,mathEdge)){
        Column {
            Surface(onClick=onToggle,color=Color.Transparent){
                Row(Modifier.fillMaxWidth().padding(13.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(title,color=AppColors.PrimaryDeep,
                            fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.bodyMedium)
                        if(!summary.isNullOrBlank())Text(summary,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription=null,tint=AppColors.PrimaryDeep)
                }
            }
            if(expanded){
                HorizontalDivider(color=mathEdge)
                Column(Modifier.fillMaxWidth().padding(13.dp),
                    verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
            }
        }
    }
}

/** Stars show evidence strength only: they are not win probabilities. */
internal fun cherryMathStars(grade:CherryMathGrade):String=when(grade){
    CherryMathGrade.A->"★★★★★"
    CherryMathGrade.B->"★★★☆☆"
    CherryMathGrade.C->"★☆☆☆☆"
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
    // Match CURRENT/BORN rule-card surface and typography.
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,mathEdge)){
        Column{
            Surface(onClick={expanded=!expanded},color=Color.Transparent){
                Row(Modifier.fillMaxWidth().padding(13.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text(f.name,color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.bodyMedium)
                        Text(if(champion){
                            if(en)"Champion · ${f.streak} consecutive"
                            else "Champion · ${f.streak} ပွဲဆက်"
                        }else if(f.streak>0){
                            if(en)"${f.streak} consecutive"
                            else "${f.streak} ပွဲဆက်ကိုက်ညီ"
                        }else{
                            if(en)"No current streak"
                            else "လတ်တလော မကိုက်ညီသေး"
                        },style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(f.result?.toString()?:"—",fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.titleLarge,color=AppColors.PrimaryDeep)
                    Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,tint=AppColors.PrimaryDeep)
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
                    MathTitle(if(en)"Recent results" else "နောက်ဆုံးစစ်ချက်များ")
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
                    MathTitle(if(en)"Every pair from this method" else "ဒီနည်းမှထွက်သော အတွဲအားလုံး")
                    MathPairGrid(producedBy(f,all),en,onPick)
                    MathLink(f.id,source,en,onOriginal)
                }
            }
        }
    }
}


/**
 * Mirrors the CURRENT rule overview: one short title and up to three equally
 * sized, tap-to-explain pairs. No evidence rank or letter-grade UI is exposed.
 */
@Composable private fun MathSelectionHero(
    selections:List<CherryMathSelection>,en:Boolean,onPick:(CherryPick)->Unit
){
    Surface(color=Color.White,shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,mathEdge)){
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Text(if(en)"Selected math pairs" else "သင်္ချာနည်း ရွေးကွက်",
                color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.titleMedium)
            if(selections.isEmpty()){
                Text(if(en)"No eligible pairs for this draw."
                    else "ဒီအကြိမ်အတွက် ရွေးနိုင်သောအတွဲ မရှိသေးပါ။",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }else{
                val strict=selections.first().mode==CherryMathSelectionMode.STRICT
                Text(if(strict){
                    if(en)"3-draw check passed" else "၃ ပွဲဆက် စစ်ချက်ပြည့်"
                }else{
                    if(en)"More checking needed" else "ထပ်မံစစ်ဆေးရန် ရွေးထားသည်"
                },style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    selections.take(3).forEachIndexed{index,selection->
                        val main=index==0
                        val color=if(main)Color.White else AppColors.PrimaryDeep
                        Surface(onClick={onPick(selection.pick)},
                            modifier=Modifier.weight(1f),
                            color=if(main)AppColors.Primary else Color(0xFFFFE7EF),
                            shape=RoundedCornerShape(13.dp),
                            border=if(main)null else
                                BorderStroke(1.dp,Color(0xFFF4C6D4))){
                            Column(Modifier.fillMaxWidth().padding(
                                horizontal=2.dp,vertical=10.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text(if(main){
                                    if(en)"MAIN" else "မိန်း"
                                }else if(selection.reverseOf!=null){
                                    if(en)"RESERVE R" else "အရံ R"
                                }else{
                                    if(en)"RESERVE" else "အရံ"
                                },style=MaterialTheme.typography.labelSmall,
                                    fontWeight=FontWeight.Bold,color=color,
                                    maxLines=1)
                                Text(selection.pick.pair,fontSize=25.sp,
                                    fontWeight=FontWeight.Black,color=color)
                                Text(cherryMathStars(selection.grade),
                                    fontSize=13.sp,maxLines=1,color=color)
                            }
                        }
                    }
                    repeat(3-selections.take(3).size){Spacer(Modifier.weight(1f))}
                }
                if(!strict)Text(
                    if(en)"Historical evidence is limited; review before using."
                    else "သမိုင်းအထောက်အထားနည်းသေး၍ စစ်ဆေးပြီးမှ အသုံးပြုပါ။",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(en)"Stars show historical evidence, not win odds. Tap a pair for details."
                    else "ကြယ်ပွင့်က သမိုင်းစစ်ချက်အဆင့်သာ ဖြစ်သည်။ အတွဲကိုနှိပ်၍ အသေးစိတ်ကြည့်ပါ။",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CherryMathFrame(
    report:CherryReport,originalSource:String?,en:Boolean,
    onPick:(CherryPick)->Unit,onOriginal:(CherryOriginalReference)->Unit
){
    var championOpen by rememberSaveable{mutableStateOf(false)}
    var formulasOpen by rememberSaveable{mutableStateOf(false)}
    var detailsOpen by rememberSaveable{mutableStateOf(false)}
    var researchOpen by rememberSaveable{mutableStateOf(false)}
    var poolOpen by rememberSaveable{mutableStateOf(false)}
    val analysis=report.mathAnalysis
    val selections=report.mathSelections()
    val overall=analysis.primaryChampion()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        MathSelectionHero(selections,en,onPick)

        // Keep a compact status when no historical champion qualifies.
        if(overall==null){
            MathBox {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(if(en)"Champion" else "အကောင်းဆုံးတွက်နည်း",
                        color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Text(if(en)"Not qualified yet" else "စစ်ချက်မပြည့်သေး",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }else{
            MathFoldFrame(
                title=if(en)"Champion" else "အကောင်းဆုံးတွက်နည်း",
                summary=overall.name+" · "+overall.streak+
                    (if(en)" consecutive" else " ပွဲဆက်"),
                expanded=championOpen,
                onToggle={championOpen=!championOpen},
            ){
                listOf("WAM" to "ဝမ်းချိန်း","LONE" to "လုံးပိုင်",
                    "BREAK" to "ဘရိတ်").forEach{(kind,title)->
                    val winner=analysis.champion(kind)
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically){
                        Text(if(en)kind else title,
                            style=MaterialTheme.typography.bodySmall)
                        Text(winner?.let{it.name+" · "+it.streak+
                            (if(en)" consecutive" else " ပွဲဆက်")} ?:
                            (if(en)"Not qualified" else "စစ်ချက်မပြည့်"),
                            style=MaterialTheme.typography.bodySmall,
                            fontWeight=FontWeight.SemiBold)
                    }
                    val previous=analysis.previousChampions[kind]
                    if(previous!=null&&previous!=winner?.id)Text(
                        if(en)"Previously: $previous" else "ယခင်နည်း: $previous",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(analysis.loneConsensus?.let{
                    if(en)"Confirmed single digit: $it" else "ဘုံတူလုံးပိုင်: $it"
                }?:if(en)"Single-digit consensus is not ready."
                    else "လုံးပိုင် ဘုံတူရလဒ် မရသေးပါ။",
                    style=MaterialTheme.typography.bodySmall)
            }
        }

        // Keep all eight original DOC formulas, in compact expandable cards.
        MathFoldFrame(
            title=if(en)"Original formulas · 8" else "မူရင်းတွက်နည်း · ၈ ခု",
            summary=null,
            expanded=formulasOpen,
            onToggle={formulasOpen=!formulasOpen},
        ){
            listOf("WAM" to "ဝမ်းချိန်း","LONE" to "လုံးပိုင်",
                "BREAK" to "ဘရိတ်").forEach{(kind,title)->
                Text(if(en)kind else title,color=AppColors.PrimaryDeep,
                    fontWeight=FontWeight.SemiBold)
                analysis.formulas.filter{it.kind==kind}.forEach{f->
                    MathFormulaCard(f,report.math,analysis.champions[kind]==f.id,
                        originalSource,en,onPick,onOriginal)
                }
            }
        }

        MathFoldFrame(
            title=if(en)"All calculated pairs" else "တွက်ချက်ထားသော အတွဲအားလုံး",
            summary=null,
            expanded=detailsOpen,
            onToggle={detailsOpen=!detailsOpen},
        ){
            Text(if(en)"Stars reflect historical checks, not winning odds."
                else "ကြယ်ပွင့်က သမိုင်းစစ်ချက်အဆင့်သာ ဖြစ်သည်။",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            MathPairGrid(report.math,en,onPick)
            if(analysis.watches.isNotEmpty()){
                HorizontalDivider(color=mathEdge)
                MathTitle(if(en)"Conditional original rules"
                    else "အခြေအနေလိုက် မူရင်းမူများ")
                analysis.watches.forEach{w->
                    Text(w.title,fontWeight=FontWeight.SemiBold)
                    Text(w.description,style=MaterialTheme.typography.bodySmall)
                    if(w.pairs.isNotEmpty())
                        MathPairGrid(report.math.filter{p->p.evidence.any{
                            it.id=="watch-${w.id}"
                        }},en,onPick)
                    MathLink(w.id,originalSource,en,onOriginal)
                }
            }
        }

        // Gemini's 210 experimental variants remain separate from DOC.
        MathFoldFrame(
            title=if(en)"Research · 210 variants" else "စမ်းသပ်တွက်နည်း · ၂၁၀",
            summary=null,
            expanded=researchOpen,
            onToggle={researchOpen=!researchOpen},
        ){
            if(analysis.confluence.isNotEmpty())
                Text((if(en)"Common research digit: "
                    else "စမ်းသပ်နည်း ဘုံဆုံလုံး: ")+
                    analysis.confluence.joinToString(),
                    color=AppColors.PrimaryDeep,fontWeight=FontWeight.SemiBold)
            Text(if(en)"Separate from original DOC methods."
                else "မူရင်း DOC နည်းများနှင့် သီးခြားဖြစ်သည်။",
                style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={poolOpen=!poolOpen}){
                Text(if(poolOpen){
                    if(en)"Hide variants" else "နည်းကွဲများ ပိတ်ရန်"
                }else{
                    if(en)"View 210 variants" else "နည်းကွဲ ၂၁၀ ကြည့်ရန်"
                })
            }
            if(poolOpen)analysis.research.forEach{f->
                Text(f.name+" → "+(f.result?.toString()?:"—")+
                    " · ${f.streak} (${f.hits}/${f.checked})",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun CherryMathPairDetail(
    pair:CherryPick,source:String?,en:Boolean,
    onOriginal:(CherryOriginalReference)->Unit,
    selection:CherryMathSelection?=null
){
    selection?.let{item->
        val label=if(item.mode==CherryMathSelectionMode.STRICT){
            if(en)"3-draw check passed" else "၃ ပွဲဆက် စစ်ချက်ပြည့်"
        }else{
            if(en)"More checking needed" else "ထပ်မံစစ်ဆေးရန် လိုသေးသည်"
        }
        Text(cherryMathStars(item.grade)+" · "+label,
            fontWeight=FontWeight.Bold,color=AppColors.PrimaryDeep)
        if(item.reverseOf!=null)Text(
            if(en)"Reverse of ${item.reverseOf}; shared evidence."
            else "${item.reverseOf} ၏ R အတွဲဖြစ်ပြီး အထောက်အထားတူသည်။",
            style=MaterialTheme.typography.bodySmall)
        if(item.tiedCandidates>1)Text(
            if(en)"Other pairs share this evidence strength."
            else "ဒီအဆင့်နဲ့ အထောက်အထားတူတဲ့ အတွဲတွေ ရှိသေးသည်။",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if(en)"Stars are not a forecast probability."
            else "ကြယ်ပွင့်သည် အနိုင်ရနိုင်ခြေရာခိုင်နှုန်း မဟုတ်ပါ။",
            style=MaterialTheme.typography.labelSmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color=mathEdge)
    }
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
