package com.myanmar.ledger2d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myanmar.ledger2d.core.design.LedgerTheme
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.design.rememberLanguageState
import com.myanmar.ledger2d.feature.main.LedgerNav
import com.myanmar.ledger2d.feature.main.WelcomeScreen

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{var showWelcome by androidx.compose.runtime.remember { mutableStateOf(true) };LedgerTheme{if(showWelcome) WelcomeScreen{showWelcome=false}else{val language=rememberLanguageState(this);androidx.compose.runtime.CompositionLocalProvider(LocalLanguage provides language){LedgerNav((application as LedgerApplication).container)}}}}}}
