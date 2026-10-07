package com.myanmar.ledger2d

import android.app.Application
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.design.WorkingContextStore
import com.myanmar.ledger2d.core.repository.*
import com.myanmar.ledger2d.feature.live.LiveCollector
import com.myanmar.ledger2d.feature.live.LiveRoomKeeperScheduler

object LedgerApplicationContextHolder { lateinit var context: Application }

class LedgerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        LedgerApplicationContextHolder.context = this
        container = AppContainer(LedgerDatabase.create(this))

        // App-scoped 2D LIVE collector: window-gated polling that continues
        // during the scheduled LIVE windows even when the screen is closed.
        // Its cold-start historical fallback uses the existing HistorySync
        // source inside LiveCollector; Room remains a write-only live record.
        LiveCollector.startOnce(
            this,
            liveRoomSaver = { patches -> container.liveResults.apply(patches) },
        )

        // Historical coverage is independent of today's LIVE flow. WorkManager
        // keeps the Room Keeper alive in the background and only runs it when
        // network connectivity is available.
        LiveRoomKeeperScheduler.enqueue(this)
    }
}

class AppContainer(val database: LedgerDatabase) {
    val workingContext = WorkingContextStore()
    val agents: AgentRepository = RoomAgentRepository(database.agentDao())
    val customers: CustomerRepository = RoomCustomerRepository(database.customerDao())
    val bets: BetRepository = RoomBetRepository(database)
    val winners: WinningNumberRepository = RoomWinningNumberRepository(database)
    val history: HistoryResultRepository = RoomHistoryResultRepository(database)
    val liveResults: LiveDailyResultRepository = RoomLiveDailyResultRepository(database)
    val closedDays: ClosedDayRepository = RoomClosedDayRepository(database.closedDayDao())
    val closedNumbers: ClosedNumberRepository = RoomClosedNumberRepository(database.closedNumberDao())
    val limits: LimitRepository = RoomLimitRepository(database.limitDao())
    val agentLimits: AgentLimitRepository = RoomAgentLimitRepository(database.agentLimitDao())
    val settlements: SettlementRepository = RoomSettlementRepository(database.settlementDao())
    val audit: AuditRepository = RoomAuditRepository(database.auditDao())
}