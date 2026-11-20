package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Instant

class SetMorningUiDisplayPolicyTest {
    private val day = LocalDate.of(2026, 11, 18)
    private val bangkok = ZoneId.of("Asia/Bangkok")

    private fun source(time: String, digit: String): SetMarketSnapshot {
        val local = LocalDateTime.of(day, LocalTime.parse(time))
            .atZone(YANGON).withZoneSameInstant(bangkok)
        return SetMarketSnapshot("1,481.46", "29,810.24", local, "Intermission", digit)
    }
    private fun state(phase: SetMorningSelectionPhase, digit: String, time: String) =
        SetMorningUiState(day, SetMorningSelection(phase, source(time, digit)), Instant.now())
    private fun clock(time: String): ZonedDateTime =
        LocalDateTime.of(day, LocalTime.parse(time)).atZone(YANGON)

    private fun luke(final: String): LiveFeedData = LiveFeedData(
        date = day.toString(), currentTime = "12:02:00", live = "64",
        liveSet = "1,481.44", liveVal = "29,810.24",
        morning = LiveSessionData(final, "1,481.46", "29,810.24", final.length == 2),
        evening = LiveSessionData("--", "--", "--", false),
        modern930 = "--", internet930 = "--", modern200 = "--", internet200 = "--",
    )

    @Test fun at_1159_even_warmed_up_set_does_not_override_luke() {
        assertNull(setMorningHeroPresentation(state(SetMorningSelectionPhase.SET_LIVE, "60", "11:59:53"),
            luke("--"), day, clock("11:59:58"), false))
    }

    @Test fun at_1200_fresh_set_shows_in_same_live_hero() {
        val visual = setMorningHeroPresentation(state(SetMorningSelectionPhase.SET_LIVE, "60", "11:59:53"),
            luke("--"), day, clock("12:00:14"), false)
        assertNotNull(visual)
        assertTrue(visual!!.isLive)
        assertEquals("60", visual.hero.result)
        assertEquals("1,481.46", visual.hero.set)
        assertEquals("11:59:53", visual.hero.sessionLabel)
    }

    @Test fun stale_set_reverts_to_luke_without_promotion() {
        assertNull(setMorningHeroPresentation(state(SetMorningSelectionPhase.SET_LIVE, "60", "11:59:05"),
            luke("--"), day, clock("12:00:14"), false))
    }

    @Test fun set_live_hero_time_follows_updated_set_source_seconds() {
        val first = setMorningHeroPresentation(
            state(SetMorningSelectionPhase.SET_LIVE, "60", "12:00:02"),
            luke("--"), day, clock("12:00:04"), false,
        )
        val next = setMorningHeroPresentation(
            state(SetMorningSelectionPhase.SET_LIVE, "61", "12:00:07"),
            luke("--"), day, clock("12:00:09"), false,
        )
        assertNotNull(first)
        assertNotNull(next)
        assertTrue(first!!.isLive)
        assertTrue(next!!.isLive)
        assertEquals("12:00:02", first.hero.sessionLabel)
        assertEquals("12:00:07", next.hero.sessionLabel)
    }

    @Test fun cutoff_snapshot_animates_no_more_after_source_crossing() {
        val visual = setMorningHeroPresentation(state(SetMorningSelectionPhase.UI_RESULT_READY, "60", "12:00:53"),
            luke("--"), day, clock("12:01:51"), false)
        assertNotNull(visual)
        assertFalse(visual!!.isLive)
        assertEquals("60", visual.hero.result)
        assertEquals(LIVE_SESSION_MORNING_LABEL, visual.hero.sessionLabel)
    }

    @Test fun luke_final_wins_even_when_set_ui_has_result() {
        val candidate = state(SetMorningSelectionPhase.UI_RESULT_READY, "60", "12:00:53")
        assertNull(setMorningHeroPresentation(candidate, luke("60"), day, clock("12:02:00"), false))
        assertNull(setMorningHeroPresentation(candidate, luke("61"), day, clock("12:02:00"), false))
        assertFalse(setMorningCorrectionNeeded(candidate, luke("60"), day, clock("12:02:00")))
        assertTrue(setMorningCorrectionNeeded(candidate, luke("61"), day, clock("12:02:00")))
        assertFalse(setMorningCorrectionNeeded(candidate, luke("61"), day, clock("16:31:00")))
    }

    @Test fun expired_set_live_is_cleared_on_network_failure() {
        val live = state(SetMorningSelectionPhase.SET_LIVE, "60", "12:00:23")
        assertFalse(shouldDiscardStaleSetLive(live, clock("12:00:40").toInstant()))
        assertTrue(shouldDiscardStaleSetLive(live, clock("12:01:24").toInstant()))
        assertTrue(shouldDiscardStaleSetLive(live, clock("12:00:22").toInstant()))
        val result = state(SetMorningSelectionPhase.UI_RESULT_READY, "60", "12:00:53")
        assertFalse(shouldDiscardStaleSetLive(result, clock("12:30:00").toInstant()))
    }

    @Test fun set_result_uses_regular_result_status_in_both_hero_and_morning_card() {
        val screen = java.io.File(
            "src/main/java/com/myanmar/ledger2d/feature/live/LiveScreen.kt",
        ).readText(Charsets.UTF_8)
        assertFalse(screen.contains("SET ယာယီ"))
        assertFalse(screen.contains("SET preview"))
        assertFalse(screen.contains("Luke အတည်ပြုချက်စောင့်"))
        assertFalse(screen.contains("Awaiting Luke confirmation"))
        assertFalse(screen.contains("setUiResult"))
        assertTrue(screen.contains("if (finalized) {"))
    }

    @Test fun known_nontrading_status_cannot_be_presented_as_set_live() {
        val snapshot = source("12:00:23", "60").copy(marketStatus = "OffHour")
        val candidate = SetMorningUiState(
            day, SetMorningSelection(SetMorningSelectionPhase.SET_LIVE, snapshot), Instant.now(),
        )
        assertNull(setMorningHeroPresentation(candidate, luke("--"), day, clock("12:00:33"), false))
        val closed = candidate.copy(selection = candidate.selection.copy(
            snapshot = snapshot.copy(marketStatus = "Closed"),
        ))
        assertNull(setMorningHeroPresentation(closed, luke("--"), day, clock("12:00:33"), false))
    }

    @Test fun open_status_can_be_live_but_cannot_be_a_ui_only_cutoff_result() {
        val open = source("12:00:53", "60").copy(marketStatus = "Open")
        val live = SetMorningUiState(day, SetMorningSelection(
            SetMorningSelectionPhase.SET_LIVE, open,
        ), Instant.now())
        assertNotNull(setMorningHeroPresentation(
            live, luke("--"), day, clock("12:00:59"), false,
        ))
        val invalidResult = live.copy(selection = SetMorningSelection(
            SetMorningSelectionPhase.UI_RESULT_READY, open,
        ))
        assertNull(setMorningHeroPresentation(
            invalidResult, luke("--"), day, clock("12:01:30"), false,
        ))
        assertFalse(setMorningCorrectionNeeded(
            invalidResult, luke("61"), day, clock("12:02:00"),
        ))
    }

    @Test fun closed_day_wrong_date_and_afternoon_never_show_set() {
        val candidate = state(SetMorningSelectionPhase.UI_RESULT_READY, "60", "12:00:53")
        assertNull(setMorningHeroPresentation(candidate, luke("--"), day, clock("12:01:59"), true))
        assertNull(setMorningHeroPresentation(candidate, luke("--"), day, clock("13:00:00"), false))
        assertNull(setMorningHeroPresentation(candidate, luke("--"), day.plusDays(1), clock("12:01:59"), false))
    }
}
