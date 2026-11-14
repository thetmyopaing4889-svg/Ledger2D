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
    Surface(color=Color(0xFFFCFBFB),shape=RoundedCornerShape(10.dp),
        border=BorderStroke(1.dp,Color(0xFFECE5E6))){
        Column{
            Surface(onClick={expanded=!expanded},color=Color.Transparent){
                Row(Modifier.fillMaxWidth().padding(9.dp),
                    horizontalArrangement=Arrangement.spacedBy(7.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text(f.name,color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
                        Text(if(champion){
                            if(en)"Qualified · ${f.streak} consecutive"
                            else "စစ်ချက်ပြည့် · ${f.streak} ပွဲဆက်"
                        }else{
                            if(en)"Calculation only · ${f.streak} consecutive"
                            else "တွက်ချက်ချက်သာ · ${f.streak} ပွဲဆက်"
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


@Composable private fun MathSelectionHero(
    selections:List<CherryMathSelection>,en:Boolean,onPick:(CherryPick)->Unit
){
    // Show the actual selected pairs immediately on opening the Math tab:
    // a single, compact row instead of a generic count or stacked cards.
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),
        border=BorderStroke(1.dp,mathEdge)){
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(en)"Selected math pairs" else "သင်္ချာနည်း ရွေးကွက်",
                color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.titleMedium)
            if(selections.isEmpty()){
                Text(if(en)"—  Not enough eligible calculated pairs"
                    else "—  ရွေးနိုင်သော တွက်ချက်အတွဲ မရှိသေးပါ။",
                    style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }else{
                val mode=selections.first().mode
                Text(if(mode==CherryMathSelectionMode.STRICT){
                    if(en)"3-Step verified · Grade A"
                    else "3-Step စစ်ချက်ပြည့် · Grade A"
                }else{
                    if(en)"Smart Fallback · experimental selection"
                    else "Smart Fallback · စမ်းသပ်ရွေးကွက်"
                },style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    selections.take(3).forEachIndexed{index,selection->
                        val pick=selection.pick
                        val isMain=index==0
                        Surface(onClick={onPick(pick)},
                            modifier=Modifier.weight(1f),
                            color=if(isMain)AppColors.PrimaryDeep
                                else Color(0xFFFFEBF1),
                            shape=RoundedCornerShape(12.dp),
                            border=if(isMain)null else BorderStroke(1.dp,mathEdge)){
                            Column(Modifier.fillMaxWidth().padding(
                                horizontal=4.dp,vertical=9.dp),
                                horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(3.dp)){
                                Text(if(isMain){
                                    if(en)"MAIN" else "မိန်း"
                                }else{
                                    if(en)"RESERVE ${index}" else "အရံ ${index}"
                                },style=MaterialTheme.typography.labelSmall,
                                    fontWeight=FontWeight.Bold,
                                    color=if(isMain)Color.White
                                        else AppColors.PrimaryDeep)
                                Text(pick.pair,
                                    style=MaterialTheme.typography.headlineMedium,
                                    fontWeight=FontWeight.Black,
                                    color=if(isMain)Color.White
                                        else AppColors.PrimaryDeep)
                                Text("Grade ${selection.grade} · #${selection.evidenceRank}",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=if(isMain)Color.White
                                        else AppColors.PrimaryDeep)
                                if(selection.tiedCandidates>1)Text(
                                    if(en)"Tied ×${selection.tiedCandidates}"
                                    else "အဆင့်တူ ${selection.tiedCandidates} ကွက်",
                                    style=MaterialTheme.typography.labelSmall,
                                    color=if(isMain)Color.White
                                        else AppColors.PrimaryDeep)
                            }
                        }
                    }
                }
                if(selections.any{it.tiesBeyondDisplayed}){
                    val count=selections.filter{it.tiesBeyondDisplayed}
                        .maxOf{it.tiedCandidates}
                    Text(if(en)"${count} pairs share the same evidence rank. "+
                        "Shown pairs use a stable display order, not a higher win chance."
                    else "အထောက်အထားအဆင့်တူ ${count} ကွက်ရှိသည်။ ပြထားသောဂဏန်း "+
                        "၃ ကွက်က အစဉ်တကျ ပြသထားခြင်းသာဖြစ်ပြီး ပိုမှန်နိုင်သည်ဟု မဆိုလိုပါ။",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
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

        // Champion is a decision/status panel, NOT another formula result card.
        Surface(
            color=if(overall==null)Color(0xFFF6F3F2) else Color(0xFFFFF5DF),
            shape=RoundedCornerShape(16.dp),
            border=BorderStroke(1.dp,
                if(overall==null)Color(0xFFE6DFDE) else Color(0xFFE5C98B))
        ){
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("★",color=Color(0xFFA87A24),
                        style=MaterialTheme.typography.headlineMedium)
                    Column(Modifier.weight(1f)){
                        Text(if(en)"CHAMPION" else "CHAMPION · အကောင်းဆုံးနည်း",
                            fontWeight=FontWeight.Black,color=Color(0xFF8A651C),
                            style=MaterialTheme.typography.labelLarge)
                        Text(overall?.name ?: if(en)"Not selected yet"
                            else "အတည်ပြုမရသေး",
                            color=AppColors.PrimaryDeep,
                            fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.titleMedium)
                    }
                    if(overall!=null){
                        Column(horizontalAlignment=Alignment.End){
                            Text("${overall.streak}",
                                color=Color(0xFF8A651C),fontWeight=FontWeight.Black,
                                style=MaterialTheme.typography.headlineMedium)
                            Text(if(en)"consecutive" else "ပွဲဆက်",
                                style=MaterialTheme.typography.labelSmall,
                                color=Color(0xFF8A651C))
                        }
                    }
                    IconButton(onClick={championOpen=!championOpen}){
                        Icon(if(championOpen)Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,null,
                            tint=AppColors.PrimaryDeep)
                    }
                }
                if(championOpen){
                    HorizontalDivider(color=Color(0xFFE5C98B))
                    listOf("WAM" to "ဝမ်းချိန်း","LONE" to "လုံးပိုင်",
                        "BREAK" to "ဘရိတ်").forEach{(kind,title)->
                        val winner=analysis.champion(kind)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.SpaceBetween){
                            Text(if(en)kind else title,
                                style=MaterialTheme.typography.bodySmall)
                            Text(winner?.let{"${it.name} · ${it.streak}"}?:
                                (if(en)"Not qualified" else "စစ်ချက်မပြည့်"),
                                style=MaterialTheme.typography.bodySmall,
                                fontWeight=FontWeight.SemiBold)
                        }
                        val previous=analysis.previousChampions[kind]
                        if(previous!=null&&previous!=winner?.id)
                            Text(if(en)"Switched from $previous"
                                else "$previous မှ ပြောင်းရွေးထားသည်",
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.labelSmall)
                    }
                    Text(analysis.loneConsensus?.let{
                        if(en)"Verified single: $it" else "စစ်ဆေးရရှိသော လုံးပိုင်: $it"
                    }?:if(en)"Single-digit consensus not confirmed."
                        else "လုံးပိုင် ဘုံတူရလဒ် မရသေးပါ။",
                        style=MaterialTheme.typography.bodySmall)
                    Text(if(en)"Historical streaks are not a guarantee."
                        else "သမိုင်း Streak က နောက်ပွဲကို အာမမခံပါ။",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Original formulas are compact, neutral evidence rows, opened on demand.
        MathBox{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    MathTitle(if(en)"Original formulas · 8"
                        else "မူရင်းတွက်နည်း · ၈ ခု")
                    Text(if(en)"Individual calculations"
                        else "တစ်နည်းချင်း တွက်ချက်ချက်များ",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick={formulasOpen=!formulasOpen}){
                    Icon(if(formulasOpen)Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,null)
                }
            }
            if(formulasOpen){
                listOf("WAM" to "ဝမ်းချိန်း","LONE" to "လုံးပိုင်",
                    "BREAK" to "ဘရိတ်").forEach{(kind,title)->
                    HorizontalDivider(color=mathEdge)
                    Text(if(en)kind else title,color=AppColors.PrimaryDeep,
                        fontWeight=FontWeight.SemiBold)
                    analysis.formulas.filter{it.kind==kind}.forEach{f->
                        MathFormulaCard(f,report.math,analysis.champions[kind]==f.id,
                            originalSource,en,onPick,onOriginal)
                    }
                }
            }
        }

        MathBox{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    MathTitle(if(en)"All pair calculations"
                        else "တွဲစပ်တွက်ချက်မှုအားလုံး")
                    Text(if(en)"For review, not extra picks"
                        else "စစ်ဆေးကြည့်ရန်သာ",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick={detailsOpen=!detailsOpen}){
                    Icon(if(detailsOpen)Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,null)
                }
            }
            if(detailsOpen){
                MathPairGrid(report.math,en,onPick)
                if(analysis.watches.isNotEmpty()){
                    HorizontalDivider(color=mathEdge)
                    MathTitle(if(en)"Conditional source rules"
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
        }

        MathBox{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    MathTitle(if(en)"Research · 210 variants"
                        else "စမ်းသပ်တွက်နည်း · ၂၁၀")
                    Text(if(en)"Experimental; separate from DOC"
                        else "မူရင်း DOC နှင့် သီးခြား",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick={researchOpen=!researchOpen}){
                    Icon(if(researchOpen)Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,null)
                }
            }
            if(researchOpen){
                if(analysis.confluence.isNotEmpty())
                    Text((if(en)"Research overlap: "
                        else "စမ်းသပ်နည်း ဘုံဆုံမှတ်: ")+
                        analysis.confluence.joinToString(),
                        color=AppColors.PrimaryDeep,fontWeight=FontWeight.SemiBold)
                Text(if(en)"Calculated for research, never promoted to DOC formulas."
                    else "စမ်းသပ်ရလဒ်များကို မူရင်းတွက်နည်းအဖြစ် မရောထားပါ။",
                    style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={poolOpen=!poolOpen}){
                    Text(if(poolOpen){
                        if(en)"Hide all variants" else "နည်းကွဲများ ပိတ်ရန်"
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
