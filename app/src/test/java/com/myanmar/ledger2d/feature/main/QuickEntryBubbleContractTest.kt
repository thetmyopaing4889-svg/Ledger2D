package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class QuickEntryBubbleContractTest {
    private val bounds = QuickEntryBubbleBounds(300f, 600f, 56f, 4f)

    @Test fun newDefaultIsTopRightAndOldPixelKeysAreNotRead() {
        assertEquals(QuickEntryBubblePoint(300f, 0f), bounds.point(QuickEntryBubblePosition()))
        assertEquals("quick_entry_bubble_v2_x", QuickEntryBubbleXKey)
        assertEquals("quick_entry_bubble_v2_y", QuickEntryBubbleYKey)
        val s = File("src/main/java/com/myanmar/ledger2d/feature/main/QuickEntryBubble.kt").readText()
        assertFalse(s.contains("\"quick_entry_x\""))
        assertFalse(s.contains("\"quick_entry_y\""))
    }

    @Test fun draggedPositionClampsToScreenAndAvoidsOtherHeaderActions() {
        assertEquals(QuickEntryBubblePoint(0f, 600f), bounds.point(bounds.position(-100f, 900f)))
        assertEquals(QuickEntryBubblePoint(150f, 56f), bounds.point(bounds.position(150f, 0f)))
        assertEquals(QuickEntryBubblePoint(300f, 0f), bounds.point(bounds.position(298f, 0f)))
        assertEquals(QuickEntryBubblePoint(300f, 0f), bounds.point(QuickEntryBubblePosition(Float.NaN, Float.NaN)))
    }

    @Test fun normalizedPositionSurvivesResizeAndKeyboardBounds() {
        val saved = bounds.canonical(bounds.position(150f, 300f))
        val resized = QuickEntryBubbleBounds(200f, 200f, 56f, 4f)
        assertEquals(QuickEntryBubblePoint(100f, 100f), resized.point(saved))
        val tiny = QuickEntryBubbleBounds(200f, 0f, 56f, 4f)
        assertEquals(QuickEntryBubblePoint(200f, 0f), tiny.point(saved))
    }

    @Test fun existingEligibilityNavigationAndScreenSpecificFabRemain() {
        val base = "src/main/java/com/myanmar/ledger2d/feature/main/"
        val s = File(base + "Screens.kt").readText()
        val scaffold = s.substringAfter("@Composable fun OperationalScaffold(")
            .substringBefore("@Composable private fun DarkStatusBarsEffect")
        assertTrue(scaffold.contains("globalQuickEntry.takeIf { includeQuickEntryFab }"))
        assertTrue(scaffold.contains("if (quickEntry == null) screen()"))
        assertTrue(scaffold.contains("action?.invoke(this)"))
        assertTrue(scaffold.contains("Spacer(Modifier.width(QuickEntryBubbleHeaderSlot))"))
        assertTrue(scaffold.contains("floatingActionButton={fab?.invoke()}"))
        assertTrue(scaffold.contains("QuickEntryFab(quickEntry, Modifier.matchParentSize())"))
        val nav = File(base + "LedgerNav.kt").readText()
        assertTrue(nav.contains("currentRoute == \"home\" || currentRoute == \"welcome\""))
        assertTrue(nav.contains("nav.navigate(\"quickEntry\")"))
        val bubble = File(base + "QuickEntryBubble.kt").readText()
        assertTrue(bubble.contains("R.drawable.btn_cherry_orb"))
        assertTrue(bubble.contains("WindowInsets.safeDrawing.union(WindowInsets.ime)"))
        assertTrue(bubble.contains("onDragEnd = { persistPlacement() }"))
        assertTrue(bubble.contains("registerOnSharedPreferenceChangeListener"))
    }
}
