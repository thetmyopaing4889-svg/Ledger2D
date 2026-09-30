package com.myanmar.ledger2d

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.design.WorkingContextStore
import com.myanmar.ledger2d.core.repository.*
import com.myanmar.ledger2d.feature.live.LiveCollector

class LedgerApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(LedgerDatabase.create(this))
        // History is bundled with the app so a new user has historical results immediately.
        runBlocking(Dispatchers.IO) { container.history.seedIfEmpty(this@LedgerApplication) }
        // App-scoped 2D LIVE collector: window-gated polling that continues
        // during the scheduled LIVE windows even when the screen is closed.
        LiveCollector.startOnce(this)
    }
}

class AppContainer(val database: LedgerDatabase) {
    val workingContext = WorkingContextStore()
    val agents: AgentRepository = RoomAgentRepository(database.agentDao())
    val customers: CustomerRepository = RoomCustomerRepository(database.customerDao())
    val bets: BetRepository = RoomBetRepository(database)
    val winners: WinningNumberRepository = RoomWinningNumberRepository(database)
    val history: HistoryResultRepository = RoomHistoryResultRepository(database)
    val closedDays: ClosedDayRepository = RoomClosedDayRepository(database.closedDayDao())
    val closedNumbers: ClosedNumberRepository = RoomClosedNumberRepository(database.closedNumberDao())
    val limits: LimitRepository = RoomLimitRepository(database.limitDao())
    val agentLimits: AgentLimitRepository = RoomAgentLimitRepository(database.agentLimitDao())
    val settlements: SettlementRepository = RoomSettlementRepository(database.settlementDao())
    val audit: AuditRepository = RoomAuditRepository(database.auditDao())
}
