package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guard for the Home-first Financial B01–B03 styling pilot. */
class FinancialChromeContractTest {
    private val root = "src/main/java/com/myanmar/ledger2d/feature/main/"
    private fun file(name: String) = File(root, name).readText()

    @Test fun bettingPreservesSubmissionAndValidationWhileStylingOnlyItsChrome() {
        val betting = file("Screens.kt").substringAfter("fun BettingScreen(")
            .substringBefore("@Composable private fun PreviewCard(")
        assertTrue(betting.contains(".cherryFinancialBackground().imePadding()"))
        assertFalse(betting.contains("CherryFinancialPanel {"))
        assertTrue(betting.contains("PremiumDateInput("))
        assertTrue(betting.contains("BetEntryConfirmButton("))
        assertTrue(betting.contains("CherryProfileIdentity(vm,ProfileKind.AGENT,agentId"))
        assertTrue(betting.contains("CherryProfileIdentity(vm,ProfileKind.CUSTOMER,customerId"))
        assertTrue(betting.contains("vm.refreshPreview(customerId, agentId"))
        assertTrue(betting.contains("vm.confirm(customerId, agentId"))
        assertTrue(betting.contains("vm.editConfirm(item, it, session, raw, format)"))
        assertTrue(betting.contains("scopeReady && currentPreview.canConfirm && !busy && date != null"))
        assertTrue(betting.contains("if (pastDraw && !backdated)"))
        assertTrue(betting.contains("BetEntryFormatGrid(format, enabled = !busy"))
    }

    @Test fun historyKeepsVoucherEditDeleteAndFinancialDataRowsUntouched() {
        val history = file("Screens.kt").substringAfter("fun BetHistoryScreen(")
            .substringBefore("@Composable fun SettingsScreen(")
        assertTrue(history.contains("LazyColumn(Modifier.fillMaxSize().padding(padding).cherryFinancialBackground()"))
        assertFalse(history.contains("CherryFinancialPanel {"))
        assertTrue(history.contains("PremiumDateInput("))
        assertTrue(history.contains("PremiumSelectionChip("))
        assertTrue(history.contains("betEntriesInSubmissionOrder(observedEntries)"))
        assertTrue(history.contains("selectedVoucherIds = voucherSelectedEntryIds("))
        assertTrue(history.contains("onEdit(record)"))
        assertTrue(history.contains("vm.deleteBet(entry)"))
        assertTrue(history.contains("onVoucherPreview(selectedDate, session, selectedVoucherIds)"))
        assertTrue(history.contains("betEntriesTotal(entries.filter"))
        // The monetary transaction cards intentionally remain neutral Material cards.
        assertTrue(history.contains("items(entries, key = { it.entry.id }) { record ->\n                ElevatedCard {"))
    }

    @Test fun todayLedgerDecoratesAgentGroupNotIndividualTransactionRows() {
        val ledger = file("OperationalScreens.kt")
        assertTrue(ledger.contains(".cherryFinancialBackground()"))
        assertEquals(1, Regex("CherryFinancialPanel \\{").findAll(ledger).count())
        assertTrue(ledger.contains("PremiumDateInput("))
        assertTrue(ledger.contains("PremiumSelectionChip("))
        assertTrue(ledger.contains("vm.agentTotals(agent.id,date,session)"))
        assertTrue(ledger.contains("vm.customerEntries(customer.id)"))
        assertTrue(ledger.contains("it.entry.drawDate==date&&it.entry.drawSession==session"))
        assertTrue(ledger.contains("flatMap{it.lines}.sumOf{it.amount}"))
        assertTrue(ledger.contains("totals.sumOf { it.amount }.mmk()"))
        assertTrue(ledger.contains("ListItemDefaults.colors(containerColor = Color.Transparent)"))
        assertFalse(ledger.contains("CherryProfileAvatar("))
        assertFalse(ledger.contains("homeRubyShadow("))
        assertTrue(ledger.contains("bottomBar={OperationalBottomBar(\"ledger\",onNavigate,{onNavigate(\"quick\")})}"))
    }

    @Test fun financialPanelDoesNotReplaceSemanticMoneyColorsOrBlurEntireTables() {
        val visuals = file("CherryFinancialChrome.kt")
        assertTrue(visuals.contains("Color(0xFFF6DCE5)"))
        assertTrue(visuals.contains("Color(0xFFF9E3EB)"))
        assertTrue(visuals.contains("Color(0xFFFCEEF3)"))
        assertTrue(visuals.contains("Color(0xEBFFFFFF)"))
        assertTrue(visuals.contains("Color.White.copy(alpha = 0.95f)"))
        assertFalse(visuals.contains("BlurMaskFilter"))
        assertFalse(visuals.contains("rememberInfiniteTransition"))
        assertFalse(visuals.contains("MaterialTheme.colorScheme.errorContainer"))
        assertFalse(visuals.contains("homeRubyShadow("))
    }
}
