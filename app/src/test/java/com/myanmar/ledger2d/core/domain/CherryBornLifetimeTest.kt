package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryBornLifetimeTest {
    private val date=LocalDate.of(2026,11,23)
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private val a=DrawIdentity(date,pm)
    private val b=DrawIdentity(date.plusDays(1),am)
    private val c=DrawIdentity(date.plusDays(1),pm)
    private val slots=listOf(a,b,c)
    private val id="s6-04@2026-11-23-MORNING"
    private fun rule(id:String=this.id,slots:List<DrawIdentity> = this.slots,n:String="11")=
        CherrySuccessRule(id,id,CherryTab.BORN,CherrySuccessCriterion.PAIR,listOf(n),eligibleSlots=slots)
    private fun snap(target:DrawIdentity,vararg rules:CherrySuccessRule,
                     version:String=CherrySuccessEngine.CURRENT_RULE_VERSION)=
        CherrySuccessSnapshot(target,target.date.atTime(10,30).atZone(ZoneId.of("Asia/Yangon"))
            .toInstant().toEpochMilli(),rules.toList(),version)
    private fun draws(vararg pairs:Pair<CherrySuccessSnapshot,String>)=
        CherrySuccessEngine.completed(pairs.map{it.first},pairs.associate{it.first.target to it.second})

    @Test fun oneEpisodeNotThreeDrawLossesAndMissingSlotsStayUnresolved() {
        val first=snap(a,rule())
        val second=snap(b,rule())
        val third=snap(c,rule())
        val incomplete=CherryBornLifetime.evaluate(draws(first to "22",third to "33")).single()
        assertEquals(CherryBornLifetime.Status.UNRESOLVED,incomplete.status)
        assertEquals(2,incomplete.recorded)
        assertEquals(3,incomplete.expected)
        assertEquals(CherryBornLifetime.Status.MISS,
            CherryBornLifetime.evaluate(draws(first to "22",second to "22",third to "33")).single().status)
        assertEquals(CherryBornLifetime.Status.HIT,
            CherryBornLifetime.evaluate(draws(second to "11")).single().status)
    }
    @Test fun oldOrMissingMetadataAndUnconfirmedNeverBackfill() {
        val old=snap(a,rule(),version="old")
        val noMetadata=snap(b,rule(slots=emptyList()))
        assertTrue(CherryBornLifetime.evaluate(draws(old to "11",noMetadata to "11")).isEmpty())
        assertTrue(CherryBornLifetime.evaluate(CherrySuccessEngine.completed(listOf(snap(a,rule())),emptyMap())).isEmpty())
    }
    @Test fun changedScheduleCannotBecomeCompleteMiss() {
        val first=snap(a,rule(slots=listOf(a,b)))
        val second=snap(b,rule(slots=listOf(b)))
        assertEquals(CherryBornLifetime.Status.UNRESOLVED,
            CherryBornLifetime.evaluate(draws(first to "22",second to "33")).single().status)
    }
    @Test fun bornRankMergesOriginsOncePerDrawAndProofMatches() {
        val first=snap(a,rule(),rule(id="s6-04@2026-11-22-EVENING",n="22"))
        val second=snap(b,rule())
        val records=draws(first to "22",second to "33")
        val rank=CherrySuccessEngine.ranking(records,minimumEvaluated=1).single()
        assertEquals("BORN:s6-04",rank.id)
        assertEquals(2,rank.evaluated)
        assertEquals(1,rank.correct)
        val proof=CherrySuccessEngine.rankEvidence(records,rank.id)
        assertEquals(2,proof.size)
        assertEquals(2,proof.last().pairCount)
        assertEquals(1,proof.count{it.correct})
    }
    @Test fun actualEngineFreezesStartEndAndExactFourSlots() {
        fun h(date:LocalDate,session:DrawSession,pair:String)=HistoricDraw(date,session,pair,HistoricSource.WINNER)
        val report=CherryBetterDataEngine.calculate(listOf(h(date,am,"75")),b)
        val birth=report.activeRules.single{it.id.startsWith("new-born-75@")}
        assertEquals(listOf(b,c,DrawIdentity(date.plusDays(2),am),DrawIdentity(date.plusDays(2),pm)),birth.eligibleSlots)
        val four=CherryBetterDataEngine.calculate(listOf(h(date,pm,"06")),b)
        val fourBirth=four.activeRules.single{it.id.startsWith("s6-06@")}
        assertEquals(4,fourBirth.eligibleSlots.size)
        assertEquals(DrawIdentity(date.plusDays(2),pm),fourBirth.eligibleSlots.last())
        val captured=CherrySuccessEngine.createSnapshot(report,emptyList(),
            b.date.atTime(10,30).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli())
        assertEquals(birth.eligibleSlots,captured.rules.single{it.id==birth.id}.eligibleSlots)
    }
    @Test fun currentCategoryExcludesOldButHistoryStillAccessible() {
        val old=snap(a,rule(),version="old")
        val fresh=snap(b,rule())
        val records=draws(old to "11",fresh to "22")
        assertEquals(CherrySuccessRate(0,1),CherrySuccessEngine.tabRate(records,CherryTab.BORN,true))
        assertEquals(CherrySuccessRate(1,2),CherrySuccessEngine.tabRate(records,CherryTab.BORN))
    }
}
