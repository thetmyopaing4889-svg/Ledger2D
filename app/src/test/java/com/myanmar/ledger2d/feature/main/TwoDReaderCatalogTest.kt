package com.myanmar.ledger2d.feature.main

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class TwoDReaderCatalogTest {
    private val source: String
        get() = File("src/main/assets/two_d_rules_reader.json")
            .readText(Charsets.UTF_8)

    private val rules: List<TwoDReaderRule>
        get() = TwoDReaderCatalog.fromJson(source)

    private val books: List<TwoDReaderBook>
        get() = TwoDReaderCatalog.booksFromJson(source)

    @Test fun allSeventyFiveSourceRulesSurviveNineFunctionalBooks() {
        val covers = books
        val expectedTitles = listOf(
            "အသေဂဏန်းမူ",
            "မွေးဂဏန်းမူ",
            "ပတ်သီးနှင့် တည်ဘောမူ",
            "အပူးနှင့် အပြီးခွေမူ",
            "ဘရိတ်မူ",
            "ပါဝါ၊ ညီအစ်ကိုနှင့် နက္ခတ်မူ",
            "မနက်၊ ညနေ နှိုင်းယှဉ်မူ",
            "နေ့ရက်အလိုက်မူ",
            "အထူးတွက်နည်းမူ",
        )
        assertEquals(expectedTitles, covers.map { it.title })
        assertEquals(9, covers.size)
        assertEquals(75, rules.size)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 8, 9), rules.map { it.section }.toSet())
        assertTrue(covers.none { it.title.startsWith("အပိုင်း (") ||
            it.title.startsWith("(က)") || Regex("""^[0-9]+\.\s""").containsMatchIn(it.title) })
        assertEquals(covers.size, covers.map { it.id }.distinct().size)
        assertEquals(9, covers.map { it.pastel }.distinct().size)
        assertEquals(rules.map { it.id }.toSet(),
            covers.flatMap { it.rules }.map { it.id }.toSet())
        val born = covers.single { it.id == "category-born" }
        assertEquals(26, born.rules.count { it.source.section == 6 })
    }

    @Test fun sourceTablesAreProjectedWithoutAlteringCanonicalCells() {
        val dead = books.single { it.id == "category-dead" }.rules
            .first { it.id == "cherry-008" }
        assertEquals(listOf("ဂဏန်း", "+7 အသေ"), dead.table!!.headings)
        assertEquals(10, dead.table!!.rows.size)
        assertEquals(listOf("0", "7 ပိတ် သေ"), dead.table!!.rows.first())
        assertEquals(listOf("9", "6 ပိတ် သေ"), dead.table!!.rows.last())
        assertEquals(1, dead.exampleLines.size)

        val follows = books.single { it.id == "category-follow" }.rules
        val plusFour = follows.first { it.title == "+4 ပတ်သီးမူ" }
        assertEquals(listOf("ဂဏန်း", "+4 ပတ်သီး"), plusFour.table!!.headings)
        assertEquals(listOf("0", "4 ပတ်သီး"), plusFour.table!!.rows.first())
        assertEquals(listOf("9", "3 ပတ်သီး"), plusFour.table!!.rows.last())
        assertEquals(1, plusFour.exampleLines.size)

        val bornDoubles = books.single { it.id == "category-born" }.rules
            .single { it.id == "cherry-021" }
        val fourDigitDoubles = books.single { it.id == "category-double" }.rules
            .single { it.id == "cherry-021" }
        assertEquals(listOf("အပူး", "မွေးဂဏန်း"), bornDoubles.table!!.headings)
        assertEquals("77 - 75 - 55", bornDoubles.table!!.rows.first()[1])
        assertEquals(listOf("အပူး", "အပြီးခွေ"), fourDigitDoubles.table!!.headings)
        assertEquals("0457 (သို့) 0459 အပြီးခွေ",
            fourDigitDoubles.table!!.rows.first()[1])
        assertEquals(10, fourDigitDoubles.table!!.rows.size)

        // The old six-column table and full three-column double source
        // are still present, byte-for-byte, in the canonical reader asset.
        assertEquals(6, rules.single { it.id == "cherry-008" }.table!!.headings.size)
        assertEquals(3, rules.single { it.id == "cherry-021" }.table!!.headings.size)
    }

    @Test fun oldSavedCoversMigrateAndNumberFavoritesRemainUnchanged() {
        val old = setOf(
            "book:cherry-book-11",
            "book:cherry-book-04",
            "s2-0-8",
        )
        val new = TwoDReaderCatalog.migrateSavedBookIds(old, books)
        assertTrue("book:category-born" in new)
        assertTrue("book:category-dead" in new)
        assertTrue("book:category-follow" in new)
        assertTrue("s2-0-8" in new)
        assertTrue(new.none { it.startsWith("book:cherry-book-") })
        assertEquals(new, TwoDReaderCatalog.migrateSavedBookIds(new, books))
    }

    @Test fun numericSearchUsesWholeNumberTokensAndReturnsExactBookPosition() {
        val token = TwoDReaderSearch.pattern("77")!!
        assertTrue(token.containsMatchIn("77 - 75 / (77)"))
        assertFalse(token.containsMatchIn("177"))
        assertFalse(token.containsMatchIn("770"))
        assertFalse(token.containsMatchIn("၇77"))
        assertTrue(TwoDReaderSearch.pattern("အသေ")!!.containsMatchIn("အသေပိတ်"))

        val hits = TwoDReaderSearch.find(books, "77")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.sourceRuleId == "cherry-021" })
        assertTrue(hits.all { hit ->
            books.single { it.id == hit.bookId }
                .rules[hit.entryIndex].id == hit.sourceRuleId
        })
        assertTrue(TwoDReaderSearch.find(books, " ".repeat(3)).isEmpty())
        assertTrue(TwoDReaderSearch.find(books, "missing-xyz-rule").isEmpty())
    }

    @Test fun titleAndTableSearchFindTheirCategoryWithoutChangingBooks() {
        assertTrue(TwoDReaderSearch.find(books, "အသေ").any {
            it.bookId == "category-dead"
        })
        assertTrue(TwoDReaderSearch.find(books, "+7").any {
            it.bookId == "category-dead"
        })
        assertEquals(9, books.size)
        assertEquals(75, rules.size)
    }

    @Test fun debugPreviewRetainsOriginalLauncherNameAndSeparatePackageId() {
        val debugManifest = File("src/debug/AndroidManifest.xml").readText()
        val mainStrings = File("src/main/res/values/strings.xml").readText()
        val gradle = File("build.gradle.kts").readText()
        assertTrue(debugManifest.contains("android:label=\"@string/app_name\""))
        assertTrue(mainStrings.contains("Cherry 2D Ledger"))
        assertTrue(gradle.contains("applicationIdSuffix = \".rulespreview\""))
    }

    @Test fun selectedExamplesArePreservedAndNoBetterDataIsIncluded() {
        val examples = rules.filter { it.exampleLines.isNotEmpty() }
        assertEquals(7, examples.size)
        assertTrue(examples.any { it.title == "ထိပ်တူ / ပိတ်တူမူ" })
        assertTrue(examples.none { it.section == 6 || it.section == 8 })
        assertTrue(examples.flatMap { it.exampleLines }.any { it.contains("[[4]]") })
        assertTrue(books.all { it.rules.isNotEmpty() })
        assertTrue(rules.none { it.section == 7 })
    }
}
