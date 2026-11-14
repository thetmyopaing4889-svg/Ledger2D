package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CherryBetterDataEngineTest {
    private fun d(date:String,session:DrawSession,n:String)=
        HistoricDraw(LocalDate.parse(date),session,n,HistoricSource.WINNER)
    private fun target(date:String,session:DrawSession)=
        DrawIdentity(LocalDate.parse(date),session)

    @Test fun fourDigitWheelHasTwelveDirectReversePairs() {
        val w=CherryPairExpander.wheel("1264")
        assertEquals(12,w.size)
        assertTrue(w.containsAll(listOf("12","21","64","46")))
        assertFalse(w.contains("11"))
    }

    @Test fun doubleWheelIncludesAllSixteenIncludingDoubles() {
        val w=CherryPairExpander.wheel("1264",true)
        assertEquals(16,w.size)
        assertTrue(w.containsAll(listOf("11","22","66","44")))
        assertEquals(25,CherryPairExpander.wheel("01234",true).size)
    }

    @Test fun digitLeadingAndBreakExpansionsAreMathematicallyComplete() {
        assertEquals(19,CherryPairExpander.any(3).size)
        assertEquals(10,CherryPairExpander.top(3).size)
        assertEquals(10,CherryPairExpander.bk(7).size)
        assertTrue(CherryPairExpander.bk(7).containsAll(listOf("07","16","25","34","43","52","61","70","89","98")))
        assertEquals(listOf("25","52"),CherryPairExpander.cross(2,7))
        assertEquals(listOf("55"),CherryPairExpander.cross(5,0))
    }

    @Test fun futureDrawsAreNeverReadAsInputs() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"80"),
                d("2026-11-10",DrawSession.MORNING,"99"),
                d("2026-11-10",DrawSession.EVENING,"33")),
            target("2026-11-10",DrawSession.MORNING))
        assertEquals(1,report.observed)
        assertEquals("80",report.last?.digit)
        assertTrue(report.activeRules.any{it.name.contains("1264")})
    }

    @Test fun 80WheelIsExpandedAsActiveBornRule() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"80")),
            target("2026-11-10",DrawSession.MORNING))
        val active=report.activeRules.first{it.name.contains("1264")}
        assertEquals(16,active.produced.size)
        assertTrue(active.produced.containsAll(listOf("11","22","66","44")))
    }

    @Test fun bornRuleIsRemovedAfterObservedMatchingHit() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.MORNING,"80"),
                d("2026-11-09",DrawSession.EVENING,"12")),
            target("2026-11-10",DrawSession.MORNING))
        assertFalse(report.activeRules.any{it.name.contains("1264")})
    }

    @Test fun bornRuleExpiresAfterFiveWorkingDays() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-02",DrawSession.EVENING,"80")),
            target("2026-11-10",DrawSession.MORNING))
        assertFalse(report.activeRules.any{it.name.contains("1264")})
    }

    @Test fun formulaProducesGoldenAndCrossPairs() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.MORNING,"12"),
                d("2026-11-09",DrawSession.EVENING,"34")),
            target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.math.any{it.pair=="27"&&it.evidence.any{ev->ev.id=="gold-w1"}})
        assertTrue(report.math.any{it.pair=="07"&&it.evidence.any{ev->ev.id=="cross-l1-b1"}})
    }

    @Test fun sectionEightCutsAreTaggedNotMistakenForCertainty() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"80")),
            target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.cuts["79"].orEmpty().any{it.contains("အပိုင်း (၈)")})
        assertTrue(report.cuts["97"].orEmpty().any{it.contains("အပိုင်း (၈)")})
        assertTrue(report.cuts["57"].orEmpty().any{it.contains("အပိုင်း (၈)")})
        assertTrue(report.cuts["17"].orEmpty().any{it.contains("+7")})
        assertTrue(report.summary.all{it.cuts.isEmpty()})
        assertTrue(report.summary.size<=3)
    }

    @Test fun 930InputsOnlyActivateForMorning() {
        val data=listOf(
            d("2026-11-09",DrawSession.EVENING,"80"),
            d("2026-11-10",DrawSession.MORNING,"22"))
        val am=CherryBetterDataEngine.calculate(data,target("2026-11-10",DrawSession.MORNING),"23","24")
        val pm=CherryBetterDataEngine.calculate(data,target("2026-11-10",DrawSession.EVENING),"23","24")
        assertTrue(am.has930)
        assertFalse(pm.has930)
        assertTrue(am.current.any{p->p.evidence.any{it.id=="s1-930"}})
        assertFalse(pm.current.any{p->p.evidence.any{it.id=="s1-930"}})
    }

    @Test fun missingImmediateResultDisablesStaleCurrentMath() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-06",DrawSession.MORNING,"80")),
            target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.current.isEmpty())
        assertTrue(report.math.isEmpty())
    }
}
