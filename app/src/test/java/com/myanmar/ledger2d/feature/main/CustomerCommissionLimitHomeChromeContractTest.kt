package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B09 visual-only source contracts. Real-phone acceptance is deferred.
 * Do not alter commission basis-points or per-draw limit storage.
 */
class CustomerCommissionLimitHomeChromeContractTest {
    private val code by lazy {
        File("src/main/java/com/myanmar/ledger2d/feature/main/Screens.kt").readText()
    }

    private val commission by lazy {
        code.substringAfter("fun CommissionScreen(")
            .substringBefore("fun LimitScreen(")
    }
    private val limit by lazy {
        code.substringAfter("fun LimitScreen(")
            .substringBefore("fun WinningHubScreen(")
    }

    @Test fun customerCommissionStylingPreservesOriginalBasisPointValidationAndSave() {
        assertTrue(commission.contains(".cherryFinancialBackground()"))
        assertTrue(commission.contains(".imePadding().verticalScroll(rememberScrollState())"))
        assertFalse(commission.contains("CherryFinancialPanel {"))
        assertTrue(commission.contains("vm.customer(id).collectAsState(initial = null)"))
        assertTrue(commission.contains("commissionRateBasisPoints / 100.0"))
        assertTrue(commission.contains("ch.isDigit() || ch == '.'"))
        assertTrue(commission.contains("KeyboardType.Decimal"))
        assertTrue(commission.contains("it >= java.math.BigDecimal.ZERO && it <= java.math.BigDecimal(100)"))
        assertTrue(commission.contains("vm.updateCommission(id, value) { value = \"\"; onBack() }"))
        assertTrue(commission.contains("FormActions("))
    }

    @Test fun customerLimitStillUsesOriginalAgentScopeDrawAndSpecialLimitData() {
        assertTrue(limit.contains(".cherryFinancialBackground().imePadding()"))
        assertFalse(limit.contains("CherryFinancialPanel {"))
        assertTrue(limit.contains("PremiumSelectionButton("))
        assertTrue(limit.contains("HorizontalDivider()"))
        assertTrue(limit.contains("PremiumSelectionChip("))
        assertTrue(limit.contains("vm.customer(id).collectAsStateWithLifecycle(initialValue = null)"))
        assertTrue(limit.contains("vm.allLimit(id, date, session)"))
        assertTrue(limit.contains("vm.specialLimits(id, date, session)"))
        assertTrue(limit.contains("LaunchedEffect(all, date, session)"))
        assertTrue(limit.contains("PremiumDateInput(dateText, { dateText = it; language.setDate(it) }"))
        assertTrue(limit.contains("language.setSession(draw)"))
    }

    @Test fun bothLimitSaveActionsAndDeleteConfirmationRemainUnchanged() {
        assertTrue(limit.contains("vm.updateAllLimit(id, date, session, allText)"))
        assertTrue(limit.contains("vm.addSpecialLimit(id, date, session, digit, amount)"))
        assertTrue(limit.contains("BetParser.validDigit(digit)"))
        assertTrue(limit.contains("amount.toLongOrNull()?.let { it > 0 } == true"))
        assertTrue(limit.contains("vm.removeSpecialLimit(value); pendingSpecial = null"))
        assertTrue(limit.contains("pendingSpecial?.let { value ->"))
        assertTrue(limit.contains("AlertDialog("))
        assertTrue(limit.contains("items(specials) { limit ->"))
        assertTrue(limit.contains("Text(limit.digit + \"  →  \" + limit.amount.mmk()"))
        assertTrue(limit.contains("TextButton({ pendingSpecial = limit })"))
    }

    @Test fun sharedFormsAndOtherBusinessScreensAreNotReplacedByFinancialGlass() {
        val form = code.substringAfter("fun CustomerFormScreen(")
            .substringBefore("private fun FormColumn(")
        val before = code.substringBefore("@Composable\nfun CommissionScreen(")
        val after = code.substringAfter("@Composable fun WinningHubScreen(")
        assertTrue(form.contains("CherryProfileFormPage("))
        assertTrue(form.contains("vm.saveCustomerWithPhoto("))
        assertFalse(form.contains("cherryFinancialBackground()"))
        assertFalse(form.contains("CherryFinancialPanel {"))
        assertTrue(before.contains("fun BettingScreen("))
        assertTrue(after.contains("fun BetHistoryScreen("))
        assertFalse(limit.contains("BlurMaskFilter"))
    }
}
