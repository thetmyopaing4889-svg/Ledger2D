package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.CherryDiscoveredFormula
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryDiscoverySearchCursor
import com.myanmar.ledger2d.core.domain.CherryDiscoverySession
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherryDiscoveryUiStateTest {
    private val target = DrawIdentity(LocalDate.of(2026, 11, 16), DrawSession.MORNING)

    private fun offered(
        kind: CherryDiscoveryKind,
        name: String,
        position: Int,
        existing: CherryDiscoverySearchCursor? = null,
    ): CherryDiscoverySearchCursor {
        val formula = CherryDiscoveredFormula(
            kind = kind, id = name, expression = "top1", offset = 0,
            currentDigit = 1, historicalStreak = 3, matchedDraws = emptyList(),
            discoveredAtStage = 1,
        )
        return CherryDiscoverySearchCursor(
            kind = kind,
            engine = CherryDiscoverySession(target, "signature", 1, 9,
                listOf(formula), hasImmediatePreviousDraw = true),
            offeredIds = existing?.offeredIds.orEmpty() + name,
            newCandidate = formula,
            bestCandidate = existing?.bestCandidate ?: formula,
            exhausted = false,
            nextPosition = position,
        )
    }

    @Test fun eachCategoryStaysInItsOwnTabAndNewResultIsDisplayed() {
        val lone = offered(CherryDiscoveryKind.LONE, "lone-1", 1)
        val wam = offered(CherryDiscoveryKind.WAM, "wam-1", 2)
        var state = CherryDiscoveryUiState().searched(CherryDiscoveryKind.LONE, lone)
        assertEquals("lone-1", state.displayId(CherryDiscoveryKind.LONE))
        assertNull("An offer is NOT automatically chosen for locking",
            state.selectedIds[CherryDiscoveryKind.LONE])

        state = state.select(CherryDiscoveryKind.LONE, "lone-1")
        state = state.searched(CherryDiscoveryKind.WAM, wam)
        assertEquals(lone, state.cursors[CherryDiscoveryKind.LONE])
        assertEquals(wam, state.cursors[CherryDiscoveryKind.WAM])
        assertNull(state.cursors[CherryDiscoveryKind.BREAK])
        assertEquals("lone-1", state.selectedIds[CherryDiscoveryKind.LONE])

        val nextLone = offered(CherryDiscoveryKind.LONE, "lone-2", 3, lone)
        state = state.searched(CherryDiscoveryKind.LONE, nextLone)
        assertEquals("New find is displayed regardless of old lock selection",
            "lone-2", state.displayId(CherryDiscoveryKind.LONE))
        assertEquals("Old explicit selection remains unchanged",
            "lone-1", state.selectedIds[CherryDiscoveryKind.LONE])
        assertEquals("wam-1", state.displayId(CherryDiscoveryKind.WAM))
    }

    @Test fun rejectsWrongCategoryAndUnseenSelections() {
        val wam = offered(CherryDiscoveryKind.WAM, "wam-1", 2)
        try {
            CherryDiscoveryUiState().searched(CherryDiscoveryKind.LONE, wam)
            fail("WAM search must never be written to LONE")
        } catch (_: IllegalArgumentException) { /* expected */ }

        val state = CherryDiscoveryUiState().searched(CherryDiscoveryKind.WAM, wam)
        try {
            state.select(CherryDiscoveryKind.WAM, "missing")
            fail("Unknown result must not be selected for lock")
        } catch (_: IllegalArgumentException) { /* expected */ }

        val accepted = state.select(CherryDiscoveryKind.WAM, "wam-1")
        assertEquals("wam-1", accepted.selectedIds[CherryDiscoveryKind.WAM])
        val abstained = accepted.select(CherryDiscoveryKind.WAM, null)
        assertNull(abstained.selectedIds[CherryDiscoveryKind.WAM])
        assertEquals("wam-1", abstained.displayId(CherryDiscoveryKind.WAM))
    }
}
