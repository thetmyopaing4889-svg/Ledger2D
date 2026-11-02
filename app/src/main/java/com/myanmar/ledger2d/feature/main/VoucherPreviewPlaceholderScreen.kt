package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myanmar.ledger2d.core.design.LocalLanguage

/** Intentionally blank: voucher design and JPEG generation will be implemented later. */
@Composable
fun VoucherPreviewPlaceholderScreen(onBack: () -> Unit) {
    val language = LocalLanguage.current
    AppScaffold(
        title = language.text("ဘောင်ချာအကြိုကြည့်ရန်", "Voucher preview"),
        onBack = onBack,
        includeQuickEntryFab = false,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding))
    }
}
