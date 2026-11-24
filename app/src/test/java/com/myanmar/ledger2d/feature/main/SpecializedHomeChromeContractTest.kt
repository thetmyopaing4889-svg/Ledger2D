package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4: specialized chrome only; actual visual sign-off still requires device QA. */
class SpecializedHomeChromeContractTest {
    private val root = "src/main/java/com/myanmar/ledger2d/feature/"
    private fun source(path: String) = File(root + path).readText()

    @Test fun liveKeepsBothDestinationsAndOriginalDataLifecycle() {
        val s = source("live/LiveScreen.kt")
        assertEquals(5, s.split(".cherryFinancialBackground()").size - 1)
        assertTrue(s.contains("NavigationBar(containerColor = Color(0xFFFFEBF0), tonalElevation = 0.dp)"))
        assertTrue(s.contains("selected = selected == \"live\""))
        assertTrue(s.contains("selected = selected == \"calendar\""))
        assertTrue(s.contains("if (it == \"live\") selectedDateText = \"\""))
        assertTrue(s.contains("LaunchedEffect(Unit) { collector.fetchCycle() }"))
        assertTrue(s.contains("SetMorningUiCoordinator.enterLiveScreen(appContext)"))
        assertTrue(s.contains("SetMorningUiCoordinator.leaveLiveScreen()"))
        assertTrue(s.contains("setMorningHeroPresentation("))
        assertTrue(s.contains("Color(0xFFD41452), Color(0xFF8D123A)"))
    }

    @Test fun betterDataRetainsFourTabsAndScreenScopedDiscovery() {
        val s = source("main/CherryBetterDataScreen.kt")
        assertTrue(s.contains("private val cherryBackground = Color(0xFFFCEEF3)"))
        val nav = s.substringAfter("bottomBar={").substringBefore("){padding->")
        assertEquals(4, nav.split("NavigationBarItem(").size - 1)
        for (tab in listOf("CURRENT", "BORN", "MATH", "SUMMARY")) {
            assertTrue(nav.contains("tab==CherryTab." + tab))
            assertTrue(nav.contains("tab=CherryTab." + tab))
        }
        assertEquals(4, nav.split("maxLines=2,textAlign=TextAlign.Center").size - 1)
        assertTrue(s.contains("CherryV3DiscoveryController(v3Service,target,v3Scope)"))
        assertTrue(s.contains("onDispose{v3Search.cancel()}"))
        assertTrue(s.contains("CherryBetterDataEngine.calculate(merged,target,modern,internet,closedDates)"))
        assertTrue(s.contains("successStore.recordBeforeCutoff(snapshot)"))
    }
}
