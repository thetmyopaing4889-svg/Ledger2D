package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryPartialRulesBatch4Test {
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun d(date:String,session:DrawSession,pair:String)=HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun r(date:String,session:DrawSession,vararg rows:HistoricDraw)=
        CherryBetterDataEngine.calculate(rows.toList(),DrawIdentity(LocalDate.parse(date),session))
    private fun pairs(report:CherryReport,id:String)=
        report.born.filter{p->p.evidence.any{it.id.substringBefore('@')==id}}.map{it.pair}.toSet()
    private fun current(report:CherryReport,id:String)=
        report.current.filter{p->p.evidence.any{it.id==id}}.map{it.pair}.toSet()

    @Test fun directRulesAreWeeklyBornOnlyAndStopOnHit() {
        val cases=mapOf("64" to setOf("65"),"65" to setOf("27","64"),
            "69" to setOf("09"),"89" to setOf("49","76"),"92" to setOf("90"))
        for((x,ys) in cases) {
            val a=d("2026-11-23",am,x)
            val id="s6-$x"
            val immediate=r("2026-11-23",pm,a)
            assertEquals(x,ys,pairs(immediate,id))
            assertTrue(x,current(immediate,id).isEmpty())
            assertTrue(pairs(r("2026-11-25",am,d("2026-11-23",am,x.reversed())),id).isEmpty())
            assertTrue(pairs(r("2026-11-25",am,a,d("2026-11-24",am,ys.first())),id).isEmpty())
            assertTrue(pairs(r("2026-11-30",am,a),id).isEmpty())
        }
    }
    @Test fun seventyEightExpandsAllEightOutputsButNotTrigger() {
        val a=d("2026-11-23",pm,"78")
        val expected=setOf("72","27","50","05","61","16","83","38",
            "13","31","41","14","85","58","63","36")
        val next=r("2026-11-24",am,a)
        assertEquals(expected,pairs(next,"s6-78"))
        assertTrue(current(next,"s6-78").isEmpty())
        assertTrue(pairs(r("2026-11-24",am,d("2026-11-23",pm,"87")),"s6-78").isEmpty())
        assertTrue(pairs(r("2026-11-24",pm,a,d("2026-11-24",am,"27")),"s6-78").isEmpty())
        assertTrue(pairs(r("2026-11-30",am,a),"s6-78").isEmpty())
    }
    @Test fun explicitReverseTriggersNeverReverseLiteralOutputs() {
        val cases=mapOf("79" to setOf("47","29"),"83" to setOf("43"),"84" to setOf("13","10"))
        for((x,ys) in cases)for(input in listOf(x,x.reversed())) {
            val a=d("2026-11-23",pm,input)
            val next=r("2026-11-24",am,a)
            assertEquals(ys,pairs(next,"s6-$x"))
            assertTrue(current(next,"s6-$x").isEmpty())
            assertTrue(pairs(r("2026-11-30",am,a),"s6-$x").isEmpty())
            assertTrue(pairs(r("2026-11-24",pm,a,d("2026-11-24",am,ys.first())),"s6-$x").isEmpty())
        }
    }
    @Test fun morningNinetyTwoHasIndependentSameDayEveningBreak() {
        val a=d("2026-11-23",am,"92")
        val report=r("2026-11-23",pm,a)
        assertEquals(CherryPairExpander.bk(7).toSet(),current(report,"s6-am92"))
        assertEquals(setOf("90"),pairs(report,"s6-92"))
        assertTrue(report.current.flatMap{it.evidence}.any{it.id=="s6-am92"&&it.detail.contains("43/07")})
        assertTrue(current(r("2026-11-24",am,a),"s6-am92").isEmpty())
        assertTrue(current(r("2026-11-24",pm,a),"s6-am92").isEmpty())
        val evening=d("2026-11-23",pm,"92")
        val next=r("2026-11-24",am,evening)
        assertEquals(setOf("90"),pairs(next,"s6-92"))
        assertTrue(current(next,"s6-am92").isEmpty())
    }
    @Test fun doubleZeroFiveCalendarDaysAndLiteralOutputs() {
        val origin=d("2026-11-20",pm,"00")
        val expected=setOf("05","14","23","69","78")
        val within=r("2026-11-25",pm,origin)
        assertEquals(expected,pairs(within,"s8-00-bk"))
        assertEquals(LocalDate.parse("2026-11-25"),
            within.activeRules.single{it.id.startsWith("s8-00-bk@")}.expires)
        assertTrue(pairs(r("2026-11-26",am,origin),"s8-00-bk").isEmpty())
        assertEquals(expected,pairs(r("2026-11-24",am,origin,d("2026-11-23",am,"50")),"s8-00-bk"))
        assertTrue(pairs(r("2026-11-24",am,origin,d("2026-11-23",am,"05")),"s8-00-bk").isEmpty())
    }
}
