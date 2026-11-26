package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.repository.CherryArchiveDecodeCache
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class CherryPerformanceGuardTest {
    @Test fun confirmationKeyChangesOnlyAtOfficialResultBoundaries() {
        val day=LocalDateTime.of(2026,11,26,0,0)
        assertEquals(LocalTime.MIDNIGHT,CherryWorkClock.resultBoundary(day.withHour(11)))
        assertEquals(LocalTime.MIDNIGHT,CherryWorkClock.resultBoundary(day.withHour(12)))
        assertEquals(LocalTime.of(12,1),CherryWorkClock.resultBoundary(day.withHour(12).withMinute(1)))
        assertEquals(LocalTime.of(12,1),CherryWorkClock.resultBoundary(day.withHour(16).withMinute(29)))
        assertEquals(LocalTime.of(16,30),CherryWorkClock.resultBoundary(day.withHour(16).withMinute(30)))
        assertEquals(LocalTime.of(16,30),CherryWorkClock.resultBoundary(day.withHour(23)))
    }
    @Test fun obsoleteCalculationCannotBeCaptured() {
        val old=CherryWorkResult(listOf("AM","11"),"old")
        assertEquals("old",old.current(listOf("AM","11")))
        assertNull(old.current(listOf("AM","12")))
        assertNull(old.current(listOf("PM","11")))
    }
    @Test fun archiveDecodesOnlyChangedRecordsAndNeverConcealsCorruption() {
        val cache=CherryArchiveDecodeCache<String>()
        var decodes=0
        fun read(rows:Map<String,String>)=cache.read(rows){_,v->
            decodes++
            require(v!="bad")
            v
        }
        assertEquals(listOf("a","b"),read(linkedMapOf("1" to "a","2" to "b")))
        assertEquals(2,decodes)
        assertEquals(listOf("a","b"),read(linkedMapOf("1" to "a","2" to "b")))
        assertEquals(2,decodes)
        assertEquals(listOf("a","c"),read(linkedMapOf("1" to "a","2" to "c")))
        assertEquals(3,decodes)
        assertEquals(listOf("c"),read(mapOf("2" to "c")))
        assertEquals(3,decodes)
        assertTrue(runCatching{read(mapOf("2" to "bad"))}.isFailure)
        assertEquals(listOf("c"),read(mapOf("2" to "c")))
        assertEquals(4,decodes)
    }
}
