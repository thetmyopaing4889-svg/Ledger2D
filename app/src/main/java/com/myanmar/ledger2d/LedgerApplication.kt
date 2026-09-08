package com.myanmar.ledger2d

import android.app.Application
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.repository.*

class LedgerApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(LedgerDatabase.create(this)) }
}
class AppContainer(val database: LedgerDatabase) {
    val agents: AgentRepository = RoomAgentRepository(database.agentDao())
    val customers: CustomerRepository = RoomCustomerRepository(database.customerDao())
    val bets: BetRepository = RoomBetRepository(database)
    val winners: WinningNumberRepository = RoomWinningNumberRepository(database.winningNumberDao())
    val closedDays: ClosedDayRepository = RoomClosedDayRepository(database.closedDayDao())
    val closedNumbers: ClosedNumberRepository = RoomClosedNumberRepository(database.closedNumberDao())
    val limits: LimitRepository = RoomLimitRepository(database.limitDao())
}
