package com.myanmar.ledger2d.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [AgentEntity::class, CustomerEntity::class, BetEntryEntity::class, BetLineEntity::class, WinningNumberEntity::class, ClosedDayEntity::class, ClosedNumberEntity::class, AllLimitEntity::class, SpecialLimitEntity::class, AgentAllLimitEntity::class, AgentSpecialLimitEntity::class, SettlementEntity::class, AuditEventEntity::class, HistoryResultEntity::class, LiveDailyResultEntity::class, ManualWinnerLockEntity::class], version = 10, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun agentDao(): AgentDao
    abstract fun customerDao(): CustomerDao
    abstract fun betDao(): BetDao
    abstract fun winningNumberDao(): WinningNumberDao
    abstract fun manualWinnerLockDao(): ManualWinnerLockDao
    abstract fun historyResultDao(): HistoryResultDao
    abstract fun liveDailyResultDao(): LiveDailyResultDao
    abstract fun closedDayDao(): ClosedDayDao
    abstract fun closedNumberDao(): ClosedNumberDao
    abstract fun limitDao(): LimitDao
    abstract fun agentLimitDao(): AgentLimitDao
    abstract fun settlementDao(): SettlementDao
    abstract fun auditDao(): AuditDao
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
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS settlements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, agentId INTEGER NOT NULL, date TEXT NOT NULL, session TEXT NOT NULL, totalBet INTEGER NOT NULL, payout INTEGER NOT NULL, commission INTEGER NOT NULL, netSettlement INTEGER NOT NULL, settledAt INTEGER NOT NULL, FOREIGN KEY(agentId) REFERENCES agents(id) ON UPDATE CASCADE ON DELETE RESTRICT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_settlements_agentId_date_session ON settlements(agentId, date, session)")
                db.execSQL("CREATE TABLE IF NOT EXISTS audit_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entityType TEXT NOT NULL, entityId INTEGER NOT NULL, action TEXT NOT NULL, detail TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_createdAt ON audit_events(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_entityType ON audit_events(entityType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_entityId ON audit_events(entityId)")
            }
        }
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bet_entries ADD COLUMN commissionRateBasisPoints INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bet_entries ADD COLUMN commissionAmount INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS history_results (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, morning2d TEXT NOT NULL, morningSet TEXT NOT NULL, morningValue TEXT NOT NULL, evening2d TEXT NOT NULL, eveningSet TEXT NOT NULL, eveningValue TEXT NOT NULL, modern930 TEXT NOT NULL, internet930 TEXT NOT NULL, modern200 TEXT NOT NULL, internet200 TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_history_results_date ON history_results(date)")
            }
        }
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS live_daily_results (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, modern930 TEXT, internet930 TEXT, modern200 TEXT, internet200 TEXT, morning2d TEXT, morningSet TEXT, morningValue TEXT, evening2d TEXT, eveningSet TEXT, eveningValue TEXT, reference930SourceAt INTEGER, reference200SourceAt INTEGER, morningSourceAt INTEGER, eveningSourceAt INTEGER, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_live_daily_results_date ON live_daily_results(date)")
            }
        }
        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS manual_winner_locks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, session TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_manual_winner_locks_date_session ON manual_winner_locks(date, session)")
                // Treat winners saved before Hybrid was introduced as user-owned.
                db.execSQL("INSERT OR IGNORE INTO manual_winner_locks (date, session, createdAt, updatedAt) SELECT date, session, createdAt, updatedAt FROM winning_numbers")
            }
        }
        // Build 704 entries did not store historical payout rates. Preserve their
        // bets and freeze the last-known agent rate; older values cannot be recovered.
        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bet_entries ADD COLUMN payoutRate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE bet_entries SET payoutRate = COALESCE((SELECT rate FROM agents WHERE agents.id = bet_entries.agentId), 0)")
            }
        }
        // v9 global rules have no draw identity. Keep them with NULL draw columns
        // for recovery, never misapply them to a future session.
        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE closed_numbers ADD COLUMN drawDate TEXT")
                db.execSQL("ALTER TABLE closed_numbers ADD COLUMN drawSession TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_closed_numbers_agentId_digit")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_closed_numbers_agentId_drawDate_drawSession_digit ON closed_numbers (agentId, drawDate, drawSession, digit)")
                db.execSQL("ALTER TABLE all_limits ADD COLUMN drawDate TEXT")
                db.execSQL("ALTER TABLE all_limits ADD COLUMN drawSession TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_all_limits_customerId")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_all_limits_customerId_drawDate_drawSession ON all_limits (customerId, drawDate, drawSession)")
                db.execSQL("ALTER TABLE special_limits ADD COLUMN drawDate TEXT")
                db.execSQL("ALTER TABLE special_limits ADD COLUMN drawSession TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_special_limits_customerId_digit")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_special_limits_customerId_drawDate_drawSession_digit ON special_limits (customerId, drawDate, drawSession, digit)")
                db.execSQL("ALTER TABLE agent_all_limits ADD COLUMN drawDate TEXT")
                db.execSQL("ALTER TABLE agent_all_limits ADD COLUMN drawSession TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_agent_all_limits_agentId")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_all_limits_agentId_drawDate_drawSession ON agent_all_limits (agentId, drawDate, drawSession)")
                db.execSQL("ALTER TABLE agent_special_limits ADD COLUMN drawDate TEXT")
                db.execSQL("ALTER TABLE agent_special_limits ADD COLUMN drawSession TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_agent_special_limits_agentId_digit")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_special_limits_agentId_drawDate_drawSession_digit ON agent_special_limits (agentId, drawDate, drawSession, digit)")
            }
        }
        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger2d.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
    }
}
