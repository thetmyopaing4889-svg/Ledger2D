package com.myanmar.ledger2d.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Application-scoped, in-memory working context. A new AppContainer creates a new instance,
 * so neither the calculated default nor a manual selection survives a full app restart.
 */
class WorkingContextStore {
    var current by mutableStateOf(DrawIdentity(LocalDate.now(), DrawSession.MORNING))
        private set
    var isInitialized by mutableStateOf(false)
        private set

    fun initialize(default: DrawIdentity) {
        if (!isInitialized) {
            current = default
            isInitialized = true
        }
    }

    fun setDate(date: LocalDate) {
        current = current.copy(date = date)
    }

    fun setSession(session: DrawSession) {
        current = current.copy(session = session)
    }
}
