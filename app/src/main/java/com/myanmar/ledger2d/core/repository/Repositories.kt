package com.myanmar.ledger2d.core.repository

import androidx.room.withTransaction

import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.domain.BetParser
import com.myanmar.ledger2d.core.domain.CommissionCalculator
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface AgentRepository { fun observeAll(): Flow<List<AgentEntity>>; fun observe(id: Long): Flow<AgentEntity?>; suspend fun get(id: Long): AgentEntity?; suspend fun save(value: AgentEntity): Long }
interface CustomerRepository { fun observeForAgent(agentId: Long): Flow<List<CustomerEntity>>; fun observe(id: Long): Flow<CustomerEntity?>; suspend fun get(id: Long): CustomerEntity?; suspend fun getForAgent(agentId: Long): List<CustomerEntity>; suspend fun save(value: CustomerEntity): Long }
interface BetRepository { fun observeCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>; fun observeAgentTotals(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitTotalRow>>; fun observeAgentTotalsWithCommission(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<DigitCommissionRow>>; fun observeEntry(id: Long): Flow<BetEntryWithLines?>; suspend fun getEntry(id: Long): BetEntryWithLines?; fun observeCustomerEntries(customerId: Long): Flow<List<BetEntryWithLines>>; fun observeCustomerEntries(customerId: Long, date: LocalDate, session: DrawSession): Flow<List<BetEntryWithLines>>; fun observeAgentEntries(agentId: Long, date: LocalDate, session: DrawSession): Flow<List<BetEntryWithLines>>; suspend fun getCustomerTotals(customerId: Long, date: LocalDate, session: DrawSession): Map<String, Long>; suspend fun getAgentTotals(agentId: Long, date: LocalDate, session: DrawSession): Map<String, Long>; suspend fun getCustomerCommission(customerId: Long, date: LocalDate, session: DrawSession): Long; suspend fun recalculateCustomerCommission(customerId: Long, basisPoints: Int); suspend fun getAgentCommission(agentId: Long, date: LocalDate, session: DrawSession): Long; suspend fun confirm(customerId: Long, agentId: Long, date: LocalDate, session: DrawSession, source: String, format: QuickFormat, bets: List<ExpandedBet>): Long; suspend fun edit(entry: BetEntryEntity, bets: List<ExpandedBet>, format: QuickFormat); suspend fun delete(entry: BetEntryEntity) }
interface WinningNumberRepository { fun observeAll(): Flow<List<WinningNumberEntity>>; fun observe(date: LocalDate, session: DrawSession): Flow<WinningNumberEntity?>; suspend fun get(date: LocalDate, session: DrawSession): WinningNumberEntity?; suspend fun save(date: LocalDate, session: DrawSession, digit: String): Long; suspend fun update(existing: WinningNumberEntity, date: LocalDate, session: DrawSession, digit: String): Long; suspend fun delete(value: WinningNumberEntity) }
interface HistoryResultRepository {
    fun observeAll(): Flow<List<HistoryResultEntity>>
    fun observe(date: LocalDate): Flow<HistoryResultEntity?>
    suspend fun get(date: LocalDate): HistoryResultEntity?
    suspend fun sync(): HistorySyncSummary
}

interface LiveDailyResultRepository {
    fun observeAll(): Flow<List<LiveDailyResultEntity>>
    fun observe(date: LocalDate): Flow<LiveDailyResultEntity?>
    suspend fun get(date: LocalDate): LiveDailyResultEntity?
    suspend fun apply(patches: List<LiveDailyResultPatch>): Int
}
interface ClosedDayRepository { fun observeAll(): Flow<List<ClosedDayEntity>>; suspend fun isClosed(date: LocalDate): Boolean; suspend fun add(date: LocalDate): Long; suspend fun remove(value: ClosedDayEntity) }
interface ClosedNumberRepository { fun observe(agentId: Long): Flow<List<ClosedNumberEntity>>; suspend fun getDigits(agentId: Long): Set<String>; suspend fun add(agentId: Long, digit: String): Long; suspend fun remove(value: ClosedNumberEntity) }
interface LimitRepository { fun observeAllLimit(customerId: Long): Flow<AllLimitEntity?>; fun observeSpecial(customerId: Long): Flow<List<SpecialLimitEntity>>; suspend fun get(customerId: Long): EffectiveLimits; suspend fun setAll(customerId: Long, amount: Long?); suspend fun setSpecial(customerId: Long, digit: String, amount: Long); suspend fun deleteSpecial(value: SpecialLimitEntity) }
interface AgentLimitRepository { fun observeAllLimit(agentId: Long): Flow<AgentAllLimitEntity?>; fun observeSpecial(agentId: Long): Flow<List<AgentSpecialLimitEntity>>; suspend fun get(agentId: Long): EffectiveLimits; suspend fun setAll(agentId: Long, amount: Long?); suspend fun setSpecial(agentId: Long, digit: String, amount: Long); suspend fun deleteSpecial(value: AgentSpecialLimitEntity) }
interface SettlementRepository { fun observeAgent(agentId: Long): Flow<List<SettlementEntity>>; suspend fun get(agentId: Long, date: LocalDate, session: DrawSession): SettlementEntity?; suspend fun settle(value: SettlementEntity): Long }
interface AuditRepository { fun observeAll(): Flow<List<AuditEventEntity>>; suspend fun record(type: String, id: Long, action: String, detail: String = ""): Long }

class RoomAgentRepository(private val dao: AgentDao): AgentRepository { override fun observeAll()=dao.observeAll(); override fun observe(id:Long)=dao.observe(id); override suspend fun get(id:Long)=dao.get(id); override suspend fun save(value:AgentEntity)=dao.upsert(value) }
class RoomCustomerRepository(private val dao: CustomerDao): CustomerRepository { override fun observeForAgent(agentId:Long)=dao.observeForAgent(agentId); override fun observe(id:Long)=dao.observe(id); override suspend fun get(id:Long)=dao.get(id); override suspend fun getForAgent(agentId:Long)=dao.getForAgent(agentId); override suspend fun save(value:CustomerEntity)=dao.upsert(value) }
class RoomBetRepository(private val db: LedgerDatabase): BetRepository {
    private val dao=db.betDao()
    override fun observeCustomerTotals(customerId:Long,date:LocalDate,session:DrawSession)=dao.observeCustomerTotals(customerId,date,session)
    override fun observeAgentTotals(agentId:Long,date:LocalDate,session:DrawSession)=dao.observeAgentTotals(agentId,date,session)
    override fun observeAgentTotalsWithCommission(agentId:Long,date:LocalDate,session:DrawSession)=dao.observeAgentEntries(agentId,date,session).map { entries ->
        val totals = mutableMapOf<String, Long>()
        val commissions = mutableMapOf<String, Long>()
        entries.forEach { entry ->
            val ordered = entry.lines.sortedBy { it.digit }
            val total = ordered.sumOf { it.amount }
            if (total > 0) {
                fun proportional(line: BetLineEntity) = Math.multiplyExact(entry.entry.commissionAmount, line.amount) / total
                val allocated = ordered.dropLast(1).sumOf(::proportional)
                ordered.forEach { line -> totals[line.digit] = Math.addExact(totals[line.digit] ?: 0L, line.amount) }
                ordered.forEachIndexed { index, line ->
                    val commission = if (index == ordered.lastIndex) Math.subtractExact(entry.entry.commissionAmount, allocated) else proportional(line)
                    commissions[line.digit] = Math.addExact(commissions[line.digit] ?: 0L, commission)
                }
            }
        }
        totals.keys.sorted().map { digit -> DigitCommissionRow(digit, totals.getValue(digit), commissions[digit] ?: 0L) }
    }
    override fun observeEntry(id:Long)=dao.observeEntry(id)
    override suspend fun getEntry(id:Long)=dao.getEntry(id)
    override fun observeCustomerEntries(customerId:Long)=dao.observeCustomerEntries(customerId)
    override fun observeCustomerEntries(customerId:Long,date:LocalDate,session:DrawSession)=dao.observeCustomerEntries(customerId,date,session)
    override fun observeAgentEntries(agentId:Long,date:LocalDate,session:DrawSession)=dao.observeAgentEntries(agentId,date,session)
    override suspend fun getCustomerTotals(customerId:Long,date:LocalDate,session:DrawSession)=dao.getCustomerTotals(customerId,date,session).associate { it.digit to it.amount }
    override suspend fun getAgentTotals(agentId:Long,date:LocalDate,session:DrawSession)=dao.getAgentTotals(agentId,date,session).associate { it.digit to it.amount }
    override suspend fun getCustomerCommission(customerId:Long,date:LocalDate,session:DrawSession)=dao.getCustomerCommission(customerId,date,session)
    override suspend fun recalculateCustomerCommission(customerId:Long,basisPoints:Int)=dao.recalculateCustomerCommission(customerId,basisPoints)
    override suspend fun getAgentCommission(agentId:Long,date:LocalDate,session:DrawSession)=dao.getAgentCommission(agentId,date,session)
    private fun validateLines(bets:List<ExpandedBet>) { require(bets.isNotEmpty()); require(bets.all { com.myanmar.ledger2d.core.domain.BetParser.validDigit(it.digit) && it.amount > 0 }); require(bets.map { it.digit }.distinct().size == bets.size) }
    private fun total(bets:List<ExpandedBet>) = bets.fold(0L) { sum, bet -> Math.addExact(sum, bet.amount) }
    override suspend fun confirm(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,source:String,format:QuickFormat,bets:List<ExpandedBet>):Long = db.withTransaction { validateLines(bets); require(source.isNotBlank()); val customer=db.customerDao().get(customerId) ?: error("Customer not found"); require(customer.agentId==agentId); val now=System.currentTimeMillis(); val amount=total(bets); val id=dao.insertEntry(BetEntryEntity(customerId=customerId,agentId=agentId,drawDate=date,drawSession=session,sourceText=source,inputFormat=format.name,commissionRateBasisPoints=customer.commissionRateBasisPoints,commissionAmount=CommissionCalculator().calculate(amount,customer.commissionRateBasisPoints),createdAt=now,updatedAt=now)); dao.insertLines(bets.map { BetLineEntity(betEntryId=id,digit=it.digit,amount=it.amount) }); id }
    override suspend fun edit(entry:BetEntryEntity,bets:List<ExpandedBet>,format:QuickFormat) = db.withTransaction { validateLines(bets); require(entry.sourceText.isNotBlank()); val old=dao.getEntry(entry.id)?.entry ?: error("Bet entry not found"); require(db.winningNumberDao().get(old.drawDate,old.drawSession)==null); require(db.winningNumberDao().get(entry.drawDate,entry.drawSession)==null); val amount=total(bets); dao.updateEntry(entry.copy(inputFormat=format.name,commissionAmount=CommissionCalculator().calculate(amount,entry.commissionRateBasisPoints),updatedAt=System.currentTimeMillis())); dao.deleteLines(entry.id); dao.insertLines(bets.map { BetLineEntity(betEntryId=entry.id,digit=it.digit,amount=it.amount) }) }
    override suspend fun delete(entry:BetEntryEntity) = db.withTransaction { require(db.winningNumberDao().get(entry.drawDate,entry.drawSession)==null); dao.deleteEntry(entry) }
}
class RoomWinningNumberRepository(private val db:LedgerDatabase):WinningNumberRepository { private val dao=db.winningNumberDao(); override fun observeAll()=dao.observeAll(); override fun observe(date:LocalDate,session:DrawSession)=dao.observe(date,session); override suspend fun get(date:LocalDate,session:DrawSession)=dao.get(date,session); override suspend fun save(date:LocalDate,session:DrawSession,digit:String):Long { require(BetParser.validDigit(digit)); require(com.myanmar.ledger2d.core.domain.DrawSchedule.isWeekday(date)); val now=System.currentTimeMillis(); val old=dao.get(date,session); require(old==null || db.betDao().countEntries(date,session)==0); return dao.upsert(WinningNumberEntity(id=old?.id?:0,date=date,session=session,digit=digit,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun update(existing:WinningNumberEntity,date:LocalDate,session:DrawSession,digit:String):Long = db.withTransaction { require(BetParser.validDigit(digit)); require(com.myanmar.ledger2d.core.domain.DrawSchedule.isWeekday(date)); require(db.betDao().countEntries(existing.date,existing.session)==0); require(db.betDao().countEntries(date,session)==0); val now=System.currentTimeMillis(); dao.delete(existing); dao.upsert(WinningNumberEntity(id=existing.id,date=date,session=session,digit=digit,createdAt=existing.createdAt,updatedAt=now)) }; override suspend fun delete(value:WinningNumberEntity) { require(db.betDao().countEntries(value.date,value.session)==0); dao.delete(value) } }
class RoomLiveDailyResultRepository(private val db: LedgerDatabase): LiveDailyResultRepository {
    private val dao = db.liveDailyResultDao()

    override fun observeAll() = dao.observeAll()
    override fun observe(date: LocalDate) = dao.observe(date)
    override suspend fun get(date: LocalDate) = dao.get(date)

    override suspend fun apply(patches: List<LiveDailyResultPatch>): Int =
        db.withTransaction {
            var changed = 0
            patches.forEach { patch ->
                val old = dao.get(patch.date)
                val merged = LiveDailyResultMerger.merge(
                    old = old,
                    patch = patch,
                    updatedAt = System.currentTimeMillis(),
                )
                if (merged != old) {
                    dao.upsert(merged)
                    changed++
                }
            }
            changed
        }
}

internal object LiveDailyResultMerger {
    fun merge(
        old: LiveDailyResultEntity?,
        patch: LiveDailyResultPatch,
        updatedAt: Long,
    ): LiveDailyResultEntity {
        val current = old ?: LiveDailyResultEntity(
            date = patch.date,
            modern930 = null,
            internet930 = null,
            modern200 = null,
            internet200 = null,
            morning2d = null,
            morningSet = null,
            morningValue = null,
            evening2d = null,
            eveningSet = null,
            eveningValue = null,
            reference930SourceAt = null,
            reference200SourceAt = null,
            morningSourceAt = null,
            eveningSourceAt = null,
            updatedAt = updatedAt,
        )

        var modern930 = current.modern930
        var internet930 = current.internet930
        var modern200 = current.modern200
        var internet200 = current.internet200
        var morning2d = current.morning2d
        var morningSet = current.morningSet
        var morningValue = current.morningValue
        var evening2d = current.evening2d
        var eveningSet = current.eveningSet
        var eveningValue = current.eveningValue
        var reference930SourceAt = current.reference930SourceAt
        var reference200SourceAt = current.reference200SourceAt
        var morningSourceAt = current.morningSourceAt
        var eveningSourceAt = current.eveningSourceAt

        val reference930Backfill = patch.reference930SourceAt == null
        if (
            (patch.modern930 != null || patch.internet930 != null) &&
            (reference930SourceAt == null || reference930Backfill || patch.reference930SourceAt >= reference930SourceAt)
        ) {
            patch.modern930?.let {
                if (!reference930Backfill || isMissingStoredValue(modern930)) modern930 = it
            }
            patch.internet930?.let {
                if (!reference930Backfill || isMissingStoredValue(internet930)) internet930 = it
            }
            patch.reference930SourceAt?.let {
                reference930SourceAt = maxOf(reference930SourceAt ?: Long.MIN_VALUE, it)
            }
        }

        val reference200Backfill = patch.reference200SourceAt == null
        if (
            (patch.modern200 != null || patch.internet200 != null) &&
            (reference200SourceAt == null || reference200Backfill || patch.reference200SourceAt >= reference200SourceAt)
        ) {
            patch.modern200?.let {
                if (!reference200Backfill || isMissingStoredValue(modern200)) modern200 = it
            }
            patch.internet200?.let {
                if (!reference200Backfill || isMissingStoredValue(internet200)) internet200 = it
            }
            patch.reference200SourceAt?.let {
                reference200SourceAt = maxOf(reference200SourceAt ?: Long.MIN_VALUE, it)
            }
        }

        val morningBackfill = patch.morningSourceAt == null
        if (
            (patch.morning2d != null || patch.morningSet != null || patch.morningValue != null) &&
            (morningSourceAt == null || morningBackfill || patch.morningSourceAt >= morningSourceAt)
        ) {
            patch.morning2d?.let {
                if (!morningBackfill || isMissingStoredValue(morning2d)) morning2d = it
            }
            patch.morningSet?.let {
                if (!morningBackfill || isMissingStoredValue(morningSet)) morningSet = it
            }
            patch.morningValue?.let {
                if (!morningBackfill || isMissingStoredValue(morningValue)) morningValue = it
            }
            patch.morningSourceAt?.let {
                morningSourceAt = maxOf(morningSourceAt ?: Long.MIN_VALUE, it)
            }
        }

        val eveningBackfill = patch.eveningSourceAt == null
        if (
            (patch.evening2d != null || patch.eveningSet != null || patch.eveningValue != null) &&
            (eveningSourceAt == null || eveningBackfill || patch.eveningSourceAt >= eveningSourceAt)
        ) {
            patch.evening2d?.let {
                if (!eveningBackfill || isMissingStoredValue(evening2d)) evening2d = it
            }
            patch.eveningSet?.let {
                if (!eveningBackfill || isMissingStoredValue(eveningSet)) eveningSet = it
            }
            patch.eveningValue?.let {
                if (!eveningBackfill || isMissingStoredValue(eveningValue)) eveningValue = it
            }
            patch.eveningSourceAt?.let {
                eveningSourceAt = maxOf(eveningSourceAt ?: Long.MIN_VALUE, it)
            }
        }

        val candidate = current.copy(
            modern930 = modern930,
            internet930 = internet930,
            modern200 = modern200,
            internet200 = internet200,
            morning2d = morning2d,
            morningSet = morningSet,
            morningValue = morningValue,
            evening2d = evening2d,
            eveningSet = eveningSet,
            eveningValue = eveningValue,
            reference930SourceAt = reference930SourceAt,
            reference200SourceAt = reference200SourceAt,
            morningSourceAt = morningSourceAt,
            eveningSourceAt = eveningSourceAt,
            updatedAt = updatedAt,
        )

        return if (candidate.copy(updatedAt = current.updatedAt) == current) {
            current
        } else {
            candidate
        }
    }

    private fun isMissingStoredValue(value: String?): Boolean =
        value.isNullOrBlank() ||
            value == "-" ||
            value == "--" ||
            value.equals("null", ignoreCase = true)
}

class RoomHistoryResultRepository(private val db: LedgerDatabase): HistoryResultRepository {
    private val dao = db.historyResultDao()

    override fun observeAll() = dao.observeAll()
    override fun observe(date: LocalDate) = dao.observe(date)
    override suspend fun get(date: LocalDate) = dao.get(date)

    override suspend fun sync(): HistorySyncSummary {
        val today = LocalDate.now()
        val start = LocalDate.of(today.year - 3, 1, 1)
        val end = today.minusDays(1)
        if (end.isBefore(start)) return HistorySyncSummary(start, end, 0)

        // History Result has one source of truth: the public 2D_history dataset.
        // WinningNumber/Bet/Settlement data are deliberately not touched here.
        val rows = HistorySync.fetch2DHistory(start, end)
        if (rows.isEmpty()) error("2D History source မှ data မရပါ")

        var updated = 0
        rows.forEach { row -> updated += upsertIfChanged(row) }

        // Keep Room bounded to the current rolling window only after a valid
        // source response has been received and merged successfully.
        dao.deleteBefore(start)

        return HistorySyncSummary(start, end, updated)
    }

    private suspend fun upsertIfChanged(incoming: HistoryResultEntity): Int {
        val old = dao.get(incoming.date)
        if (old == incoming) return 0
        dao.upsert(
            if (old == null) incoming
            else incoming.copy(id = old.id)
        )
        return 1
    }
}

object HistorySync {
    private const val HISTORY_URL =
        "https://raw.githubusercontent.com/2d3dthailand/2d3dthailand/main/2D_history"

    private val sourceDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    suspend fun fetch2DHistory(
        start: LocalDate,
        end: LocalDate
    ): List<HistoryResultEntity> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = httpGet(HISTORY_URL)
            parse2DHistory(body, start, end)
        }

    internal fun parse2DHistory(
        body: String,
        start: LocalDate,
        end: LocalDate
    ): List<HistoryResultEntity> {
        val data = org.json.JSONArray(body)
        val out = ArrayList<HistoryResultEntity>(data.length())

        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val date = LocalDate.parse(
                o.getString("date"),
                sourceDateFormatter
            )

            if (date.isBefore(start) || date.isAfter(end)) continue

            out += HistoryResultEntity(
                date = date,
                morning2d = o.optString("result_1200", "-"),
                morningSet = o.optString("set_1200", "-"),
                morningValue = o.optString("val_1200", "-"),
                evening2d = o.optString("result_430", "-"),
                eveningSet = o.optString("set_430", "-"),
                eveningValue = o.optString("val_430", "-"),
                modern930 = o.optString("modern_930", "-"),
                internet930 = o.optString("internet_930", "-"),
                modern200 = o.optString("modern_200", "-"),
                internet200 = o.optString("internet_200", "-")
            )
        }

        return out
            .distinctBy { it.date }
            .sortedByDescending { it.date }
    }

    private fun httpGet(url: String): String {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        return try {
            if (connection.responseCode !in 200..299) {
                error("HTTP " + connection.responseCode)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class RoomClosedDayRepository(private val dao:ClosedDayDao):ClosedDayRepository { override fun observeAll()=dao.observeAll(); override suspend fun isClosed(date:LocalDate)=dao.isClosed(date); override suspend fun add(date:LocalDate):Long { require(!date.isBefore(LocalDate.now())); val now=System.currentTimeMillis(); val old=dao.get(date); return dao.upsert(ClosedDayEntity(id=old?.id?:0,date=date,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun remove(value:ClosedDayEntity)=dao.delete(value) }
class RoomClosedNumberRepository(private val dao:ClosedNumberDao):ClosedNumberRepository { override fun observe(agentId:Long)=dao.observe(agentId); override suspend fun getDigits(agentId:Long)=dao.getDigits(agentId).toSet(); override suspend fun add(agentId:Long,digit:String):Long { require(com.myanmar.ledger2d.core.domain.BetParser.validDigit(digit)); val now=System.currentTimeMillis(); val old=dao.get(agentId,digit); return dao.upsert(ClosedNumberEntity(id=old?.id?:0,agentId=agentId,digit=digit,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun remove(value:ClosedNumberEntity)=dao.delete(value) }
class RoomLimitRepository(private val dao:LimitDao):LimitRepository { override fun observeAllLimit(customerId:Long)=dao.observeAllLimit(customerId); override fun observeSpecial(customerId:Long)=dao.observeSpecialLimits(customerId); override suspend fun get(customerId:Long)=EffectiveLimits(dao.getAllLimit(customerId)?.amount,dao.getSpecialLimits(customerId).associate { it.digit to it.amount }); override suspend fun setAll(customerId:Long,amount:Long?) { if(amount==null) dao.clearAll(customerId) else { require(amount>0); val now=System.currentTimeMillis(); val old=dao.getAllLimit(customerId); dao.upsert(AllLimitEntity(id=old?.id?:0,customerId=customerId,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) } }; override suspend fun setSpecial(customerId:Long,digit:String,amount:Long) { require(com.myanmar.ledger2d.core.domain.BetParser.validDigit(digit)&&amount>0); val now=System.currentTimeMillis(); val old=dao.getSpecial(customerId,digit); dao.upsert(SpecialLimitEntity(id=old?.id?:0,customerId=customerId,digit=digit,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun deleteSpecial(value:SpecialLimitEntity)=dao.delete(value) }
class RoomAgentLimitRepository(private val dao:AgentLimitDao):AgentLimitRepository { override fun observeAllLimit(agentId:Long)=dao.observeAllLimit(agentId); override fun observeSpecial(agentId:Long)=dao.observeSpecialLimits(agentId); override suspend fun get(agentId:Long)=EffectiveLimits(dao.getAllLimit(agentId)?.amount,dao.getSpecialLimits(agentId).associate { it.digit to it.amount }); override suspend fun setAll(agentId:Long,amount:Long?) { if(amount==null) dao.clearAll(agentId) else { require(amount>0); val now=System.currentTimeMillis(); val old=dao.getAllLimit(agentId); dao.upsert(AgentAllLimitEntity(id=old?.id?:0,agentId=agentId,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) } }; override suspend fun setSpecial(agentId:Long,digit:String,amount:Long) { require(BetParser.validDigit(digit)&&amount>0); val now=System.currentTimeMillis(); val old=dao.getSpecial(agentId,digit); dao.upsert(AgentSpecialLimitEntity(id=old?.id?:0,agentId=agentId,digit=digit,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun deleteSpecial(value:AgentSpecialLimitEntity)=dao.deleteSpecial(value) }
class RoomSettlementRepository(private val dao: SettlementDao): SettlementRepository { override fun observeAgent(agentId:Long)=dao.observeAgent(agentId); override suspend fun get(agentId:Long,date:LocalDate,session:DrawSession)=dao.get(agentId,date,session); override suspend fun settle(value:SettlementEntity)=dao.upsert(value) }
class RoomAuditRepository(private val dao: AuditDao): AuditRepository { override fun observeAll()=dao.observeAll(); override suspend fun record(type:String,id:Long,action:String,detail:String)=dao.insert(AuditEventEntity(entityType=type,entityId=id,action=action,detail=detail,createdAt=System.currentTimeMillis())) }
