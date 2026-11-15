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

@Composable internal fun CherryMathFormulaRuleCard(
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
 * Same overview pattern as CURRENT: one three-column main/reserve row and
 * an explicit details action. The formula engine and selected order stay intact.
 */
@Composable internal fun CherryMathSelectionOverview(
    selections:List<CherryMathSelection>,all:List<CherryPick>,
    en:Boolean,onPick:(CherryPick)->Unit
){
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color=Color.White,shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,mathEdge)){
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Text(if(en)"Prioritized math pairs" else "ဦးစားပေး သင်္ချာရွေးကွက်များ",
                color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.titleMedium)
            if(selections.isEmpty()){
                Text(if(en)"No eligible math pair for this draw."
                    else "ဒီအကြိမ်အတွက် ရွေးနိုင်သောအတွဲ မရှိသေးပါ။",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }else{
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
                                    fontWeight=FontWeight.Bold,color=color,maxLines=1)
                                Text(selection.pick.pair,fontSize=25.sp,
                                    fontWeight=FontWeight.Black,color=color)
                                Text(cherryMathStars(selection.grade),
                                    fontSize=13.sp,maxLines=1,color=color)
                            }
                        }
                    }
                    repeat(3-selections.take(3).size){Spacer(Modifier.weight(1f))}
                }
            }
            TextButton(onClick={expanded=!expanded},
                modifier=Modifier.fillMaxWidth()){
                Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription=null)
                Spacer(Modifier.width(5.dp))
                Text(if(expanded){
                    if(en)"Hide calculated pairs" else "ဂဏန်းအားလုံး ပိတ်ရန်"
                }else{
                    if(en)"View details — all ${all.size} pairs"
                    else "အသေးစိတ်ကြည့်ရန် — ${all.size} တွဲအားလုံး"
                },fontWeight=FontWeight.SemiBold)
            }
            if(expanded){
                HorizontalDivider(color=mathEdge)
                val strict=selections.firstOrNull()?.mode==CherryMathSelectionMode.STRICT
                Text(if(strict){
                    if(en)"3-draw check passed" else "၃ ပွဲဆက် စစ်ချက်ပြည့်"
                }else{
                    if(en)"Historical evidence needs further checking"
                    else "သမိုင်းစစ်ချက်ကို ထပ်မံစစ်ဆေးရန် လိုသေးသည်"
                },style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                MathPairGrid(all,en,onPick)
                Text(if(en)"Stars describe past evidence, not winning odds. Tap any pair for its formula."
                    else "ကြယ်ပွင့်သည် သမိုင်းစစ်ချက်အဆင့်သာဖြစ်ပြီး အနိုင်ရနိုင်ခြေမဟုတ်ပါ။ အတွဲနှိပ်၍ တွက်နည်းကြည့်နိုင်သည်။",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/** LONE, WAM and BREAK are arranged as three category cards, never an 8-card list. */
internal fun cherryMathCategoryKinds():List<String> = listOf("LONE","WAM","BREAK")

@Composable internal fun CherryMathMethodCategories(
    analysis:CherryMathAnalysis,all:List<CherryPick>,originalSource:String?,
    en:Boolean,onPick:(CherryPick)->Unit,
    onOriginal:(CherryOriginalReference)->Unit
){
    var openKind by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            cherryMathCategoryKinds().forEach{kind->
                val active=openKind==kind
                val label=when(kind){
                    "LONE"->if(en)"Lone" else "လုံးပိုင်"
                    "WAM"->if(en)"Wam" else "ဝမ်းချိန်း"
                    else->if(en)"Break" else "ဘရိတ်"
                }
                val count=analysis.formulas.count{it.kind==kind}
                Surface(
                    onClick={openKind=if(active)null else kind},
                    modifier=Modifier.weight(1f),
                    color=if(active)Color(0xFFFFE7EF) else Color.White,
                    shape=RoundedCornerShape(16.dp),
                    border=BorderStroke(1.dp,if(active)AppColors.PrimaryDeep else mathEdge)
                ){
                    Column(Modifier.fillMaxWidth().padding(horizontal=2.dp,vertical=13.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(label,color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.bodyMedium,maxLines=1)
                        Text(if(en)"$count methods" else "$count နည်း",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(if(active)Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription=null,tint=AppColors.PrimaryDeep,
                            modifier=Modifier.size(20.dp))
                    }
                }
            }
        }
        openKind?.let{kind->
            analysis.formulas.filter{it.kind==kind}.forEach{formula->
                key(formula.id){
                    CherryMathFormulaRuleCard(
                        f=formula,all=all,
                        champion=analysis.champions[formula.kind]==formula.id,
                        source=originalSource,en=en,onPick=onPick,
                        onOriginal=onOriginal
                    )
                }
            }
            // Original conditional watch explanations remain accessible inside
            // the methods section, without changing the five-section ordering.
            if(analysis.watches.isNotEmpty())
                CherryMathSupportFrames(
                    analysis=analysis,all=all,originalSource=originalSource,
                    en=en,onPick=onPick,onOriginal=onOriginal,
                    showChampion=false,showResearch=false
                )
        }
    }
}

/**
 * Secondary Math panels remain separate from the eight visible rule cards.
 * Historical Champion, conditional original rules and Gemini research survive
 * unchanged; none of them can invoke or start the Discovery search.
 */
@Composable internal fun CherryMathSupportFrames(
    analysis:CherryMathAnalysis,all:List<CherryPick>,originalSource:String?,
    en:Boolean,onPick:(CherryPick)->Unit,
    onOriginal:(CherryOriginalReference)->Unit,
    showChampion:Boolean=true,showWatches:Boolean=true,showResearch:Boolean=true
){
    var championOpen by rememberSaveable{mutableStateOf(false)}
    var watchesOpen by rememberSaveable{mutableStateOf(false)}
    var researchOpen by rememberSaveable{mutableStateOf(false)}
    var poolOpen by rememberSaveable{mutableStateOf(false)}
    val overall=analysis.primaryChampion()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(showChampion && overall==null){
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
        }else if(showChampion && overall!=null){
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
        if(showWatches && analysis.watches.isNotEmpty()){
            MathFoldFrame(
                title=if(en)"Conditional original rules"
                    else "အခြေအနေလိုက် မူရင်းမူများ",
                summary=null,
                expanded=watchesOpen,
                onToggle={watchesOpen=!watchesOpen},
            ){
                analysis.watches.forEach{w->
                    Text(w.title,fontWeight=FontWeight.SemiBold,
                        color=AppColors.PrimaryDeep)
                    Text(w.description,style=MaterialTheme.typography.bodySmall)
                    if(w.pairs.isNotEmpty())
                        MathPairGrid(all.filter{p->p.evidence.any{
                            it.id=="watch-${w.id}"
                        }},en,onPick)
                    MathLink(w.id,originalSource,en,onOriginal)
                }
            }
        }
        if(showResearch) MathFoldFrame(
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
