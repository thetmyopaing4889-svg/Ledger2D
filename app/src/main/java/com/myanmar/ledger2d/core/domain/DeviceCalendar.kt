package com.myanmar.ledger2d.core.domain

import java.time.Clock
import java.time.LocalDate

/** Supplies the actual device calendar date for Home, independently of the working context. */
object DeviceCalendar {
    fun today(clock: Clock = Clock.systemDefaultZone()): LocalDate = LocalDate.now(clock)
}
