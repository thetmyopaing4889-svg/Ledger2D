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
data class BetPreview(val parse:ParseResult=ParseResult.Error("Enter bets to see a live preview"),val validation:ValidationResult?=null,val loading:Boolean=false){ val canConfirm get()=parse is ParseResult.Success&&validation?.canConfirm==true&&!loading }
data class DrawReport(val calculation:DrawCalculation, val winningDigit:String?, val winnerAvailable:Boolean, val after:Boolean)
data class AgentCustomerReportRow(val customer:CustomerEntity, val calculation:DrawCalculation)
class LedgerViewModel(private val container: AppContainer):ViewModel(){
    val agents=container.agents.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val winners=container.winners.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val closedDays=container.closedDays.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val engine=BetExpansionEngine(); private val validator=LimitValidator(); private val mutationMutex=Mutex()
    private val _preview=MutableStateFlow(BetPreview()); val preview:StateFlow<BetPreview> = _preview
    private val _submit=MutableStateFlow<SubmitState>(SubmitState.Idle); val submit:StateFlow<SubmitState> = _submit
    private val _revision=MutableStateFlow(0L); val revision:StateFlow<Long> = _revision.asStateFlow()
    private fun changed(){ _revision.update { it + 1 } }
    fun agent(id:Long)=container.agents.observe(id)
    fun customers(agentId:Long)=container.customers.observeForAgent(agentId)
    fun customer(id:Long)=container.customers.observe(id)
    fun closedNumbers(agentId:Long)=container.closedNumbers.observe(agentId)
    fun allLimit(customerId:Long)=container.limits.observeAllLimit(customerId)
    fun specialLimits(customerId:Long)=container.limits.observeSpecial(customerId)
    fun customerTotals(customerId:Long,date:LocalDate,session:DrawSession)=container.bets.observeCustomerTotals(customerId,date,session)
    fun customerEntries(customerId:Long)=container.bets.observeCustomerEntries(customerId)
    fun betEntry(id:Long)=container.bets.observeEntry(id)
    fun agentTotals(agentId:Long,date:LocalDate,session:DrawSession)=container.bets.observeAgentTotals(agentId,date,session)
    fun saveAgent(id:Long,name:String,address:String,phone:String,rate:String,remark:String,onDone:()->Unit){ val clean=name.trim(); val parsed=rate.toLongOrNull(); if(clean.isEmpty()||parsed==null||parsed<=0)return; viewModelScope.launch { val old=if(id>0)container.agents.get(id) else null; val now=System.currentTimeMillis(); container.agents.save(AgentEntity(id=id,name=clean,address=address.trim(),phone=phone.trim(),rate=parsed,remark=remark.trim(),createdAt=old?.createdAt?:now,updatedAt=now)); onDone() } }
    fun saveCustomer(id:Long,agentId:Long,name:String,address:String,phone:String,remark:String,onDone:()->Unit){ val clean=name.trim(); if(clean.isEmpty())return; viewModelScope.launch { val old=if(id>0)container.customers.get(id) else null; val now=System.currentTimeMillis(); container.customers.save(CustomerEntity(id=id,agentId=agentId,name=clean,address=address.trim(),phone=phone.trim(),remark=remark.trim(),commissionRateBasisPoints=old?.commissionRateBasisPoints?:0,createdAt=old?.createdAt?:now,updatedAt=now)); onDone() } }
    fun updateCommission(customerId:Long,percent:String,onDone:()->Unit){ val bp=percent.trim().toBigDecimalOrNull()?.movePointRight(2)?.toInt() ?: return; if(bp !in 0..10_000)return; viewModelScope.launch { val old=container.customers.get(customerId)?:return@launch; container.customers.save(old.copy(commissionRateBasisPoints=bp,updatedAt=System.currentTimeMillis())); onDone() } }
    fun updateAllLimit(customerId:Long,amount:String,onDone:()->Unit){ val parsed=amount.trim().takeIf(String::isNotEmpty)?.toLongOrNull(); if(amount.isNotBlank()&&(parsed==null||parsed<=0))return; viewModelScope.launch { container.limits.setAll(customerId,parsed); changed(); onDone() } }
    fun addSpecialLimit(customerId:Long,digit:String,amount:String){ val value=amount.toLongOrNull()?:return; if(!BetParser.validDigit(digit)||value<=0)return; viewModelScope.launch { container.limits.setSpecial(customerId,digit,value);changed() } }
    fun removeSpecialLimit(value:SpecialLimitEntity){ viewModelScope.launch { container.limits.deleteSpecial(value);changed() } }
    fun addClosedNumber(agentId:Long,digit:String){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { container.closedNumbers.add(agentId,digit);changed() } }
    fun removeClosedNumber(value:ClosedNumberEntity){ viewModelScope.launch { container.closedNumbers.remove(value);changed() } }
    fun refreshPreview(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,raw:String,format:QuickFormat,editingEntryId:Long=0L){ val parsed=engine.expand(raw,format); _preview.value=BetPreview(parsed,loading=parsed is ParseResult.Success); if(parsed !is ParseResult.Success)return; viewModelScope.launch { val existing=if(editingEntryId>0) container.bets.getEntry(editingEntryId) else null; if(container.closedDays.isClosed(date)){_preview.value=BetPreview(ParseResult.Error("This date is a Closed Day"));return@launch}; val sameDraw=existing?.entry?.customerId==customerId&&existing.entry.drawDate==date&&existing.entry.drawSession==session; if(container.winners.get(date,session)!=null&&!sameDraw){_preview.value=BetPreview(ParseResult.Error("ဒီရက်နှင့် အချိန်အတွက် ထီပေါက်စဉ် ထည့်ပြီးပါပြီ"));return@launch}; val current=container.bets.getCustomerTotals(customerId,date,session).toMutableMap(); if(sameDraw){existing!!.lines.forEach{current[it.digit]=(current[it.digit]?:0L)-it.amount;if(current[it.digit]==0L)current.remove(it.digit)}}; val limits=container.limits.get(customerId); val closed=container.closedNumbers.getDigits(agentId); _preview.value=BetPreview(parsed,validator.validate(parsed.bets,current,limits,closed)) } }
    private suspend fun canSubmit(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,bets:List<ExpandedBet>,editing:BetEntryWithLines?=null):Boolean {
        val customer=container.customers.get(customerId) ?: return false
        if(customer.agentId!=agentId || (editing != null && editing.entry.customerId != customerId)) return false
        if(container.closedDays.isClosed(date)) return false
        val sameDraw=editing!=null && editing.entry.drawDate==date && editing.entry.drawSession==session
        if(container.winners.get(date,session)!=null && !sameDraw) return false
        val current=container.bets.getCustomerTotals(customerId,date,session).toMutableMap()
        if(sameDraw) editing!!.lines.forEach { current[it.digit]=(current[it.digit]?:0L)-it.amount; if(current[it.digit]==0L) current.remove(it.digit) }
        return validator.validate(bets,current,container.limits.get(customerId),container.closedNumbers.getDigits(agentId)).canConfirm
    }
    fun confirm(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,source:String,format:QuickFormat=QuickFormat.MANUAL){ val parsed=engine.expand(source,format) as? ParseResult.Success?:return; _submit.value=SubmitState.Working; viewModelScope.launch { mutationMutex.withLock { runCatching { require(canSubmit(customerId,agentId,date,session,parsed.bets)); container.bets.confirm(customerId,agentId,date,session,source,parsed.bets) }.onSuccess { changed(); _submit.value=SubmitState.Success(it) }.onFailure { _submit.value=SubmitState.Error("စာရင်းသွင်း၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။") } } } }
    fun deleteBet(value:BetEntryEntity){ viewModelScope.launch { container.bets.delete(value); changed() } }
    fun editConfirm(entry:BetEntryWithLines,date:LocalDate,session:DrawSession,source:String,format:QuickFormat=QuickFormat.MANUAL){ val parsed=engine.expand(source,format) as? ParseResult.Success?:return; _submit.value=SubmitState.Working; viewModelScope.launch { mutationMutex.withLock { runCatching { require(canSubmit(entry.entry.customerId,entry.entry.agentId,date,session,parsed.bets,entry)); container.bets.edit(entry.entry.copy(drawDate=date,drawSession=session,sourceText=source),parsed.bets) }.onSuccess { changed(); _submit.value=SubmitState.Success(entry.entry.id) }.onFailure { _submit.value=SubmitState.Error("စာရင်းပြင်၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။") } } } }
    fun resetSubmit(){_submit.value=SubmitState.Idle}
    fun saveWinner(date:LocalDate,session:DrawSession,digit:String,onDone:()->Unit){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { if(container.winners.get(date,session)==null){container.winners.save(date,session,digit);changed();onDone()} } }
    fun updateWinner(existing:WinningNumberEntity,digit:String,onDone:()->Unit){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { container.winners.save(existing.date,existing.session,digit);changed();onDone() } }
    fun deleteWinner(value:WinningNumberEntity){ viewModelScope.launch { container.winners.delete(value);changed() } }
    fun addClosedDay(date:LocalDate){ viewModelScope.launch { container.closedDays.add(date);changed() } }
    fun removeClosedDay(value:ClosedDayEntity){ viewModelScope.launch { container.closedDays.remove(value);changed() } }
    suspend fun customerReport(customerId:Long,date:LocalDate,session:DrawSession,after:Boolean = true):DrawReport { val customer=container.customers.get(customerId) ?: return DrawReport(DrawCalculation(0,0,0,0,0,0),null,!after,after); val agent=container.agents.get(customer.agentId); val winner=if(after) container.winners.get(date,session) else null; val totals=container.bets.getCustomerTotals(customerId,date,session); val calc=ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,customer.commissionRateBasisPoints); return DrawReport(calc,winner?.digit,!after||winner!=null,after) }
    suspend fun agentReport(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean = true):DrawReport { val winner=if(after) container.winners.get(date,session) else null; val totals=container.bets.getAgentTotals(agentId,date,session); val agent=container.agents.get(agentId); val calc=ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,0); return DrawReport(calc,winner?.digit,!after||winner!=null,after) }
    suspend fun agentCustomerReport(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean):List<AgentCustomerReportRow>{ val agent=container.agents.get(agentId); val winner=if(after) container.winners.get(date,session) else null; return container.customers.getForAgent(agentId).map{customer->val totals=container.bets.getCustomerTotals(customer.id,date,session);AgentCustomerReportRow(customer,ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,customer.commissionRateBasisPoints))} }
    suspend fun weeklyCustomerReport(customerId:Long,anyDate:LocalDate,after:Boolean):WeeklyReport { val customer=container.customers.get(customerId) ?: return WeeklyReportCalculator().build(anyDate,emptyMap()); val agent=container.agents.get(customer.agentId); val monday=anyDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)); val calculations=mutableMapOf<Pair<LocalDate,DrawSession>,DrawCalculation>(); val winners=mutableMapOf<Pair<LocalDate,DrawSession>,String>(); for(offset in 0L..4L){val date=monday.plusDays(offset);for(session in DrawSession.entries){val winner=container.winners.get(date,session);if(winner!=null) winners[date to session]=winner.digit; val eligible=(!date.isAfter(LocalDate.now())&&!after)||(after&&winner!=null); if(eligible){val totals=container.bets.getCustomerTotals(customerId,date,session);calculations[date to session]=ReportCalculator().calculate(totals,if(after) winner?.digit else null,agent?.rate?:0,customer.commissionRateBasisPoints)}}}; return WeeklyReportCalculator().build(anyDate,calculations,winners) }
    suspend fun analysis(customerId:Long,date:LocalDate,session:DrawSession):AnalysisResult { val customer=container.customers.get(customerId)?:return AnalysisResult(0,0,0,0,0,0,emptyList(),emptySet(),emptySet(),emptyList()); val agent=container.agents.get(customer.agentId); return AnalysisCalculator().calculate(container.bets.getCustomerTotals(customerId,date,session),container.limits.get(customerId),container.closedNumbers.getDigits(customer.agentId),agent?.rate?:0) }
}
