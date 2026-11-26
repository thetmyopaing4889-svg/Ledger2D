package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Synthetic inputs exercise the pipeline, not claims of historical predictive accuracy. */
class CherryEndToEndEvidenceTest {
    private val monday=LocalDate.of(2026,11,23)
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private val zone=ZoneId.of("Asia/Yangon")
    private fun row(date:LocalDate,session:DrawSession,n:String)=
        HistoricDraw(date,session,n,HistoricSource.WINNER)
    private fun snap(report:CherryReport):CherrySuccessSnapshot =
        CherrySuccessEngine.createSnapshot(report,emptyList(),
            report.target.date.atTime(11,0).atZone(zone).toInstant().toEpochMilli())
    private fun checkReport(report:CherryReport) {
        val snapshot=snap(report)
        for((tab,picks) in listOf(CherryTab.CURRENT to report.current,CherryTab.BORN to report.born)) {
            fun expected(values:List<CherryPick>)=values.flatMap{it.evidence}
                .filter{it.tab==tab}.groupBy{it.id}
                .mapValues{(_,ev)->ev.map{it.pair}.toSet()}
            fun actual(rules:List<CherrySuccessRule>)=rules.filter{it.tab==tab}
                .associate{it.id to it.predictions.toSet()}
            assertEquals(expected(picks),actual(snapshot.rawRules))
            assertEquals(expected(picks.filter{it.cuts.isEmpty()}),actual(snapshot.rules))
        }
        assertEquals(report.cutEvidence,snapshot.cutRules)
        val excluded=snapshot.cutRules.flatMap{it.excluded}.toSet()
        assertTrue(snapshot.rules.all{r->r.predictions.none{it in excluded}})
        assertTrue(snapshot.rawRules.filter{it.tab==CherryTab.BORN}.all{
            report.target in it.eligibleSlots
        })
        assertTrue(snapshot.rawRules.all{
            CherryLibrarySourceEvidence.sourceKey(it.id)!=null || it.id=="s7-am05"
        })
        assertTrue(snapshot.cutRules.all{
            CherryLibrarySourceEvidence.sourceKey(it.id)!=null ||
                it.id in setOf("cut-double-digit","cut-morning-head")
        })
        for(result in listOf("00","11","99")) {
            val completed=CherrySuccessEngine.completed(listOf(snapshot,snapshot),mapOf(report.target to result))
            val draw=completed.single()
            assertTrue(draw.checks.all{it.correct==(result in it.rule.predictions)})
            val raw=CherryCutSuccess.rawView(draw)
            if(snapshot.rawRules.isNotEmpty()) {
                assertNotNull(raw)
                assertTrue(raw!!.checks.all{it.correct==(result in it.rule.predictions)})
            } else assertNull(raw)
            assertTrue(CherryCutSuccess.checks(draw).all{it.correct==(result !in it.rule.excluded)})
            val allowed=snapshot.rawRules.mapNotNull{CherryLibrarySourceEvidence.sourceKey(it.id)}
                .map{it.substringBefore('#')}.toSet()+
                snapshot.cutRules.mapNotNull{CherryLibrarySourceEvidence.sourceKey(it.id)}
            val history=listOf(row(report.target.date,report.target.session,result))
            assertEquals(CherryLibrarySourceEvidence.scoredCompleted(completed,allowed),
                CherryLibrarySourceEvidence.scored(listOf(snapshot),history,allowed))
            assertEquals(CherryLibrarySuccessBridge.scoredCompleted(completed,allowed),
                CherryLibrarySuccessBridge.scored(listOf(snapshot),history,allowed))
        }
    }

    @Test fun allHundredPriorPairsAcrossAMAndPMKeepEvidenceAligned() {
        for(n in 0..99) {
            val pair=n.toString().padStart(2,'0')
            val history=listOf(row(monday,am,pair))
            val target=DrawIdentity(monday,pm)
            val report=CherryBetterDataEngine.calculate(history,target)
            checkReport(report)
            val next=DrawIdentity(monday.plusDays(1),am)
            val prior=listOf(row(monday,am,"11"),row(monday,pm,pair))
            val nextReport=CherryBetterDataEngine.calculate(prior,next,"11","12")
            checkReport(nextReport)
            val contaminated=prior+listOf(row(next.date,next.session,"99"),
                row(next.date.plusDays(1),am,"88"))
            assertEquals(nextReport,CherryBetterDataEngine.calculate(contaminated.reversed(),next,"11","12"))
        }
    }

    @Test fun multiWeekAndClosedDayPipelineNeverUsesFutureResults() {
        val days=(0L..18L).map{monday.minusWeeks(1).plusDays(it)}
            .filter{DrawSchedule.isWeekday(it)}
        val closed=setOf(monday.plusDays(2))
        val rows=days.filter{it !in closed}.flatMapIndexed{i,d->
            listOf(row(d,am,((i*17+6)%100).toString().padStart(2,'0')),
                row(d,pm,((i*29+44)%100).toString().padStart(2,'0')))
        }
        for(d in days.filter{it>=monday&&it !in closed}) for(session in listOf(am,pm)) {
            val target=DrawIdentity(d,session)
            val before=rows.filter{it.date<d || (it.date==d&&it.session.ordinal<session.ordinal)}
            val clean=CherryBetterDataEngine.calculate(before,target,"11","12",closed)
            val dirty=rows+row(monday.plusDays(2),am,"99")+row(monday.minusDays(1),pm,"88")
            assertEquals(clean,CherryBetterDataEngine.calculate(dirty.reversed(),target,"11","12",closed))
            checkReport(clean)
        }
    }
}
