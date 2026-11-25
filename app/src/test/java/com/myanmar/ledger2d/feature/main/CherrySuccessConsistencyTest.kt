package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherrySuccessConsistencyTest {
    @Test fun exactPairAndSeparateLabelsUseSameArchivedRuleIds() {
        val date=LocalDate.of(2026,11,25)
        val target=DrawIdentity(date,DrawSession.MORNING)
        val prior=HistoricDraw(date.minusDays(1),DrawSession.EVENING,"53",HistoricSource.WINNER)
        val report=CherryBetterDataEngine.calculate(listOf(prior),target)
        val time=date.atTime(11,57).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli()
        val snap=CherrySuccessEngine.createSnapshot(report,emptyList(),time)
        val checks=CherrySuccessEngine.evaluate(snap,"97")!!.forTab(CherryTab.CURRENT)
        val byId=checks.associateBy { it.rule.id }
        assertEquals("3 လုံးမှ 7 ပတ်သီးမူ",CherryRuleWording.title("s2-p-3",byId.getValue("s2-p-3").rule.name))
        assertEquals("5 လုံးမှ 9 ပတ်သီးမူ",CherryRuleWording.title("s2-p-5",byId.getValue("s2-p-5").rule.name))
        assertTrue(byId.getValue("s2-p-3").rule.detail.isNotBlank())
        assertTrue(byId.getValue("s2-p-5").rule.detail.isNotBlank())
        for(pair in listOf("79","97")) {
            val displayed=report.current.firstOrNull { it.pair==pair && it.cuts.isEmpty() }
            val archived=snap.rules.filter { it.tab==CherryTab.CURRENT && pair in it.predictions }
            assertEquals(displayed?.evidence?.map { it.id }?.distinct()?.toSet().orEmpty(),
                archived.map { it.id }.toSet())
        }
        assertFalse(snap.rules.any { it.id.startsWith("s1-930") })
        val with930=CherryBetterDataEngine.calculate(listOf(prior),target,"11","12")
        assertTrue(CherrySuccessEngine.createSnapshot(with930,emptyList(),time).rules.any { it.id=="s1-930" })
    }
}
