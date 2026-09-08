package com.myanmar.ledger2d.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [AgentEntity::class, CustomerEntity::class, BetEntryEntity::class, BetLineEntity::class, WinningNumberEntity::class, ClosedDayEntity::class, ClosedNumberEntity::class, AllLimitEntity::class, SpecialLimitEntity::class], version = 1, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun customerDao(): CustomerDao
    abstract fun betDao(): BetDao
    abstract fun winningNumberDao(): WinningNumberDao
    abstract fun closedDayDao(): ClosedDayDao
    abstract fun closedNumberDao(): ClosedNumberDao
    abstract fun limitDao(): LimitDao
    companion object { fun create(context: Context): LedgerDatabase = Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger2d.db").build() }
}
