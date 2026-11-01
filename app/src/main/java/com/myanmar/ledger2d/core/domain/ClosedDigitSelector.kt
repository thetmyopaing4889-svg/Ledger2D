package com.myanmar.ledger2d.core.domain

/** Read-only preview; actual closed-number writes are always session scoped. */
sealed interface ClosedDigitSelection {
    data class Valid(val digits: List<String>) : ClosedDigitSelection
    data class Invalid(val message: String) : ClosedDigitSelection
}

/** Shares ALL patterned-number definitions with Quick Entry. No separate pattern lists. */
object ClosedDigitSelector {
    private val engine = BetExpansionEngine()
    private val needsSource = setOf(
        QuickFormat.MANUAL,
        QuickFormat.COMBINATION, QuickFormat.COMBINATION_DOUBLES,
        QuickFormat.ROUND, QuickFormat.HEAD, QuickFormat.TAIL,
    )

    fun requiresInput(format: QuickFormat): Boolean = format in needsSource

    fun select(format: QuickFormat, source: String = ""): ClosedDigitSelection {
        val normalized = buildString {
            source.trim().forEach { c ->
                append(if (c in '၀'..'၉') ('0'.code + c.code - '၀'.code).toChar() else c)
            }
        }
        if (format == QuickFormat.MANUAL) {
            val reversed = normalized.endsWith("R", ignoreCase = true)
            val digitsText = if (reversed) normalized.dropLast(1) else normalized
            val digits = digitsText.split(Regex("""[\s.,/;:|=\-]+"""))
                .filter(String::isNotBlank)
            if (digits.isEmpty()) return ClosedDigitSelection.Invalid("ဂဏန်း 00–99 ထည့်ပါ")
            if (digits.any { !BetParser.validDigit(it) }) {
                return ClosedDigitSelection.Invalid("ဂဏန်းနှစ်လုံးစီ 00–99 သာ ထည့်ပါ")
            }
            return ClosedDigitSelection.Valid(
                (digits + if (reversed) digits.map { it.reversed() } else emptyList())
                    .distinct().sorted()
            )
        }

        val input = when (format) {
            QuickFormat.HEAD, QuickFormat.TAIL, QuickFormat.ROUND -> {
                if (!normalized.matches(Regex("[0-9]"))) {
                    return ClosedDigitSelection.Invalid("0–9 ထဲမှ တစ်လုံးသာ ရွေးပါ")
                }
                "$normalized.1"
            }
            QuickFormat.COMBINATION, QuickFormat.COMBINATION_DOUBLES -> {
                if (normalized.length < 3 || normalized.any { it !in '0'..'9' }) {
                    return ClosedDigitSelection.Invalid("ထပ်မနေသော အခွေဂဏန်း အနည်းဆုံး ၃ လုံး ထည့်ပါ")
                }
                "$normalized.1"
            }
            else -> "1"
        }
        return when (val expanded = engine.expand(input, format)) {
            is ParseResult.Success ->
                ClosedDigitSelection.Valid(expanded.bets.map { it.digit }.distinct().sorted())
            is ParseResult.Error -> ClosedDigitSelection.Invalid(expanded.message)
        }
    }
}
