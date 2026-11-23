package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard the accepted dashboard card pilot and the narrow-screen information
 * layout during subsequent design-consistency passes.
 * Source contracts do not replace Android phone screenshot acceptance.
 */
class ManagementUiConsistencyContractTest {
    private val path = "src/main/java/com/myanmar/ledger2d/feature/main/"
    private fun source(name: String) = File(path + name).readText()

    @Test fun bothDashboardsKeepOneApprovedSharedLandingCard() {
        val dashboard = source("DashboardScreens.kt")
        assertEquals(1, Regex("DashboardLandingCard\\(action, forCustomer = false").findAll(dashboard).count())
        assertEquals(1, Regex("DashboardLandingCard\\(action, forCustomer = true").findAll(dashboard).count())
        assertTrue(dashboard.contains("Modifier.fillMaxWidth().heightIn(min = 78.dp)"))
        assertTrue(dashboard.contains("DashboardCardTypography.title(l.code)"))
        assertTrue(dashboard.contains("DashboardCardTypography.subtitle(l.code)"))
    }

    @Test fun bothInformationScreensHaveStableLoadingHeaderAndScrollableDetails() {
        val screens = source("ListWorkspaceScreens.kt")
        val agent = screens.substringAfter("fun AgentInformationScreen(")
            .substringBefore("fun CustomerInformationScreen(")
        val customer = screens.substringAfter("fun CustomerInformationScreen(")
            .substringBefore("private fun InformationCard(")
        assertTrue(agent.contains("if (profile == null)"))
        assertTrue(customer.contains("if (profile == null)"))
        assertTrue(customer.contains("CircularProgressIndicator()"))
        assertTrue(agent.contains("scrollContent = true"))
        assertTrue(customer.contains("scrollContent = true"))
        assertTrue(customer.contains("CherryProfileAvatar(vm.profilePhotos,ProfileKind.CUSTOMER,id"))
        assertTrue(agent.contains("CherryProfileAvatar(vm.profilePhotos,ProfileKind.AGENT,id"))
    }

    @Test fun sharedDetailRowsWrapLongValuesWithoutChangingAmountsOrData() {
        val screens = source("ListWorkspaceScreens.kt")
        val card = screens.substringAfter("private fun InformationCard(")
        assertTrue(card.contains("horizontalArrangement = Arrangement.spacedBy(12.dp)"))
        assertTrue(card.contains("Modifier.weight(0.38f)"))
        assertTrue(card.contains("Modifier.weight(0.62f)"))
        assertTrue(card.contains("textAlign = TextAlign.End"))
        assertTrue(card.contains("fields.filter { it.second.isNotBlank() }"))
    }

    @Test fun listsPreserveProfilePhotosAndClickableNavigation() {
        val list = source("ListWorkspaceScreens.kt")
        assertTrue(list.contains("ElevatedCard(onClick = onInfo"))
        assertTrue(list.contains("ElevatedCard(onClick = { onInfo(customer.id) }"))
        assertTrue(list.contains("ProfileKind.AGENT,agent.id,Modifier.size(42.dp)"))
        assertTrue(list.contains("ProfileKind.CUSTOMER,customer.id,Modifier.size(42.dp)"))
        assertTrue(list.contains("contentDescription = l.translate(\"ထိုးသားအချက်အလက်ကြည့်ရန်\")"))
    }
}
