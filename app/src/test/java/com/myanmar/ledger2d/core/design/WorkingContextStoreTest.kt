package com.myanmar.ledger2d.core.design

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkingContextStoreTest {
    @Test fun fresh_app_context_uses_a_newly_calculated_default() {
        val freshContext = WorkingContextStore()
        val calculatedDefault = DrawIdentity(LocalDate.of(2026, 9, 22), DrawSession.EVENING)

        freshContext.initialize(calculatedDefault)

        assertTrue(freshContext.isInitialized)
        assertEquals(calculatedDefault, freshContext.current)
    }

    @Test fun previous_working_context_is_not_restored_by_a_fresh_store() {
        val previousAppSession = WorkingContextStore().apply {
            initialize(DrawIdentity(LocalDate.of(2026, 9, 22), DrawSession.MORNING))
            setDate(LocalDate.of(2026, 9, 25))
            setSession(DrawSession.EVENING)
        }
        val restartedAppSession = WorkingContextStore().apply {
            initialize(DrawIdentity(LocalDate.of(2026, 9, 28), DrawSession.MORNING))
        }

        assertEquals(DrawIdentity(LocalDate.of(2026, 9, 25), DrawSession.EVENING), previousAppSession.current)
        assertNotEquals(previousAppSession.current, restartedAppSession.current)
        assertEquals(DrawIdentity(LocalDate.of(2026, 9, 28), DrawSession.MORNING), restartedAppSession.current)
    }

    @Test fun manual_date_selection_becomes_the_shared_working_date() {
        val context = initializedContext()

        context.setDate(LocalDate.of(2026, 9, 25))

        assertEquals(LocalDate.of(2026, 9, 25), context.current.date)
        assertEquals(DrawSession.MORNING, context.current.session)
    }

    @Test fun manual_session_selection_becomes_the_shared_working_session() {
        val context = initializedContext()

        context.setSession(DrawSession.EVENING)

        assertEquals(LocalDate.of(2026, 9, 22), context.current.date)
        assertEquals(DrawSession.EVENING, context.current.session)
    }

    @Test fun manual_and_historical_selection_remains_stable_during_active_session() {
        val context = initializedContext()
        val historicalDate = LocalDate.of(2020, 1, 2)

        context.setDate(historicalDate)
        context.setSession(DrawSession.EVENING)
        context.initialize(DrawIdentity(LocalDate.of(2026, 9, 23), DrawSession.MORNING))

        assertEquals(DrawIdentity(historicalDate, DrawSession.EVENING), context.current)
    }

    @Test fun closed_day_selection_has_no_implicit_working_context_mutation() {
        val context = initializedContext()
        val closedDayPickerDate = LocalDate.of(2026, 9, 30)

        assertEquals(DrawIdentity(LocalDate.of(2026, 9, 22), DrawSession.MORNING), context.current)
        assertEquals(LocalDate.of(2026, 9, 30), closedDayPickerDate)
        assertEquals(DrawIdentity(LocalDate.of(2026, 9, 22), DrawSession.MORNING), context.current)
    }

    @Test fun adding_a_closed_day_does_not_change_an_existing_manual_context() {
        val context = initializedContext()
        context.setDate(LocalDate.of(2026, 9, 25))
        val newlyClosedDay = LocalDate.of(2026, 9, 30)

        assertEquals(LocalDate.of(2026, 9, 30), newlyClosedDay)
        assertEquals(LocalDate.of(2026, 9, 25), context.current.date)
    }

    @Test fun store_is_uninitialized_until_the_new_session_default_is_available() {
        val context = WorkingContextStore()

        assertFalse(context.isInitialized)
    }

    private fun initializedContext(): WorkingContextStore = WorkingContextStore().apply {
        initialize(DrawIdentity(LocalDate.of(2026, 9, 22), DrawSession.MORNING))
    }
}
