package com.myanmar.ledger2d.core.database

import androidx.room.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

@Entity(tableName = "agents", indices = [Index(value = ["name"])])
data class AgentEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val address: String = "", val phone: String = "", val rate: Long, val remark: String = "", val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "customers", foreignKeys = [ForeignKey(entity = AgentEntity::class, parentColumns = ["id"], childColumns = ["agentId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("agentId"), Index(value = ["agentId", "name"])])
data class CustomerEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val agentId: Long, val name: String, val address: String = "", val phone: String = "", val remark: String = "", val commissionRateBasisPoints: Int = 0, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "bet_entries", foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE), ForeignKey(entity = AgentEntity::class, parentColumns = ["id"], childColumns = ["agentId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("customerId"), Index("agentId"), Index(value = ["customerId", "drawDate", "drawSession"]), Index(value = ["agentId", "drawDate", "drawSession"])])
data class BetEntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long, val agentId: Long, val drawDate: LocalDate, val drawSession: DrawSession, val sourceText: String, val inputFormat: String = "MANUAL", val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "bet_lines", foreignKeys = [ForeignKey(entity = BetEntryEntity::class, parentColumns = ["id"], childColumns = ["betEntryId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)], indices = [Index("betEntryId"), Index("digit"), Index(value = ["betEntryId", "digit"], unique = true)])
data class BetLineEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val betEntryId: Long, val digit: String, val amount: Long)

@Entity(tableName = "winning_numbers", indices = [Index(value = ["date", "session"], unique = true)])
data class WinningNumberEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: LocalDate, val session: DrawSession, val digit: String, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "closed_days", indices = [Index(value = ["date"], unique = true)])
data class ClosedDayEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: LocalDate, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "closed_numbers", foreignKeys = [ForeignKey(entity = AgentEntity::class, parentColumns = ["id"], childColumns = ["agentId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("agentId"), Index(value = ["agentId", "digit"], unique = true)])
data class ClosedNumberEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val agentId: Long, val digit: String, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "all_limits", foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index(value = ["customerId"], unique = true)])
data class AllLimitEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long, val amount: Long, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "special_limits", foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("customerId"), Index(value = ["customerId", "digit"], unique = true)])
data class SpecialLimitEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long, val digit: String, val amount: Long, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "agent_all_limits", foreignKeys = [ForeignKey(entity = AgentEntity::class, parentColumns = ["id"], childColumns = ["agentId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index(value = ["agentId"], unique = true)])
data class AgentAllLimitEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val agentId: Long, val amount: Long, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "agent_special_limits", foreignKeys = [ForeignKey(entity = AgentEntity::class, parentColumns = ["id"], childColumns = ["agentId"], onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)], indices = [Index("agentId"), Index(value = ["agentId", "digit"], unique = true)])
data class AgentSpecialLimitEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val agentId: Long, val digit: String, val amount: Long, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "settlements", indices = [Index(value = ["agentId", "date", "session"], unique = true)])
data class SettlementEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val agentId: Long, val date: LocalDate, val session: DrawSession, val totalBet: Long, val payout: Long, val commission: Long, val netSettlement: Long, val settledAt: Long)

@Entity(tableName = "audit_events", indices = [Index("createdAt"), Index("entityType"), Index("entityId")])
data class AuditEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val entityType: String, val entityId: Long, val action: String, val detail: String = "", val createdAt: Long)

data class DigitTotalRow(val digit: String, val amount: Long)
data class BetEntryWithLines(@Embedded val entry: BetEntryEntity, @Relation(parentColumn = "id", entityColumn = "betEntryId") val lines: List<BetLineEntity>)

class DatabaseConverters {
    @TypeConverter fun fromDate(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun fromSession(value: DrawSession?): String? = value?.name
    @TypeConverter fun toSession(value: String?): DrawSession? = value?.let(DrawSession::valueOf)
}
