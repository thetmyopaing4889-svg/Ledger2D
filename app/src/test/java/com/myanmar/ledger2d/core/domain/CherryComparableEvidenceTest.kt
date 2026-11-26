package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryComparableEvidenceTest {
    private val monday=LocalDate.of(2026,11,23)
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private val version=CherrySuccessEngine.CURRENT_RULE_VERSION
    private val first=DrawIdentity(monday,am)
    private val second=DrawIdentity(monday,pm)
    private fun rule(id:String,pair:String,slots:List<DrawIdentity> = listOf(first,second))=
        CherrySuccessRule(id,id,CherryTab.BORN,CherrySuccessCriterion.PAIR,listOf(pair),eligibleSlots=slots)
    private fun draw(target:DrawIdentity,raw:List<CherrySuccessRule>,offered:List<CherrySuccessRule>,n:String)=
        requireNotNull(CherrySuccessEngine.evaluate(CherrySuccessSnapshot(target,
            target.date.atTime(10,0).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli(),
            offered,version,rawRules=raw),n))

    @Test fun oldOfferedCannotEnterRawComparisonAndFullyCutIsNotALoss() {
        val id="s6-04@2026-11-20-EVENING"
        val r=rule(id,"11")
        val old=draw(first,emptyList(),listOf(r),"11")
        val current=draw(second,listOf(r),emptyList(),"11")
        val cohort=CherryComparableEvidence.cohort(listOf(current,old))
        assertEquals(listOf(second),cohort.raw.map{it.snapshot.target})
        assertEquals(listOf(second),cohort.offered.map{it.snapshot.target})
        assertEquals(listOf(first),cohort.withoutRaw.map{it.snapshot.target})
        assertEquals(CherryBornLifetime.Status.HIT,CherryBornLifetime.evaluate(cohort.raw).single().status)
        assertTrue(CherryBornLifetime.evaluate(cohort.offered).isEmpty())
        assertEquals(CherryComparableEvidence.Counts(1,1,0,0,1),
            CherryComparableEvidence.counts(cohort.raw.single().checks,cohort.offered.single().checks))
    }

    @Test fun sameCapturedDrawsDoNotTurnUnrecordedSlotsIntoLoss() {
        val r=rule("s6-04@2026-11-20-EVENING","11")
        val a=draw(first,listOf(r),listOf(r),"22")
        val b=draw(second,listOf(r),emptyList(),"33")
        val cohort=CherryComparableEvidence.cohort(listOf(b,a))
        assertEquals(CherryBornLifetime.Status.MISS,CherryBornLifetime.evaluate(cohort.raw).single().status)
        assertEquals(CherryBornLifetime.Status.UNRESOLVED,CherryBornLifetime.evaluate(cohort.offered).single().status)
    }

    @Test fun olderOnlyEvidenceRemainsSeparateAndWeeklyScopeUsesSameDraws() {
        val r=rule("s3-week","27")
        val old=draw(first,emptyList(),listOf(r),"27")
        val current=draw(second,listOf(r),listOf(r),"28")
        val cohort=CherryComparableEvidence.cohort(listOf(current,old))
        assertEquals(0,CherryWeeklyLifetime.evaluate(cohort.raw).single().distinctHits)
        assertEquals(0,CherryWeeklyLifetime.evaluate(cohort.offered).single().distinctHits)
        assertEquals(1,CherryWeeklyLifetime.evaluate(listOf(current,old)).single().distinctHits)
        val onlyOld=CherryComparableEvidence.cohort(listOf(old))
        assertTrue(onlyOld.raw.isEmpty())
        assertTrue(onlyOld.offered.isEmpty())
        assertEquals(listOf(old),onlyOld.withoutRaw)
    }
}
