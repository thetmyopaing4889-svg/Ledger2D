package com.myanmar.ledger2d

import android.app.Application
import androidx.work.WorkManager
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.design.WorkingContextStore
import com.myanmar.ledger2d.core.repository.*
import com.myanmar.ledger2d.feature.live.LiveCollector
import com.myanmar.ledger2d.feature.live.LiveRoomKeeperWorker
import com.myanmar.ledger2d.feature.live.RoomWinningNumberAutoSaveBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object LedgerApplicationContextHolder { lateinit var context: Application }

class LedgerApplication : Application() {
    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        LedgerApplicationContextHolder.context = this

        // Stop the historical backfill chain that may have been persisted by
        // older app releases. The compatibility worker below never reschedules it.
        WorkManager.getInstance(this).cancelUniqueWork(LiveRoomKeeperWorker.WORK_NAME)

        container = AppContainer(LedgerDatabase.create(this))

        // Independent Room -> operational Winner route. Existing Live/Room/Calendar
        // ingestion remains unchanged; manual locks protect every user-owned draw.
        RoomWinningNumberAutoSaveBridge(
            context = this,
            liveResults = container.liveResults,
            winners = container.winners,
            closedDays = container.closedDays,
            scope = applicationScope,
        ).start()

        // App-scoped 2D LIVE collector: window-gated polling that continues
        // during the scheduled LIVE windows even when the screen is closed.
        // Its cold-start historical fallback uses the existing HistorySync
        // source inside LiveCollector; Room remains a write-only live record.
        LiveCollector.startOnce(
            this,
            liveRoomSaver = { patches -> container.liveResults.apply(patches) },
        )
    }
}

class AppContainer(val database: LedgerDatabase) {
    val workingContext = WorkingContextStore()
    val agents: AgentRepository = RoomAgentRepository(database)
    val customers: CustomerRepository = RoomCustomerRepository(database.customerDao())
    val commissions: CommissionRepository = RoomCommissionRepository(database)
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
