package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PremiumResultScopesContractTest {
    private fun source(name: String) = File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun resultScopeUsesSelectedIdentitiesAndKeepsExistingReturnAction() {
        val d = source("DashboardScreens.kt")
        val agent = d.substringAfter("fun AgentFeatureWorkspaceScreen(").substringBefore("fun CustomerFeatureWorkspaceScreen(")
        val customer = d.substringAfter("fun CustomerFeatureWorkspaceScreen(").substringBefore("fun SelectionCard(")
        assertTrue(agent.contains("ProfileKind.AGENT, selected, scopeLabel"))
        assertTrue(agent.contains("if (selected == -1L) language.text(\"ဒိုင်အားလုံး\", \"All agents\")"))
        assertTrue(customer.contains("ProfileKind.AGENT, agentId, scopeAgent"))
        assertTrue(customer.contains("ProfileKind.CUSTOMER, customerId, scopeCustomer"))
        for (s in listOf(agent, customer)) {
            assertTrue(s.contains("PremiumCompactScope("))
            assertTrue(s.contains("onReselect = { resultPage = false }"))
            assertTrue(s.contains("sessionLabel = compactScopeSessionLabel(session)"))
        }
        val review = source("CustomerReviewScreens.kt")
        assertTrue(review.contains("agentName = selectedAgentName,"))
        val compatibility = source("CustomerStatementResults.kt")
        assertTrue(compatibility.contains("vm.agent(agentId).collectAsStateWithLifecycle"))
        assertTrue(compatibility.contains("agentName = agent?.name"))
        val scope = source("PremiumCompactScope.kt")
        assertTrue(scope.contains("if (person.id > 0L)"))
        assertTrue(scope.contains("Icons.Default.Group"))
        assertTrue(scope.contains("null -> lang.text(\"နှစ်ကြိမ်လုံး\", \"Both draws\")"))
    }

    @Test fun activeHistoryKeepsRawTextTotalsAndVoucherActions() {
        val s = source("BetEntriesWorkspace.kt")
        assertTrue(s.contains("transactionDisplayText(record.entry.inputFormat, record.entry.sourceText)"))
        assertTrue(s.contains("betEntriesInSubmissionOrder(observed)"))
        assertTrue(s.contains("betEntriesTotal(entries)"))
        assertTrue(s.contains("onEditEntry(record.entry.id)"))
        assertTrue(s.contains("vm.deleteBet(entry)"))
        assertTrue(s.contains("voucherSelectedEntryIds(entries, customerId, date, session, voucherIds)"))
        assertTrue(s.contains("onToggleSelection?.invoke()"))
        assertFalse(s.contains("AppColors.Champagne"))
    }
}
