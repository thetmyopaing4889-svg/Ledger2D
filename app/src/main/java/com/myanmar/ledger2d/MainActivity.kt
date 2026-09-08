package com.myanmar.ledger2d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.myanmar.ledger2d.core.design.LedgerTheme
import com.myanmar.ledger2d.feature.main.LedgerNav

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{LedgerTheme{LedgerNav((application as LedgerApplication).container)}}}}
