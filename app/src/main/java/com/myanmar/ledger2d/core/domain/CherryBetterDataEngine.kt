package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.DayOfWeek
import kotlin.math.abs

/** Independent Better Data computation. Never reads current/future winning slots. */
enum class CherryTab { CURRENT, BORN, MATH, SUMMARY }

data class CherryEvidence(
    val id: String,
    val family: String,
    val name: String,
    val detail: String,
    val pair: String,
    val tab: CherryTab,
    val origin: DrawIdentity? = null,
)
data class CherryPick(
    val pair: String,
    val evidence: List<CherryEvidence>,
    val cuts: List<String>,
    val score: Int,
) {
    val sourceCount: Int get() = evidence.map { it.tab }.distinct().size
    val familyCount: Int get() = evidence.map { it.family }.distinct().size
}
/**
 * Grades describe strength of the available historical checks, NOT a forecast
 * probability. A = 3-step qualification, B/C = experimental fallback.
 */
enum class CherryMathSelectionMode { STRICT, FALLBACK }
enum class CherryMathGrade { A, B, C }
data class CherryMathSelection(
    val pick: CherryPick,
    /** Display slot (main=1; reserves=2/3), not forecast quality. */
    val rank: Int,
    val mode: CherryMathSelectionMode,
    val grade: CherryMathGrade,
    val reason: String,
    /** Evidence rank of the PAIR FAMILY, with direct and reverse grouped. */
    val evidenceRank: Int = rank,
    /** Number of distinct pair families sharing this evidence rank. */
    val tiedCandidates: Int = 1,
    val tiesBeyondDisplayed: Boolean = false,
    /** The first displayed pair in the same direct/reverse family. */
    val reverseOf: String? = null,
    /** A reverse direction exists but did not fit in the 3 visible slots. */
    val hiddenReverse: Boolean = false
)
internal data class CherryMathCandidate(
    val pick: CherryPick,
    val evidenceScore: Int,
    val grade: CherryMathGrade,
    /** Presentation order only; never part of evidence score. */
    val preferDirect: Boolean = false
)
private data class CherryMathRankGroup(
    val key:String,
    val best:CherryMathCandidate,
    val members:List<CherryMathCandidate>
)
private fun cherryPairFamily(pair:String):String=
    minOf(pair,pair.reversed())

/**
 * Calculate evidence rank by unique direct/reverse families, not by the number
 * of derived directions. A reverse is still shown as a separate 2D choice
 * when space allows, but shares evidence rank with the same formula family.
 * Numeric order is deterministic presentation only, NEVER a win-rate signal.
 */
internal fun rankCherryMathCandidates(
    candidates: List<CherryMathCandidate>,
    mode: CherryMathSelectionMode
): List<CherryMathSelection> {
    val strength=mapOf(CherryMathGrade.A to 3,CherryMathGrade.B to 2,
        CherryMathGrade.C to 1)
    val byPair=candidates.filter{it.pick.cuts.isEmpty()}
        .groupBy{it.pick.pair}.values.mapNotNull{same->
            same.maxWithOrNull(
                compareBy<CherryMathCandidate>{strength[it.grade]?:0}
                    .thenBy{it.evidenceScore}
                    .thenBy{it.preferDirect}
            )
        }
    val groups=byPair.groupBy{cherryPairFamily(it.pick.pair)}
        .map{(key,items)->
            val best=items.maxWithOrNull(
                compareBy<CherryMathCandidate>{strength[it.grade]?:0}
                    .thenBy{it.evidenceScore}
            )!!
            // Direct preference only selects which DIRECTION appears first.
            val members=items.sortedWith(
                compareByDescending<CherryMathCandidate>{it.preferDirect}
                    .thenByDescending{strength[it.grade]?:0}
                    .thenByDescending{it.evidenceScore}
                    .thenBy{it.pick.pair}
            )
            CherryMathRankGroup(key,best,members)
        }.sortedWith(
            compareByDescending<CherryMathRankGroup>{strength[it.best.grade]?:0}
                .thenByDescending{it.best.evidenceScore}.thenBy{it.key}
        )
    val flat=groups.flatMap{g->g.members.map{g to it}}
    val visible=flat.take(3)
    val visiblePairs=visible.map{it.second.pick.pair}.toSet()
    return visible.mapIndexed{index,(group,candidate)->
        val firstGroupIndex=groups.indexOfFirst{
            it.best.grade==group.best.grade&&
                it.best.evidenceScore==group.best.evidenceScore
        }
        val tiedGroups=groups.count{
            it.best.grade==group.best.grade&&
                it.best.evidenceScore==group.best.evidenceScore
        }
        val unseenTied=groups.any{g->
            g.key!=group.key&&g.best.grade==group.best.grade&&
                g.best.evidenceScore==group.best.evidenceScore&&
                g.members.none{it.pick.pair in visiblePairs}
        }
        val unseenReverse=group.members.any{it.pick.pair !in visiblePairs}
        val reverseOf=group.members.first().pick.pair
            .takeIf{it!=candidate.pick.pair}
        val reason=when(mode){
            CherryMathSelectionMode.STRICT->"3-Step စစ်ချက်ပြည့်"
            CherryMathSelectionMode.FALLBACK->if(candidate.grade==CherryMathGrade.B)
                "သမိုင်းစစ်ချက်အထောက်အထားရှိ · စမ်းသပ်ရွေး"
                else "အထောက်အထားနည်းသေး · စမ်းသပ်ရွေး"
        }
        CherryMathSelection(candidate.pick,index+1,mode,candidate.grade,
            reason,firstGroupIndex+1,tiedGroups,unseenTied,
            reverseOf,unseenReverse)
    }
}
data class CherryBirth(
    val id: String,
    val name: String,
    val origin: DrawIdentity,
    val expires: LocalDate,
    val produced: Set<String>,
)
data class CherryValue(
    val id: String,
    val name: String,
    val digit: Int,
    val formula: String,
    val kind: String,
)
data class CherryReport(
    val target: DrawIdentity,
    val observed: Int,
    val last: HistoricDraw?,
    val current: List<CherryPick>,
    val born: List<CherryPick>,
    val math: List<CherryPick>,
    val summary: List<CherryPick>,
    val ranked: List<CherryPick>,
    val activeRules: List<CherryBirth>,
    val singles: List<CherryValue>,
    val breaks: List<CherryValue>,
    val cuts: Map<String, List<String>>,
    val has930: Boolean,
    val mathAnalysis: CherryMathAnalysis,
) {
    fun visible(tab: CherryTab, limit: Int = 5): List<CherryPick> = when (tab) {
        CherryTab.CURRENT -> current
        CherryTab.BORN -> born
        CherryTab.MATH -> math
        CherryTab.SUMMARY -> summary
    }.filter { it.cuts.isEmpty() }.take(limit)
    /**
     * One MAIN, at most two RESERVES. Strict 3-step results take precedence.
     * A soft fallback is allowed only from calculable DOC methods, not the
     * research pool's 210 fitted variants. All cut conflicts stay excluded.
     */
    fun mathShortlist(): List<CherryPick> = mathSelections().map{it.pick}

    fun mathSelections(): List<CherryMathSelection> {
        val strict=mathStrictCandidates()
        if(strict.isNotEmpty())return rankCherryMathCandidates(
            strict,CherryMathSelectionMode.STRICT)
        // Compare historical hit rates WITHIN each method role; WAM, LONE,
        // and BREAK have different hit definitions and base frequencies.
        fun best(kind:String):CherryMathFormula? =
            mathAnalysis.formulas.filter{it.kind==kind&&it.result!=null}
                .sortedWith(compareByDescending<CherryMathFormula>{
                    if(it.checked>=5)it.hits*100/it.checked else -1
                }.thenByDescending{it.streak}
                    .thenByDescending{it.checked}
                    .thenBy{it.id})
                .firstOrNull()
        val wam=best("WAM")
        val lone=best("LONE")
        val br=best("BREAK")
        if(wam==null&&lone==null&&br==null)return emptyList()
        val goldId=wam?.let{"gold-${it.id}"}
        val crossId=if(lone!=null&&br!=null)
            "cross-${lone.id}-${br.id}" else null
        val powerId=if(br?.id=="b1"||br?.id=="b2")crossId?.plus("-power")
            else null
        val direct=if(lone?.result!=null&&br?.result!=null)
            "${lone.result}"+Math.floorMod(br.result-lone.result,10) else null
        val eligible=math.filter{p->p.cuts.isEmpty()&&p.evidence.any{e->
            e.id==goldId||(crossId!=null&&e.id==crossId)||
                (powerId!=null&&e.id==powerId)
        }}
        // Count distinct original roles, not the number of combinations.
        // The research overlap can corroborate, never generate a candidate.
        val ranked=eligible.map{p->
            val gold=p.evidence.any{it.id==goldId&&goldId!=null}
            val cross=p.evidence.any{it.id==crossId&&crossId!=null}
            val power=p.evidence.any{it.id==powerId&&powerId!=null}
            val supporting=buildList{
                if(gold)wam?.let{add(it)}
                if(cross||power){
                    lone?.let{add(it)}
                    br?.let{add(it)}
                }
            }
            val recent=supporting.count{it.streak>=1}
            // B requires that ONE supporting formula meets all historical
            // conditions. Do not combine checked/hits from one method with
            // another method's streak to manufacture a stronger grade.
            val recentSupported=supporting.any{
                it.checked>=8&&it.hits>=3&&it.streak>=1
            }
            // No invented role bonus: a CROSS is not intrinsically more
            // predictive than a GOLD pair, nor is an in-sample research
            // overlap proof. Only observable recent checks add evidence.
            val score=recent*2+(if(recentSupported)2 else 0)
            val grade=if(recentSupported)
                CherryMathGrade.B else CherryMathGrade.C
            CherryMathCandidate(p,score,grade,
                preferDirect=cross&&p.pair==direct)
        }
        return rankCherryMathCandidates(ranked,CherryMathSelectionMode.FALLBACK)
    }

    private fun mathStrictCandidates(): List<CherryMathCandidate> {
        val wam=mathAnalysis.champion("WAM")
        val lone=mathAnalysis.champion("LONE")?.takeIf{
            it.result==mathAnalysis.loneConsensus
        }
        val br=mathAnalysis.champion("BREAK")
        val directId=if(lone!=null&&br!=null)
            "cross-${lone.id}-${br.id}" else null
        val allowed=mutableSetOf<String>()
        wam?.let{allowed+="gold-${it.id}"}
        directId?.let{
            allowed+=it
            allowed+=it+"-power"
        }
        if(wam!=null&&mathAnalysis.confluence.singleOrNull()==wam.result)
            allowed+="research-confluence"
        if(allowed.isEmpty())return emptyList()
        val directPair=if(lone?.result!=null&&br?.result!=null)
            lone.result.toString()+
                Math.floorMod(br.result-lone.result,10) else null
        return math.filter{
            it.cuts.isEmpty()&&it.evidence.any{ev->ev.id in allowed}
        }.map{p->
            // Different checks determine evidence score, not pair number.
            val score=p.evidence.count{it.id in allowed}*10+
                (if(p.evidence.any{it.id=="research-confluence"})5 else 0)
            CherryMathCandidate(p,score,CherryMathGrade.A,
                preferDirect=directId!=null&&p.pair==directPair&&
                    p.evidence.any{it.id==directId})
        }
    }
}

object CherryPairExpander {
    val all = (0..99).map { it.toString().padStart(2, '0') }
    fun wheel(raw: String, doubles: Boolean = false): List<String> {
        val ds = raw.filter { it in '0'..'9' }.toList().distinct()
        return ds.flatMap { a -> ds.mapNotNull { b ->
            if (doubles || a != b) "$a$b" else null
        } }.distinct()
    }
    fun any(d: Int) = all.filter { it[0].digitToInt() == d || it[1].digitToInt() == d }
    fun top(d: Int) = (0..9).map { "$d$it" }
    fun tail(d: Int) = (0..9).map { "$it$d" }
    fun bk(b: Int) = (0..9).map { a -> "$a${Math.floorMod(b-a,10)}" }
    fun cross(d: Int, b: Int): List<String> {
        val y = Math.floorMod(b-d,10)
        return listOf("$d$y", "$y$d").distinct()
    }
    fun power(d: Int): List<String> = listOf("$d${(d+5)%10}", "${(d+5)%10}$d").distinct()
}

object CherryBetterDataEngine {
    private val section6 = mapOf(
        "01" to listOf("68","46"),"04" to listOf("11"),"07" to listOf("49","29"),
        "09" to listOf("68"),"14" to listOf("17"),"17" to listOf("14"),
        "23" to listOf("79"),"27" to listOf("47","37"),"43" to listOf("89"),
        "49" to listOf("60"),"54" to listOf("04"),"59" to listOf("47","79"),
        "64" to listOf("65"),"65" to listOf("27","64"),"69" to listOf("09"),
        "78" to listOf("72","50","61","83","13","41","85","63"),
        "79" to listOf("47","29"),"83" to listOf("43"),"84" to listOf("13","10"),
        "89" to listOf("49","76"),"92" to listOf("90")
    )
    private val wheels = mapOf(
        "00" to listOf("0457","0459"),"11" to listOf("1267"),
        "22" to listOf("2479"),"33" to listOf("1368"),
        "44" to listOf("3489"),"55" to listOf("1267"),
        "66" to listOf("2378"),"77" to listOf("0358"),
        "88" to listOf("2479"),"99" to listOf("0156")
    )
    private val doubleBorn = mapOf(
        "00" to listOf("77","75","55"),
        "11" to listOf("76","64","78","84","70","71"),
        "22" to listOf("07","48","64","82","34","79","72","49","42"),
        "33" to listOf("81","89","51","59"),
        "44" to listOf("83","81","31","88","33","91","93","21","23","27","38","49"),
        "55" to listOf("17","36","37","35","07","05"),
        "66" to listOf("17","14","97","94"),
        "77" to listOf("09","02","29","22"),
        "88" to listOf("31","39","11","19"),
        "99" to listOf("61","63","41","43")
    )
    private val deadTriples = mapOf(
        0 to "795",1 to "608",2 to "714",3 to "825",4 to "923",
        5 to "043",6 to "195",7 to "260",8 to "371",9 to "486"
    )
    private val topFollowers = mapOf(
        0 to "5678",1 to "678",2 to "345678",3 to "5678",
        4 to "123890",5 to "0123",6 to "012789",7 to "012",
        8 to "123",9 to "456"
    )
    private val friendDigits = mapOf(
        0 to "47",2 to "94",3 to "16",6 to "38",8 to "02",9 to "75"
    )
    private val mondayBk = mapOf(
        0 to "057",1 to "168",2 to "274",3 to "358",4 to "429",
        5 to "503",6 to "691",7 to "702",8 to "831",9 to "946"
    )
    private val power = listOf("05","50","16","61","27","72","38","83","49","94")
    private val siblings = listOf("01","10","12","21","23","32","34","43",
        "45","54","56","65","67","76","78","87","89","98","09","90")
    private val astrology = listOf("07","70","18","81","24","42","35","53","69","96")
    private val weeklyEight = listOf("27","82","48","54","65","46","94","92")
    private val groupSix = listOf("34","38","39","48","49","89")
    // User-supplied additional Rules Library, source page 1.
    private val mobGroup = setOf("17","20","80","56","51")
    private fun before(d: HistoricDraw, x: DrawIdentity): Boolean =
        d.date < x.date || (d.date == x.date && d.session.ordinal < x.session.ordinal)
    private fun identity(d: HistoricDraw) = DrawIdentity(d.date,d.session)
    private fun immediate(x: DrawIdentity, closed: Set<LocalDate>): DrawIdentity {
        if (x.session == DrawSession.EVENING) return DrawIdentity(x.date,DrawSession.MORNING)
        var d=x.date.minusDays(1)
        while (!DrawSchedule.isWorkingDate(d,closed)) d=d.minusDays(1)
        return DrawIdentity(d,DrawSession.EVENING)
    }
    private fun priorSlots(rows: List<HistoricDraw>,target: DrawIdentity,
                           closed: Set<LocalDate>,n:Int): List<HistoricDraw>? {
        var key=immediate(target,closed)
        val list=mutableListOf<HistoricDraw>()
        repeat(n) {
            val found=rows.firstOrNull{it.date==key.date&&it.session==key.session}
                ?:return null
            list+=found
            key=immediate(key,closed)
        }
        return list
    }
    private fun daysAfter(date:LocalDate,target:LocalDate,closed:Set<LocalDate>):Int {
        var d=date.plusDays(1)
        var n=0
        while (!d.isAfter(target)) {
            if (DrawSchedule.isWorkingDate(d,closed)) n++
            d=d.plusDays(1)
        }
        return n
    }
    private fun plusWorkingDays(date:LocalDate,n:Int,closed:Set<LocalDate>):LocalDate {
        var x=date
        var count=0
        while(count<n){
            x=x.plusDays(1)
            if(DrawSchedule.isWorkingDate(x,closed))count++
        }
        return x
    }
    private fun bk(s:String)=(s[0].digitToInt()+s[1].digitToInt())%10

    fun calculate(raw:List<HistoricDraw>,target:DrawIdentity,modern:String?=null,
                  internet:String?=null,closed:Set<LocalDate> = emptySet()):CherryReport {
        val rows=raw.filter{validHistoric2D(it.digit)&&before(it,target)&&it.date !in closed}
            .sortedWith(compareBy<HistoricDraw>{it.date}.thenBy{it.session.ordinal})
            .distinctBy{it.date to it.session}
        val prev=priorSlots(rows,target,closed,1)?.firstOrNull()
        val current=mutableListOf<CherryEvidence>()
        val born=mutableListOf<CherryEvidence>()
        val cut=mutableMapOf<String,MutableList<String>>()
        val births=mutableListOf<CherryBirth>()
        fun add(to:MutableList<CherryEvidence>,rule:String,family:String,
                title:String,detail:String,pairs:Iterable<String>,origin:DrawIdentity?=null,tab:CherryTab) {
            pairs.filter(::validHistoric2D).distinct().forEach{p->
                to+=CherryEvidence(rule,family,title,detail,p,tab,origin)
            }
        }
        if(prev!=null){
            val last=prev.digit
            val origin=identity(prev)
            val tail=(last[1].digitToInt()+7)%10
            CherryPairExpander.tail(tail).forEach{p->
                cut.getOrPut(p){mutableListOf()}+="+7 ပိတ်ဖြတ်: ${last[1]} → $tail"
            }
            if(last[0]==last[1]){
                CherryPairExpander.any(last[0].digitToInt()).forEach{p->
                    cut.getOrPut(p){mutableListOf()}+="အပူး Cut Digit: ${last[0]}"
                }
            }
            if(target.session==DrawSession.EVENING){
                CherryPairExpander.any(last[0].digitToInt()).forEach{p->
                    cut.getOrPut(p){mutableListOf()}+="မနက်ထိပ် Cut Digit: ${last[0]}"
                }
            }
            val triple=deadTriples[last[1].digitToInt()].orEmpty()
            CherryPairExpander.wheel(triple).forEach{p->
                cut.getOrPut(p){mutableListOf()}+="အပိုင်း (၈) ${triple} (ဒဲ့/R) ဖြတ်မူ"
            }
            fun rule(id:String,name:String,pairs:List<String>,text:String?=null)=
                add(current,id,id,name,
                    "နောက်ဆုံးထွက် ${last} ကြောင့် ${name} အကျုံးဝင်သည်။ " +
                    (text ?: "မူရင်းဇယား/အလိုက်မူမှ အတွဲ ${pairs.distinct().size} တွဲ ထုတ်ထားသည်။"),
                    pairs,origin,CherryTab.CURRENT)
            last.toSet().forEach{d->
                val digit=d.digitToInt()
                val p=(digit+4)%10
                rule("s2-p-$digit","အပိုင်း (၂) +4 ပတ်သီး",
                    CherryPairExpander.any(p),
                    "(${digit} + ၄) mod ၁၀ = ${p} ပတ်သီး။ " +
                        "${p} ပါဝင်သော ထိပ်/ပိတ် ၁၉ တွဲကို ချဲ့သည်။")
                topFollowers[digit].orEmpty().forEach{h->
                    rule("s2-h-$digit-$h","အပိုင်း (၂) • ${h} ထိပ်စီး",
                        CherryPairExpander.top(h.digitToInt()),
                        "${digit} လုံးက အပိုင်း (၂) ဇယားအရ ${h} ထိပ်စီးကို ညွှန်ပြသည်။ " +
                            "${h}0 မှ ${h}9 အထိ ချဲ့သည်။")
                }
                friendDigits[digit]?.let{friends->
                    rule("s2-f-$digit","အပိုင်း (၂) တွဲဖက်လုံးပိုင်",
                        listOf("${friends[0]}${friends[1]}","${friends[1]}${friends[0]}"),
                        "${digit} နောက် ${friends} တွဲဖက်")
                }
            }
            // Explicit R trigger rows: keep the canonical evidence ID for both
            // directions. The recorded output pairs remain unchanged.
            val reverseSourceKeys = setOf("43","79","83","84")
            val sourceKey = if(last in section6) last
                else last.reversed().takeIf { it in reverseSourceKeys } ?: last
            section6[sourceKey]?.let{
                rule("s6-$sourceKey","အပိုင်း (၆) သီးသန့်မွေး",it)
            }
            // Source 10R has an explicit break but no expiry. Same immediate
            // next-draw convention as existing duration-free current rules.
            if(last=="10"||last=="01")
                rule("s6-10","10R နောက် 7 ဘရိတ်",CherryPairExpander.bk(7))
            // Explicit page-2 6-BK => 1649 wheel; current draw only.
            if(bk(last)==6)
                rule("new-bk-six","6 ဘရိတ်နောက် 1649 အခွေ",
                    CherryPairExpander.wheel("1649"),
                    "$last သည် 6 ဘရိတ်။ 1649 အခွေ ဒဲ့/ပြန် 12 တွဲ။")
            // Page 3: only 50 is a new rule; 12R / 27R pre-exist.
            if(last=="50")
                rule("new-born-50","50 နောက် 3/8 ထိပ်",
                    CherryPairExpander.top(3)+CherryPairExpander.top(8),
                    "50 ထွက်ပြီး 3 ထိပ်/8 ထိပ် (37,82) ဆက်စပ်မူ။")
            if(last in mobGroup) {
                // The source gives TWO outcomes for the same group trigger,
                // not a rule to bet the other four source pairs.
                rule("new-mob-double","လူမိုက်ကြီးအပူး",
                    (0..9).map{"$it$it"},
                    "လူမိုက်အုပ်စုထဲမှ $last ထွက်ပြီး အပူးအုပ်စု လိုက်မူ။ သက်တမ်းမပေးထားသဖြင့် နောက်တစ်ပွဲအတွက်သာ ပြ။")
                // The 1267 rule references a separate mob group whose
                // members are not listed in the user's source image.
            }
            if(last in setOf("98","11","91"))
                rule("new-special-khant","2D Ko Khant — 0 ထိပ်",
                    CherryPairExpander.top(0),
                    "98 / 11 / 91 ထွက်ပြီး 0 ထိပ်ကို စောင့်ကြည့်သော အပိုင်းသာ တွက်ထားသည်။")
            if(last=="12"||last=="21") {
                // Already exists as reader rule cherry-038; this fills only
                // the previously missing Better Data calculation.
                rule("s6-12","12R → 48R",listOf("48","84"))
                rule("s6-12-wheel","12R → 4789 အခွေ",
                    CherryPairExpander.wheel("4789"))
            }
            if(last=="70")rule("s6-70","70 R နောက် ပါဝါ",power)
            if(last=="80"||last=="08")rule("s8-80","80 R → 1264 အပူးခွေ",
                CherryPairExpander.wheel("1264",true))
            if(last=="67"||last=="76")rule("s8-67","67 R → 8123 အပူးခွေ",
                CherryPairExpander.wheel("8123",true))
            if(last[0]=='8')rule("s2-8head","8 ထိပ် → 01234 အပူးခွေ",
                CherryPairExpander.wheel("01234",true))
            if(last=="48"||last=="84")rule("s8-48","48 R နောက် 6 ပိတ်",CherryPairExpander.tail(6))
            if(last=="13"||last=="31")rule("s8-13","13 R နောက် 3 ဘရိတ်",CherryPairExpander.bk(3))
            if(last=="74"||last=="47")rule("s8-74","74 R နောက် 8 ပတ်သီး",CherryPairExpander.any(8))
            if(last=="71"||last=="17")rule("s8-71","71 R နောက် 2 ပတ်သီး",CherryPairExpander.any(2))
            if(last=="15"||last=="51")rule("s8-15","15 R နောက် 4 ပတ်သီး",CherryPairExpander.any(4))
            if(last=="88")rule("s8-88","88 နောက် 3 ပတ်သီး",CherryPairExpander.any(3))
            if(last in listOf("16","94","83"))rule("s3-ten","ပါဝါနောက် ဆယ်ပြည့်",
                (0..9).map{"$it${(10-it)%10}"})
            if(last in listOf("50","27")||last[0]==last[1])
                rule("s3-sib","ညီအစ်ကို အလိုက်မူ",siblings)
            if(last in listOf("12","65","32"))rule("s3-power","ညီအစ်ကိုနောက် ပါဝါ",power)
            if(last in siblings)rule("s3-astro","ညီအစ်ကိုနောက် နက္ခတ်",astrology)
            if(last in listOf("35","81","10","29","38","56","47"))
                rule("s3-double","အပူး အလိုက်မူ",(0..9).map{"$it$it"})
            if(bk(last)==9)rule("s3-9to7","9 BK → 7 BK",CherryPairExpander.bk(7))
            if(bk(last)==7)rule("s3-7to4","7 BK → 4 BK",CherryPairExpander.bk(4))
            if(target.session==DrawSession.EVENING&&last=="05")
                rule("s7-am05","မနက် 05 → ညနေ 7–2",
                    listOf("52","25","27","72"))
            if(target.session==DrawSession.EVENING&&last=="92")
                rule("s6-am92","မနက် 92 → ညနေ 7 ဘရိတ်",
                    listOf("43","07"))
            if(target.session==DrawSession.MORNING&&modern?.matches(Regex("[0-9]{2}"))==true&&
                internet?.matches(Regex("[0-9]{2}"))==true){
                if(modern[0]==internet[0]||modern[1]==internet[1])
                    rule("s1-930","9:30 ထိပ်/ပိတ်တူ",
                        CherryPairExpander.any(0)+(0..9).map{"$it$it"},
                        "Modern $modern / Internet $internet")
                if(bk(modern)==bk(internet))
                    rule("s1-930bk","9:30 ဘရိတ်တူ",
                        CherryPairExpander.any(0)+(0..9).map{"$it$it"}+
                            listOf(modern.reversed(),internet.reversed()),
                        "Modern $modern / Internet $internet")
            }
        }
        fun birth(origin:HistoricDraw,code:String,name:String,pairs:List<String>,
                  limit:Int=5,calendarDays:Boolean=false) {
            // The original legacy born rules use trading days. The user-supplied
            // 75 and 19R rules say "days" with no trading-day qualification.
            val elapsed=if(calendarDays)
                (target.date.toEpochDay()-origin.date.toEpochDay()).toInt()
            else daysAfter(origin.date,target.date,closed)
            if(elapsed>limit || elapsed<0)return
            val values=pairs.filter(::validHistoric2D).toSet()
            if(values.isEmpty())return
            val after=rows.filter{r->before(origin,identity(r))}
            if(after.any{it.digit in values})return
            val key="$code@${origin.date}-${origin.session.name}"
            births+=CherryBirth(key,name,identity(origin),
                if(calendarDays) origin.date.plusDays(limit.toLong())
                else plusWorkingDays(origin.date,limit,closed),values)
            add(born,key,code,name,
                "${origin.date} ${origin.session.label}: ${origin.digit} → $name",
                values,identity(origin),CherryTab.BORN)
        }
        rows.takeLast(20).forEach{d->
            val x=d.digit
            // Source page 1/3: plain "2 days" and "5 days", not working days.
            if(x=="75"&&d.session==DrawSession.MORNING&&d.date<target.date)
                birth(d,"new-born-75","75 နောက် 45789 အခွေ",
                    CherryPairExpander.wheel("45789"),limit=2,calendarDays=true)
            if(x=="19"||x=="91")
                birth(d,"new-bk-19r","19R နောက် 1 ဘရိတ်",
                    CherryPairExpander.bk(1),limit=5,calendarDays=true)
            // Source 94R explicitly says five days, with no trading-day
            // qualifier. Apply the documented calendar-day convention.
            if(x=="94"||x=="49")
                birth(d,"s6-94","94R နောက် 1 ပတ်သီး",CherryPairExpander.any(1),
                    limit=5,calendarDays=true)
            if(x[0]==x[1]) {
                wheels[x].orEmpty().forEachIndexed{i,w->
                    birth(d,"s4-wheel-$x-$i","${x} အပူး → ${w} ခွေ",
                        CherryPairExpander.wheel(w))
                }
                birth(d,"s4-born-$x","${x} အပူးမွေးဂဏန်း",doubleBorn[x].orEmpty())
            }
            if(x=="80"||x=="08")birth(d,"s8-80","80 R → 1264 အပူးခွေ",
                CherryPairExpander.wheel("1264",true))
            if(x=="67"||x=="76")birth(d,"s8-67","67 R → 8123 အပူးခွေ",
                CherryPairExpander.wheel("8123",true))
            if(x=="00")birth(d,"s8-00-bk","00 နောက် 5 ဘရိတ်",
                listOf("05","14","23","69","78"))
            if(d.date.dayOfWeek==DayOfWeek.FRIDAY&&x in power)
                birth(d,"s5-fri","သောကြာပါဝါမွေး",listOf("09","13","26","47","58"))
            if(d.date.dayOfWeek==DayOfWeek.WEDNESDAY&&
                d.session==DrawSession.EVENING&&x[0]==x[1])
                birth(d,"s5-wed","ဗုဒ္ဓဟူးညနေ အပူးနောက် 7 BK",CherryPairExpander.bk(7))
            if(d.date.dayOfWeek==DayOfWeek.MONDAY&&
                d.session==DrawSession.MORNING){
                val bs=mondayBk[bk(x)].orEmpty()
                birth(d,"s5-mon","တနင်္လာမနက် ဘရိတ်အလိုက်",
                    bs.flatMap{CherryPairExpander.bk(it.digitToInt())})
            }
        }
        val mon=target.date.minusDays((target.date.dayOfWeek.value-1).toLong())
        val thisWeek=rows.filter{it.date>=mon&&it.date<mon.plusDays(7)}
            .map{it.digit}.toSet()
        add(born,"s3-week","s3-week","အပတ်စဉ် ၈ ကွက်",
            "ယခုအပတ် မပေါ်သေးသော အုပ်စု",
            weeklyEight.filterNot{it in thisWeek},null,CherryTab.BORN)
        if(groupSix.any{it in thisWeek})add(born,"s3-six","s3-six",
            "အပတ်စဉ် ၆ ကွက်အုပ်စု","ယခုအပတ် အုပ်စုတစ်ကွက် ပေါ်ပြီး",
            groupSix.filterNot{it in thisWeek},null,CherryTab.BORN)
        // Legacy DOC/Gemini Math generation is retired. The V3 discovery
        // engine is a separate, explicit-tap workflow and never executes here.
        fun group(e:List<CherryEvidence>):List<CherryPick> =
            e.distinctBy{Triple(it.id,it.pair,it.origin)}.groupBy{it.pair}
                .map{(p,ev)->
                    val familyCount=ev.map{it.family}.distinct().size
                    val sourceCount=ev.map{it.tab}.distinct().size
                    val specific=ev.count{it.id.startsWith("cross-")||
                        it.id.startsWith("gold-")||it.id.startsWith("s6-")||
                        it.id.startsWith("s4-")}
                    CherryPick(p,ev,cut[p].orEmpty().distinct(),
                        familyCount*10+sourceCount*15+specific*2)
                }.sortedWith(compareBy<CherryPick>{it.cuts.isNotEmpty()}
                    .thenByDescending{it.sourceCount}
                    .thenByDescending{it.familyCount}
                    .thenByDescending{it.score}.thenBy{it.pair})
        val currentPicks=group(current)
        val bornPicks=group(born)
        // Final Summary now contains only CURRENT + BORN evidence.
        // Preserve documented cuts and deduplicate reverse families.
        val ranked=group(current+born).filter{it.cuts.isEmpty()}
            .distinctBy{minOf(it.pair,it.pair.reversed())}
        val summary=ranked.take(3)
        // Temporary compatibility payload only: no Champion or Gemini is
        // evaluated when Better Data opens. V3 owns Math independently.
        val inactiveMath=CherryMathAnalysis(
            formulas=emptyList(),champions=emptyMap(),
            previousChampions=emptyMap(),research=emptyList(),
            confluence=emptyList(),watches=emptyList(),testedDraws=0,
        )
        return CherryReport(
            target,rows.size,rows.lastOrNull(),currentPicks,bornPicks,
            emptyList(),summary,ranked,births,emptyList(),emptyList(),
            cut.mapValues{it.value.distinct()},
            target.session==DrawSession.MORNING&&
                modern?.matches(Regex("[0-9]{2}"))==true&&
                internet?.matches(Regex("[0-9]{2}"))==true,
            inactiveMath,
        )
    }
}