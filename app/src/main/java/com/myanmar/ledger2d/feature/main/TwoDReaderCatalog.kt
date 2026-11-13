package com.myanmar.ledger2d.feature.main

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reader-only presentation of the supplied Rules source. The unmodified source
 * and number-first [TwoDRulesCatalog] remain separate.
 *
 * Section seven is Better Data, and must not be reintroduced into Rules.
 * Editorial notes and unverified success-rate claims are not interpreted as
 * computation or shown as developer metadata.
 */
internal data class TwoDReaderTable(
    val headings: List<String>,
    val rows: List<List<String>>,
)

internal data class TwoDReaderRule(
    val id: String,
    val title: String,
    val section: Int,
    val text: String,
    val exampleLines: List<String>,
    val table: TwoDReaderTable?,
) {
    val originalText: String
        get() = buildString {
            append(title)
            append('\n')
            append(text)
            table?.let { values ->
                append('\n')
                append(values.headings.joinToString(" | "))
                values.rows.forEach { row ->
                    append('\n')
                    append(row.joinToString(" | "))
                }
            }
        }
}

/**
 * A book entry is a view onto ONE canonical source rule. A cross-listed rule
 * can use different table columns without duplicating or editing source data.
 */
internal data class TwoDReaderBookEntry(
    val source: TwoDReaderRule,
    val title: String,
    val text: String,
    val table: TwoDReaderTable?,
    val exampleLines: List<String>,
) {
    val id: String get() = source.id
    val originalText: String get() = buildString {
        append(title)
        append('\n')
        append(text)
        table?.let { values ->
            append('\n')
            append(values.headings.joinToString(" | "))
            values.rows.forEach { row ->
                append('\n')
                append(row.joinToString(" | "))
            }
        }
    }
}

internal data class TwoDReaderBook(
    val id: String,
    val title: String,
    val section: Int,
    val pastel: String,
    val legacyBookIds: List<String>,
    val rules: List<TwoDReaderBookEntry>,
) {
    val originalText: String
        get() = title + "\n" + rules.joinToString("\n\n") { it.originalText }
}

internal object TwoDReaderCatalog {
    fun booksFromJson(source: String): List<TwoDReaderBook> {
        val canonical = fromJson(source)
        val lookup = canonical.associateBy { it.id }
        val rows = JSONObject(source).getJSONArray("books")
        val books = (0 until rows.length()).map { index ->
            val node = rows.getJSONObject(index)
            val section = node.getInt("section")
            val title = node.getString("title").trim()
            require(title.isNotBlank() && !title.startsWith("အပိုင်း ("))
            require(!Regex("""^\([ကခဂဃင]\)""").containsMatchIn(title))
            require(!Regex("""^[0-9]+\.\s""").containsMatchIn(title))
            val color = node.getString("pastel")
            require(color.matches(Regex("""#[0-9A-Fa-f]{6}""")))
            val entries = node.getJSONArray("entries")
            val members = (0 until entries.length()).map { entryIndex ->
                val entry = entries.getJSONObject(entryIndex)
                val id = entry.getString("id")
                val rule = requireNotNull(lookup[id]) { "Unknown source rule id: $id" }
                require(rule.section == section)
                val projectedTable = rule.table?.let { sourceTable ->
                    val requested = entry.optJSONArray("tableColumns")?.ints()
                        ?: sourceTable.headings.indices.toList()
                    require(requested.isNotEmpty() && requested.distinct() == requested)
                    require(requested.first() == 0)
                    require(requested.all { it in sourceTable.headings.indices })
                    TwoDReaderTable(
                        headings = requested.map { sourceTable.headings[it] },
                        rows = sourceTable.rows.map { cells ->
                            requested.map { cells[it] }
                        },
                    )
                }
                val requestedExamples = entry.optJSONArray("exampleIndexes")?.ints()
                val example = if (requestedExamples == null) rule.exampleLines else {
                    require(requestedExamples.all { it in rule.exampleLines.indices })
                    requestedExamples.map { rule.exampleLines[it] }
                }
                TwoDReaderBookEntry(
                    source = rule,
                    title = entry.optString("title", rule.title),
                    text = entry.optString("text", rule.text),
                    table = projectedTable,
                    exampleLines = example,
                )
            }
            TwoDReaderBook(
                id = node.getString("id"),
                title = title,
                section = section,
                pastel = color,
                legacyBookIds = node.getJSONArray("legacyBookIds").strings(),
                rules = members,
            ).also { require(members.isNotEmpty()) }
        }
        require(books.size == 9)
        require(books.map { it.id }.distinct().size == books.size)
        require(books.map { it.title }.distinct().size == books.size)
        // Cross-listed entries point to the same canonical source. No source
        // rule may be lost during an organizational redesign.
        require(books.flatMap { it.rules }.map { it.id }.toSet() ==
            canonical.map { it.id }.toSet())
        return books
    }

    /** Preserve saved covers when migrating the previously shipped 16-book UI. */
    fun migrateSavedBookIds(saved: Set<String>, books: List<TwoDReaderBook>): Set<String> {
        val olderKeys = saved.filter { it.startsWith("book:cherry-book-") }.toSet()
        val newKeys = books.filter { book ->
            book.legacyBookIds.any { "book:$it" in olderKeys }
        }.map { "book:" + it.id }.toSet()
        return (saved - olderKeys) + newKeys
    }

    fun fromJson(source: String): List<TwoDReaderRule> {
        val root = JSONObject(source)
        require(root.getInt("schemaVersion") == 1)
        val rows = root.getJSONArray("rules")
        val rules = (0 until rows.length()).map { index ->
            val node = rows.getJSONObject(index)
            val section = node.getInt("section")
            require(section != 7) { "Better Data cannot appear in the Rules shelf" }
            val title = node.getString("title").trim()
            require(title.isNotBlank())
            require(!title.startsWith("အပိုင်း ("))
            require(!Regex("""^\([ကခဂဃင]\)""").containsMatchIn(title))
            require(!Regex("""^[0-9]+\.\s""").containsMatchIn(title))
            val table = node.optJSONObject("table")?.let { data ->
                TwoDReaderTable(
                    headings = data.getJSONArray("headers").strings(),
                    rows = data.getJSONArray("rows").let { tableRows ->
                        (0 until tableRows.length()).map { row ->
                            tableRows.getJSONArray(row).strings()
                        }
                    },
                ).also { value ->
                    require(value.headings.size >= 2)
                    require(value.rows.all { it.size == value.headings.size })
                }
            }
            TwoDReaderRule(
                id = node.getString("id"),
                title = title,
                section = section,
                text = node.getString("text").trim(),
                exampleLines = node.getJSONArray("example").strings(),
                table = table,
            )
        }
        require(rules.isNotEmpty())
        require(rules.map { it.id }.distinct().size == rules.size)
        require(rules.map { it.section }.toSet() == setOf(1, 2, 3, 4, 5, 6, 8, 9))
        return rules
    }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).map { getString(it) }

    private fun JSONArray.ints(): List<Int> =
        (0 until length()).map { getInt(it) }
}
