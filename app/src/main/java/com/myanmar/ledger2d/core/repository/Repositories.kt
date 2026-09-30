package com.myanmar.ledger2d.core.repository

import androidx.room.withTransaction

import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.domain.BetParser
import com.myanmar.ledger2d.core.domain.CommissionCalculator
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.model.*
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
    suspend fun seedIfEmpty(context: android.content.Context)
    suspend fun sync(context: android.content.Context): HistorySyncSummary
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
class RoomHistoryResultRepository(private val db: LedgerDatabase): HistoryResultRepository {
    private val dao = db.historyResultDao()
    override fun observeAll() = dao.observeAll()
    override fun observe(date: LocalDate) = dao.observe(date)
    override suspend fun get(date: LocalDate) = dao.get(date)

    override suspend fun seedIfEmpty(context: android.content.Context) {
        if (dao.count() > 0) return
        importSeed(context, LocalDate.MIN, LocalDate.MAX)
    }

    private suspend fun importSeed(context: android.content.Context, start: LocalDate, end: LocalDate) {
        val json = context.assets.open("history_seed.json").bufferedReader().use { it.readText() }
        val array = org.json.JSONArray(json)
        val rows = ArrayList<HistoryResultEntity>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val date = LocalDate.parse(o.getString("date"), DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            if (date.isBefore(start) || date.isAfter(end)) continue
            rows += HistoryResultEntity(
                date = date,
                morning2d = o.optString("morning2d", "-"),
                morningSet = o.optString("morningSet", "-"),
                morningValue = o.optString("morningValue", "-"),
                evening2d = o.optString("evening2d", "-"),
                eveningSet = o.optString("eveningSet", "-"),
                eveningValue = o.optString("eveningValue", "-"),
                modern930 = o.optString("modern930", "-"),
                internet930 = o.optString("internet930", "-"),
                modern200 = o.optString("modern200", "-"),
                internet200 = o.optString("internet200", "-")
            )
        }
        if (rows.isNotEmpty()) db.withTransaction { dao.upsertAll(rows) }
    }

    override suspend fun sync(context: android.content.Context): HistorySyncSummary {
        val today=LocalDate.now(); val start=LocalDate.of(today.year-3,1,1); val end=today.minusDays(1)
        if (end.isBefore(start)) return HistorySyncSummary(start,end,0)
        if (dao.count()==0) importSeed(context,start,end)
        var updated=0
        HistorySync.fetchShweLatest().filter{!it.date.isBefore(start)&&!it.date.isAfter(end)}.forEach{db.withTransaction{dao.upsert(HistorySync.merge(dao.get(it.date),it))};updated++}
        val minDate=dao.minDate()
        if(minDate==null){
            HistorySync.fetchThaiStockRange(start,end,6).forEach{db.withTransaction{dao.upsert(HistorySync.merge(dao.get(it.date),it))};updated++}
        }else{
            val headEnd=minDate.minusDays(1)
            if(!headEnd.isBefore(start)) HistorySync.fetchThaiStockRange(start,headEnd,6).forEach{db.withTransaction{dao.upsert(HistorySync.merge(dao.get(it.date),it))};updated++}
            var cursor=dao.maxDate()?.plusDays(1)?:start; if(cursor.isBefore(start)) cursor=start
            if(!cursor.isAfter(end)) HistorySync.fetchThaiStockRange(cursor,end,6).forEach{db.withTransaction{dao.upsert(HistorySync.merge(dao.get(it.date),it))};updated++}
        }
        dao.deleteBefore(start); return HistorySyncSummary(start,end,updated)
    }
}

object HistorySync {
    private val fmt=DateTimeFormatter.ofPattern("dd-MM-yyyy")
    suspend fun fetchShweLatest():List<HistoryResultEntity>=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
        val body=httpGet("https://backend.shwemyanmar2d.com/api/lv/twod-result").trim()
        val data=if(body.startsWith("{")) org.json.JSONObject(body).optJSONArray("data") else org.json.JSONArray(body)
        val out=ArrayList<HistoryResultEntity>()
        for(j in 0 until data.length()){val o=data.getJSONObject(j);out += HistoryResultEntity(
                date = LocalDate.parse(o.getString("date")),
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
            )};out
    }
    suspend fun fetchThaiStockRange(start:LocalDate,end:LocalDate,parallelism:Int):List<HistoryResultEntity> = kotlinx.coroutines.coroutineScope {
        if (start.isAfter(end)) return@coroutineScope emptyList()
        val dates = generateSequence(start) { p ->
            if (p.isBefore(end)) p.plusDays(1) else null
        }.toList()
        val result = ArrayList<HistoryResultEntity>()
        for (batch in dates.chunked(parallelism.coerceAtLeast(1))) {
            result += batch.map { d ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    fetchThaiStockDate(d)
                }
            }.awaitAll().filterNotNull()
        }
        result
    }
    private suspend fun fetchThaiStockDate(date:LocalDate):HistoryResultEntity?=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
        val arr=runCatching{org.json.JSONArray(httpGet("https://api.thaistock2d.com/2d_result?date="+date.format(fmt)))}.getOrNull()?:return@withContext null
        val child=arr.optJSONObject(0)?.optJSONArray("child")?:return@withContext null;var m:org.json.JSONObject?=null;var e:org.json.JSONObject?=null
        for(i in 0 until child.length()){val r=child.getJSONObject(i);when(r.optString("time")){"12:01:00"->m=r;"16:30:00"->e=r}}
        HistoryResultEntity(
            date = date,
            morning2d = m?.optString("twod", "-") ?: "-",
            morningSet = m?.optString("set", "-") ?: "-",
            morningValue = m?.optString("value", "-") ?: "-",
            evening2d = e?.optString("twod", "-") ?: "-",
            eveningSet = e?.optString("set", "-") ?: "-",
            eveningValue = e?.optString("value", "-") ?: "-",
            modern930 = "-",
            internet930 = "-",
            modern200 = "-",
            internet200 = "-"
        )
    }
    fun merge(old: HistoryResultEntity?, inc: HistoryResultEntity): HistoryResultEntity {
        if (old == null) return inc

        fun keepIncomingOrOld(incoming: String, existing: String): String {
            return if (incoming.isBlank() || incoming == "-") existing else incoming
        }

        return inc.copy(
            id = old.id,
            morning2d = keepIncomingOrOld(inc.morning2d, old.morning2d),
            morningSet = keepIncomingOrOld(inc.morningSet, old.morningSet),
            morningValue = keepIncomingOrOld(inc.morningValue, old.morningValue),
            evening2d = keepIncomingOrOld(inc.evening2d, old.evening2d),
            eveningSet = keepIncomingOrOld(inc.eveningSet, old.eveningSet),
            eveningValue = keepIncomingOrOld(inc.eveningValue, old.eveningValue),
            modern930 = keepIncomingOrOld(inc.modern930, old.modern930),
            internet930 = keepIncomingOrOld(inc.internet930, old.internet930),
            modern200 = keepIncomingOrOld(inc.modern200, old.modern200),
            internet200 = keepIncomingOrOld(inc.internet200, old.internet200)
        )
    }
    private fun httpGet(url:String):String{val c=java.net.URL(url).openConnection() as java.net.HttpURLConnection;c.connectTimeout=15_000;c.readTimeout=15_000;c.requestMethod="GET";c.setRequestProperty("Accept","application/json");return try{if(c.responseCode !in 200..299)error("HTTP "+c.responseCode);c.inputStream.bufferedReader().use{it.readText()}}finally{c.disconnect()}}
}

class RoomClosedDayRepository(private val dao:ClosedDayDao):ClosedDayRepository { override fun observeAll()=dao.observeAll(); override suspend fun isClosed(date:LocalDate)=dao.isClosed(date); override suspend fun add(date:LocalDate):Long { require(!date.isBefore(LocalDate.now())); val now=System.currentTimeMillis(); val old=dao.get(date); return dao.upsert(ClosedDayEntity(id=old?.id?:0,date=date,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun remove(value:ClosedDayEntity)=dao.delete(value) }
class RoomClosedNumberRepository(private val dao:ClosedNumberDao):ClosedNumberRepository { override fun observe(agentId:Long)=dao.observe(agentId); override suspend fun getDigits(agentId:Long)=dao.getDigits(agentId).toSet(); override suspend fun add(agentId:Long,digit:String):Long { require(com.myanmar.ledger2d.core.domain.BetParser.validDigit(digit)); val now=System.currentTimeMillis(); val old=dao.get(agentId,digit); return dao.upsert(ClosedNumberEntity(id=old?.id?:0,agentId=agentId,digit=digit,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun remove(value:ClosedNumberEntity)=dao.delete(value) }
class RoomLimitRepository(private val dao:LimitDao):LimitRepository { override fun observeAllLimit(customerId:Long)=dao.observeAllLimit(customerId); override fun observeSpecial(customerId:Long)=dao.observeSpecialLimits(customerId); override suspend fun get(customerId:Long)=EffectiveLimits(dao.getAllLimit(customerId)?.amount,dao.getSpecialLimits(customerId).associate { it.digit to it.amount }); override suspend fun setAll(customerId:Long,amount:Long?) { if(amount==null) dao.clearAll(customerId) else { require(amount>0); val now=System.currentTimeMillis(); val old=dao.getAllLimit(customerId); dao.upsert(AllLimitEntity(id=old?.id?:0,customerId=customerId,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) } }; override suspend fun setSpecial(customerId:Long,digit:String,amount:Long) { require(com.myanmar.ledger2d.core.domain.BetParser.validDigit(digit)&&amount>0); val now=System.currentTimeMillis(); val old=dao.getSpecial(customerId,digit); dao.upsert(SpecialLimitEntity(id=old?.id?:0,customerId=customerId,digit=digit,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun deleteSpecial(value:SpecialLimitEntity)=dao.delete(value) }
class RoomAgentLimitRepository(private val dao:AgentLimitDao):AgentLimitRepository { override fun observeAllLimit(agentId:Long)=dao.observeAllLimit(agentId); override fun observeSpecial(agentId:Long)=dao.observeSpecialLimits(agentId); override suspend fun get(agentId:Long)=EffectiveLimits(dao.getAllLimit(agentId)?.amount,dao.getSpecialLimits(agentId).associate { it.digit to it.amount }); override suspend fun setAll(agentId:Long,amount:Long?) { if(amount==null) dao.clearAll(agentId) else { require(amount>0); val now=System.currentTimeMillis(); val old=dao.getAllLimit(agentId); dao.upsert(AgentAllLimitEntity(id=old?.id?:0,agentId=agentId,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) } }; override suspend fun setSpecial(agentId:Long,digit:String,amount:Long) { require(BetParser.validDigit(digit)&&amount>0); val now=System.currentTimeMillis(); val old=dao.getSpecial(agentId,digit); dao.upsert(AgentSpecialLimitEntity(id=old?.id?:0,agentId=agentId,digit=digit,amount=amount,createdAt=old?.createdAt?:now,updatedAt=now)) }; override suspend fun deleteSpecial(value:AgentSpecialLimitEntity)=dao.deleteSpecial(value) }
class RoomSettlementRepository(private val dao: SettlementDao): SettlementRepository { override fun observeAgent(agentId:Long)=dao.observeAgent(agentId); override suspend fun get(agentId:Long,date:LocalDate,session:DrawSession)=dao.get(agentId,date,session); override suspend fun settle(value:SettlementEntity)=dao.upsert(value) }
class RoomAuditRepository(private val dao: AuditDao): AuditRepository { override fun observeAll()=dao.observeAll(); override suspend fun record(type:String,id:Long,action:String,detail:String)=dao.insert(AuditEventEntity(entityType=type,entityId=id,action=action,detail=detail,createdAt=System.currentTimeMillis())) }
