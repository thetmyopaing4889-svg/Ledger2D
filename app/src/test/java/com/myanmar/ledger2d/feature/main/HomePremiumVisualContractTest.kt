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
        assertTrue(screen.contains("screenWidthDp*.53f"))
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
        assertTrue(orb.contains("shadowElevation = 0.dp"))
        assertTrue(orb.contains("Color(0x77D81B60)"))
        assertTrue(orb.contains("scaleX = 1.06f"))
        assertTrue(orb.contains("border = BorderStroke(1.5.dp, Color(0xCCFFFFFF))"))
        assertTrue(orb.contains("contentScale = ContentScale.Crop"))
        assertTrue(orb.contains(".clip(CircleShape)"))
        assertTrue(orb.contains("onClick = onClick"))
        assertFalse(orb.contains("offset(y = (-31).dp)"))
    }

    @Test fun cardsHaveFaintWatermarksNoBlurTilesOrOpaqueContentBlocks() {
        assertTrue(screen.contains("HomeCherryWatermarks(Modifier.matchParentSize())"))
        assertTrue(screen.contains("Faint cherry motifs"))
        assertFalse(screen.contains(".blur(22.dp)"))
        assertTrue(screen.contains(".background(Color(0xB8FFFFFF))"))
        assertTrue(commission.contains(".background(Color(0xB8FFFFFF))"))
        assertTrue(commission.contains(".background(Color(0x90F8DDE7))"))
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
        assertTrue(screen.contains("homeLiveGlowAlpha"))
        assertTrue(screen.contains("Color(0x66B71C1C)"))
        assertTrue(screen.contains("drawRoundRect("))
        assertTrue(screen.contains("0f to Color(0x22FFFFFF)"))
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

    @Test fun homeGlassIsPaintedAsSinglePanesWithoutNestedOpaqueContainers() {
        val count = screen.substringAfter("@Composable private fun HomeCountCard(")
            .substringBefore("@Composable private fun HomeWeeklyResults(")
        val weekly = screen.substringAfter("@Composable private fun HomeWeeklyResults(")
            .substringBefore("@Composable private fun HomeWeekArrows(")
        assertTrue(count.contains("One painted glass surface"))
        assertFalse(count.contains("Surface("))
        assertTrue(weekly.contains("transparent weekly grid on one glass pane"))
        assertFalse(weekly.contains("Card("))
        assertTrue(count.contains(".background(Color.Transparent)"))
        assertTrue(weekly.contains(".background(Color.Transparent)"))
        assertTrue(commission.contains("No Material3 Card surface"))
        assertFalse(commission.contains("    Card("))
        assertFalse(commission.contains("                Surface("))
        assertTrue(commission.contains(".clickable(onClick = onDetails)"))
        assertTrue(commission.contains("background(Color(0x90F8DDE7))"))
    }
}
