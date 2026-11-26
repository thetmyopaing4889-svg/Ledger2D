package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.database.WinningNumberEntity
import com.myanmar.ledger2d.core.database.ManualWinnerLockEntity
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryLibraryHistoryParityTest {
    private val day=LocalDate.of(2026,11,23)
    private val am=DrawSession.MORNING
    private val target=DrawIdentity(day,am)
    private val stamp=day.atTime(11,0).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()
    private val rule=CherrySuccessRule("s1-day-head-tail","rule",CherryTab.CURRENT,
        CherrySuccessCriterion.PAIR,listOf("12"))
    private val snapshot=CherrySuccessSnapshot(target,stamp,listOf(rule),
        CherrySuccessEngine.CURRENT_RULE_VERSION,rawRules=listOf(rule))
    private val allowed=setOf("cherry-001")

    private fun daily(date:LocalDate,n:String)=LiveDailyResultEntity(
        date=date,modern930=null,internet930=null,modern200=null,internet200=null,
        morning2d=n,morningSet=null,morningValue=null,evening2d=null,
        eveningSet=null,eveningValue=null,reference930SourceAt=null,
        reference200SourceAt=null,morningSourceAt=null,eveningSourceAt=null,updatedAt=1L)
    private fun winner(n:String)=WinningNumberEntity(
        date=day,session=am,digit=n,createdAt=0,updatedAt=0)
    private fun resolve(rows:List<LiveDailyResultEntity>,winners:List<WinningNumberEntity> = emptyList(),
                        locks:List<ManualWinnerLockEntity> = emptyList(),
                        closed:Set<LocalDate> = emptySet())=
        Historic2DDataSource.resolve(rows,emptyList(),day.plusDays(1),
            LocalTime.of(17,0),winners,locks,cherryClosedDates=closed).draws

    private fun assertParity(rows:List<HistoricDraw>,expected:Int?) {
        val completed=CherrySuccessEngine.completed(listOf(snapshot),CherryVerifiedHistory.results(rows))
        val offered=CherryLibrarySuccessBridge.scored(listOf(snapshot),rows,allowed)
        val raw=CherryLibrarySourceEvidence.scored(listOf(snapshot),rows,allowed)
        if(expected==null) {
            assertTrue(completed.isEmpty())
            assertTrue(offered.isEmpty())
            assertTrue(raw.isEmpty())
        } else {
            assertEquals(expected,completed.single().checks.count{it.correct})
            assertEquals(expected,offered.getValue("cherry-001").correct)
            assertEquals(expected,raw.getValue("cherry-001").single().correct)
            assertEquals(1,offered.getValue("cherry-001").eligible)
            assertEquals(1,raw.getValue("cherry-001").single().evaluated)
        }
        assertEquals(offered,CherryLibrarySuccessBridge.scoredCompleted(completed,allowed))
        assertEquals(raw,CherryLibrarySourceEvidence.scoredCompleted(completed,allowed))
    }

    @Test fun conflictingDailyResultsWithheldBeforeSharedMerge() {
        val rows=listOf(daily(day,"12"),daily(day,"34"))
        assertParity(resolve(rows),null)
        assertParity(resolve(rows.reversed()),null)
        assertParity(resolve(listOf(daily(day,"12"),daily(day,"12"))),1)
        // Other analysis and Research consumers retain their existing projection.
        val legacy=Historic2DDataSource.resolve(rows,emptyList(),day.plusDays(1))
        assertEquals(1,legacy.draws.size)
    }

    @Test fun correctedWinnerAndManualDeletionAreConsistent() {
        val rows=listOf(daily(day,"12"),daily(day,"34"))
        assertParity(resolve(rows,listOf(winner("12"))),1)
        assertParity(resolve(rows,listOf(winner("34"))),0)
        assertParity(resolve(rows,listOf(winner("12"),winner("34"))),null)
        val lock=ManualWinnerLockEntity(date=day,session=am,createdAt=0,updatedAt=0)
        assertParity(resolve(rows,locks=listOf(lock)),null)
        assertParity(resolve(rows,listOf(winner("12")),listOf(lock)),1)
        assertParity(resolve(listOf(daily(day,"12")),listOf(winner("?")),listOf(lock)),null)
        assertParity(resolve(listOf(daily(day,"12")),listOf(winner("12"),winner("34")),listOf(lock)),null)
    }

    @Test fun weekendsClosedDatesAndUnconfirmedTodayAreExcluded() {
        val rows=listOf(daily(day,"12"),daily(day.minusDays(1),"56"))
        assertParity(resolve(rows,closed=setOf(day)),null)
        assertTrue(resolve(rows,closed=setOf(day)).isEmpty())
        val today=Historic2DDataSource.resolve(listOf(daily(day,"12")),emptyList(),
            day,LocalTime.of(17,0),cherryClosedDates=emptySet()).draws
        assertParity(today,null)
    }

    @Test fun publicScoringWrappersRejectConflictingSameSourceInputs() {
        val rows=listOf(HistoricDraw(day,am,"12",HistoricSource.WINNER),
            HistoricDraw(day,am,"34",HistoricSource.WINNER))
        assertParity(rows,null)
    }
}
