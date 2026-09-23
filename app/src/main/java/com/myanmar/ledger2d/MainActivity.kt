package com.myanmar.ledger2d

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myanmar.ledger2d.core.design.LedgerTheme
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.design.rememberLanguageState
import com.myanmar.ledger2d.feature.main.LedgerNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = AndroidColor.TRANSPARENT
        setContent {
            val container = (application as LedgerApplication).container
            LedgerTheme {
                val language = rememberLanguageState(this, container.workingContext)
                androidx.compose.runtime.CompositionLocalProvider(LocalLanguage provides language) {
                    LedgerNav(container, startAtWelcome = true)
                }
            }
        }
    }
}
