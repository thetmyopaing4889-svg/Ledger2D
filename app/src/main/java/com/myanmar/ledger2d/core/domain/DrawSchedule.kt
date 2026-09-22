package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Selects draw times and fresh working contexts using the device's local date and time. */
object DrawSchedule {
    val morningResultTime: LocalTime = LocalTime.NOON
    val eveningResultTime: LocalTime = LocalTime.of(16, 30)
    private val workingMorningCutoff: LocalTime = LocalTime.of(12, 1)

    fun isWeekday(date: LocalDate): Boolean =
        date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun isWorkingDate(date: LocalDate, closedDays: Set<LocalDate>): Boolean =
        isWeekday(date) && date !in closedDays

    /**
     * Calculates the default context for a newly created application session.
     * Manual selections are deliberately not represented here; callers invoke this only once.
     */
    fun defaultWorkingContext(now: LocalDateTime, closedDays: Set<LocalDate>): DrawIdentity {
        val today = now.toLocalDate()
        if (!isWorkingDate(today, closedDays)) {
            return DrawIdentity(nextWorkingDate(today.plusDays(1), closedDays), DrawSession.MORNING)
        }
        return when {
            now.toLocalTime().isBefore(workingMorningCutoff) -> DrawIdentity(today, DrawSession.MORNING)
            now.toLocalTime().isBefore(eveningResultTime) -> DrawIdentity(today, DrawSession.EVENING)
            else -> DrawIdentity(nextWorkingDate(today.plusDays(1), closedDays), DrawSession.MORNING)
        }
    }

    private fun nextWorkingDate(start: LocalDate, closedDays: Set<LocalDate>): LocalDate {
        var candidate = start
        while (!isWorkingDate(candidate, closedDays)) candidate = candidate.plusDays(1)
        return candidate
    }

    fun isSessionOpenForToday(session: DrawSession, now: LocalDateTime): Boolean = when (session) {
        DrawSession.MORNING -> now.toLocalTime().isBefore(morningResultTime)
        DrawSession.EVENING -> now.toLocalTime().isBefore(eveningResultTime)
    }

    fun nextDraw(now: LocalDateTime): DrawIdentity {
        var date = now.toLocalDate()
        if (!isWeekday(date)) {
            do {
                date = date.plusDays(1)
            } while (!isWeekday(date))
            return DrawIdentity(date, DrawSession.MORNING)
        }
        return when {
            now.toLocalTime().isBefore(morningResultTime) -> DrawIdentity(date, DrawSession.MORNING)
            now.toLocalTime().isBefore(eveningResultTime) -> DrawIdentity(date, DrawSession.EVENING)
            else -> {
                do {
                    date = date.plusDays(1)
                } while (!isWeekday(date))
                DrawIdentity(date, DrawSession.MORNING)
            }
        }
    }
}
