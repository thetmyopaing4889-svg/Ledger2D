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
        assertEquals(30,am.mathAnalysis.research.size)
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
    @Test fun producesOriginalDocConditionalMathWatchWithoutInventingFormula17Pairs(){
        val history=listOf(draw("2026-11-09",DrawSession.MORNING,"05"))
        val report=CherryBetterDataEngine.calculate(history,target("2026-11-09",DrawSession.EVENING))
        assertTrue(report.mathAnalysis.watches.any{it.id=="m05"&&it.pairs.contains("25")})
    }
}
