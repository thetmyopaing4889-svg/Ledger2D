package com.myanmar.ledger2d.feature.main

import org.junit.Assert.*
import org.junit.Test

class TwoDRulesBookLibraryTest {
    private val source: String
        get() = javaClass.classLoader!!.getResourceAsStream("two_d_rules_source.txt")
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Original Rules source test resource missing")

    @Test fun booksKeepDocumentHeadingsAndWholeContents() {
        val books = TwoDRulesBookLibrary.fromSource(source)
        assertEquals(16, books.size)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 8, 9), books.map { it.section }.toSet())
        assertTrue(books.none { it.section == 7 })
        assertEquals("(က) မနက် (နတ်) နှင့် ညနေ (မော်) တူညီမှုမူများ", books.first().title)
        assertTrue(books.first().originalText.contains("* ထိပ်တူ / ပိတ်တူမူ:"))
        assertEquals(26, books.first { it.section == 6 }.lines.count {
            it.matches(Regex("""^\d+\. .*"""))
        })
    }

    @Test fun digitTableRemainsTogetherWithOriginalColumnWording() {
        val table = TwoDRulesBookLibrary.fromSource(source).single { it.section == 2 }
        assertEquals("digit-table", table.kind)
        assertTrue(table.lines.contains("ဂဏန်း"))
        assertTrue(table.lines.contains("\tတည်ဘောနှင့် ထိပ်စီးများ (၁၀ ကြိမ်တွင် ၇ ကြိမ်ကျ)"))
        assertTrue(table.lines.contains("\t5 - 6 - 7 - 8 ထိပ်"))
        assertTrue(table.lines.contains("\t4 - 7 လိုက်ပါ"))
        assertTrue(table.lines.contains("\t9"))
        assertTrue(table.originalText.contains("0 မှ 9"))
    }

    @Test fun doubleTableIsOneBookAndSourceIsNotRewritten() {
        val books = TwoDRulesBookLibrary.fromSource(source)
        val doubles = books.single { it.kind == "double-table" }
        assertTrue(doubles.lines.contains("\t(00) ထွက်လျှင်"))
        assertTrue(doubles.lines.contains("\t0457 (သို့) 0459 အပြီးခွေ"))
        assertTrue(doubles.lines.contains("\t(99) ထွက်လျှင်"))
        assertTrue(doubles.lines.contains("\t61 - 63 - 41 - 43"))
        assertTrue(books.any { it.title == "(ခ) အပူး ဆက်စပ် အထူးမူများ" })
    }
}
