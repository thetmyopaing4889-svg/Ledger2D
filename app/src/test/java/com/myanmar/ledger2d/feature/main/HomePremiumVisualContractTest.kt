package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level regression contracts; final optical/touch validation still
 * requires an APK screenshot on a real Android device.
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

    @Test fun headerPreservesArtworkBrandDateAndPlainSettings() {
        assertTrue(screen.contains("screenWidthDp*.55f"))
        assertTrue(screen.contains("val sheetOverlap=24.dp"))
        assertTrue(screen.contains("R.drawable.cherry_header_art"))
        assertTrue(screen.contains("Color(0xFF210612).copy(alpha = .12f)"))
        assertFalse(screen.contains("Color(0x882E0C20)"))
        val header=screen.substringAfter("@Composable private fun HomeHeader(")
            .substringBefore("@Composable private fun HomeCherryWatermarks(")
        assertTrue(header.contains("Icons.Default.MoreHoriz"))
        assertTrue(header.contains("onClick = onSettings"))
        assertFalse(header.contains("Icons.Default.Settings"))
        assertFalse(header.contains("Icons.Default.Notifications"))
    }

    @Test fun rubyOrbUsesExactAssetAndCircularScaffoldFab() {
        assertTrue(screen.contains("floatingActionButtonPosition=FabPosition.Center"))
        assertTrue(screen.contains("QuickCenterFab(onQuickEntry, Modifier.offset(y=60.dp))"))
        val bar=screen.substringAfter("@Composable private fun HomeBottomBar(")
            .substringBefore("@Composable private fun RowScope.HomeNavItem(")
        assertFalse(bar.contains("QuickCenterFab("))
        assertTrue(bar.contains("NavigationBar("))
        val orb=screen.substringAfter("@Composable private fun QuickCenterFab(")
            .substringBefore("@Composable private fun HomeMetric(")
        assertTrue(orb.contains("modifier.size(82.dp)"))
        assertTrue(orb.contains("R.drawable.btn_cherry_orb"))
        assertTrue(orb.contains("shadowElevation = 9.dp"))
        assertTrue(orb.contains("contentScale = ContentScale.Crop"))
        assertTrue(orb.contains(".clip(CircleShape)"))
        assertTrue(orb.contains("onClick = onClick"))
        assertFalse(orb.contains("offset(y = (-31).dp)"))
    }

    @Test fun cardsHaveFaintWatermarksNoBlurTilesOrOpaqueContentBlocks() {
        assertTrue(screen.contains("HomeCherryWatermarks(Modifier.matchParentSize())"))
        assertTrue(screen.contains("Faint cherry motifs"))
        assertFalse(screen.contains(".blur(22.dp)"))
        assertTrue(screen.contains("color = Color(0xB8FFFFFF)"))
        assertTrue(screen.contains("containerColor=Color(0xB8FFFFFF)"))
        assertTrue(commission.contains("containerColor = Color(0xB8FFFFFF)"))
        assertTrue(commission.contains("color = Color(0x90F8DDE7)"))
        assertTrue(commission.contains("language.text(\"ယနေ့အထိ\", \"Total to date\")"))
    }

    @Test fun commissionDoesNotShrinkBelowItsContentsAndFooterClearsOrb() {
        assertTrue(screen.contains("HomeCommissionCard(vm, onCommissionDetails)"))
        assertFalse(screen.contains("HomeCommissionCard(vm, onCommissionDetails, Modifier.weight"))
        assertTrue(screen.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(screen.contains("offset(y=(-24).dp)"))
        assertTrue(screen.contains("heightIn(min = 30.dp)"))
        assertTrue(commission.contains("CommissionLedgerCalculator.overview(lines, today)"))
        assertTrue(commission.contains("padding(horizontal = 8.dp, vertical = 4.dp)"))
        assertTrue(commission.contains("homeCommissionWrapNumber(totals.toDateTotal)"))
    }

    @Test fun liveUsesGeminiSilkImageAndBusinessNavigationUnchanged() {
        assertTrue(screen.contains("R.drawable.bg_live_wave"))
        assertTrue(screen.contains("contentScale = ContentScale.Crop"))
        assertFalse(screen.contains("drawPath(darkFold"))
        assertTrue(screen.contains("HomeLiveAndSummary(agents.size,customerCount,onLive,onWinning)"))
        assertTrue(screen.contains("HomeQuickActionsRow(onAddAgent, onAddCustomer, onAnalysis, onBetterData)"))
        assertTrue(screen.contains("HomeBottomBar(onAgentDashboard,onCustomerDashboard,onQuickEntry,onClosedDays,onWinning)"))
    }

    @Test fun weeklyResultsKeepOriginalHistoryAndDrawSelectionLogic() {
        assertTrue(screen.contains("vm.winners.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("vm.historyResults.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("val digit=winnerMap[date to session] ?: \"--\""))
        assertTrue(screen.contains("onSelect(date,session,digit,stats.first,stats.second)"))
        assertTrue(screen.contains("val radius=size.minDimension/2f-1.dp.toPx()"))
    }
}
