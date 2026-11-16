package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Regression gates for replacing legacy on-open Math with manual V3. */
class CherryMathStreakEngineTest {
    private fun draw(date:String,session:DrawSession,pair:String)=
        HistoricDraw(LocalDate.parse(date),session,pair,HistoricSource.WINNER)
    private fun target(date:String,session:DrawSession)=
        DrawIdentity(LocalDate.parse(date),session)

    @Test fun legacyGeminiAndChampionNeverRunWhenBetterDataOpens() {
        val history=(0 until 8).map{i->
            val date=LocalDate.parse("2026-11-09").plusDays((i/2).toLong())
            HistoricDraw(date,if(i%2==0)DrawSession.MORNING else DrawSession.EVENING,
                "90",HistoricSource.WINNER)
        }
        val report=CherryBetterDataEngine.calculate(
            history,target("2026-11-13",DrawSession.MORNING))
        assertTrue(report.math.isEmpty())
        assertTrue(report.mathAnalysis.formulas.isEmpty())
        assertTrue(report.mathAnalysis.research.isEmpty())
        assertTrue(report.mathAnalysis.champions.isEmpty())
        assertNull(report.mathAnalysis.primaryChampion())
        assertTrue(report.mathSelections().isEmpty())
        assertTrue(report.ranked.none{p->p.evidence.any{it.tab==CherryTab.MATH}})
        assertTrue(report.summary.none{p->p.evidence.any{it.tab==CherryTab.MATH}})
        assertTrue(report.summary.size<=3)
    }

    @Test fun currentBornAndSummaryIgnoreFutureDrawsWithoutLegacyMath() {
        val results=listOf(
            draw("2026-11-09",DrawSession.MORNING,"80"),
            draw("2026-11-09",DrawSession.EVENING,"34"),
            draw("2026-11-10",DrawSession.MORNING,"99"))
        val a=CherryBetterDataEngine.calculate(
            results,target("2026-11-10",DrawSession.MORNING))
        val b=CherryBetterDataEngine.calculate(
            results.take(2),target("2026-11-10",DrawSession.MORNING))
        assertEquals(a.current,b.current)
        assertEquals(a.born,b.born)
        assertEquals(a.summary,b.summary)
        assertTrue(a.current.isNotEmpty())
        assertTrue(a.summary.all{it.cuts.isEmpty()})
    }
}
