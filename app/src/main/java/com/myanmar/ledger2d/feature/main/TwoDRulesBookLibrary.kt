package com.myanmar.ledger2d.feature.main

/**
 * An original-source "book" is exactly one titled subsection in the supplied
 * document, or one whole section when that section has no subsections.
 *
 * Titles, lines, and the document order come from the source, not inferred rules.
 * Source section (7) is intentionally excluded because it belongs to Better Data.
 */
internal data class TwoDRulesBook(
    val id: String,
    val title: String,
    val sectionTitle: String,
    val section: Int,
    val lines: List<String>,
    val kind: String,
) {
    val originalText: String
        get() = (if (title == sectionTitle) listOf(title) else listOf(sectionTitle, title))
            .plus(lines).joinToString("\n")
}

internal object TwoDRulesBookLibrary {
    private val sectionHeader = Regex("""^အပိုင်း \(([၁၂၃၄၅၆၇၈၉])\)""")
    private val subsectionHeader = Regex("""^\([ကခဂဃင]\)""")
    private const val numerals = "၁၂၃၄၅၆၇၈၉"

    fun fromSource(source: String): List<TwoDRulesBook> {
        val books = mutableListOf<TwoDRulesBook>()
        var section = 0
        var sectionTitle = ""
        var title = ""
        var subsection = 0
        var body = mutableListOf<String>()

        fun flush() {
            if (section == 0 || title.isEmpty()) return
            val lines = body.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            if (lines.isNotEmpty()) {
                books += TwoDRulesBook(
                    id = "source-$section-$subsection",
                    title = title,
                    sectionTitle = sectionTitle,
                    section = section,
                    lines = lines,
                    kind = when {
                        section == 2 -> "digit-table"
                        section == 4 && subsection == 1 -> "double-table"
                        else -> "text"
                    },
                )
            }
            body = mutableListOf()
        }

        source.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            val match = sectionHeader.find(line)
            when {
                match != null -> {
                    flush()
                    section = numerals.indexOf(match.groupValues[1]) + 1
                    require(section != 7) { "Better Data section cannot appear in Rules" }
                    sectionTitle = line
                    title = line
                    subsection = 0
                }
                section == 0 -> Unit
                subsectionHeader.containsMatchIn(line.trimStart()) -> {
                    flush()
                    subsection++
                    title = line.trimStart()
                }
                line.trim().matches(Regex("_+")) -> Unit
                else -> body += line
            }
        }
        flush()
        require(books.map { it.section }.toSet() ==
            setOf(1, 2, 3, 4, 5, 6, 8, 9)) {
            "Original Rules sections must be complete and section (7) absent"
        }
        return books
    }
}
