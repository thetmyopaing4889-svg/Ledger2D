package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryEvidenceIntegrityTest {
    private val mon=LocalDate.of(2026,11,23)
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun row(day:Long,n:String,source:HistoricSource=HistoricSource.WINNER,
                    session:DrawSession=am)=HistoricDraw(mon.plusDays(day),session,n,source)

    @Test fun equalDuplicatesCollapseAndHigherAuthorityWins() {
        val history=listOf(row(0,"12",HistoricSource.HISTORY),row(0,"34",HistoricSource.DAILY),
            row(0,"56"),row(0,"56"),row(0,"78",session=pm))
        val results=CherryVerifiedHistory.normalize(history)
        assertEquals(listOf("56","78"),results.map{it.digit})
        assertEquals(results,CherryVerifiedHistory.normalize(history.reversed()))
    }

    @Test fun conflictingHighestAuthorityMissingAndWeekendClosedExcluded() {
        val history=listOf(row(0,"12"),row(0,"34"),row(0,"56",HistoricSource.DAILY),
            row(1,"00"),row(2,"23"),row(5,"45"),row(6,"67"),row(3,"?"))
        val normalized=CherryVerifiedHistory.normalize(history,setOf(mon.plusDays(1)))
        assertEquals(listOf("23"),normalized.map{it.digit})
        assertEquals(normalized,CherryVerifiedHistory.normalize(history.reversed(),setOf(mon.plusDays(1))))
        val report=CherryBetterDataEngine.calculate(history,DrawIdentity(mon,pm))
        assertNull(report.last)
        assertTrue(report.cutEvidence.isEmpty())
    }

    @Test fun repeatedArchiveUsesLatestValidSnapshotOnceAndEqualTimeConflictExcluded() {
        val target=DrawIdentity(mon,am)
        val time=mon.atTime(11,0).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()
        fun snap(n:String,t:Long)=CherrySuccessSnapshot(target,t,
            listOf(CherrySuccessRule("x","x",CherryTab.CURRENT,CherrySuccessCriterion.PAIR,listOf(n))))
        val a=snap("12",time)
        val b=snap("34",time+1000)
        val late=snap("12",mon.atTime(12,0).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli())
        val confirmed=mapOf(target to "34")
        val valid=CherrySuccessEngine.completed(listOf(a,b,b,late),confirmed)
        assertEquals(b,valid.single().snapshot)
        assertTrue(valid.single().checks.single().correct)
        assertTrue(CherrySuccessEngine.completed(listOf(b,snap("56",time+1000)),confirmed).isEmpty())
    }

    @Test fun rankingAndProofRemainIdenticalAfterGrouping() {
        val target=DrawIdentity(mon,am)
        val time=mon.atTime(11,0).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()
        val snapshots=(0L..2L).map{day->
            CherrySuccessSnapshot(target.copy(date=mon.plusDays(day)),time+day*86400000,
                listOf(CherrySuccessRule("x@"+day,"x",CherryTab.BORN,CherrySuccessCriterion.PAIR,listOf("12")),
                    CherrySuccessRule("x@other"+day,"x",CherryTab.BORN,CherrySuccessCriterion.PAIR,listOf("34"))),
                CherrySuccessEngine.CURRENT_RULE_VERSION)
        }
        val results=CherrySuccessEngine.completed(snapshots,snapshots.associate{it.target to "34"})
        val rank=CherrySuccessEngine.ranking(results,1).single()
        val proof=CherrySuccessEngine.rankEvidence(results,rank.id)
        assertEquals(proof.size,rank.evaluated)
        assertEquals(proof.count{it.correct},rank.correct)
        assertEquals(proof.first().pairCount,rank.pairCount)
    }
}
