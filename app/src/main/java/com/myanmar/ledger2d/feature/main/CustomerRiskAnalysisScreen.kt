@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.AnalysisResult
import com.myanmar.ledger2d.core.domain.CustomerRiskDecision
import com.myanmar.ledger2d.core.domain.CustomerRiskPlanner
import com.myanmar.ledger2d.core.domain.RiskHedgePlan
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale

private fun Long.riskMoney(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun signedRiskMoney(value: Long): String =
    (if (value > 0L) "+" else if (value < 0L) "−" else "") + kotlin.math.abs(value).riskMoney()

private data class RiskUiLoaded(val source: AnalysisResult, val decision: CustomerRiskDecision)

@Composable
internal fun CustomerRiskAnalysisWorkspace(
    vm: LedgerViewModel,
    agentId: Long,
    customerId: Long,
    date: LocalDate,
    session: DrawSession,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val loaded by produceState<RiskUiLoaded?>(null, agentId, customerId, date, session, revision) {
        value = null
        val result = if (customerId == -1L) vm.aggregateAnalysis(agentId, date, session)
                     else vm.analysis(customerId, date, session)
        val rate = vm.agentRateForDraw(agentId, date, session) ?: 0L
        value = RiskUiLoaded(result, CustomerRiskPlanner().calculate(result, rate))
    }
    val winner by vm.winner(date, session).collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable(agentId, customerId, date.toString(), session.name) { mutableStateOf("current") }
    var planId by rememberSaveable(agentId, customerId, date.toString(), session.name) { mutableStateOf("balance") }
    var details by rememberSaveable(agentId, customerId, date.toString(), session.name) { mutableStateOf(false) }
    var budgetText by rememberSaveable(agentId, customerId, date.toString(), session.name) { mutableStateOf("") }
    var budgetError by remember(agentId, customerId, date, session) { mutableStateOf("") }
    var custom by remember(agentId, customerId, date, session, revision) { mutableStateOf<RiskHedgePlan?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (customerId == -1L) "Customer အားလုံး၏ Analysis" else "Customer Analysis",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${date} • ${session.label} • ကော်မရှင်မပါ",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val data = loaded
        if (data == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            val source = data.source
            val decision = data.decision
            val base = decision.baseline
            if (decision.missingHedgeRate) {
                Text("ဒီ Session ရဲ့ ဒိုင်လျော်ကြေးအဆ မရသေးလို့ အရှုံး/အမြတ် Analysis ကို အတည်မပြုနိုင်ပါ။",
                    color = MaterialTheme.colorScheme.error)
            } else if (base.totalStake <= 0L) {
                Text("ဒီ Session မှာ ထိုးစာရင်းမရှိသေးပါ။", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("စုစုပေါင်းထိုးကြေး", style = MaterialTheme.typography.labelSmall)
                            Text("${base.totalStake.riskMoney()} ကျပ်",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ထိုးကွက်", style = MaterialTheme.typography.labelSmall)
                            Text("${base.coveredDigits}/100",
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (winner != null) {
                    val actual = source.scenarios.firstOrNull { it.digit == winner?.digit }?.profitLoss
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("တကယ်ထွက်သောဂဏန်း: ${winner?.digit}", fontWeight = FontWeight.Bold)
                            if (actual != null) {
                                Text("မကာထားဟု ယူဆသည့် စာရင်းအရ ${if (actual < 0L) "ရှုံး" else if (actual == 0L) "အရင်းကျေ" else "မြတ်"}ငွေ",
                                    style = MaterialTheme.typography.labelSmall)
                                Text("${signedRiskMoney(actual)} ကျပ်",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (actual < 0L) MaterialTheme.colorScheme.error else AppColors.Success)
                            }
                            Text("အောက်ပါ Analysis သုံးပိုင်းသည် အရင်စာရင်း၏ ဖြစ်နိုင်ခြေကို နှိုင်းယှဉ်ပြသော နမူနာများသာ ဖြစ်သည်။",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    RiskTab("လက်ရှိ", tab == "current", Modifier.weight(1f)) { tab = "current"; details = false }
                    RiskTab("50/50 မျှခြေ", tab == "balance", Modifier.weight(1f)) { tab = "balance"; details = false }
                    RiskTab("အမြတ်သေချာ", tab == "guarantee", Modifier.weight(1f)) { tab = "guarantee"; details = false }
                }
                // After a saved winning number, the actual result above takes priority.
                when (tab) {
                    "current" -> {
                        RiskMoneyPair(base.bestProfit, base.worstLoss)
                        val warning = when {
                            base.worstLoss == 0L -> "အရှုံးဖြစ်မယ့်ဂဏန်း မရှိပါ"
                            base.worstLoss > base.totalStake -> "ရောင်းရငွေထက် ပိုရှုံးနိုင်သည်"
                            else -> "အရှုံးဖြစ်နိုင်သော ဂဏန်းများရှိသည်"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(if (base.worstLoss == 0L) Icons.Default.CheckCircle else Icons.Default.Warning,
                                null, tint = if (base.worstLoss == 0L) AppColors.Success else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                            Text(warning, fontWeight = FontWeight.SemiBold)
                        }
                        RiskOutcomeCounts(base.profitableDigits, base.losingDigits, base.breakEvenDigits)
                        Surface(shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(19.dp))
                                    Text("လုပ်ဆောင်ရန် အကြံပြုချက်", fontWeight = FontWeight.Bold)
                                }
                                if (decision.alreadyGuaranteed) {
                                    Text("ဘယ်ဂဏန်းပေါက်ပေါက် အမြတ်ကျန်ပြီးသားဖြစ်လို့ ထပ်မကာလည်းရသည်။")
                                } else {
                                    val riskReducing = decision.plans.any { it.upstreamTotal > 0L && it.worstLoss < base.worstLoss }
                                    Text("အန္တရာယ်အများဆုံးဂဏန်း: ${base.riskDigits.take(10).joinToString(", ")}။ " +
                                        if (riskReducing) "ကာနည်းရွေးပြီး အများဆုံးရှုံးငွေ လျှော့နိုင်သည်။"
                                        else "ဒီလျော်ကြေးအဆဖြင့် အရှုံးလျော့မည့် ကာနည်းမတွေ့ပါ။")
                                    val suggested = decision.plans.firstOrNull { it.id == "balance" }
                                    if (suggested != null && suggested.upstreamTotal > 0L && suggested.worstLoss < base.worstLoss) {
                                        Text("မျှခြေနီးစပ်ရန်: ${suggested.upstreamTotal.riskMoney()} ကျပ် ကာလျှင် " +
                                            "အများဆုံး ${signedRiskMoney(suggested.bestProfit)} / −${suggested.worstLoss.riskMoney()} ကျပ်",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (!decision.alreadyGuaranteed && winner == null) {
                            Button(onClick = { tab = "balance"; details = false }, Modifier.fillMaxWidth()) {
                                Text("ကာနည်းတွေ နှိုင်းယှဉ်ကြည့်ရန်")
                                Spacer(Modifier.width(7.dp))
                                Icon(Icons.Default.ArrowForward, null, Modifier.size(17.dp))
                            }
                        }
                        TextButton(onClick = { details = !details }) {
                            Text(if (details) "အချက်အလက်အသေးစိတ် ပိတ်ရန်" else "ဂဏန်းနှင့် Limit အသေးစိတ်")
                            Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                        if (details) {
                            RiskDetailRow("အများဆုံးလျော်ကြေး", source.worstCasePayout.riskMoney())
                            RiskDetailRow("ထိုးကြေးအများဆုံးဂဏန်း", source.highest.joinToString(" • ") {
                                "${it.first}=${it.second.riskMoney()}"
                            }.ifBlank { "မရှိ" })
                            RiskDetailRow("ပိတ်ဂဏန်း", source.closedDigits.sorted().joinToString(", ").ifBlank { "မရှိ" })
                            RiskDetailRow("Limit 80% နှင့်အထက်", "${source.warningDigits} ကွက်")
                            RiskDetailRow("Limit 90% နှင့်အထက်", "${source.nearDigits} ကွက်")
                            RiskDetailRow("Limit 100% နှင့်အထက်", "${source.fullDigits} ကွက်")
                            RiskDetailRow("ထပ်မလက်ခံသင့်သောဂဏန်း", source.rejectDigits.sorted().joinToString(", ").ifBlank { "မရှိ" })
                        }
                    }
                    "balance" -> {
                        if (decision.missingHedgeRate) {
                            Text("ဒီ Session ရဲ့ ဒိုင်လျော်ကြေးအဆ မရသေးသဖြင့် ကာနည်းမတွက်နိုင်ပါ။")
                        } else if (decision.alreadyGuaranteed) {
                            RiskGuaranteedAlready(base.worstProfit)
                        } else {
                            Text("50/50 ဆိုသည်မှာ မြတ်/ရှုံးနိုင်သည့် ငွေပမာဏမျှခြေဖြစ်ပြီး ပေါက်နိုင်ခြေ 50% မဟုတ်ပါ။",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                decision.plans.forEach { plan ->
                                    FilterChip(planId == plan.id, onClick = { planId = plan.id; details = false },
                                        label = { Text(plan.title) })
                                }
                                FilterChip(planId == "budget", onClick = { planId = "budget"; details = false },
                                    label = { Text("စိတ်ကြိုက်အရှုံးကန့်သတ်") })
                            }
                            if (planId == "budget") {
                                OutlinedTextField(
                                    value = budgetText,
                                    onValueChange = { budgetText = it.filter { c -> c.isDigit() }; budgetError = "" },
                                    label = { Text("အများဆုံး ရှုံးခံနိုင်ငွေ (ကျပ်)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    val budget = budgetText.toLongOrNull()
                                    if (budget == null) {
                                        budgetError = "ကန့်သတ်ငွေကို မှန်မှန်ထည့်ပါ"
                                        custom = null
                                    } else {
                                        custom = CustomerRiskPlanner().planForLossBudget(source, decision.hedgeRate, budget)
                                        budgetError = if (custom == null) "ဒီလျော်ကြေးစာရင်းအတွက် စိတ်ကြိုက် Plan မတွက်နိုင်ပါ" else ""
                                    }
                                }) { Text("အဲဒီငွေအတွင်း ကာနည်းတွက်ရန်") }
                                if (budgetError.isNotBlank()) Text(budgetError, color = MaterialTheme.colorScheme.error)
                            }
                            val picked = if (planId == "budget") custom
                                         else decision.plans.firstOrNull { it.id == planId } ?: decision.plans.firstOrNull()
                            if (picked != null) {
                                RiskPlanSummary(base.bestProfit, base.worstLoss, base.profitableDigits, picked)
                                TextButton(onClick = { details = !details }) {
                                    Text(if (details) "ကာစာရင်းပိတ်ရန်" else "ဘယ်ဂဏန်းကို ဘယ်လောက်ကာရမလဲ")
                                    Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                                if (details) RiskHedgeDetails(picked, allowCopy = winner == null)
                            }
                        }
                    }
                    else -> {
                        if (decision.alreadyGuaranteed) {
                            RiskGuaranteedAlready(base.worstProfit)
                        } else if (decision.missingHedgeRate) {
                            Text("ဒီ Session ရဲ့ ဒိုင်လျော်ကြေးအဆ မရသေးလို့ အမြတ်သေချာ Plan ကို မတွက်နိုင်သေးပါ။",
                                style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val guaranteed = decision.guaranteedPlan
                            if (guaranteed == null) {
                                Surface(shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                                            Text("အမြတ်သေချာကျန်အောင် မကာနိုင်ပါ", fontWeight = FontWeight.Bold)
                                        }
                                        Text("မူလထိုးထားသောဂဏန်းတွေကိုပဲ ပြန်ကာပြီး " +
                                            "ဘယ်ဂဏန်းပေါက်ပေါက် အမြတ်ကျန်အောင်လုပ်မရပါ။ " +
                                            "စာရင်းအားလုံးကို ပြန်ကာလိုက်ခြင်းကလည်း အမြတ်သေချာမကျန်စေပါ။",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("ထိုးထားသော ${base.coveredDigits} ကွက် • ဒိုင်လျော်ကြေး ${decision.hedgeRate} ဆ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                OutlinedButton(onClick = { tab = "balance" }, modifier = Modifier.fillMaxWidth()) {
                                    Text("အစားထိုး 50/50 ကာနည်းကြည့်ရန်")
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Icon(Icons.Default.Verified, null, tint = AppColors.Success)
                                    Text("ဂဏန်း 100 လုံးမှာ အမြတ်ကျန်နိုင်သည့် ကာနည်းရှိသည်",
                                        fontWeight = FontWeight.Bold)
                                }
                                RiskMoneyPair(guaranteed.bestProfit, guaranteed.worstLoss,
                                    "အများဆုံးမြတ်ငွေ", "အများဆုံးရှုံးငွေ")
                                RiskDetailRow("အနည်းဆုံးသေချာမြတ်ငွေ", "+${guaranteed.worstProfit.riskMoney()} ကျပ်")
                                RiskDetailRow("ဒိုင်ဆီကာရန်", "${guaranteed.upstreamTotal.riskMoney()} ကျပ်")
                                RiskDetailRow("ကိုယ်တိုင်ကျန်ငွေ", "${guaranteed.keptCash.riskMoney()} ကျပ်")
                                Text("ဒီ Plan သည် ဂဏန်း 100 လုံး အမြတ်ကျန်နိုင်သည့်နည်းများထဲမှ ကာကြေးအနည်းဆုံးနည်းဖြစ်သည်။",
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { details = !details }) {
                                    Text(if (details) "ကာစာရင်းပိတ်ရန်" else "အမြတ်သေချာကာစာရင်းကြည့်ရန်")
                                    Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                                if (details) RiskHedgeDetails(guaranteed, allowCopy = winner == null)
                            }
                        }
                    }
                }
                if (tab != "current" && !decision.alreadyGuaranteed) {
                    Text("ကာနည်းများသည် တွက်ချက်မှု Preview သာဖြစ်သည်။ ဒိုင်လက်ခံမည့် အနည်းဆုံးကာကြေး၊ ပိတ်ဂဏန်းနှင့် Limit ကို စစ်ပြီးမှ တကယ်တင်ပါ။",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun RiskTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.padding(horizontal = 5.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable private fun RiskMoneyPair(
    bestProfit: Long, worstLoss: Long,
    bestLabel: String = "အများဆုံးမြတ်ငွေ", lossLabel: String = "အများဆုံးရှုံးငွေ",
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RiskMoneyBox(bestLabel, signedRiskMoney(bestProfit), AppColors.Success, Modifier.weight(1f))
        RiskMoneyBox(lossLabel, if (worstLoss == 0L) "0" else "−${worstLoss.riskMoney()}",
            MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable private fun RiskMoneyBox(label: String, amount: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black, color = tint, maxLines = 1)
            Text("ကျပ်", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun RiskOutcomeCounts(profit: Int, loss: Int, equal: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("မြတ်မယ့်ဂဏန်း ${profit} ကွက် • ရှုံးမယ့်ဂဏန်း ${loss} ကွက်" +
            if (equal > 0) " • အရင်းကျေ ${equal} ကွက်" else "",
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp))) {
            if (profit > 0) Box(Modifier.weight(profit.toFloat()).fillMaxHeight().background(AppColors.Success))
            if (equal > 0) Box(Modifier.weight(equal.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
            if (loss > 0) Box(Modifier.weight(loss.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.error))
        }
        Text("00–99 ဂဏန်းအားလုံး ပေါက်နိုင်ခြေတူသည်ဟု ယူဆထားသည်။",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun RiskPlanSummary(
    oldProfit: Long, oldLoss: Long, oldWinning: Int, plan: RiskHedgePlan,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        RiskMoneyPair(plan.bestProfit, plan.worstLoss)
        RiskOutcomeCounts(plan.profitableDigits, plan.losingDigits, plan.breakEvenDigits)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RiskDetailRow("ဒိုင်ဆီကာရန်", "${plan.upstreamTotal.riskMoney()} ကျပ်")
                RiskDetailRow("ကိုယ်တိုင်ကျန်ငွေ", "${plan.keptCash.riskMoney()} ကျပ်")
                RiskDetailRow("ငွေကြေးမျှခြေ", plan.balanceRatioLabel)
            }
        }
        Text("မကာခင် ${signedRiskMoney(oldProfit)} / −${oldLoss.riskMoney()} → " +
            "ကာပြီး ${signedRiskMoney(plan.bestProfit)} / ${if (plan.worstLoss == 0L) "0" else "−${plan.worstLoss.riskMoney()}"} ကျပ်",
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        val reduction = oldLoss - plan.worstLoss
        val lossChange = when {
            reduction > 0L -> "အများဆုံးရှုံးငွေ ${reduction.riskMoney()} ကျပ် လျော့မည်။"
            reduction < 0L -> "အများဆုံးရှုံးငွေ ${(-reduction).riskMoney()} ကျပ် ပိုတိုးမည်။"
            else -> "အများဆုံးရှုံးငွေ မပြောင်းပါ။"
        }
        Text(lossChange + " အမြတ်ဖြစ်မယ့်ဂဏန်း ${oldWinning} → ${plan.profitableDigits} ကွက်။",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun RiskGuaranteedAlready(minimumProfit: Long) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Verified, null, tint = AppColors.Success)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ဘယ်ဂဏန်းပေါက်ပေါက် အမြတ်ကျန်ပြီးသား",
                    fontWeight = FontWeight.Bold)
                Text("အနည်းဆုံး +${minimumProfit.riskMoney()} ကျပ်မြတ်မည်။ " +
                    "မလိုအပ်ဘဲ ဒိုင်ဆီထပ်ကာရန်မလိုပါ။")
            }
        }
    }
}

@Composable private fun RiskDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.15f), style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable private fun RiskHedgeDetails(plan: RiskHedgePlan, allowCopy: Boolean) {
    val clipboard = LocalClipboardManager.current
    val copyText = plan.hedges.joinToString("\n") { "${it.digit}=${it.amount}" }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("ပြန်ကာရမယ့်ဂဏန်း (${plan.hedges.size} ကွက်)",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (plan.hedges.isEmpty()) Text("ကာရန်မလိုပါ")
            else {
                plan.hedges.forEach { hedge ->
                    RiskDetailRow(hedge.digit, "${hedge.amount.riskMoney()} ကျပ်")
                }
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(copyText)) },
                    enabled = allowCopy,
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("ကာစာရင်း Copy ယူရန်")
                }
            }
            Text(if (allowCopy) "Copy ယူထားသည့်စာရင်းသည် ဒိုင်ဆီ တင်ပြီးသားစာရင်း မဟုတ်ပါ။"
                 else "ပေါက်ဂဏန်းထွက်ပြီးပြီ။ ဤကာစာရင်းသည် အတိတ်ကာနည်း နမူနာသာဖြစ်၍ တကယ်ကာရန် မဟုတ်ပါ။",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
