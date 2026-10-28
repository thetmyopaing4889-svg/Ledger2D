package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LiveTemporaryWinnerPolicyTest {
    private val today = LocalDate.of(2026, 10, 28)

    private fun feed(
        digit: String,
        time: String = "12:02:00",
        date: LocalDate = today,
        morning: String = LIVE_PENDING,
        evening: String = LIVE_PENDING,
    ) = LiveFeedData(
        date = date.toString(),
        currentTime = date.toString() + " " + time,
        live = digit,
        liveSet = "1566",
        liveVal = "65000",
        morning = LiveSessionData(morning, LIVE_PENDING, LIVE_PENDING, isValidLive2d(morning)),
        evening = LiveSessionData(evening, LIVE_PENDING, LIVE_PENDING, isValidLive2d(evening)),
        modern930 = LIVE_PENDING,
        internet930 = LIVE_PENDING,
        modern200 = LIVE_PENDING,
        internet200 = LIVE_PENDING,
    )

    @Test fun post_close_first_luke_live_is_used_once() {
        val selected = LiveTemporaryWinnerPolicy.select(
            existing = null, preClose = null, incoming = feed("47"),
            today = today, now = LocalTime.of(12, 2), finalConfirmed = false,
        )
        assertEquals("47", selected?.hero?.result)
        assertEquals(LIVE_SESSION_MORNING_LABEL, selected?.hero?.sessionLabel)

        val next = LiveTemporaryWinnerPolicy.select(
            existing = selected, preClose = null, incoming = feed("48", "12:03:00"),
            today = today, now = LocalTime.of(12, 3), finalConfirmed = false,
        )
        assertEquals(selected, next)
    }

    @Test fun actual_pre_close_observation_has_priority_over_late_live() {
        val before = LiveTemporaryWinnerPolicy.candidate(
            feed("47", "12:00:59"), today, LiveSession.MORNING,
        )
        assertEquals(
            "47",
            LiveTemporaryWinnerPolicy.select(
                existing = null, preClose = before, incoming = feed("49", "12:01:03"),
                today = today, now = LocalTime.of(12, 1, 3), finalConfirmed = false,
            )?.hero?.result,
        )
    }

    @Test fun final_suppresses_temporary_winner() {
        val current = LiveTemporaryWinnerPolicy.candidate(feed("47"), today, LiveSession.MORNING)
        assertNull(
            LiveTemporaryWinnerPolicy.select(
                existing = current, preClose = null, incoming = feed("48"),
                today = today, now = LocalTime.of(12, 3), finalConfirmed = true,
            ),
        )
        org.junit.Assert.assertEquals(
            true, LiveTemporaryWinnerPolicy.sessionFinalConfirmed(feed("48", morning = "49"), today, LiveSession.MORNING),
        )
    }

    @Test fun wrong_day_and_wrong_session_never_reuse_cached_temporary() {
        val cached = LiveTemporaryWinnerPolicy.candidate(feed("47"), today, LiveSession.MORNING)
        assertNull(
            LiveTemporaryWinnerPolicy.select(
                existing = cached, preClose = null, incoming = null,
                today = today.plusDays(1), now = LocalTime.of(12, 2), finalConfirmed = false,
            ),
        )
        assertNull(
            LiveTemporaryWinnerPolicy.select(
                existing = cached, preClose = null, incoming = null,
                today = today, now = LocalTime.of(16, 35), finalConfirmed = false,
            ),
        )
    }

    @Test fun evening_does_not_steal_morning_luke_live() {
        assertNull(LiveTemporaryWinnerPolicy.candidate(feed("47"), today, LiveSession.EVENING))
        val selected = LiveTemporaryWinnerPolicy.select(
            existing = null, preClose = null, incoming = feed("03", "16:31:00"),
            today = today, now = LocalTime.of(16, 31), finalConfirmed = false,
        )
        assertEquals("03", selected?.hero?.result)
        assertEquals(LIVE_SESSION_EVENING_LABEL, selected?.hero?.sessionLabel)
    }

    @Test fun invalid_luke_or_closed_market_cannot_make_temporary_winner() {
        assertNull(LiveTemporaryWinnerPolicy.candidate(feed("--"), today, LiveSession.MORNING))
        assertNull(LiveTemporaryWinnerPolicy.candidate(feed("47", date = today.minusDays(1)), today, LiveSession.MORNING))
        assertNull(LiveTemporaryWinnerPolicy.candidate(feed("47").copy(isCloseDay = true), today, LiveSession.MORNING))
        val weekend = LocalDate.of(2026, 10, 31)
        assertNull(
            LiveTemporaryWinnerPolicy.select(
                existing = null, preClose = null, incoming = feed("47", date = weekend),
                today = weekend, now = LocalTime.of(12, 2), finalConfirmed = false,
            ),
        )
    }
}
