package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime

/** Payout and commission changes may apply to the whole active morning/evening session. */
object DrawRatePolicy {
    fun key(date:LocalDate,session:DrawSession):Long =
        Math.addExact(Math.multiplyExact(date.toEpochDay(),2L),if(session==DrawSession.MORNING)0L else 1L)

    /** Session ownership follows the reseller's workday, not the last betting cutoff. */
    fun currentSession(now:LocalDateTime):DrawIdentity {
        val today=now.toLocalDate()
        if(!DrawSchedule.isWeekday(today)) {
            var next=today.plusDays(1)
            while(!DrawSchedule.isWeekday(next)) next=next.plusDays(1)
            return DrawIdentity(next,DrawSession.MORNING)
        }
        return DrawIdentity(today, if(now.toLocalTime().isBefore(DrawSchedule.morningResultTime))
            DrawSession.MORNING else DrawSession.EVENING)
    }

    /** Kept as a compatibility alias for screens that still use the old name. */
    fun earliestChange(now:LocalDateTime):DrawIdentity = currentSession(now)

    fun following(date:LocalDate,session:DrawSession):DrawIdentity =
        if(session==DrawSession.MORNING) DrawIdentity(date,DrawSession.EVENING)
        else {
            var next=date.plusDays(1)
            while(!DrawSchedule.isWeekday(next)) next=next.plusDays(1)
            DrawIdentity(next,DrawSession.MORNING)
        }
}
