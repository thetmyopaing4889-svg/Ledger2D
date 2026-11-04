package com.myanmar.ledger2d.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontLabSpecTest {
    @Test fun rolesHaveStableUniqueIdentifiersAndBothLanguages() {
        assertEquals(fontLabTokens.size, fontLabTokens.map { it.key }.toSet().size)
        assertTrue(fontLabTokens.all { it.english.isNotBlank() && it.myanmar.isNotBlank() })
    }

    @Test fun fontSamplesAreBoundedAndDoNotChangeProductionTheme() {
        assertTrue(fontLabTokens.all { it.sizeSp in 11..24 && it.lineSp >= it.sizeSp })
        assertEquals(19, fontLabTokens.single { it.key == "appBar" }.sizeSp)
        assertEquals(17, fontLabTokens.single { it.key == "section" }.sizeSp)
    }
}
