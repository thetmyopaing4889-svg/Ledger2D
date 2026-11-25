package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryPartialRulesBatch3Test {
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun d(date:String,session:DrawSession,pair:String)=HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun r(date:String,session:DrawSession,vararg rows:HistoricDraw)=
        CherryBetterDataEngine.calculate(rows.toList(),DrawIdentity(LocalDate.parse(date),session))
    private fun pairs(report:CherryReport,id:String)=
        report.born.filter{p->p.evidence.any{it.id.substringBefore('@')==id}}.map{it.pair}.toSet()
    @Test fun literalRulesAreWeeklyBornOnlyAndStopOnHit() {
        val cases=mapOf("09" to "68","14" to "17","17" to "14","23" to "79","49" to "60","54" to "04")
        for((x,y) in cases) {
            val a=d("2026-11-23",am,x)
            val report=r("2026-11-25",am,a)
            assertEquals(x,setOf(y),pairs(report,"s6-$x"))
            assertTrue(report.current.none{p->p.evidence.any{it.id=="s6-$x"}})
            assertTrue(pairs(r("2026-11-25",am,d("2026-11-23",am,x.reversed())),"s6-$x").isEmpty())
            assertTrue(pairs(r("2026-11-25",am,a,d("2026-11-24",am,y)),"s6-$x").isEmpty())
            assertTrue(pairs(r("2026-11-30",am,a),"s6-$x").isEmpty())
        }
    }
    @Test fun twelveReverseBranchesRetireIndependently() {
        for(x in listOf("12","21")) {
            val a=d("2026-11-23",am,x)
            val report=r("2026-11-23",pm,a)
            assertEquals(CherryPairExpander.any(1).toSet(),pairs(report,"s6-12-digit"))
            assertEquals(setOf("48","84"),pairs(report,"s6-12"))
            assertEquals(setOf("80","08","35","53"),pairs(report,"s6-12-extra"))
            assertEquals(CherryPairExpander.wheel("7894").toSet(),pairs(report,"s6-12-wheel"))
            assertTrue(report.current.none{p->p.evidence.any{it.id.startsWith("s6-12")}})
            val after=r("2026-11-24",am,a,d("2026-11-23",pm,"48"))
            assertTrue(pairs(after,"s6-12").isEmpty())
            assertEquals(setOf("80","08","35","53"),pairs(after,"s6-12-extra"))
            assertTrue(pairs(r("2026-11-30",am,a),"s6-12-extra").isEmpty())
        }
    }
    @Test fun twentySevenAndFortyThreeUseCorrectExplicitDirections() {
        val a=r("2026-11-24",am,d("2026-11-23",pm,"27"))
        val b=r("2026-11-24",am,d("2026-11-23",pm,"72"))
        val extra=(CherryPairExpander.top(0)+CherryPairExpander.bk(8)).toSet()
        assertEquals(setOf("47","37"),pairs(a,"s6-27"))
        assertEquals(extra,pairs(a,"s6-27-extra"))
        assertEquals(extra,pairs(b,"s6-27-extra"))
        assertTrue(pairs(b,"s6-27").isEmpty())
        for(x in listOf("43","34"))
            assertEquals(setOf("89"),pairs(r("2026-11-24",am,d("2026-11-23",pm,x)),"s6-43"))
    }
    @Test fun fiftyNineRepeatOnlyUsesChronologicallyKnownExactPairs() {
        val a=d("2026-11-23",am,"26")
        val b=d("2026-11-23",pm,"59")
        val c=d("2026-11-24",am,"62")
        val first=r("2026-11-24",am,a,b,c)
        assertEquals(setOf("26","59"),pairs(first,"s6-59-repeat"))
        assertEquals(setOf("47","79"),pairs(first,"s6-59"))
        assertEquals(setOf("26","59","62"),pairs(r("2026-11-24",pm,a,b,c),"s6-59-repeat"))
        val next=r("2026-11-25",am,a,b,c,d("2026-11-24",pm,"26"))
        assertTrue(pairs(next,"s6-59-repeat").isEmpty())
        assertEquals(setOf("47","79"),pairs(next,"s6-59"))
        assertTrue(pairs(r("2026-11-30",am,a,b),"s6-59-repeat").isEmpty())
    }
}
