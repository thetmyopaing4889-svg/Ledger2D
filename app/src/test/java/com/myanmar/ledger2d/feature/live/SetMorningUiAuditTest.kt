package com.myanmar.ledger2d.feature.live

import android.app.Application
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SetMorningUiAuditTest {
    private lateinit var context: Context
    private val day = LocalDate.of(2026, 11, 18)

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("set_ui_display_only_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.filesDir.resolve("set_ui_audit.log").delete()
    }

    private fun snapshot(): SetMarketSnapshot {
        val bangkok = LocalDateTime.of(day, LocalTime.parse("12:00:53"))
            .atZone(YANGON).withZoneSameInstant(java.time.ZoneId.of("Asia/Bangkok"))
        return SetMarketSnapshot("1,481.46", "29,810.24", bangkok, "Intermission", "60")
    }

    @Test fun live_snapshot_does_not_persist_as_a_result() {
        val observation = SetFetchObservation(snapshot(), Instant.now(), 100L)
        SetMorningAuditStore.recordObservation(
            context, day, observation,
            SetMorningSelection(SetMorningSelectionPhase.SET_LIVE, observation.snapshot),
        )
        assertNull(SetMorningAuditStore.restoreUiResult(context, day))
    }

    @Test fun selected_ui_result_restores_only_for_its_day_and_compares_luke() {
        val observation = SetFetchObservation(snapshot(), Instant.now(), 100L)
        SetMorningAuditStore.recordObservation(
            context, day, observation,
            SetMorningSelection(SetMorningSelectionPhase.UI_RESULT_READY, observation.snapshot),
        )
        val loaded = SetMorningAuditStore.restoreUiResult(context, day)
        assertEquals("60", loaded?.selection?.snapshot?.computed2d)
        assertEquals(SetMorningSelectionPhase.UI_RESULT_READY, loaded?.selection?.phase)
        assertNull(SetMorningAuditStore.restoreUiResult(context, day.plusDays(1)))
        SetMorningAuditStore.recordLukeFinal(context, day, "61")
        val log = SetMorningAuditStore.recentLog(context)
        assertTrue(log.contains("UI_RESULT"))
        assertTrue(log.contains("LUKE_COMPARE"))
        assertTrue(log.contains("luke=61,match=false"))
    }
}
