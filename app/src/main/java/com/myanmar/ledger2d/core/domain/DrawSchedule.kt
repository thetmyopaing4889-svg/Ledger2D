package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.DayOfWeek

/** Selects the next available 2D draw using the device's local date and time. */
object DrawSchedule {
    val morningResultTime: LocalTime = LocalTime.NOON
    val eveningResultTime: LocalTime = LocalTime.of(16, 30)
    fun isWeekday(date: java.time.LocalDate): Boolean = date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    fun isSessionOpenForToday(session: DrawSession, now: LocalDateTime): Boolean = when(session) {
        DrawSession.MORNING -> now.toLocalTime().isBefore(morningResultTime)
        DrawSession.EVENING -> now.toLocalTime().isBefore(eveningResultTime)
    }
    fun nextDraw(now: LocalDateTime): DrawIdentity {
        var date = now.toLocalDate()
        if (!isWeekday(date)) {
            do { date = date.plusDays(1) } while (!isWeekday(date))
            return DrawIdentity(date, DrawSession.MORNING)
        }
        return when {
            now.toLocalTime().isBefore(morningResultTime) -> DrawIdentity(date, DrawSession.MORNING)
            now.toLocalTime().isBefore(eveningResultTime) -> DrawIdentity(date, DrawSession.EVENING)
            else -> {
                do { date = date.plusDays(1) } while (!isWeekday(date))
                DrawIdentity(date, DrawSession.MORNING)
            }
        }
    }
}
