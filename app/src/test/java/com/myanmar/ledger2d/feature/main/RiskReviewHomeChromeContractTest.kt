package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B11 source-level UI contracts; device layout still requires combined phone QA. */
class RiskReviewHomeChromeContractTest {
    private fun source(name: String) =
        File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun selectionChromeAppliesOnlyToAnalysis() {
        val s = source("CustomerReviewScreens.kt")
        assertTrue(s.contains("if (mode == \"analysis\") CherryFinancialPanel { content() } else content()"))
        assertTrue(s.contains(".then(if (mode == \"analysis\") Modifier.cherryFinancialBackground() else Modifier)"))
        assertEquals(5, s.split("RiskSelectionPanel(mode) {").size - 1)
        assertTrue(s.contains("agentId = it; customerId = -1L"))
        assertTrue(s.contains("customerId = it"))
        assertTrue(s.contains("if (drawChoice == \"pm\") DrawSession.EVENING else DrawSession.MORNING"))
        assertTrue(s.contains("enabled = canView"))
        assertTrue(s.contains("onBack = { if (resultPage) resultPage = false else onBack() }"))
        assertTrue(s.contains("CustomerLedgerReportResults("))
        assertTrue(s.contains("CustomerRiskReviewResults("))
        assertEquals(2, Regex("avatar = \\{ photoId").findAll(s).count())
    }

    @Test fun overviewPreservesScopedCalculationAndActions() {
        val s = source("CustomerRiskReviewResults.kt")
        assertTrue(s.contains("LazyColumn(modifier.cherryFinancialBackground()"))
        assertFalse(s.contains("CherryFinancialPanel {"))
        assertTrue(s.contains("PremiumCompactScope("))
        assertTrue(s.contains("ProfileKind.AGENT, agentId, agentName"))
        assertTrue(s.contains("ProfileKind.CUSTOMER, customerId, customerName"))
        assertTrue(source("CustomerReviewScreens.kt").contains("agentName = selectedAgentName, customerName = selectedCustomerName"))
        val tabs = s.substringAfter("listOf(\"current\" to \"လက်ရှိ\"").substringBefore("when (tab)")
        assertTrue(tabs.contains("Modifier.weight(1f)"))
        assertTrue(s.contains("vm.aggregateAnalysis(agentId, date, session)"))
        assertTrue(s.contains("vm.analysis(customerId, date, session)"))
        assertTrue(s.contains("vm.agentRateForDraw(agentId, date, session)"))
        assertTrue(s.contains("vm.analysisSavedPayoutRates(agentId, customerId, date, session)"))
        assertTrue(s.contains("CustomerRiskPlanner().calculate(source, rate)"))
        assertTrue(s.contains("CustomerRiskPlanner().planForLossBudget(data.source, data.decision.hedgeRate, requested)"))
        assertTrue(s.contains("onReselect = onReselect"))
        assertTrue(source("PremiumCompactScope.kt").contains("onClick = onReselect"))
        assertTrue(s.contains("onClick = { tab = id }"))
        assertTrue(s.contains("enabled = allowCopy"))
        assertTrue(s.contains("AnalysisDigitDrilldown("))
    }

    @Test fun moneyRangeAdaptsWithoutChangingAmountsOrSemanticColors() {
        val s = source("CustomerRiskReviewResults.kt")
        val range = s.substringAfter("private fun RiskMoneyRange(")
            .substringBefore("private fun RiskDigitTable(")
        assertTrue(range.contains("val bestText = signed(best)"))
        assertTrue(range.contains("val lossText = if (loss == 0L)"))
        assertTrue(range.contains("if (best < 0L) MaterialTheme.colorScheme.error else Color(0xFF18794E)"))
        assertTrue(range.contains("val lossTint = MaterialTheme.colorScheme.error"))
        assertTrue(range.contains("BoxWithConstraints"))
        assertFalse(range.contains("380.dp"))
        assertFalse(range.contains("fontScale <= 1.15f"))
        assertTrue(range.contains("measurer.measure(AnnotatedString(it)"))
        assertTrue(range.contains("if (amountsFit)"))
        assertTrue(range.contains("RiskMoneyBox(bestTitle, bestText, bestTint, Modifier.weight(1f))"))
        assertTrue(range.contains("RiskMoneyBox(bestTitle, bestText, bestTint, Modifier.fillMaxWidth())"))
        assertFalse(range.contains("TextOverflow.Ellipsis"))
        val table = s.substringAfter("private fun RiskDigitTable(")
            .substringBefore("private fun RiskPlanCard(")
        assertFalse(table.contains("CherryFinancialPanel"))
        assertFalse(table.contains("shadow("))
        assertTrue(table.contains("source.scenarios.filter { it.profitLoss < 0L }.sortedBy { it.profitLoss }"))
    }
}
