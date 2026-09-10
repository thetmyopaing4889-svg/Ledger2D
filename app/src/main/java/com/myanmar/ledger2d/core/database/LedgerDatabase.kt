package com.myanmar.ledger2d.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [AgentEntity::class, CustomerEntity::class, BetEntryEntity::class, BetLineEntity::class, WinningNumberEntity::class, ClosedDayEntity::class, ClosedNumberEntity::class, AllLimitEntity::class, SpecialLimitEntity::class, AgentAllLimitEntity::class, AgentSpecialLimitEntity::class], version = 3, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun customerDao(): CustomerDao
    abstract fun betDao(): BetDao
    abstract fun winningNumberDao(): WinningNumberDao
    abstract fun closedDayDao(): ClosedDayDao
    abstract fun closedNumberDao(): ClosedNumberDao
    abstract fun limitDao(): LimitDao
    abstract fun agentLimitDao(): AgentLimitDao
    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS agent_all_limits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, agentId INTEGER NOT NULL, amount INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(agentId) REFERENCES agents(id) ON UPDATE CASCADE ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_all_limits_agentId ON agent_all_limits(agentId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS agent_special_limits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, agentId INTEGER NOT NULL, digit TEXT NOT NULL, amount INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(agentId) REFERENCES agents(id) ON UPDATE CASCADE ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_special_limits_agentId ON agent_special_limits(agentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_special_limits_agentId_digit ON agent_special_limits(agentId, digit)")
            }
        }
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE bet_entries ADD COLUMN inputFormat TEXT NOT NULL DEFAULT 'MANUAL'") }
        }
        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger2d.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
