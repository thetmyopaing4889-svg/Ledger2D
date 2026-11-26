package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryLibrarySourceEvidenceTest {
    private val date=LocalDate.of(2026,11,23)
    private val target=DrawIdentity(date,DrawSession.EVENING)
    private val time=date.atTime(15,0).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()
    private val version=CherrySuccessEngine.CURRENT_RULE_VERSION
    private fun pair(id:String,tab:CherryTab,n:String,slots:List<DrawIdentity> = emptyList())=
        CherrySuccessRule(id,id,tab,CherrySuccessCriterion.PAIR,listOf(n),eligibleSlots=slots)
    private fun completed(snap:CherrySuccessSnapshot,n:String)=
        CherrySuccessEngine.completed(listOf(snap),mapOf(target to n))
    @Test fun all98SourcesHaveExplicitDispositionAndNoExcludedMapping() {
        val text=java.io.File("src/main/assets/two_d_rules_reader.json").readText(Charsets.UTF_8)
        val ids=Regex("\"id\"\\s*:\\s*\"(cherry-[0-9]{3}|new-[a-z0-9-]+)\"")
            .findAll(text).map{it.groupValues[1]}.toSet()
        val supported=ids.filter{CherryLibrarySourceEvidence.supported(it) ||
            CherryLibrarySourceEvidence.supported(it+"#wheel") ||
            CherryLibrarySourceEvidence.supported(it+"#born")}
        assertEquals(98,ids.size)
        assertEquals(78,supported.size)
        assertFalse(CherryLibrarySourceEvidence.supported("new-special-khant"))
        assertFalse(CherryLibrarySourceEvidence.supported("cherry-006"))
        assertNull(CherryLibrarySourceEvidence.sourceKey("s6-04-unrelated"))
        assertEquals("cherry-021#wheel",CherryLibrarySourceEvidence.sourceKey("s4-wheel-00-1@2026-11-23-MORNING"))
        assertEquals("cherry-021#born",CherryLibrarySourceEvidence.sourceKey("s4-born-44@2026-11-23-MORNING"))
    }
    @Test fun sourceComponentsNeverMergeBirthDrawAndCutAndOldEvidenceIsAbsent() {
        val id="s6-92@2026-11-23-MORNING"
        val raw=listOf(pair(id,CherryTab.BORN,"14",listOf(target)),
            pair("s6-am92",CherryTab.CURRENT,"14"),
            pair("s2-p-1",CherryTab.CURRENT,"14"))
        val cut=CherryCutEvidence("cut-tail-plus7","cut",listOf("14"),
            DrawIdentity(date,DrawSession.MORNING),"detail")
        val snap=CherrySuccessSnapshot(target,time,emptyList(),version,rawRules=raw,cutRules=listOf(cut))
        val score=CherryLibrarySourceEvidence.scoredCompleted(completed(snap,"14"),setOf("cherry-056","cherry-008"))
        assertEquals(setOf(CherryLibrarySourceEvidence.Metric.DRAW,CherryLibrarySourceEvidence.Metric.BIRTH),
            score.getValue("cherry-056").map{it.metric}.toSet())
        assertTrue(score.getValue("cherry-056").all{it.correct==1&&it.evaluated==1})
        assertEquals(0,score.getValue("cherry-008").single{it.metric==CherryLibrarySourceEvidence.Metric.CUT}.correct)
        assertEquals(1,score.getValue("cherry-008").single{it.metric==CherryLibrarySourceEvidence.Metric.DRAW}.correct)
        assertTrue(CherryLibrarySuccessBridge.scoredCompleted(completed(snap,"14"),setOf("cherry-056","cherry-008")).isEmpty())
        assertTrue(CherryLibrarySourceEvidence.scoredCompleted(completed(snap.copy(rawRules=emptyList(),cutRules=emptyList()),"14"),
            setOf("cherry-056","cherry-008")).isEmpty())
    }
    @Test fun weeklyCoverageDoesNotCreateWholeSourceWinAndUnresolvedBirthIsNotLoss() {
        val raw=listOf(pair("s3-week",CherryTab.BORN,"27",listOf(target)),
            pair("s6-04@2026-11-23-MORNING",CherryTab.BORN,"11",
                listOf(target,DrawIdentity(date.plusDays(1),DrawSession.MORNING))))
        val snap=CherrySuccessSnapshot(target,time,emptyList(),version,rawRules=raw)
        val score=CherryLibrarySourceEvidence.scoredCompleted(completed(snap,"27"),setOf("cherry-020","cherry-033"))
        val week=score.getValue("cherry-020").single()
        assertEquals(0,week.evaluated)
        assertEquals("1/8",week.events.single().label)
        val birth=score.getValue("cherry-033").single()
        assertEquals(0,birth.evaluated)
        assertEquals(1,birth.unresolved)
    }
}
