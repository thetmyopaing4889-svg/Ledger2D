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

    @Test fun wheel80IsExpandedAsActiveBornRule() {
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

    @Test fun formerGeminiChampionPairsAreNotIncludedInBetterData() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.MORNING,"12"),
                d("2026-11-09",DrawSession.EVENING,"34")),
            target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.math.isEmpty())
        assertTrue(report.mathAnalysis.research.isEmpty())
        assertTrue(report.mathAnalysis.champions.isEmpty())
        assertTrue(report.summary.none{p->p.evidence.any{it.tab==CherryTab.MATH}})
        assertTrue(report.current.isNotEmpty())
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

    @Test fun inputs930OnlyActivateForMorning() {
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


    @Test fun mobTriggerNeverGuessesUnspecified1267Group() {
        val history=listOf(d("2026-11-09",DrawSession.EVENING,"17"))
        val report=CherryBetterDataEngine.calculate(
            history,target("2026-11-10",DrawSession.MORNING))
        val doublePairs=report.current.filter { p ->
            p.evidence.any { it.id == "new-mob-double" }
        }.map{it.pair}.toSet()
        val wheelPairs=report.current.filter { p ->
            p.evidence.any { it.id == "new-mob-wheel" }
        }.map{it.pair}.toSet()
        assertEquals((0..9).map{"$it$it"}.toSet(),doublePairs)
        assertTrue(wheelPairs.isEmpty())
    }

    @Test fun sixBreakTriggers1649WheelButSevenBreakDoesNot() {
        val yes=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"24")),
            target("2026-11-10",DrawSession.MORNING))
        val pairs=yes.current.filter{p->p.evidence.any{it.id=="new-bk-six"}}
            .map{it.pair}.toSet()
        assertEquals(12,pairs.size)
        assertTrue(pairs.containsAll(listOf("16","61","49","94","14","41")))
        val no=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"25")),
            target("2026-11-10",DrawSession.MORNING))
        assertFalse(no.current.any{p->p.evidence.any{it.id=="new-bk-six"}})
    }

    @Test fun fiftyTriggersThreeEightHeadsWithoutDuplicateReaderRule() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"50")),
            target("2026-11-10",DrawSession.MORNING))
        val pairs=report.current.filter{p->p.evidence.any{it.id=="new-born-50"}}
            .map{it.pair}.toSet()
        assertEquals(20,pairs.size)
        assertTrue(pairs.containsAll(listOf("30","37","39","80","82","89")))
        assertFalse(report.current.any{p->p.evidence.any{
            it.id=="new-born-12-27-50"
        }})
    }

    @Test fun morning75CreatesOnlyTwoDayBornWheel() {
        val input=listOf(d("2026-11-09",DrawSession.MORNING,"75"))
        val next=CherryBetterDataEngine.calculate(input,target("2026-11-10",DrawSession.MORNING))
        val born=next.activeRules.single { it.id.startsWith("new-born-75@") }
        assertEquals(20,born.produced.size)
        assertTrue(born.produced.containsAll(listOf("45","54","47","74")))
        val expired=CherryBetterDataEngine.calculate(input,target("2026-11-12",DrawSession.MORNING))
        assertFalse(expired.activeRules.any{it.id.startsWith("new-born-75@")})
        val sameDay=CherryBetterDataEngine.calculate(input,target("2026-11-09",DrawSession.EVENING))
        assertFalse(sameDay.activeRules.any{it.id.startsWith("new-born-75@")})
    }

    @Test fun newSourceTwoCalendarDaysAndReviewedWeeklyRulesExpireAcrossWeekend() {
        val source75=listOf(d("2026-11-19",DrawSession.MORNING,"75"))
        val friday=CherryBetterDataEngine.calculate(
            source75,target("2026-11-20",DrawSession.MORNING))
        val rule=friday.activeRules.single{it.id.startsWith("new-born-75@")}
        assertEquals(LocalDate.parse("2026-11-21"),rule.expires)
        val monday=CherryBetterDataEngine.calculate(
            source75,target("2026-11-23",DrawSession.MORNING))
        assertFalse(monday.activeRules.any{it.id.startsWith("new-born-75@")})

        val legacy=listOf(d("2026-11-20",DrawSession.EVENING,"11"))
        val nextTradingDay=CherryBetterDataEngine.calculate(
            legacy,target("2026-11-23",DrawSession.MORNING))
        assertFalse(nextTradingDay.activeRules.any{it.id.startsWith("s4-wheel-11-")})
    }

    @Test fun nineteenBreakHasFiveCalendarDaysNotFiveTradingDays() {
        val origin=listOf(d("2026-11-20",DrawSession.EVENING,"91"))
        val within=CherryBetterDataEngine.calculate(
            origin,target("2026-11-25",DrawSession.MORNING))
        val born=within.activeRules.single{it.id.startsWith("new-bk-19r@")}
        assertEquals(LocalDate.parse("2026-11-25"),born.expires)
        val after=CherryBetterDataEngine.calculate(
            origin,target("2026-11-26",DrawSession.MORNING))
        assertFalse(after.activeRules.any{it.id.startsWith("new-bk-19r@")})
    }

    @Test fun nineteenReverseBirthCreatesOneBreakAndExpires() {
        val input=listOf(d("2026-11-09",DrawSession.EVENING,"91"))
        val next=CherryBetterDataEngine.calculate(input,target("2026-11-10",DrawSession.MORNING))
        val born=next.activeRules.single{it.id.startsWith("new-bk-19r@")}
        assertEquals(10,born.produced.size)
        assertTrue(born.produced.containsAll(listOf("01","10","29","92")))
        val expired=CherryBetterDataEngine.calculate(input,target("2026-11-17",DrawSession.MORNING))
        assertFalse(expired.activeRules.any{it.id.startsWith("new-bk-19r@")})
    }

    @Test fun excludedKhantNeverProducesActiveEvidence() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"98")),
            target("2026-11-10",DrawSession.MORNING))
        val pairs=report.current.filter{p->p.evidence.any{
            it.id=="new-special-khant"
        }}.map{it.pair}.toSet()
        assertTrue(pairs.isEmpty())
        assertTrue(report.born.none{p->p.evidence.any{it.id.startsWith("new-special-khant")}})
    }

    @Test fun missingImmediateResultDisablesStaleCurrentMath() {
        val report=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-06",DrawSession.MORNING,"80")),
            target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.current.isEmpty())
        assertTrue(report.math.isEmpty())
    }

    @Test fun explicitReverseSourceTriggersUseCanonicalIdsAndIdenticalOutputs() {
        fun pairs(input:String,id:String):Set<String> =
            CherryBetterDataEngine.calculate(
                listOf(d("2026-11-09",DrawSession.EVENING,input)),
                target("2026-11-10",DrawSession.MORNING))
                .current.filter{p->p.evidence.any{it.id==id}}.map{it.pair}.toSet()
        for((base,id) in listOf(
            "43" to "s6-43","79" to "s6-79","83" to "s6-83","84" to "s6-84",
            "80" to "s8-80","67" to "s8-67","48" to "s8-48",
            "13" to "s8-13","74" to "s8-74","71" to "s8-71","15" to "s8-15",
            "10" to "s6-10")) {
            assertTrue(id,pairs(base,id).isNotEmpty())
            assertEquals(id,pairs(base,id),pairs(base.reversed(),id))
            assertTrue(id,pairs("22",id).isEmpty())
        }
        assertEquals(CherryPairExpander.bk(7).toSet(),pairs("01","s6-10"))
        // A source without R is not silently expanded.
        assertTrue(pairs("40","s6-04").isEmpty())
    }

    @Test fun reverseWheelBornRetainsExpiryAndStopsAfterObservedHit() {
        val start=d("2026-11-09",DrawSession.MORNING,"08")
        val next=CherryBetterDataEngine.calculate(listOf(start),
            target("2026-11-09",DrawSession.EVENING))
        val born=next.activeRules.single{it.id.startsWith("s8-80@")}
        assertEquals(16,born.produced.size)
        val settled=CherryBetterDataEngine.calculate(
            listOf(start,d("2026-11-09",DrawSession.EVENING,"12")),
            target("2026-11-10",DrawSession.MORNING))
        assertFalse(settled.activeRules.any{it.id.startsWith("s8-80@")})
        val second=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-09",DrawSession.EVENING,"76")),
            target("2026-11-10",DrawSession.MORNING))
        assertTrue(second.activeRules.any{it.id.startsWith("s8-67@")})
    }

    @Test fun ninetyFourReverseBornUsesFiveCalendarDaysAndExcludesFutureInput() {
        val start=d("2026-11-20",DrawSession.EVENING,"49")
        val within=CherryBetterDataEngine.calculate(listOf(start),
            target("2026-11-25",DrawSession.MORNING))
        val born=within.activeRules.single{it.id.startsWith("s6-94@")}
        assertEquals(LocalDate.parse("2026-11-25"),born.expires)
        assertEquals(CherryPairExpander.any(1).toSet(),born.produced.toSet())
        val expired=CherryBetterDataEngine.calculate(listOf(start),
            target("2026-11-26",DrawSession.MORNING))
        assertFalse(expired.activeRules.any{it.id.startsWith("s6-94@")})
        val future=CherryBetterDataEngine.calculate(
            listOf(d("2026-11-25",DrawSession.EVENING,"94")),
            target("2026-11-25",DrawSession.MORNING))
        assertFalse(future.activeRules.any{it.id.startsWith("s6-94@")})
    }

}
