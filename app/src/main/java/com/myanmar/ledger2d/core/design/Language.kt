package com.myanmar.ledger2d.core.design

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class LanguageState(context: Context) {
    private val preferences = context.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)
    var code by mutableStateOf(preferences.getString("language", "my") ?: "my")
        private set
    fun set(code: String) { this.code = code; preferences.edit().putString("language", code).apply() }
    fun text(my: String, en: String): String = if (code == "en") en else my
}
val LocalLanguage = staticCompositionLocalOf<LanguageState> { error("LanguageState not provided") }
@Composable fun rememberLanguageState(context: Context): LanguageState = remember { LanguageState(context) }
