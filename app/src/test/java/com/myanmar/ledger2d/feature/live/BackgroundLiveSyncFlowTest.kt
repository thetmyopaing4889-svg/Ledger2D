package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Verify that one late-day Luke response can supply every due checkpoint. */
class BackgroundLiveSyncFlowTest {
    private val date = LocalDate.of(2026, 10, 29)

    @Test fun singleEveningResponseSuppliesAllFourCheckpoints() {
        val patches = buildLiveDailyResultPatches(feed(LocalTime.of(17, 30)), date, LocalTime.of(17, 30))
        assertEquals(1, patches.size)
        val patch = patches.single()
        assertEquals("10", patch.modern930)
        assertEquals("20", patch.internet930)
        assertEquals("30", patch.modern200)
        assertEquals("40", patch.internet200)
        assertEquals("05", patch.morning2d)
        assertEquals("89", patch.evening2d)
    }

    @Test fun morningResponseCannotSaveEveningResultEarly() {
        val patches = buildLiveDailyResultPatches(feed(LocalTime.of(11, 0)), date, LocalTime.of(11, 0))
        assertEquals(1, patches.size)
        val patch = patches.single()
        assertEquals("10", patch.modern930)
        assertEquals(null, patch.morning2d)
        assertEquals(null, patch.evening2d)
        assertEquals(null, patch.modern200)
    }

    @Test fun wrongProviderDateCannotBeSaved() {
        val oldFeed = feed(LocalTime.of(17, 30)).copy(date = date.minusDays(1).toString())
        assertTrue(buildLiveDailyResultPatches(oldFeed, date, LocalTime.of(17, 30)).isEmpty())
    }

    @Test fun savedLateDayResultsEligibleForManualWinningBridge() {
        val observed = date.atTime(17, 30).atZone(YANGON).toInstant().toEpochMilli()
        val row = LiveDailyResultEntity(
            id = 1, date = date,
            modern930 = "10", internet930 = "20", modern200 = "30", internet200 = "40",
            morning2d = "05", morningSet = "1500", morningValue = "5000",
            evening2d = "89", eveningSet = "1600", eveningValue = "6000",
            reference930SourceAt = observed, reference200SourceAt = observed,
            morningSourceAt = observed, eveningSourceAt = observed, updatedAt = observed,
        )
        val winners = RoomWinningNumberAutoSavePolicy.candidates(
            row, date, LocalTime.of(17, 30), false,
        )
        assertEquals(listOf(DrawSession.MORNING, DrawSession.EVENING), winners.map { it.session })
        assertEquals(listOf("05", "89"), winners.map { it.digit })
        assertFalse(BackgroundLiveSyncPolicy.needsFetch(date, LocalTime.of(17, 30), row))
    }

    private fun feed(at: LocalTime): LiveFeedData {
        val instant = date.atTime(at).atZone(YANGON).toInstant().toEpochMilli()
        return LiveFeedData(
            date = date.toString(),
            currentTime = date.toString() + " " + at.toString(),
            live = "--", liveSet = "--", liveVal = "--",
            morning = LiveSessionData(result = "05", set = "1500", value = "5000", finalized = true),
            evening = LiveSessionData(result = "89", set = "1600", value = "6000", finalized = true),
            modern930 = "10", internet930 = "20", modern200 = "30", internet200 = "40",
            sourceTag = "LUKE", serverTimeEpochMs = instant,
        )
    }
}
