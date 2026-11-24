package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contracts for non-transaction avatar surfaces.
 * A passing build proves code wiring, not the final visual layout on a phone.
 */
class CherryProfileCoverageTest {
    private val directory = "src/main/java/com/myanmar/ledger2d/feature/main/"
    private fun file(name: String) = File(directory + name).readText()

    @Test fun quickEntryAndSettingsIncludeBothRealPersonSelectors() {
        val screens = file("Screens.kt")
        val quick = screens.substringAfter("fun QuickEntryScreen(").substringBefore("/** Quick Entry")
        val settings = screens.substringAfter("fun SettingsScreen(")
        assertEquals(2, Regex("avatar\\s*=\\s*\\{\\s*photoId").findAll(quick).count())
        assertTrue(quick.contains("ProfileKind.AGENT"))
        assertTrue(quick.contains("ProfileKind.CUSTOMER"))
        assertEquals(2, Regex("avatar = \\{ photoId").findAll(settings).count())
        assertTrue(screens.contains("avatar = avatar, onSelect = onSelect"))
        val compactQuick = quick.replace(Regex("\\s+"), "")
        assertTrue(compactQuick.contains("CherryProfileAvatar(vm.profilePhotos,ProfileKind.CUSTOMER,customerId"))
    }

    @Test fun customerAndAgentOperationalHeadersNeverUseTransactionRows() {
        val screens = file("Screens.kt")
        val betting = screens.substringAfter("fun BettingScreen(").substringBefore("@Composable private fun PreviewCard")
        val history = screens.substringAfter("fun BetHistoryScreen(").substringBefore("fun SettingsScreen(")
        assertTrue(betting.contains("CherryProfileIdentity(vm,ProfileKind.AGENT,agentId"))
        assertTrue(betting.contains("CherryProfileIdentity(vm,ProfileKind.CUSTOMER,customerId"))
        assertTrue(history.contains("CherryProfileIdentity(vm, ProfileKind.CUSTOMER, customerId"))
        // The customer photo must remain in both controls after their B09 Compose
        // formatting changes. Ignore whitespace, but check real id/name wiring.
        val customerControls = screens.substringAfter("fun CommissionScreen(")
            .substringBefore("fun WinningHubScreen(")
            .replace(Regex("\\s+"), "")
        assertTrue(customerControls.contains("CherryProfileIdentity(vm,ProfileKind.CUSTOMER,id,customer?.name"))
        assertTrue(customerControls.contains("CherryProfileIdentity(vm,ProfileKind.CUSTOMER,id,limitCustomer?.name"))
        assertTrue(screens.contains("CherryProfileIdentity(vm,ProfileKind.CUSTOMER,customerId,digitCustomer?.name"))
        assertFalse(betting.contains("CherryProfileAvatar(vm.profilePhotos,ProfileKind.CUSTOMER,record.entry"))
        assertFalse(history.contains("CherryProfileAvatar(vm.profilePhotos,ProfileKind.CUSTOMER,record.entry"))
    }

    @Test fun allScopeIdentityHasNoPersonPhotoAndGroupsUseTheirOwnIds() {
        val identity = file("CherryProfileIdentity.kt")
        assertTrue(identity.contains("if (id > 0L) CherryProfileAvatar"))
        assertTrue(file("DashboardScreens.kt").contains("options.any { it.first == value }"))
        val commission = file("CommissionReviewScreen.kt")
        assertTrue(commission.contains("agentId, avatar ="))
        assertTrue(commission.contains("first.agentId,first.agentName"))
        val totals = file("AgentDigitTotalsScreens.kt")
        assertTrue(totals.contains("group.agentId,group.agentName"))
        val agentLedger = file("AgentLedgerReportResults.kt")
        assertTrue(agentLedger.contains("ProfileKind.AGENT,id,name"))
        val customerLedger = file("CustomerLedgerReportResults.kt")
        assertTrue(customerLedger.contains("ProfileKind.CUSTOMER,id,name"))
        val customerStatement = file("CustomerStatementScreen.kt")
        assertTrue(customerStatement.contains("customer.id,customer.name"))
        val agentStatement = file("AgentStatementScreens.kt")
        assertTrue(agentStatement.contains("ProfileKind.AGENT,id,agentNames[id]"))
        val risk = file("CustomerRiskReviewResults.kt")
        assertTrue(risk.contains("ProfileKind.AGENT, agentId, agentName"))
        assertTrue(risk.contains("ProfileKind.CUSTOMER, customerId, customerName"))
        val compactScope = file("PremiumCompactScope.kt")
        assertTrue(compactScope.contains("if (person.id > 0L)"))
        assertTrue(compactScope.contains("CherryProfileAvatar(vm.profilePhotos, person.kind, person.id"))
        assertEquals(2, Regex("CherryProfileIdentity\\(vm,com.myanmar.ledger2d.core.profile.ProfileKind.AGENT,agentId,agentName")
            .findAll(file("AgentPatternRulesScreens.kt")).count())
    }

    @Test fun jpegHeaderAndTransactionCalculationsRemainSeparate() {
        val voucher = file("VoucherRenderer.kt")
        val agent = file("AgentSettlementJpegRenderer.kt")
        val customer = file("CustomerSettlementJpegRenderer.kt")
        for (renderer in listOf(voucher, agent, customer)) {
            assertEquals(1, Regex("drawJpegProfileAvatar\\(").findAll(renderer).count())
        }
        assertTrue(file("DashboardScreens.kt").contains("row.customer.id,Modifier.size(34.dp)"))
        val scope = file("ProfileJpegHeader.kt")
        assertTrue(scope.contains("if (id <= 0L) return null"))
    }
}
