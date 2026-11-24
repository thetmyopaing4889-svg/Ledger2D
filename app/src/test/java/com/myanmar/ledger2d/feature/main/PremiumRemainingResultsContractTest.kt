package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumRemainingResultsContractTest {
    private fun source(name: String) = File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun bulkCommissionPreservesModesAndValidation() {
        val s = source("CustomerBulkCommissionWorkspace.kt")
        assertTrue(s.contains("vm.overrideCommissionForAgentDraw(agentId,date,session,percent"))
        assertTrue(s.contains("vm.scheduleCommissionForAgent(agentId,percent,effective,startSession"))
        assertTrue(s.contains("validRate && !processing"))
        assertTrue(s.contains("pending && canSubmit"))
        assertTrue(s.contains("PremiumSelectionButton(onClick={pending=true},enabled=canSubmit"))
        assertFalse(s.contains("CherryFinancialPanel"))
    }

    @Test fun winnerAndClosedDayActionsPreserveValidation() {
        val s = source("Screens.kt")
        val winner = s.substringAfter("fun WinningNumberScreen(").substringBefore("fun ScopedWinningScreen(")
        assertTrue(winner.contains("vm.saveWinner(parsedDate, session, digit"))
        assertTrue(winner.contains("vm.updateWinner(existing, parsedDate, session, digit"))
        assertTrue(winner.contains("vm.deleteWinner(winner)"))
        assertTrue(winner.contains("enabled = BetParser.validDigit(digit)"))
        assertTrue(winner.contains("WinningWeekGrid(monday, displayWinners, closedDays)"))
        assertTrue(winner.contains("WinningMonthGrid(first, last, displayWinners, closedDays)"))
        val closed = s.substringAfter("fun ClosedDayScreen(").substringBefore("fun BetHistoryScreen(")
        assertTrue(closed.contains("vm.addClosedDay(it)"))
        assertTrue(closed.contains("vm.removeClosedDay(day)"))
        assertTrue(closed.contains("PremiumSelectionButton("))
    }
}
