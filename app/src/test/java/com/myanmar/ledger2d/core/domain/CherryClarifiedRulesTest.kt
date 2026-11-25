package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CherryClarifiedRulesTest {
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun d(date:String,session:DrawSession,pair:String)=
        HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun report(date:String,session:DrawSession,vararg rows:HistoricDraw,
                       closed:Set<LocalDate> = emptySet())=
        CherryBetterDataEngine.calculate(rows.toList(),DrawIdentity(LocalDate.parse(date),session),closed=closed)
    private fun pairs(report:CherryReport,id:String)=
        (report.current+report.born).filter{p->p.evidence.any{it.id.substringBefore('@')==id}}
            .map{it.pair}.toSet()
    private fun digit(n:Int)=CherryPairExpander.any(n).toSet()
    private fun bk(n:Int)=CherryPairExpander.bk(n).toSet()
    private fun wheel(n:String,doubles:Boolean=false)=CherryPairExpander.wheel(n,doubles).toSet()
    private val doubles=(0..9).map{"$it$it"}.toSet()

    @Test fun previousDayRulesUseBothSlotsAndNeverCrossWeek() {
        val a=d("2026-11-23",am,"01")
        val b=d("2026-11-23",pm,"02")
        val r=report("2026-11-24",am,a,b)
        assertEquals(digit(9)+digit(0)+doubles,pairs(r,"s1-day-head-tail"))
        assertEquals(digit(3),pairs(r,"s9-wailin"))
        assertEquals(pairs(r,"s1-day-head-tail"),pairs(report("2026-11-24",pm,a,b),"s1-day-head-tail"))
        assertTrue(pairs(report("2026-11-24",am,a),"s1-day-head-tail").isEmpty())
        assertTrue(pairs(report("2026-11-30",am,d("2026-11-27",am,"01"),
            d("2026-11-27",pm,"02")),"s1-day-head-tail").isEmpty())
        assertTrue(pairs(report("2026-11-25",am,a,b,
            closed=setOf(LocalDate.parse("2026-11-24"))),"s9-wailin").isEmpty())
    }

    @Test fun sameBreakAndDiagonalUseExactRecordedPairsOnly() {
        val r=report("2026-11-25",am,d("2026-11-23",am,"26"),
            d("2026-11-24",am,"12"),d("2026-11-24",pm,"21"),
            d("2026-11-25",am,"88"))
        val b=pairs(r,"s1-day-break")
        assertTrue(b.containsAll(listOf("26","12","21","00")))
        assertFalse("62" in b)
        assertEquals(digit(0)+doubles+setOf("26","12","21"),b)
        val diag=report("2026-11-25",am,d("2026-11-23",am,"26"),
            d("2026-11-24",am,"23"),d("2026-11-24",pm,"45"))
        assertTrue(pairs(diag,"s1-day-diagonal").containsAll(listOf("26","23","45","07")))
        assertFalse("62" in pairs(diag,"s1-day-diagonal"))
    }

    @Test fun oneSessionRulesRespectTriggerAndScope() {
        assertEquals(wheel("357",true),pairs(report("2026-11-24",am,
            d("2026-11-23",pm,"75")),"s6-57"))
        assertEquals(digit(7),pairs(report("2026-11-24",pm,
            d("2026-11-24",am,"90")),"s8-zero-follow"))
        assertEquals(digit(7),pairs(report("2026-11-24",pm,
            d("2026-11-24",am,"09")),"s8-zero-follow"))
        assertTrue(pairs(report("2026-11-25",am,d("2026-11-24",am,"75")),"s6-57").isEmpty())
        val r=report("2026-11-24",am,d("2026-11-23",pm,"42"))
        assertTrue(pairs(r,"s8-evening-four").containsAll(listOf("05","07","94","96")))
        assertTrue(pairs(report("2026-11-24",pm,d("2026-11-24",am,"42")),"s8-evening-four").isEmpty())
    }

    @Test fun fourSessionsUseScheduleNotObservedRowCount() {
        val a=d("2026-11-23",pm,"60")
        val closed=setOf(LocalDate.parse("2026-11-24"))
        assertTrue(pairs(report("2026-11-26",pm,a,closed=closed),"s6-06").isNotEmpty())
        assertTrue(pairs(report("2026-11-27",am,a,closed=closed),"s6-06").isEmpty())
        assertTrue(pairs(report("2026-11-26",am,a,d("2026-11-25",am,"12"),closed=closed),"s6-06").isEmpty())
    }

    @Test fun mondayRulesUseSeparateBranchesAndExactRepeats() {
        val a=d("2026-11-23",am,"11")
        val b=d("2026-11-23",pm,"21")
        assertEquals(bk(9),pairs(report("2026-11-24",am,a,b),"s5-mon-ones"))
        val c=d("2026-11-23",am,"93")
        val e=d("2026-11-23",pm,"50")
        val r=report("2026-11-24",am,c,e)
        assertEquals(doubles,pairs(r,"s5-mon-nine"))
        assertEquals(setOf("93","50"),pairs(r,"s5-mon-three"))
        assertEquals(bk(0),pairs(r,"s5-mon-five-break"))
        assertEquals(digit(5),pairs(r,"s5-mon-five-digit"))
        assertTrue(pairs(report("2026-11-25",am,c,e,d("2026-11-24",am,"93")),"s5-mon-three").isEmpty())
        assertTrue(pairs(report("2026-11-30",am,c,e),"s5-mon-nine").isEmpty())
        assertEquals(digit(3),pairs(report("2026-11-23",pm,d("2026-11-23",am,"72")),"s8-mon-seven"))
    }

    @Test fun nextWeekPowerUsesPriorThursdayFridayAndEndsAfterHit() {
        val th=d("2026-11-19",am,"10")
        val fr=d("2026-11-20",am,"65")
        val expected=listOf("26","29","28","48","46","49").flatMap{listOf(it,it.reversed())}.toSet()
        assertEquals(expected,pairs(report("2026-11-23",am,th,fr),"s8-thufri-power"))
        assertTrue(pairs(report("2026-11-20",pm,th,fr),"s8-thufri-power").isEmpty())
        assertTrue(pairs(report("2026-11-24",am,th,fr,d("2026-11-23",am,"62")),"s8-thufri-power").isEmpty())
        assertTrue(pairs(report("2026-11-30",am,th,fr),"s8-thufri-power").isEmpty())
    }

    @Test fun weeklyMobAndEveningTailHaveIndependentExpiry() {
        val a=d("2026-11-23",pm,"17")
        val r=report("2026-11-24",am,a)
        assertEquals(wheel("1267"),pairs(r,"new-mob-wheel"))
        assertEquals(digit(5)+digit(0)+bk(3),pairs(r,"new-bk-evening7"))
        assertTrue(pairs(report("2026-11-30",am,a),"new-mob-wheel").isEmpty())
        assertTrue(pairs(report("2026-11-30",am,a),"new-bk-evening7").isEmpty())
    }

    @Test fun weekdayRulesUseConfirmedSessionsAndNoExcludedBranches() {
        val monday=report("2026-11-24",am,d("2026-11-23",am,"13"),d("2026-11-23",pm,"76"))
        assertEquals(setOf("27","45"),pairs(monday,"new-day-mon-b4"))
        assertEquals(listOf("09","27","35","49","56","89").flatMap{listOf(it,it.reversed())}.toSet(),
            pairs(monday,"new-day-mon-t6"))
        val tue=report("2026-11-25",am,d("2026-11-24",am,"56"),d("2026-11-24",pm,"13"))
        assertEquals(setOf("13","31"),pairs(report("2026-11-24",pm,d("2026-11-24",am,"56")),"new-day-tue-56"))
        assertEquals(bk(8),pairs(tue,"new-day-tue-b4"))
        val wed=report("2026-11-26",am,d("2026-11-25",am,"36"),d("2026-11-25",pm,"50"))
        assertEquals(wheel("1456"),pairs(wed,"new-day-wed-b9"))
        assertEquals(bk(6)+bk(7)+bk(8),pairs(wed,"new-day-wed-b5"))
        assertEquals(CherryPairExpander.top(8).toSet()+bk(8),pairs(wed,"new-day-wed-t6"))
        assertTrue(pairs(report("2026-11-30",am,d("2026-11-25",am,"36")),"new-day-wed-b9").isEmpty())
    }

    @Test fun weeklyWatchesStartFreshAndRetireIndependently() {
        val first=report("2026-11-23",am)
        assertEquals(digit(9),pairs(first,"new-groups-weekly-nine"))
        assertEquals(bk(1),pairs(first,"new-groups-weekly-break"))
        assertEquals(wheel("4679"),pairs(first,"new-groups-weekly-wheel"))
        val r=report("2026-11-23",pm,d("2026-11-23",am,"19"))
        assertTrue(pairs(r,"new-groups-weekly-nine").isEmpty())
        assertEquals(bk(1),pairs(r,"new-groups-weekly-break"))
        assertEquals(wheel("4679"),pairs(r,"new-groups-weekly-wheel"))
    }
}
