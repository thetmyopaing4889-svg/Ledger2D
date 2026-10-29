package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.ExpandedBet

enum class QuickFormat(val label: String) {
    MANUAL("လက်ဖြင့်"),
    POWER("ပါဝါ"), ASTROLOGY("နက္ခတ်"), DOUBLES("အပူး"),
    SIBLINGS("ညီအကို"), COMBINATION("အခွေ"),
    COMBINATION_DOUBLES("အခွေပူး"), ROUND("ပတ်သီး"),
    HEAD("ထိပ်စည်း"), TAIL("နောက်ပိတ်"),
    EVEN_ODD("စုံမ"), ODD_EVEN("မစုံ"),
    EVEN_EVEN("စုံစုံ"), ODD_ODD("မမ"),
}

sealed interface ParseResult {
    data class Success(val bets: List<ExpandedBet>, val total: Long) : ParseResult
    data class Error(val message: String) : ParseResult
}

data class SmartLine(val source: String, val format: QuickFormat, val result: ParseResult)
data class SmartParseResult(val lines: List<SmartLine>, val bets: List<ExpandedBet>, val total: Long) {
    val hasErrors get() = lines.any { it.result is ParseResult.Error }
}

/** Convert Myanmar numerals only for parsing; the original message is persisted untouched. */
private fun normalDigits(raw: String): String = buildString(raw.length) {
    raw.forEach { ch ->
        when (ch) {
            in '၀'..'၉' -> append(('0'.code + (ch.code - '၀'.code)).toChar())
            '\u200b', '\u200c', '\ufeff' -> Unit
            else -> append(ch)
        }
    }
}

/**
 * Distinguish money "1,000" from the separator in "01=1000,02=500".
 * Empty entries between commas remain errors, never silently accepted.
 * Blank Messenger lines are harmless.
 */
private fun entrySegments(raw: String): List<String> {
    // A grouped-money comma must end at a delimiter, space, or 'ကျပ်'.
    // "11 500,456အခွေ1000" is TWO bets, not 500456 kyats.
    val normalized = Regex("""(?<!\d)[0-9]{1,3}(?:,[0-9]{3})+(?=$|[\s,၊]|ကျပ်)""")
        .replace(normalDigits(raw)) { it.value.replace(",", "") }
    val result = mutableListOf<String>()
    for (line in normalized.lines()) {
        if (line.isBlank()) continue
        val commaParts = line.split(Regex("[,၊]"), limit = Int.MAX_VALUE)
        commaParts.forEachIndexed { index, part ->
            if (part.isBlank()) {
                if (index != commaParts.lastIndex) result += ""
            } else {
                // A two-digit token followed by ":" may also be another
                // digit in "01 02 03:1000", NOT a second bet. Only separate
                // adjacent entries when the left side has a clear amount.
                val source = part.trim()
                val boundary = Regex("""\s+(?=\d{2}\s*[=:/-]\s*\d)""")
                val separated = mutableListOf<String>()
                var start = 0
                for (match in boundary.findAll(source)) {
                    val left = source.substring(start, match.range.first).trim()
                    val priced = Regex("""[=:]\s*\d+(?:[kK]|ထောင်|ကျပ်)?$""").containsMatchIn(left) ||
                        Regex("""[.\-/\s]\s*\d{3,}(?:[kK]|ထောင်|ကျပ်)?$""").containsMatchIn(left)
                    if (priced) {
                        separated += left
                        start = match.range.last + 1
                    }
                }
                separated += source.substring(start).trim()
                result += separated
            }
        }
    }
    return result
}

private fun hasExplicitNegativeAmount(raw: String): Boolean =
    Regex("""(?:[=:]\s*-\s*|\s+-\s*)\d""").containsMatchIn(raw)

private fun positiveAmount(raw: String): Long? {
    val cleaned = raw.trim().removeSuffix("ကျပ်").trim()
    val match = Regex("""^([0-9]+)\s*([kK]|ထောင်)?$""").matchEntire(cleaned) ?: return null
    val amount = match.groupValues[1].toLongOrNull() ?: return null
    if (amount <= 0L) return null
    return try {
        if (match.groupValues[2].isEmpty()) amount else Math.multiplyExact(amount, 1000L)
    } catch (_: ArithmeticException) { null }
}

class BetParser {
    private val reverse = Regex(
        """^([0-9]{2}(?:[.\-/=:\s]+[0-9]{2})*)\s*(?:[Rr]|ပြောင်းပြန်|ပြန်)\s*[=:/-]?\s*([0-9]+\s*(?:[kK]|ထောင်|ကျပ်)?)$""",
    )
    private val separators = Regex("""[.\-/=:\s]+""")

    fun parse(raw: String): ParseResult {
        if (raw.isBlank()) return ParseResult.Error("Input is required")
        val expanded = mutableListOf<ExpandedBet>()
        val segments = entrySegments(raw)
        if (segments.isEmpty()) return ParseResult.Error("Enter digit and amount")
        for (line in segments) {
            if (line.isEmpty()) return ParseResult.Error("Each entry must contain a digit and amount")
            if (hasExplicitNegativeAmount(line)) {
                return ParseResult.Error("Amount must be a positive MMK integer")
            }
            if (Regex("""\d+\s+-\s*\d+""").containsMatchIn(line)) {
                return ParseResult.Error("Amount must be a positive MMK integer")
            }
            if (Regex("""\d+\s+\d+\.\d+""").containsMatchIn(line)) {
                return ParseResult.Error("Amount must be a positive MMK integer")
            }
            val reversed = reverse.matchEntire(line)
            if (reversed != null) {
                val amount = positiveAmount(reversed.groupValues[2])
                    ?: return ParseResult.Error("Amount must be a positive MMK integer")
                val digits = reversed.groupValues[1].split(separators).filter(String::isNotBlank)
                for (digit in digits) {
                    if (!validDigit(digit)) return ParseResult.Error("Digit must be exactly 00–99")
                    expanded += ExpandedBet(digit, amount)
                    // Includes doubles: 11R1000 is 11 = 2000, as requested.
                    expanded += ExpandedBet(digit.reversed(), amount)
                }
            } else {
                val tokens = line.split(separators).filter(String::isNotBlank)
                if (tokens.size < 2) return ParseResult.Error("Enter digit and amount")
                val amount = positiveAmount(tokens.last())
                    ?: return ParseResult.Error("Amount must be a positive MMK integer")
                for (digit in tokens.dropLast(1)) {
                    if (!validDigit(digit)) return ParseResult.Error("Digit must be exactly 00–99")
                    expanded += ExpandedBet(digit, amount)
                }
            }
        }
        return aggregate(expanded)
    }

    internal fun isExplicitReverse(raw: String): Boolean =
        reverse.matches(normalDigits(raw))

    internal fun aggregate(values: List<ExpandedBet>): ParseResult = try {
        val grouped = linkedMapOf<String, Long>()
        values.forEach { grouped[it.digit] = Math.addExact(grouped[it.digit] ?: 0L, it.amount) }
        val bets = grouped.map { ExpandedBet(it.key, it.value) }.sortedBy { it.digit }
        ParseResult.Success(bets, bets.fold(0L) { sum, bet -> Math.addExact(sum, bet.amount) })
    } catch (_: ArithmeticException) {
        ParseResult.Error("Amount is too large")
    }

    companion object {
        fun validDigit(value: String) = value.length == 2 && value.all { it in '0'..'9' }
    }
}

class BetExpansionEngine(private val parser: BetParser = BetParser()) {
    private val power = listOf("05", "50", "16", "61", "27", "72", "38", "83", "49", "94")
    private val astrology = listOf("07", "70", "18", "81", "24", "42", "35", "53", "69", "96")
    private val doubles = (0..9).map { "$it$it" }
    private val siblings = listOf(
        "01", "10", "12", "21", "23", "32", "34", "43", "45", "54",
        "56", "65", "67", "76", "78", "87", "89", "98", "09", "90",
    )
    // Longest matching alias wins. E.g. ပါဝါ before ပါ, ခွေပူး before ခွေ.
    private val aliases = listOf(
        "အခွေပူး" to QuickFormat.COMBINATION_DOUBLES,
        "အခေပူး" to QuickFormat.COMBINATION_DOUBLES,
        "ခွေပူး" to QuickFormat.COMBINATION_DOUBLES,
        "ခေပူး" to QuickFormat.COMBINATION_DOUBLES,
        "အခွေ" to QuickFormat.COMBINATION, "အခေ" to QuickFormat.COMBINATION,
        "ခွေ" to QuickFormat.COMBINATION, "ခေ" to QuickFormat.COMBINATION,
        "ပတ်သီး" to QuickFormat.ROUND, "အပါ" to QuickFormat.ROUND,
        "ပတ်" to QuickFormat.ROUND, "ပါ" to QuickFormat.ROUND,
        "ပါဝါ" to QuickFormat.POWER, "Power" to QuickFormat.POWER,
        "နက္ခတ်" to QuickFormat.ASTROLOGY, "နက်ခတ်" to QuickFormat.ASTROLOGY,
        "အပူး" to QuickFormat.DOUBLES, "ပူး" to QuickFormat.DOUBLES,
        "ညီအကို" to QuickFormat.SIBLINGS, "ညီကို" to QuickFormat.SIBLINGS,
        "ထိပ်စည်း" to QuickFormat.HEAD, "ထိပ်" to QuickFormat.HEAD,
        "နောက်ပိတ်" to QuickFormat.TAIL, "နောက်" to QuickFormat.TAIL,
        "စုံမ" to QuickFormat.EVEN_ODD, "မစုံ" to QuickFormat.ODD_EVEN,
        "စုံစုံ" to QuickFormat.EVEN_EVEN, "မမ" to QuickFormat.ODD_ODD,
    ).sortedByDescending { it.first.length }

    fun expand(raw: String, format: QuickFormat = QuickFormat.MANUAL): ParseResult =
        if (format == QuickFormat.MANUAL) parser.parse(raw) else expandParts(raw, format)

    /**
     * Messenger paste: independently detect each line's label (or use the chip
     * as fallback). Unknown numeric lines always fail; no partial confirmations.
     */
    fun smartExpand(raw: String, fallback: QuickFormat = QuickFormat.MANUAL): SmartParseResult {
        if (raw.isBlank()) return SmartParseResult(
            listOf(SmartLine(raw, fallback, ParseResult.Error("Enter digit and amount"))),
            emptyList(), 0L,
        )
        val sources = entrySegments(raw).filterNot(::isMetadata)
        val lines = sources.map { source ->
            val matched = findAlias(source)
            // An explicit two-digit R command takes priority over a chip fallback.
            // "9r1000" under the ROUND chip is still a round separator.
            val format = if (matched == null && parser.isExplicitReverse(source)) {
                QuickFormat.MANUAL
            } else matched?.second ?: fallback
            val cleaned = if (matched == null) source else
                source.replace(matched.first, " ", ignoreCase = true)
                    .trim()
                    // Fixed formats can be written as "စုံမ=1000" or "ပူး:500".
                    .trimStart { it.isWhitespace() || it in "=:/.-" }
            val result = if (hasExplicitNegativeAmount(source)) {
                ParseResult.Error("Amount must be a positive MMK integer")
            } else expand(cleaned, format)
            SmartLine(source, format, result)
        }
        if (lines.isEmpty()) return SmartParseResult(
            listOf(SmartLine(raw, fallback, ParseResult.Error("Enter digit and amount"))),
            emptyList(), 0L,
        )
        val valid = lines.flatMap { (it.result as? ParseResult.Success)?.bets.orEmpty() }
        val combined = parser.aggregate(valid)
        return when (combined) {
            is ParseResult.Success -> SmartParseResult(lines, combined.bets, combined.total)
            is ParseResult.Error -> SmartParseResult(
                lines + SmartLine(raw, fallback, combined), emptyList(), 0L,
            )
        }
    }

    private fun findAlias(source: String): Pair<String, QuickFormat>? =
        aliases.firstOrNull { (alias, _) -> source.contains(alias, ignoreCase = true) }

    private fun isMetadata(source: String): Boolean {
        val s = source.trim()
        if (s.matches(Regex("""^(မနက်|ညနေ|နံနက်|မနက်ပိုင်း|ညနေပိုင်း)$"""))) return true
        if (s.matches(Regex("""^(စုစုပေါင်း|စုစုပေါင်းငွေ|total)\s*[:=]?\s*\d+\s*(?:ကျပ်)?$""", RegexOption.IGNORE_CASE))) {
            return true
        }
        // Explicitly labelled Messenger headers are not stakes.
        if (s.matches(Regex("""^(အမည်|နာမည်|name)\s*[:：].+$""", RegexOption.IGNORE_CASE))) return true
        return false
    }

    private fun expandParts(raw: String, format: QuickFormat): ParseResult {
        val parts = entrySegments(raw)
        if (parts.isEmpty()) return ParseResult.Error("Input is required")
        val values = mutableListOf<ExpandedBet>()
        for (part in parts) {
            if (part.isEmpty()) return ParseResult.Error("Each entry must contain a valid format")
            when (val parsed = expandSingle(part, format)) {
                is ParseResult.Error -> return parsed
                is ParseResult.Success -> values += parsed.bets
            }
        }
        return parser.aggregate(values)
    }

    private fun expandSingle(raw: String, format: QuickFormat): ParseResult = when (format) {
        QuickFormat.MANUAL -> parser.parse(raw)
        QuickFormat.POWER -> fixed(raw, power)
        QuickFormat.ASTROLOGY -> fixed(raw, astrology)
        QuickFormat.DOUBLES -> fixed(raw, doubles)
        QuickFormat.SIBLINGS -> fixed(raw, siblings)
        QuickFormat.COMBINATION -> combination(raw, false)
        QuickFormat.COMBINATION_DOUBLES -> combination(raw, true)
        QuickFormat.ROUND -> singleDigit(raw) { source ->
            (0..9).flatMap { listOf("$it$source", "$source$it") }.distinct()
        }
        QuickFormat.HEAD -> singleDigit(raw) { source ->
            (0..9).map { "$source$it" }
        }
        QuickFormat.TAIL -> singleDigit(raw) { source ->
            (0..9).map { "$it$source" }
        }
        QuickFormat.EVEN_ODD -> parity(0, 1, raw)
        QuickFormat.ODD_EVEN -> parity(1, 0, raw)
        QuickFormat.EVEN_EVEN -> parity(0, 0, raw)
        QuickFormat.ODD_ODD -> parity(1, 1, raw)
    }

    private fun fixed(raw: String, digits: List<String>): ParseResult {
        val amount = positiveAmount(raw) ?: return ParseResult.Error("Enter a positive amount")
        return parser.aggregate(digits.map { ExpandedBet(it, amount) })
    }

    private fun parity(first: Int, second: Int, raw: String): ParseResult {
        val amount = positiveAmount(raw) ?: return ParseResult.Error("Enter a positive amount")
        val bets = (first..9 step 2).flatMap { a ->
            (second..9 step 2).map { b -> ExpandedBet("$a$b", amount) }
        }
        return parser.aggregate(bets)
    }

    private fun sourceAndAmount(raw: String): Pair<String, Long>? {
        val match = Regex(
            """^([0-9]+)[.\-/=:\sRr]+([0-9]+\s*(?:[kK]|ထောင်|ကျပ်)?)$""",
        ).matchEntire(raw.trim()) ?: return null
        val amount = positiveAmount(match.groupValues[2]) ?: return null
        return match.groupValues[1] to amount
    }

    private fun combination(raw: String, withDoubles: Boolean): ParseResult {
        val (source, amount) = sourceAndAmount(raw)
            ?: return ParseResult.Error("Enter source digits and amount")
        if (source.length < 3) return ParseResult.Error("အခွေ requires at least 3 digits")
        // Repeated source digits are ambiguous, even if aggregation would work.
        if (source.toSet().size != source.length) {
            return ParseResult.Error("အခွေဂဏန်းတွင် ဂဏန်းထပ်နေပါသည်")
        }
        val values = mutableListOf<ExpandedBet>()
        source.indices.forEach { i ->
            ((i + 1)..source.lastIndex).forEach { j ->
                values += ExpandedBet("${source[i]}${source[j]}", amount)
                values += ExpandedBet("${source[j]}${source[i]}", amount)
            }
        }
        if (withDoubles) source.forEach { values += ExpandedBet("$it$it", amount) }
        return parser.aggregate(values)
    }

    private fun singleDigit(
        raw: String,
        producer: (Char) -> List<String>,
    ): ParseResult {
        val (source, amount) = sourceAndAmount(raw)
            ?: return ParseResult.Error("Enter one digit and amount")
        if (source.length != 1) return ParseResult.Error("Format requires one source digit")
        return parser.aggregate(producer(source.single()).map { ExpandedBet(it, amount) })
    }
}
