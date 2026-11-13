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

internal object TwoDReaderCatalog {
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
}
