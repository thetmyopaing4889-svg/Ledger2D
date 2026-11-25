package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryPartialRulesBatch5Test {
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun d(date:String,session:DrawSession,pair:String)=HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun r(date:String,session:DrawSession,vararg rows:HistoricDraw)=
        CherryBetterDataEngine.calculate(rows.toList(),DrawIdentity(LocalDate.parse(date),session))
    private fun pairs(report:CherryReport,id:String)=
        report.born.filter{p->p.evidence.any{it.id.substringBefore('@')==id}}.map{it.pair}.toSet()
    private fun current(report:CherryReport,id:String)=
        report.current.filter{p->p.evidence.any{it.id==id}}.map{it.pair}.toSet()

    @Test fun reviewedWheelsAreWeeklyBornOnlyAndStopOnHit() {
        for((trigger,wheel) in mapOf("80" to "1264","67" to "8123")) {
            val id="s8-$trigger"
            for(x in listOf(trigger,trigger.reversed())) {
                val a=d("2026-11-23",am,x)
                val expected=CherryPairExpander.wheel(wheel,true).toSet()
                val next=r("2026-11-23",pm,a)
                assertEquals(16,expected.size)
                assertEquals(expected,pairs(next,id))
                assertTrue(current(next,id).isEmpty())
                assertEquals(expected,pairs(r("2026-11-27",pm,a),id))
                assertTrue(pairs(r("2026-11-30",am,a),id).isEmpty())
                assertTrue(pairs(r("2026-11-24",am,a,d("2026-11-23",pm,expected.first())),id).isEmpty())
            }
        }
    }
    @Test fun fiveDurationFreeRulesStayImmediateCurrentOnly() {
        val cases=mapOf(
            "74" to CherryPairExpander.any(8),
            "48" to CherryPairExpander.tail(6),
            "71" to CherryPairExpander.any(2),
            "15" to CherryPairExpander.any(4),
            "13" to CherryPairExpander.bk(3))
        for((trigger,expected) in cases)for(x in listOf(trigger,trigger.reversed())) {
            val id="s8-$trigger"
            val a=d("2026-11-23",am,x)
            val next=r("2026-11-23",pm,a)
            assertEquals(id,expected.toSet(),current(next,id))
            assertTrue(pairs(next,id).isEmpty())
            assertTrue(current(r("2026-11-24",am,a),id).isEmpty())
            assertTrue(current(r("2026-11-23",pm,d("2026-11-23",am,"99")),id).isEmpty())
        }
    }
    @Test fun mobGroupIsLiteralWithIndependentWeeklyCriteria() {
        val doubles=(0..9).map{"$it$it"}.toSet()
        val wheel=CherryPairExpander.wheel("1267").toSet()
        for(x in listOf("17","20","80","56","51")) {
            val a=d("2026-11-23",am,x)
            val next=r("2026-11-23",pm,a)
            assertEquals(doubles,pairs(next,"new-mob-double"))
            assertEquals(wheel,pairs(next,"new-mob-wheel"))
            assertTrue(current(next,"new-mob-double").isEmpty())
            val reverse=r("2026-11-23",pm,d("2026-11-23",am,x.reversed()))
            assertTrue(pairs(reverse,"new-mob-double").isEmpty())
            assertTrue(pairs(reverse,"new-mob-wheel").isEmpty())
            val doubleHit=r("2026-11-24",am,a,d("2026-11-23",pm,"11"))
            assertTrue(pairs(doubleHit,"new-mob-double").isEmpty())
            assertEquals(wheel,pairs(doubleHit,"new-mob-wheel"))
            val wheelHit=r("2026-11-24",am,a,d("2026-11-23",pm,"12"))
            assertEquals(doubles,pairs(wheelHit,"new-mob-double"))
            assertTrue(pairs(wheelHit,"new-mob-wheel").isEmpty())
            assertTrue(pairs(r("2026-11-30",am,a),"new-mob-double").isEmpty())
        }
    }
    @Test fun morning75WindowStartsTomorrowAndIgnoresEarlierHits() {
        val id="new-born-75"
        val a=d("2026-11-23",am,"75")
        val sameDayHit=d("2026-11-23",pm,"45")
        val expected=CherryPairExpander.wheel("45789").toSet()
        assertTrue(pairs(r("2026-11-23",pm,a),id).isEmpty())
        assertEquals(expected,pairs(r("2026-11-24",am,a,sameDayHit),id))
        val last=r("2026-11-25",pm,a,sameDayHit)
        assertEquals(expected,pairs(last,id))
        assertEquals(LocalDate.parse("2026-11-25"),last.activeRules.single{it.id.startsWith("$id@")}.expires)
        assertTrue(pairs(r("2026-11-26",am,a,sameDayHit),id).isEmpty())
        assertTrue(pairs(r("2026-11-24",pm,a,sameDayHit,d("2026-11-24",am,"45")),id).isEmpty())
        assertTrue(pairs(r("2026-11-24",am,d("2026-11-23",am,"57")),id).isEmpty())
        assertTrue(pairs(r("2026-11-24",am,d("2026-11-23",pm,"75")),id).isEmpty())
    }
    @Test fun excludedKhantNeverEmitsAnyActiveBranch() {
        for(x in listOf("98","11","91","40","04")) {
            val next=r("2026-11-23",pm,d("2026-11-23",am,x))
            assertTrue((next.current+next.born).none{p->p.evidence.any{it.id.startsWith("new-special-khant")}})
        }
    }
}
