package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B06/B07 scope regression checks. CI verifies source wiring; the user's
 * combined device review is still required to approve final visual spacing.
 */
class LedgerReportHomeChromeContractTest {
    private val dir = "src/main/java/com/myanmar/ledger2d/feature/main/"
    private fun source(name: String) = File(dir, name).readText()

    @Test fun agentSelectionKeepsPeriodAgentAndDrawSemantics() {
        val screen = source("AgentStatementScreens.kt")
            .substringAfter("fun AgentStatementWorkspace(")
            .substringBefore("private data class AgentReportSection(")
        assertTrue(screen.contains(".cherryFinancialBackground()"))
        assertFalse(screen.contains("CherryFinancialPanel {"))
        assertTrue(screen.contains("PremiumScopeDropdown("))
        assertTrue(screen.contains("PremiumSelectionChip("))
        assertTrue(screen.contains("StatementPeriods.range(period, anchor, start, end)"))
        assertTrue(screen.contains("StatementPeriods.move(period, it, -1)"))
        assertTrue(screen.contains("StatementPeriods.move(period, it, 1)"))
        assertTrue(screen.contains("agentId = -1L"))
        assertTrue(screen.contains("ScopeDropdown("))
        assertTrue(screen.contains("if (results) results = false else onBack()"))
        assertTrue(screen.contains("AgentLedgerReportResults("))
        assertTrue(screen.contains("session = selectedSession"))
        assertTrue(screen.contains("onReselect = { results = false }"))
    }

    @Test fun customerSelectionKeepsScopedCustomerDatesAndQuery() {
        val screen = source("CustomerStatementScreen.kt")
            .substringAfter("fun CustomerStatementScreen(")
            .substringBefore("private fun StatementMoneyLine(")
        assertTrue(screen.contains(".cherryFinancialBackground()"))
        assertFalse(screen.contains("CherryFinancialPanel {"))
        assertTrue(screen.contains("PremiumScopeDropdown("))
        assertTrue(screen.contains("PremiumSelectionChip("))
        assertTrue(screen.contains("linkedCustomer?.let { agentId = it.agentId; customerId = it.id }"))
        assertTrue(screen.contains("agentId = it; customerId = -1L; view = \"summary\"; search = \"\""))
        assertTrue(screen.contains("enabled = agentId > 0L"))
        assertTrue(screen.contains("StatementPeriods.range(period, anchor, start, end)"))
        assertTrue(screen.contains("StatementDateField("))
        assertTrue(screen.contains("vm.customerStatement(agentId, customerId, range, drawFilter)"))
        assertTrue(screen.contains("CustomerStatementContent("))
    }

    @Test fun agentResultsHaveGlassOuterSurfacesButRetainSemanticMoneyAndTables() {
        val src = source("AgentLedgerReportResults.kt")
        val summary = src.substringAfter("private fun AgentReportSummary(")
            .substringBefore("private fun AgentDue(")
        val result = src.substringAfter("fun AgentLedgerReportResults(")
        assertTrue(result.contains("LazyColumn(modifier.cherryFinancialBackground()"))
        assertTrue(result.contains("PremiumCompactScope("))
        assertTrue(result.contains("ProfileKind.AGENT, agentId, subtitle"))
        assertTrue(result.contains("onReselect = onReselect"))
        assertTrue(result.contains("AgentReportSummary(statement.draws, period"))
        assertTrue(result.contains("AgentReportTable("))
        assertTrue(result.contains("vm.agentStatement(agentId, range, session)"))
        assertTrue(summary.contains("AgentStatementCalculator.summarize(draws)"))
        assertTrue(summary.contains("AgentMetricCard("))
        assertTrue(summary.contains("AgentClearingCard(draws"))
        assertTrue(summary.contains("Color(0xEBFFFFFF)"))
        assertTrue(src.contains("AgentLedgerColors.GreenInk"))
        assertTrue(src.contains("AgentLedgerColors.RedInk"))
        assertFalse(summary.contains("CherryFinancialPanel {"))
    }

    @Test fun customerLegacyStatementTotalsKeepFinancialLabelsAndLogic() {
        val src = source("CustomerStatementScreen.kt")
        val totals = src.substringAfter("private fun StatementTotalCard(")
            .substringBefore("private fun StatementDrawCard(")
        assertTrue(totals.contains("CherryFinancialPanel {"))
        assertTrue(totals.contains("StatementMoneyLine(\"စုစုပေါင်းထိုးကြေး\", t.totalBet, true)"))
        assertTrue(totals.contains("StatementMoneyLine(\"ရလဒ်ထွက်ပြီးသော အကြိမ်များ၏ ထိုးကြေး\", t.completedBet)"))
        assertTrue(totals.contains("StatementMoneyLine(\"စုစုပေါင်း\", t.customerWinLoss, strong = true, positiveGood = true)"))
        assertTrue(totals.contains("if (!t.isComplete)"))
        assertTrue(totals.contains("color = MaterialTheme.colorScheme.error"))
    }

    @Test fun customerResultsKeepOriginalTotalsAndNumericTables() {
        val src = source("CustomerLedgerReportResults.kt")
        val summary = src.substringAfter("private fun StatementSummary(")
            .substringBefore("private fun RowScope.LedgerText(")
        val result = src.substringAfter("fun CustomerLedgerReportResults(")
        assertTrue(result.contains("LazyColumn(modifier.cherryFinancialBackground()"))
        assertTrue(result.contains("PremiumCompactScope("))
        assertTrue(result.contains("ProfileKind.AGENT, agentId, agentName"))
        assertTrue(result.contains("ProfileKind.CUSTOMER, customerId, subtitle"))
        assertTrue(result.contains("onReselect = onReselect"))
        assertTrue(result.contains("vm.customerStatement(agentId,customerId,range,session)"))
        assertTrue(result.contains("StatementSummary(statement.draws"))
        assertTrue(result.contains("PeriodLedger("))
        assertTrue(result.contains("CustomerGroupLedger("))
        assertTrue(summary.contains("CustomerStatementCalculator.totals(draws)"))
        assertTrue(summary.contains("statementDrawCounts(draws)"))
        assertTrue(summary.contains("StatementMetric("))
        assertTrue(summary.contains("SettlementMetric(draws"))
        assertTrue(summary.contains("Color(0xEBFFFFFF)"))
        assertTrue(src.contains("ReportPalette.GreenInk"))
        assertTrue(src.contains("ReportPalette.RedInk"))
        assertFalse(summary.contains("CherryFinancialPanel {"))
    }
}
