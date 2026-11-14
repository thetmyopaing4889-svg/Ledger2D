package com.myanmar.ledger2d.feature.main

import androidx.compose.runtime.Composable
import com.myanmar.ledger2d.AppContainer

/** Home navigation entry point. Better Data features live in CherryBetterDataScreen. */
@Composable
fun BetterDataScreen(container: AppContainer, onBack: () -> Unit) {
    CherryBetterDataScreen(container, onBack)
}
