package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B08: protect Commission Review math, filters and expansion through the glass-only pass. */
class CommissionReviewHomeChromeContractTest {
    private fun code(): String =
        File("src/main/java/com/myanmar/ledger2d/feature/main/CommissionReviewScreen.kt").readText()

    @Test fun commissionSelectorsPreservePeriodDateAndDrawFilters() {
        val selection = code().substringAfter("fun CommissionReviewScreen(")
            .substringBefore("private fun CommissionReviewResults(")
        assertTrue(selection.contains(".cherryFinancialBackground()"))
        assertFalse(selection.contains("CherryFinancialPanel {"))
        assertTrue(selection.contains("PremiumScopeDropdown("))
        assertTrue(selection.contains("PremiumSelectionChip("))
        assertTrue(selection.contains("StatementPeriods.range(period, anchor, customStart, customEnd)"))
        assertTrue(selection.contains("StatementPeriods.move(period, it, -1)"))
        assertTrue(selection.contains("StatementPeriods.move(period, it, 1)"))
        assertTrue(selection.contains("StatementRange(allRows.minOfOrNull { it.date } ?: today, today)"))
        assertTrue(selection.contains("CommissionLedgerCalculator.select(allRows, range, agentId, draw)"))
        assertTrue(selection.contains("agentId = it"))
        assertTrue(selection.contains("sessionChoice = key"))
        assertTrue(selection.contains("if (resultPage) resultPage = false else onBack()"))
        assertTrue(selection.contains("enabled = valid"))
    }

    @Test fun commissionSummaryStillUsesOriginalFilteredTotalsAndNavigation() {
        val result = code().substringAfter("private fun CommissionReviewResults(")
            .substringBefore("private fun CommissionMoneyRow(")
        assertTrue(result.contains("modifier.cherryFinancialBackground()"))
        assertEquals(1, result.split("CherryFinancialPanel {").size - 1)
        assertTrue(result.contains("PremiumCompactScope("))
        assertTrue(result.contains("onReselect = onReselect"))
        assertTrue(result.contains("CommissionLedgerCalculator.total(rows).commissionMoney()"))
        assertTrue(result.contains("rows.filter { it.session == DrawSession.MORNING }"))
        assertTrue(result.contains("rows.filter { it.session == DrawSession.EVENING }"))
        assertTrue(result.contains("CommissionLedgerCalculator.total(rows), bold = true"))
        assertTrue(result.contains("rows.groupBy { it.agentId }"))
        assertTrue(result.contains("items(groups, key = { it.first })"))
        assertTrue(result.contains("CommissionDayGroup(day, lines, showAgents = agentId == -1L)"))
        assertTrue(result.contains("view == \"combined\""))
    }

    @Test fun expandableAgentCardsPreserveCallbacksAndCommissionAmounts() {
        val agent = code().substringAfter("private fun CommissionAgentGroup(")
            .substringBefore("private fun CommissionDayGroup(")
        assertTrue(agent.contains("Color(0xEBFFFFFF)"))
        assertTrue(agent.contains("BorderStroke(1.dp, Color.White)"))
        assertTrue(agent.contains("Surface(onClick = { open = !open }, color = Color.Transparent)"))
        assertTrue(agent.contains("rememberSaveable(first.agentId)"))
        assertTrue(agent.contains("CommissionLedgerCalculator.total(lines).commissionMoney()"))
        assertTrue(agent.contains("CommissionDayGroup(date, dateRows, showAgents = false)"))
    }

    @Test fun perDayAmountsAndFormattingKeepOriginalSemanticSources() {
        val src = code()
        val money = src.substringAfter("private fun CommissionMoneyRow(")
            .substringBefore("private fun CommissionAgentGroup(")
        val day = src.substringAfter("private fun CommissionDayGroup(")
        assertTrue(money.contains("amount.commissionMoney()"))
        assertTrue(money.contains("TextAlign.End"))
        assertTrue(day.contains("CommissionLedgerCalculator.total(lines).commissionMoney()"))
        assertTrue(day.contains("CommissionLedgerCalculator.total(drawRows), true"))
        assertTrue(day.contains("line.amount.commissionMoney()"))
        assertTrue(day.contains("drawRows.sortedWith(compareBy<CommissionLedgerLine> { it.agentName }"))
        assertTrue(day.contains("if (showAgents) Text(line.agentName"))
        assertTrue(day.contains("if (open)"))
        assertFalse(day.contains("CherryFinancialPanel"))
        assertFalse(day.contains("BlurMaskFilter"))
    }
}
