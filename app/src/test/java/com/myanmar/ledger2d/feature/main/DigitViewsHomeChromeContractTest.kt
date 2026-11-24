package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home-first B04/B05 source contracts. These are structural guards, not
 * claims of user-device sign-off. In particular, no financial calculations,
 * digit statuses or 00–99 cell presentation may change in this styling pass.
 */
class DigitViewsHomeChromeContractTest {
    private val root = "src/main/java/com/myanmar/ledger2d/feature/main/"
    private fun file(name: String): String = File(root, name).readText()

    @Test fun agentTotalsSelectionAndResultPageUseHomeGlassWithoutRouteChanges() {
        val src = file("AgentDigitTotalsScreens.kt")
        val workspace = src.substringAfter("fun AgentDigitTotalsWorkspace(")
            .substringBefore("private fun DigitTotalsAgentCard(")
        assertTrue(workspace.contains(".padding(padding).cherryFinancialBackground()"))
        assertEquals(1, Regex("CherryFinancialPanel \\{").findAll(workspace).count())
        assertTrue(workspace.contains("PremiumDateInput("))
        assertTrue(workspace.contains("PremiumScopeDropdown("))
        assertTrue(workspace.contains("ScopeDropdown("))
        assertTrue(workspace.contains("selectedAgent = it"))
        assertTrue(workspace.contains("if (resultPage) resultPage = false else onBack()"))
        assertTrue(workspace.contains("if (selectedAgent == -1L) agents"))
        assertTrue(workspace.contains("vm.agentTotalsWithCommission(it.id, date, session)"))
        assertTrue(workspace.contains("AgentDigitTotalsAggregator.combine(it)"))
        assertTrue(workspace.contains("DigitTotalsTable(combined.orEmpty())"))
        assertTrue(workspace.contains("DigitTotalsAgentCard(vm, group)"))
    }

    @Test fun agentTotalsKeepAllMoneyEquationsAndTableRowsIntact() {
        val src = file("AgentDigitTotalsScreens.kt")
        val summary = src.substringAfter("private fun DigitTotalsSummary(")
            .substringBefore("private fun DigitTotalsSummaryLine(")
        assertTrue(summary.contains("CherryFinancialPanel {"))
        assertTrue(summary.contains("Math.addExact(sum, row.stake)"))
        assertTrue(summary.contains("Math.addExact(sum, row.netCommission)"))
        assertTrue(summary.contains("DigitTotalsSummaryLine(\"စုစုပေါင်းထိုးကြေး\""))
        assertTrue(summary.contains("DigitTotalsSummaryLine(\"Net ကော်မရှင်\""))
        val table = src.substringAfter("private fun DigitTotalsTable(")
        assertFalse(table.contains("CherryFinancialPanel"))
        assertTrue(table.contains("rows.sortedBy { it.digit }"))
        assertTrue(table.contains("row.stake.digitMoney()"))
        assertTrue(table.contains("row.netCommission.digitMoney()"))
        assertTrue(table.contains("Math.addExact(sum, row.stake)"))
        assertTrue(table.contains("Math.addExact(sum, row.netCommission)"))
    }

    @Test fun customerDigitSelectionAndSearchChromeLeaveGridSemanticColorsUnchanged() {
        val src = file("CustomerDigitViewsScreen.kt")
        val workspace = src.substringAfter("internal fun CustomerDigitViewsWorkspace(")
            .substringBefore("private fun RowScope.DigitViewSummary(")
        assertEquals(2, Regex("CherryFinancialPanel \\{").findAll(workspace).count())
        assertTrue(workspace.contains("DigitViewSelector.select(amounts, view, order, search)"))
        assertTrue(workspace.contains("DigitViewSelector.normalizeSearch(it)"))
        assertTrue(workspace.contains("DigitViewSort.STAKE_DESC"))
        assertTrue(workspace.contains("DigitViewSort.STAKE_ASC"))
        assertTrue(workspace.contains("DigitViewSort.NUMBER"))
        assertTrue(workspace.contains("val columns = if (view == DigitViewFilter.STAKED) 3 else 5"))
        assertTrue(workspace.contains("repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }"))
        assertTrue(workspace.contains("DigitViewCard("))
        assertTrue(workspace.contains("DigitViewSelector.effectiveLimit("))
        assertTrue(workspace.contains("selectedDigit?.let { digit ->"))
        assertTrue(workspace.contains("winner?.digit == item.digit"))
    }

    @Test fun customerDigitCellsKeepWinnerClosedSpecialLimitAndAmounts() {
        val src = file("CustomerDigitViewsScreen.kt")
        val cell = src.substringAfter("internal fun DigitViewCard(")
        assertFalse(cell.contains("CherryFinancialPanel"))
        assertTrue(cell.contains("isWinner -> AppColors.GoldSoft"))
        assertTrue(cell.contains("isClosed -> MaterialTheme.colorScheme.errorContainer"))
        assertTrue(cell.contains("isSpecial -> MaterialTheme.colorScheme.tertiary"))
        assertTrue(cell.contains("NumberFormat.getIntegerInstance(Locale.US).format(amount)"))
        assertTrue(cell.contains("if (isWinner) Text("))
        assertTrue(cell.contains("if (isClosed) Text("))
        assertTrue(cell.contains("if (isSpecial) Text("))
        assertTrue(cell.contains("if (percentUsed != null) Text("))
    }
}
