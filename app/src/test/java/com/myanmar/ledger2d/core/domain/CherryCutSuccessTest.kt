package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryCutSuccessTest {
    private val date=LocalDate.of(2026,11,23)
    private val am=DrawSession.MORNING
    private val pm=DrawSession.EVENING
    private val target=DrawIdentity(date,pm)
    private val time=date.atTime(15,0).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()
    private fun report(pair:String="44")=CherryBetterDataEngine.calculate(
        listOf(HistoricDraw(date,am,pair,HistoricSource.WINNER)),target)
    private fun snap(report:CherryReport=report())=
        CherrySuccessEngine.createSnapshot(report,emptyList(),time)
    private fun checked(snap:CherrySuccessSnapshot,winner:String)=
        requireNotNull(CherrySuccessEngine.evaluate(snap,winner))

    @Test fun allFourCutsMatchActualEngineAndOverlapCountsSeparately() {
        val r=report()
        val cuts=r.cutEvidence.associateBy{it.id}
        assertEquals(setOf("cut-tail-plus7","cut-double-digit","cut-morning-head","cut-dead-triples"),cuts.keys)
        assertEquals(CherryPairExpander.tail(1).toSet(),cuts.getValue("cut-tail-plus7").excluded.toSet())
        assertEquals(CherryPairExpander.any(4).toSet(),cuts.getValue("cut-double-digit").excluded.toSet())
        assertEquals(cuts.getValue("cut-double-digit").excluded,cuts.getValue("cut-morning-head").excluded)
        assertEquals(setOf("92","29","93","39","23","32"),cuts.getValue("cut-dead-triples").excluded.toSet())
        assertEquals(r.cuts.keys,cuts.values.flatMap{it.excluded}.toSet())
        cuts.values.forEach{cut->assertTrue(cut.excluded.all{cut.detail in r.cuts.getValue(it)})}
        val result=CherryCutSuccess.checks(checked(snap(r),"41")).associateBy{it.rule.id}
        assertFalse(result.getValue("cut-tail-plus7").correct)
        assertFalse(result.getValue("cut-double-digit").correct)
        assertFalse(result.getValue("cut-morning-head").correct)
        assertTrue(result.getValue("cut-dead-triples").correct)
        val scores=CherryCutSuccess.scores(listOf(checked(snap(r),"41"),checked(snap(r),"41")))
        assertTrue(scores.all{it.evaluated==1})
    }
    @Test fun noPriorNoCutsAndMorningHasNoMorningHeadCriterion() {
        val morning=DrawIdentity(date,am)
        assertTrue(CherryBetterDataEngine.calculate(emptyList(),morning).cutEvidence.isEmpty())
        val friday=date.minusDays(3)
        val r=CherryBetterDataEngine.calculate(
            listOf(HistoricDraw(friday,pm,"23",HistoricSource.WINNER)),morning)
        assertEquals(setOf("cut-tail-plus7","cut-dead-triples"),r.cutEvidence.map{it.id}.toSet())
        assertFalse(report("23").cutEvidence.any{it.id=="cut-double-digit"})
    }
    @Test fun rawIncludesFullyExcludedRuleButOfferedDoesNotAndNoRankLeak() {
        val r=report()
        val pick=r.current.first{it.cuts.isNotEmpty()}
        val isolated=r.copy(current=listOf(pick),born=emptyList())
        val stored=snap(isolated)
        assertTrue(stored.rules.isEmpty())
        assertTrue(stored.rawRules.isNotEmpty())
        assertTrue(stored.rawRules.all{it.predictions==listOf(pick.pair)})
        val draw=checked(stored,pick.pair)
        assertTrue(draw.checks.isEmpty())
        assertTrue(CherryCutSuccess.rawView(draw)!!.checks.all{it.correct})
        assertEquals(0,CherrySuccessEngine.tabRate(listOf(draw),CherryTab.CURRENT).evaluated)
        assertTrue(CherrySuccessEngine.ranking(listOf(draw),minimumEvaluated=1).isEmpty())
    }
    @Test fun rawAndOfferedBornLifetimeUseSeparateEvidence() {
        val r=report("44")
        val birth=r.activeRules.first { it.produced.any{p->p in r.cuts} }
        val pair=birth.produced.first{it in r.cuts}
        val stored=snap(r)
        val draw=checked(stored,pair)
        val raw=CherryCutSuccess.rawView(draw)!!
        assertTrue(raw.checks.any{it.rule.id==birth.id&&it.correct})
        assertFalse(draw.checks.any{it.rule.id==birth.id&&it.correct})
        assertEquals(CherryBornLifetime.Status.HIT,
            CherryBornLifetime.evaluate(listOf(raw)).single{it.id==birth.id}.status)
        assertFalse(CherryBornLifetime.evaluate(listOf(draw)).any{
            it.id==birth.id&&it.status==CherryBornLifetime.Status.HIT})
    }
    @Test fun correctionsReevaluateFrozenEvidenceAndOldOrLateDoNotBackfill() {
        val stored=snap()
        val first=CherryCutSuccess.checks(checked(stored,"41")).single{it.rule.id=="cut-tail-plus7"}
        val revised=CherryCutSuccess.checks(checked(stored,"80")).single{it.rule.id=="cut-tail-plus7"}
        assertFalse(first.correct)
        assertTrue(revised.correct)
        assertEquals(first.rule,revised.rule)
        val old=stored.copy(rawRules=emptyList(),cutRules=emptyList())
        assertTrue(CherryCutSuccess.checks(checked(old,"41")).isEmpty())
        assertNull(CherryCutSuccess.rawView(checked(old,"41")))
        val unknown=stored.copy(sourceVersion="old")
        assertTrue(CherryCutSuccess.checks(checked(unknown,"41")).isEmpty())
        assertNull(CherryCutSuccess.rawView(checked(unknown,"41")))
        assertTrue(CherrySuccessEngine.completed(listOf(stored),emptyMap()).isEmpty())
        assertNull(CherrySuccessEngine.evaluate(stored.copy(capturedAtMillis=
            date.atTime(16,30).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()),"41"))
    }
}
