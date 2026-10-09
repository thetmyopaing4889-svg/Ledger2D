package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class LiveSchedulePolicyTest {
    private fun shouldPoll(
        time: String,
        isWorkingDay: Boolean = true,
        hasClosedHold: Boolean = false,
        morningFinalized: Boolean? = false,
        eveningFinalized: Boolean? = false,
    ): Boolean = shouldPollLiveAt(
        t = LocalTime.parse(time),
        isWorkingDay = isWorkingDay,
        hasClosedHold = hasClosedHold,
        morningFinalized = morningFinalized,
        eveningFinalized = eveningFinalized,
    )

    @Test
    fun morning_live_window_includes_1130_and_stops_before_1201() {
        assertFalse(shouldPoll("11:29:59"))
        assertTrue(shouldPoll("11:30:00", morningFinalized = true))
        assertTrue(shouldPoll("12:00:59", morningFinalized = true))
    }

    @Test
    fun morning_catchup_window_stops_at_1231_or_when_final_is_confirmed() {
        assertTrue(shouldPoll("12:01:00", morningFinalized = null))
        assertTrue(shouldPoll("12:01:00", morningFinalized = false))
        assertFalse(shouldPoll("12:01:00", morningFinalized = true))
        assertTrue(shouldPoll("12:30:59", morningFinalized = false))
        assertFalse(shouldPoll("12:31:00", morningFinalized = false))
    }

    @Test
    fun evening_live_window_includes_1600_and_stops_before_1630() {
        assertFalse(shouldPoll("15:59:59"))
        assertTrue(shouldPoll("16:00:00", eveningFinalized = true))
        assertTrue(shouldPoll("16:29:59", eveningFinalized = true))
    }

    @Test
    fun evening_catchup_window_stops_at_1700_or_when_final_is_confirmed() {
        assertTrue(shouldPoll("16:30:00", eveningFinalized = null))
        assertTrue(shouldPoll("16:30:00", eveningFinalized = false))
        assertFalse(shouldPoll("16:30:00", eveningFinalized = true))
        assertTrue(shouldPoll("16:59:59", eveningFinalized = false))
        assertFalse(shouldPoll("17:00:00", eveningFinalized = false))
    }

    @Test
    fun closed_day_weekends_and_non_live_windows_never_poll() {
        assertFalse(shouldPoll("11:30:00", hasClosedHold = true))
        assertFalse(shouldPoll("16:30:00", hasClosedHold = true))
        assertFalse(shouldPoll("11:30:00", isWorkingDay = false))
        assertFalse(shouldPoll("16:30:00", isWorkingDay = false))
        assertFalse(shouldPoll("09:30:00"))
        assertFalse(shouldPoll("14:00:00"))
        assertFalse(shouldPoll("17:00:00"))
    }
}
