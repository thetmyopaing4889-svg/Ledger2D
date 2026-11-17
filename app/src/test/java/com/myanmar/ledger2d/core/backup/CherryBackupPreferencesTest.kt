package com.myanmar.ledger2d.core.backup

import android.app.Application
import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class CherryBackupPreferencesTest {
    @Test fun allSavedValueTypesAndFormulaChoiceSurviveRoundTrip() {
        val ctx=RuntimeEnvironment.getApplication()
        val name="cherry_backup_test_preferences"
        val file=ctx.getSharedPreferences(name,Context.MODE_PRIVATE)
        check(file.edit().clear()
            .putString("selected_lone","id_lone|2|5")
            .putString("success_snapshot","{\"date\":\"2026-11-17\"}")
            .putStringSet("groups",setOf("A","B"))
            .putBoolean("enabled",true)
            .putInt("count",5)
            .putLong("total",9_876_543_210L)
            .putFloat("scale",1.25f)
            .commit())
        val store=CherryBackupPreferences(ctx)
        val original=store.snapshot()
        assertTrue(original.containsKey(name))
        val zipped=ByteArrayOutputStream()
        store.write(zipped,original)
        val roundTrip=store.parse(ByteArrayInputStream(zipped.toByteArray()))
        assertEquals(original,roundTrip)
        check(file.edit().clear().putString("bad","overwritten").commit())
        store.replace(roundTrip)
        val after=ctx.getSharedPreferences(name,Context.MODE_PRIVATE)
        assertEquals("id_lone|2|5",after.getString("selected_lone",null))
        assertEquals("{\"date\":\"2026-11-17\"}",after.getString("success_snapshot",null))
        assertEquals(setOf("A","B"),after.getStringSet("groups",emptySet()))
        assertTrue(after.getBoolean("enabled",false))
        assertEquals(5,after.getInt("count",0))
        assertEquals(9_876_543_210L,after.getLong("total",0))
        assertEquals(1.25f,after.getFloat("scale",0f),0.00001f)
        assertFalse(after.contains("bad"))
    }
}
