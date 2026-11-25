package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CherryPendingDrawTest {
    private val date=LocalDate.of(2026,11,25)
    private val am=DrawIdentity(date,DrawSession.MORNING)
    private val pm=DrawIdentity(date,DrawSession.EVENING)
    private val next=DrawIdentity(date.plusDays(1),DrawSession.MORNING)
    private fun at(h:Int,m:Int)=date.atTime(h,m)

    @Test fun lateMorningKeepsCurrentAndSwitchesOnConfirmedResult() {
        assertEquals(am,CherryPendingDraw.select(at(11,59),emptySet(),emptySet()))
        assertEquals(am,CherryPendingDraw.select(at(12,3),emptySet(),emptySet(),setOf(am)))
        assertEquals(pm,CherryPendingDraw.select(at(12,3),emptySet(),setOf(am),setOf(am)))
        assertFalse(CherrySuccessEngine.beforeCutoff(am,
            at(12,3).atZone(java.time.ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()))
    }

    @Test fun lateEveningAndReopenAfterMidnight() {
        assertEquals(pm,CherryPendingDraw.select(at(16,33),emptySet(),setOf(am),setOf(pm)))
        assertEquals(pm,CherryPendingDraw.select(date.plusDays(1).atTime(0,5),
            emptySet(),setOf(am),setOf(pm)))
        assertEquals(next,CherryPendingDraw.select(at(16,33),emptySet(),setOf(am,pm),setOf(pm)))
        assertEquals(next,CherryPendingDraw.select(at(16,33),emptySet(),setOf(pm),setOf(am)))
    }

    @Test fun closedDayAndWeekendNeverGenerateClosedTarget() {
        val friday=LocalDate.of(2026,11,27)
        val mon=friday.plusDays(3)
        assertEquals(DrawIdentity(mon.plusDays(1),DrawSession.MORNING),
            CherryPendingDraw.select(friday.plusDays(1).atTime(12,3),setOf(mon),emptySet()))
        assertEquals(next,CherryPendingDraw.select(at(12,3),setOf(date),emptySet()))
    }
}
