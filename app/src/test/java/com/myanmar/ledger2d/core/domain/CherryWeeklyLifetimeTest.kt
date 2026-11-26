package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryWeeklyLifetimeTest {
    private val monday=LocalDate.of(2026,11,23)
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private fun slot(day:Long,session:DrawSession=am)=DrawIdentity(monday.plusDays(day),session)
    private val slots=listOf(slot(0,pm),slot(1),slot(1,pm),slot(2))
    private fun snap(target:DrawIdentity,id:String,pair:String,expected:List<DrawIdentity>,
                     version:String=CherrySuccessEngine.CURRENT_RULE_VERSION)=
        CherrySuccessSnapshot(target,target.date.atTime(10,30).atZone(ZoneId.of("Asia/Yangon"))
            .toInstant().toEpochMilli(),listOf(CherrySuccessRule(id,id,CherryTab.BORN,
                CherrySuccessCriterion.PAIR,listOf(pair),eligibleSlots=expected)),version)
    private fun eval(vararg items:Pair<CherrySuccessSnapshot,String>)=
        CherryWeeklyLifetime.evaluate(CherrySuccessEngine.completed(
            items.map{it.first},items.associate{it.first.target to it.second}))

    @Test fun sixDistinctFollowUpsTwoLowerThreeCompleteRepeatedNotCounted() {
        val id="s3-six"
        val a=snap(slots[0],id,"38",slots) to "38"
        val b=snap(slots[1],id,"38",slots) to "38"
        val c=snap(slots[2],id,"39",slots) to "39"
        val d=snap(slots[3],id,"48",slots) to "48"
        assertEquals(1,eval(a,b).single().distinctHits)
        assertEquals(CherryWeeklyLifetime.Status.LOWER_TARGET,eval(a,b,c).single().status)
        assertEquals(3,eval(a,b,c,d).single().distinctHits)
        assertEquals(CherryWeeklyLifetime.Status.COMPLETE,eval(a,b,c,d).single().status)
    }
    @Test fun eightPairCoverageNoImplicitWholeSourceLoss() {
        val id="s3-week"
        val a=snap(slots[0],id,"27",slots) to "27"
        val b=snap(slots[1],id,"82",slots) to "28"
        val c=snap(slots[2],id,"48",slots) to "84"
        val d=snap(slots[3],id,"54",slots) to "45"
        val incomplete=eval(a,d).single()
        assertEquals(1,incomplete.distinctHits)
        assertEquals(8,incomplete.goal)
        assertEquals(CherryWeeklyLifetime.Status.UNRESOLVED,incomplete.status)
        assertEquals(CherryWeeklyLifetime.Status.RECORDED_WINDOW_END,eval(a,b,c,d).single().status)
    }
    @Test fun weeklyIdentityResetsAndFirstHitNeedsNoLaterSnapshot() {
        for(id in listOf("new-groups-weekly-nine","new-groups-weekly-break","new-groups-weekly-wheel")) {
            val later=slot(7)
            val result=eval(snap(slots[0],id,"19",slots) to "19",
                snap(later,id,"19",listOf(later,slot(7,pm))) to "22")
            assertEquals(2,result.size)
            assertEquals(CherryWeeklyLifetime.Status.UNRESOLVED,result.first().status)
            assertEquals(CherryWeeklyLifetime.Status.COMPLETE,result.last().status)
        }
    }
    @Test fun unknownOldMissingOrCrossWeekMetadataCannotCount() {
        val target=slots[0]
        assertTrue(eval(snap(target,"s3-week","27",emptyList()) to "27").isEmpty())
        assertTrue(eval(snap(target,"s3-week","27",slots,version="old") to "27").isEmpty())
        assertTrue(eval(snap(target,"s3-week","27",slots+slot(7)) to "27").isEmpty())
        assertTrue(eval(snap(target,"s6-04","27",slots) to "27").isEmpty())
    }
    @Test fun frozenEngineSlotsUseHolidaysAndSixStartsAfterTrigger() {
        val origin=HistoricDraw(monday,am,"34",HistoricSource.WINNER)
        val target=slot(0,pm)
        val closed=setOf(monday.plusDays(1))
        val report=CherryBetterDataEngine.calculate(listOf(origin),target,closed=closed)
        assertEquals(8,report.weeklySlots.getValue("s3-week").size)
        assertEquals(7,report.weeklySlots.getValue("s3-six").size)
        assertEquals(target,report.weeklySlots.getValue("s3-six").first())
        assertTrue(report.weeklySlots.values.flatten().none{it.date in closed})
        val captured=CherrySuccessEngine.createSnapshot(report,emptyList(),
            monday.atTime(11,30).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli())
        assertEquals(report.weeklySlots.getValue("s3-week"),
            captured.rules.single{it.id=="s3-week"}.eligibleSlots)
        assertEquals(report.weeklySlots.getValue("s3-six"),
            captured.rules.single{it.id=="s3-six"}.eligibleSlots)
        val next=CherryBetterDataEngine.calculate(listOf(origin),slot(7))
        assertFalse("s3-six" in next.weeklySlots)
        assertEquals(10,next.weeklySlots.getValue("s3-week").size)
    }
}
