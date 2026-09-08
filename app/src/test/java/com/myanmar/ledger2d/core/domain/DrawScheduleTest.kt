package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DrawScheduleTest {
    private val date = LocalDate.of(2026, 9, 7)

    @Test fun before_noon_defaults_to_same_day_morning() {
        assertEquals(date, DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(11, 59))).date)
        assertEquals(DrawSession.MORNING, DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(11, 59))).session)
    }

    @Test fun after_morning_before_evening_defaults_to_same_day_evening() {
        val draw = DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(16, 29)))
        assertEquals(date, draw.date)
        assertEquals(DrawSession.EVENING, draw.session)
    }

    @Test fun after_evening_defaults_to_next_day_morning() {
        val draw = DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(18, 0)))
        assertEquals(date.plusDays(1), draw.date)
        assertEquals(DrawSession.MORNING, draw.session)
    }

    @Test fun exact_result_times_are_already_past() {
        assertEquals(DrawSession.EVENING, DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.NOON)).session)
        assertEquals(date.plusDays(1), DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(16, 30))).date)
    }
}
