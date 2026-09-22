package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DrawScheduleTest {
    private val monday = LocalDate.of(2026, 9, 7)

    @Test fun before_1201_defaults_to_current_working_day_morning() {
        assertDefault(LocalDateTime.of(monday, LocalTime.of(12, 0)), monday, DrawSession.MORNING)
    }

    @Test fun exactly_1201_defaults_to_current_working_day_evening() {
        assertDefault(LocalDateTime.of(monday, LocalTime.of(12, 1)), monday, DrawSession.EVENING)
    }

    @Test fun after_1201_defaults_to_current_working_day_evening() {
        assertDefault(LocalDateTime.of(monday, LocalTime.of(12, 2)), monday, DrawSession.EVENING)
    }

    @Test fun at_429_defaults_to_current_working_day_evening() {
        assertDefault(LocalDateTime.of(monday, LocalTime.of(16, 29)), monday, DrawSession.EVENING)
    }

    @Test fun exactly_430_defaults_to_next_working_day_morning() {
        assertDefault(LocalDateTime.of(monday, LocalTime.of(16, 30)), monday.plusDays(1), DrawSession.MORNING)
    }

    @Test fun weekends_are_skipped_for_new_working_context() {
        val saturday = LocalDate.of(2026, 9, 12)
        assertDefault(LocalDateTime.of(saturday, LocalTime.of(10, 0)), LocalDate.of(2026, 9, 14), DrawSession.MORNING)
        val sunday = saturday.plusDays(1)
        assertDefault(LocalDateTime.of(sunday, LocalTime.of(15, 0)), LocalDate.of(2026, 9, 14), DrawSession.MORNING)
    }

    @Test fun one_closed_day_is_skipped_for_new_working_context() {
        val friday = LocalDate.of(2026, 9, 11)
        assertDefault(
            LocalDateTime.of(friday, LocalTime.of(16, 30)),
            LocalDate.of(2026, 9, 15),
            DrawSession.MORNING,
            setOf(LocalDate.of(2026, 9, 14)),
        )
    }

    @Test fun consecutive_closed_days_are_all_skipped_for_new_working_context() {
        val friday = LocalDate.of(2026, 9, 11)
        assertDefault(
            LocalDateTime.of(friday, LocalTime.of(16, 30)),
            LocalDate.of(2026, 9, 17),
            DrawSession.MORNING,
            setOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16)),
        )
    }

    @Test fun today_closed_day_skips_even_before_morning_cutoff() {
        assertDefault(
            LocalDateTime.of(monday, LocalTime.of(10, 0)),
            monday.plusDays(1),
            DrawSession.MORNING,
            setOf(monday),
        )
    }

    @Test fun original_draw_acceptance_cutoffs_remain_unchanged() {
        assertEquals(true, DrawSchedule.isSessionOpenForToday(DrawSession.MORNING, LocalDateTime.of(monday, LocalTime.of(11, 59))))
        assertEquals(false, DrawSchedule.isSessionOpenForToday(DrawSession.MORNING, LocalDateTime.of(monday, LocalTime.NOON)))
        assertEquals(true, DrawSchedule.isSessionOpenForToday(DrawSession.EVENING, LocalDateTime.of(monday, LocalTime.of(16, 29))))
        assertEquals(false, DrawSchedule.isSessionOpenForToday(DrawSession.EVENING, LocalDateTime.of(monday, LocalTime.of(16, 30))))
    }

    private fun assertDefault(
        now: LocalDateTime,
        expectedDate: LocalDate,
        expectedSession: DrawSession,
        closedDays: Set<LocalDate> = emptySet(),
    ) {
        val actual = DrawSchedule.defaultWorkingContext(now, closedDays)
        assertEquals(expectedDate, actual.date)
        assertEquals(expectedSession, actual.session)
    }
}
