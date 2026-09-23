package com.myanmar.ledger2d.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DeviceCalendarTest {
    @Test fun home_date_uses_actual_current_calendar_date_not_working_date() {
        val zone = ZoneId.of("Asia/Yangon")
        val clock = Clock.fixed(Instant.parse("2026-09-22T18:00:00Z"), zone)

        assertEquals(LocalDate.of(2026, 9, 23), DeviceCalendar.today(clock))
    }
}
