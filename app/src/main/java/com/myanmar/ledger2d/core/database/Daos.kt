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
    @Upsert suspend fun upsert(value: CustomerEntity): Long
}
@Dao interface BetDao {
    @Insert suspend fun insertEntry(value: BetEntryEntity): Long
    @Insert suspend fun insertLines(values: List<BetLineEntity>)
    @Update suspend fun updateEntry(value: BetEntryEntity)
    @Query("DELETE FROM bet_lines WHERE betEntryId=:entryId") suspend fun deleteLines(entryId: Long)
    @Transaction @Query("SELECT * FROM bet_entries WHERE id=:id") fun observeEntry(id: Long): Flow<BetEntryWithLines?>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit ORDER BY bl.digit") fun observeCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.agentId=:agentId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit ORDER BY bl.digit") fun observeAgentTotals(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>
    @Query("SELECT bl.digit AS digit, COALESCE(SUM(bl.amount),0) AS amount FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session GROUP BY bl.digit") suspend fun getCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): List<DigitTotalRow>
    @Query("SELECT COALESCE(SUM(bl.amount),0) FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session") suspend fun getTotalBet(customerId: Long, date: LocalDate, session: DrawSession): Long
    @Query("SELECT COALESCE(SUM(bl.amount),0) FROM bet_lines bl JOIN bet_entries be ON be.id=bl.betEntryId WHERE be.customerId=:customerId AND be.drawDate=:date AND be.drawSession=:session AND bl.digit=:digit") suspend fun getWinningStake(customerId: Long, date: LocalDate, session: DrawSession, digit: String): Long
}
@Dao interface WinningNumberDao {
    @Query("SELECT * FROM winning_numbers ORDER BY date DESC, session") fun observeAll(): Flow<List<WinningNumberEntity>>
    @Query("SELECT * FROM winning_numbers WHERE date=:date AND session=:session") fun observe(date: LocalDate, session: DrawSession): Flow<WinningNumberEntity?>
    @Upsert suspend fun upsert(value: WinningNumberEntity): Long
}
@Dao interface ClosedDayDao {
    @Query("SELECT * FROM closed_days ORDER BY date") fun observeAll(): Flow<List<ClosedDayEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM closed_days WHERE date=:date)") suspend fun isClosed(date: LocalDate): Boolean
    @Upsert suspend fun upsert(value: ClosedDayEntity): Long
    @Delete suspend fun delete(value: ClosedDayEntity)
}
@Dao interface ClosedNumberDao {
    @Query("SELECT * FROM closed_numbers WHERE agentId=:agentId ORDER BY digit") fun observe(agentId: Long): Flow<List<ClosedNumberEntity>>
    @Query("SELECT digit FROM closed_numbers WHERE agentId=:agentId") suspend fun getDigits(agentId: Long): List<String>
    @Upsert suspend fun upsert(value: ClosedNumberEntity): Long
    @Delete suspend fun delete(value: ClosedNumberEntity)
}
@Dao interface LimitDao {
    @Query("SELECT * FROM all_limits WHERE customerId=:customerId") fun observeAllLimit(customerId: Long): Flow<AllLimitEntity?>
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId ORDER BY digit") fun observeSpecialLimits(customerId: Long): Flow<List<SpecialLimitEntity>>
    @Query("SELECT * FROM all_limits WHERE customerId=:customerId") suspend fun getAllLimit(customerId: Long): AllLimitEntity?
    @Query("SELECT * FROM special_limits WHERE customerId=:customerId") suspend fun getSpecialLimits(customerId: Long): List<SpecialLimitEntity>
    @Upsert suspend fun upsert(value: AllLimitEntity): Long
    @Upsert suspend fun upsert(value: SpecialLimitEntity): Long
    @Query("DELETE FROM all_limits WHERE customerId=:customerId") suspend fun clearAll(customerId: Long)
    @Delete suspend fun delete(value: SpecialLimitEntity)
}
