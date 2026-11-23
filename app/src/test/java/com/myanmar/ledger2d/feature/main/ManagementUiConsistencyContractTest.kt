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

    @Test fun bothDashboardsShareOneHomeInspiredPremiumCard() {
        val dashboard = source("DashboardScreens.kt")
        val card = dashboard.substringAfter("private fun DashboardLandingCard(")
            .substringBefore("fun AgentDashboardScreen(")
        assertEquals(1, Regex("DashboardLandingCard\\(action, forCustomer = false")
            .findAll(dashboard).count())
        assertEquals(1, Regex("DashboardLandingCard\\(action, forCustomer = true")
            .findAll(dashboard).count())
        assertTrue(card.contains("Modifier.fillMaxWidth().heightIn(min = 78.dp)"))
        assertTrue(card.contains("DashboardCardTypography.title(l.code)"))
        assertTrue(card.contains("DashboardCardTypography.subtitle(l.code)"))
        assertTrue(card.contains(".background(Color(0xEBFFFFFF))"))
        assertTrue(card.contains(".shadow("))
        assertTrue(card.contains(".border(1.5.dp, Color.White.copy(alpha = 0.95f)"))
        assertTrue(card.contains(".clickable(onClick = { onFeature(action.key) })"))
        assertTrue(card.contains("Brush.verticalGradient("))
        assertTrue(card.contains("Icon(action.icon"))
        assertTrue(card.contains("Icons.Default.ChevronRight"))
        assertTrue(!card.contains("CardDefaults.elevatedCardColors(containerColor = Color.White)"))
    }

    @Test fun bothDashboardListsUseHomesCherryBlushAtmosphere() {
        val dashboard = source("DashboardScreens.kt")
        val agent = dashboard.substringAfter("fun AgentDashboardScreen(")
            .substringBefore("fun AgentScopeScreen(")
        val customer = dashboard.substringAfter("fun CustomerDashboardScreen(")
            .substringBefore("fun CustomerScopeScreen(")
        for (screen in listOf(agent, customer)) {
            assertTrue(screen.contains("Color(0xFFF6DCE5)"))
            assertTrue(screen.contains("Color(0xFFF9E3EB)"))
            assertTrue(screen.contains("Color(0xFFFCEEF3)"))
            assertTrue(screen.contains("PaddingValues(horizontal = 16.dp, vertical = 14.dp)"))
        }
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
        assertTrue(list.contains("ManagementListGlassCard(onClick = onInfo)"))
        assertTrue(list.contains("ManagementListGlassCard(onClick = { onInfo(customer.id) })"))
        assertTrue(list.contains("ProfileKind.AGENT,agent.id,Modifier.size(42.dp)"))
        assertTrue(list.contains("ProfileKind.CUSTOMER,customer.id,Modifier.size(42.dp)"))
        assertTrue(list.contains("contentDescription = l.translate(\"ထိုးသားအချက်အလက်ကြည့်ရန်\")"))
        assertTrue(list.contains("colors = ListItemDefaults.colors(containerColor = Color.Transparent)"))
    }

    @Test fun managementListAndInformationSurfacesMatchHomeGlassWithoutBlurringRows() {
        val list = source("ListWorkspaceScreens.kt")
        val agentList = list.substringAfter("fun AgentListWorkspaceScreen(")
            .substringBefore("private fun AgentListRow(")
        val customerList = list.substringAfter("fun CustomerListWorkspaceScreen(")
            .substringBefore("internal fun nextAgentListDraw(")
        val info = list.substringAfter("private fun InformationCard(")
        val glass = list.substringAfter("private fun Modifier.managementGlassPane(")
            .substringBefore("private fun ManagementListGlassCard(")
        assertTrue(list.contains("Color(0xFFF6DCE5)"))
        assertTrue(list.contains("Color(0xFFF9E3EB)"))
        assertTrue(list.contains("Color(0xFFFCEEF3)"))
        assertTrue(agentList.contains(".background(managementBlushBackground)"))
        assertTrue(customerList.contains(".background(managementBlushBackground)"))
        assertTrue(info.contains(".background(managementBlushBackground)"))
        assertTrue(info.contains(".managementGlassPane(managementProfileShape, shadowDepth = 5.dp)"))
        assertTrue(glass.contains(".background(Color(0xEBFFFFFF))"))
        assertTrue(glass.contains(".border(1.5.dp, Color.White.copy(alpha = 0.95f)"))
        assertTrue(glass.contains(".shadow("))
        assertTrue(!glass.contains("homeRubyShadow("))
        assertTrue(!glass.contains("BlurMaskFilter"))
    }

    @Test fun agentAndCustomerSelectorsAddButtonsAndProfileScrollArePreserved() {
        val list = source("ListWorkspaceScreens.kt")
        val customerList = list.substringAfter("fun CustomerListWorkspaceScreen(")
            .substringBefore("internal fun nextAgentListDraw(")
        assertTrue(customerList.contains("rememberSaveable(initialAgentId)"))
        assertTrue(customerList.contains("ScopeDropdown("))
        assertTrue(customerList.contains("onAdd(selectedAgent)"))
        assertTrue(list.contains("filterAgentsForList(agents, search)"))
        assertTrue(list.contains("if (profile == null)"))
        assertTrue(list.contains("scrollContent = true"))
        assertTrue(list.contains("Modifier.weight(0.38f)"))
        assertTrue(list.contains("Modifier.weight(0.62f)"))
    }

}
