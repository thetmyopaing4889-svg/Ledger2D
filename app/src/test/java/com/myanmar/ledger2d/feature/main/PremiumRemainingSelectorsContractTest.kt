package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumRemainingSelectorsContractTest {
    private fun source(name: String) = File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun jpegSelectorsRetainAllControlsAndBusyGuardsWithoutNestedCards() {
        for (kind in listOf("Agent", "Customer")) {
            val s = source(kind + "SettlementJpegScreen.kt").substringBefore("private fun " + kind + "SettlementJpegPreview(")
            assertFalse(s.contains("CherryFinancialPanel {"))
            assertTrue(s.contains("PremiumScopeDropdown("))
            assertTrue(s.contains("PremiumSelectionChip("))
            assertTrue(s.contains("shape = premiumSelectorShape"))
            assertTrue(s.contains("enabled = !busy"))
            assertTrue(s.contains("enabled = !busy &&"))
            assertTrue(s.contains("busy = true"))
            assertTrue(s.contains("busy = false"))
        }
    }

    @Test fun drawSelectorsUseOptInWithoutMovingOriginalActions() {
        val dashboard = source("DashboardScreens.kt")
        for (fn in listOf("AllAgentFeatureScreen", "AllCustomerScopeScreen")) {
            val s = dashboard.substringAfter("fun " + fn + "(").substringBefore("\n@Composable")
            assertTrue(s.contains("PremiumDateInput("))
            assertTrue(s.contains("PremiumSelectionChip("))
            assertTrue(s.contains("language.setSession(draw)"))
        }
        val screens = source("Screens.kt")
        for ((start, end) in listOf(
            "DigitListScreen" to "TotalListScreen",
            "LimitScreen" to "WinningHubScreen",
            "ScopedWinningScreen" to "ReportScreen",
            "ReportScreen" to "AnalysisScreen",
            "BetHistoryScreen" to "SettingsScreen",
        )) {
            val s = screens.substringAfter("fun " + start + "(").substringBefore("fun " + end + "(")
            assertTrue(start, s.contains("PremiumDateInput("))
            assertTrue(start, s.contains("PremiumSelectionChip("))
        }
        val winning = screens.substringAfter("fun WinningNumberScreen(").substringBefore("fun ScopedWinningScreen(")
        assertTrue(winning.contains("PremiumDateInput("))
        assertTrue(winning.contains("SegmentedButton(session == draw, { session = draw; language.setSession(draw) }"))
        val closed = screens.substringAfter("fun ClosedDayScreen(").substringBefore("fun BetHistoryScreen(")
        assertTrue(closed.contains("PremiumDateInput("))
        assertTrue(closed.contains("vm.addClosedDay(it)"))
        val history = screens.substringAfter("fun BetHistoryScreen(").substringBefore("fun SettingsScreen(")
        assertTrue(history.contains("onVoucherPreview(selectedDate, session, selectedVoucherIds)"))
        val today = source("OperationalScreens.kt")
        assertTrue(today.contains("PremiumDateInput("))
        assertTrue(today.contains("vm.agentTotals(agent.id,date,session)"))
    }
}
