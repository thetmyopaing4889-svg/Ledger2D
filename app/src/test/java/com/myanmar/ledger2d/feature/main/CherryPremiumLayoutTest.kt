package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryPremiumLayoutTest {
    @Test fun exactToneBoundariesAndMissingEvidence() {
        assertEquals(CherryOrbTone.PEARL,cherryOrbTone(null))
        assertEquals(CherryOrbTone.PEARL,cherryOrbTone(CherrySuccessRate(4,4)))
        assertEquals(CherryOrbTone.PEARL,cherryOrbTone(CherrySuccessRate(3,6)))
        assertEquals(CherryOrbTone.ROSE,cherryOrbTone(CherrySuccessRate(3,5)))
        assertEquals(CherryOrbTone.ROSE,cherryOrbTone(CherrySuccessRate(79,99)))
        assertEquals(CherryOrbTone.RUBY,cherryOrbTone(CherrySuccessRate(4,5)))
    }
    @Test fun exclusionsGroupAndDeduplicateWithoutDroppingRuleEvidence() {
        val origin=DrawIdentity(LocalDate.of(2026,11,23),DrawSession.MORNING)
        val rules=listOf(
            CherryCutEvidence("cut-tail-plus7","ending",CherryPairExpander.tail(4),origin,""),
            CherryCutEvidence("cut-double-digit","double",CherryPairExpander.any(2),origin,""),
            CherryCutEvidence("cut-morning-head","head",CherryPairExpander.any(2),origin,""),
            CherryCutEvidence("cut-dead-triples","pairs",listOf("12","21","13","31","23","32"),origin,""))
        val groups=cherryCutGroups(rules)
        assertEquals(listOf("4"),groups[0].values)
        assertEquals(listOf("2"),groups[1].values)
        assertEquals(2,groups[1].rules.size)
        assertEquals(6,groups[2].values.size)
        assertTrue(cherryCutGroups(emptyList()).isEmpty())
    }
}
