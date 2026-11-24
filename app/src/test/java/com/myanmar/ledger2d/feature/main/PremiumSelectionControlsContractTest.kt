package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumSelectionControlsContractTest {
    private fun source(name: String) = File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun premiumControlsAreOptInAndDoNotReplaceSelectionBehavior() {
        val s = source("PremiumSelectionControls.kt")
        assertTrue(s.contains("compositionLocalOf { false }"))
        assertTrue(s.contains("ScopeDropdown(label, selected, options, value, modifier, enabled, compact, avatar, onSelect)"))
        assertTrue(s.contains("DateInput(value, onValue, label, modifier, enabled)"))
        assertTrue(s.contains("FilterChip(selected = selected, onClick = onClick"))
        assertTrue(s.contains("Button(onClick = onClick, modifier = modifier, enabled = enabled"))
        val dashboard = source("DashboardScreens.kt")
        assertTrue(dashboard.contains("if (premium) premiumSelectorColors() else OutlinedTextFieldDefaults.colors()"))
        assertTrue(dashboard.contains("onClick = { onSelect(id); expanded = false }"))
        val date = source("Screens.kt").substringAfter("@Composable fun DateInput(").substringBefore("@Composable private fun FormActions(")
        assertTrue(date.contains("while(!DrawSchedule.isWeekday(picked))"))
        assertTrue(date.contains("onValue(picked.toString())"))
        assertTrue(date.contains("if (LocalPremiumSelector.current) premiumSelectorColors()"))
    }

    @Test fun activeSelectorsPreserveOriginalControlsAndSelectionCallbacks() {
        val d = source("DashboardScreens.kt")
        val agent = d.substringAfter("fun AgentFeatureWorkspaceScreen(").substringBefore("fun CustomerFeatureWorkspaceScreen(")
        val customer = d.substringAfter("fun CustomerFeatureWorkspaceScreen(").substringBefore("fun SelectionCard(")
        for (s in listOf(agent, customer)) {
            assertTrue(s.contains("PremiumScopeDropdown("))
            assertTrue(s.contains("PremiumDateInput("))
            assertTrue(s.contains("PremiumSelectionChip("))
            assertTrue(s.contains("onClick = { resultPage = true }"))
            assertTrue(s.contains("language.setSession(draw)"))
            assertTrue(s.contains("language.setDate(it)"))
        }
        assertTrue(customer.contains("customerId = if (it == language.defaultAgentId) language.defaultCustomerId else 0L"))
        assertTrue(customer.contains("CustomerBetEntriesWorkspace("))
        val card = d.substringAfter("fun SelectionCard(").substringBefore("fun ScopeDropdown(")
        assertFalse(card.contains("AppColors.Champagne"))
        assertFalse(card.contains("padding(14.dp)"))
    }
}
