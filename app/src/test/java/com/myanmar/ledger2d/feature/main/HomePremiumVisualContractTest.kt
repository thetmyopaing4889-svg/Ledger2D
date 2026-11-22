package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard critical Home visual structure at source level. Pixel-perfect layout
 * still requires a device screenshot; JVM tests cannot render Compose surfaces.
 */
class HomePremiumVisualContractTest {
    private val screen by lazy {
        File("src/main/java/com/myanmar/ledger2d/feature/main/Screens.kt")
            .readText(Charsets.UTF_8)
    }
    private val commission by lazy {
        File("src/main/java/com/myanmar/ledger2d/feature/main/HomeCommissionCard.kt")
            .readText(Charsets.UTF_8)
    }

    @Test fun headerHasSpaceForDateAndNoDarkMoreBubble() {
        assertTrue(screen.contains("screenWidthDp*.55f"))
        assertTrue(screen.contains("val sheetOverlap=24.dp"))
        assertTrue(screen.contains("Color(0xFF210612).copy(alpha = .12f)"))
        assertFalse(screen.contains("Color(0x882E0C20)"))
        val header=screen.substringAfter("@Composable private fun HomeHeader(")
            .substringBefore("@Composable private fun HomeCherryWatermarks(")
        assertTrue(header.contains("Icons.Default.MoreHoriz"))
        assertTrue(header.contains("onClick = onSettings"))
        assertFalse(header.contains("Icons.Default.Settings"))
        assertFalse(header.contains("Icons.Default.Notifications"))
    }

    @Test fun theCherryOrbIsADedicatedScaffoldFabNotClippedInsideBottomNav() {
        assertTrue(screen.contains("floatingActionButtonPosition=FabPosition.Center"))
        assertTrue(screen.contains("QuickCenterFab(onQuickEntry, Modifier.offset(y=60.dp))"))
        val bar=screen.substringAfter("@Composable private fun HomeBottomBar(")
            .substringBefore("@Composable private fun RowScope.HomeNavItem(")
        assertFalse(bar.contains("QuickCenterFab("))
        assertTrue(bar.contains("NavigationBar("))
        assertTrue(screen.contains("modifier.size(82.dp)"))
        assertFalse(screen.contains("offset(y = (-31).dp).size(82.dp)"))
    }

    @Test fun frostedCardsHaveVisibleWatermarkAndRealTranslucentMaterial() {
        assertTrue(screen.contains("HomeCherryWatermarks(Modifier.matchParentSize())"))
        assertTrue(screen.contains(".blur(22.dp)"))
        assertTrue(screen.contains("color = Color.White.copy(alpha = .70f)"))
        assertTrue(screen.contains("containerColor=Color.White.copy(alpha=.71f)"))
        assertTrue(screen.contains("shadowElevation=3.dp"))
        assertTrue(commission.contains("containerColor = Color.White.copy(alpha = .70f)"))
        assertTrue(commission.contains("language.text(\"ယနေ့အထိ\", \"Total to date\")"))
        assertTrue(commission.contains("Color(0xC5F8DBE6)"))
    }

    @Test fun liveUsesSilkRibbonsAndKeepsCoreNavigationTargets() {
        assertTrue(screen.contains("style = Stroke(width = h * (.24f + i * .04f)"))
        assertTrue(screen.contains("Color(0x60FF4D90)"))
        assertTrue(screen.contains("Color(0xFFFFF1F6)"))
        assertTrue(screen.contains("HomeLiveAndSummary(agents.size,customerCount,onLive,onWinning)"))
        assertTrue(screen.contains("HomeQuickActionsRow(onAddAgent, onAddCustomer, onAnalysis, onBetterData)"))
        assertTrue(screen.contains("HomeBottomBar(onAgentDashboard,onCustomerDashboard,onQuickEntry,onClosedDays,onWinning)"))
    }
}
