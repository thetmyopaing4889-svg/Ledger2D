package com.myanmar.ledger2d.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TwoDRulesCatalogTest {
    private fun catalog(): TwoDRulesCatalog {
        val original = File("src/main/assets/two_d_rules_source.txt").readText(Charsets.UTF_8)
        return TwoDRulesCatalog.fromSource(original)
    }

    @Test fun originalSectionsArePreservedAndBetterDataIsExcluded() {
        val rules = catalog()
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 8, 9), rules.sections)
        assertTrue(rules.allItems.isNotEmpty())
        assertFalse(rules.allItems.any { it.section == 7 })
        assertEquals(rules.allItems.size, rules.allItems.map { it.id }.distinct().size)
    }

    @Test fun ruleViewKeepsFullTenDigitColumnsWithoutParaphrasing() {
        val rules = catalog()
        val groups = rules.groupsIn("digits")
        assertEquals(5, groups.size)
        assertTrue(groups.all { it.items.size == 10 })
        val dead = groups.single { it.title == "နောက်ပိတ်မှ အသေယူနည်း (+7 မူ)" }
        assertEquals("5 ပိတ် သေ", dead.items.single { it.label == "8" }.original)
        val follow = groups.single { it.title == "တစ်လုံးချင်းလာပြီး လိုက်ရမည့်အတွဲ" }
        assertEquals(
            "0 - 2 လိုက်ပါ (8 ထိပ် -> 01234 ပူးခွေ)",
            follow.items.single { it.label == "8" }.original,
        )
    }

    @Test fun doubleAndTriggerRulesKeepEntireSourceWording() {
        val rules = catalog()
        val doubles = rules.groupsIn("double")
            .flatMap { it.items }.filter { it.id.startsWith("s4-") && !it.id.contains("-g") }
        assertEquals(10, doubles.size)
        assertEquals(
            "(22) ထွက်လျှင်\n2479 အပြီးခွေ\n" +
                "07 - 48 - 64 - 82 - 34 (အမြဲထွက်တတ်သည်) / 79 - 72 - 49 - 42",
            doubles.single { it.id == "s4-22" }.original,
        )
        val born = rules.groupsIn("born").flatMap { it.items }
        assertEquals(26, born.size)
        assertTrue(born.single { "27" in it.sourceNumbers }.original.contains("(47 / 37) မွေးပါ"))
        assertTrue(born.any { "(65) ထွက်လျှင်: (27 / 64) မွေးပါ" in it.original })
    }

    @Test fun numberViewAggregatesDifferentSourceSectionsWithoutDuplication() {
        val rules = catalog()
        val twentySeven = rules.byNumber("27")
        assertTrue(twentySeven.any { it.section == 3 })
        assertTrue(twentySeven.any { it.section == 4 })
        assertTrue(twentySeven.any { it.section == 6 && "27" in it.sourceNumbers })
        assertTrue(twentySeven.any { it.section == 6 && "27" in it.relatedNumbers })
        assertTrue(twentySeven.first().sourceNumbers.contains("27"))
        assertEquals(twentySeven.size, twentySeven.map { it.id }.distinct().size)
        assertTrue(rules.byNumber("22").any { it.id == "s4-22" })
        assertTrue(rules.byNumber("8").any { it.section == 2 })
    }

    @Test fun searchAndValidationAreReadOnlyAndExactForPairs() {
        val rules = catalog()
        assertTrue(rules.search("ပတ်သီး").isNotEmpty())
        assertTrue(rules.search("27").any { it.section == 6 })
        assertEquals(emptyList<TwoDRuleItem>(), rules.byNumber("999"))
        assertEquals(emptyList<TwoDRuleItem>(), rules.byNumber("2a"))
        val original = File("src/main/assets/two_d_rules_source.txt").readText(Charsets.UTF_8)
        assertTrue(original.contains("အပိုင်း (၈)"))
        assertTrue(original.contains("အပိုင်း (၉)"))
        assertFalse(original.contains("အပိုင်း (၇)"))
    }
}
