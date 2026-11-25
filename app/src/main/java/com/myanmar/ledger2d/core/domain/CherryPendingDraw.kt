package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime

/** Display lifecycle only: do not change settlement or archive cutoff. */
internal object CherryPendingDraw {
    fun isDue(draw: DrawIdentity, now: LocalDateTime): Boolean =
        !now.isBefore(draw.date.atTime(if(draw.session==DrawSession.MORNING)
            DrawSchedule.morningResultTime else DrawSchedule.eveningResultTime))

    private fun after(a:DrawIdentity,b:DrawIdentity):Boolean =
        a.date>b.date || (a.date==b.date && a.session.ordinal>b.session.ordinal)

    fun select(now:LocalDateTime,closed:Set<LocalDate>,confirmed:Set<DrawIdentity>,
               archived:Set<DrawIdentity> = emptySet()):DrawIdentity {
        var scheduled=DrawSchedule.nextDraw(now)
        while(!DrawSchedule.isWorkingDate(scheduled.date,closed))
            scheduled=DrawIdentity(scheduled.date.plusDays(1),DrawSession.MORNING)
        val today=now.toLocalDate()
        val morning=DrawIdentity(today,DrawSession.MORNING)
        val evening=DrawIdentity(today,DrawSession.EVENING)
        fun pending(draw:DrawIdentity):Boolean =
            isDue(draw,now) && draw !in confirmed && confirmed.none { after(it,draw) }
        if(DrawSchedule.isWorkingDate(today,closed)) {
            if(pending(morning)) return morning
            if(pending(evening)) return evening
        }
        // On restart after midnight/weekend, keep only the immediately previous
        // working day's recorded pending draw. Never revive ancient missing data.
        var previous=today.minusDays(1)
        while(!DrawSchedule.isWorkingDate(previous,closed)) previous=previous.minusDays(1)
        return archived.filter { it.date==previous && pending(it) }
            .minByOrNull { it.session.ordinal } ?: scheduled
    }
}
