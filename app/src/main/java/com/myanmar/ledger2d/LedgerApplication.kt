package com.myanmar.ledger2d

import android.app.Application
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.repository.*

class LedgerApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(LedgerDatabase.create(this)) }
}
class AppContainer(db: LedgerDatabase) {
    val agents: AgentRepository = RoomAgentRepository(db.agentDao())
    val customers: CustomerRepository = RoomCustomerRepository(db.customerDao())
    val bets: BetRepository = RoomBetRepository(db)
    val winners: WinningNumberRepository = RoomWinningNumberRepository(db.winningNumberDao())
    val closedDays: ClosedDayRepository = RoomClosedDayRepository(db.closedDayDao())
    val closedNumbers: ClosedNumberRepository = RoomClosedNumberRepository(db.closedNumberDao())
    val limits: LimitRepository = RoomLimitRepository(db.limitDao())
}
