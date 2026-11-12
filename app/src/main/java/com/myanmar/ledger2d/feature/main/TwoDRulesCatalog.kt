package com.myanmar.ledger2d.feature.main

/**
 * Read-only reference library derived from the user-supplied 2D rules document.
 *
 * The stored text is NEVER rewritten. Titles and lookup metadata are navigation
 * aids only. Source section seven is deliberately excluded: it belongs to
 * Better Data, not the historical Analysis Rules library.
 */
internal data class TwoDRuleItem(
    val id: String,
    val label: String,
    val original: String,
    val section: Int,
    val category: String,
    val sourceNumbers: Set<String> = emptySet(),
    val relatedNumbers: Set<String> = emptySet(),
)

internal data class TwoDRuleGroup(
    val id: String,
    val title: String,
    val section: Int,
    val category: String,
    val items: List<TwoDRuleItem>,
)

internal class TwoDRulesCatalog private constructor(
    val groups: List<TwoDRuleGroup>,
) {
    val allItems: List<TwoDRuleItem> = groups.flatMap { it.items }
    val sections: Set<Int> = groups.map { it.section }.toSet()

    fun groupsIn(category: String): List<TwoDRuleGroup> =
        groups.filter { it.category == category }

    fun byNumber(number: String): List<TwoDRuleItem> {
        if (!number.matches(Regex("[0-9]{1,2}"))) return emptyList()
        return allItems.filter { number in it.sourceNumbers || number in it.relatedNumbers }
            .sortedWith(compareBy<TwoDRuleItem> { if (number in it.sourceNumbers) 0 else 1 }
                .thenBy { it.section }.thenBy { it.id })
    }

    fun search(term: String): List<TwoDRuleItem> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        val lowered = trimmed.lowercase()
        return allItems.filter { entry ->
            entry.original.lowercase().contains(lowered) ||
                entry.label.lowercase().contains(lowered) ||
                groups.firstOrNull { group -> group.items.any { it.id == entry.id } }
                    ?.title?.lowercase()?.contains(lowered) == true ||
                (trimmed.matches(Regex("[0-9]{1,2}")) &&
                    (trimmed in entry.sourceNumbers || trimmed in entry.relatedNumbers))
        }.distinctBy { it.id }.sortedWith(
            compareBy<TwoDRuleItem> {
                if (trimmed in it.sourceNumbers) 0
                else if (trimmed in it.relatedNumbers) 1
                else 2
            }.thenBy { it.section }.thenBy { it.id }
        )
    }

    companion object {
        private val sectionPattern = Regex("""^အပိုင်း \(([၁၂၃၄၅၆၇၈၉])\)""")
        private val subsectionPattern = Regex("""^\([ကခဂဃင]\)""")
        private val pairPattern = Regex("""(?<![0-9])[0-9]{2}(?![0-9])""")
        private val numberPattern = Regex("""(?<![0-9])[0-9]{1,2}(?![0-9])""")
        private val numberedPattern = Regex("""^[0-9]+\.\s""")
        private val doublePattern = Regex("""^\(([0-9]{2})\) ထွက်လျှင်""")

        fun fromSource(source: String): TwoDRulesCatalog {
            val parts = mutableMapOf<Int, MutableList<String>>()
            var section = 0
            source.lineSequence().forEach { raw ->
                val line = raw.trimEnd('\r')
                val heading = sectionPattern.find(line)
                if (heading != null) {
                    section = "၁၂၃၄၅၆၇၈၉".indexOf(heading.groupValues[1]) + 1
                }
                if (section != 0) parts.getOrPut(section) { mutableListOf() }.add(line)
            }
            require(7 !in parts) { "Better Data section must not enter Rules" }
            require(parts.keys.containsAll(setOf(1, 2, 3, 4, 5, 6, 8, 9))) {
                "Rules source is missing one or more source sections"
            }

            val result = mutableListOf<TwoDRuleGroup>()

            // The five original TABLE COLUMNS are five rules, each with ten
            // original unmodified cells. Rule-first view navigates the columns,
            // while number-first view intersects the same items by digit.
            val digitLines = parts.getValue(2).map { it.trim() }
            val columnStart = digitLines.indexOf("ဂဏန်း")
            require(columnStart >= 0)
            val originalColumns = digitLines.drop(columnStart + 1).take(5)
            require(originalColumns.size == 5)
            val digitRows = mutableMapOf<String, List<String>>()
            var i = columnStart + 6
            while (i < digitLines.size) {
                val value = digitLines[i]
                if (value.matches(Regex("[0-9]")) && i + 5 < digitLines.size) {
                    digitRows[value] = digitLines.subList(i + 1, i + 6)
                    i += 6
                } else i++
            }
            require(digitRows.size == 10) { "All ten digits must be present in section two" }
            originalColumns.forEachIndexed { column, title ->
                result += TwoDRuleGroup(
                    id = "s2-column-" + column, title = title,
                    section = 2, category = "digits",
                    items = (0..9).map { digit ->
                        val number = digit.toString()
                        val original = digitRows.getValue(number)[column]
                        TwoDRuleItem(
                            id = "s2-" + column + "-" + number,
                            label = number,
                            original = original,
                            section = 2, category = "digits",
                            sourceNumbers = setOf(number),
                            relatedNumbers = numbers(original) - number,
                        )
                    }
                )
            }

            // Section four's double table has two cells following each
            // heading. Keep the exact cell wording and its source heading.
            val doubleLines = parts.getValue(4).map { it.trim() }
            val doubles = mutableListOf<TwoDRuleItem>()
            doubleLines.forEachIndexed { index, line ->
                val match = doublePattern.find(line) ?: return@forEachIndexed
                require(index + 2 < doubleLines.size)
                val pair = match.groupValues[1]
                val exact = listOf(line, doubleLines[index + 1], doubleLines[index + 2])
                    .joinToString("\n")
                doubles += TwoDRuleItem(
                    id = "s4-" + pair, label = line, original = exact,
                    section = 4, category = "double",
                    sourceNumbers = setOf(pair), relatedNumbers = numbers(exact) - pair,
                )
            }
            require(doubles.size == 10)
            result += TwoDRuleGroup(
                "s4-double-table",
                "(က) အပူး (00 မှ 99) မွေးဂဏန်းနှင့် ၅ ရက်အတွင်း ၄ လုံး အပြီးခွေဇယား",
                4, "double", doubles,
            )

            // Section six's 26 numbered instructions are themselves intact,
            // not condensed into a synthetic 'trigger -> answer' formula.
            val born = parts.getValue(6)
                .map { it.trim() }.filter { numberedPattern.containsMatchIn(it) }
                .mapIndexed { index, line ->
                    val condition = line.substringBefore("ထွက်လျှင်")
                    val triggeringPairs = pairPattern.findAll(condition).map { it.value }.toSet()
                    TwoDRuleItem(
                        id = "s6-" + index, label = line.substringBefore(":").take(100),
                        original = line, section = 6, category = "born",
                        sourceNumbers = triggeringPairs,
                        relatedNumbers = numbers(line) - triggeringPairs,
                    )
                }
            require(born.size == 26)
            result += TwoDRuleGroup(
                "s6-born",
                "အပိုင်း (၆) - ထွက်ဂဏန်းတစ်ကွက်ချင်း အလိုက် မွေးရမည့် သီးသန့်ဂဏန်းများ (ငယ်စဉ်ကြီးလိုက်)",
                6, "born", born,
            )

            // Everything else is recorded by subsection, with each source
            // bullet/paragraph preserved verbatim as one swipeable entry.
            listOf(1, 3, 4, 5, 8, 9).forEach { part ->
                val category = when (part) {
                    1, 5 -> "days"
                    3 -> "groups"
                    4 -> "double"
                    else -> "special"
                }
                var heading = ""
                var groupIndex = 0
                var items = mutableListOf<TwoDRuleItem>()
                fun flush() {
                    if (items.isNotEmpty()) {
                        result += TwoDRuleGroup(
                            id = "s" + part + "-sub-" + groupIndex,
                            title = heading, section = part, category = category,
                            items = items.toList(),
                        )
                        items = mutableListOf()
                    }
                }
                parts.getValue(part).forEach { sourceLine ->
                    val line = sourceLine.trim()
                    when {
                        line.isEmpty() || line.startsWith("____") ||
                            sectionPattern.containsMatchIn(line) -> Unit
                        subsectionPattern.containsMatchIn(line) -> {
                            flush()
                            groupIndex++
                            heading = line
                        }
                        part == 4 && groupIndex <= 1 -> Unit // the ten double rows are indexed above
                        else -> {
                            if (heading.isEmpty()) heading = "အပိုင်း (" + part + ")"
                            val continuation = line.startsWith("1.") ||
                                line.startsWith("2.") ||
                                (sourceLine.startsWith("   ") &&
                                    !line.startsWith("*"))
                            val isNew = line.startsWith("*") ||
                                numberedPattern.containsMatchIn(line) && !continuation
                            if (items.isNotEmpty() && !isNew &&
                                (continuation || !line.startsWith("*"))) {
                                val previous = items.removeAt(items.lastIndex)
                                val joined = previous.original + "\n" + sourceLine
                                items += previous.copy(
                                    original = joined,
                                    relatedNumbers = numbers(joined) - previous.sourceNumbers
                                )
                            } else {
                                val text = sourceLine
                                items += TwoDRuleItem(
                                    id = "s" + part + "-g" + groupIndex + "-i" + items.size,
                                    label = line.substringBefore(":").take(90),
                                    original = text,
                                    section = part, category = category,
                                    relatedNumbers = numbers(text),
                                )
                            }
                        }
                    }
                }
                flush()
            }

            return TwoDRulesCatalog(result.sortedWith(
                compareBy<TwoDRuleGroup> { it.section }.thenBy { it.id }
            ))
        }

        private fun numbers(s: String): Set<String> =
            numberPattern.findAll(s).map { it.value }.toSet()
    }
}
