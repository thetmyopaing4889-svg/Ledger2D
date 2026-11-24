package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PremiumCompactScopeContractTest {
    private fun source(name: String) =
        File("src/main/java/com/myanmar/ledger2d/feature/main/" + name).readText()

    @Test fun approvedScopeUsesRealSeparateIdentitiesAndOriginalReselect() {
        val s = source("PremiumCompactScope.kt")
        assertTrue(s.contains("agent: CherryScopePerson?"))
        assertTrue(s.contains("customer: CherryScopePerson?"))
        assertTrue(s.contains("if (agent != null) ScopePortrait(vm, agent"))
        assertTrue(s.contains("if (customer != null) ScopePortrait(vm, customer"))
        assertTrue(s.contains("if (person.id > 0L)"))
        assertTrue(s.contains("CherryProfileAvatar(vm.profilePhotos, person.kind, person.id"))
        assertTrue(s.contains("Icons.Default.Group"))
        assertTrue(s.contains("onClick = onReselect"))
        assertTrue(s.contains("dateText"))
        assertTrue(s.contains("sessionLabel"))
        assertFalse(s.contains("TextOverflow.Ellipsis"))
        assertFalse(s.contains("produceState"))
    }

    @Test fun riskFirstViewPreservesOriginalInformationOrderAndTabs() {
        val s = source("CustomerRiskReviewResults.kt").substringAfter("LazyColumn(modifier.cherryFinancialBackground()")
        val header = s.indexOf("PremiumCompactScope(")
        val summary = s.indexOf("RiskValue(\"စုစုပေါင်းထိုးကြေး\"")
        val tabs = s.indexOf("PremiumRiskTabs(")
        val result = s.indexOf("when (tab)")
        assertTrue(header >= 0 && header < summary && summary < tabs && tabs < result)
        assertFalse(s.substring(0, result).contains("FlowRow("))
        assertTrue(s.substring(tabs, result).contains("selected = tab, onSelect = { tab = it }"))
        assertTrue(source("PremiumRiskControls.kt").contains("modifier = Modifier.weight(1f)"))
        assertTrue(s.contains("RiskMoneyRange(base.bestProfit, base.worstLoss)"))
    }
}
