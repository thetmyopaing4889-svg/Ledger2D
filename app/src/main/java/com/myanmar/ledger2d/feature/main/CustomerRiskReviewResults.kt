@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private fun Long.rm() = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun signed(amount: Long) = (if (amount > 0) "+" else "") + amount.rm()
private data class BudgetLoad(val budget: Long, val plan: RiskHedgePlan?)

private data class ReviewRiskData(
    val source: AnalysisResult,
    val decision: CustomerRiskDecision,
    val rates: List<Long>,
    val warnings: List<CustomerLimitWarning>,
)

@Composable
private fun RiskValue(label: String, value: String, tint: Color? = null, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = tint ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.End)
    }
}
@Composable
private fun RiskMoneyBox(title: String, amount: String, tint: Color, modifier: Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
            Text("ကျပ်", style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun RiskMoneyRange(best: Long, loss: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RiskMoneyBox("အများဆုံးမြတ်နိုင်", signed(best), Color(0xFF18794E), Modifier.weight(1f))
        RiskMoneyBox("အများဆုံးရှုံးနိုင်", if (loss == 0L) "0" else "−${loss.rm()}",
            MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable
private fun RiskDigitTable(source: AnalysisResult, showAll: Boolean, toggle: () -> Unit) {
    val risk = source.scenarios.filter { it.profitLoss < 0L }.sortedBy { it.profitLoss }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("အရှုံးဖြစ်နိုင်တဲ့ဂဏန်း ${risk.size} ကွက်", fontWeight = FontWeight.Bold)
        if (risk.isEmpty()) Text("အရှုံးဖြစ်နိုင်တဲ့ဂဏန်း မရှိပါ။")
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("ဂဏန်း", Modifier.weight(.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("ထိုးကြေး", Modifier.weight(1f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("ပြန်လျော်ငွေ", Modifier.weight(1.2f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("ရှုံးနိုင်ငွေ", Modifier.weight(1.15f), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            risk.take(if (showAll) risk.size else 8).forEach { scenario ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(scenario.digit, Modifier.weight(.7f), style = MaterialTheme.typography.labelSmall)
                    Text(scenario.stake.rm(), Modifier.weight(1f), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall)
                    Text(scenario.payout.rm(), Modifier.weight(1.2f), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall)
                    Text("−${(-scenario.profitLoss).rm()}", Modifier.weight(1.15f),
                        textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (risk.size > 8) TextButton(onClick = toggle) {
                Text(if (showAll) "အပေါ်ဆုံး ၈ ကွက်သာ" else "ကျန် ${risk.size - 8} ကွက် ကြည့်ရန်")
                Icon(if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
        }
    }
}

@Composable
private fun RiskPlanCard(plan: RiskHedgePlan, baseline: RiskSnapshot, allowCopy: Boolean) {
    val clipboard = LocalClipboardManager.current
    var showList by rememberSaveable(plan.id) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            RiskValue("ဒိုင်ဆီ ကာရန်စုစုပေါင်း", "${plan.upstreamTotal.rm()} ကျပ်", emphasize = true)
            RiskValue("ကိုယ့်လက်ထဲကျန်ငွေ", "${plan.keptCash.rm()} ကျပ်")
            HorizontalDivider()
            RiskMoneyRange(plan.bestProfit, plan.worstLoss)
            RiskValue("ကာမီ အများဆုံးရှုံးနိုင်", "${baseline.worstLoss.rm()} ကျပ်")
            RiskValue("ကာပြီး အများဆုံးရှုံးနိုင်", "${plan.worstLoss.rm()} ကျပ်", emphasize = true)
            RiskValue("ငွေပမာဏမျှခြေ", plan.balanceRatioLabel)
            if (plan.hedges.isEmpty()) Text("ကာရန်မလိုပါ။")
            else {
                TextButton(onClick = { showList = !showList }) {
                    Text("ဂဏန်းနဲ့ ကာရမယ့်ငွေ ${plan.hedges.size} ကွက်")
                    Icon(if (showList) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                if (showList) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ဂဏန်း", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("မူလထိုးကြေး", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("ကာရန်", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                    HorizontalDivider()
                    plan.hedges.forEach { bet ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(bet.digit, style = MaterialTheme.typography.bodySmall)
                            Text(bet.originalStake.rm(), style = MaterialTheme.typography.bodySmall)
                            Text(bet.amount.rm(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(
                        enabled = allowCopy,
                        onClick = {
                            clipboard.setText(AnnotatedString(plan.hedges.joinToString("\n") { "${it.digit}=${it.amount}" }))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ကာစာရင်း Copy ယူရန်") }
                }
            }
            Text("တွက်ချက်ထားသော ကာနည်းနမူနာသာဖြစ်သည်။ ဒိုင်ဆီ အမှန်တကယ် မတင်ရသေးပါ။",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CustomerRiskReviewResults(
    vm: LedgerViewModel,
    agentId: Long,
    customerId: Long,
    date: LocalDate,
    session: DrawSession,
    subtitle: String,
    onReselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val winner by vm.winner(date, session).collectAsStateWithLifecycle(initialValue = null)
    val loaded by produceState<ReviewRiskData?>(null, agentId, customerId, date, session, revision) {
        value = null
        val source = if (customerId == -1L) vm.aggregateAnalysis(agentId, date, session)
                     else vm.analysis(customerId, date, session)
        val rate = vm.agentRateForDraw(agentId, date, session) ?: 0L
        val decision = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            CustomerRiskPlanner().calculate(source, rate)
        }
        val rates = vm.analysisSavedPayoutRates(agentId, customerId, date, session)
        val warnings = if (customerId == -1L) vm.customerLimitWarnings(agentId, date, session) else emptyList()
        value = ReviewRiskData(source, decision, rates, warnings)
    }
    var tab by rememberSaveable(agentId, customerId, date, session) { mutableStateOf("current") }
    var chosen by rememberSaveable(agentId, customerId, date, session) { mutableStateOf("balance") }
    var allRisk by rememberSaveable(agentId, customerId, date, session) { mutableStateOf(false) }
    var breakdown by rememberSaveable(agentId, customerId, date, session) { mutableStateOf(false) }
    var warningsOpen by rememberSaveable(agentId, customerId, date, session) { mutableStateOf(false) }
    var budgetText by rememberSaveable(agentId, customerId, date, session) { mutableStateOf("") }
    var budgetAmount by remember(agentId, customerId, date, session) { mutableStateOf<Long?>(null) }
    var budgetError by remember(agentId, customerId, date, session) { mutableStateOf("") }
    val budgetLoad by produceState<BudgetLoad?>(
        null, agentId, customerId, date, session, loaded, budgetAmount
    ) {
        value = null
        val data = loaded
        val requested = budgetAmount
        if (requested != null && data != null) {
            val plan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                CustomerRiskPlanner().planForLossBudget(data.source, data.decision.hedgeRate, requested)
            }
            value = BudgetLoad(requested, plan)
        }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subtitle, fontWeight = FontWeight.SemiBold)
                    Text("${date.reviewDate()} • ${session.reviewLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onReselect) { Text("ပြန်ရွေးရန်") }
            }
        }
        val data = loaded
        if (data == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
            val base = data.decision.baseline
            val source = data.source
            val decision = data.decision
            if (base.totalStake <= 0) {
                item { Text("ရွေးထားတဲ့အကြိမ်မှာ ထိုးစာရင်းမရှိပါ။") }
            } else {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            RiskValue("စုစုပေါင်းထိုးကြေး", "${base.totalStake.rm()} ကျပ်", emphasize = true)
                            RiskValue("ထိုးထားတဲ့ဂဏန်း", "${base.coveredDigits}/100 ကွက်")
                            RiskValue("လျော်ကြေးအဆ", "${decision.hedgeRate} ဆ")
                            if (data.rates.isNotEmpty() && data.rates.any { it != decision.hedgeRate }) {
                                Text("မူလစာရင်းလျော်ကြေးအဆ ${data.rates.joinToString("/") { "$it ဆ" }} • မူလငွေတွက်ချက်မှုမှာ သိမ်းထားတဲ့အဆကို သုံးထားသည်။",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            winner?.let {
                                val actual = source.scenarios.firstOrNull { row -> row.digit == it.digit }?.profitLoss
                                RiskValue("တကယ်ထွက်သောဂဏန်း", it.digit)
                                if (actual != null) RiskValue("မကာဘဲ အနိုင်/အရှုံး", "${signed(actual)} ကျပ်",
                                    emphasize = true, tint = if (actual < 0L) MaterialTheme.colorScheme.error else Color(0xFF18794E))
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("current" to "လက်ရှိ", "balance" to "50/50 မျှခြေ", "guarantee" to "အမြတ်သေချာ").forEach { (id,title) ->
                            FilterChip(tab == id, onClick = { tab = id }, label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
                when (tab) {
                    "current" -> {
                        item { RiskMoneyRange(base.bestProfit, base.worstLoss) }
                        item {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RiskValue("အန္တရာယ်ရှိသောဂဏန်း", "${base.losingDigits} ကွက်",
                                        MaterialTheme.colorScheme.error, true)
                                    TextButton(onClick = { breakdown = !breakdown }) {
                                        Text(if (breakdown) "ဂဏန်းအရေအတွက် အသေးစိတ်ပိတ်ရန်" else "ဂဏန်းအရေအတွက် ဘယ်လိုရလဲ")
                                        Icon(if (breakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                    }
                                    if (breakdown) {
                                        val uncovered = 100 - base.coveredDigits
                                        val coveredWin = source.scenarios.count { it.stake > 0L && it.profitLoss > 0L }
                                        val coveredLoss = source.scenarios.count { it.stake > 0L && it.profitLoss < 0L }
                                        RiskValue("မထိုးထားတဲ့ဂဏန်း", "${uncovered} ကွက်")
                                        RiskValue("ထိုးထားပြီး မြတ်နိုင်", "${coveredWin} ကွက်")
                                        RiskValue("ထိုးထားပြီး ရှုံးနိုင်", "${coveredLoss} ကွက်")
                                        RiskValue("အရင်းကျေ", "${base.breakEvenDigits} ကွက်")
                                    }
                                    HorizontalDivider()
                                    RiskDigitTable(source, allRisk, { allRisk = !allRisk })
                                }
                            }
                        }
                        item {
                            if (decision.missingHedgeRate) Text("ဒိုင်လျော်ကြေးအဆ မရသေးသဖြင့် ကာနည်းမတွက်နိုင်ပါ။",
                                color = MaterialTheme.colorScheme.error)
                            else if (decision.alreadyGuaranteed) Text("ဘယ်ဂဏန်းပေါက်ပေါက် အမြတ်ကျန်နိုင်ပြီးသားဖြစ်သည်။")
                            else Button(onClick = { tab = "balance" }, Modifier.fillMaxWidth()) {
                                Text("ကာနည်းနဲ့ အရှုံးလျှော့ရန်")
                            }
                        }
                    }
                    "balance" -> {
                        item {
                            Text("50/50 ဆိုတာ မြတ်/ရှုံးနိုင်တဲ့ ငွေပမာဏ မျှခြေကို ရည်ညွှန်းတယ်။ ပေါက်နိုင်ခြေ 50% မဟုတ်ဘူး။",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (decision.alreadyGuaranteed) {
                            item { Text("ကာရန်မလိုပါ။ မူလစာရင်းမှာပဲ အရှုံးမရှိပြီးသားဖြစ်သည်။") }
                        } else if (decision.missingHedgeRate) {
                            item { Text("ဒိုင်လျော်ကြေးအဆ မရှိသဖြင့် ကာနည်းမတွက်နိုင်ပါ။") }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        decision.plans.forEach { p ->
                                            FilterChip(chosen == p.id, onClick = { chosen = p.id }, label = {
                                                Text(p.title, style = MaterialTheme.typography.labelSmall)
                                            })
                                        }
                                    }
                                    FilterChip(chosen == "budget", onClick = { chosen = "budget" },
                                        label = { Text("စိတ်ကြိုက်အရှုံးကန့်သတ်") })
                                    if (chosen == "budget") {
                                        OutlinedTextField(
                                            budgetText, onValueChange = {
                                                budgetText = it.filter { c -> c.isDigit() }
                                                budgetAmount = null; budgetError = ""
                                            },
                                            label = { Text("အများဆုံးရှုံးခံနိုင်ငွေ (ကျပ်)") },
                                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                                        )
                                        Button(onClick = {
                                            budgetAmount = budgetText.toLongOrNull()
                                            budgetError = if (budgetAmount == null) "ငွေပမာဏ မှန်မှန်ထည့်ပါ" else ""
                                        }) { Text("ကာနည်းတွက်မည်") }
                                        if (budgetError.isNotEmpty()) Text(budgetError, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            val matchingBudget = budgetLoad?.takeIf { it.budget == budgetAmount }
                            val chosenPlan = if (chosen == "budget") matchingBudget?.plan else
                                decision.plans.firstOrNull { it.id == chosen } ?: decision.plans.firstOrNull()
                            if (chosen == "budget" && budgetAmount != null && matchingBudget == null) {
                                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            } else if (chosen == "budget" && matchingBudget != null && matchingBudget.plan == null) {
                                item { Text("သတ်မှတ်ထားသော အရှုံးကန့်သတ်အတွင်း ကာနည်းမရှိပါ။",
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (chosenPlan != null) item { RiskPlanCard(chosenPlan, base, winner == null) }
                        }
                    }
                    else -> {
                        if (decision.alreadyGuaranteed) {
                            item { Text("ကာရန်မလိုပါ။ အနည်းဆုံး ${base.worstProfit.rm()} ကျပ် မြတ်နိုင်ပြီးသား ဖြစ်သည်။") }
                        } else if (decision.missingHedgeRate) {
                            item { Text("ဒိုင်လျော်ကြေးအဆ မရှိသဖြင့် အမြတ်သေချာကာနည်း မတွက်နိုင်ပါ။") }
                        } else if (decision.guaranteedPlan == null) {
                            item { Text("ဒီစာရင်းနဲ့ အဆအတွက် ဘယ်ဂဏန်းပေါက်ပေါက် အမြတ်ကျန်စေမယ့် ကာနည်း မရှိပါ။") }
                        } else {
                            item { RiskValue("အနည်းဆုံးသေချာမြတ်ငွေ", "${decision.guaranteedPlan.worstProfit.rm()} ကျပ်",
                                Color(0xFF18794E), true) }
                            item { RiskPlanCard(decision.guaranteedPlan, base, winner == null) }
                        }
                    }
                }
                if (customerId == -1L && data.warnings.isNotEmpty()) item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { warningsOpen = !warningsOpen }) {
                                Text("ထိုးသား Limit သတိပေးချက် ${data.warnings.size} ယောက်")
                                Icon(if (warningsOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            if (warningsOpen) data.warnings.forEach { warning ->
                                Text("${warning.name} • 80%+: ${warning.warningDigits.size}၊ 90%+: ${warning.nearDigits.size}၊ 100%+: ${warning.fullDigits.size}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
