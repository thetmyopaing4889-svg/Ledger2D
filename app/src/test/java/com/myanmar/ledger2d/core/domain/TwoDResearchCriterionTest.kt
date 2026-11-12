package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TwoDResearchCriterionTest {
    private val am = DrawSession.MORNING
    private val pm = DrawSession.EVENING
    private fun row(day: String, s: DrawSession, pair: String) =
        HistoricDraw(LocalDate.parse(day), s, pair, HistoricSource.HISTORY)
    private fun cutoff(day: String, s: DrawSession = am) =
        DrawIdentity(LocalDate.parse(day), s)
    private fun c(kind: TwoDResearchCriterionKind, value: String = "") =
        TwoDResearchCriterion(kind, value)

    @Test fun exactPairAndLeadingZeroArePreserved() {
        assertTrue(c(TwoDResearchCriterionKind.PAIR, "07").matches("07"))
        assertFalse(c(TwoDResearchCriterionKind.PAIR, "07").matches("70"))
        assertTrue(c(TwoDResearchCriterionKind.PAIR, "00").matches("00"))
        assertFalse(c(TwoDResearchCriterionKind.PAIR, "0").valid())
        assertFalse(c(TwoDResearchCriterionKind.PAIR, "100").valid())
        assertFalse(c(TwoDResearchCriterionKind.PAIR, "A7").valid())
    }

    @Test fun digitAnywhereIsOneSourceOccurrenceEvenForDoubles() {
        val digit = c(TwoDResearchCriterionKind.DIGIT, "5")
        assertTrue(digit.matches("55"))
        assertTrue(digit.matches("15"))
        assertTrue(digit.matches("50"))
        assertFalse(digit.matches("04"))
        val r = TwoDResearchEngine.search(
            listOf(row("2026-11-02", am, "55"),
                row("2026-11-02", pm, "21")),
            TwoDResearchQuery("", TwoDResearchScope.AM,
                TwoDResearchRelationship.SAME_DAY_PM,
                sourceCriterion = digit),
            cutoff("2026-11-03"))
        assertEquals(1, r.sourceCount)
        assertEquals(1, r.observedCount)
        assertEquals(1, r.followingFrequency.single().count)
    }

    @Test fun tensAndOnesMatchOnlyTheirOwnPosition() {
        val tens = c(TwoDResearchCriterionKind.TENS, "7")
        val ones = c(TwoDResearchCriterionKind.ONES, "7")
        assertTrue(tens.matches("74"))
        assertFalse(tens.matches("47"))
        assertTrue(ones.matches("47"))
        assertFalse(ones.matches("74"))
        assertTrue(tens.matches("77"))
        assertTrue(ones.matches("77"))
    }

    @Test fun parityUsesCorrectTensThenOnesOrdering() {
        val eEven = c(TwoDResearchCriterionKind.PARITY, "EE")
        val eOdd = c(TwoDResearchCriterionKind.PARITY, "EO")
        val oEven = c(TwoDResearchCriterionKind.PARITY, "OE")
        val oOdd = c(TwoDResearchCriterionKind.PARITY, "OO")
        assertTrue(eEven.matches("24"))
        assertTrue(eOdd.matches("23"))
        assertTrue(oEven.matches("32"))
        assertTrue(oOdd.matches("31"))
        assertFalse(eOdd.matches("32"))
        assertFalse(oEven.matches("23"))
        assertFalse(c(TwoDResearchCriterionKind.PARITY, "E").valid())
        assertFalse(c(TwoDResearchCriterionKind.PARITY, "XX").valid())
    }

    @Test fun doubleMatchesOnlyEqualTwoDigitValues() {
        val doubles = c(TwoDResearchCriterionKind.DOUBLE)
        assertTrue(doubles.matches("00"))
        assertTrue(doubles.matches("55"))
        assertTrue(doubles.matches("99"))
        assertFalse(doubles.matches("56"))
        assertFalse(c(TwoDResearchCriterionKind.DOUBLE, "55").valid())
    }

    @Test fun anyDigitSourceWithParitiedTargetUsesTypedRules() {
        val rows = listOf(
            row("2026-11-02", am, "72"),
            row("2026-11-02", pm, "24"),  // EE matches
            row("2026-11-03", am, "47"),  // source digit in ones
            row("2026-11-03", pm, "23"),  // EO does not match
            row("2026-11-04", am, "66"),
            row("2026-11-04", pm, "44"),
        )
        val q = TwoDResearchQuery(
            sourcePair = "",
            scope = TwoDResearchScope.AM,
            relationship = TwoDResearchRelationship.SAME_DAY_PM,
            sourceCriterion = c(TwoDResearchCriterionKind.DIGIT, "7"),
            targetCriterion = c(TwoDResearchCriterionKind.PARITY, "EE"),
        )
        val r = TwoDResearchEngine.search(rows, q, cutoff("2026-11-05"))
        assertEquals(2, r.sourceCount)
        assertEquals(2, r.observedCount)
        assertEquals(1, r.matchCount)
        assertEquals(1, r.otherCount)
        assertEquals(0, r.missingCount)
        assertEquals(listOf("23", "24"), r.followingFrequency.map { it.digit })
    }

    @Test fun validatesNewCriterionAndRejectsAmbiguousDoubleTarget() {
        assertThrows(IllegalArgumentException::class.java) {
            TwoDResearchEngine.search(emptyList(),
                TwoDResearchQuery("", TwoDResearchScope.AM,
                    TwoDResearchRelationship.SAME_DAY_PM,
                    sourceCriterion = c(TwoDResearchCriterionKind.TENS, "50")),
                cutoff("2026-11-05"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TwoDResearchEngine.search(emptyList(),
                TwoDResearchQuery("12", TwoDResearchScope.AM,
                    TwoDResearchRelationship.SAME_DAY_PM,
                    targetPair = "21",
                    targetCriterion = c(TwoDResearchCriterionKind.DOUBLE)),
                cutoff("2026-11-05"))
        }
    }
    @Test fun digitSevenToDigitThreeShowsOnlyMatchingFollowingPairs() {
        // Rows mirror the user's question, not their confidential device history.
        val rows = listOf(
            row("2026-11-02", am, "17"), row("2026-11-02", pm, "03"),
            row("2026-11-03", am, "74"), row("2026-11-03", pm, "13"),
            row("2026-11-04", am, "77"), row("2026-11-04", pm, "30"),
            row("2026-11-05", am, "67"), row("2026-11-05", pm, "04"),
            row("2026-11-06", am, "97"), row("2026-11-06", pm, "15"),
            row("2026-11-09", am, "70"), // missing PM: not a non-match
        )
        val report = TwoDResearchEngine.search(
            rows,
            TwoDResearchQuery(
                sourcePair = "",
                scope = TwoDResearchScope.AM,
                relationship = TwoDResearchRelationship.SAME_DAY_PM,
                sourceCriterion = c(TwoDResearchCriterionKind.DIGIT, "7"),
                targetCriterion = c(TwoDResearchCriterionKind.DIGIT, "3"),
            ),
            cutoff("2026-11-10"),
        )
        assertEquals(6, report.sourceCount)
        assertEquals(5, report.observedCount)
        assertEquals(3, report.matchCount)
        assertEquals(2, report.otherCount)
        assertEquals(1, report.missingCount)
        assertEquals(listOf("03", "13", "30"),
            report.matchedFollowingFrequency.map { it.digit })
        assertEquals(3, report.matchedFollowingFrequency.sumOf { it.count })
        assertTrue(report.matchedFollowingFrequency.all { "3" in it.digit })
        assertEquals(setOf("03", "04", "13", "15", "30"),
            report.followingFrequency.map { it.digit }.toSet())
        assertEquals(5, report.followingFrequency.sumOf { it.count })
        // A double such as "77" creates one source draw, not two.
        assertEquals(6, report.evidence.size)
    }

    @Test fun zeroMatchingTargetsDoNotHideOtherObservedOutcomes() {
        val rows = listOf(
            row("2026-11-02", am, "17"), row("2026-11-02", pm, "04"),
            row("2026-11-03", am, "70"), row("2026-11-03", pm, "15"),
        )
        val report = TwoDResearchEngine.search(
            rows,
            TwoDResearchQuery(
                sourcePair = "",
                scope = TwoDResearchScope.AM,
                relationship = TwoDResearchRelationship.SAME_DAY_PM,
                sourceCriterion = c(TwoDResearchCriterionKind.DIGIT, "7"),
                targetCriterion = c(TwoDResearchCriterionKind.DIGIT, "3"),
            ),
            cutoff("2026-11-04"),
        )
        assertEquals(0, report.matchCount)
        assertTrue(report.matchedFollowingFrequency.isEmpty())
        assertEquals(2, report.observedCount)
        assertEquals(setOf("04", "15"),
            report.followingFrequency.map { it.digit }.toSet())
    }

    @Test fun repeatedMatchingPairsRetainTrueFrequenciesAndDiscoveryHasNoMatches() {
        val rows = listOf(
            row("2026-11-02", am, "72"), row("2026-11-02", pm, "03"),
            row("2026-11-03", am, "74"), row("2026-11-03", pm, "03"),
            row("2026-11-04", am, "77"), row("2026-11-04", pm, "13"),
            row("2026-11-05", am, "17"), row("2026-11-05", pm, "04"),
        )
        val base = TwoDResearchQuery(
            sourcePair = "",
            scope = TwoDResearchScope.AM,
            relationship = TwoDResearchRelationship.SAME_DAY_PM,
            sourceCriterion = c(TwoDResearchCriterionKind.DIGIT, "7"),
        )
        val compared = TwoDResearchEngine.search(
            rows, base.copy(targetCriterion = c(TwoDResearchCriterionKind.DIGIT, "3")),
            cutoff("2026-11-06"),
        )
        assertEquals(3, compared.matchCount)
        assertEquals(listOf("03", "13"),
            compared.matchedFollowingFrequency.map { it.digit })
        assertEquals(listOf(2, 1),
            compared.matchedFollowingFrequency.map { it.count })
        assertEquals(4, compared.followingFrequency.sumOf { it.count })
        val discovery = TwoDResearchEngine.search(rows, base, cutoff("2026-11-06"))
        assertTrue(discovery.matchedFollowingFrequency.isEmpty())
        assertEquals(4, discovery.observedCount)
        assertEquals(4, discovery.followingFrequency.sumOf { it.count })
    }

}
