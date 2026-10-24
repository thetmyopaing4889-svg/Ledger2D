package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class DrawRatePolicyTest {
    @Test fun mid_morning_change_starts_evening(){
        val next=DrawRatePolicy.earliestChange(LocalDateTime.of(2026,10,26,9,30))
        assertEquals(LocalDate.of(2026,10,26),next.date);assertEquals(DrawSession.EVENING,next.session)
    }
    @Test fun mid_afternoon_change_starts_next_working_day(){
        val next=DrawRatePolicy.earliestChange(LocalDateTime.of(2026,10,23,15,0))
        assertEquals(LocalDate.of(2026,10,26),next.date);assertEquals(DrawSession.MORNING,next.session)
    }
    @Test fun rate_key_preserves_session_order(){
        val date=LocalDate.of(2026,10,26)
        assertTrue(DrawRatePolicy.key(date,DrawSession.MORNING)<DrawRatePolicy.key(date,DrawSession.EVENING))
        assertTrue(DrawRatePolicy.key(date,DrawSession.EVENING)<DrawRatePolicy.key(date.plusDays(1),DrawSession.MORNING))
    }
}
