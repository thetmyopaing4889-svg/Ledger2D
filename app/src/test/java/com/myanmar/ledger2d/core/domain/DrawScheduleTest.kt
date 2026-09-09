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

    @Test fun after_morning_defaults_to_same_day_evening_without_invented_cutoff() {
        val draw = DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(16, 29)))
        assertEquals(date, draw.date)
        assertEquals(DrawSession.EVENING, draw.session)
    }

    @Test fun late_evening_still_defaults_to_same_day_evening_until_result_exists() {
        val draw = DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(18, 0)))
        assertEquals(date, draw.date)
        assertEquals(DrawSession.EVENING, draw.session)
    }

    @Test fun noon_selects_evening_without_an_evening_clock_assumption() {
        assertEquals(DrawSession.EVENING, DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.NOON)).session)
        assertEquals(date, DrawSchedule.nextDraw(LocalDateTime.of(date, LocalTime.of(16, 30))).date)
    }
}
