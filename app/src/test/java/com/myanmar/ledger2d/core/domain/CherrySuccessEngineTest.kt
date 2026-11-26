package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherrySuccessEngineTest {
    private val zone = ZoneId.of("Asia/Yangon")
    private val morning = DrawIdentity(LocalDate.of(2026, 11, 16), DrawSession.MORNING)
    private val evening = DrawIdentity(LocalDate.of(2026, 11, 16), DrawSession.EVENING)
    private fun millis(dateTime: LocalDateTime) =
        dateTime.atZone(zone).toInstant().toEpochMilli()
    private val before = millis(LocalDateTime.of(2026, 11, 16, 11, 40))

    private fun rule(id: String, tab: CherryTab, kind: CherrySuccessCriterion,
                     vararg predictions: String) =
        CherrySuccessRule(id, id, tab, kind, predictions.toList())

    @Test fun cutoffIsStrictAndUsesYangonSessionBoundary() {
        assertTrue(CherrySuccessEngine.beforeCutoff(morning, before))
        assertFalse(CherrySuccessEngine.beforeCutoff(morning,
            millis(LocalDateTime.of(2026, 11, 16, 12, 0))))
        assertTrue(CherrySuccessEngine.beforeCutoff(evening,
            millis(LocalDateTime.of(2026, 11, 16, 16, 29))))
        assertFalse(CherrySuccessEngine.beforeCutoff(evening,
            millis(LocalDateTime.of(2026, 11, 16, 16, 30))))
    }

    @Test fun exactPairAndAllThreeMathKindsUseDistinctDefinitions() {
        val sample = CherrySuccessSnapshot(morning, before, listOf(
            rule("pair80", CherryTab.CURRENT, CherrySuccessCriterion.PAIR, "08", "80"),
            rule("reverse-only", CherryTab.BORN, CherrySuccessCriterion.PAIR, "08"),
            rule("lone", CherryTab.MATH, CherrySuccessCriterion.LONE, "8"),
            rule("wam", CherryTab.MATH, CherrySuccessCriterion.WAM, "3"),
            rule("break", CherryTab.MATH, CherrySuccessCriterion.BREAK, "8"),
        ))
        val checks = requireNotNull(CherrySuccessEngine.evaluate(sample, "80")).checks
        assertEquals(listOf(true, false, true, true, true), checks.map { it.correct })
        assertNull(CherrySuccessEngine.evaluate(sample, "not-result"))
        assertNull(CherrySuccessEngine.evaluate(
            sample.copy(capturedAtMillis=millis(LocalDateTime.of(2026, 11, 16, 12, 1))),
            "80"))
    }

    @Test fun removingChosenMathKindFromNextSnapshotClearsOnlyThatKind() {
        val report = CherryBetterDataEngine.calculate(
            listOf(HistoricDraw(LocalDate.of(2026,11,13),DrawSession.EVENING,
                "80",HistoricSource.WINNER)), morning)
        val lone = CherryV3FormulaSelection(CherryDiscoveryKind.LONE,2,5,"lone-formula")
        val br = CherryV3FormulaSelection(CherryDiscoveryKind.BREAK,8,4,"break-formula")
        val original = CherrySuccessEngine.createSnapshot(report,listOf(lone,br),before)
        val changed = CherrySuccessEngine.createSnapshot(report,listOf(br),before+1000)
        assertEquals(2,original.rules.count { it.tab==CherryTab.MATH })
        assertEquals(1,changed.rules.count { it.tab==CherryTab.MATH })
        assertTrue(changed.rules.any { it.id=="BREAK:break-formula" })
        assertFalse(changed.rules.any { it.id=="LONE:lone-formula" })
        assertEquals(original.rules.filter { it.tab!=CherryTab.MATH },
            changed.rules.filter { it.tab!=CherryTab.MATH })
    }

    @Test fun missingResultAndMissingCategoryNeverCountAsLoss() {
        val snap = CherrySuccessSnapshot(morning, before, listOf(
            rule("pair", CherryTab.CURRENT, CherrySuccessCriterion.PAIR, "80")))
        val draws = CherrySuccessEngine.completed(listOf(snap), emptyMap())
        assertTrue(draws.isEmpty())
        val verified = CherrySuccessEngine.completed(listOf(snap), mapOf(morning to "80"))
        assertEquals(CherrySuccessRate(1, 1), CherrySuccessEngine.tabRate(
            verified, CherryTab.CURRENT))
        assertEquals(CherrySuccessRate(0, 0), CherrySuccessEngine.tabRate(
            verified, CherryTab.BORN))
        assertEquals(CherrySuccessRate(0, 0), CherrySuccessEngine.tabRate(
            verified, CherryTab.MATH))
    }

    @Test fun snapshotKeepsRuleSourcesInputsAndOnlyExplicitMathSelections() {
        val prior = HistoricDraw(LocalDate.of(2026,11,13),
            DrawSession.EVENING,"80",HistoricSource.WINNER)
        val report = CherryBetterDataEngine.calculate(listOf(prior),morning)
        val selected = CherryV3FormulaSelection(
            CherryDiscoveryKind.BREAK,8,4,"v4:10+0@BREAK")
        val snap = CherrySuccessEngine.createSnapshot(
            report,listOf(selected),before,"modern930=23;internet930=24")
        assertEquals(morning,snap.target)
        assertEquals(CherrySuccessEngine.CURRENT_RULE_VERSION,snap.sourceVersion)
        assertTrue(snap.inputEvidence.contains("modern930=23"))
        assertTrue(snap.rules.any { it.tab==CherryTab.CURRENT })
        assertTrue(snap.rules.any { it.tab==CherryTab.BORN })
        assertEquals(1,snap.rules.count { it.tab==CherryTab.MATH })
        assertTrue(snap.rules.none { it.tab==CherryTab.SUMMARY })
    }

    @Test fun correctedWinnerRecomputesWithoutChangingLockedSnapshot() {
        val snap = CherrySuccessSnapshot(morning, before, listOf(
            rule("pair", CherryTab.CURRENT, CherrySuccessCriterion.PAIR, "80")))
        val original = CherrySuccessEngine.completed(listOf(snap), mapOf(morning to "80"))
        val revised = CherrySuccessEngine.completed(listOf(snap), mapOf(morning to "08"))
        assertEquals(1, CherrySuccessEngine.tabRate(original, CherryTab.CURRENT).correct)
        assertEquals(0, CherrySuccessEngine.tabRate(revised, CherryTab.CURRENT).correct)
        assertEquals(snap, revised.single().snapshot)
    }

    @Test fun rankingNeedsSampleAndReportsCurrentConsecutiveStreak() {
        val snapshots = (0..3).map { i ->
            CherrySuccessSnapshot(morning.copy(date=morning.date.minusDays(i.toLong())),
                before - i * 86_400_000L,
                listOf(rule("repeat", CherryTab.BORN, CherrySuccessCriterion.PAIR, "80")),
                sourceVersion=CherrySuccessEngine.CURRENT_RULE_VERSION)
        }
        val confirmed = snapshots.associate { it.target to if (it.target.date == morning.date.minusDays(3)) "08" else "80" }
        val scored = CherrySuccessEngine.completed(snapshots, confirmed)
        val rank = CherrySuccessEngine.ranking(scored).single()
        assertEquals(3, rank.correct)
        assertEquals(4, rank.evaluated)
        assertEquals(3, rank.currentStreak)
        assertTrue(CherrySuccessEngine.ranking(scored.take(2)).isEmpty())
    }
}
