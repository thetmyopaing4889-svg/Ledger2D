package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** All six flourishes are visual-only, Home-scoped and motion-aware. */
class HomeMicroAnimationContractTest {
    private val screen by lazy {
        File("src/main/java/com/myanmar/ledger2d/feature/main/Screens.kt").readText()
    }
    private val commission by lazy {
        File("src/main/java/com/myanmar/ledger2d/feature/main/HomeCommissionCard.kt").readText()
    }
    private val effects by lazy {
        File("src/main/java/com/myanmar/ledger2d/feature/main/HomeMicroAnimations.kt").readText()
    }

    @Test fun sixEffectsAreConnectedOnlyToHomeSurfaces() {
        assertTrue(screen.contains("HomeDriftingPetals(Modifier.matchParentSize())"))
        assertTrue(screen.contains("HomeLiveLightSweep(Modifier.matchParentSize())"))
        assertTrue(screen.contains("HomeOrbBubbles(Modifier.size(74.dp).clip(CircleShape))"))
        assertTrue(commission.contains("HomeCommissionShimmer(Modifier.matchParentSize())"))
        assertTrue(screen.contains("rememberHomeRollingCount(value)"))
        assertTrue(commission.contains("rememberHomeRollingAmount(totals.toDateTotal)"))
        assertTrue(screen.contains("rememberHomePressScale(\"liveCard\")"))
        assertTrue(screen.contains("rememberHomePressScale(\"quickFab\")"))
        assertTrue(screen.contains("rememberHomePressScale(\"homeQuickAction\")"))
    }

    @Test fun petalShimmerAndSweepAreLightAndClipped() {
        assertTrue(effects.contains("repeat(5)"))
        assertTrue(effects.contains("tween(6500, easing = LinearEasing)"))
        assertTrue(effects.contains("delay(4800)"))
        assertTrue(effects.contains("delay(3500)"))
        assertTrue(effects.contains("delay(4500)"))
        assertTrue(effects.contains("Color(0x55FFFFFF)"))
        assertTrue(effects.contains("Canvas(modifier.clipToBounds())"))
        assertTrue(effects.contains("ValueAnimator.areAnimatorsEnabled()"))
    }

    @Test fun pressUsesOneClickWithSpringAndLightHaptics() {
        assertTrue(effects.contains("HapticFeedbackType.TextHandleMove"))
        assertTrue(effects.contains("dampingRatio = Spring.DampingRatioMediumBouncy"))
        assertTrue(effects.contains("if (pressed && enabled) .94f else 1f"))
        assertTrue(screen.contains("interactionSource = pressInteraction"))
        assertTrue(screen.contains("onClick = onClick"))
        assertFalse(screen.contains("val tapModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)"))
    }

    @Test fun ledgerValuesRemainExactAndLargeNumbersDoNotUseFloat() {
        assertTrue(homeMoneyRollEligible(0L))
        assertTrue(homeMoneyRollEligible(9_999_999L))
        assertFalse(homeMoneyRollEligible(10_000_000L))
        assertFalse(homeMoneyRollEligible(Long.MAX_VALUE))
        assertFalse(homeMoneyRollEligible(Long.MIN_VALUE))
        assertTrue(commission.contains("CommissionLedgerCalculator.overview(lines, today)"))
        assertTrue(commission.contains("homeCommissionNeedsWideRows(totals.morning, totals.evening, totals.todayTotal, totals.toDateTotal)"))
        assertTrue(commission.contains("homeCommissionWrapNumber(shownToDate)"))
        assertTrue(effects.contains("if (animated.value == amount.toFloat()) amount"))
    }

    @Test fun previouslyApprovedGlassArtworkAndNavigationAreNotRedesigned() {
        assertTrue(screen.contains("R.drawable.cherry_header_art"))
        assertTrue(screen.contains("R.drawable.bg_live_wave"))
        assertTrue(screen.contains("R.drawable.btn_cherry_orb"))
        assertTrue(screen.contains("Color(0xFFF6DCE5), Color(0xFFF9E3EB), Color(0xFFFCEEF3)"))
        assertTrue(screen.contains("HomeBottomBar(onAgentDashboard,onCustomerDashboard,onQuickEntry,onClosedDays,onWinning)"))
        assertTrue(screen.contains("HomeQuickActionsRow(onAddAgent, onAddCustomer, onAnalysis, onBetterData)"))
    }
}
