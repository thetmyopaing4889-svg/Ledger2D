package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity

/** Prospective, uncut offered-pair lifetime evidence. Never reconstruct forecasts. */
internal object CherryBornLifetime {
    enum class Status { HIT, MISS, UNRESOLVED }
    data class Episode(val id:String,val status:Status,val recorded:Int,val expected:Int)

    fun evaluate(draws:List<CherrySuccessDraw>):List<Episode> {
        val records=draws.flatMap{draw->
            draw.checks.filter{it.rule.tab==CherryTab.BORN &&
                it.rule.criterion==CherrySuccessCriterion.PAIR &&
                it.rule.id.contains('@') &&
                CherrySuccessEngine.isCurrentRuleEvidence(draw,it) &&
                it.rule.eligibleSlots.isNotEmpty() &&
                draw.snapshot.target in it.rule.eligibleSlots}
                .map{draw.snapshot.target to it}
        }.groupBy{it.second.rule.id}
        return records.map{(id,items)->
            val schedules=items.map{it.second.rule.eligibleSlots.toSet()}.distinct()
            // A changed holiday calendar or inconsistent schedules cannot
            // establish a complete miss. A recorded eligible hit is still proof.
            val expected=schedules.flatten().toSet()
            val recorded=items.map{it.first}.toSet()
            val status=when {
                items.any{it.second.correct}->Status.HIT
                schedules.size==1 && recorded.containsAll(expected)->Status.MISS
                else->Status.UNRESOLVED
            }
            Episode(id,status,recorded.size,expected.size)
        }
    }
}
