@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.database.ClosedNumberEntity
import com.myanmar.ledger2d.core.database.AgentSpecialLimitEntity
import com.myanmar.ledger2d.core.domain.ClosedDigitSelection
import com.myanmar.ledger2d.core.domain.ClosedDigitSelector
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

@Composable
private fun RulePatternPicker(
    format: QuickFormat, changeFormat: (QuickFormat) -> Unit,
    source: String, changeSource: (String) -> Unit,
    selection: ClosedDigitSelection,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အမြန်ရွေးချယ်ပုံ"), fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            QuickFormat.entries.forEach { option ->
                FilterChip(
                    selected = format == option,
                    onClick = { changeFormat(option); changeSource("") },
                    label = { Text(option.label) },
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
        }
        if (ClosedDigitSelector.requiresInput(format)) {
            val hint = when (format) {
                QuickFormat.MANUAL -> "ဥပမာ 20.39.45 (သို့) 20.39R"
                QuickFormat.HEAD, QuickFormat.TAIL, QuickFormat.ROUND -> "ဥပမာ 9"
                else -> "ဥပမာ 345"
            }
            OutlinedTextField(
                source, changeSource,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဂဏန်းရင်းမြစ်")) },
                placeholder = { Text(hint) },
                maxLines = 3,
            )
        }
        when (selection) {
            is ClosedDigitSelection.Invalid ->
                Text(selection.message, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            is ClosedDigitSelection.Valid -> {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("Preview • ${selection.digits.size} ကွက်", "Preview • ${selection.digits.size} digits"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    selection.digits.forEach { digit ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(digit, Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentClosedPatternWorkspace(
    vm: LedgerViewModel, agentId: Long, date: LocalDate, session: DrawSession,
) {
    key(agentId, date, session) {
        val current by vm.closedNumbers(agentId,date,session)
            .collectAsStateWithLifecycle(initialValue=emptyList())
        val agent by vm.agent(agentId).collectAsStateWithLifecycle(initialValue=null)
        val agentName = agent?.name ?: "ဒိုင် #$agentId"
        var format by rememberSaveable { mutableStateOf(QuickFormat.MANUAL) }
        var source by rememberSaveable { mutableStateOf("") }
        val selection = remember(format,source) { ClosedDigitSelector.select(format,source) }
        val candidates = (selection as? ClosedDigitSelection.Valid)?.digits.orEmpty()
        val currentSet = current.map { it.digit }.toSet()
        val newlyClosed = candidates.filterNot { it in currentSet }
        val alreadyClosed = candidates.filter { it in currentSet }
        var pendingAction by remember { mutableStateOf<String?>(null) }
        var pendingOne by remember { mutableStateOf<ClosedNumberEntity?>(null) }
        var notice by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        if (pendingAction != null) {
            val closing = pendingAction == "close"
            val affected = if (closing) newlyClosed else alreadyClosed
            AlertDialog(
                onDismissRequest = { pendingAction=null },
                title = { Text(if (closing) com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အုပ်စုလိုက်ပိတ်ရန် အတည်ပြုပါ") else com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အုပ်စုလိုက်ပြန်ဖွင့်ရန် အတည်ပြုပါ")) },
                text = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("$agentName • $date • ${session.label} • ${affected.size} ကွက် ", "$agentName • $date • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)} • ${affected.size} digits ") +
                    if (closing) com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ပိတ်မည်။", "will be closed.") else com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ပြန်ဖွင့်မည်။", "will be reopened.")) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingAction=null
                        busy=true
                        vm.updateClosedDigitGroup(agentId,date,session,affected,closing,
                            onDone={ busy=false; notice="${it} ကွက် ဆောင်ရွက်ပြီး" },
                            onError={ busy=false; notice=it })
                    }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အတည်ပြုမည်")) }
                },
                dismissButton = { TextButton(onClick = { pendingAction=null }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("မလုပ်ပါ")) } },
            )
        }
        pendingOne?.let { row ->
            AlertDialog(
                onDismissRequest = { pendingOne=null },
                title = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပိတ်ဂဏန်းပြန်ဖွင့်ရန်")) },
                text = { Text("$date • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)} • ${row.digit}") },
                confirmButton = { TextButton(onClick={
                    vm.removeClosedNumber(row); pendingOne=null
                }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဖယ်ရှားမည်")) } },
                dismissButton = { TextButton(onClick={pendingOne=null}) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("မလုပ်ပါ")) } },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("$agentName • $date • ${session.label} အတွက်သာ", "$agentName • $date • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)} only"),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီ Session အတွက် ပိတ်ထားတာက နောက် Session နဲ့ တခြားဒိုင်ဆီ မကူးပါ။ ") +
                "လက်ခံပြီးသားစာရင်းကိုလည်း မဖျက်ပါ။",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            RulePatternPicker(format, {format=it}, source, {source=it}, selection)
            if (candidates.isNotEmpty()) {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("အသစ်ပိတ်မယ့် ${newlyClosed.size} ကွက် • ပိတ်ပြီးသား ${alreadyClosed.size} ကွက်", "New closures: ${newlyClosed.size} • Already closed: ${alreadyClosed.size}"),
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick={pendingAction="close"},enabled=!busy && newlyClosed.isNotEmpty()) {
                        Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အုပ်စုလိုက်ပိတ်မည်"))
                    }
                    OutlinedButton(onClick={pendingAction="open"},enabled=!busy && alreadyClosed.isNotEmpty()) {
                        Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရွေးထားတာ ပြန်ဖွင့်မည်"))
                    }
                }
            }
            if (notice.isNotEmpty()) Text(notice,
                color=MaterialTheme.colorScheme.primary,
                style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("လက်ရှိပိတ်ထားသောဂဏန်း (${current.size} ကွက်)", "Currently closed digits (${current.size})"),
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if (current.isEmpty()) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီ Session အတွက် ပိတ်ဂဏန်းမရှိသေးပါ"))
            else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    current.forEach { row ->
                        OutlinedButton(onClick={pendingOne=row},
                            contentPadding=PaddingValues(horizontal=9.dp,vertical=3.dp)) {
                            Text("${row.digit} ×")
                        }
                    }
                }
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဂဏန်းတစ်ကွက်ကို နှိပ်ပြီးလည်း ပြန်ဖွင့်နိုင်သည်"),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AgentLimitPatternWorkspace(
    vm: LedgerViewModel, agentId: Long, date: LocalDate, session: DrawSession,
) {
    key(agentId,date,session) {
        val all by vm.agentAllLimit(agentId,date,session)
            .collectAsStateWithLifecycle(initialValue=null)
        val specials by vm.agentSpecialLimits(agentId,date,session)
            .collectAsStateWithLifecycle(initialValue=emptyList())
        val agent by vm.agent(agentId).collectAsStateWithLifecycle(initialValue=null)
        val agentName = agent?.name ?: "ဒိုင် #$agentId"
        var allText by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(all?.id,all?.amount) { allText=all?.amount?.toString().orEmpty() }
        var format by rememberSaveable { mutableStateOf(QuickFormat.MANUAL) }
        var source by rememberSaveable { mutableStateOf("") }
        var amountText by rememberSaveable { mutableStateOf("") }
        val selection = remember(format,source) { ClosedDigitSelector.select(format,source) }
        val digits = (selection as? ClosedDigitSelection.Valid)?.digits.orEmpty()
        val amount = amountText.toLongOrNull()?.takeIf { it>0L }
        val limitMap = specials.associate { it.digit to it.amount }
        val needsUpdate = digits.count { limitMap[it] != amount }
        val alreadyCustom = digits.count { it in limitMap }
        val replacements = digits.count { it in limitMap && limitMap[it] != amount }
        val unchanged = digits.count { it in limitMap && limitMap[it] == amount }
        val removable = digits.filter { it in limitMap }
        var action by remember { mutableStateOf<String?>(null) }
        var pendingOne by remember { mutableStateOf<AgentSpecialLimitEntity?>(null) }
        var notice by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        if(action!=null) {
            val remove=action=="remove"
            val selected = if(remove) removable else digits
            AlertDialog(
                onDismissRequest={action=null},
                title={Text(if(remove) com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Limit အုပ်စုလိုက်ဖယ်ရှားမည်လား") else com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Limit အုပ်စုလိုက်သိမ်းမည်လား"))},
                text={ Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("$agentName • $date • ${session.label} • ${selected.size} ကွက် ", "$agentName • $date • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)} • ${selected.size} digits ") +
                    if(remove) com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အထူး Limit ကို ဖယ်ရှားမည်။") else
                        com.myanmar.ledger2d.core.design.LocalLanguage.current.text("တစ်ကွက်လျှင် ${amountText} ကျပ် သတ်မှတ်မည်။ ", "Set ${amountText} MMK per digit. ") +
                        com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ရှိပြီးသား Limit ${replacements} ကွက် အစားထိုးမည်။", "Replace ${replacements} existing limits.")) },
                confirmButton={ TextButton(onClick={
                    action=null
                    busy=true
                    vm.updateAgentLimitGroup(agentId,date,session,selected,amount,remove,
                        onDone={ busy=false; notice="${it} ကွက် ဆောင်ရွက်ပြီး" },
                        onError={ busy=false; notice=it })
                }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အတည်ပြုမည်")) } },
                dismissButton={ TextButton(onClick={action=null}) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("မလုပ်ပါ")) } },
            )
        }
        pendingOne?.let { row ->
            AlertDialog(onDismissRequest={pendingOne=null},
                title={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အထူး Limit ဖယ်ရှားရန်"))},
                text={Text("$date • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)} • ${row.digit} • ${row.amount.mmk()}")},
                confirmButton={TextButton(onClick={
                    vm.removeAgentSpecialLimit(row);pendingOne=null
                }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဖယ်ရှားမည်")) }},
                dismissButton={TextButton(onClick={pendingOne=null}) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("မလုပ်ပါ")) }})
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("$agentName • $date • ${session.label} အတွက်သာ", "$agentName • $date • ${com.myanmar.ledger2d.core.design.LocalLanguage.current.translate(session.label)} only"),
                style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အကွက်အားလုံးအတွက် Limit"),style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold)
            OutlinedTextField(allText,{ allText=it.filter(Char::isDigit) },
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                label={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အကွက်အားလုံးအတွက် Limit (အလွတ် = ဖယ်ရှား)"))})
            Button(
                enabled=allText.isBlank() || (allText.toLongOrNull()?.let { it>0L }==true),
                onClick={vm.updateAgentAllLimit(agentId,date,session,allText) {notice="အကွက်အားလုံး Limit သိမ်းပြီး"}},
            ) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အကွက်အားလုံး Limit သိမ်းမည်")) }
            HorizontalDivider()
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရွေးချယ်ထားသောဂဏန်းများအတွက် အထူး Limit"),
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            RulePatternPicker(format,{format=it},source,{source=it},selection)
            if (digits.isNotEmpty()) {
                OutlinedTextField(
                    amountText,{amountText=it.filter(Char::isDigit)},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,
                    label={Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("တစ်ကွက်လျှင် Limit ပမာဏ (ကျပ်)"))},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                )
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("ရွေးထားတာ ${digits.size} ကွက် • အထူး Limit ရှိပြီးသား ${alreadyCustom} ကွက်", "Selected ${digits.size} digits • Existing special limits ${alreadyCustom}") +
                    com.myanmar.ledger2d.core.design.LocalLanguage.current.text(" • အသစ် ${digits.size-alreadyCustom} • အစားထိုး ${replacements} • မပြောင်း ${unchanged}", " • New ${digits.size-alreadyCustom} • Replaced ${replacements} • Unchanged ${unchanged}"),
                    style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={action="set"},
                        enabled=!busy && amount!=null && needsUpdate>0) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Limit အုပ်စုလိုက်သိမ်းမည်")) }
                    OutlinedButton(onClick={action="remove"},
                        enabled=!busy && removable.isNotEmpty()) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရွေးထားတာ ဖယ်ရှားမည်")) }
                }
            }
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အထူး Limit က အကွက်အားလုံး Limit အပေါ် ဦးစားပေးသက်ရောက်သည်။ ") +
                "Limit ကျော်သွားသော အသစ်သွင်းစာရင်းကို တစ်စိတ်တစ်ပိုင်း လက်မခံပါ။",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(notice.isNotEmpty()) Text(notice,
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text("သတ်မှတ်ထားသော အထူး Limit (${specials.size} ကွက်)", "Configured special limits (${specials.size})"),
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(specials.isEmpty()) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အထူး Limit မရှိသေးပါ"))
            else {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    specials.forEach { row ->
                        OutlinedButton(onClick={pendingOne=row},
                            contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)) {
                            Text("${row.digit}: ${row.amount.mmk()} ×")
                        }
                    }
                }
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အထူး Limit တစ်ကွက်ကို နှိပ်ပြီး ဖယ်ရှားနိုင်သည်"),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ClosedPatternStandaloneScreen(vm: LedgerViewModel, agentId: Long, onBack: ()->Unit) {
    val language=LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    val date=runCatching{LocalDate.parse(dateText)}.getOrNull()
    AppScaffold(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပိတ်ဂဏန်း"),onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(14.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { DateInput(dateText,{dateText=it;language.setDate(it)},"ရက်စွဲ") }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DrawSession.entries.forEach { draw ->
                    FilterChip(session==draw,{session=draw;language.setSession(draw)},
                        label={Text(draw.label)})
                }
            } }
            item {
                if(date==null) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရက်စွဲ မှန်ကန်စွာရွေးပါ"),color=MaterialTheme.colorScheme.error)
                else AgentClosedPatternWorkspace(vm,agentId,date,session)
            }
        }
    }
}

@Composable
fun LimitPatternStandaloneScreen(vm: LedgerViewModel,agentId:Long,onBack:()->Unit) {
    val language=LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(language.selectedDate) }
    var session by rememberSaveable { mutableStateOf(language.selectedSession) }
    val date=runCatching{LocalDate.parse(dateText)}.getOrNull()
    AppScaffold(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒိုင်အလိုက် ကန့်သတ်ပမာဏ"),onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding=PaddingValues(14.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { DateInput(dateText,{dateText=it;language.setDate(it)},"ရက်စွဲ") }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DrawSession.entries.forEach { draw ->
                    FilterChip(session==draw,{session=draw;language.setSession(draw)},
                        label={Text(draw.label)})
                }
            } }
            item {
                if(date==null) Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ရက်စွဲ မှန်ကန်စွာရွေးပါ"),color=MaterialTheme.colorScheme.error)
                else AgentLimitPatternWorkspace(vm,agentId,date,session)
            }
        }
    }
}
