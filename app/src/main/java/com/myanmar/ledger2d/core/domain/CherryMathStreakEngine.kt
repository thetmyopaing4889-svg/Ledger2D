package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import kotlin.math.abs

data class CherryMathCheck(
    val date:LocalDate,val session:DrawSession,val actual:String,
    val predicted:Int?,val hit:Boolean?
)
data class CherryMathFormula(
    val id:String,val name:String,val kind:String,val result:Int?,
    val streak:Int,val checked:Int,val hits:Int,
    val checks:List<CherryMathCheck>,val source:String="DOC",
    val explanation:String=""
)
data class CherryMathWatch(
    val id:String,val title:String,val description:String,
    val pairs:List<String>,val section:Int
)
data class CherryMathAnalysis(
    val formulas:List<CherryMathFormula>,
    val champions:Map<String,String>,
    val previousChampions:Map<String,String>,
    val research:List<CherryMathFormula>,
    val confluence:List<Int>,
    val watches:List<CherryMathWatch>,
    val testedDraws:Int
){
    fun champion(kind:String):CherryMathFormula?=
        formulas.firstOrNull{it.id==champions[kind]}
    fun byId(id:String)=formulas.firstOrNull{it.id==id}
}

/**
 * Pure as-of historical replay. Each historical formula is evaluated ONLY from
 * results preceding the draw being checked. A missing input is not a hit.
 * "WAM hit" means the drawn pair includes either digit of a power pair.
 * "LONE hit" means the drawn pair contains the single exact digit.
 * "BREAK hit" means the two drawn digits sum to that break modulo ten.
 * These are evaluation definitions, not measured forecast reliability.
 */
object CherryMathStreakEngine {
    private val known=listOf(
        Triple("w1","ဝမ်းချိန်း (၁)","WAM"),
        Triple("w2","ဝမ်းချိန်း (၂)","WAM"),
        Triple("l1","လုံးပိုင် (၁)","LONE"),
        Triple("l2","လုံးပိုင် (၂)","LONE"),
        Triple("wl","WaiLin လုံးပိုင်","LONE"),
        Triple("b1","ဘရိတ် (၁)","BREAK"),
        Triple("b2","ဘရိတ် (၂)","BREAK"),
        Triple("cycle","၃ လျှော့ ဘရိတ်","BREAK")
    )
    private fun mod(n:Int)=Math.floorMod(n,10)
    private fun before(r:HistoricDraw,t:DrawIdentity)=
        r.date<t.date||(r.date==t.date&&r.session.ordinal<t.session.ordinal)
    private fun immediatelyBefore(t:DrawIdentity,closed:Set<LocalDate>):DrawIdentity {
        if(t.session==DrawSession.EVENING)return DrawIdentity(t.date,DrawSession.MORNING)
        var d=t.date.minusDays(1)
        while(!DrawSchedule.isWorkingDate(d,closed))d=d.minusDays(1)
        return DrawIdentity(d,DrawSession.EVENING)
    }
    private fun previous(
        rows:List<HistoricDraw>,target:DrawIdentity,n:Int,closed:Set<LocalDate>
    ):List<HistoricDraw>? {
        val indexed=rows.associateBy{it.date to it.session}
        val result=mutableListOf<HistoricDraw>()
        var t=immediatelyBefore(target,closed)
        repeat(n){
            val found=indexed[t.date to t.session]?:return null
            result+=found
            t=immediatelyBefore(t,closed)
        }
        return result
    }
    fun predict(
        id:String,rows:List<HistoricDraw>,target:DrawIdentity,
        closed:Set<LocalDate> = emptySet()
    ):Int? {
        val two=if(id=="wl"||id=="cycle")null else previous(rows,target,2,closed)
        val old=two?.get(1)?.digit
        val recent=two?.get(0)?.digit
        val three=if(id=="w2"||id=="l2")previous(rows,target,3,closed) else null
        fun top(s:String)=s[0].digitToInt()
        fun tail(s:String)=s[1].digitToInt()
        return when(id){
            "w1"->if(old!=null&&recent!=null)mod(tail(old)*2+tail(recent)+4)else null
            "l1"->recent?.let{mod(tail(it)*2+2)}
            "b1"->if(old!=null&&recent!=null)
                mod(top(old)*2+top(recent)+tail(recent)*3)else null
            "b2"->old?.let{mod((top(it)+abs(top(it)-tail(it)))*4+1)}
            "w2"->three?.let{
                val a=it[2].digit;val b=it[1].digit;val c=it[0].digit
                mod((tail(a)+tail(b))*2+(top(b)+tail(c))*3+2)
            }
            "l2"->three?.let{
                val a=it[2].digit;val b=it[1].digit;val c=it[0].digit
                mod(tail(a)*2+top(b)*4+top(c)*2+tail(c)*3+1)
            }
            "cycle"->previous(rows,target,1,closed)?.firstOrNull()?.digit?.let{
                mod(top(it)+tail(it)-3)
            }
            "wl"->{
                val date=rows.filter{it.date<target.date}.maxOfOrNull{it.date}
                val am=rows.firstOrNull{it.date==date&&it.session==DrawSession.MORNING}
                val pm=rows.firstOrNull{it.date==date&&it.session==DrawSession.EVENING}
                if(am!=null&&pm!=null)mod(top(am.digit)+top(pm.digit)+tail(pm.digit))else null
            }
            else->null
        }
    }
    private fun hit(kind:String,d:Int,actual:String):Boolean=when(kind){
        "WAM"->actual.any{it.digitToInt()==d||it.digitToInt()==mod(d+5)}
        "LONE"->actual.any{it.digitToInt()==d}
        "BREAK"->mod(actual[0].digitToInt()+actual[1].digitToInt())==d
        else->false
    }
    private fun evaluate(
        name:String,id:String,kind:String,rows:List<HistoricDraw>,target:DrawIdentity,
        closed:Set<LocalDate>,source:String="DOC",
        calculator:(List<HistoricDraw>,DrawIdentity)->Int?
    ):CherryMathFormula {
        val relevant=rows.filter{before(it,target)}.takeLast(20)
        val checks=relevant.map{actual->
            val t=DrawIdentity(actual.date,actual.session)
            val earlier=rows.filter{before(it,t)}
            val digit=calculator(earlier,t)
            CherryMathCheck(actual.date,actual.session,actual.digit,digit,
                digit?.let{hit(kind,it,actual.digit)})
        }
        var streak=0
        for(c in checks.asReversed()){if(c.hit!=true)break;streak++}
        return CherryMathFormula(id,name,kind,calculator(rows,target),streak,
            checks.count{it.hit!=null},checks.count{it.hit==true},checks,source)
    }
    private fun select(items:List<CherryMathFormula>):Map<String,String> =
        listOf("WAM","LONE","BREAK").mapNotNull{kind->
            items.filter{it.kind==kind&&it.result!=null&&it.streak>=3}
                .sortedWith(compareByDescending<CherryMathFormula>{it.streak}
                    .thenByDescending{it.hits}.thenBy{it.id})
                .firstOrNull()?.let{kind to it.id}
        }.toMap()
    private fun research(
        type:String,a:Int,b:Int,k:Int,rows:List<HistoricDraw>,
        target:DrawIdentity,closed:Set<LocalDate>
    ):Int?{
        val last=previous(rows,target,1,closed)?.firstOrNull()?.digit?:return null
        val t=last[0].digitToInt();val u=last[1].digitToInt()
        return mod(when(type){
            "sum"->a*t+b*u+k
            "nine"->a*(9-t)+b*(9-u)+k
            "diff"->a*abs(t-u)+k
            else->return null
        })
    }
    private fun watches(rows:List<HistoricDraw>,target:DrawIdentity):List<CherryMathWatch>{
        val result=mutableListOf<CherryMathWatch>()
        val last=rows.lastOrNull()
        if(last?.digit=="44"&&last.session==DrawSession.EVENING&&
            target.session==DrawSession.MORNING)
            result+=CherryMathWatch("m44","44 နောက် မနက်ဝမ်းချိန်း",
                "ညနေ 44 ထွက်ပြီး နောက်မနက် 0–5 ဝမ်းချိန်း စောင့်ကြည့်မူ။",
                listOf("05","50"),7)
        if(last?.digit=="05"&&last.session==DrawSession.MORNING&&
            target.session==DrawSession.EVENING)
            result+=CherryMathWatch("m05","05 နောက် ညနေဝမ်းချိန်း",
                "မနက် 05 ထွက်ပြီး ညနေ 7–2 ဝမ်းချိန်း စောင့်ကြည့်မူ။",
                listOf("52","25","27","72"),7)
        val monday=target.date.minusDays((target.date.dayOfWeek.value-1).toLong())
        val prevMon=monday.minusWeeks(1);val prevFri=prevMon.plusDays(4)
        val m=rows.firstOrNull{it.date==prevMon&&it.session==DrawSession.MORNING}
        val f=rows.firstOrNull{it.date==prevFri&&it.session==DrawSession.EVENING}
        if(m!=null&&f!=null){
            val sum=mod(m.digit[0].digitToInt()+f.digit[0].digitToInt())
            if(sum==0||sum==5)result+=CherryMathWatch(
                "formula17","တနင်္လာ–သောကြာ ပါဝါမူ",
                "ရှေ့အပတ် တနင်္လာမနက် ${m.digit} နဲ့ သောကြာညနေ ${f.digit} တို့ရဲ့ " +
                    "ထိပ်နှစ်လုံးပေါင်းလဒ် နောက်ဆုံးလုံး $sum ဖြစ်လို့ " +
                    "ညီအစ်ကိုနဲ့ အပူးအုပ်စုကို စောင့်ကြည့်တဲ့မူပါ။ " +
                    "မူရင်းမှာ အတွဲအတိအကျ မပေးထားပါ။",emptyList(),9)
        }
        return result
    }
    fun analyze(
        history:List<HistoricDraw>,target:DrawIdentity,
        singles:List<CherryValue>,breaks:List<CherryValue>,
        closed:Set<LocalDate> = emptySet()
    ):CherryMathAnalysis{
        val prior=history.filter{before(it,target)&&it.date !in closed}
            .sortedWith(compareBy<HistoricDraw>{it.date}.thenBy{it.session.ordinal})
            .distinctBy{it.date to it.session}
        val current=(singles+breaks).associateBy{it.id}
        val formulas=known.map{(id,name,kind)->
            val e=evaluate(name,id,kind,prior,target,closed){data,t->
                predict(id,data,t,closed)
            }
            e.copy(result=current[id]?.digit,explanation=current[id]?.formula.orEmpty())
        }
        val champions=select(formulas)
        val previousTarget=prior.lastOrNull()?.let{DrawIdentity(it.date,it.session)}
        val prevChampion=if(previousTarget==null)emptyMap() else {
            val priorBefore=prior.filter{before(it,previousTarget)}
            select(known.map{(id,name,kind)->
                evaluate(name,id,kind,priorBefore,previousTarget,closed){data,t->
                    predict(id,data,t,closed)
                }
            })
        }
        val types=listOf("sum" to "ထိပ်ပိတ်ပေါင်း",
            "nine" to "၉ နှုတ်ဓာတ်ပြောင်း","diff" to "ထိပ်ပိတ်ခြားနားချက်")
        // Experiment ONLY. Coefficients and constants are not in the DOC.
        // 90 sum + 90 nine-transform + 30 difference = 210 candidates.
        val pool=types.flatMap{(type,name)->
            (1..3).flatMap{a->
                (if(type=="diff")listOf(1)else (1..3).toList()).flatMap{b->
                    (0..9).map{k->
                        val label="$name (a=$a, b=$b, k=$k)"
                        val e=evaluate(label,"research-$type-$a-$b-$k",
                            "WAM",prior,target,closed,"RESEARCH"){data,t->
                            research(type,a,b,k,data,t,closed)
                        }
                        e.copy(explanation="Gemini သုတေသနအလေးချိန် a=$a, b=$b, " +
                            "ကိန်းသေ k=$k — မူရင်း DOC မဟုတ်ပါ")
                    }
                }
            }
        }
        val best=types.mapNotNull{(type,_)->
            pool.filter{it.id.startsWith("research-$type-")&&
                it.streak>=3&&it.checked>=10}
                .sortedWith(compareByDescending<CherryMathFormula>{it.streak}
                    .thenByDescending{it.hits}.thenBy{it.id})
                .firstOrNull()
        }
        val confluence=if(best.size==3&&best.map{it.result}.distinct().size==1)
            listOfNotNull(best[0].result) else emptyList()
        return CherryMathAnalysis(
            formulas,champions,prevChampion,
            pool.sortedWith(compareByDescending<CherryMathFormula>{it.streak}
                .thenByDescending{it.hits}.thenBy{it.id}),
            confluence,watches(prior,target),prior.takeLast(20).size
        )
    }
}
