package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ResearchAtlasGuideTest {
    @Test fun examplesHaveUniqueStableIdsBilingualDescriptionsAndValidQueries() {
        val list = ResearchGuideCatalog.examples
        assertEquals(6, list.size)
        assertEquals(list.size, list.map { it.id }.distinct().size)
        list.forEach { example ->
            assertTrue(example.id.isNotBlank())
            assertTrue(example.titleMy.isNotBlank())
            assertTrue(example.titleEn.isNotBlank())
            assertTrue(example.descriptionMy.isNotBlank())
            assertTrue(example.descriptionEn.isNotBlank())
            assertTrue(example.source.valid())
            assertTrue(example.target == null || example.target.valid())
            val query = example.toQuery()
            assertEquals(example.relationship, query.relationship)
            assertEquals(example.source, query.sourceCriterion)
            assertEquals(example.target, query.targetCriterion)
            assertNull(query.targetPair)
            assertNull(query.range.start)
            assertNull(query.range.end)
            if (query.relationship == TwoDResearchRelationship.SAME_DAY_PM) {
                assertEquals(TwoDResearchScope.AM, query.scope)
            } else {
                assertEquals(example.scope, query.scope)
            }
        }
    }

    @Test fun guideExamplesAreRealQueriesNotFixedOutcomes() {
        val reverse = ResearchGuideCatalog.examples.single { it.id == "reverse-12" }
        assertEquals("12", reverse.source.value)
        assertEquals("21", reverse.target?.value)
        assertEquals(TwoDResearchRelationship.SAME_DAY_PM, reverse.toQuery().relationship)

        val digit = ResearchGuideCatalog.examples.single { it.id == "digit-7-3" }
        assertEquals(TwoDResearchCriterionKind.DIGIT, digit.source.kind)
        assertEquals("7", digit.source.value)
        assertEquals("3", digit.target?.value)
        assertEquals(TwoDResearchScope.ALL, digit.toQuery().scope)
        assertTrue(digit.source.matches("77"))
        assertTrue(digit.target!!.matches("03"))
        assertFalse(digit.target!!.matches("04"))

        val doubles = ResearchGuideCatalog.examples.single { it.id == "double-discovery" }
        assertEquals(TwoDResearchCriterionKind.DOUBLE, doubles.source.kind)
        assertNull(doubles.target)
        assertNull(doubles.toQuery().targetCriterion)

        val parity = ResearchGuideCatalog.examples.single { it.id == "parity-odd-to-even" }
        assertEquals("OO", parity.source.value)
        assertEquals("EE", parity.target?.value)
    }

    @Test fun oneTapStudyClosesGuideAndKeepsEditableQuestionConsistentWithEvidence() {
        val session = ResearchLabSessionState(LocalDate.parse("2026-11-12"))
        session.guideVisible = true
        session.frequencyMode = "all"
        session.frequencyExpanded = true
        session.evidenceFilter = "other"
        session.evidenceLimit = 75
        val sample = ResearchGuideCatalog.examples.single { it.id == "digit-7-3" }
        session.openPreset(sample)

        assertFalse(session.guideVisible)
        assertTrue(session.resultVisible)
        assertEquals("next", session.relationship)
        assertEquals("all", session.scope)
        assertEquals(TwoDResearchCriterionKind.DIGIT, session.sourceKind)
        assertEquals("7", session.sourceValue)
        assertTrue(session.compare)
        assertEquals(TwoDResearchCriterionKind.DIGIT, session.targetKind)
        assertEquals("3", session.targetValue)
        assertEquals("all", session.period)
        assertEquals(sample.toQuery(), session.submitted)
        assertEquals("all", session.evidenceFilter)
        assertEquals(25, session.evidenceLimit)
        assertFalse(session.frequencyExpanded)
        assertEquals("matched", session.frequencyMode)
    }

    @Test fun discoveryGuideExampleTurnsTargetOffWithoutInventingAPair() {
        val session = ResearchLabSessionState(LocalDate.parse("2026-11-12"))
        session.openPreset(ResearchGuideCatalog.examples.single { it.id == "double-discovery" })
        assertFalse(session.compare)
        assertEquals(TwoDResearchCriterionKind.DOUBLE, session.sourceKind)
        assertEquals("", session.sourceValue)
        assertEquals("", session.submitted?.sourcePair)
        assertNull(session.submitted?.targetCriterion)
        assertEquals(TwoDResearchRelationship.NEXT_DRAW, session.submitted?.relationship)
    }
}
