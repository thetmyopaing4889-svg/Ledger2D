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
        val prev2=priorSlots(rows,target,closed,2)
        val prev3=priorSlots(rows,target,closed,3)
        val current=mutableListOf<CherryEvidence>()
        val born=mutableListOf<CherryEvidence>()
        val math=mutableListOf<CherryEvidence>()
        val cut=mutableMapOf<String,MutableList<String>>()
        val births=mutableListOf<CherryBirth>()
        val singles=mutableListOf<CherryValue>()
        val breaks=mutableListOf<CherryValue>()
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
            section6[last]?.let{rule("s6-$last","အပိုင်း (၆) သီးသန့်မွေး",it)}
            if(last=="70")rule("s6-70","70 R နောက် ပါဝါ",power)
            if(last=="80")rule("s8-80","80 R → 1264 အပူးခွေ",
                CherryPairExpander.wheel("1264",true))
            if(last=="67")rule("s8-67","67 R → 8123 အပူးခွေ",
                CherryPairExpander.wheel("8123",true))
            if(last[0]=='8')rule("s2-8head","8 ထိပ် → 01234 အပူးခွေ",
                CherryPairExpander.wheel("01234",true))
            if(last=="48")rule("s8-48","48 R နောက် 6 ပိတ်",CherryPairExpander.tail(6))
            if(last=="13")rule("s8-13","13 R နောက် 3 ဘရိတ်",CherryPairExpander.bk(3))
            if(last=="74")rule("s8-74","74 R နောက် 8 ပတ်သီး",CherryPairExpander.any(8))
            if(last=="71")rule("s8-71","71 R နောက် 2 ပတ်သီး",CherryPairExpander.any(2))
            if(last=="15")rule("s8-15","15 R နောက် 4 ပတ်သီး",CherryPairExpander.any(4))
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
        fun birth(origin:HistoricDraw,code:String,name:String,pairs:List<String>,limit:Int=5) {
            val elapsed=daysAfter(origin.date,target.date,closed)
            if(elapsed>limit || elapsed<0)return
            val values=pairs.filter(::validHistoric2D).toSet()
            if(values.isEmpty())return
            val after=rows.filter{r->before(origin,identity(r))}
            if(after.any{it.digit in values})return
            val key="$code@${origin.date}-${origin.session.name}"
            births+=CherryBirth(key,name,identity(origin),
                plusWorkingDays(origin.date,limit,closed),values)
            add(born,key,code,name,
                "${origin.date} ${origin.session.label}: ${origin.digit} → $name",
                values,identity(origin),CherryTab.BORN)
        }
        rows.takeLast(20).forEach{d->
            val x=d.digit
            if(x[0]==x[1]) {
                wheels[x].orEmpty().forEachIndexed{i,w->
                    birth(d,"s4-wheel-$x-$i","${x} အပူး → ${w} ခွေ",
                        CherryPairExpander.wheel(w))
                }
                birth(d,"s4-born-$x","${x} အပူးမွေးဂဏန်း",doubleBorn[x].orEmpty())
            }
            if(x=="80")birth(d,"s8-80","80 R → 1264 အပူးခွေ",
                CherryPairExpander.wheel("1264",true))
            if(x=="67")birth(d,"s8-67","67 R → 8123 အပူးခွေ",
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
        if(prev2!=null){
            val p=prev2[1].digit
            val c=prev2[0].digit
            val w=(p[1].digitToInt()*2+c[1].digitToInt()+4)%10
            singles+=CherryValue("w1","ဝမ်းချိန်း (၁)",w,
                "(${p[1]} × ၂) + ${c[1]} + ၄","WAM")
            val l=(c[1].digitToInt()*2+2)%10
            singles+=CherryValue("l1","လုံးပိုင် (၁)",l,
                "(${c[1]} × ၂) + ၂","LONE")
            val b1=(p[0].digitToInt()*2+c[0].digitToInt()+c[1].digitToInt()*3)%10
            val b2=((p[0].digitToInt()+abs(p[0].digitToInt()-p[1].digitToInt()))*4+1)%10
            breaks+=CherryValue("b1","ဘရိတ် (၁)",b1,
                "(${p[0]} × ၂) + ${c[0]} + (${c[1]} × ၃)","BREAK")
            breaks+=CherryValue("b2","ဘရိတ် (၂)",b2,
                "(${p[0]} + |${p[0]} − ${p[1]}|) × ၄ + ၁","BREAK")
        }
        if(prev3!=null){
            val a=prev3[2].digit;val b=prev3[1].digit;val c=prev3[0].digit
            val w=((a[1].digitToInt()+b[1].digitToInt())*2+
                (b[0].digitToInt()+c[1].digitToInt())*3+2)%10
            val l=(a[1].digitToInt()*2+b[0].digitToInt()*4+
                c[0].digitToInt()*2+c[1].digitToInt()*3+1)%10
            singles+=CherryValue("w2","ဝမ်းချိန်း (၂)",w,
                "[(${a[1]} + ${b[1]}) × ၂] + [(${b[0]} + ${c[1]}) × ၃] + ၂","WAM")
            singles+=CherryValue("l2","လုံးပိုင် (၂)",l,
                "(${a[1]}×၂) + (${b[0]}×၄) + (${c[0]}×၂) + (${c[1]}×၃) + ၁","LONE")
        }
        val yesterday=rows.lastOrNull{it.date<target.date}?.date
        if(yesterday!=null){
            val am=rows.firstOrNull{it.date==yesterday&&it.session==DrawSession.MORNING}
            val pm=rows.firstOrNull{it.date==yesterday&&it.session==DrawSession.EVENING}
            if(am!=null&&pm!=null){
                val l=(am.digit[0].digitToInt()+pm.digit[0].digitToInt()+
                    pm.digit[1].digitToInt())%10
                singles+=CherryValue("wl","WaiLin လုံးပိုင်",l,
                    "${am.digit[0]} + ${pm.digit[0]} + ${pm.digit[1]}","LONE")
            }
        }
        if(prev!=null){
            val b=Math.floorMod(bk(prev.digit)-3,10)
            breaks+=CherryValue("cycle","၃ လျှော့ ဘရိတ်",b,
                "${bk(prev.digit)} BK → $b BK","BREAK")
        }
        val mathAnalysis=CherryMathStreakEngine.analyze(rows,target,singles,breaks,closed)
        // Keep DOC conditional math watches visible without inventing pairs.
        mathAnalysis.watches.filter{it.pairs.isNotEmpty()}.forEach{watch->
            add(math,"watch-${watch.id}","watch-${watch.id}",watch.title,
                watch.description,watch.pairs,null,CherryTab.MATH)
        }
        singles.filter{it.kind=="WAM"}.forEach{w->
            add(math,"gold-${w.id}","gold-${w.id}","${w.name} ပါဝါတွဲ",
                "${w.formula} = ${w.digit} → ${CherryPairExpander.power(w.digit).joinToString(" / ")}",
                CherryPairExpander.power(w.digit),null,CherryTab.MATH)
        }
        singles.filter{it.kind=="LONE"}.forEach{l->
            breaks.forEach{b->
                add(math,"cross-${l.id}-${b.id}","cross-${l.id}-${b.id}",
                    "${l.name} × ${b.name} (ဒဲ့ဘရိတ်)",
                    "လုံးပိုင် ${l.digit}၊ ဘရိတ် ${b.digit}။ " +
                        "တွဲဖက်လုံး = (${b.digit} − ${l.digit} + 10) mod 10။ " +
                        CherryPairExpander.cross(l.digit,b.digit).joinToString(" / "),
                    CherryPairExpander.cross(l.digit,b.digit),null,CherryTab.MATH)
                if(b.id=="b1"||b.id=="b2"){
                    val powerBreak=(b.digit+5)%10
                    add(math,"cross-${l.id}-${b.id}-power",
                        "cross-${l.id}-${b.id}-power",
                        "${l.name} × ${b.name} (ပါဝါဘရိတ်)",
                        "ဒဲ့ဘရိတ် ${b.digit} + 5 = ${powerBreak} ပါဝါဘရိတ်။ " +
                            "လုံးပိုင် ${l.digit} နှင့် တွဲစပ် → " +
                            CherryPairExpander.cross(l.digit,powerBreak).joinToString(" / "),
                        CherryPairExpander.cross(l.digit,powerBreak),null,CherryTab.MATH)
                }
            }
        }
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
        val mathPicks=group(math)
        val distinct=mutableSetOf<String>()
        val ranked=group(current+born+math).filter{it.cuts.isEmpty()}.filter{p->
            val reverse=p.pair.reversed()
            val key=if(p.pair<reverse)p.pair else reverse
            distinct.add(key)
        }
        val summary=ranked.take(3)
        return CherryReport(target,rows.size,rows.lastOrNull(),currentPicks,bornPicks,
            mathPicks,summary,ranked,births,singles,breaks,cut.mapValues{it.value.distinct()},
            target.session==DrawSession.MORNING&&modern?.matches(Regex("[0-9]{2}"))==true&&
                internet?.matches(Regex("[0-9]{2}"))==true,mathAnalysis)
    }
}