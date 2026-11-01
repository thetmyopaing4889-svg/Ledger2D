package com.myanmar.ledger2d.core.repository

import androidx.room.withTransaction
import com.myanmar.ledger2d.core.database.AgentSpecialLimitEntity
import com.myanmar.ledger2d.core.database.ClosedNumberEntity
import com.myanmar.ledger2d.core.database.LedgerDatabase
import com.myanmar.ledger2d.core.domain.BetParser
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Same atomic, session-specific writes used by both pattern screens.
 * Keeping the writes here lets Room integration tests exercise the real
 * mutation path rather than separately reproducing its DAO operations.
 */
class AgentSessionRuleWriter(
    private val db: LedgerDatabase,
    private val audit: AuditRepository,
) {
    suspend fun closed(
        agentId: Long, date: LocalDate, session: DrawSession,
        digits: List<String>, close: Boolean,
    ): Int {
        validate(agentId, digits)
        return db.withTransaction {
            val dao = db.closedNumberDao()
            var count = 0
            digits.distinct().forEach { digit ->
                val existing = dao.get(agentId, date, session, digit)
                if (close && existing == null) {
                    val now = System.currentTimeMillis()
                    val id = dao.upsert(ClosedNumberEntity(
                        agentId = agentId, digit = digit, drawDate = date,
                        drawSession = session, createdAt = now, updatedAt = now,
                    ))
                    audit.record("CLOSED_NUMBER", id, "CREATE", "$date/$session/$digit/group")
                    count++
                } else if (!close && existing != null) {
                    dao.delete(existing)
                    audit.record("CLOSED_NUMBER", existing.id, "DELETE", "$date/$session/$digit/group")
                    count++
                }
            }
            count
        }
    }

    suspend fun specialLimits(
        agentId: Long, date: LocalDate, session: DrawSession,
        digits: List<String>, amount: Long?, remove: Boolean,
    ): Int {
        validate(agentId, digits)
        if (!remove) require(amount != null && amount > 0L) { "Invalid limit amount" }
        return db.withTransaction {
            val dao = db.agentLimitDao()
            var count = 0
            digits.distinct().forEach { digit ->
                val existing = dao.getSpecial(agentId, date, session, digit)
                if (remove) {
                    if (existing != null) {
                        dao.deleteSpecial(existing)
                        audit.record("AGENT_SPECIAL_LIMIT", existing.id, "DELETE",
                            "$date/$session/$digit/group")
                        count++
                    }
                } else if (existing?.amount != amount) {
                    val now = System.currentTimeMillis()
                    val row = AgentSpecialLimitEntity(
                        id = existing?.id ?: 0L, agentId = agentId, digit = digit,
                        amount = requireNotNull(amount), drawDate = date,
                        drawSession = session,
                        createdAt = existing?.createdAt ?: now, updatedAt = now,
                    )
                    val id = dao.upsert(row)
                    audit.record("AGENT_SPECIAL_LIMIT",
                        existing?.id ?: id, if (existing == null) "CREATE" else "UPDATE",
                        "$date/$session/$digit/group")
                    count++
                }
            }
            count
        }
    }

    private fun validate(agentId: Long, digits: List<String>) {
        require(agentId > 0 && digits.isNotEmpty() && digits.size <= 100 &&
            digits.all(BetParser::validDigit)) { "Invalid agent or 2D digits" }
    }
}
