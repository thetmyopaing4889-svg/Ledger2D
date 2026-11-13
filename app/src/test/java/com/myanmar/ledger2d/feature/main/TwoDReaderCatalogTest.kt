package com.myanmar.ledger2d.feature.main

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TwoDReaderCatalogTest {
    private val rules: List<TwoDReaderRule>
        get() = TwoDReaderCatalog.fromJson(
            File("src/main/assets/two_d_rules_reader.json").readText(Charsets.UTF_8)
        )

    @Test fun everyCoverHasOnlyItsRuleNameAndExcludesBetterData() {
        val books = rules
        assertEquals(75, books.size)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 8, 9), books.map { it.section }.toSet())
        assertEquals("ထိပ်တူ / ပိတ်တူမူ", books.first().title)
        assertTrue(books.none { it.title.startsWith("အပိုင်း (") || it.title.startsWith("(က)") })
        assertEquals(books.size, books.map { it.id }.distinct().size)
    }

    @Test fun examplesAreOnlyOnSelectedCalculationAndComparisonRules() {
        val examples = rules.filter { it.exampleLines.isNotEmpty() }
        assertEquals(7, examples.size)
        assertTrue(examples.any { it.title == "ထိပ်တူ / ပိတ်တူမူ" })
        assertTrue(examples.none { it.title.startsWith("Modern / Internet") })
        assertTrue(examples.none { it.section == 6 || it.section == 8 })
        assertTrue(examples.flatMap { it.exampleLines }.any { it.contains("[[4]]") })
    }

    @Test fun digitAndDoublesTablesKeepAllSourceRows() {
        val digits = rules.single { it.section == 2 }
        assertEquals(10, digits.table!!.rows.size)
        assertEquals("7 ပိတ် သေ", digits.table.rows.first()[1])
        val doubles = rules.single { it.section == 4 && it.table != null }
        assertEquals(10, doubles.table!!.rows.size)
        assertEquals("0457 (သို့) 0459 အပြီးခွေ", doubles.table.rows.first()[1])
    }
}
