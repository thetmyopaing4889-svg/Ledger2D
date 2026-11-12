package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Reconciled-history coverage, not an assertion about the original provider.
 *
 * Open slots with no verified row are UNVERIFIED (not negative outcomes, and
 * not proof that a draw was held). Dates before the earliest eligible record
 * are unknown and deliberately not counted. Call only with the app's
 * Historic2DDataSource.resolve draw stream.
 */
data class TwoDResearchHistoryAudit(
    val sourceRows: Int,
    val verifiedSlots: Int,
    val earliestVerified: DrawIdentity?,
    val latestVerified: DrawIdentity?,
    val invalidPairRows: Int,
    val nonWorkingDayRows: Int,
    val atOrAfterCutoffRows: Int,
    val duplicateSlotRows: Int,
    val conflictingSlots: Int,
    val expectedOpenSlots: Int,
    val unverifiedOpenSlots: List<DrawIdentity>,
) {
    val unverifiedCount: Int get() = unverifiedOpenSlots.size
    val coverageComplete: Boolean get() = expectedOpenSlots > 0 && unverifiedCount == 0
    val coverageRatio: Double? get() =
        if (expectedOpenSlots == 0) null
        else verifiedSlots.toDouble() / expectedOpenSlots.toDouble()
}

/** Deterministic diagnostics. Does not modify the user's records. */
object TwoDResearchHistoryAuditor {
    private fun validPair(pair: String) =
        pair.length == 2 && pair.all { it in '0'..'9' }

    private fun before(date: LocalDate, session: DrawSession, cutoff: DrawIdentity) =
        date.isBefore(cutoff.date) ||
            (date == cutoff.date && session.ordinal < cutoff.session.ordinal)

    fun inspect(
        rows: List<HistoricDraw>,
        cutoffExclusive: DrawIdentity,
        verifiedClosedDays: Set<LocalDate> = emptySet(),
    ): TwoDResearchHistoryAudit {
        val valid = mutableListOf<HistoricDraw>()
        var invalid = 0
        var nonWorking = 0
        var beyond = 0
        rows.forEach { row ->
            when {
                !validPair(row.digit) -> invalid++
                !DrawSchedule.isWorkingDate(row.date, verifiedClosedDays) -> nonWorking++
                !before(row.date, row.session, cutoffExclusive) -> beyond++
                else -> valid.add(row)
            }
        }

        val groups = valid.groupBy { it.date to it.session }
        val authoritative = groups.values.map { it.first() }
            .sortedWith(compareBy<HistoricDraw> { it.date }.thenBy { it.session.ordinal })
        val duplicates = groups.values.sumOf { it.size - 1 }
        val conflicting = groups.values.count { group ->
            group.map { it.digit }.distinct().size > 1
        }

        val missing = mutableListOf<DrawIdentity>()
        val first = authoritative.firstOrNull()
        val observedSlots = groups.keys
        if (first != null) {
            var day = first.date
            while (!day.isAfter(cutoffExclusive.date)) {
                if (DrawSchedule.isWorkingDate(day, verifiedClosedDays)) {
                    for (session in listOf(DrawSession.MORNING, DrawSession.EVENING)) {
                        if (before(day, session, cutoffExclusive) &&
                            (day to session) !in observedSlots) {
                            missing.add(DrawIdentity(day, session))
                        }
                    }
                }
                day = day.plusDays(1)
            }
        }

        return TwoDResearchHistoryAudit(
            sourceRows = rows.size,
            verifiedSlots = authoritative.size,
            earliestVerified = authoritative.firstOrNull()?.let {
                DrawIdentity(it.date, it.session)
            },
            latestVerified = authoritative.lastOrNull()?.let {
                DrawIdentity(it.date, it.session)
            },
            invalidPairRows = invalid,
            nonWorkingDayRows = nonWorking,
            atOrAfterCutoffRows = beyond,
            duplicateSlotRows = duplicates,
            conflictingSlots = conflicting,
            expectedOpenSlots = authoritative.size + missing.size,
            unverifiedOpenSlots = missing,
        )
    }
}
