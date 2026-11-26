package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryReviewedSuccessVersionTest {
    private val start=LocalDate.of(2026,11,23)
    private val zone=ZoneId.of("Asia/Yangon")
    private val version=CherrySuccessEngine.CURRENT_RULE_VERSION
    private val old="cherry-better-data-v3.2-success-v1"
    private fun snap(i:Int,version:String,vararg rules:CherrySuccessRule):CherrySuccessSnapshot {
        val date=start.plusDays(i.toLong())
        return CherrySuccessSnapshot(DrawIdentity(date,DrawSession.MORNING),
            date.atTime(11,30).atZone(zone).toInstant().toEpochMilli(),rules.toList(),version)
    }
    private fun pair(id:String,tab:CherryTab=CherryTab.CURRENT,n:String="12")=
        CherrySuccessRule(id,id,tab,CherrySuccessCriterion.PAIR,listOf(n))

    @Test fun reusedIdsNeverBlendOldAndNewSourceScores() {
        val legacy=snap(0,old,pair("s1-930"))
        val revised=snap(1,version,pair("s1-930"))
        val draws=CherrySuccessEngine.completed(listOf(legacy,revised),
            mapOf(legacy.target to "12",revised.target to "34"))
        assertEquals(2,draws.size)
        assertTrue(draws.first{it.snapshot==legacy}.checks.single().correct)
        val current=CherryLibrarySuccessBridge.scoredCompleted(draws,setOf("cherry-004")).getValue("cherry-004")
        assertEquals(1,current.eligible)
        assertEquals(0,current.correct)
        val archived=CherryLibrarySuccessBridge.scoredCompleted(draws,setOf("cherry-004"),legacyArchive=true).getValue("cherry-004")
        assertEquals(1,archived.eligible)
        assertEquals(1,archived.correct)
    }
    @Test fun newExactMappingsAndNoPartialOrWeeklyAwards() {
        val a=snap(0,version,pair("s1-930bk-v2"),pair("s6-10"),
            pair("s1-930bk"),pair("s3-w-017-9",CherryTab.BORN),
            pair("s6-04",CherryTab.BORN),pair("new-special-khant"),
            pair("s4-born-11",CherryTab.BORN))
        val ids=setOf("cherry-005","cherry-037","cherry-017","cherry-033","new-special-khant","cherry-021")
        val draws=CherrySuccessEngine.completed(listOf(a),mapOf(a.target to "12"))
        assertEquals(setOf("cherry-005","cherry-037"),
            CherryLibrarySuccessBridge.scoredCompleted(draws,ids).keys)
        assertTrue(CherryLibrarySuccessBridge.scoredCompleted(draws,ids,legacyArchive=true).isEmpty())
        assertTrue(CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(a),emptyMap()),ids).isEmpty())
        assertTrue(CherryLibrarySuccessBridge.isSupportedSource("category-born","cherry-037"))
        assertFalse(CherryLibrarySuccessBridge.isSupportedSource("category-born","cherry-033"))
    }
    @Test fun unknownVersionCannotEnterCurrentScores() {
        val a=snap(0,"unknown",pair("s1-930"))
        val draws=CherrySuccessEngine.completed(listOf(a),mapOf(a.target to "12"))
        assertEquals(1,draws.size)
        assertTrue(CherryLibrarySuccessBridge.scoredCompleted(draws,setOf("cherry-004")).isEmpty())
        assertTrue(CherryLibrarySuccessBridge.scoredCompleted(draws,setOf("cherry-004"),legacyArchive=true).isEmpty())
    }
    @Test fun rankingAndProofUseCurrentVersionButMathKeepsHistory() {
        fun math()=CherrySuccessRule("LONE:formula","Math",CherryTab.MATH,CherrySuccessCriterion.LONE,listOf("2"))
        val snaps=listOf(snap(0,old,pair("s1-930"),math()),
            snap(1,version,pair("s1-930"),math()),
            snap(2,version,pair("s1-930"),math()),
            snap(3,version,pair("s1-930"),math()))
        val draws=CherrySuccessEngine.completed(snaps,snaps.associate{it.target to if(it==snaps.first())"12" else "23"})
        val ranks=CherrySuccessEngine.ranking(draws)
        assertEquals(3,ranks.single{it.tab==CherryTab.CURRENT}.evaluated)
        assertEquals(0,ranks.single{it.tab==CherryTab.CURRENT}.correct)
        assertEquals(4,ranks.single{it.tab==CherryTab.MATH}.evaluated)
        assertEquals(3,draws.sumOf{d->d.checks.count{it.rule.tab==CherryTab.CURRENT&&CherrySuccessEngine.isCurrentRuleEvidence(d,it)}})
        assertEquals(4,CherrySuccessEngine.tabRate(draws,CherryTab.CURRENT).evaluated)
    }
}
