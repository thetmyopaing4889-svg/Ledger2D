package com.myanmar.ledger2d.core.domain

/** Shared 2D category definitions used by Quick Entry and historical analysis. */
object TwoDigitGroups {
    val power = listOf("05", "50", "16", "61", "27", "72", "38", "83", "49", "94")
    val astrology = listOf("07", "70", "18", "81", "24", "42", "35", "53", "69", "96")
    val doubles = (0..9).map { "$it$it" }
    val siblings = listOf(
        "01", "10", "12", "21", "23", "32", "34", "43", "45", "54",
        "56", "65", "67", "76", "78", "87", "89", "98", "09", "90",
    )

    /** Categories may overlap. Never force an outcome into only one group. */
    val named: Map<String, Set<String>> = linkedMapOf(
        "အပူး" to doubles.toSet(),
        "ညီကို" to siblings.toSet(),
        "ပါဝါ" to power.toSet(),
        "နက္ခတ်" to astrology.toSet(),
    )

    fun parity(digit: String): String {
        require(validHistoric2D(digit))
        val tensEven = (digit[0] - '0') % 2 == 0
        val onesEven = (digit[1] - '0') % 2 == 0
        return when {
            tensEven && onesEven -> "စုံစုံ"
            !tensEven && !onesEven -> "မမ"
            tensEven -> "စုံမ"
            else -> "မစုံ"
        }
    }

    fun counts(digits: List<String>): Map<String, Int> =
        named.mapValues { (_, numbers) -> digits.count { it in numbers } }
}
