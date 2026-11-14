package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CherryMathStreakEngineTest {
    private fun draw(date:String,session:DrawSession,pair:String)=
        HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun target(date:String,session:DrawSession)=
        DrawIdentity(LocalDate.parse(date),session)

    @Test fun canonicalDocFormulasUseOnlyEarlierSlots(){
        val history=listOf(
            draw("2026-11-09",DrawSession.MORNING,"12"),
            draw("2026-11-09",DrawSession.EVENING,"34")
        )
        val whenNext=target("2026-11-10",DrawSession.MORNING)
        assertEquals(2,CherryMathStreakEngine.predict("w1",history,whenNext))
        assertEquals(0,CherryMathStreakEngine.predict("l1",history,whenNext))
        assertEquals(7,CherryMathStreakEngine.predict("b1",history,whenNext))
        assertEquals(9,CherryMathStreakEngine.predict("b2",history,whenNext))
        assertEquals(4,CherryMathStreakEngine.predict("cycle",history,whenNext))
        assertNull(CherryMathStreakEngine.predict("w2",history,whenNext))
    }
    @Test fun evaluatesOnlyCompletedHistoryAndDoesNotPeekAtTarget(){
        val history=listOf(
            draw("2026-11-09",DrawSession.MORNING,"12"),
            draw("2026-11-09",DrawSession.EVENING,"34"),
            draw("2026-11-10",DrawSession.MORNING,"99")
        )
        val am=CherryBetterDataEngine.calculate(history,target("2026-11-10",DrawSession.MORNING))
        assertEquals("34",am.last?.digit)
        assertEquals(2,am.mathAnalysis.testedDraws)
        assertTrue(am.mathAnalysis.formulas.first{it.id=="w1"}.result==2)
        assertEquals(210,am.mathAnalysis.research.size)
        assertTrue(am.mathAnalysis.research.all{it.source=="RESEARCH"})
        assertTrue(am.mathAnalysis.formulas.all{it.source=="DOC"})
        assertTrue(am.mathAnalysis.champions.isEmpty())
    }
    @Test fun crossPairingIncludesDirectAndPowerBreakWithoutDroppingOriginal(){
        val history=listOf(
            draw("2026-11-09",DrawSession.MORNING,"12"),
            draw("2026-11-09",DrawSession.EVENING,"34")
        )
        val report=CherryBetterDataEngine.calculate(history,target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.math.any{it.pair=="07"&&it.evidence.any{e->e.id=="cross-l1-b1"}})
        assertTrue(report.math.any{it.pair=="02"&&it.evidence.any{e->e.id=="cross-l1-b1-power"}})
        assertTrue(report.math.any{it.pair=="27"&&it.evidence.any{e->e.id=="gold-w1"}})
    }
    @Test fun longestStreakChampionChangesAfterNewMiss(){
        val history=listOf(
            draw("2026-11-09",DrawSession.MORNING,"00"),
            draw("2026-11-09",DrawSession.EVENING,"07"),
            draw("2026-11-10",DrawSession.MORNING,"04"),
            draw("2026-11-10",DrawSession.EVENING,"01"),
            draw("2026-11-11",DrawSession.MORNING,"08"),
            draw("2026-11-11",DrawSession.EVENING,"05"),
            draw("2026-11-12",DrawSession.MORNING,"02")
        )
        val before=CherryBetterDataEngine.calculate(
            history,target("2026-11-12",DrawSession.EVENING))
        assertEquals("cycle",before.mathAnalysis.champions["BREAK"])
        assertEquals(6,before.mathAnalysis.byId("cycle")?.streak)
        val after=CherryBetterDataEngine.calculate(
            history+draw("2026-11-12",DrawSession.EVENING,"55"),
            target("2026-11-13",DrawSession.MORNING))
        assertEquals("cycle",after.mathAnalysis.previousChampions["BREAK"])
        assertNotEquals("cycle",after.mathAnalysis.champions["BREAK"])
        assertEquals(0,after.mathAnalysis.byId("cycle")?.streak)
    }
    @Test fun loneConsensusNeedsThreeConfirmedMethodsAndSameWamPair(){
        fun lone(id:String,digit:Int,streak:Int=3)=CherryMathFormula(
            id,id,"LONE",digit,streak,streak,streak,emptyList())
        val agreeing=listOf(lone("l1",2),lone("l2",2),lone("wl",2))
        assertEquals(2,CherryMathStreakEngine.confirmedLone(agreeing,7))
        assertNull(CherryMathStreakEngine.confirmedLone(agreeing,1))
        assertNull(CherryMathStreakEngine.confirmedLone(
            listOf(lone("l1",2),lone("l2",2),lone("wl",8)),7))
        assertNull(CherryMathStreakEngine.confirmedLone(
            listOf(lone("l1",2),lone("l2",2,2),lone("wl",2)),7))
    }
    @Test fun unverifiedMathSignalsStayInDetailsButOutOfRankedPicks(){
        val history=listOf(
            draw("2026-11-09",DrawSession.MORNING,"12"),
            draw("2026-11-09",DrawSession.EVENING,"34")
        )
        val report=CherryBetterDataEngine.calculate(
            history,target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.math.any{p->p.evidence.any{it.id.startsWith("gold-")}})
        assertNull(report.mathAnalysis.primaryChampionId)
        assertNull(report.mathAnalysis.loneConsensus)
        assertFalse(report.ranked.any{p->p.evidence.any{it.tab==CherryTab.MATH}})
        assertFalse(report.summary.any{p->p.evidence.any{it.tab==CherryTab.MATH}})
    }
    @Test fun qualifiedGoldenPairReservesBothDirectionsInShortSummary(){
        val history=(0 until 8).map{i->
            val day=LocalDate.parse("2026-11-09").plusDays((i/2).toLong())
            HistoricDraw(day,if(i%2==0)DrawSession.MORNING
                else DrawSession.EVENING,"90",HistoricSource.WINNER)
        }
        val report=CherryBetterDataEngine.calculate(
            history,target("2026-11-13",DrawSession.MORNING))
        assertEquals("w1",report.mathAnalysis.champions["WAM"])
        assertTrue(report.mathAnalysis.champion("WAM")!!.streak>=3)
        assertTrue(report.summary.any{it.pair=="49"&&it.cuts.isEmpty()})
        assertTrue(report.summary.any{it.pair=="94"&&it.cuts.isEmpty()})
        assertTrue(report.summary.size<=3)
        assertNull(report.mathAnalysis.loneConsensus)
        assertFalse(report.ranked.any{p->p.evidence.any{
            it.id.startsWith("cross-")
        }})
    }

    @Test fun producesOriginalDocConditionalMathWatchWithoutInventingFormula17Pairs(){
        val history=listOf(draw("2026-11-09",DrawSession.MORNING,"05"))
        val report=CherryBetterDataEngine.calculate(history,target("2026-11-09",DrawSession.EVENING))
        assertTrue(report.mathAnalysis.watches.any{it.id=="m05"&&it.pairs.contains("25")})
    }
}
