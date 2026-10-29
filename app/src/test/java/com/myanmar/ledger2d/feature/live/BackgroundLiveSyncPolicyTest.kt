package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BackgroundLiveSyncPolicyTest {
    private val thursday = LocalDate.of(2026, 10, 29)

    @Test fun beforeMarketAndOnWeekendNeverFetch() {
        assertFalse(BackgroundLiveSyncPolicy.needsFetch(thursday, LocalTime.of(9, 29), null))
        assertFalse(BackgroundLiveSyncPolicy.needsFetch(thursday.plusDays(2), LocalTime.of(17, 30), null))
    }

    @Test fun newDayNeedsFetchAfter0930() {
        assertTrue(BackgroundLiveSyncPolicy.needsFetch(thursday, LocalTime.of(9, 45), null))
    }

    @Test fun missingSessionAndReferenceFieldsAreRetriedAfterTheirCheckpoints() {
        val full = fullRow()
        assertTrue(BackgroundLiveSyncPolicy.needsFetch(
            thursday, LocalTime.of(12, 15), full.copy(morning2d = null),
        ))
        assertTrue(BackgroundLiveSyncPolicy.needsFetch(
            thursday, LocalTime.of(14, 15), full.copy(internet200 = null),
        ))
        assertTrue(BackgroundLiveSyncPolicy.needsFetch(
            thursday, LocalTime.of(17, 30), full.copy(eveningValue = null),
        ))
    }

    @Test fun completedCurrentDayDoesNotFetchAgain() {
        assertFalse(BackgroundLiveSyncPolicy.needsFetch(thursday, LocalTime.of(17, 30), fullRow()))
        assertTrue(BackgroundLiveSyncPolicy.needsFetch(
            thursday, LocalTime.of(17, 30), fullRow().copy(date = thursday.minusDays(1)),
        ))
    }

    private fun fullRow() = LiveDailyResultEntity(
        id = 1,
        date = thursday,
        modern930 = "12",
        internet930 = "34",
        modern200 = "56",
        internet200 = "78",
        morning2d = "90",
        morningSet = "1590.01",
        morningValue = "12345",
        evening2d = "01",
        eveningSet = "1580.02",
        eveningValue = "54321",
        reference930SourceAt = null,
        reference200SourceAt = null,
        morningSourceAt = null,
        eveningSourceAt = null,
        updatedAt = 0L,
    )
}
