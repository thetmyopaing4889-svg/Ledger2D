package com.myanmar.ledger2d.feature.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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
    fun reference_retry_window_stays_open_on_cycle_day_except_friday_after_evening_close() {
        val friday = LocalDate.of(2026, 10, 2)
        val monday = friday.plusDays(3)

        assertTrue(isReferenceRetryWindowOpenAt(friday, friday, LocalTime.of(9, 30), false))
        assertTrue(isReferenceRetryWindowOpenAt(friday, friday, LocalTime.of(16, 29, 59), false))
        assertFalse(isReferenceRetryWindowOpenAt(friday, friday, LocalTime.of(16, 30), false))
        assertTrue(isReferenceRetryWindowOpenAt(monday, monday, LocalTime.of(16, 45), false))
    }

    @Test
    fun reference_retry_window_allows_only_next_working_day_before_0930() {
        val monday = LocalDate.of(2026, 10, 5)
        val tuesday = monday.plusDays(1)
        val saturday = monday.plusDays(5)

        assertTrue(isReferenceRetryWindowOpenAt(monday, tuesday, LocalTime.of(9, 29, 59), false))
        assertFalse(isReferenceRetryWindowOpenAt(monday, tuesday, LocalTime.of(9, 30), false))
        assertFalse(isReferenceRetryWindowOpenAt(monday, saturday, LocalTime.of(9, 0), false))
        assertFalse(isReferenceRetryWindowOpenAt(monday, monday.plusDays(2), LocalTime.of(9, 0), false))
    }

    @Test
    fun closed_day_hold_disables_reference_retries() {
        val monday = LocalDate.of(2026, 10, 5)
        assertFalse(
            isReferenceRetryWindowOpenAt(
                cycleDate = monday,
                currentDate = monday,
                now = LocalTime.of(11, 45),
                hasClosedHold = true,
            ),
        )
        assertFalse(
            isReferenceRetryWindowOpenAt(
                cycleDate = monday,
                currentDate = monday.plusDays(1),
                now = LocalTime.of(8, 45),
                hasClosedHold = true,
            ),
        )
    }

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
