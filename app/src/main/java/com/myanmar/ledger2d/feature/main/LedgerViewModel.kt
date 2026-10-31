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
data class BetPreview(val parse:ParseResult=ParseResult.Error("စာရင်းထည့်ပါက အကြိုကြည့်ရှုနိုင်ပါမည်"),val validation:ValidationResult?=null,val loading:Boolean=false,val issues:List<String> = emptyList(),val emptyInput:Boolean=false){ val canConfirm get()=!emptyInput&&parse is ParseResult.Success&&validation?.canConfirm==true&&issues.isEmpty()&&!loading }
data class DrawReport(val calculation:DrawCalculation, val winningDigit:String?, val winnerAvailable:Boolean, val after:Boolean)
data class AgentCustomerReportRow(val customer:CustomerEntity, val calculation:DrawCalculation)
data class ScopeSummary(val id:Long, val name:String, val totalBet:Long, val winningStake:Long, val payout:Long, val profitLoss:Long, val commission:Long)
sealed interface HistoryUpdateState { data object Idle: HistoryUpdateState; data object Working: HistoryUpdateState; data class Success(val message:String): HistoryUpdateState; data class Error(val message:String): HistoryUpdateState }
class LedgerViewModel(private val container: AppContainer):ViewModel(){
    val agents=container.agents.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val winners=container.winners.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    val historyResults=container.history.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    fun historyResult(date:LocalDate)=container.history.observe(date)
    fun winner(date:LocalDate, session:DrawSession)=container.winners.observe(date,session)
    val closedDays=container.closedDays.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val engine=BetExpansionEngine(); private val validator=LimitValidator(); private val mutationMutex=Mutex()
    private val _preview=MutableStateFlow(BetPreview(emptyInput=true)); val preview:StateFlow<BetPreview> = _preview
    private var previewRequestId=0L
    private val _submit=MutableStateFlow<SubmitState>(SubmitState.Idle); val submit:StateFlow<SubmitState> = _submit
    private val _revision=MutableStateFlow(0L); val revision:StateFlow<Long> = _revision.asStateFlow()
    private val _historyUpdate=MutableStateFlow<HistoryUpdateState>(HistoryUpdateState.Idle)
    val historyUpdate:StateFlow<HistoryUpdateState> = _historyUpdate.asStateFlow()
    private fun changed(){ _revision.update { it + 1 } }
    // LIVE Auto Save bypasses ViewModel mutations; observe Room winner changes.
    init { viewModelScope.launch { container.winners.observeAll().collect { changed() } } }
    fun updateHistory(){if(_historyUpdate.value is HistoryUpdateState.Working)return;_historyUpdate.value=HistoryUpdateState.Working;viewModelScope.launch{runCatching{container.history.sync()}.onSuccess{_historyUpdate.value=HistoryUpdateState.Success("History "+it.startDate+" → "+it.endDate+" ("+it.updatedRows+" updates)");changed()}.onFailure{_historyUpdate.value=HistoryUpdateState.Error(it.message?:"History update မအောင်မြင်ပါ")}}}
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
        if(editing != null) {
            val issues=smart.lines.mapIndexedNotNull { index,line -> (line.result as? ParseResult.Error)?.let { "စာကြောင်း ${index+1} • "+line.source+": "+it.message } }
            if(issues.isNotEmpty()) return ParseResult.Error(issues.joinToString("; ")) to issues
            return engine.run { BetParser().aggregate(smart.lines.flatMap { (it.result as ParseResult.Success).bets }) } to emptyList()
        }
        val agentCurrent = container.bets.getAgentTotals(agentId,date,session).toMutableMap()
        val customerCurrent = container.bets.getCustomerTotals(customerId,date,session).toMutableMap()
        val agentLimits=container.agentLimits.get(agentId,date,session)
        val customerLimits=container.limits.get(customerId,date,session)
        val closed=container.closedNumbers.getDigits(agentId,date,session)
        val checker=SessionScopedLimitValidator()
        val accepted=mutableListOf<ExpandedBet>()
        val issues=mutableListOf<String>()
        for((lineIndex,line) in smart.lines.withIndex()){
            val result=line.result
            if(result is ParseResult.Error){
                issues += "စာကြောင်း ${lineIndex+1} • ${line.source}: ${result.message}"
                continue
            }
            val bets=(result as ParseResult.Success).bets
            val check=checker.validate(bets,agentCurrent,customerCurrent,agentLimits,customerLimits,closed)
            if(check.canConfirm){
                accepted+=bets
                bets.forEach{bet->
                    agentCurrent[bet.digit]=Math.addExact(agentCurrent[bet.digit]?:0L,bet.amount)
                    customerCurrent[bet.digit]=Math.addExact(customerCurrent[bet.digit]?:0L,bet.amount)
                }
            }else{
                // Attribute every rejection to the independent agent/customer rule.
                // Preview must never silently accept a partial batch.
                val a=LimitValidator().validate(bets,agentCurrent,agentLimits,closed)
                val b=LimitValidator().validate(bets,customerCurrent,customerLimits)
                val details=mutableListOf<String>()
                fun describe(scope:String,row:DigitValidation) {
                    if(row.isClosed) details += "${row.digit} • $scope: ပိတ်ဂဏန်း"
                    if(row.exceedsLimit) {
                        val limit=row.limit ?: return
                        val available=(limit-row.currentTotal).coerceAtLeast(0L)
                        val excess=(row.afterInput-limit).coerceAtLeast(0L)
                        details += "${row.digit} • $scope: ယခင် ${row.currentTotal} + အသစ် ${row.thisInput} = ${row.afterInput}၊ Limit $limit၊ လက်ကျန် $available၊ ကျော် $excess ကျပ်၊ အသစ်ထည့်ငွေ အပြည့်လက်မခံ"
                    }
                }
                a.rows.forEach{describe("Agent",it)}
                b.rows.forEach{describe("Customer",it)}
                issues += "စာကြောင်း ${lineIndex+1} • ${line.source}: ${details.joinToString(" | ")}"
            }
        }
        // Never turn a partially rejected paste into a confirmed financial entry.
        // Show all rejected lines and require the user to correct the whole batch.
        if(issues.isNotEmpty()) return ParseResult.Error("ပိတ်ဂဏန်း/Limit/စာကြောင်းအမှား ရှိနေသည်။ အားလုံးပြင်ပြီးမှ အတည်ပြုပါ။") to issues
        return if(accepted.isEmpty()) ParseResult.Error("စာရင်းထည့်ပါက အကြိုကြည့်ရှုနိုင်ပါမည်") to issues
        else engine.run { BetParser().aggregate(accepted) } to issues
    }
    private suspend fun effectiveLimits(agentId:Long,customerId:Long,date:LocalDate,session:DrawSession):EffectiveLimits {
        val agent=container.agentLimits.get(agentId,date,session)
        val customer=container.limits.get(customerId,date,session)
        val digits=(agent.specialLimits.keys+customer.specialLimits.keys).associateWith { digit ->
            listOfNotNull(agent.forDigit(digit),customer.forDigit(digit)).minOrNull() ?: Long.MAX_VALUE
        }.filterValues { it != Long.MAX_VALUE }
        val allLimit=listOfNotNull(agent.allLimit,customer.allLimit).minOrNull()
        return EffectiveLimits(allLimit,digits)
    }
    fun agent(id:Long)=container.agents.observe(id)
    suspend fun agentRateForDraw(id:Long,date:LocalDate,session:DrawSession):Long? =
        container.agents.rateForDraw(id,date,session)
    fun customers(agentId:Long)=container.customers.observeForAgent(agentId)
    fun customer(id:Long)=container.customers.observe(id)
    fun closedNumbers(agentId:Long,date:LocalDate,session:DrawSession)=container.closedNumbers.observe(agentId,date,session)
    fun allLimit(customerId:Long,date:LocalDate,session:DrawSession)=container.limits.observeAllLimit(customerId,date,session)
    fun specialLimits(customerId:Long,date:LocalDate,session:DrawSession)=container.limits.observeSpecial(customerId,date,session)
    fun agentAllLimit(agentId:Long,date:LocalDate,session:DrawSession)=container.agentLimits.observeAllLimit(agentId,date,session)
    fun agentSpecialLimits(agentId:Long,date:LocalDate,session:DrawSession)=container.agentLimits.observeSpecial(agentId,date,session)
    fun customerTotals(customerId:Long,date:LocalDate,session:DrawSession)=container.bets.observeCustomerTotals(customerId,date,session)
    fun customerEntries(customerId:Long)=container.bets.observeCustomerEntries(customerId)
    fun customerEntries(customerId:Long,date:LocalDate,session:DrawSession)=container.bets.observeCustomerEntries(customerId,date,session)
    fun agentEntries(agentId:Long,date:LocalDate,session:DrawSession)=container.bets.observeAgentEntries(agentId,date,session)
    fun betEntry(id:Long)=container.bets.observeEntry(id)
    fun agentTotals(agentId:Long,date:LocalDate,session:DrawSession)=container.bets.observeAgentTotals(agentId,date,session)
    fun agentTotalsWithCommission(agentId:Long,date:LocalDate,session:DrawSession)=container.bets.observeAgentTotalsWithCommission(agentId,date,session)
    fun saveAgent(id:Long,name:String,address:String,phone:String,rate:String,remark:String,effectiveDate:LocalDate,effectiveSession:DrawSession,forceSessionReprice:Boolean=false,onDone:()->Unit,onError:(String)->Unit={}){
        val clean=name.trim();val parsed=rate.toLongOrNull()
        if(clean.isEmpty()||parsed==null||parsed<=0)return
        viewModelScope.launch {
            val old=if(id>0)container.agents.get(id) else null
            val now=System.currentTimeMillis()
            val entity=AgentEntity(id=id,name=clean,address=address.trim(),phone=phone.trim(),rate=parsed,remark=remark.trim(),createdAt=old?.createdAt?:now,updatedAt=now)
            runCatching { container.agents.saveWithRateSchedule(entity,effectiveDate,effectiveSession,forceSessionReprice) }
                .onSuccess { changed();onDone() }
                .onFailure { onError(it.message?:"ဒိုင်အချက်အလက် သိမ်း၍မရပါ") }
        }
    }
    fun saveCustomer(id:Long,agentId:Long,name:String,address:String,phone:String,remark:String,commissionPercent:String,
        onDone:()->Unit,onError:(String)->Unit={}) {
        val clean=name.trim()
        if(clean.isEmpty()) return onError("ထိုးသားအမည် ထည့်ပါ")
        val openingRate=if(id==0L) commissionBasisPoints(commissionPercent) ?:return onError("ကော်မရှင် 0 မှ 100% အထိ သေချာသတ်မှတ်ပါ") else null
        viewModelScope.launch {
            runCatching {
                val old=if(id>0L)container.customers.get(id) ?:error("Customer not found") else null
                val now=System.currentTimeMillis()
                // Editing profile details never silently changes historical/active commission.
                container.customers.save(CustomerEntity(id=id,agentId=agentId,name=clean,
                    address=address.trim(),phone=phone.trim(),remark=remark.trim(),
                    commissionRateBasisPoints=old?.commissionRateBasisPoints?:openingRate!!,
                    createdAt=old?.createdAt?:now,updatedAt=now))
            }.onSuccess { changed();onDone() }.onFailure { onError(it.message?:"ထိုးသားသိမ်းမရပါ") }
        }
    }
    /** Existing call retained only for compatibility with older UI entry points. */
    private fun commissionBasisPoints(percent:String):Int? {
        val parsed=percent.trim().toBigDecimalOrNull() ?: return null
        if(parsed<java.math.BigDecimal.ZERO || parsed>java.math.BigDecimal(100))return null
        val scaled=parsed.movePointRight(2)
        if(scaled.stripTrailingZeros().scale()>0)return null
        return scaled.toInt()
    }
    suspend fun commissionForDraw(customerId:Long,date:LocalDate,session:DrawSession):Int? =
        container.commissions.rateForDraw(customerId,date,session)

    fun scheduleCommission(customerId:Long,percent:String,effectiveDate:LocalDate,effectiveSession:DrawSession,
        onDone:()->Unit,onError:(String)->Unit={}) {
        val bp=commissionBasisPoints(percent) ?: return onError("ကော်မရှင် 0 မှ 100% အထိသာ သတ်မှတ်ပါ")
        viewModelScope.launch {
            runCatching { container.commissions.schedule(customerId,bp,effectiveDate,effectiveSession) }
                .onSuccess { changed();onDone() }
                .onFailure { onError(it.message?:"ကော်မရှင် သိမ်းမရပါ") }
        }
    }


    fun scheduleCommissionForAgent(
        agentId:Long,percent:String,effectiveDate:LocalDate,effectiveSession:DrawSession,
        onDone:(Int)->Unit,onError:(String)->Unit={}
    ) {
        val bp=commissionBasisPoints(percent) ?: return onError("ကော်မရှင်နှုန်း 0% မှ 100% အတွင်း ထည့်ပါ")
        viewModelScope.launch {
            mutationMutex.withLock {
                runCatching { container.commissions.scheduleForAgent(agentId,bp,effectiveDate,effectiveSession) }
                    .onSuccess { count->
                        container.audit.record("COMMISSION_AGENT",agentId,"BULK_FORWARD",
                            effectiveDate.toString()+"/"+effectiveSession.name+"/"+bp+"/"+count)
                        changed();onDone(count)
                    }.onFailure { onError(it.message ?: "Customer အားလုံးအတွက် ကော်မရှင် ပြောင်းမရပါ") }
            }
        }
    }
    fun overrideCommissionForAgentDraw(
        agentId:Long,date:LocalDate,session:DrawSession,percent:String,
        onDone:(Int)->Unit,onError:(String)->Unit={}
    ) {
        val bp=commissionBasisPoints(percent) ?: return onError("ကော်မရှင်နှုန်း 0% မှ 100% အတွင်း ထည့်ပါ")
        viewModelScope.launch {
            mutationMutex.withLock {
                runCatching { container.commissions.overrideDrawForAgent(agentId,date,session,bp) }
                    .onSuccess { count->
                        container.audit.record("COMMISSION_AGENT",agentId,"BULK_DRAW",
                            date.toString()+"/"+session.name+"/"+bp+"/"+count)
                        changed();onDone(count)
                    }.onFailure { onError(it.message ?: "ဒီ Session ကို ပြန်တွက်၍ မရပါ") }
            }
        }
    }
    fun overrideSessionCommission(customerId:Long,date:LocalDate,session:DrawSession,percent:String,
        onDone:()->Unit,onError:(String)->Unit={}) {
        val bp=commissionBasisPoints(percent) ?: return onError("ကော်မရှင် 0 မှ 100% အထိသာ သတ်မှတ်ပါ")
        viewModelScope.launch {
            mutationMutex.withLock {
                runCatching { container.commissions.overrideDraw(customerId,date,session,bp) }
                    .onSuccess {
                        container.audit.record("COMMISSION",customerId,"DRAW_OVERRIDE","$date/${session.name}/$bp")
                        changed();onDone()
                    }
                    .onFailure { onError(it.message?:"ဒီ Session ၏ ကော်မရှင် ပြင်မရပါ") }
            }
        }
    }

    fun updateCommission(customerId:Long,percent:String,onDone:()->Unit) {
        val next=DrawRatePolicy.currentSession(java.time.LocalDateTime.now())
        scheduleCommission(customerId,percent,next.date,next.session,onDone)
    }
    fun updateAllLimit(customerId:Long,date:LocalDate,session:DrawSession,amount:String,onDone:()->Unit) {
        val parsed=amount.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        if(amount.isNotBlank()&&(parsed==null||parsed<=0))return
        viewModelScope.launch{container.limits.setAll(customerId,date,session,parsed);changed();onDone()}
    }
    fun updateAgentAllLimit(agentId:Long,date:LocalDate,session:DrawSession,amount:String,onDone:()->Unit) {
        val parsed=amount.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        if(amount.isNotBlank()&&(parsed==null||parsed<=0))return
        viewModelScope.launch{container.agentLimits.setAll(agentId,date,session,parsed);changed();onDone()}
    }
    fun addSpecialLimit(customerId:Long,date:LocalDate,session:DrawSession,digit:String,amount:String){
        val value=amount.toLongOrNull()?:return
        if(!BetParser.validDigit(digit)||value<=0)return
        viewModelScope.launch{container.limits.setSpecial(customerId,date,session,digit,value);changed()}
    }
    fun addAgentSpecialLimit(agentId:Long,date:LocalDate,session:DrawSession,digit:String,amount:String){
        val value=amount.toLongOrNull()?:return
        if(!BetParser.validDigit(digit)||value<=0)return
        viewModelScope.launch{container.agentLimits.setSpecial(agentId,date,session,digit,value);changed()}
    }
    fun removeSpecialLimit(value:SpecialLimitEntity){viewModelScope.launch{container.limits.deleteSpecial(value);container.audit.record("SPECIAL_LIMIT",value.id,"DELETE",value.drawDate.toString()+"/"+value.drawSession);changed()}}
    fun removeAgentSpecialLimit(value:AgentSpecialLimitEntity){viewModelScope.launch{container.agentLimits.deleteSpecial(value);container.audit.record("AGENT_SPECIAL_LIMIT",value.id,"DELETE",value.drawDate.toString()+"/"+value.drawSession);changed()}}
    fun addClosedNumber(agentId:Long,date:LocalDate,session:DrawSession,digit:String){
        if(!BetParser.validDigit(digit))return
        viewModelScope.launch{val id=container.closedNumbers.add(agentId,date,session,digit);container.audit.record("CLOSED_NUMBER",id,"CREATE",date.toString()+"/"+session+"/"+digit);changed()}
    }
    fun removeClosedNumber(value:ClosedNumberEntity){viewModelScope.launch{container.closedNumbers.remove(value);container.audit.record("CLOSED_NUMBER",value.id,"DELETE",value.drawDate.toString()+"/"+value.drawSession);changed()}}
    fun refreshPreview(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,raw:String,format:QuickFormat,editingEntryId:Long=0L,allowBackdated:Boolean=false){
        val requestId=++previewRequestId
        // Empty input is a normal initial state, not a limit/closed-number error.
        if(raw.isBlank()) {
            _preview.value=BetPreview(emptyInput=true)
            return
        }
        _preview.value=BetPreview(loading=true)
        viewModelScope.launch {
            fun emitIfLatest(result:BetPreview) {
                if(requestId==previewRequestId) _preview.value=result
            }
            val existing=if(editingEntryId>0) container.bets.getEntry(editingEntryId) else null
            if(!DrawSchedule.isWeekday(date)){emitIfLatest(BetPreview(ParseResult.Error("စနေ၊ တနင်္ဂနွေတွင် 2D စာရင်းမလုပ်ပါ")));return@launch}
            if(!allowBackdated&&date==LocalDate.now()&&!DrawSchedule.isSessionOpenForToday(session,java.time.LocalDateTime.now())&&existing==null){emitIfLatest(BetPreview(ParseResult.Error("ဒီ session အတွက် စာရင်းလက်ခံချိန် ကျော်လွန်သွားပါပြီ")));return@launch}
            if(container.closedDays.isClosed(date)&&existing?.entry?.drawDate!=date){emitIfLatest(BetPreview(ParseResult.Error("ဒီရက်သည် ပိတ်ရက်ဖြစ်သောကြောင့် စာရင်းသွင်း၍ မရပါ")));return@launch}
            val sameDraw=existing?.entry?.customerId==customerId&&existing.entry.drawDate==date&&existing.entry.drawSession==session
            if(!allowBackdated&&container.winners.get(date,session)!=null&&!sameDraw){emitIfLatest(BetPreview(ParseResult.Error("ဒီရက်နှင့် အချိန်အတွက် ထီပေါက်စဉ် ထည့်ပြီးပါပြီ")));return@launch}
            val (parsed,issues)=prepareInput(raw,format,agentId,customerId,date,session,existing?.takeIf { sameDraw })
            if(parsed is ParseResult.Error){emitIfLatest(BetPreview(parsed,issues=issues));return@launch}
            val agentCurrent=container.bets.getAgentTotals(agentId,date,session).toMutableMap()
            val customerCurrent=container.bets.getCustomerTotals(customerId,date,session).toMutableMap()
            if(sameDraw)existing!!.lines.forEach{line->
                agentCurrent[line.digit]=(agentCurrent[line.digit]?:0L)-line.amount
                customerCurrent[line.digit]=(customerCurrent[line.digit]?:0L)-line.amount
            }
            val agentLimits=container.agentLimits.get(agentId,date,session)
            val customerLimits=container.limits.get(customerId,date,session)
            val closed=container.closedNumbers.getDigits(agentId,date,session)
            val checker=SessionScopedLimitValidator()
            val currentBets=(parsed as ParseResult.Success).bets
            val original=if(sameDraw)existing!!.lines.associate{it.digit to it.amount} else null
            val checked=checker.validate(currentBets,agentCurrent,customerCurrent,agentLimits,customerLimits,closed,original)
            emitIfLatest(BetPreview(parsed,checked,issues=issues))
        }
    }
    private suspend fun canSubmit(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,bets:List<ExpandedBet>,editing:BetEntryWithLines?=null,allowBackdated:Boolean=false):Boolean {
        val customer=container.customers.get(customerId) ?: return false
        if(customer.agentId!=agentId || (editing != null && editing.entry.customerId != customerId)) return false
        if(!DrawSchedule.isWeekday(date)) return false
        if(!allowBackdated&&date==LocalDate.now()&&!DrawSchedule.isSessionOpenForToday(session,java.time.LocalDateTime.now())&&editing==null) return false
        if(container.closedDays.isClosed(date)&&editing?.entry?.drawDate!=date) return false
        val sameDraw=editing!=null && editing.entry.drawDate==date && editing.entry.drawSession==session
        if(!allowBackdated&&container.winners.get(date,session)!=null && !sameDraw) return false
        val agentCurrent=container.bets.getAgentTotals(agentId,date,session).toMutableMap()
        val customerCurrent=container.bets.getCustomerTotals(customerId,date,session).toMutableMap()
        if(sameDraw)editing!!.lines.forEach{line->
            agentCurrent[line.digit]=(agentCurrent[line.digit]?:0L)-line.amount
            customerCurrent[line.digit]=(customerCurrent[line.digit]?:0L)-line.amount
        }
        val agentLimits=container.agentLimits.get(agentId,date,session)
        val customerLimits=container.limits.get(customerId,date,session)
        val closed=container.closedNumbers.getDigits(agentId,date,session)
        val original=if(sameDraw)editing!!.lines.associate{it.digit to it.amount} else null
        return SessionScopedLimitValidator().validate(bets,agentCurrent,customerCurrent,agentLimits,customerLimits,closed,original).canConfirm
    }
    fun confirm(customerId:Long,agentId:Long,date:LocalDate,session:DrawSession,source:String,
        format:QuickFormat=QuickFormat.MANUAL,allowBackdated:Boolean=false){
        _submit.value=SubmitState.Working
        viewModelScope.launch { mutationMutex.withLock {
            runCatching {
                val prepared=prepareInput(source,format,agentId,customerId,date,session)
                val parsed=prepared.first as? ParseResult.Success ?:error("စာရင်းသွင်းရန် အဆင်ပြေသောစာရင်းမရှိပါ")
                require(canSubmit(customerId,agentId,date,session,parsed.bets,allowBackdated=allowBackdated))
                container.bets.confirm(customerId,agentId,date,session,source,format,parsed.bets)
            }.onSuccess {
                container.audit.record("BET",it,"CREATE","$date/${session.name}")
                changed()
                _submit.value=SubmitState.Success(it)
            }.onFailure {
                _submit.value=SubmitState.Error(it.message?:"စာရင်းသွင်း၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။")
            }
        } }
    }
    fun deleteBet(value:BetEntryEntity,onError:(String)->Unit={}){ viewModelScope.launch { runCatching { container.bets.delete(value) }.onSuccess { container.audit.record("BET",value.id,"DELETE",value.drawDate.toString()+"/"+value.drawSession.name); changed() }.onFailure { onError(it.message?:"စာရင်းဖျက်၍မရပါ") } } }
    fun editConfirm(entry:BetEntryWithLines,date:LocalDate,session:DrawSession,source:String,format:QuickFormat=QuickFormat.MANUAL){ _submit.value=SubmitState.Working; viewModelScope.launch { mutationMutex.withLock { runCatching { val prepared=prepareInput(source,format,entry.entry.agentId,entry.entry.customerId,date,session,entry); val parsed=prepared.first as? ParseResult.Success ?: error("စာရင်းပြင်ရန် အဆင်ပြေသောစာရင်းမရှိပါ"); require(canSubmit(entry.entry.customerId,entry.entry.agentId,date,session,parsed.bets,entry)); container.bets.edit(entry.entry.copy(drawDate=date,drawSession=session,sourceText=source),parsed.bets,format) }.onSuccess { container.audit.record("BET",entry.entry.id,"UPDATE",date.toString()+"/"+session.name);changed(); _submit.value=SubmitState.Success(entry.entry.id) }.onFailure { _submit.value=SubmitState.Error("စာရင်းပြင်၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။") } } } }
    fun resetSubmit(){_submit.value=SubmitState.Idle}
    fun saveWinner(date:LocalDate,session:DrawSession,digit:String,onDone:()->Unit,onError:(String)->Unit={} ){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { runCatching { val existing=container.winners.get(date,session); val id=container.winners.save(date,session,digit);container.audit.record("WINNER",id,if(existing==null) "CREATE" else "UPDATE","$date/${session.name}/$digit") }.onSuccess { changed();onDone() }.onFailure { onError(it.message ?: "ရလဒ်သိမ်း၍ မရပါ") } } }
    fun updateWinner(existing:WinningNumberEntity,date:LocalDate,session:DrawSession,digit:String,onDone:()->Unit,onError:(String)->Unit={} ){ if(!BetParser.validDigit(digit))return; viewModelScope.launch { runCatching { val id=container.winners.update(existing,date,session,digit);container.audit.record("WINNER",id,"UPDATE","$date/${session.name}/$digit") }.onSuccess { changed();onDone() }.onFailure { onError(it.message ?: "ရလဒ်ပြင်၍ မရပါ") } } }
    fun deleteWinner(value:WinningNumberEntity,onError:(String)->Unit={} ){ viewModelScope.launch { runCatching { container.winners.delete(value) }.onSuccess { container.audit.record("WINNER",value.id,"DELETE","${value.date}/${value.session.name}"); changed() }.onFailure { onError(it.message ?: "ရလဒ်ဖျက်၍ မရပါ") } } }
    fun addClosedDay(date:LocalDate,onError:(String)->Unit={} ){ viewModelScope.launch { runCatching { container.closedDays.add(date) }.onSuccess { changed() }.onFailure { onError(it.message ?: "ပိတ်ရက်သိမ်း၍ မရပါ") } } }
    fun removeClosedDay(value:ClosedDayEntity){ viewModelScope.launch { runCatching { container.closedDays.remove(value) }.onSuccess { container.audit.record("CLOSED_DAY",value.id,"DELETE",value.date.toString()); changed() } } }
    /** Customer-facing report is read-only; no commission or agent P/L is exposed. */
    suspend fun customerStatement(
        agentId: Long,
        customerId: Long,
        range: StatementRange,
        selectedSession: DrawSession? = null,
    ): CustomerStatement {
        require(agentId > 0L && (customerId == -1L || customerId > 0L))
        val rows = container.database.betDao()
            .statementRatedRows(agentId, customerId, range.start, range.end)
            .map {
                StatementStake(
                    customerId = it.customerId, date = it.drawDate,
                    session = it.drawSession, digit = it.digit,
                    payoutRate = it.payoutRate, amount = it.amount,
                )
            }
        // Read only persisted, final winning numbers. UI-only LIVE provisional values
        // must never affect a customer statement.
        val winnersByDraw = container.winners.observeAll().first()
            .filter { range.contains(it.date) }
            .associate { (it.date to it.session) to it.digit }
        return CustomerStatementCalculator.build(rows, winnersByDraw, selectedSession)
    }

    private suspend fun customerFinancials(customerId:Long,date:LocalDate,session:DrawSession,winningDigit:String?):DrawCalculation {
        val totals=container.bets.getCustomerTotals(customerId,date,session)
        val base=ReportCalculator().calculate(totals,winningDigit,0,0)
        val savedPayout=winningDigit?.let { container.bets.getCustomerPayout(customerId,date,session,it) } ?: 0L
        val savedCommission=container.bets.getCustomerCommission(customerId,date,session)
        return base.withSnapshotFinancials(savedPayout,savedCommission)
    }
    private suspend fun agentFinancials(agentId:Long,date:LocalDate,session:DrawSession,winningDigit:String?):DrawCalculation {
        val totals=container.bets.getAgentTotals(agentId,date,session)
        val base=ReportCalculator().calculate(totals,winningDigit,0,0)
        val savedPayout=winningDigit?.let { container.bets.getAgentPayout(agentId,date,session,it) } ?: 0L
        val savedCommission=container.bets.getAgentCommission(agentId,date,session)
        return base.withSnapshotFinancials(savedPayout,savedCommission)
    }
    suspend fun customerReport(customerId:Long,date:LocalDate,session:DrawSession,after:Boolean = true):DrawReport {
        val customer=container.customers.get(customerId) ?: return DrawReport(DrawCalculation(0,0,0,0,0,0),null,!after,after)
        val winner=if(after) container.winners.get(date,session) else null
        return DrawReport(customerFinancials(customer.id,date,session,winner?.digit),winner?.digit,!after||winner!=null,after)
    }
    suspend fun aggregateCustomerReport(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean = true):DrawReport {
        val winner=if(after) container.winners.get(date,session) else null
        return DrawReport(agentFinancials(agentId,date,session,winner?.digit),winner?.digit,!after||winner!=null,after)
    }
    suspend fun agentReport(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean = true):DrawReport {
        val winner=if(after) container.winners.get(date,session) else null
        return DrawReport(agentFinancials(agentId,date,session,winner?.digit),winner?.digit,!after||winner!=null,after)
    }
    suspend fun allAgentSummaries(date:LocalDate,session:DrawSession,after:Boolean):List<ScopeSummary> = agents.value.mapNotNull { agent -> val report=agentReport(agent.id,date,session,after); if (!report.winnerAvailable || (report.calculation.totalBet==0L && after)) null else ScopeSummary(agent.id,agent.name,report.calculation.totalBet,report.calculation.winningStake,report.calculation.payout,report.calculation.profitLoss,report.calculation.commission) }
    suspend fun allCustomerSummaries(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean):List<ScopeSummary> = container.customers.getForAgent(agentId).mapNotNull { customer -> val report=customerReport(customer.id,date,session,after); if (!report.winnerAvailable || (report.calculation.totalBet==0L && after)) null else ScopeSummary(customer.id,customer.name,report.calculation.totalBet,report.calculation.winningStake,report.calculation.payout,report.calculation.profitLoss,report.calculation.commission) }
    suspend fun aggregateAgentSummary(date:LocalDate,session:DrawSession,after:Boolean):ScopeSummary? { val reports=agents.value.map { agentReport(it.id,date,session,after) }.filter { it.winnerAvailable && (it.calculation.totalBet>0L || !after) }; if (reports.isEmpty()) return null; return ScopeSummary(-1L,"ဒိုင်အားလုံး",reports.sumOf { it.calculation.totalBet },reports.sumOf { it.calculation.winningStake },reports.sumOf { it.calculation.payout },reports.sumOf { it.calculation.profitLoss },reports.sumOf { it.calculation.commission }) }
    suspend fun aggregateCustomerSummary(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean):ScopeSummary? { val reports=container.customers.getForAgent(agentId).map { customerReport(it.id,date,session,after) }.filter { it.winnerAvailable && (it.calculation.totalBet>0L || !after) }; if (reports.isEmpty()) return null; return ScopeSummary(-1L,"Customer အားလုံး",reports.sumOf { it.calculation.totalBet },reports.sumOf { it.calculation.winningStake },reports.sumOf { it.calculation.payout },reports.sumOf { it.calculation.profitLoss },reports.sumOf { it.calculation.commission }) }
    suspend fun agentCustomerReport(agentId:Long,date:LocalDate,session:DrawSession,after:Boolean):List<AgentCustomerReportRow> {
        val winner=if(after) container.winners.get(date,session) else null
        return container.customers.getForAgent(agentId).map { customer ->
            AgentCustomerReportRow(customer,customerFinancials(customer.id,date,session,winner?.digit))
        }
    }
    suspend fun weeklyCustomerReport(customerId:Long,anyDate:LocalDate,after:Boolean):WeeklyReport {
        val customer=container.customers.get(customerId) ?: return WeeklyReportCalculator().build(anyDate,emptyMap())
        val monday=anyDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val calculations=mutableMapOf<Pair<LocalDate,DrawSession>,DrawCalculation>()
        val winners=mutableMapOf<Pair<LocalDate,DrawSession>,String>()
        for(offset in 0L..4L) {
            val date=monday.plusDays(offset)
            for(session in DrawSession.entries) {
                val winner=container.winners.get(date,session)
                if(after && winner!=null) winners[date to session]=winner.digit
                // Before shows already-entered bets even for a future-dated test draw.
                // After stays gated by the saved winning digit.
                val eligible=!after||winner!=null
                if(eligible) calculations[date to session]=customerFinancials(customer.id,date,session,if(after) winner?.digit else null)
            }
        }
        return WeeklyReportCalculator().build(anyDate,calculations,winners)
    }
    suspend fun aggregateWeeklyCustomerReport(agentId:Long,anyDate:LocalDate,after:Boolean):WeeklyReport { val customers=container.customers.getForAgent(agentId); val reports=customers.map { weeklyCustomerReport(it.id,anyDate,after) }; if (reports.isEmpty()) return WeeklyReportCalculator().build(anyDate,emptyMap()); val calculations=mutableMapOf<Pair<LocalDate,DrawSession>,DrawCalculation>(); val winners=mutableMapOf<Pair<LocalDate,DrawSession>,String>(); reports.flatMap { it.rows }.groupBy { it.date to it.session }.forEach { (key, rows) -> rows.mapNotNull { it.calculation }.takeIf { it.isNotEmpty() }?.let { calcs -> calculations[key]=DrawCalculation(calcs.sumOf { it.totalBet },calcs.sumOf { it.distinctSlots },calcs.sumOf { it.winningStake },calcs.sumOf { it.payout },calcs.sumOf { it.commission },calcs.sumOf { it.profitLoss }) }; rows.mapNotNull { it.winningDigit }.firstOrNull()?.let { winners[key]=it } }; return WeeklyReportCalculator().build(anyDate,calculations,winners) }
    /** Read the ACTUAL saved payout ratios, rather than inferring a weighted average. */
    suspend fun analysisSavedPayoutRates(agentId:Long,customerId:Long,date:LocalDate,session:DrawSession):List<Long> {
        val entries = if (customerId == -1L)
            container.bets.observeAgentEntries(agentId,date,session).first()
        else container.bets.observeCustomerEntries(customerId,date,session).first()
        return entries.map { it.entry.payoutRate }.distinct().sorted()
    }

    /** Per-customer 80/90/100% warnings for the selected agent's selected draw. */
    suspend fun customerLimitWarnings(agentId:Long,date:LocalDate,session:DrawSession):List<CustomerLimitWarning> {
        return container.customers.getForAgent(agentId).mapNotNull { customer ->
            val amounts=container.bets.getCustomerTotals(customer.id,date,session)
            val limits=effectiveLimits(agentId,customer.id,date,session)
            CustomerLimitWarningCalculator.inspect(customer.id,customer.name,amounts,limits)
        }.sortedWith(compareByDescending<CustomerLimitWarning> { it.fullDigits.size }
            .thenByDescending { it.nearDigits.size }
            .thenByDescending { it.warningDigits.size }.thenBy { it.name })
    }

    suspend fun analysis(customerId:Long,date:LocalDate,session:DrawSession):AnalysisResult { val customer=container.customers.get(customerId)?:return AnalysisResult(0,0,0,0,0,0,emptyList(),emptySet(),emptySet(),emptyList()); val agent=container.agents.get(customer.agentId); return AnalysisCalculator().calculate(container.bets.getCustomerTotals(customerId,date,session),effectiveLimits(customer.agentId,customerId,date,session),container.closedNumbers.getDigits(customer.agentId,date,session),agent?.rate?:0,container.bets.getCustomerScenarioPayouts(customerId,date,session)) }
    suspend fun aggregateAnalysis(agentId:Long,date:LocalDate,session:DrawSession):AnalysisResult { val agent=container.agents.get(agentId); val limits=container.agentLimits.get(agentId,date,session); return AnalysisCalculator().calculate(container.bets.getAgentTotals(agentId,date,session),EffectiveLimits(limits.allLimit,limits.specialLimits),container.closedNumbers.getDigits(agentId,date,session),agent?.rate?:0,container.bets.getAgentScenarioPayouts(agentId,date,session)) }
}
