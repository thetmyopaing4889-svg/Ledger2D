package com.myanmar.ledger2d.core.domain

/** Purely presentational views over the already saved per-session 00..99 stakes. */
enum class DigitViewFilter { ALL, STAKED, EMPTY }
enum class DigitViewSort { NUMBER, STAKE_ASC, STAKE_DESC }

data class DigitViewRow(val digit: String, val stake: Long)

object DigitViewSelector {
    /** Accept ASCII or Myanmar digits; one digit matches anywhere (e.g. 3 -> 03, 30, 33). */
    fun normalizeSearch(raw: String): String = raw.mapNotNull { ch ->
        when (ch) {
            in '0'..'9' -> ch
            in '၀'..'၉' -> ('0'.code + ch.code - '၀'.code).toChar()
            else -> null
        }
    }.take(2).joinToString("")

    fun select(
        amounts: Map<String, Long>,
        filter: DigitViewFilter = DigitViewFilter.ALL,
        sort: DigitViewSort = DigitViewSort.NUMBER,
        search: String = "",
    ): List<DigitViewRow> {
        val query = normalizeSearch(search)
        val rows = (0..99).map { number ->
            val digit = number.toString().padStart(2, '0')
            DigitViewRow(digit, amounts[digit] ?: 0L)
        }.filter { row ->
            when (filter) {
                DigitViewFilter.ALL -> true
                DigitViewFilter.STAKED -> row.stake > 0L
                DigitViewFilter.EMPTY -> row.stake == 0L
            }
        }.filter { row -> query.isEmpty() || query in row.digit }

        return if (filter != DigitViewFilter.STAKED) rows else when (sort) {
            DigitViewSort.NUMBER -> rows
            DigitViewSort.STAKE_ASC -> rows.sortedWith(compareBy<DigitViewRow> { it.stake }.thenBy { it.digit })
            DigitViewSort.STAKE_DESC -> rows.sortedWith(compareByDescending<DigitViewRow> { it.stake }.thenBy { it.digit })
        }
    }
}
