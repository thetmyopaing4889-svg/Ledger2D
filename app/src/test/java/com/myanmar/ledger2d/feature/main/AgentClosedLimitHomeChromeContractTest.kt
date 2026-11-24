package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B10: visual-only Agent Closed/Limit source contracts. No draw/persistence logic changes. */
class AgentClosedLimitHomeChromeContractTest {
    private val src by lazy {
        File("src/main/java/com/myanmar/ledger2d/feature/main/AgentPatternRulesScreens.kt").readText()
    }

    private val closed by lazy {
        src.substringAfter("fun AgentClosedPatternWorkspace(")
            .substringBefore("fun AgentLimitPatternWorkspace(")
    }
    private val limit by lazy {
        src.substringAfter("fun AgentLimitPatternWorkspace(")
            .substringBefore("fun ClosedPatternStandaloneScreen(")
    }
    private val closedStandalone by lazy {
        src.substringAfter("fun ClosedPatternStandaloneScreen(")
            .substringBefore("fun LimitPatternStandaloneScreen(")
    }
    private val limitStandalone by lazy {
        src.substringAfter("fun LimitPatternStandaloneScreen(")
    }

    @Test fun closedDigitsKeepPreviewScopesAndConfirmations() {
        assertEquals(3, closed.split("CherryFinancialPanel {").size - 1)
        assertTrue(closed.contains("key(agentId, date, session)"))
        assertTrue(closed.contains("vm.closedNumbers(agentId,date,session)"))
        assertTrue(closed.contains("ClosedDigitSelector.select(format,source)"))
        assertTrue(closed.contains("val newlyClosed = candidates.filterNot { it in currentSet }"))
        assertTrue(closed.contains("val alreadyClosed = candidates.filter { it in currentSet }"))
        assertTrue(closed.contains("vm.updateClosedDigitGroup(agentId,date,session,affected,closing,"))
        assertTrue(closed.contains("vm.removeClosedNumber(row)"))
        assertTrue(closed.contains("pendingAction == \"close\""))
        assertTrue(closed.contains("enabled=!busy && newlyClosed.isNotEmpty()"))
        assertTrue(closed.contains("enabled=!busy && alreadyClosed.isNotEmpty()"))
        assertTrue(closed.contains("CherryProfileIdentity(vm,com.myanmar.ledger2d.core.profile.ProfileKind.AGENT,agentId,agentName"))
    }

    @Test fun agentLimitMathAndSaveRemoveRoutesRemainUntouched() {
        assertEquals(4, limit.split("CherryFinancialPanel {").size - 1)
        assertTrue(limit.contains("key(agentId,date,session)"))
        assertTrue(limit.contains("vm.agentAllLimit(agentId,date,session)"))
        assertTrue(limit.contains("vm.agentSpecialLimits(agentId,date,session)"))
        assertTrue(limit.contains("LaunchedEffect(all?.id,all?.amount)"))
        assertTrue(limit.contains("ClosedDigitSelector.select(format,source)"))
        assertTrue(limit.contains("val amount = amountText.toLongOrNull()?.takeIf { it>0L }"))
        assertTrue(limit.contains("val needsUpdate = digits.count { limitMap[it] != amount }"))
        assertTrue(limit.contains("vm.updateAgentAllLimit(agentId,date,session,allText)"))
        assertTrue(limit.contains("vm.updateAgentLimitGroup(agentId,date,session,selected,amount,remove,"))
        assertTrue(limit.contains("vm.removeAgentSpecialLimit(row)"))
        assertTrue(limit.contains("amount!=null && needsUpdate>0"))
        assertTrue(limit.contains("removable.isNotEmpty()"))
        assertTrue(limit.contains("specials.forEach { row ->"))
    }

    @Test fun bothStandaloneScreensRetainDateSessionRoutingAndErrors() {
        for (section in listOf(closedStandalone, limitStandalone)) {
            assertTrue(section.contains(".cherryFinancialBackground()"))
            assertFalse(section.contains("CherryFinancialPanel {"))
            assertTrue(section.contains("PremiumSelectionChip("))
            assertTrue(section.contains("dateText=it;language.setDate(it)"))
            assertTrue(section.contains("session=draw;language.setSession(draw)"))
            assertTrue(section.contains("PremiumDateInput(dateText"))
            assertTrue(section.contains("if(date==null) Text("))
            assertTrue(section.contains("MaterialTheme.colorScheme.error"))
        }
        assertTrue(closedStandalone.contains("AgentClosedPatternWorkspace(vm,agentId,date,session)"))
        assertTrue(limitStandalone.contains("AgentLimitPatternWorkspace(vm,agentId,date,session)"))
    }

    @Test fun digitPickerAndIndividualRowsDoNotGetPerCellEffects() {
        val picker = src.substringAfter("private fun RulePatternPicker(")
            .substringBefore("fun AgentClosedPatternWorkspace(")
        assertTrue(picker.contains("QuickFormat.entries.forEach"))
        assertTrue(picker.contains("ClosedDigitSelector.requiresInput(format)"))
        assertTrue(picker.contains("selection.digits.forEach { digit ->"))
        assertTrue(picker.contains("is ClosedDigitSelection.Invalid"))
        assertTrue(picker.contains("is ClosedDigitSelection.Valid"))
        assertFalse(picker.contains("CherryFinancialPanel"))
        assertFalse(picker.contains("BlurMaskFilter"))
        assertFalse(src.contains("rememberInfiniteTransition"))
    }
}
