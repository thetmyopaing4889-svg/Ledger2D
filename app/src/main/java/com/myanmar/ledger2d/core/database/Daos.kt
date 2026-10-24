package com.myanmar.ledger2d.core.database

import androidx.room.*
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<AgentEntity>>
    @Query("SELECT * FROM agents WHERE id=:id") fun observe(id: Long): Flow<AgentEntity?>
    @Query("SELECT * FROM agents WHERE id=:id") suspend fun get(id: Long): AgentEntity?
    @Upsert suspend fun upsert(value: AgentEntity): Long
}
@Dao interface CustomerDao {
    @Query("SELECT * FROM customers WHERE agentId=:agentId ORDER BY name COLLATE NOCASE") fun observeForAgent(agentId: Long): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE id=:id") fun observe(id: Long): Flow<CustomerEntity?>
    @Query("SELECT * FROM customers WHERE id=:id") suspend fun get(id: Long): CustomerEntity?
    @Query("SELECT * FROM customers WHERE agentId=:agentId ORDER BY name COLLATE NOCASE") suspend fun getForAgent(agentId: Long): List<CustomerEntity>
    @Upsert suspend fun upsert(value: CustomerEntity): Long
}
@Dao interface BetDao {
    @Insert suspend fun insertEntry(value: BetEntryEntity): Long
    @Insert suspend fun insertLines(values: List<BetLineEntity>)
    @Update suspend fun updateEntry(value: BetEntryEntity)
    @Query("DELETE FROM bet_lines WHERE betEntryId=:entryId") suspend fun deleteLines(entryId: Long)
    @Transaction @Query("SELECT * FROM bet_entries WHERE id=:id") fun observeEntry(id: Long): Flow<BetEntryWithLines?>
    @Transaction @Query("SELECT * FROM bet_entries WHERE id=:id") suspend fun getEntry(id: Long): BetEntryWithLines?
    @Transaction @Query("SELECT * FROM bet_entries WHERE customerId=:customerId ORDER BY drawDate DESC, drawSession DESC, id DESC") fun observeCustomerEntries(customerId: Long): Flow<List<BetEntryWithLines>>
    @Transaction @Query("SELECT * FROM bet_entries WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session ORDER BY id DESC") fun observeCustomerEntries(customerId: Long, date: LocalDate, session: DrawSession): Flow<List<BetEntryWithLines>>
    @Transaction @Query("SELECT * FROM bet_entries WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session ORDER BY id DESC") fun observeAgentEntries(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<BetEntryWithLines>>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit ORDER BY bl.digit") fun observeCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.agentId=:agentId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit ORDER BY bl.digit") fun observeAgentTotals(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit") suspend fun getCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): List<DigitTotalRow>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.agentId=:agentId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit") suspend fun getAgentTotals(agentId: Long, date: LocalDate, session: DrawSession): List<DigitTotalRow>
    @Query("SELECT COALESCE(SUM(bl.amount),0) FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session") suspend fun getTotalBet(customerId: Long, date: LocalDate, session: DrawSession): Long
    @Query("SELECT COALESCE(SUM(bl.amount),0) FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session AND bl.digit=:digit") suspend fun getWinningStake(customerId: Long, date: LocalDate, session: DrawSession, digit: String): Long
    @Query("SELECT COALESCE(SUM(commissionAmount),0) FROM bet_entries WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session") suspend fun getCustomerCommission(customerId: Long, date: LocalDate, session: DrawSession): Long
    @Query("SELECT bl.amount AS amount, be.payoutRate AS payoutRate FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session AND bl.digit=:digit") suspend fun getCustomerPayoutComponents(customerId: Long, date: LocalDate, session: DrawSession, digit: String): List<BetPayoutComponent>
    @Query("SELECT bl.amount AS amount, be.payoutRate AS payoutRate FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.agentId=:agentId AND be.drawDate=:date AND be.drawSession=:session AND bl.digit=:digit") suspend fun getAgentPayoutComponents(agentId: Long, date: LocalDate, session: DrawSession, digit: String): List<BetPayoutComponent>
    @Query("SELECT COALESCE(SUM(commissionAmount),0) FROM bet_entries WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session") suspend fun getAgentCommission(agentId: Long, date: LocalDate, session: DrawSession): Long
    @Query("SELECT COUNT(*) FROM bet_entries WHERE drawDate=:date AND drawSession=:session") suspend fun countEntries(date: LocalDate, session: DrawSession): Int
    @Delete suspend fun deleteEntry(value: BetEntryEntity)
}
@Dao interface WinningNumberDao {
    @Query("SELECT * FROM winning_numbers ORDER BY date DESC, session") fun observeAll(): Flow<List<WinningNumberEntity>>
    @Query("SELECT * FROM winning_numbers WHERE date=:date AND session=:session") fun observe(date: LocalDate, session: DrawSession): Flow<WinningNumberEntity?>
    @Query("SELECT * FROM winning_numbers WHERE date=:date AND session=:session") suspend fun get(date: LocalDate, session: DrawSession): WinningNumberEntity?
    @Upsert suspend fun upsert(value: WinningNumberEntity): Long
    @Delete suspend fun delete(value: WinningNumberEntity)
}

@Dao interface ManualWinnerLockDao {
    @Query("SELECT * FROM manual_winner_locks WHERE date=:date AND session=:session LIMIT 1")
    suspend fun get(date: LocalDate, session: DrawSession): ManualWinnerLockEntity?

    @Upsert
    suspend fun upsert(value: ManualWinnerLockEntity): Long
}
@Dao interface HistoryResultDao {
    @Query("SELECT * FROM history_results ORDER BY date DESC") fun observeAll(): Flow<List<HistoryResultEntity>>
    @Query("SELECT * FROM history_results WHERE date=:date") fun observe(date: LocalDate): Flow<HistoryResultEntity?>
    @Query("SELECT * FROM history_results WHERE date=:date") suspend fun get(date: LocalDate): HistoryResultEntity?
    @Upsert suspend fun upsert(value: HistoryResultEntity): Long
    @Upsert suspend fun upsertAll(values: List<HistoryResultEntity>)
    @Query("SELECT COUNT(*) FROM history_results") suspend fun count(): Int
    @Query("SELECT MIN(date) FROM history_results") suspend fun minDate(): LocalDate?
    @Query("SELECT MAX(date) FROM history_results") suspend fun maxDate(): LocalDate?
    @Query("SELECT date FROM history_results WHERE date BETWEEN :start AND :end") suspend fun getDates(start: LocalDate, end: LocalDate): List<LocalDate>
    @Query("DELETE FROM history_results WHERE date < :date") suspend fun deleteBefore(date: LocalDate)
}

@Dao interface LiveDailyResultDao {
    @Query("SELECT * FROM live_daily_results ORDER BY date DESC")
    fun observeAll(): Flow<List<LiveDailyResultEntity>>

    @Query("SELECT * FROM live_daily_results WHERE date=:date")
    fun observe(date: LocalDate): Flow<LiveDailyResultEntity?>

    @Query("SELECT * FROM live_daily_results WHERE date=:date")
    suspend fun get(date: LocalDate): LiveDailyResultEntity?

    @Upsert
    suspend fun upsert(value: LiveDailyResultEntity): Long
}

@Dao interface ClosedDayDao {
    @Query("SELECT * FROM closed_days ORDER BY date") fun observeAll(): Flow<List<ClosedDayEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM closed_days WHERE date=:date)") suspend fun isClosed(date: LocalDate): Boolean
    @Query("SELECT * FROM closed_days WHERE date=:date") suspend fun get(date: LocalDate): ClosedDayEntity?
    @Upsert suspend fun upsert(value: ClosedDayEntity): Long
    @Delete suspend fun delete(value: ClosedDayEntity)
}
@Dao interface ClosedNumberDao {
    @Query("SELECT * FROM closed_numbers WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session ORDER BY digit") fun observe(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<ClosedNumberEntity>>
    @Query("SELECT digit FROM closed_numbers WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session") suspend fun getDigits(agentId: Long, date: LocalDate, session: DrawSession): List<String>
    @Query("SELECT * FROM closed_numbers WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session AND digit=:digit") suspend fun get(agentId: Long, date: LocalDate, session: DrawSession, digit: String): ClosedNumberEntity?
    @Upsert suspend fun upsert(value: ClosedNumberEntity): Long
    @Delete suspend fun delete(value: ClosedNumberEntity)
}
@Dao interface LimitDao {
    @Query("SELECT * FROM all_limits WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session") fun observeAllLimit(customerId: Long, date: LocalDate, session: DrawSession): Flow<AllLimitEntity?>
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session ORDER BY digit") fun observeSpecialLimits(customerId: Long, date: LocalDate, session: DrawSession): Flow<List<SpecialLimitEntity>>
    @Query("SELECT * FROM all_limits WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session") suspend fun getAllLimit(customerId: Long, date: LocalDate, session: DrawSession): AllLimitEntity?
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session") suspend fun getSpecialLimits(customerId: Long, date: LocalDate, session: DrawSession): List<SpecialLimitEntity>
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session AND digit=:digit") suspend fun getSpecial(customerId: Long, date: LocalDate, session: DrawSession, digit: String): SpecialLimitEntity?
    @Upsert suspend fun upsert(value: AllLimitEntity): Long
    @Upsert suspend fun upsert(value: SpecialLimitEntity): Long
    @Query("DELETE FROM all_limits WHERE customerId=:customerId AND drawDate=:date AND drawSession=:session") suspend fun clearAll(customerId: Long, date: LocalDate, session: DrawSession)
    @Delete suspend fun delete(value: SpecialLimitEntity)
}

@Dao interface AgentLimitDao {
    @Query("SELECT * FROM agent_all_limits WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session") fun observeAllLimit(agentId: Long, date: LocalDate, session: DrawSession): Flow<AgentAllLimitEntity?>
    @Query("SELECT * FROM agent_special_limits WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session ORDER BY digit") fun observeSpecialLimits(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<AgentSpecialLimitEntity>>
    @Query("SELECT * FROM agent_all_limits WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session") suspend fun getAllLimit(agentId: Long, date: LocalDate, session: DrawSession): AgentAllLimitEntity?
    @Query("SELECT * FROM agent_special_limits WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session") suspend fun getSpecialLimits(agentId: Long, date: LocalDate, session: DrawSession): List<AgentSpecialLimitEntity>
    @Query("SELECT * FROM agent_special_limits WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session AND digit=:digit") suspend fun getSpecial(agentId: Long, date: LocalDate, session: DrawSession, digit: String): AgentSpecialLimitEntity?
    @Upsert suspend fun upsert(value: AgentAllLimitEntity): Long
    @Upsert suspend fun upsert(value: AgentSpecialLimitEntity): Long
    @Query("DELETE FROM agent_all_limits WHERE agentId=:agentId AND drawDate=:date AND drawSession=:session") suspend fun clearAll(agentId: Long, date: LocalDate, session: DrawSession)
    @Delete suspend fun deleteSpecial(value: AgentSpecialLimitEntity)
}

@Dao interface SettlementDao {
    @Query("SELECT * FROM settlements WHERE agentId=:agentId ORDER BY date DESC, session") fun observeAgent(agentId: Long): Flow<List<SettlementEntity>>
    @Query("SELECT * FROM settlements WHERE agentId=:agentId AND date=:date AND session=:session") suspend fun get(agentId: Long, date: LocalDate, session: DrawSession): SettlementEntity?
    @Upsert suspend fun upsert(value: SettlementEntity): Long
}

@Dao interface AuditDao {
    @Query("SELECT * FROM audit_events ORDER BY createdAt DESC") fun observeAll(): Flow<List<AuditEventEntity>>
    @Insert suspend fun insert(value: AuditEventEntity): Long
}
