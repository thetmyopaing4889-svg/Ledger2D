package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CherryLibrarySuccessBridgeTest {
    private val zone = ZoneId.of("Asia/Yangon")
    private val start = LocalDate.of(2026, 11, 16)
    private fun draw(i: Int, session: DrawSession = DrawSession.MORNING) =
        DrawIdentity(start.plusDays(i.toLong()), session)

    private fun snapshot(target: DrawIdentity, vararg rules: CherrySuccessRule) =
        CherrySuccessSnapshot(
            target,
            target.date.atTime(10, 30).atZone(zone).toInstant().toEpochMilli(),
            rules.toList(),
        )
    private fun pair(id: String, tab: CherryTab, vararg pairs: String) =
        CherrySuccessRule(id, id, tab, CherrySuccessCriterion.PAIR, pairs.toList())

    private val known = setOf(
        "new-mob-double", "new-bk-six", "new-born-75",
        "cherry-038", "cherry-021", "cherry-017",
    )

    @Test fun sevenEligibleDrawsProduceThreeWinsEvenWhenFourMissedFirst() {
        val stored = (0..6).map { i ->
            snapshot(draw(i), pair("new-mob-double", CherryTab.CURRENT, "11"))
        }
        val winners = (0..6).associate { i ->
            draw(i) to if (i in setOf(2, 3, 6)) "11" else "34"
        }
        val scores = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(stored, winners), known)
        val stat = requireNotNull(scores["new-mob-double"])
        assertEquals(7, stat.eligible)
        assertEquals(3, stat.correct)
        assertEquals(42.9, stat.percent, 0.01)
        assertEquals(3, stat.events.count { it.correct })
        assertEquals(1, CherryLibrarySuccessBridge.successful(scores).size)
    }

    @Test fun onlyMatchedAndEligibleDrawsCountMissingEvidenceNeverAFalseLoss() {
        val stored = listOf(
            snapshot(draw(0), pair("new-bk-six", CherryTab.CURRENT, "16")),
            snapshot(draw(1), pair("new-bk-six", CherryTab.CURRENT, "16")),
            snapshot(draw(2), pair("new-bk-six", CherryTab.CURRENT, "16")),
            snapshot(draw(3), pair("new-bk-six", CherryTab.CURRENT, "16")),
        )
        val knownResults = (0..5).associate { draw(it) to "22" }
        val actual = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(stored, knownResults), known)
        assertEquals(0, actual.getValue("new-bk-six").correct)
        assertEquals(4, actual.getValue("new-bk-six").eligible)
        assertTrue(CherryLibrarySuccessBridge.successful(actual).isEmpty())
        val noResults = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(stored, emptyMap()), known)
        assertTrue(noResults.isEmpty())
    }

    @Test fun twoRulesAndTwoTabsOneLibraryRuleOnlyOneDrawOnePoint() {
        val target = draw(0)
        val snap = snapshot(target,
            pair("s3-9to7", CherryTab.CURRENT, "07"),
            pair("s3-9to7", CherryTab.BORN, "70", "61"))
        val actual = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap), mapOf(target to "70")), known)
        val score = actual.getValue("cherry-017")
        assertEquals(1, score.eligible)
        assertEquals(1, score.correct)
        assertEquals(3, score.events.single().pairCount)
        assertEquals(setOf(CherryTab.CURRENT, CherryTab.BORN),
            score.events.single().tabs)
    }

    @Test fun officialCorrectionRecalculatesFromFrozenSnapshot() {
        val target = draw(0)
        val snap = snapshot(target,
            pair("new-born-75", CherryTab.BORN, "45", "54"))
        val first = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap), mapOf(target to "45")),
            known)
        val revised = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap), mapOf(target to "08")),
            known)
        assertEquals(1, first.getValue("new-born-75").correct)
        assertEquals(0, revised.getValue("new-born-75").correct)
        assertEquals(1, revised.getValue("new-born-75").eligible)
        assertEquals(listOf("45", "54"), snap.rules.single().predictions)
    }

    @Test fun ambiguousMathAndUnmappedIDsCannotEarnLibraryScore() {
        val target = draw(0)
        val snap = snapshot(target,
            pair("new-mob-wheel", CherryTab.CURRENT, "12"),
            pair("new-special-khant", CherryTab.BORN, "12"),
            pair("s2-p-1", CherryTab.CURRENT, "12"),
            CherrySuccessRule("LONE:v1", "Math formula", CherryTab.MATH,
                CherrySuccessCriterion.LONE, listOf("2")))
        val score = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap), mapOf(target to "12")), known)
        assertTrue(score.isEmpty())
        assertNull(CherryLibrarySuccessBridge.libraryRuleId("new-mob-wheel"))
        assertNull(CherryLibrarySuccessBridge.libraryRuleId("new-special-khant"))
        assertEquals("cherry-021#wheel", CherryLibrarySuccessBridge.libraryRuleId("s4-wheel-44-0"))
        assertEquals("cherry-021#wheel", CherryLibrarySuccessBridge.libraryRuleId("s4-wheel-44-0@2026-11-16-MORNING"))
        assertEquals("cherry-021#born", CherryLibrarySuccessBridge.libraryRuleId("s4-born-11@2026-11-16-EVENING"))
        assertEquals("cherry-021#wheel", CherryLibrarySuccessBridge.entryScoreKey("category-double","cherry-021"))
        assertEquals("cherry-021#born", CherryLibrarySuccessBridge.entryScoreKey("category-born","cherry-021"))
        assertEquals("new-born-75", CherryLibrarySuccessBridge.libraryRuleId("new-born-75@2026-11-16-MORNING"))
        assertEquals("new-bk-19r", CherryLibrarySuccessBridge.libraryRuleId("new-bk-19r@2026-11-16-EVENING"))
        assertEquals("cherry-017", CherryLibrarySuccessBridge.libraryRuleId("s3-7to4"))
        assertNull(CherryLibrarySuccessBridge.libraryRuleId("s6-12"))
    }

    @Test fun clarifiedCurrentRulesResolveAndCountOncePerConfirmedDraw() {
        val expected = mapOf(
            "s1-day-head-tail" to "cherry-001",
            "s1-day-break" to "cherry-002",
            "s1-day-diagonal" to "cherry-003",
            "s6-57" to "cherry-046",
            "s8-zero-follow" to "cherry-078",
            "s8-evening-four" to "cherry-085",
            "s9-wailin" to "cherry-089",
        )
        val target = draw(0)
        val snap = snapshot(target, *expected.keys.map {
            pair(it, CherryTab.CURRENT, "12")
        }.toTypedArray())
        val scores = CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap), mapOf(target to "12")), expected.values.toSet())
        assertEquals(expected.values.toSet(), scores.keys)
        expected.forEach { (id, canonical) ->
            assertEquals(canonical, CherryLibrarySuccessBridge.libraryRuleId(id))
            assertEquals(1, scores.getValue(canonical).eligible)
            assertEquals(1, scores.getValue(canonical).correct)
        }
        assertTrue(CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap), emptyMap()), expected.values.toSet()).isEmpty())
    }

    @Test fun weeklyAndPartialRuleSignalsCannotEarnWholeSourcePoints() {
        val withheld = listOf(
            "s3-power", "s3-six", "s3-week", "s5-mon", "s5-wed",
            "s6-70", "s6-12", "s6-12-wheel", "s6-am92", "s6-92",
            "s8-00-bk", "s6-78", "s6-06", "s8-mon-seven", "s5-mon-five-digit",
            "s5-mon-five-break", "s8-thufri-power", "new-bk-evening7",
            "new-day-mon-b4", "new-day-tue-b3", "new-day-wed-b9", "new-groups-weekly-nine",
        )
        withheld.forEach { assertNull("Must not award a whole-rule score for $it",
            CherryLibrarySuccessBridge.libraryRuleId(it)) }
        val target=draw(0)
        val snap=snapshot(target, *withheld.map {
            pair(it, CherryTab.CURRENT, "47")
        }.toTypedArray())
        val actual=CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap), mapOf(target to "47")),
            setOf("cherry-011","cherry-019","cherry-020","cherry-028",
                "cherry-029","cherry-035","cherry-038","cherry-051",
                "cherry-056","cherry-074"))
        assertTrue(actual.isEmpty())
        assertTrue(CherryLibrarySuccessBridge.successful(actual).isEmpty())
    }

    @Test fun unsupportedOriginalsDoNotPretendToHaveZeroEligibleHistory() {
        assertFalse(CherryLibrarySuccessBridge.isSupportedSource(
            "category-groups","cherry-019"))
        assertFalse(CherryLibrarySuccessBridge.isSupportedSource(
            "category-groups","cherry-020"))
        assertFalse(CherryLibrarySuccessBridge.isSupportedSource(
            "category-born","cherry-038"))
        assertTrue(CherryLibrarySuccessBridge.isSupportedSource(
            "category-double","cherry-021"))
        assertTrue(CherryLibrarySuccessBridge.isSupportedSource(
            "category-born","cherry-021"))
        assertTrue(CherryLibrarySuccessBridge.isSupportedSource(
            "category-bk","new-bk-six"))
    }

    @Test fun mappedIdsExistInSourceLibraryNotInDisplayOnlyBook() {
        val source = java.io.File("src/main/assets/two_d_rules_reader.json").readText(Charsets.UTF_8)
        // Android's org.json methods are not available in plain JVM tests.
        // Collect stable rule IDs directly from the source text instead.
        val ids = Regex("\"id\"\\s*:\\s*\"(cherry-[0-9]{3}|new-[a-z0-9-]+)\"")
            .findAll(source).map { it.groupValues[1] }.toSet()
        val allMapped = listOf(
            "new-mob-double", "new-bk-six", "new-born-75", "new-bk-19r",
            "new-born-50", "s1-930", "s3-ten", "s3-astro", "s5-fri",
            "s6-65", "s8-80", "s4-born-11", "s4-wheel-44-0",
        ).mapNotNull { CherryLibrarySuccessBridge.libraryRuleId(it) }
        assertTrue(allMapped.all { it.substringBefore('#') in ids })
        assertEquals(98, ids.size)
    }

    @Test fun twoBirthOriginsForSameRuleInOneDrawCountOnce() {
        val target=draw(2)
        val snap=snapshot(target,
            pair("new-born-75@2026-11-16-MORNING",CherryTab.BORN,"45"),
            pair("new-born-75@2026-11-17-MORNING",CherryTab.BORN,"54"))
        val scored=CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap),mapOf(target to "54")),known)
        val single=scored.getValue("new-born-75")
        assertEquals(1,single.eligible)
        assertEquals(1,single.correct)
        assertEquals(2,single.events.single().pairCount)
    }

    @Test fun doubleWheelAndDoubleBornColumnsAreNotGivenEachOthersPoints() {
        val target=draw(0)
        val snap=snapshot(target,
            pair("s4-wheel-44-0@2026-11-13-EVENING",CherryTab.BORN,"12"),
            pair("s4-born-44@2026-11-13-EVENING",CherryTab.BORN,"81"))
        val scores=CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(snap),mapOf(target to "81")),known)
        assertEquals(0,scores.getValue("cherry-021#wheel").correct)
        assertEquals(1,scores.getValue("cherry-021#wheel").eligible)
        assertEquals(1,scores.getValue("cherry-021#born").correct)
        assertEquals(1,scores.getValue("cherry-021#born").eligible)
        assertEquals(listOf("cherry-021#born"),
            CherryLibrarySuccessBridge.successful(scores).map { it.libraryRuleId })
    }

    @Test fun snapshotSavedAfterCutoffCannotBackfillScores() {
        val target = draw(0)
        val snap = snapshot(target, pair("new-mob-double", CherryTab.CURRENT, "11"))
        val late = snap.copy(
            capturedAtMillis = target.date.atTime(12, 1).atZone(zone).toInstant().toEpochMilli())
        assertTrue(CherryLibrarySuccessBridge.scoredCompleted(
            CherrySuccessEngine.completed(listOf(late), mapOf(target to "11")),
            known).isEmpty())
    }
}
