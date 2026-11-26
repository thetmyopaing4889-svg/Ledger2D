package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/**
 * Raw source evidence, separate from existing offered-pair scores.
 * A component is never promoted to a whole-source score. Each component
 * keeps its own unit: draw, birth instance, weekly coverage, or exclusion.
 */
internal object CherryLibrarySourceEvidence {
    enum class Metric { DRAW, BIRTH, WEEK, CUT }
    data class Event(
        val date:LocalDate,val label:String,val detail:String,
    )
    data class Component(
        val id:String,val name:String,val metric:Metric,
        val correct:Int,val evaluated:Int,val unresolved:Int,
        val events:List<Event>,
        // Weekly coverage is not a binary win; only an explicitly met target qualifies.
        val weeklyTargetMet:Boolean=false,
    )
    private val sources=mapOf(
        "cherry-001" to setOf("s1-day-head-tail"),
        "cherry-002" to setOf("s1-day-break"),
        "cherry-003" to setOf("s1-day-diagonal"),
        "cherry-004" to setOf("s1-930"),
        "cherry-005" to setOf("s1-930bk-v2"),
        "cherry-008" to setOf("s2-p-0", "s2-return-0", "s2-h-0-5", "s2-h-0-6", "s2-h-0-7", "s2-h-0-8", "s2-p-1", "s2-return-1", "s2-h-1-6", "s2-h-1-7", "s2-h-1-8", "s2-p-2", "s2-return-2", "s2-h-2-3", "s2-h-2-4", "s2-h-2-5", "s2-h-2-6", "s2-h-2-7", "s2-h-2-8", "s2-p-3", "s2-return-3", "s2-h-3-5", "s2-h-3-6", "s2-h-3-7", "s2-h-3-8", "s2-p-4", "s2-return-4", "s2-h-4-1", "s2-h-4-2", "s2-h-4-3", "s2-h-4-8", "s2-h-4-9", "s2-h-4-0", "s2-p-5", "s2-return-5", "s2-h-5-0", "s2-h-5-1", "s2-h-5-2", "s2-h-5-3", "s2-p-6", "s2-return-6", "s2-h-6-0", "s2-h-6-1", "s2-h-6-2", "s2-h-6-7", "s2-h-6-8", "s2-h-6-9", "s2-p-7", "s2-return-7", "s2-h-7-0", "s2-h-7-1", "s2-h-7-2", "s2-p-8", "s2-return-8", "s2-h-8-1", "s2-h-8-2", "s2-h-8-3", "s2-p-9", "s2-return-9", "s2-h-9-4", "s2-h-9-5", "s2-h-9-6", "s2-f-0", "s2-f-2", "s2-f-3", "s2-f-6", "s2-f-8", "s2-f-9", "s2-tail6-49", "s2-8head", "cut-tail-plus7"),
        "cherry-009" to setOf("s3-ten"),
        "cherry-010" to setOf("s3-w-010"),
        "cherry-011" to setOf("s3-w-011"),
        "cherry-012" to setOf("s3-w-012"),
        "cherry-013" to setOf("s3-w-013"),
        "cherry-014" to setOf("s3-w-014"),
        "cherry-015" to setOf("s3-w-015"),
        "cherry-016" to setOf("s3-w-016"),
        "cherry-017" to setOf("s3-w-017-9", "s3-w-017-7"),
        "cherry-019" to setOf("s3-six"),
        "cherry-020" to setOf("s3-week"),
        "cherry-021#wheel" to setOf("s4-wheel-00-0", "s4-wheel-11-0", "s4-wheel-22-0", "s4-wheel-33-0", "s4-wheel-44-0", "s4-wheel-55-0", "s4-wheel-66-0", "s4-wheel-77-0", "s4-wheel-88-0", "s4-wheel-99-0", "s4-wheel-00-1"),
        "cherry-021#born" to setOf("s4-born-00", "s4-born-11", "s4-born-22", "s4-born-33", "s4-born-44", "s4-born-55", "s4-born-66", "s4-born-77", "s4-born-88", "s4-born-99"),
        "cherry-023" to setOf("s4-mid44"),
        "cherry-024" to setOf("s5-mon-ones"),
        "cherry-025" to setOf("s5-mon-nine"),
        "cherry-026" to setOf("s5-mon-three"),
        "cherry-027" to setOf("s5-mon-five-break", "s5-mon-five-digit"),
        "cherry-028" to setOf("s5-mon-0", "s5-mon-1", "s5-mon-2", "s5-mon-3", "s5-mon-4", "s5-mon-5", "s5-mon-6", "s5-mon-7", "s5-mon-8", "s5-mon-9"),
        "cherry-029" to setOf("s5-wed"),
        "cherry-030" to setOf("s5-fri"),
        "cherry-032" to setOf("s6-01-68", "s6-01-46"),
        "cherry-033" to setOf("s6-04"),
        "cherry-034" to setOf("s6-06"),
        "cherry-035" to setOf("s6-07", "s6-70"),
        "cherry-036" to setOf("s6-09"),
        "cherry-037" to setOf("s6-10"),
        "cherry-038" to setOf("s6-12-digit", "s6-12", "s6-12-extra", "s6-12-wheel"),
        "cherry-039" to setOf("s6-14"),
        "cherry-040" to setOf("s6-17"),
        "cherry-041" to setOf("s6-23"),
        "cherry-042" to setOf("s6-27", "s6-27-extra"),
        "cherry-043" to setOf("s6-43"),
        "cherry-044" to setOf("s6-49"),
        "cherry-045" to setOf("s6-54"),
        "cherry-046" to setOf("s6-57"),
        "cherry-047" to setOf("s6-59", "s6-59-repeat"),
        "cherry-048" to setOf("s6-64"),
        "cherry-049" to setOf("s6-65"),
        "cherry-050" to setOf("s6-69"),
        "cherry-051" to setOf("s6-78"),
        "cherry-052" to setOf("s6-79"),
        "cherry-053" to setOf("s6-83"),
        "cherry-054" to setOf("s6-84"),
        "cherry-055" to setOf("s6-89"),
        "cherry-056" to setOf("s6-92", "s6-am92"),
        "cherry-057" to setOf("s6-94"),
        "cherry-073" to setOf("cut-dead-triples"),
        "cherry-074" to setOf("s8-00-bk"),
        "cherry-076" to setOf("s8-80"),
        "cherry-077" to setOf("s8-67"),
        "cherry-078" to setOf("s8-zero-follow"),
        "cherry-079" to setOf("s8-74"),
        "cherry-080" to setOf("s8-48"),
        "cherry-081" to setOf("s8-71"),
        "cherry-082" to setOf("s8-15"),
        "cherry-083" to setOf("s8-88"),
        "cherry-084" to setOf("s8-13"),
        "cherry-085" to setOf("s8-evening-four"),
        "cherry-086" to setOf("s8-mon-seven"),
        "cherry-087" to setOf("s8-thufri-power"),
        "cherry-089" to setOf("s9-wailin"),
        "new-mob-double" to setOf("new-mob-double"),
        "new-mob-wheel" to setOf("new-mob-wheel"),
        "new-born-75" to setOf("new-born-75"),
        "new-bk-evening7" to setOf("new-bk-evening7"),
        "new-bk-six" to setOf("new-bk-six"),
        "new-day-mon" to setOf("new-day-mon-b4", "new-day-mon-t6", "new-day-mon-79", "new-day-mon-89"),
        "new-day-tue" to setOf("new-day-tue-b3", "new-day-tue-56", "new-day-tue-h5", "new-day-tue-b0", "new-day-tue-b4"),
        "new-day-wed" to setOf("new-day-wed-b9", "new-day-wed-b5", "new-day-wed-t6"),
        "new-bk-19r" to setOf("new-bk-19r"),
        "new-born-50" to setOf("new-born-50"),
        "new-groups-weekly" to setOf("new-groups-weekly-nine", "new-groups-weekly-break", "new-groups-weekly-wheel"),
    )
    private val byId=sources.flatMap{(source,ids)->ids.map{it to source}}.toMap()
    fun sourceKey(id:String):String? = byId[id.substringBefore('@')]
    fun supported(key:String):Boolean=key in sources

    fun scored(snapshots:List<CherrySuccessSnapshot>,history:List<HistoricDraw>,
               allowed:Set<String>):Map<String,List<Component>> {
        val confirmed=CherryVerifiedHistory.results(history)
        return scoredCompleted(CherrySuccessEngine.completed(snapshots,confirmed),allowed)
    }

    fun scoredCompleted(draws:List<CherrySuccessDraw>,allowed:Set<String>):Map<String,List<Component>> {
        val raw=draws.distinctBy{it.snapshot.target}.mapNotNull(CherryCutSuccess::rawView)
        val result=mutableListOf<Component>()
        val records=raw.flatMap{d->d.checks.map{d.snapshot.target to it}}
        val current=records.filter{it.second.rule.tab==CherryTab.CURRENT}
            .groupBy{it.second.rule.id}
        current.forEach{(id,rows)->
            if(sourceKey(id)==null)return@forEach
            val events=rows.groupBy{it.first}.map{(target,checks)->
                val correct=checks.any{it.second.correct}
                Event(target.date,if(correct)"မှန်" else "လွဲ",
                    target.session.label+" • "+checks.first().second.result)
            }.sortedByDescending{it.date}
            result+=Component(id,rows.first().second.rule.name,Metric.DRAW,
                events.count{it.label=="မှန်"},events.size,0,events)
        }
        val birthRecords=records.groupBy{it.second.rule.id}
        val birthNames=birthRecords.mapValues{it.value.first().second.rule.name}
        CherryBornLifetime.evaluate(raw).groupBy{it.id.substringBefore('@')}.forEach{(id,episodes)->
            if(sourceKey(id)==null)return@forEach
            val events=episodes.map{ep->
                val first=birthRecords.getValue(ep.id).first().first
                val origin=runCatching{LocalDate.parse(ep.id.substringAfter('@').take(10))}.getOrDefault(first.date)
                Event(origin,when(ep.status){
                    CherryBornLifetime.Status.HIT->"မှန်"
                    CherryBornLifetime.Status.MISS->"လွဲ"
                    CherryBornLifetime.Status.UNRESOLVED->"မဆုံးဖြတ်ရသေး"
                },ep.id.substringAfter('@')+" • "+ep.recorded+"/"+ep.expected+" ပွဲ")
            }.sortedByDescending{it.date}
            val hits=episodes.count{it.status==CherryBornLifetime.Status.HIT}
            val misses=episodes.count{it.status==CherryBornLifetime.Status.MISS}
            result+=Component(id,birthNames[episodes.first().id]?:id,Metric.BIRTH,
                hits,hits+misses,episodes.size-hits-misses,events)
        }
        CherryWeeklyLifetime.evaluate(raw).groupBy{it.id}.forEach{(id,episodes)->
            if(sourceKey(id)==null)return@forEach
            // Weekly coverage remains informational; no percentage or invented loss.
            result+=Component(id,episodes.first().name,Metric.WEEK,0,0,0,
                episodes.map{ep->Event(ep.week,"${ep.distinctHits}/${ep.goal}",
                    "${ep.recorded}/${ep.expected} ပွဲ • "+when(ep.status){
                        CherryWeeklyLifetime.Status.COMPLETE->"သတ်မှတ်ချက်ပြည့်"
                        CherryWeeklyLifetime.Status.LOWER_TARGET->"အနည်းဆုံးပြည့်"
                        CherryWeeklyLifetime.Status.RECORDED_WINDOW_END->"သက်တမ်းမှတ်တမ်းပြည့်"
                        CherryWeeklyLifetime.Status.UNRESOLVED->"မဆုံးဖြတ်ရသေး"
                    })},
                weeklyTargetMet=id!="s3-week" && episodes.any{
                    it.status==CherryWeeklyLifetime.Status.COMPLETE ||
                        (id=="s3-six" && it.status==CherryWeeklyLifetime.Status.LOWER_TARGET)
                })
        }
        CherryCutSuccess.scores(draws).forEach{score->
            if(sourceKey(score.id)==null)return@forEach
            result+=Component(score.id,score.name,Metric.CUT,score.correct,score.evaluated,0,
                score.events.map{Event(it.target.date,if(it.correct)"မှန်" else "လွဲ",
                    it.target.session.label+" • "+it.result+" • "+it.rule.excluded.joinToString("/"))})
        }
        return result.groupBy{sourceKey(it.id)!!}
            .filterKeys{it.substringBefore('#') in allowed}
            .mapValues{(_,components)->components.sortedWith(compareBy<Component>{it.metric.ordinal}.thenBy{it.id})}
    }
}
