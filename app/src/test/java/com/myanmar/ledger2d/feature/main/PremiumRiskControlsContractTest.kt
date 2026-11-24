package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumRiskControlsContractTest {
    private fun source(name: String) = File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun tabsAndPlanOptionsKeepExistingIdsAndCallbacks() {
        val s = source("CustomerRiskReviewResults.kt")
        for (id in listOf("current", "balance", "guarantee")) {
            assertTrue(s.contains("RiskControlOption(\"" + id + "\""))
        }
        assertTrue(s.contains("selected = tab, onSelect = { tab = it }"))
        assertTrue(s.contains("options = decision.plans.map { p -> riskPlanControlOption(p.id, p.title) } +"))
        assertTrue(s.contains("riskPlanControlOption(\"budget\", \"စိတ်ကြိုက်အရှုံးကန့်သတ်\")"))
        assertTrue(s.contains("selected = chosen, onSelect = { chosen = it }"))
        assertTrue(s.contains("if (chosen == \"budget\")"))
        assertTrue(s.contains("budgetAmount = budgetText.toLongOrNull()"))
        assertTrue(s.contains("decision.plans.firstOrNull { it.id == chosen }"))
    }

    @Test fun compactControlsKeepEveryOptionAndAccessibility() {
        val s = source("PremiumRiskControls.kt")
        assertTrue(s.contains("val columns = if (compact) 4 else 2"))
        assertTrue(s.contains("options.chunked(columns)"))
        assertTrue(s.contains("modifier = Modifier.weight(1f)"))
        assertTrue(s.contains("heightIn(min = 48.dp)"))
        assertTrue(s.contains("selectable(selected = selected"))
        assertTrue(s.contains("contentDescription = option.description"))
        assertTrue(s.contains("\"middle\" ->"))
        assertFalse(s.contains("TextOverflow.Ellipsis"))
        assertFalse(s.contains("options.take("))
    }
}
