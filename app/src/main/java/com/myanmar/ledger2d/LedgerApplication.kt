package com.myanmar.ledger2d

import android.app.Application
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.repository.*
import java.io.File

class LedgerApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(LedgerDatabase.create(this)) }
    fun backupFile(): File = File(filesDir, "ledger2d-backup.db")
    fun createBackup(): File { container.database.close(); File(databasePath()).copyTo(backupFile(), true); container = AppContainer(LedgerDatabase.create(this)); return backupFile() }
    fun restoreBackup(): Boolean { val backup=backupFile(); if(!backup.exists()) return false; container.database.close(); backup.copyTo(File(databasePath()), true); File(databasePath()+"-wal").delete(); File(databasePath()+"-shm").delete(); container=AppContainer(LedgerDatabase.create(this)); return true }
    private fun databasePath(): String = getDatabasePath("ledger2d.db").absolutePath
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
