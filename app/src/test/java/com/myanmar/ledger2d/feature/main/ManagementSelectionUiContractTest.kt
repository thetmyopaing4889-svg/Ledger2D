package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A07–A09: source-level design and routing contracts. CI success is not a
 * substitute for the user's planned combined Android phone visual review.
 */
class ManagementSelectionUiContractTest {
    private val root = "src/main/java/com/myanmar/ledger2d/feature/main/"
    private fun file(name: String): String = File(root + name).readText()

    @Test fun quickEntryKeepsAgentCustomerStateAndNavigation() {
        val quick = file("Screens.kt").substringAfter("fun QuickEntryScreen(")
            .substringBefore("/** Quick Entry uses")
        assertTrue(quick.contains("var agentId by rememberSaveable"))
        assertTrue(quick.contains("var customerId by rememberSaveable"))
        assertTrue(quick.contains("vm.customers(agentId)"))
        assertTrue(quick.contains("SelectionField("))
        assertFalse(quick.contains("CherryManagementSelectionPane"))
        assertTrue(quick.contains("CherryManagementSelectionSummary"))
        assertTrue(quick.contains("PremiumSelectionButton"))
        assertTrue(quick.contains("verticalArrangement = Arrangement.spacedBy(16.dp)"))
        assertTrue(quick.contains("onBet(agentId, customerId)"))
        assertTrue(quick.contains("enabled = agentId > 0 && customerId > 0"))
        assertTrue(quick.contains("includeQuickEntryFab = false"))
        assertTrue(quick.contains(".imePadding()"))
        assertTrue(quick.contains(".weight(1f).verticalScroll(rememberScrollState())"))
        assertEquals(2, Regex("avatar\\s*=\\s*\\{\\s*photoId").findAll(quick).count())
    }

    @Test fun settingsRetainsSelectionsHistoryBackupAndLanguageActions() {
        val settings = file("Screens.kt").substringAfter("fun SettingsScreen(")
            .substringBefore("fun SimpleListScreen(")
        assertTrue(settings.contains("cherryManagementScreenBackground()"))
        assertEquals(3, Regex("CherryManagementSelectionPane").findAll(settings).count())
        assertTrue(settings.contains("contentPadding = 0.dp, itemSpacing = 0.dp"))
        assertEquals(2, Regex("PremiumScopeDropdown").findAll(settings).count())
        assertTrue(settings.contains("language.setDefaultAgent(it)"))
        assertTrue(settings.contains("language.setDefaultCustomer(it)"))
        assertTrue(settings.contains("enabled = language.defaultAgentId > 0"))
        assertTrue(settings.contains("onClick = vm::updateHistory"))
        assertTrue(settings.contains("HistoryUpdateState.Working"))
        assertTrue(settings.contains("HistoryUpdateState.Success"))
        assertTrue(settings.contains("HistoryUpdateState.Error"))
        assertTrue(settings.contains("CherryBackupSettings()"))
        assertTrue(settings.contains("language.set(\"my\")"))
        assertTrue(settings.contains("language.set(\"en\")"))
        assertTrue(settings.contains("ListItemDefaults.colors(containerColor = Color.Transparent)"))
        assertEquals(2, Regex("avatar\\s*=\\s*\\{\\s*photoId").findAll(settings).count())
    }

    @Test fun addCustomerPickerKeepsOriginalAgentBoundCreateRoute() {
        val add = file("DashboardScreens.kt").substringAfter("fun AddCustomerFromHomeScreen(")
            .substringBefore("private fun AgentTotalTableWorkspace(")
        assertTrue(add.contains("rememberSaveable"))
        assertTrue(add.contains("language.defaultAgentId"))
        assertTrue(add.contains("PremiumScopeDropdown("))
        assertTrue(add.contains("ProfileKind.AGENT"))
        assertFalse(add.contains("CherryManagementSelectionPane"))
        assertTrue(add.contains("PremiumSelectionButton"))
        assertTrue(add.contains("onCreate(selected)"))
        assertTrue(add.contains("enabled = selected > 0L"))
        assertTrue(add.contains("LedgerControlDimensions.fabSafeBottom"))
    }

    @Test fun sharedSelectionGlassIsLightweightAndScopedToManagement() {
        val styling = file("CherryManagementSelectionUi.kt")
        assertTrue(styling.contains("Color(0xFFF6DCE5)"))
        assertTrue(styling.contains("Color(0xFFF9E3EB)"))
        assertTrue(styling.contains("Color(0xFFFCEEF3)"))
        assertTrue(styling.contains("Color(0xEBFFFFFF)"))
        assertTrue(styling.contains("Color.White.copy(alpha = 0.95f)"))
        assertTrue(styling.contains("AppColors.PrimaryDeep"))
        assertFalse(styling.contains("BlurMaskFilter"))
        assertFalse(styling.contains("homeRubyShadow("))
        assertFalse(styling.contains("rememberInfiniteTransition"))
    }
}
