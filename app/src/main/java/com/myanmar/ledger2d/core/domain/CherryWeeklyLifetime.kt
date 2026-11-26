package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/**
 * Weekly observations are not a per-draw hit rate or triggered birth episode.
 * Only pre-cutoff uncut offered matches count. Never reconstruct from history.
 */
internal object CherryWeeklyLifetime {
    enum class Status { COMPLETE, LOWER_TARGET, RECORDED_WINDOW_END, UNRESOLVED }
    data class Episode(
        val id:String,val name:String,val week:LocalDate,val distinctHits:Int,
        val goal:Int,val recorded:Int,val expected:Int,val status:Status,
    )
    private val goals=mapOf(
        "s3-week" to 8, // coverage display, not a claimed whole-source success
        "s3-six" to 3, // two follow-ups satisfy lower target; three ends watch
        "new-groups-weekly-nine" to 1,
        "new-groups-weekly-break" to 1,
        "new-groups-weekly-wheel" to 1,
    )
    private fun monday(target:DrawIdentity)=
        target.date.minusDays((target.date.dayOfWeek.value-1).toLong())

    fun evaluate(draws:List<CherrySuccessDraw>):List<Episode> {
        val groups=draws.flatMap{draw->
            val target=draw.snapshot.target
            val week=monday(target)
            draw.checks.filter{check->
                val r=check.rule
                r.tab==CherryTab.BORN && r.criterion==CherrySuccessCriterion.PAIR &&
                    r.id in goals && CherrySuccessEngine.isCurrentRuleEvidence(draw,check) &&
                    r.eligibleSlots.isNotEmpty() && target in r.eligibleSlots &&
                    r.eligibleSlots.all{monday(it)==week && DrawSchedule.isWeekday(it.date)}
            }.map{target to it}
        }.groupBy{(target,check)->monday(target) to check.rule.id}
        return groups.map{(key,items)->
            val (week,id)=key
            val goal=goals.getValue(id)
            val schedules=items.map{it.second.rule.eligibleSlots.toSet()}.distinct()
            val expected=schedules.flatten().toSet()
            val recorded=items.map{it.first}.toSet()
            val hits=items.filter{it.second.correct}.map{it.second.result}.toSet().size
            val status=when {
                hits>=goal->Status.COMPLETE
                id=="s3-six" && hits>=2->Status.LOWER_TARGET
                schedules.size==1 && recorded.containsAll(expected)->Status.RECORDED_WINDOW_END
                else->Status.UNRESOLVED
            }
            Episode(id,items.first().second.rule.name,week,hits,goal,recorded.size,expected.size,status)
        }.sortedWith(compareByDescending<Episode>{it.week}.thenBy{it.id})
    }
}
