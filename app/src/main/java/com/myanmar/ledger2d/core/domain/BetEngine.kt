package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.ExpandedBet

enum class QuickFormat(val label: String) {
    MANUAL("Manual"), POWER("ပါဝါ"), ASTROLOGY("နက္ခတ်"), DOUBLES("အပူး"), SIBLINGS("ညီအကို"), COMBINATION("အခွေ"), COMBINATION_DOUBLES("အခွေပူး"), ROUND("ပတ်သီး"), HEAD("ထိပ်စည်း"), TAIL("နောက်ပိတ်")
}
sealed interface ParseResult { data class Success(val bets: List<ExpandedBet>, val total: Long) : ParseResult; data class Error(val message: String) : ParseResult }

class BetParser {
    private val reverse = Regex("^([0-9]{2}(?:[.\\-/\\s]+[0-9]{2})*)\\s*[Rr]\\s*([0-9]+)$")
    private val separators = Regex("[.\\-/\\s]+")
    fun parse(raw: String): ParseResult {
        if (raw.isBlank()) return ParseResult.Error("Input is required")
        val expanded = mutableListOf<ExpandedBet>()
        for (line in raw.lines().map(String::trim).filter(String::isNotEmpty)) {
            // A whitespace-delimited decimal-looking amount is never valid MMK.
            // Keep dot-separated digit syntax such as `10.13.14 100` intact.
            if (Regex("\\d+\\s+\\d+\\.\\d+").containsMatchIn(line)) {
                return ParseResult.Error("Amount must be a positive MMK integer")
            }
            val reverseMatch = reverse.matchEntire(line)
            if (reverseMatch != null) {
                val amount = positiveAmount(reverseMatch.groupValues[2]) ?: return ParseResult.Error("Amount must be a positive MMK integer")
                val digits = reverseMatch.groupValues[1].split(separators).filter(String::isNotBlank)
                for (digit in digits) {
                    if (!validDigit(digit)) return ParseResult.Error("Digit must be exactly 00–99")
                    expanded += ExpandedBet(digit, amount)
                    expanded += ExpandedBet(digit.reversed(), amount)
                }
            } else {
                val tokens = line.split(separators).filter(String::isNotBlank)
                if (tokens.size < 2) return ParseResult.Error("Enter digit and amount")
                val amount = positiveAmount(tokens.last()) ?: return ParseResult.Error("Amount must be a positive MMK integer")
                for (digit in tokens.dropLast(1)) {
                    if (!validDigit(digit)) return ParseResult.Error("Digit must be exactly 00–99")
                    expanded += ExpandedBet(digit, amount)
                }
            }
        }
        return aggregate(expanded)
    }
    internal fun aggregate(values: List<ExpandedBet>): ParseResult = try {
        val grouped = linkedMapOf<String, Long>()
        values.forEach { grouped[it.digit] = Math.addExact(grouped[it.digit] ?: 0, it.amount) }
        val bets = grouped.map { ExpandedBet(it.key, it.value) }.sortedBy { it.digit }
        ParseResult.Success(bets, bets.fold(0L) { sum, bet -> Math.addExact(sum, bet.amount) })
    } catch (_: ArithmeticException) { ParseResult.Error("Amount is too large") }
    private fun positiveAmount(value: String) = value.toLongOrNull()?.takeIf { it > 0 }
    companion object { fun validDigit(value: String) = value.length == 2 && value.all { it in '0'..'9' } }
}

class BetExpansionEngine(private val parser: BetParser = BetParser()) {
    private val power = listOf("05","50","16","61","27","72","38","83","49","94")
    private val astrology = listOf("07","70","18","81","24","42","35","53","69","96")
    private val doubles = (0..9).map { "$it$it" }
    private val siblings = listOf("01","10","12","21","23","32","34","43","45","54","56","65","67","76","78","87","89","98","09","90")
    fun expand(raw: String, format: QuickFormat = QuickFormat.MANUAL): ParseResult = when (format) {
        QuickFormat.MANUAL -> parser.parse(raw)
        QuickFormat.POWER -> fixed(raw, power)
        QuickFormat.ASTROLOGY -> fixed(raw, astrology)
        QuickFormat.DOUBLES -> fixed(raw, doubles)
        QuickFormat.SIBLINGS -> fixed(raw, siblings)
        QuickFormat.COMBINATION -> combination(raw, false)
        QuickFormat.COMBINATION_DOUBLES -> combination(raw, true)
        QuickFormat.ROUND -> singleDigit(raw) { source -> (0..9).flatMap { listOf("$it$source", "$source$it") }.distinct() }
        QuickFormat.HEAD -> singleDigit(raw) { source -> (0..9).map { "$source$it" } }
        QuickFormat.TAIL -> singleDigit(raw) { source -> (0..9).map { "$it$source" } }
    }
    private fun fixed(raw: String, digits: List<String>): ParseResult {
        val amount = raw.trim().toLongOrNull()?.takeIf { it > 0 } ?: return ParseResult.Error("Enter a positive amount")
        return parser.aggregate(digits.map { ExpandedBet(it, amount) })
    }
    private fun sourceAndAmount(raw: String): Pair<String, Long>? {
        val match = Regex("^([0-9]+)[.\\-/\\s]+([0-9]+)$").matchEntire(raw.trim()) ?: return null
        return match.groupValues[1] to (match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null)
    }
    private fun combination(raw: String, withDoubles: Boolean): ParseResult {
        val (source, amount) = sourceAndAmount(raw) ?: return ParseResult.Error("Enter source digits and amount")
        if (source.length < 3) return ParseResult.Error("အခွေ requires at least 3 digits")
        val values = mutableListOf<ExpandedBet>()
        source.indices.forEach { i -> ((i + 1)..source.lastIndex).forEach { j -> values += ExpandedBet("${source[i]}${source[j]}", amount); values += ExpandedBet("${source[j]}${source[i]}", amount) } }
        if (withDoubles) source.forEach { values += ExpandedBet("$it$it", amount) }
        return parser.aggregate(values)
    }
    private fun singleDigit(raw: String, producer: (Char) -> List<String>): ParseResult {
        val (source, amount) = sourceAndAmount(raw) ?: return ParseResult.Error("Enter one digit and amount")
        if (source.length != 1) return ParseResult.Error("Format requires one source digit")
        return parser.aggregate(producer(source.single()).map { ExpandedBet(it, amount) })
    }
}
