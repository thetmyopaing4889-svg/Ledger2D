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
    // Schedule known at calculation time. Empty means no lifetime evidence.
    val eligibleSlots: List<DrawIdentity> = emptyList(),
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
    private val wheels = mapOf(
        "00" to listOf("0457","0459"),"11" to listOf("1267"),
        "22" to listOf("2479"),"33" to listOf("1368"),
        "44" to listOf("3489"),"55" to listOf("1267"),
        "66" to listOf("2378"),"77" to listOf("0358"),
        "88" to listOf("2479"),"99" to listOf("0156")
    )
    private val doubleBorn = mapOf(
        "00" to listOf("77","75","55"),
        "11" to listOf("76","64","78","84","70","07","71","17","67"),
        "22" to listOf("07","48","64","82","34","79","72","49","42"),
        "33" to listOf("81","89","51","59"),
        "44" to listOf("83","38","81","18","31","13","88","33","91","93","21","23","27","72","49","94"),
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
            if(target.session==DrawSession.EVENING&&last=="05")
                rule("s7-am05","မနက် 05 → ညနေ 7–2",
                    listOf("52","25","27","72"))
            if(target.session==DrawSession.EVENING&&prev.session==DrawSession.MORNING&&
                prev.date==target.date&&last=="92")
                rule("s6-am92","မနက် 92 နောက် ညနေ 7 ဘရိတ်မူ",
                    CherryPairExpander.bk(7),"မနက် 92 ဒဲ့ • ညနေ 7 ဘရိတ် • အထူး 43/07")
        }
        fun eligibleSlots(origin:HistoricDraw,starts:LocalDate,end:LocalDate,
                          endSession:DrawSession=DrawSession.EVENING):List<DrawIdentity> {
            if(end<starts)return emptyList()
            return generateSequence(starts){it.plusDays(1)}.takeWhile{it<=end}
                .filter{DrawSchedule.isWorkingDate(it,closed)}
                .flatMap{date->sequenceOf(DrawIdentity(date,DrawSession.MORNING),
                    DrawIdentity(date,DrawSession.EVENING))}
                .filter{before(origin,it) && (it.date<end || it.session.ordinal<=endSession.ordinal)}
                .toList()
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
            val end=if(calendarDays) origin.date.plusDays(limit.toLong())
                else plusWorkingDays(origin.date,limit,closed)
            births+=CherryBirth(key,name,identity(origin),end,values,
                eligibleSlots(origin,origin.date,end))
            add(born,key,code,name,
                "${origin.date} ${origin.session.label}: ${origin.digit} → $name",
                values,identity(origin),CherryTab.BORN)
        }
        rows.takeLast(20).forEach{d->
            val x=d.digit
            // Source page 3: plain five calendar days, not working days.
            if(x=="19"||x=="91")
                birth(d,"new-bk-19r","19R နောက် 1 ဘရိတ်",
                    CherryPairExpander.bk(1),limit=5,calendarDays=true)
            // Source 94R explicitly says five days, with no trading-day
            // qualifier. Apply the documented calendar-day convention.
            if(x=="94"||x=="49")
                birth(d,"s6-94","94R နောက် 1 ပတ်သီး",CherryPairExpander.any(1),
                    limit=5,calendarDays=true)
        }
        val mon=target.date.minusDays((target.date.dayOfWeek.value-1).toLong())
        val thisWeek=rows.filter{it.date>=mon&&it.date<mon.plusDays(7)}
            .map{it.digit}.toSet()
        // User-confirmed omissions, clarification batches 1–4.
        // IDs are independent of legacy evidence; no retrospective scoring.
        // Use only rows strictly before the target (filtered above).
        val weekRows=rows.filter{it.date>=mon && it.date<mon.plusDays(7)}
        val weekEnd=mon.plusDays(4)
        val doubles=(0..9).map{"$it$it"}
        fun withReverse(pairs:List<String>)=pairs.flatMap{listOf(it,it.reversed())}.distinct()
        fun breaks(vararg digits:Int)=digits.flatMap{CherryPairExpander.bk(it)}.distinct()
        fun followDigits(vararg digits:Int)=digits.flatMap{CherryPairExpander.any(it)}.distinct()
        fun dayRow(date:LocalDate,session:DrawSession)=rows.firstOrNull{
            it.date==date && it.session==session
        }
        fun currentRule(code:String,name:String,pairs:List<String>,origin:HistoricDraw,detail:String) {
            add(current,code,code,name,detail,pairs,identity(origin),CherryTab.CURRENT)
        }
        fun scopedBorn(code:String,name:String,pairs:List<String>,origin:HistoricDraw,
                       end:LocalDate,stopOnHit:Boolean=true,detail:String="",
                       starts:LocalDate=origin.date,family:String=code,
                       endSession:DrawSession=DrawSession.EVENING) {
            if(target.date<starts || target.date>end || !before(origin,target))return
            val values=pairs.filter(::validHistoric2D).toSet()
            if(values.isEmpty())return
            if(stopOnHit && rows.any{before(origin,identity(it)) &&
                    it.date>=starts && it.date<=end && it.digit in values})return
            val key="$code@${origin.date}-${origin.session.name}"
            births+=CherryBirth(key,name,identity(origin),end,values,
                eligibleSlots(origin,starts,end,endSession))
            add(born,key,family,name,
                "${origin.date} ${origin.session.label}: ${origin.digit} • $name" +
                    if(detail.isBlank()) "" else " • $detail",
                values,identity(origin),CherryTab.BORN)
        }
        // One-day rules: previous calendar day's two confirmed sessions.
        // No stale substitution or Friday-to-Monday rollover. Both AM/PM
        // remain applicable for that day; this is a per-session observation.
        val yesterday=target.date.minusDays(1)
        if(yesterday>=mon && DrawSchedule.isWorkingDate(yesterday,closed)) {
            val am=dayRow(yesterday,DrawSession.MORNING)
            val pm=dayRow(yesterday,DrawSession.EVENING)
            if(am!=null && pm!=null) {
                val a=am.digit
                val b=pm.digit
                val common=(0..1).filter{a[it]==b[it]}
                    .map{Math.floorMod(a[it].digitToInt()-1,10)}.distinct()
                val context="$yesterday • မနက် $a / ညနေ $b"
                if(common.isNotEmpty())
                    currentRule("s1-day-head-tail","ထိပ်တူ / ပိတ်တူမူ",
                        common.flatMap{CherryPairExpander.any(it)}+CherryPairExpander.any(0)+doubles,
                        pm,"$context • တူဂဏန်း 1 လျှော့၊ 0 သို့ အပူး")
                if(bk(a)==bk(b))
                    currentRule("s1-day-break","ဘရိတ်တူမူ",
                        CherryPairExpander.any(0)+doubles+thisWeek,pm,
                        "$context • ဘရိတ်တူ၊ 0 / အပူး / ယခုအပတ် ပြန်ကျ")
                if((a[0].digitToInt()+b[1].digitToInt())%10==
                    (a[1].digitToInt()+b[0].digitToInt())%10)
                    currentRule("s1-day-diagonal","ဘေးတစ်စောင်း ဘရိတ်တူမူ",
                        astrology+thisWeek,pm,"$context • ဘေးတစ်စောင်း ဘရိတ်တူ")
                val digit=(a[0].digitToInt()+b[0].digitToInt()+b[1].digitToInt())%10
                currentRule("s9-wailin","WaiLin နေ့စဉ် တစ်လုံးပိုင်မူ",
                    CherryPairExpander.any(digit),pm,
                    "$context • ${a[0]} + ${b[0]} + ${b[1]} → $digit")
            }
        }
        if(prev!=null) {
            val x=prev.digit
            if(x=="57"||x=="75")currentRule("s6-57","57R နောက် 357 အခွေပူးမူ",
                CherryPairExpander.wheel("357",true),prev,"$x • နောက် session တစ်ကြိမ်")
            if('0' in x)currentRule("s8-zero-follow","0 နောက် 7 ပတ်သီးမူ",
                CherryPairExpander.any(7),prev,"$x • 0 ပါဝင်")
            if(prev.session==DrawSession.EVENING && target.session==DrawSession.MORNING &&
                prev.date==yesterday && x[0]=='4')
                currentRule("s8-evening-four","ညနေ 4 ထိပ်မူ",power+astrology,prev,
                    "${prev.date} ညနေ $x • နောက်နေ့မနက်")
        }
        // Exactly four scheduled sessions, including gaps in observed history.
        // The expiry date is the date of session 4; the detail retains session.
        fun nextSlot(slot:DrawIdentity):DrawIdentity {
            if(slot.session==DrawSession.MORNING)
                return DrawIdentity(slot.date,DrawSession.EVENING)
            var next=slot.date.plusDays(1)
            while(!DrawSchedule.isWorkingDate(next,closed))next=next.plusDays(1)
            return DrawIdentity(next,DrawSession.MORNING)
        }
        rows.takeLast(4).filter{it.digit=="06"||it.digit=="60"}.forEach{origin->
            var slot=identity(origin)
            val validSlots=(1..4).map{slot=nextSlot(slot);slot}
            if(target in validSlots)
                scopedBorn("s6-06","06R ညီအစ်ကိုမွေးမူ",siblings,origin,
                    validSlots.last().date,detail="4 sessions • ${validSlots.last().session.label}",
                    endSession=validSlots.last().session)
        }
        // 9:30 is an independent input, not an official-result derivative.
        // The revised break-rule ID does not merge old reverse-input evidence.
        if(target.session==DrawSession.MORNING && modern?.matches(Regex("[0-9]{2}"))==true &&
            internet?.matches(Regex("[0-9]{2}"))==true) {
            val context="Modern $modern / Internet $internet"
            if(modern[0]==internet[0] || modern[1]==internet[1])
                add(current,"s1-930","s1-930","9:30 ထိပ်/ပိတ်တူမူ",context,
                    followDigits(0)+doubles,null,CherryTab.CURRENT)
            if(bk(modern)==bk(internet))
                add(current,"s1-930bk-v2","s1-930bk-v2","9:30 ဘရိတ်တူမူ",
                    "$context • ယခုအပတ် ပြန်ကျ",
                    followDigits(0)+doubles+thisWeek,null,CherryTab.CURRENT)
        }

        // Absent throughout the previous complete trading week, then first
        // return this week. A missing historical slot is not evidence of absence.
        val previousWeekStart=mon.minusDays(7)
        val previousDates=(0L..4L).map{previousWeekStart.plusDays(it)}
            .filter{DrawSchedule.isWorkingDate(it,closed)}
        val previousRows=rows.filter{it.date in previousDates}
        val previousComplete=previousDates.isNotEmpty() && previousDates.all{date->
            dayRow(date,DrawSession.MORNING)!=null && dayRow(date,DrawSession.EVENING)!=null
        }
        val returnPairs=listOf(
            listOf("50","60","90"),listOf("01","13","15"),listOf("22","23","24"),
            listOf("23","13","43"),listOf("24","34","94"),listOf("51","59","57"),
            listOf("56","06","96"),listOf("79","75","77"),listOf("84","86","89"),
            listOf("09","59","79"))
        if(previousComplete && prev!=null && prev.date>=mon && target.date<=weekEnd) {
            // Require known earlier scheduled slots this week too, to establish
            // that this is the first return, not a gap in observed results.
            val expectedEarlier=(0L..4L).map{mon.plusDays(it)}
                .filter{DrawSchedule.isWorkingDate(it,closed)}
                .flatMap{date->listOf(DrawIdentity(date,DrawSession.MORNING),
                    DrawIdentity(date,DrawSession.EVENING))}
                .filter{it.date<prev.date || (it.date==prev.date && it.session.ordinal<prev.session.ordinal)}
            if(expectedEarlier.all{dayRow(it.date,it.session)!=null}) {
                for(d in prev.digit.toSet()) {
                    if(previousRows.none{d in it.digit} &&
                        weekRows.none{before(it,identity(prev)) && d in it.digit})
                        currentRule("s2-return-$d","$d ပျောက်ပြီးပြန်လာမူ",
                            returnPairs[d.digitToInt()],prev,
                            "$previousWeekStart မှ ယခင်အပတ် $d မပါ • ယခုအပတ် ${prev.digit} တွင် ပြန်လာ")
                }
            }
        }
        rows.takeLast(4).filter{it.digit[1]=='6'}.forEach{origin->
            var slot=identity(origin)
            val slots=(1..4).map{slot=nextSlot(slot);slot}
            if(target in slots)
                scopedBorn("s2-tail6-49","6 ပိတ်နောက် 4/9 ပတ်သီးမူ",
                    followDigits(4,9),origin,slots.last().date,
                    detail="4 sessions • ${slots.last().session.label}",endSession=slots.last().session)
        }

        // Literal listed pairs are not reversed unless R is explicitly present.
        // Named arithmetic breaks use complete modulo-10 expansion.
        val directSiblings=listOf("01","12","23","34","45","56","67","78","89","90")
        val directPower=listOf("05","16","27","38","49")
        val directAstrology=listOf("07","18","24","35","69")
        val tenSum=(1..9).map{"$it${10-it}"}
        for(origin in weekRows) {
            val x=origin.digit
            if(x in setOf("50","27"))
                scopedBorn("s3-w-010","ပါဝါနောက် ညီအစ်ကိုမူ",
                    directSiblings,origin,weekEnd)
            if(x in setOf("12","65","32"))
                scopedBorn("s3-w-011","ညီအစ်ကိုနောက် ပါဝါ / ဆယ်ပြည့်မူ",
                    directPower+tenSum,origin,weekEnd)
            // The specific subset and its parent share one evidence family.
            if(x in setOf("34","45","67","78","89"))
                scopedBorn("s3-w-012","သီးသန့် ညီအစ်ကိုနောက် နက္ခတ်မူ",
                    directAstrology,origin,weekEnd,family="s3-w-astro")
            if(x in setOf("35","81"))
                scopedBorn("s3-w-013","နက္ခတ်နောက် အပူးမူ",doubles,origin,weekEnd)
            if(x in setOf("10","29","38","56","47"))
                scopedBorn("s3-w-014","အပူးအလိုက်မူ",doubles,origin,weekEnd)
            if(x[0]==x[1])
                scopedBorn("s3-w-015","အပူးနောက် ညီအစ်ကိုမူ",
                    directSiblings,origin,weekEnd)
            if(x in directSiblings)
                scopedBorn("s3-w-016","ညီအစ်ကိုနောက် နက္ခတ်မူ",
                    directAstrology,origin,weekEnd,family="s3-w-astro")
            if(bk(x)==9)
                scopedBorn("s3-w-017-9","9 ဘရိတ်နောက် 7 ဘရိတ်မူ",
                    breaks(7),origin,weekEnd)
            if(bk(x)==7)
                scopedBorn("s3-w-017-7","7 ဘရိတ်နောက် 4 ဘရိတ်မူ",
                    breaks(4),origin,weekEnd)
        }

        // Reviewed weekly watches, exact pairs only (no implicit R).
        if(DrawSchedule.isWorkingDate(target.date,closed) && target.date<=weekEnd) {
            add(born,"s3-week","s3-week","အပတ်စဉ် ၈ ကွက်",
                "ယခုအပတ် မပေါ်သေးသော အုပ်စု",
                weeklyEight.filterNot{it in thisWeek},null,CherryTab.BORN)
            val seenSix=groupSix.filter{it in thisWeek}
            // First distinct hit is the trigger; three additional distinct
            // hits complete the watch. Repeated pairs never advance it.
            if(seenSix.size in 1..3)
                add(born,"s3-six","s3-six","အပတ်စဉ် ၆ ကွက်အုပ်စု",
                    "နောက်ဆက်တွဲ ထွက်ပြီး ${seenSix.size-1}/3",
                    groupSix.filterNot{it in thisWeek},null,CherryTab.BORN)
        }
        for(origin in weekRows) {
            val x=origin.digit
            if(x[0]==x[1]) {
                wheels[x].orEmpty().forEachIndexed{i,w->
                    scopedBorn("s4-wheel-$x-$i","${x} အပူး → ${w} ခွေ",
                        CherryPairExpander.wheel(w),origin,weekEnd)
                }
                // The 44 subset is a separate source card but shares the
                // parent born family. Do not add another specificity credit.
                scopedBorn("s4-born-$x","${x} အပူးမွေးဂဏန်း",
                    doubleBorn[x].orEmpty(),origin,weekEnd)
                if(x=="44")scopedBorn("s4-mid44","အလယ်အပူးမူ",
                    listOf("83","38","81","18","31","13","88","33"),
                    origin,weekEnd,family="s4-born-44")
            }
            if(x=="01")scopedBorn("s6-01-68","01 နောက် 68 မွေးမူ",
                listOf("68"),origin,weekEnd)
            if(x=="01"||x=="10")scopedBorn("s6-01-46","01R နောက် 46 မွေးမူ",
                listOf("46"),origin,weekEnd)
            if(x=="04")scopedBorn("s6-04","04 နောက် 11 မွေးမူ",
                listOf("11"),origin,weekEnd)
            if(x=="07")scopedBorn("s6-07","07 နောက် 49/29 မွေးမူ",
                listOf("49","29"),origin,weekEnd)
            if(x=="70")scopedBorn("s6-70","70 နောက် ပါဝါမွေးမူ",
                power,origin,weekEnd)
        }
        // Wednesday evening double: four scheduled sessions, capped by the
        // following Wednesday. Missing observations don't extend the slots.
        rows.takeLast(4).filter{it.date.dayOfWeek==DayOfWeek.WEDNESDAY &&
            it.session==DrawSession.EVENING && it.digit[0]==it.digit[1]}.forEach{origin->
            var slot=identity(origin)
            val slots=(1..4).map{slot=nextSlot(slot);slot}
                .filter{it.date<=origin.date.plusDays(7)}
            if(target in slots)
                scopedBorn("s5-wed","ဗုဒ္ဓဟူးညနေ အပူးနောက် 7 ဘရိတ်မူ",
                    breaks(7),origin,slots.last().date,
                    detail="4 sessions • ${slots.last().session.label}",endSession=slots.last().session)
        }
        // Friday power is a named group; existing group definition includes
        // both directions. Output list is literal. Start next Monday only.
        rows.filter{it.date==mon.minusDays(3) && it.digit in power}.forEach{origin->
            scopedBorn("s5-fri","သောကြာပါဝါမွေးမူ",
                listOf("09","13","26","47","58"),origin,weekEnd,starts=mon)
        }
        val mondayAm=dayRow(mon,DrawSession.MORNING)
        val mondayPm=dayRow(mon,DrawSession.EVENING)
        if(mondayAm!=null) {
            mondayBk[bk(mondayAm.digit)].orEmpty().forEach{b->
                scopedBorn("s5-mon-$b","တနင်္လာမနက် $b ဘရိတ်မွေးမူ",
                    breaks(b.digitToInt()),mondayAm,weekEnd)
            }
        }
        if(mondayAm!=null && mondayPm!=null) {
            val ones=(mondayAm.digit+mondayPm.digit).count{it=='1'}
            if(ones==2||ones==3)
                scopedBorn("s5-mon-ones","တနင်္လာ 1 နှစ်လုံး/သုံးလုံးမူ",
                    breaks(9),mondayPm,weekEnd)
        }
        if(mondayAm!=null && mondayAm.digit[0]=='7')
            scopedBorn("s8-mon-seven","တနင်္လာမနက် 7 ထိပ်မူ",
                followDigits(3),mondayAm,weekEnd)
        // Dynamic exact repeat watch: compare each observed result only with
        // earlier same-week results, not with a later enlarged candidate set.
        fun exactRepeatAfter(origin:HistoricDraw):Boolean {
            val seen=mutableSetOf<String>()
            for(row in weekRows) {
                if(before(origin,identity(row)) && row.digit in seen)return true
                seen.add(row.digit)
            }
            return false
        }
        // Approved 21–30: same-week born, literal outputs except marked R.
        val weeklyDirect6=mapOf("09" to listOf("68"),"14" to listOf("17"),
            "17" to listOf("14"),"23" to listOf("79"),"27" to listOf("47","37"),
            "49" to listOf("60"),"54" to listOf("04"),"59" to listOf("47","79"),
            "64" to listOf("65"),"65" to listOf("27","64"),"69" to listOf("09"),
            "89" to listOf("49","76"),"92" to listOf("90"))
        for(origin in weekRows) {
            val x=origin.digit
            weeklyDirect6[x]?.let{pairs->
                scopedBorn("s6-$x","$x နောက် ${pairs.joinToString("/")} မွေးမူ",
                    pairs,origin,weekEnd)
            }
            if(x=="43"||x=="34")
                scopedBorn("s6-43","43R နောက် 89 မွေးမူ",listOf("89"),origin,weekEnd)
            // 31–40: the 78 trigger is literal, R marks the whole output group.
            if(x=="78")
                scopedBorn("s6-78","78 နောက် ပါဝါပဒေသာမွေးမူ",
                    withReverse(listOf("72","50","61","83","13","41","85","63")),
                    origin,weekEnd)
            if(x=="79"||x=="97")
                scopedBorn("s6-79","79R နောက် 47/29 မွေးမူ",listOf("47","29"),origin,weekEnd)
            if(x=="83"||x=="38")
                scopedBorn("s6-83","83R နောက် 43 မွေးမူ",listOf("43"),origin,weekEnd)
            if(x=="84"||x=="48")
                scopedBorn("s6-84","84R နောက် 13/10 မွေးမူ",listOf("13","10"),origin,weekEnd)
            if(x=="12"||x=="21") {
                scopedBorn("s6-12-digit","12R နောက် 1 ပတ်သီးမူ",
                    followDigits(1),origin,weekEnd)
                scopedBorn("s6-12","12R နောက် 48R မွေးမူ",
                    listOf("48","84"),origin,weekEnd)
                scopedBorn("s6-12-extra","12R နောက် 80/35R မွေးမူ",
                    withReverse(listOf("80","35")),origin,weekEnd)
                scopedBorn("s6-12-wheel","12R နောက် 7894 အခွေမူ",
                    CherryPairExpander.wheel("7894"),origin,weekEnd)
            }
            if(x=="27"||x=="72")
                scopedBorn("s6-27-extra","27R နောက် 0 ထိပ် / 8 ဘရိတ်မူ",
                    CherryPairExpander.top(0)+breaks(8),origin,weekEnd)
            if(x=="59" && !exactRepeatAfter(origin))
                scopedBorn("s6-59-repeat","59 နောက် ပြန်ကျမူ",
                    thisWeek.toList(),origin,weekEnd,stopOnHit=false)
        }
        // Plain five calendar days, including closures/weekends. The reader
        // lists five literal pairs, not the entire 5-break group.
        rows.filter{it.date>=target.date.minusDays(5)&&it.digit=="00"}.forEach{origin->
            scopedBorn("s8-00-bk","00 နောက် 5 ဘရိတ်မွေးမူ",
                listOf("05","14","23","69","78"),origin,origin.date.plusDays(5),
                detail="5 ရက် • အထူး 05/69")
        }
        for(origin in listOfNotNull(mondayAm,mondayPm)) {
            val x=origin.digit
            if('9' in x)scopedBorn("s5-mon-nine","တနင်္လာ 9 အပူးမွေးမူ",
                doubles,origin,weekEnd)
            if('3' in x && !exactRepeatAfter(origin))
                scopedBorn("s5-mon-three","တနင်္လာ 3 ပြန်ကျမူ",thisWeek.toList(),
                    origin,weekEnd,stopOnHit=false)
            if('5' in x) {
                scopedBorn("s5-mon-five-break","တနင်္လာ 5 ဘူ / ဆယ်ပြည့်မူ",
                    breaks(0),origin,weekEnd)
                scopedBorn("s5-mon-five-digit","တနင်္လာ 5 တစ်လုံးမူ",
                    followDigits(5),origin,weekEnd,starts=mon.plusDays(1))
            }
        }
        // Previous week's exact Thursday/Friday morning input slots.
        val th=dayRow(mon.minusDays(4),DrawSession.MORNING)
        val fr=dayRow(mon.minusDays(3),DrawSession.MORNING)
        if(th!=null && fr!=null && "${th.digit[0]}${fr.digit[0]}" in power)
            scopedBorn("s8-thufri-power","ကြာသပတေး/သောကြာ ပါဝါတွဲမွေးမူ",
                withReverse(listOf("26","29","28","48","46","49")),fr,weekEnd,starts=mon)
        for(origin in weekRows) {
            val x=origin.digit
            // Approved 41/42/48: weekly Born only; independent criteria.
            if(x=="80"||x=="08")scopedBorn("s8-80","80 R → 1264 အပူးခွေ",
                CherryPairExpander.wheel("1264",true),origin,weekEnd)
            if(x=="67"||x=="76")scopedBorn("s8-67","67 R → 8123 အပူးခွေ",
                CherryPairExpander.wheel("8123",true),origin,weekEnd)
            if(x in mobGroup) {
                scopedBorn("new-mob-double","လူမိုက်ကြီးအပူးမူ",
                    doubles,origin,weekEnd)
                scopedBorn("new-mob-wheel","လူမိုက်အခွေမူ",
                    CherryPairExpander.wheel("1267"),origin,weekEnd)
            }
            if(origin.session==DrawSession.EVENING && x[1]=='7')
                scopedBorn("new-bk-evening7","ညနေ 7 ပိတ်မူ",
                    followDigits(5,0)+breaks(3),origin,weekEnd)
            // Weekday branches get independent stable IDs and lifetimes.
            // Source 29's unresolved equal-tail branch is intentionally absent.
            when(origin.date.dayOfWeek) {
                DayOfWeek.MONDAY->{
                    if(bk(x)==4)scopedBorn("new-day-mon-b4","တနင်္လာ 4 ဘရိတ်မူ",
                        listOf("27","45"),origin,weekEnd)
                    if(x[1]=='6')scopedBorn("new-day-mon-t6","တနင်္လာ 6 ပိတ်မူ",
                        withReverse(listOf("09","27","35","49","56","89")),origin,weekEnd)
                    if(x=="79"||x=="97")scopedBorn("new-day-mon-79","တနင်္လာ 79R မူ",
                        withReverse(listOf("12","15","17","29","36","69")),origin,weekEnd)
                    if(x=="89"||x=="98")scopedBorn("new-day-mon-89","တနင်္လာ 89R မူ",
                        breaks(2,7,4,9),origin,weekEnd)
                }
                DayOfWeek.TUESDAY->{
                    if(bk(x)==3)scopedBorn("new-day-tue-b3","အင်္ဂါ 3 ဘရိတ်မူ",
                        breaks(5,9),origin,weekEnd)
                    if(x=="56"||x=="65")scopedBorn("new-day-tue-56","အင်္ဂါ 56R မူ",
                        withReverse(listOf("13")),origin,weekEnd)
                    if(x[0]=='5')scopedBorn("new-day-tue-h5","အင်္ဂါ 5 ထိပ်မူ",
                        withReverse(listOf("63")),origin,weekEnd)
                    if(bk(x)==0)scopedBorn("new-day-tue-b0","အင်္ဂါ 0 ဘရိတ်မူ",
                        breaks(0),origin,weekEnd)
                    if(bk(x)==4)scopedBorn("new-day-tue-b4","အင်္ဂါ 4 ဘရိတ်မူ",
                        breaks(8),origin,weekEnd)
                }
                DayOfWeek.WEDNESDAY->{
                    if(bk(x)==9)scopedBorn("new-day-wed-b9","ဗုဒ္ဓဟူး 9 ဘရိတ်မူ",
                        CherryPairExpander.wheel("1456"),origin,weekEnd)
                    if(bk(x)==5)scopedBorn("new-day-wed-b5","ဗုဒ္ဓဟူး 5 ဘရိတ်မူ",
                        breaks(6,7,8),origin,weekEnd)
                    if(x[1]=='6')scopedBorn("new-day-wed-t6","ဗုဒ္ဓဟူး 6 ပိတ်မူ",
                        CherryPairExpander.top(8)+breaks(8),origin,weekEnd)
                    // User excludes the 5-three-occurrences branch.
                }
                else->Unit
            }
        }
        // 49: the two-calendar-date window starts tomorrow. Results on the
        // origin date (including PM) are outside the eligible stop-on-hit window.
        rows.filter{it.date>=target.date.minusDays(2)&&it.date<target.date&&
            it.session==DrawSession.MORNING&&it.digit=="75"}.forEach{origin->
            scopedBorn("new-born-75","75 နောက် 45789 အခွေ",
                CherryPairExpander.wheel("45789"),origin,origin.date.plusDays(2),
                starts=origin.date.plusDays(1),detail="နောက် 2 ရက်")
        }
        // Weekly watches begin Monday; each criterion independently retires
        // after the first observed matching result, like existing born rules.
        if(DrawSchedule.isWorkingDate(target.date,closed) && target.date<=weekEnd) {
            fun weeklyWatch(code:String,name:String,pairs:List<String>) {
                if(pairs.none{it in thisWeek})add(born,code,code,name,
                    "$mon – $weekEnd • အပတ်စဉ် စောင့်ကြည့်မူ",pairs,null,CherryTab.BORN)
            }
            weeklyWatch("new-groups-weekly-nine","အပတ်စဉ် 9 လုံးမူ",followDigits(9))
            weeklyWatch("new-groups-weekly-break","အပတ်စဉ် 1 ဘရိတ်မူ",breaks(1))
            weeklyWatch("new-groups-weekly-wheel","အပတ်စဉ် 4679 အခွေမူ",
                CherryPairExpander.wheel("4679"))
        }
        // Legacy DOC/Gemini Math generation is retired. The V3 discovery
        // engine is a separate, explicit-tap workflow and never executes here.
        fun group(e:List<CherryEvidence>):List<CherryPick> =
            e.distinctBy{Triple(it.id,it.pair,it.origin)}.groupBy{it.pair}
                .map{(p,ev)->
                    val familyCount=ev.map{it.family}.distinct().size
                    val sourceCount=ev.map{it.tab}.distinct().size
                    val specific=ev.filter{it.id.startsWith("cross-")||
                        it.id.startsWith("gold-")||it.id.startsWith("s6-")||
                        it.id.startsWith("s4-")}.map{it.family}.distinct().size
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