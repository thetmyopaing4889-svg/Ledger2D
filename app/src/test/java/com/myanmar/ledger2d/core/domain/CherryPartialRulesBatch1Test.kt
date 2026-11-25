package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryPartialRulesBatch1Test {
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun d(date:String,session:DrawSession,pair:String)=
        HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun r(date:String,session:DrawSession,rows:List<HistoricDraw>,
                  modern:String?=null,internet:String?=null,closed:Set<LocalDate> = emptySet())=
        CherryBetterDataEngine.calculate(rows,DrawIdentity(LocalDate.parse(date),session),modern,internet,closed)
    private fun pairs(report:CherryReport,id:String)=
        (report.current+report.born).filter{p->p.evidence.any{it.id.substringBefore('@')==id}}
            .map{it.pair}.toSet()
    private val siblings=setOf("01","12","23","34","45","56","67","78","89","90")
    private val astrology=setOf("07","18","24","35","69")
    private val doubles=(0..9).map{"$it$it"}.toSet()

    @Test fun independent930UsesExactPriorWeekPairsNotInputReversals() {
        val rows=listOf(d("2026-11-23",am,"26"),d("2026-11-20",pm,"72"))
        val report=r("2026-11-25",am,rows,"12","21")
        val expected=CherryPairExpander.any(0).toSet()+doubles+setOf("26")
        assertEquals(expected,pairs(report,"s1-930bk-v2"))
        assertTrue(pairs(report,"s1-930bk").isEmpty())
        assertTrue(pairs(r("2026-11-25",pm,rows,"12","21"),"s1-930bk-v2").isEmpty())
        assertTrue(pairs(r("2026-11-25",am,emptyList(),"12","13"),"s1-930").isNotEmpty())
    }
    @Test fun firstReturnNeedsCompleteAbsentPreviousWeekAndLiteralPairs() {
        val prev=(16..20).flatMap{day->listOf(d("2026-11-$day",am,"12"),d("2026-11-$day",pm,"34"))}
        val first=d("2026-11-23",am,"05")
        assertEquals(setOf("50","60","90"),pairs(r("2026-11-23",pm,prev+first),"s2-return-0"))
        assertTrue(pairs(r("2026-11-23",pm,prev.dropLast(1)+first),"s2-return-0").isEmpty())
        assertTrue(pairs(r("2026-11-24",am,prev+first+d("2026-11-23",pm,"09")),"s2-return-0").isEmpty())
        assertTrue(pairs(r("2026-11-23",pm,prev.dropLast(1)+d("2026-11-20",pm,"01")+first),"s2-return-0").isEmpty())
    }
    @Test fun tailSixFourScheduledSessionsIncludesHolidayAndStopsOnHit() {
        val origin=d("2026-11-23",pm,"36")
        val closed=setOf(LocalDate.parse("2026-11-24"))
        val expected=(CherryPairExpander.any(4)+CherryPairExpander.any(9)).toSet()
        assertEquals(expected,pairs(r("2026-11-26",pm,listOf(origin),closed=closed),"s2-tail6-49"))
        assertTrue(pairs(r("2026-11-27",am,listOf(origin),closed=closed),"s2-tail6-49").isEmpty())
        assertTrue(pairs(r("2026-11-26",am,listOf(origin,d("2026-11-25",am,"14")),closed=closed),"s2-tail6-49").isEmpty())
    }
    @Test fun groupRulesAreWeeklyLiteralAndSeparate() {
        val a=d("2026-11-23",am,"50")
        val report=r("2026-11-25",am,listOf(a))
        assertEquals(siblings,pairs(report,"s3-w-010"))
        assertTrue(pairs(report,"s3-w-015").isEmpty())
        assertTrue(report.current.none{p->p.evidence.any{it.id=="s3-sib"}})
        assertTrue(pairs(r("2026-11-30",am,listOf(a)),"s3-w-010").isEmpty())
        assertTrue(pairs(r("2026-11-25",am,listOf(d("2026-11-23",am,"05"))),"s3-w-010").isEmpty())
        assertTrue(pairs(r("2026-11-25",am,listOf(a,d("2026-11-24",am,"12"))),"s3-w-010").isEmpty())
        assertEquals(siblings,pairs(r("2026-11-25",am,listOf(d("2026-11-23",am,"11"))),"s3-w-015"))
        assertEquals(doubles,pairs(r("2026-11-25",am,listOf(d("2026-11-23",am,"81"))),"s3-w-013"))
        assertEquals(doubles,pairs(r("2026-11-25",am,listOf(d("2026-11-23",am,"10"))),"s3-w-014"))
    }
    @Test fun powerTenSumAndAstrologyOverlapHaveCorrectFamilies() {
        val report=r("2026-11-25",am,listOf(d("2026-11-23",am,"12")))
        assertEquals(setOf("05","16","27","38","49")+(1..9).map{"$it${10-it}"},pairs(report,"s3-w-011"))
        val both=r("2026-11-25",am,listOf(d("2026-11-23",am,"34")))
        assertEquals(astrology,pairs(both,"s3-w-012"))
        assertEquals(astrology,pairs(both,"s3-w-016"))
        val evidence=both.born.flatMap{it.evidence}.filter{it.id.startsWith("s3-w-012@")||it.id.startsWith("s3-w-016@")}
        assertEquals(setOf("s3-w-astro"),evidence.map{it.family}.toSet())
        assertEquals(CherryPairExpander.bk(4).toSet(),pairs(both,"s3-w-017-7"))
        assertEquals(CherryPairExpander.bk(7).toSet(),pairs(r("2026-11-25",am,listOf(d("2026-11-23",am,"90"))),"s3-w-017-9"))
    }
}
