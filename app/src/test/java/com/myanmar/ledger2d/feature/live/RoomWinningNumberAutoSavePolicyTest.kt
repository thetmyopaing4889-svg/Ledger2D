package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class RoomWinningNumberAutoSavePolicyTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test
    fun acceptsOnlyValidCurrentDayMorningAndEveningResultsAfterTheirCheckpoints() {
        val row = row(
            morning2d = "05",
            morningSourceAt = Instant.parse("2026-10-15T05:30:50Z").toEpochMilli(), // Yangon 12:00:50
            evening2d = "90",
            eveningSourceAt = Instant.parse("2026-10-15T09:59:50Z").toEpochMilli(), // Yangon 16:29:50
        )

        val candidates = RoomWinningNumberAutoSavePolicy.candidates(
            row = row,
            today = today,
            now = LocalTime.of(16, 30),
            isClosedDay = false,
        )

        assertEquals(listOf(DrawSession.MORNING, DrawSession.EVENING), candidates.map { it.session })
        assertEquals(listOf("05", "90"), candidates.map { it.digit })
    }

    @Test
    fun rejectsPendingOrPreCheckpointResults() {
        val row = row(
            morning2d = "--",
            morningSourceAt = Instant.parse("2026-10-15T05:30:49Z").toEpochMilli(), // Yangon 12:00:49
            evening2d = "31",
            eveningSourceAt = Instant.parse("2026-10-15T09:59:49Z").toEpochMilli(), // Yangon 16:29:49
        )

        val candidates = RoomWinningNumberAutoSavePolicy.candidates(
            row = row,
            today = today,
            now = LocalTime.of(16, 30),
            isClosedDay = false,
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun rejectsStaleDateWeekendAndClosedDayRows() {
        val valid = row(
            morning2d = "12",
            morningSourceAt = Instant.parse("2026-10-15T05:31:00Z").toEpochMilli(),
        )

        assertTrue(
            RoomWinningNumberAutoSavePolicy.candidates(
                valid.copy(date = today.minusDays(1)),
                today,
                LocalTime.of(12, 1),
                false,
            ).isEmpty(),
        )
        assertTrue(
            RoomWinningNumberAutoSavePolicy.candidates(
                valid,
                LocalDate.of(2026, 10, 17),
                LocalTime.of(12, 1),
                false,
            ).isEmpty(),
        )
        assertTrue(
            RoomWinningNumberAutoSavePolicy.candidates(
                valid,
                today,
                LocalTime.of(12, 1),
                true,
            ).isEmpty(),
        )
    }

    private fun row(
        morning2d: String? = null,
        morningSourceAt: Long? = null,
        evening2d: String? = null,
        eveningSourceAt: Long? = null,
    ) = LiveDailyResultEntity(
        id = 1,
        date = today,
        modern930 = null,
        internet930 = null,
        modern200 = null,
        internet200 = null,
        morning2d = morning2d,
        morningSet = null,
        morningValue = null,
        evening2d = evening2d,
        eveningSet = null,
        eveningValue = null,
        reference930SourceAt = null,
        reference200SourceAt = null,
        morningSourceAt = morningSourceAt,
        eveningSourceAt = eveningSourceAt,
        updatedAt = 0L,
    )
}
