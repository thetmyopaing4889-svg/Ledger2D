package com.myanmar.ledger2d.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.database.*
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface SubmitState { data object Idle:SubmitState; data object Working:SubmitState; data class Success(val id:Long):SubmitState; data class Error(val message:String):SubmitState }
data class BetPreview(val parse:ParseResult=ParseResult.Error("Enter bets to see a live preview"),val validation:ValidationResult?=null,val loading:Boolean=false){ val canConfirm get()=parse is ParseResult.Success&&validation?.canConfirm==true&&!loading }
data class DrawReport(val calculation:DrawCalculation, val winningDigit:String?, val winnerAvailable:Boolean)
class LedgerViewModel(private val container: AppContainer):ViewModel(){
    val agents=container.agents.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val winners=container.winners.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val closedDays=container.closedDays.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val engine=BetExpansionEngine(); private val validator=LimitValidator()
    private val _preview=MutableStateFlow(BetPreview()); val preview:StateFlow<BetPreview> = _preview
    private val _submit=MutableStateFlow<SubmitState>(SubmitState.Idle); val submit:StateFlow<SubmitState> = _submit
    fun agent(id:Long)=container.agents.observe(id)
    fun customers(agentId:Long)=container.customers.observeForAgent(agentId)
    fun customer(id:Long)=container.customers.observe(id)
    fun closedNumbers(agentId:Long)=container.closedNumbers.observe(agentId)
    fun allLimit(customerId:Long)=container.limits.observeAllLimit(customerId)
    fun specialLimits(customerId:Long)=container.limits.observeSpecial(customerId)
    fun customerTotals(customerId:Long,date:LocalDate,session:DrawSession)=container.bets.observeCustomerTotals(customerId,date,session)
    fun agentTotals(agentId:Long,date:LocalDate,session:DrawSession)=container.bets.observeAgentTotals(agentId,date,session)
    fun saveAgent(id:Long,name:String,address:String,phone:String,rate:String,remark:String,onDone:()->Unit){ val clean=name.trim(); val parsed=rate.toLongOrNull(); if(clean.isEmpty()||parsed==null||parsed<=0)return; viewModelScope.launch { val old=if(id>0)container.agents.get(id) else null; val now=System.currentTimeMillis(); container.agents.save(AgentEntity(id=id,name=clean,address=address.trim(),phone=phone.trim(),rate=parsed,remark=remark.trim(),createdAt=old?.createdAt?:now,updatedAt=now)); onDone() } }
    fun saveCustomer(id:Long,agentId:Long,name:String,address:String,phone:String,remark:String,onDone:()->Unit){ val clean=name.trim(); if(clean.isEmpty())return; viewModelScope.launch { val old=if(id>0)container.customers.get(id) else null; val now=System.currentTimeMillis(); container.customers.save(CustomerEntity(id=id,agentId=agentId,name=clean,address=address.trim(),phone=phone.trim(),remark=remark.trim(),commissionRateBasisPoints=old?.commissionRateBasisPoints?:0,createdAt=old?.createdAt?:now,updatedAt=now)); onDone() } }
    fun updateCommission(customerId:Long,percent:String,onDone:()->Unit){ val bp=percent.trim().toBigDecimalOrNull()?.movePointRight(2)?.toInt() ?: return; if(bp !in 0..10_000)return; viewModelScope.launch { val old=container.customers.get(customerId)?:return@launch; container.customers.save(old.copy(commissionRateBasisPoints=bp,updatedAt=System.currentTimeMillis())); onDone() } }
    fun updateAllLimit(customerId:Long,amount:String,onDone:()->Unit){ val parsed=amount.trim().takeIf(String::isNotEmpty)?.toLongOrNull(); if(amount.isNotBlank()&&(parsed==null||parsed<=0))return; viewModelScope.launch { container.limits.setAll(customerId,parsed); onDone() } }
    fun addSpecialLimit(customerId:Long,digit:String,amount:String){ val value=amount.toLongOrNull()?:return; if(!BetParser.validDigit(digit)||value<=0)return; viewModelScope.launch { container.limits.setSpecial(customerId,digit,value) } }
    fun addClosedNumber(agentId:Long,digit:String){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { container.closedNumbers.add(agentId,digit) } }
    fun removeClosedNumber(value:ClosedNumberEntity){ viewModelScope.launch { container.closedNumbers.remove(value) } }
    fun refreshPreview(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,raw:String,format:QuickFormat){ val parsed=engine.expand(raw,format); _preview.value=BetPreview(parsed,loading=parsed is ParseResult.Success); if(parsed !is ParseResult.Success)return; viewModelScope.launch { if(container.closedDays.isClosed(date)){_preview.value=BetPreview(ParseResult.Error("This date is a Closed Day"));return@launch}; val current=container.bets.getCustomerTotals(customerId,date,session); val limits=container.limits.get(customerId); val closed=container.closedNumbers.getDigits(agentId); _preview.value=BetPreview(parsed,validator.validate(parsed.bets,current,limits,closed)) } }
    fun confirm(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,source:String){ val state=_preview.value; val parsed=state.parse as? ParseResult.Success?:return; if(!state.canConfirm)return; _submit.value=SubmitState.Working; viewModelScope.launch { runCatching { container.bets.confirm(customerId,agentId,date,session,source,parsed.bets) }.onSuccess { _submit.value=SubmitState.Success(it) }.onFailure { _submit.value=SubmitState.Error("Unable to confirm this entry") } } }
    fun resetSubmit(){_submit.value=SubmitState.Idle}
    fun saveWinner(date:LocalDate,session:DrawSession,digit:String,onDone:()->Unit){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { container.winners.save(date,session,digit); onDone() } }
    suspend fun customerReport(customerId:Long,date:LocalDate,session:DrawSession):DrawReport { val customer=container.customers.get(customerId) ?: return DrawReport(DrawCalculation(0,0,0,0,0,0),null,false); val agent=container.agents.get(customer.agentId); val winner=container.winners.get(date,session); val totals=container.bets.getCustomerTotals(customerId,date,session); val calc=ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,customer.commissionRateBasisPoints); return DrawReport(calc,winner?.digit,winner!=null) }
    suspend fun agentReport(agentId:Long,date:LocalDate,session:DrawSession):DrawReport { val winner=container.winners.get(date,session); val totals=container.bets.getAgentTotals(agentId,date,session); val agent=container.agents.get(agentId); val calc=ReportCalculator().calculate(totals,winner?.digit,agent?.rate?:0,0); return DrawReport(calc,winner?.digit,winner!=null) }
}
