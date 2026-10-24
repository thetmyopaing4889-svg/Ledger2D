package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime

/** Changes are activated on draw boundaries, never inside an open session. */
object DrawRatePolicy {
    fun key(date:LocalDate,session:DrawSession):Long =
        Math.addExact(Math.multiplyExact(date.toEpochDay(),2L),if(session==DrawSession.MORNING)0L else 1L)

    fun earliestChange(now:LocalDateTime):DrawIdentity {
        val next=DrawSchedule.nextDraw(now)
        val isOngoing=next.date==now.toLocalDate() && DrawSchedule.isSessionOpenForToday(next.session,now)
        if(!isOngoing)return next
        if(next.session==DrawSession.MORNING)return DrawIdentity(next.date,DrawSession.EVENING)
        return DrawSchedule.nextDraw(now.toLocalDate().atTime(16,31))
    }
}
