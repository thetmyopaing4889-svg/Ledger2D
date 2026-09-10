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
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit ORDER BY bl.digit") fun observeCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.agentId=:agentId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit ORDER BY bl.digit") fun observeAgentTotals(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit") suspend fun getCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): List<DigitTotalRow>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.agentId=:agentId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit") suspend fun getAgentTotals(agentId: Long, date: LocalDate, session: DrawSession): List<DigitTotalRow>
    @Query("SELECT COALESCE(SUM(bl.amount),0) FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session") suspend fun getTotalBet(customerId: Long, date: LocalDate, session: DrawSession): Long
    @Query("SELECT COALESCE(SUM(bl.amount),0) FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session AND bl.digit=:digit") suspend fun getWinningStake(customerId: Long, date: LocalDate, session: DrawSession, digit: String): Long
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
@Dao interface ClosedDayDao {
    @Query("SELECT * FROM closed_days ORDER BY date") fun observeAll(): Flow<List<ClosedDayEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM closed_days WHERE date=:date)") suspend fun isClosed(date: LocalDate): Boolean
    @Query("SELECT * FROM closed_days WHERE date=:date") suspend fun get(date: LocalDate): ClosedDayEntity?
    @Upsert suspend fun upsert(value: ClosedDayEntity): Long
    @Delete suspend fun delete(value: ClosedDayEntity)
}
@Dao interface ClosedNumberDao {
    @Query("SELECT * FROM closed_numbers WHERE agentId=:agentId ORDER BY digit") fun observe(agentId: Long): Flow<List<ClosedNumberEntity>>
    @Query("SELECT digit FROM closed_numbers WHERE agentId=:agentId") suspend fun getDigits(agentId: Long): List<String>
    @Query("SELECT * FROM closed_numbers WHERE agentId=:agentId AND digit=:digit") suspend fun get(agentId: Long, digit: String): ClosedNumberEntity?
    @Upsert suspend fun upsert(value: ClosedNumberEntity): Long
    @Delete suspend fun delete(value: ClosedNumberEntity)
}
@Dao interface LimitDao {
    @Query("SELECT * FROM all_limits WHERE customerId=:customerId") fun observeAllLimit(customerId: Long): Flow<AllLimitEntity?>
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId ORDER BY digit") fun observeSpecialLimits(customerId: Long): Flow<List<SpecialLimitEntity>>
    @Query("SELECT * FROM all_limits WHERE customerId=:customerId") suspend fun getAllLimit(customerId: Long): AllLimitEntity?
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId") suspend fun getSpecialLimits(customerId: Long): List<SpecialLimitEntity>
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId AND digit=:digit") suspend fun getSpecial(customerId: Long, digit: String): SpecialLimitEntity?
    @Upsert suspend fun upsert(value: AllLimitEntity): Long
    @Upsert suspend fun upsert(value: SpecialLimitEntity): Long
    @Query("DELETE FROM all_limits WHERE customerId=:customerId") suspend fun clearAll(customerId: Long)
    @Delete suspend fun delete(value: SpecialLimitEntity)
}

@Dao interface AgentLimitDao {
    @Query("SELECT * FROM agent_all_limits WHERE agentId=:agentId") fun observeAllLimit(agentId: Long): Flow<AgentAllLimitEntity?>
    @Query("SELECT * FROM agent_special_limits WHERE agentId=:agentId ORDER BY digit") fun observeSpecialLimits(agentId: Long): Flow<List<AgentSpecialLimitEntity>>
    @Query("SELECT * FROM agent_all_limits WHERE agentId=:agentId") suspend fun getAllLimit(agentId: Long): AgentAllLimitEntity?
    @Query("SELECT * FROM agent_special_limits WHERE agentId=:agentId") suspend fun getSpecialLimits(agentId: Long): List<AgentSpecialLimitEntity>
    @Query("SELECT * FROM agent_special_limits WHERE agentId=:agentId AND digit=:digit") suspend fun getSpecial(agentId: Long, digit: String): AgentSpecialLimitEntity?
    @Upsert suspend fun upsert(value: AgentAllLimitEntity): Long
    @Upsert suspend fun upsert(value: AgentSpecialLimitEntity): Long
    @Query("DELETE FROM agent_all_limits WHERE agentId=:agentId") suspend fun clearAll(agentId: Long)
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
