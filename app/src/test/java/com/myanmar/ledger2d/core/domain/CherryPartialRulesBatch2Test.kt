package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryPartialRulesBatch2Test {
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun d(date:String,session:DrawSession,pair:String)=HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun r(date:String,session:DrawSession,rows:List<HistoricDraw>,closed:Set<LocalDate> = emptySet(),modern:String?=null,internet:String?=null)=
        CherryBetterDataEngine.calculate(rows,DrawIdentity(LocalDate.parse(date),session),modern,internet,closed)
    private fun pairs(report:CherryReport,id:String)=
        (report.current+report.born).filter{p->p.evidence.any{it.id.substringBefore('@')==id}}.map{it.pair}.toSet()

    @Test fun sixGroupCountsDistinctFollowUpsAndStopsAtThree() {
        val a=d("2026-11-23",am,"34")
        val b=d("2026-11-23",pm,"38")
        val c=d("2026-11-24",am,"38")
        val e=d("2026-11-24",pm,"39")
        val f=d("2026-11-25",am,"48")
        assertTrue(pairs(r("2026-11-23",am,emptyList()),"s3-six").isEmpty())
        assertEquals(setOf("39","48","49","89"),pairs(r("2026-11-24",pm,listOf(a,b,c)),"s3-six"))
        assertEquals(setOf("48","49","89"),pairs(r("2026-11-25",am,listOf(a,b,c,e)),"s3-six"))
        assertTrue(pairs(r("2026-11-25",pm,listOf(a,b,c,e,f)),"s3-six").isEmpty())
        assertTrue(pairs(r("2026-11-30",am,listOf(a)),"s3-six").isEmpty())
    }
    @Test fun weeklyEightExactPairsResetNextWeek() {
        val a=d("2026-11-23",am,"27")
        val b=d("2026-11-23",pm,"28")
        val expected=setOf("27","82","48","54","65","46","94","92")
        assertEquals(expected-setOf("27"),pairs(r("2026-11-24",am,listOf(a,b)),"s3-week"))
        assertEquals(expected,pairs(r("2026-11-30",am,listOf(a,b)),"s3-week"))
    }
    @Test fun allDoubleTableRowsMatchLiteralAndExplicitReverseExpansion() {
        val expected=mapOf(
            "00" to "77 75 55","11" to "76 64 78 84 70 07 71 17 67",
            "22" to "07 48 64 82 34 79 72 49 42","33" to "81 89 51 59",
            "44" to "83 38 81 18 31 13 88 33 91 93 21 23 27 72 49 94",
            "55" to "17 36 37 35 07 05","66" to "17 14 97 94",
            "77" to "09 02 29 22","88" to "31 39 11 19","99" to "61 63 41 43")
        val wheels=mapOf("00" to listOf("0457","0459"),"11" to listOf("1267"),"22" to listOf("2479"),
            "33" to listOf("1368"),"44" to listOf("3489"),"55" to listOf("1267"),"66" to listOf("2378"),
            "77" to listOf("0358"),"88" to listOf("2479"),"99" to listOf("0156"))
        for((x,values) in expected) {
            val report=r("2026-11-24",am,listOf(d("2026-11-23",pm,x)))
            assertEquals(x,values.split(" ").toSet(),pairs(report,"s4-born-$x"))
            wheels.getValue(x).forEachIndexed{i,w->
                assertEquals(x,CherryPairExpander.wheel(w).toSet(),pairs(report,"s4-wheel-$x-$i"))
            }
        }
        val origin=d("2026-11-23",am,"44")
        val report=r("2026-11-23",pm,listOf(origin))
        assertEquals(setOf("83","38","81","18","31","13","88","33"),pairs(report,"s4-mid44"))
        val ev=report.born.flatMap{it.evidence}.filter{it.id.startsWith("s4-mid44@")||it.id.startsWith("s4-born-44@")}
        assertEquals(setOf("s4-born-44"),ev.map{it.family}.toSet())
        assertTrue(pairs(r("2026-11-30",am,listOf(origin)),"s4-born-44").isEmpty())
    }
    @Test fun mondayBreaksRetireIndependently() {
        val a=d("2026-11-23",am,"42")
        val b=d("2026-11-23",pm,"15")
        val first=r("2026-11-23",pm,listOf(a))
        for(n in listOf(6,9,1))assertEquals(CherryPairExpander.bk(n).toSet(),pairs(first,"s5-mon-$n"))
        val next=r("2026-11-24",am,listOf(a,b))
        assertTrue(pairs(next,"s5-mon-6").isEmpty())
        assertEquals(CherryPairExpander.bk(9).toSet(),pairs(next,"s5-mon-9"))
        assertTrue(pairs(r("2026-11-30",am,listOf(a)),"s5-mon-9").isEmpty())
    }
    @Test fun wednesdayFourSlotsAndFridayNextWeek() {
        val wed=d("2026-11-25",pm,"22")
        assertEquals(CherryPairExpander.bk(7).toSet(),pairs(r("2026-11-27",pm,listOf(wed)),"s5-wed"))
        assertTrue(pairs(r("2026-11-30",am,listOf(wed)),"s5-wed").isEmpty())
        val closed=setOf(LocalDate.parse("2026-11-26"))
        assertTrue(pairs(r("2026-11-30",pm,listOf(wed),closed),"s5-wed").isNotEmpty())
        assertTrue(pairs(r("2026-12-01",am,listOf(wed),closed),"s5-wed").isEmpty())
        val fri=d("2026-11-27",am,"05")
        assertTrue(pairs(r("2026-11-27",pm,listOf(fri)),"s5-fri").isEmpty())
        assertEquals(setOf("09","13","26","47","58"),pairs(r("2026-11-30",am,listOf(fri,d("2026-11-27",pm,"09"))),"s5-fri"))
        assertTrue(pairs(r("2026-12-07",am,listOf(fri)),"s5-fri").isEmpty())
    }
    @Test fun explicit01And07BranchesDoNotReverseImplicitly() {
        fun forInput(x:String)=r("2026-11-24",am,listOf(d("2026-11-23",pm,x)))
        assertEquals(setOf("68"),pairs(forInput("01"),"s6-01-68"))
        assertEquals(setOf("46"),pairs(forInput("01"),"s6-01-46"))
        assertEquals(setOf("46"),pairs(forInput("10"),"s6-01-46"))
        assertTrue(pairs(forInput("10"),"s6-01-68").isEmpty())
        assertEquals(setOf("11"),pairs(forInput("04"),"s6-04"))
        assertTrue(pairs(forInput("40"),"s6-04").isEmpty())
        assertEquals(setOf("49","29"),pairs(forInput("07"),"s6-07"))
        assertTrue(pairs(forInput("07"),"s6-70").isEmpty())
        assertEquals(setOf("05","50","16","61","27","72","38","83","49","94"),pairs(forInput("70"),"s6-70"))
        assertTrue(pairs(forInput("70"),"s6-07").isEmpty())
        assertTrue(pairs(r("2026-11-30",am,listOf(d("2026-11-23",pm,"07"))),"s6-07").isEmpty())
    }
    @Test fun independent930DoesNotCreateStaleExclusions() {
        val report=r("2026-11-25",am,listOf(d("2026-11-23",pm,"80")),modern="12",internet="13")
        assertTrue(report.current.isNotEmpty())
        assertTrue(report.cuts.isEmpty())
    }
}
