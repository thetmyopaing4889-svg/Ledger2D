package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.DrawSchedule
import java.time.LocalDateTime
import java.time.LocalTime

/** The confirmed-result projection only changes at these two daily boundaries. */
internal object CherryWorkClock {
    fun resultBoundary(now:LocalDateTime):LocalTime=when {
        !now.toLocalTime().isBefore(DrawSchedule.eveningResultTime)->DrawSchedule.eveningResultTime
        !now.toLocalTime().isBefore(DrawSchedule.morningResultTime)->DrawSchedule.morningResultTime
        else->LocalTime.MIDNIGHT
    }
}

/** A result from an obsolete request cannot be saved as the current report. */
internal data class CherryWorkResult<K,V>(val key:K,val value:V) {
    fun current(request:K):V?=value.takeIf{key==request}
}
