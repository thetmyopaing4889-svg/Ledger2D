package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDateTime
import java.time.LocalTime

/** Selects the next available 2D draw using the device's local date and time. */
object DrawSchedule {
    val morningResultTime: LocalTime = LocalTime.NOON
    val eveningResultTime: LocalTime = LocalTime.of(16, 30)

    fun nextDraw(now: LocalDateTime): DrawIdentity = when {
        now.toLocalTime().isBefore(morningResultTime) -> DrawIdentity(now.toLocalDate(), DrawSession.MORNING)
        now.toLocalTime().isBefore(eveningResultTime) -> DrawIdentity(now.toLocalDate(), DrawSession.EVENING)
        else -> DrawIdentity(now.toLocalDate().plusDays(1), DrawSession.MORNING)
    }
}
