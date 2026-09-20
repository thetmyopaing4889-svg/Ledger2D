package com.myanmar.ledger2d.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

sealed interface SubmitState { data object Idle:SubmitState; data object Working:SubmitState; data class Success(val id:Long):SubmitState; data class Error(val message:String):SubmitState }
data class BetPreview(val parse:ParseResult=ParseResult.Error("စာရင်းထည့်ပါက အကြိုကြည့်ရှုနိုင်ပါမည်"),val validation:ValidationResult?=null,val loading:Boolean=false,val issues:List<String> = emptyList()){ val canConfirm get()=parse is ParseResult.Success&&validation?.canConfirm==true&&!loading }
data class DrawReport(val calculation:DrawCalculation, val winningDigit:String?, val winnerAvailable:Boolean, val after:Boolean)
data class AgentCustomerReportRow(val customer:CustomerEntity, val calculation:DrawCalculation)
data class ScopeSummary(val id:Long, val name:String, val totalBet:Long, val winningStake:Long, val payout:Long, val profitLoss:Long, val commission:Long)
class LedgerViewModel(private val container: AppContainer):ViewModel(){
    val agents=container.agents.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val winners=container.winners.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val closedDays=container.closedDays.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val engine=BetExpansionEngine(); private val validator=LimitValidator(); private val mutationMutex=Mutex()
    private val _preview=MutableStateFlow(BetPreview()); val preview:StateFlow<BetPreview> = _preview
    private val _submit=MutableStateFlow<SubmitState>(SubmitState.Idle); val submit:StateFlow<SubmitState> = _submit
    private val _revision=MutableStateFlow(0L); val revision:StateFlow<Long> = _revision.asStateFlow()
    private fun changed(){ _revision.update { it + 1 } }
    private fun expandInput(raw: String, format: QuickFormat): ParseResult {
        val smart = engine.smartExpand(raw, format)
        val error = smart.lines.mapNotNull { it.result as? ParseResult.Error }.firstOrNull()
        return error ?: ParseResult.Success(smart.bets, smart.total)
    }

    private suspend fun prepareInput(
        raw: String,
        format: QuickFormat,
        agentId: Long,
        customerId: Long,
        date: LocalDate,
        session: DrawSession,
        editing: BetEntryWithLines? = null,
    ): Pair<ParseResult, List<String>> {
        val smart = engine.smartExpand(raw, format)
        val current = container.bets.getAgentTotals(agentId, date, session).toMutableMap()
        val sameDraw = editing != null && editing.entry.drawDate == date && editing.entry.drawSession == session
        if (sameDraw) editing!!.lines.forEach { current[it.digit] = (current[it.digit] ?: 0L) - it.amount }
        val limits = effectiveLimits(agentId, customerId)
        val closed = container.closedNumbers.getDigits(agentId)
        val accepted = mutableListOf<ExpandedBet>()
        val issues = mutableListOf<String>()
        for (line in smart.lines) {
            val result = line.result
            if (result is ParseResult.Error) {
                issues += "${line.source}: ${result.message}"
                continue
            }
            val bets = (result as ParseResult.Success).bets
            val check = validator.validate(bets, current, limits, closed)
            if (check.canConfirm) {
                accepted += bets
                bets.forEach { current[it.digit] = (current[it.digit] ?: 0L) + it.amount }
            } else {
                val bad = check.rows.filter { it.isClosed || it.exceedsLimit }.joinToString(", ") { row ->
                    "${row.digit} ${if (row.isClosed) "ပိတ်" else "ကန့်သတ်ချက်ကျော်"}"
                }
                issues += "${line.source}: $bad"
            }
        }
        return if (accepted.isEmpty()) ParseResult.Error(issues.firstOrNull() ?: "စာရင်းထည့်ပါက အကြိုကြည့်ရှုနိုင်ပါမည်") to issues
        else engine.run { BetParser().aggregate(accepted) } to issues
    }
    private suspend fun effectiveLimits(agentId:Long,customerId:Long):EffectiveLimits {
        val agent=container.agentLimits.get(agentId)
        val customer=container.limits.get(customerId)
        val digits=(agent.specialLimits.keys+customer.specialLimits.keys).associateWith { digit ->
            listOfNotNull(agent.forDigit(digit),customer.forDigit(digit)).minOrNull() ?: Long.MAX_VALUE
        }.filterValues { it != Long.MAX_VALUE }
        val allLimit=listOfNotNull(agent.allLimit,customer.allLimit).minOrNull()
        return EffectiveLimits(allLimit,digits)
    }
    fun agent(id:Long)=container.agents.observe(id)
    fun customers(agentId:Long)=container.customers.observeForAgent(agentId)
    fun customer(id:Long)=container.customers.observe(id)
    fun closedNumbers(agentId:Long)=container.closedNumbers.observe(agentId)
    fun allLimit(customerId:Long)=container.limits.observeAllLimit(customerId)
    fun specialLimits(customerId:Long)=container.limits.observeSpecial(customerId)
    fun agentAllLimit(agentId:Long)=container.agentLimits.observeAllLimit(agentId)
    fun agentSpecialLimits(agentId:Long)=container.agentLimits.observeSpecial(agentId)
    fun customerTotals(customerId:Long,date:LocalDate,session:DrawSession)=container.bets.observeCustomerTotals(customerId,date,session)
    fun customerEntries(customerId:Long)=container.bets.observeCustomerEntries(customerId)
    fun customerEntries(customerId:Long,date:LocalDate,session:DrawSession)=container.bets.observeCustomerEntries(customerId,date,session)
    fun betEntry(id:Long)=container.bets.observeEntry(id)
    fun agentTotals(agentId:Long,date:LocalDate,session:DrawSession)=container.bets.observeAgentTotals(agentId,date,session)
    fun agentTotalsWithCommission(agentId:Long,date:LocalDate,session:DrawSession)=container.bets.observeAgentTotalsWithCommission(agentId,date,session)
    fun saveAgent(id:Long,name:String,address:String,phone:String,rate:String,remark:String,onDone:()->Unit){ val clean=name.trim(); val parsed=rate.toLongOrNull(); if(clean.isEmpty()||parsed==null||parsed<=0)return; viewModelScope.launch { val old=if(id>0)container.agents.get(id) else null; val now=System.currentTimeMillis(); container.agents.save(AgentEntity(id=id,name=clean,address=address.trim(),phone=phone.trim(),rate=parsed,remark=remark.trim(),createdAt=old?.createdAt?:now,updatedAt=now)); onDone() } }
    fun saveCustomer(id:Long,agentId:Long,name:String,address:String,phone:String,remark:String,onDone:()->Unit){ val clean=name.trim(); if(clean.isEmpty())return; viewModelScope.launch { val old=if(id>0)container.customers.get(id) else null; val now=System.currentTimeMillis(); container.customers.save(CustomerEntity(id=id,agentId=agentId,name=clean,address=address.trim(),phone=phone.trim(),remark=remark.trim(),commissionRateBasisPoints=old?.commissionRateBasisPoints?:0,createdAt=old?.createdAt?:now,updatedAt=now)); onDone() } }
    fun updateCommission(customerId:Long,percent:String,onDone:()->Unit){ val bp=percent.trim().toBigDecimalOrNull()?.movePointRight(2)?.toInt() ?: return; if(bp !in 0..10_000)return; viewModelScope.launch { val old=container.customers.get(customerId)?:return@launch; container.customers.save(old.copy(commissionRateBasisPoints=bp,updatedAt=System.currentTimeMillis())); changed(); onDone() } }
    fun updateAllLimit(customerId:Long,amount:String,onDone:()->Unit){ val parsed=amount.trim().takeIf(String::isNotEmpty)?.toLongOrNull(); if(amount.isNotBlank()&&(parsed==null||parsed<=0))return; viewModelScope.launch { container.limits.setAll(customerId,parsed); changed(); onDone() } }
    fun updateAgentAllLimit(agentId:Long,amount:String,onDone:()->Unit){ val parsed=amount.trim().takeIf(String::isNotEmpty)?.toLongOrNull(); if(amount.isNotBlank()&&(parsed==null||parsed<=0))return; viewModelScope.launch { container.agentLimits.setAll(agentId,parsed); changed(); onDone() } }
    fun addSpecialLimit(customerId:Long,digit:String,amount:String){ val value=amount.toLongOrNull()?:return; if(!BetParser.validDigit(digit)||value<=0)return; viewModelScope.launch { container.limits.setSpecial(customerId,digit,value);changed() } }
    fun addAgentSpecialLimit(agentId:Long,digit:String,amount:String){ val value=amount.toLongOrNull()?:return; if(!BetParser.validDigit(digit)||value<=0)return; viewModelScope.launch { container.agentLimits.setSpecial(agentId,digit,value);changed() } }
    fun removeSpecialLimit(value:SpecialLimitEntity){ viewModelScope.launch { container.limits.deleteSpecial(value);container.audit.record("SPECIAL_LIMIT",value.id,"DELETE","${value.customerId}/${value.digit}");changed() } }
    fun removeAgentSpecialLimit(value:AgentSpecialLimitEntity){ viewModelScope.launch { container.agentLimits.deleteSpecial(value);container.audit.record("AGENT_SPECIAL_LIMIT",value.id,"DELETE","${value.agentId}/${value.digit}");changed() } }
    fun addClosedNumber(agentId:Long,digit:String){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { val id=container.closedNumbers.add(agentId,digit);container.audit.record("CLOSED_NUMBER",id,"CREATE","$agentId/$digit");changed() } }
    fun removeClosedNumber(value:ClosedNumberEntity){ viewModelScope.launch { container.closedNumbers.remove(value);container.audit.record("CLOSED_NUMBER",value.id,"DELETE","${value.agentId}/${value.digit}");changed() } }
    fun refreshPreview(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,raw:String,format:QuickFormat,editingEntryId:Long=0L,allowBackdated:Boolean=false){
        viewModelScope.launch {
            val existing=if(editingEntryId>0) container.bets.getEntry(editingEntryId) else null
            if(!DrawSchedule.isWeekday(date)){_preview.value=BetPreview(ParseResult.Error("စနေ၊ တနင်္ဂနွေတွင် 2D စာရင်းမလုပ်ပါ"));return@launch}
            if(!allowBackdated&&date==LocalDate.now()&&!DrawSchedule.isSessionOpenForToday(session,java.time.LocalDateTime.now())&&existing==null){_preview.value=BetPreview(ParseResult.Error("ဒီ session အတွက် စာရင်းလက်ခံချိန် ကျော်လွန်သွားပါပြီ"));return@launch}
            if(container.closedDays.isClosed(date)){_preview.value=BetPreview(ParseResult.Error("ဒီရက်သည် ပိတ်ရက်ဖြစ်သောကြောင့် စာရင်းသွင်း၍ မရပါ"));return@launch}
            val sameDraw=existing?.entry?.customerId==customerId&&existing.entry.drawDate==date&&existing.entry.drawSession==session
            if(!allowBackdated&&container.winners.get(date,session)!=null&&!sameDraw){_preview.value=BetPreview(ParseResult.Error("ဒီရက်နှင့် အချိန်အတွက် ထီပေါက်စဉ် ထည့်ပြီးပါပြီ"));return@launch}
            val (parsed,issues)=prepareInput(raw,format,agentId,customerId,date,session,existing?.takeIf { sameDraw })
            if(parsed is ParseResult.Error){_preview.value=BetPreview(parsed,issues=issues);return@launch}
            val current=container.bets.getAgentTotals(agentId,date,session).toMutableMap()
            if(sameDraw) existing!!.lines.forEach{current[it.digit]=(current[it.digit]?:0L)-it.amount;if(current[it.digit]==0L)current.remove(it.digit)}
            val limits=effectiveLimits(agentId,customerId)
            _preview.value=BetPreview(parsed,validator.validate((parsed as ParseResult.Success).bets,current,limits,container.closedNumbers.getDigits(agentId)),issues=issues)
        }
    }
    private suspend fun canSubmit(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,bets:List<ExpandedBet>,editing:BetEntryWithLines?=null,allowBackdated:Boolean=false):Boolean {
        val customer=container.customers.get(customerId) ?: return false
        if(customer.agentId!=agentId || (editing != null && editing.entry.customerId != customerId)) return false
        if(!DrawSchedule.isWeekday(date)) return false
        if(!allowBackdated&&date==LocalDate.now()&&!DrawSchedule.isSessionOpenForToday(session,java.time.LocalDateTime.now())&&editing==null) return false
        if(container.closedDays.isClosed(date)) return false
        val sameDraw=editing!=null && editing.entry.drawDate==date && editing.entry.drawSession==session
        if(!allowBackdated&&container.winners.get(date,session)!=null && !sameDraw) return false
        val current=container.bets.getAgentTotals(agentId,date,session).toMutableMap()
        if(sameDraw) editing!!.lines.forEach { current[it.digit]=(current[it.digit]?:0L)-it.amount; if(current[it.digit]==0L) current.remove(it.digit) }
        return validator.validate(bets,current,effectiveLimits(agentId,customerId),container.closedNumbers.getDigits(agentId)).canConfirm
    }
    fun confirm(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,source:String,format:QuickFormat=QuickFormat.MANUAL,allowBackdated:Boolean=false){ _submit.value=SubmitState.Working; viewModelScope.launch { mutationMutex.withLock { runCatching { val prepared=prepareInput(source,format,agentId,customerId,date,session); val parsed=prepared.first as? ParseResult.Success ?: error("စာရင်းသွင်းရန် အဆင်ပြေသောစာရင်းမရှိပါ"); require(canSubmit(customerId,agentId,date,session,parsed.bets,allowBackdated=allowBackdated)); container.bets.confirm(customerId,agentId,date,session,source,format,parsed.bets) }.onSuccess { container.audit.record("BET",it,"CREATE","$date/${session.name}"); changed(); _submit.value=SubmitState.Success(it) }.onFailure { _submit.value=SubmitState.Error("စာရင်းသွင်း၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။") } } } }
    fun deleteBet(value:BetEntryEntity){ viewModelScope.launch { runCatching { container.bets.delete(value) }.onSuccess { container.audit.record("BET",value.id,"DELETE","${value.drawDate}/${value.drawSession.name}"); changed() } } }
    fun editConfirm(entry:BetEntryWithLines,date:LocalDate,session:DrawSession,source:String,format:QuickFormat=QuickFormat.MANUAL){ _submit.value=SubmitState.Working; viewModelScope.launch { mutationMutex.withLock { runCatching { val prepared=prepareInput(source,format,entry.entry.agentId,entry.entry.customerId,date,session,entry); val parsed=prepared.first as? ParseResult.Success ?: error("စာရင်းပြင်ရန် အဆင်ပြေသောစာရင်းမရှိပါ"); require(canSubmit(entry.entry.customerId,entry.entry.agentId,date,session,parsed.bets,entry)); container.bets.edit(entry.entry.copy(drawDate=date,drawSession=session,sourceText=source),parsed.bets,format) }.onSuccess { changed(); _submit.value=SubmitState.Success(entry.entry.id) }.onFailure { _submit.value=SubmitState.Error("စာရင်းပြင်၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။") } } } }
    fun resetSubmit(){_submit.value=SubmitState.Idle}
    fun saveWinner(date:LocalDate,session:DrawSession,digit:String,onDone:()->Unit,onError:(String)->Unit={} ){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { runCatching { if(container.winners.get(date,session)!=null) error("ဒီ draw အတွက် ရလဒ်ရှိပြီးသားပါ"); val id=container.winners.save(date,session,digit);container.audit.record("WINNER",id,"CREATE","$date/${session.name}/$digit") }.onSuccess { changed();onDone() }.onFailure { onError(it.message ?: "ရလဒ်သိမ်း၍ မရပါ") } } }
    fun updateWinner(existing:WinningNumberEntity,date:LocalDate,session:DrawSession,digit:String,onDone:()->Unit,onError:(String)->Unit={} ){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { runCatching { val id=container.winners.update(existing,date,session,digit);container.audit.record("WINNER",id,"UPDATE","$date/${session.name}/$digit") }.onSuccess { changed();onDone() }.onFailure { onError(it.message ?: "ရလဒ်ပြင်၍ မရပါ") } } }
    fun deleteWinner(value:WinningNumberEntity,onError:(String)->Unit={} ){ viewModelScope.launch { runCatching { container.winners.delete(value) }.onSuccess { container.audit.record("WINNER",value.id,"DELETE","${value.date}/${value.session.name}"); changed() }.onFailure { onError(it.message ?: "ရလဒ်ဖျက်၍ မရပါ") } } }
    fun addClosedDay(date:LocalDate,onError:(String)->Unit={} ){ viewModelScope.launch { runCatching { container.closedDays.add(date) }.onSuccess { changed() }.onFailure { onError(it.message ?: "ပိတ်ရက်သိမ်း၍ မရပါ") } } }
    fun removeClosedDay(value:ClosedDayEntity){ viewModelScope.launch { runCatching { container.closedDays.remove(value) }.onSuccess { container.audit.record("CLOSED_DAY",value.id,"DELETE",value.date.toString()); changed() } } }
    fun settleAgent(agentId:Long,date:LocalDate,session:DrawSession){ viewModelScope.launch { if(container.winners.get(date,session)==null)return@launch; if(container.settlements.get(agentId,date,session)!=null)return@launch; val report=agentReport(agentId,date,session,true); val id=container.settlements.settle(SettlementEntity(agentId=agentId,date=date,session=session,totalBet=report.calculation.totalBet,payout=report.calculation.payout,commission=report.calculation.commission,netSettlement=report.calculation.netSettlement,settledAt=System.currentTimeMillis())); container.audit.record("SETTLEMENT",id,"CONFIRM","$date/${session.name}"); changed() } }
    suspend fun isSettled(agentId:Long,date:LocalDate,session:DrawSession):Boolean = container.settlements.get(agentId,date,session) != null
    suspend fun customerReport(customerId:Long,date:LocalDate,session:DrawSession,after:Boolean = true):DrawReport { val customer=container.customers.get(customerId) ?: return DrawReport(DrawCalculation(0,0,0,0,0,0),null,!after,after); val agent=container.agents.get(customer.agentId); val winner=if(after) container.winners.get(date,session) else null; val totals=container.bets.getCustomerTotals(customerId,date,session); val base=ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,customer.commissionRateBasisPoints); val calc=base.copy(commission=container.bets.getCustomerCommission(customerId,date,session)); return DrawReport(calc,winner?.digit,!after||winner!=null,after) }
    suspend fun agentReport(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean = true):DrawReport { val winner=if(after) container.winners.get(date,session) else null; val totals=container.bets.getAgentTotals(agentId,date,session); val agent=container.agents.get(agentId); val base=ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,0); val calc=base.copy(commission=container.bets.getAgentCommission(agentId,date,session)); return DrawReport(calc,winner?.digit,!after||winner!=null,after) }
    suspend fun allAgentSummaries(date:LocalDate,session:DrawSession,after:Boolean):List<ScopeSummary> = agents.value.mapNotNull { agent -> val report=agentReport(agent.id,date,session,after); if (!report.winnerAvailable || (report.calculation.totalBet==0L && after)) null else ScopeSummary(agent.id,agent.name,report.calculation.totalBet,report.calculation.winningStake,report.calculation.payout,report.calculation.profitLoss,report.calculation.commission) }
    suspend fun allCustomerSummaries(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean):List<ScopeSummary> = container.customers.getForAgent(agentId).mapNotNull { customer -> val report=customerReport(customer.id,date,session,after); if (!report.winnerAvailable || (report.calculation.totalBet==0L && after)) null else ScopeSummary(customer.id,customer.name,report.calculation.totalBet,report.calculation.winningStake,report.calculation.payout,report.calculation.profitLoss,report.calculation.commission) }
    suspend fun agentCustomerReport(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean):List<AgentCustomerReportRow>{ val agent=container.agents.get(agentId); val winner=if(after) container.winners.get(date,session) else null; return container.customers.getForAgent(agentId).map{customer->val totals=container.bets.getCustomerTotals(customer.id,date,session);val calculated=ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,customer.commissionRateBasisPoints);AgentCustomerReportRow(customer,calculated.copy(commission=container.bets.getCustomerCommission(customer.id,date,session)))} }
    suspend fun weeklyCustomerReport(customerId:Long,anyDate:LocalDate,after:Boolean):WeeklyReport { val customer=container.customers.get(customerId) ?: return WeeklyReportCalculator().build(anyDate,emptyMap()); val agent=container.agents.get(customer.agentId); val monday=anyDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)); val calculations=mutableMapOf<Pair<LocalDate,DrawSession>,DrawCalculation>(); val winners=mutableMapOf<Pair<LocalDate,DrawSession>,String>(); for(offset in 0L..4L){val date=monday.plusDays(offset);for(session in DrawSession.entries){val winner=container.winners.get(date,session);if(winner!=null) winners[date to session]=winner.digit; val eligible=(!date.isAfter(LocalDate.now())&&!after)||(after&&winner!=null); if(eligible){val totals=container.bets.getCustomerTotals(customerId,date,session);val calculated=ReportCalculator().calculate(totals,if(after) winner?.digit else null,agent?.rate?:0,customer.commissionRateBasisPoints);calculations[date to session]=calculated.copy(commission=container.bets.getCustomerCommission(customerId,date,session))}}}; return WeeklyReportCalculator().build(anyDate,calculations,winners) }
    suspend fun analysis(customerId:Long,date:LocalDate,session:DrawSession):AnalysisResult { val customer=container.customers.get(customerId)?:return AnalysisResult(0,0,0,0,0,0,emptyList(),emptySet(),emptySet(),emptyList()); val agent=container.agents.get(customer.agentId); return AnalysisCalculator().calculate(container.bets.getCustomerTotals(customerId,date,session),effectiveLimits(customer.agentId,customerId),container.closedNumbers.getDigits(customer.agentId),agent?.rate?:0) }
}
