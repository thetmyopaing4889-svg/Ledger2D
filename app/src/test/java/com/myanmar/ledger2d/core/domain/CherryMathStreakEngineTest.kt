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
        // No Champion is legitimate, but calculable DOC methods can still
        // provide a clearly labelled experimental fallback (not a 3-step win).
        val fallback=report.mathSelections()
        assertTrue(fallback.isNotEmpty())
        assertTrue(fallback.size<=3)
        assertTrue(fallback.all{it.mode==CherryMathSelectionMode.FALLBACK})
        assertTrue(fallback.all{it.grade!=CherryMathGrade.A})
        assertTrue(fallback.all{it.pick.cuts.isEmpty()})
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
        val mathChoice=report.mathShortlist()
        assertEquals(setOf("49","94"),mathChoice.map{it.pair}.toSet())
        assertTrue(report.mathSelections().all{it.mode==CherryMathSelectionMode.STRICT})
        assertTrue(report.mathSelections().all{it.grade==CherryMathGrade.A})
        assertTrue(mathChoice.size<=3)
        assertTrue(mathChoice.all{it.cuts.isEmpty()})
        assertTrue(report.ranked.any{it.pair=="49"})
        assertTrue(report.ranked.any{it.pair=="94"})
        assertTrue(report.summary.size<=3)
        assertNull(report.mathAnalysis.loneConsensus)
        assertFalse(report.ranked.any{p->p.evidence.any{
            it.id.startsWith("cross-")
        }})
    }

    @Test fun fallbackTiesOverTenStayVisibleWithoutFalseRanking(){
        // Deliberately twelve distinct pair FAMILIES, not 01 and 10
        // (those are the same direct/reverse family).
        val twelve=((0..9).map{"0${it}"}+listOf("11","22")).map{pair->
            CherryMathCandidate(
                CherryPick(pair,emptyList(),emptyList(),0),
                12,CherryMathGrade.B)
        }
        val cut=CherryMathCandidate(
            CherryPick("99",emptyList(),listOf("documented cut"),0),
            99,CherryMathGrade.A)
        val selections=rankCherryMathCandidates(
            twelve+cut,CherryMathSelectionMode.FALLBACK)
        assertEquals(listOf("00","01","02"),selections.map{it.pick.pair})
        assertEquals(listOf(1,2,3),selections.map{it.rank})
        assertTrue(selections.all{it.grade==CherryMathGrade.B})
        assertTrue(selections.all{it.evidenceRank==1})
        assertTrue(selections.all{it.tiedCandidates==12})
        assertTrue(selections.all{it.tiesBeyondDisplayed})
    }
    @Test fun directAndReverseShareRankInsteadOfClaimingIndependentEvidence(){
        val candidates=listOf(
            CherryMathCandidate(CherryPick("14",emptyList(),emptyList(),0),
                7,CherryMathGrade.C,preferDirect=true),
            CherryMathCandidate(CherryPick("41",emptyList(),emptyList(),0),
                7,CherryMathGrade.C),
            CherryMathCandidate(CherryPick("38",emptyList(),emptyList(),0),
                5,CherryMathGrade.C),
            CherryMathCandidate(CherryPick("83",emptyList(),emptyList(),0),
                5,CherryMathGrade.C)
        )
        val chosen=rankCherryMathCandidates(
            candidates,CherryMathSelectionMode.FALLBACK)
        assertEquals(listOf("14","41","38"),chosen.map{it.pick.pair})
        assertEquals(listOf(1,1,2),chosen.map{it.evidenceRank})
        assertEquals("14",chosen[1].reverseOf)
        assertNull(chosen[0].reverseOf)
        assertTrue(chosen[2].hiddenReverse)
        assertTrue(chosen.all{it.tiedCandidates==1})
        assertTrue(chosen.all{!it.tiesBeyondDisplayed})
    }
    @Test fun directionsDoNotGetArtificialScoreForBeingDirect(){
        val samples=listOf(
            CherryMathCandidate(CherryPick("14",emptyList(),emptyList(),0),
                5,CherryMathGrade.C,preferDirect=true),
            CherryMathCandidate(CherryPick("41",emptyList(),emptyList(),0),
                5,CherryMathGrade.C),
            CherryMathCandidate(CherryPick("38",emptyList(),emptyList(),0),
                5,CherryMathGrade.C),
            CherryMathCandidate(CherryPick("83",emptyList(),emptyList(),0),
                5,CherryMathGrade.C))
        val chosen=rankCherryMathCandidates(samples,CherryMathSelectionMode.FALLBACK)
        assertEquals(setOf(1),chosen.map{it.evidenceRank}.toSet())
        assertTrue(chosen.all{it.tiedCandidates==2})
        assertTrue(chosen.none{it.grade==CherryMathGrade.A})
    }

    @Test fun lowEvidenceCrossPairDoesNotOutrankGoldenByArbitraryCategoryBonus(){
        // The UI once ranked 14 above 38 solely because CROSS got an
        // undocumented category bonus (and put 41 in a different rank).
        val formulas=listOf(
            CherryMathFormula("w1","WAM","WAM",8,0,8,0,emptyList()),
            CherryMathFormula("wl","WaiLin","LONE",1,0,8,0,emptyList()),
            CherryMathFormula("cycle","3-less","BREAK",5,0,8,0,emptyList())
        )
        val analysis=CherryMathAnalysis(formulas,emptyMap(),emptyMap(),
            emptyList(),emptyList(),emptyList(),8)
        fun pick(pair:String,id:String)=CherryPick(pair,
            listOf(CherryEvidence(id,id,id,"",pair,CherryTab.MATH)),
            emptyList(),0)
        val report=CherryReport(
            target=target("2026-11-16",DrawSession.MORNING),
            observed=8,last=null,current=emptyList(),born=emptyList(),
            math=listOf(pick("14","cross-wl-cycle"),
                pick("41","cross-wl-cycle"),
                pick("38","gold-w1"),pick("83","gold-w1")),
            summary=emptyList(),ranked=emptyList(),activeRules=emptyList(),
            singles=emptyList(),breaks=emptyList(),cuts=emptyMap(),
            has930=false,mathAnalysis=analysis)
        val selected=report.mathSelections()
        assertEquals(listOf("14","41","38"),selected.map{it.pick.pair})
        assertTrue(selected.all{it.mode==CherryMathSelectionMode.FALLBACK})
        assertTrue(selected.all{it.grade==CherryMathGrade.C})
        assertTrue(selected.all{it.evidenceRank==1})
        assertTrue(selected.all{it.tiedCandidates==2})
        assertEquals("14",selected[1].reverseOf)
    }

    @Test fun differentEvidenceScoresBreakTiesBeforeDisplayOrder(){
        val candidates=listOf("78" to 4,"93" to 12,"24" to 8,"21" to 8)
            .map{(pair,score)->
                CherryMathCandidate(CherryPick(pair,emptyList(),emptyList(),0),
                    score,CherryMathGrade.B)
            }
        val selections=rankCherryMathCandidates(
            candidates,CherryMathSelectionMode.FALLBACK)
        assertEquals(listOf("93","21","24"),selections.map{it.pick.pair})
        assertEquals(listOf(1,2,2),selections.map{it.evidenceRank})
        assertTrue(selections[1].tiedCandidates==2)
        assertTrue(selections.all{it.rank in 1..3})
    }
    @Test fun noInputDoesNotInventFallback(){
        val report=CherryBetterDataEngine.calculate(
            emptyList(),target("2026-11-10",DrawSession.MORNING))
        assertTrue(report.mathSelections().isEmpty())
    }

    @Test fun waiLinUsesActualPreviousWorkingDayAndRejectsStaleHistory(){
        val friday=listOf(
            draw("2026-11-06",DrawSession.MORNING,"42"),
            draw("2026-11-06",DrawSession.EVENING,"13")
        )
        assertEquals(8,CherryMathStreakEngine.predict(
            "wl",friday,target("2026-11-09",DrawSession.MORNING)))
        assertNull(CherryMathStreakEngine.predict(
            "wl",friday,target("2026-11-10",DrawSession.MORNING)))
        val stale=CherryBetterDataEngine.calculate(
            friday,target("2026-11-10",DrawSession.MORNING))
        assertFalse(stale.singles.any{it.id=="wl"})
        assertNull(stale.mathAnalysis.byId("wl")?.result)
    }

    @Test fun producesOriginalDocConditionalMathWatchWithoutInventingFormula17Pairs(){
        val history=listOf(draw("2026-11-09",DrawSession.MORNING,"05"))
        val report=CherryBetterDataEngine.calculate(history,target("2026-11-09",DrawSession.EVENING))
        assertTrue(report.mathAnalysis.watches.any{it.id=="m05"&&it.pairs.contains("25")})
    }
}
