package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.domain.BetParser
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal data class RoomWinnerCandidate(
    val date: LocalDate,
    val session: DrawSession,
    val digit: String,
)

/**
 * Pure eligibility policy for final-result fields already accepted into Room.
 * Room's existing patch builder remains the producer and keeps its own rules.
 */
internal object RoomWinningNumberAutoSavePolicy {
    private const val EARLY_CHECKPOINT_GRACE_SECONDS = 10L

    fun candidates(
        row: LiveDailyResultEntity,
        today: LocalDate,
        now: LocalTime,
        isClosedDay: Boolean,
    ): List<RoomWinnerCandidate> {
        if (row.date != today || !isWorkingDay(today) || isClosedDay) return emptyList()

        return listOfNotNull(
            candidate(
                date = row.date,
                session = DrawSession.MORNING,
                digit = row.morning2d,
                sourceAt = row.morningSourceAt,
                checkpoint = MORNING_CLOSE,
                today = today,
                now = now,
            ),
            candidate(
                date = row.date,
                session = DrawSession.EVENING,
                digit = row.evening2d,
                sourceAt = row.eveningSourceAt,
                checkpoint = EVENING_CLOSE,
                today = today,
                now = now,
            ),
        )
    }

    private fun candidate(
        date: LocalDate,
        session: DrawSession,
        digit: String?,
        sourceAt: Long?,
        checkpoint: LocalTime,
        today: LocalDate,
        now: LocalTime,
    ): RoomWinnerCandidate? {
        val validDigit = digit?.takeIf { BetParser.validDigit(it) } ?: return null
        val observedAt = sourceAt?.let { Instant.ofEpochMilli(it).atZone(YANGON) } ?: return null
        if (observedAt.toLocalDate() != today) return null

        // Match the 10-second early tolerance already used by Room ingestion.
        val earliest = checkpoint.minusSeconds(EARLY_CHECKPOINT_GRACE_SECONDS)
        if (observedAt.toLocalTime().isBefore(earliest) || now.isBefore(earliest)) return null

        return RoomWinnerCandidate(date = date, session = session, digit = validDigit)
    }
}
