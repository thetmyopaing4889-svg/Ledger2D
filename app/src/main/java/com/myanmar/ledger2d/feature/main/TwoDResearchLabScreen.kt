@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Research has a distinct, unobtrusive visual identity. The host owns this
 * state so switching to Analysis/Summary and back does not erase a study.
 * It is deliberately session-scoped: saved-query persistence is a later feature.
 */
internal class ResearchLabSessionState(date: LocalDate) {
    var relationship by mutableStateOf("same")
    var scope by mutableStateOf("all")
    var sourceKind by mutableStateOf(TwoDResearchCriterionKind.PAIR)
    var sourceValue by mutableStateOf("")
    var compare by mutableStateOf(false)
    var targetKind by mutableStateOf(TwoDResearchCriterionKind.PAIR)
    var targetValue by mutableStateOf("")
    var period by mutableStateOf("all")
    var start by mutableStateOf(date.minusMonths(1).toString())
    var end by mutableStateOf(date.toString())
    var submitted by mutableStateOf<TwoDResearchQuery?>(null)
    var resultVisible by mutableStateOf(false)
    // Guide is an optional destination; closing it restores the prior editor/result.
    var guideVisible by mutableStateOf(false)
    // Read-only data-integrity view; never changes the submitted study.
    var auditVisible by mutableStateOf(false)
    var evidenceFilter by mutableStateOf("all")
    var evidenceLimit by mutableIntStateOf(25)
    var frequencyExpanded by mutableStateOf(false)
    // When a target exists, show matching outcomes first; preserve all-outcome
    // discovery as a separate, explicitly labeled tab.
    var frequencyMode by mutableStateOf("matched")
    var compactEvidence by mutableStateOf(false)
}

/** Equal-frequency results are more legible as pair tiles than identical bars. */
internal fun useCompactFrequencyGrid(frequencies: List<TwoDCount>): Boolean =
    frequencies.size > 1 && frequencies.all { it.count == frequencies.first().count }


/**
 * One-tap templates execute a real query; they never populate fake results.
 * Preserve the editable fields so Back from the evidence view is intuitive.
 */
internal fun ResearchLabSessionState.openPreset(example: ResearchGuideExample) {
    val query = example.toQuery()
    relationship = if (query.relationship == TwoDResearchRelationship.SAME_DAY_PM)
        "same" else "next"
    scope = query.scope.name.lowercase(Locale.US)
    sourceKind = example.source.kind
    sourceValue = example.source.value
    compare = example.target != null
    targetKind = example.target?.kind ?: TwoDResearchCriterionKind.PAIR
    targetValue = example.target?.value.orEmpty()
    period = "all"
    submitted = query
    evidenceFilter = "all"
    evidenceLimit = 25
    frequencyExpanded = false
    frequencyMode = "matched"
    guideVisible = false
    resultVisible = true
}

internal data class ResearchLabPalette(
    val background: Color,
    val card: Color,
    val mutedSurface: Color,
    val ink: Color,
    val muted: Color,
    val outline: Color,
    val accent: Color,
    val accentWash: Color,
    val hero: Color,
    val heroLight: Color,
    val heroText: Color,
)

@Composable
internal fun researchLabPalette(): ResearchLabPalette {
    // Respect LedgerTheme's explicit in-app Light/Dark setting, which may
    // deliberately differ from Android's system appearance.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) ResearchLabPalette(
        background = Color(0xFF0D1922),
        card = Color(0xFF152733),
        mutedSurface = Color(0xFF1D3641),
        ink = Color(0xFFF0F7F8),
        muted = Color(0xFFAFC2C7),
        outline = Color(0xFF36515C),
        accent = Color(0xFF67E0CC),
        accentWash = Color(0xFF204940),
        hero = Color(0xFF102D38),
        heroLight = Color(0xFF22525B),
        heroText = Color(0xFFF5FFFF),
    ) else ResearchLabPalette(
        background = Color(0xFFF0F5F5),
        card = Color(0xFFFFFFFF),
        mutedSurface = Color(0xFFF2F7F7),
        ink = Color(0xFF17303C),
        muted = Color(0xFF58717A),
        outline = Color(0xFFD8E5E7),
        accent = Color(0xFF087F76),
        accentWash = Color(0xFFE0F5F0),
        hero = Color(0xFF112D3A),
        heroLight = Color(0xFF28616B),
        heroText = Color(0xFFF5FFFF),
    )
}

private fun lab(en: Boolean, mm: String, english: String) =
    if (en) english else mm

@Composable
private fun LabText(
    text: String, en: Boolean, palette: ResearchLabPalette,
    role: LedgerTextRole = LedgerTextRole.BODY,
    modifier: Modifier = Modifier, color: Color = palette.ink,
    weight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier = modifier, color = color,
        style = LedgerTypographyStandard.style(role, if (en) "en" else "my")
            .let { if (weight == null) it else it.copy(fontWeight = weight) },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis)
}

@Composable
private fun LabCard(
    p: ResearchLabPalette, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp), color = p.card,
        border = BorderStroke(1.dp, p.outline.copy(alpha = .8f)),
        shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private fun kindName(kind: TwoDResearchCriterionKind, en: Boolean): String =
    when (kind) {
        TwoDResearchCriterionKind.PAIR -> lab(en, "ဂဏန်းတွဲ", "Exact pair")
        TwoDResearchCriterionKind.DIGIT -> lab(en, "တစ်လုံး", "Any digit")
        TwoDResearchCriterionKind.TENS -> lab(en, "ထိပ်", "Tens")
        TwoDResearchCriterionKind.ONES -> lab(en, "ပိတ်", "Ones")
        TwoDResearchCriterionKind.DOUBLE -> lab(en, "အပူး", "Double")
        TwoDResearchCriterionKind.PARITY -> lab(en, "စုံ/မ", "Even/odd")
    }

private fun kindHint(kind: TwoDResearchCriterionKind, en: Boolean): String =
    when (kind) {
        TwoDResearchCriterionKind.PAIR -> lab(en, "00–99", "00–99")
        TwoDResearchCriterionKind.DIGIT -> lab(en, "0–9 နှစ်နေရာလုံး", "0–9 in either position")
        TwoDResearchCriterionKind.TENS -> lab(en, "ထိပ် 0–9", "Tens 0–9")
        TwoDResearchCriterionKind.ONES -> lab(en, "ပိတ် 0–9", "Ones 0–9")
        TwoDResearchCriterionKind.DOUBLE -> lab(en, "00, 11, … 99", "00, 11, … 99")
        TwoDResearchCriterionKind.PARITY -> lab(en, "ဆယ်/ခု အစဉ်", "Tens/ones parity")
    }

private fun kindValue(kind: TwoDResearchCriterionKind, value: String): String =
    if (kind == TwoDResearchCriterionKind.DOUBLE) "" else value

private fun labelOf(condition: TwoDResearchCriterion, en: Boolean): String {
    val value = when (condition.kind) {
        TwoDResearchCriterionKind.DOUBLE -> ""
        TwoDResearchCriterionKind.PARITY -> when (condition.value) {
            "EE" -> lab(en, "စုံစုံ", "Even/Even")
            "EO" -> lab(en, "စုံမ", "Even/Odd")
            "OE" -> lab(en, "မစုံ", "Odd/Even")
            else -> lab(en, "မမ", "Odd/Odd")
        }
        else -> condition.value
    }
    return kindName(condition.kind, en) + (if (value.isEmpty()) "" else " · " + value)
}

private fun researchScopeName(s: TwoDResearchScope) = s.name

@Composable
private fun LabChoice(
    label: String, selected: Boolean, p: ResearchLabPalette,
    modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit,
) {
    Surface(modifier = modifier, onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) p.accent else p.outline),
        color = if (selected) p.accentWash else p.card) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center) {
            Text(label, color = if (!enabled) p.muted else if (selected) p.accent else p.ink,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LabCriterionPicker(
    en: Boolean, p: ResearchLabPalette, kind: TwoDResearchCriterionKind,
    value: String, onKind: (TwoDResearchCriterionKind) -> Unit,
    onValue: (String) -> Unit,
) {
    // All six research operators remain visible; none is clipped offscreen.
    TwoDResearchCriterionKind.entries.toList().chunked(3).forEach { choices ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { candidate ->
                LabChoice(kindName(candidate, en), candidate == kind, p,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onKind(candidate)
                        onValue(if (candidate == TwoDResearchCriterionKind.PARITY) "EE" else "")
                    })
            }
        }
    }
    if (kind == TwoDResearchCriterionKind.PARITY) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(
                "EE" to lab(en, "စုံစုံ", "E/E"),
                "EO" to lab(en, "စုံမ", "E/O"),
                "OE" to lab(en, "မစုံ", "O/E"),
                "OO" to lab(en, "မမ", "O/O"),
            ).forEach { (key, label) ->
                LabChoice(label, value == key, p, Modifier.weight(1f)) { onValue(key) }
            }
        }
    } else if (kind == TwoDResearchCriterionKind.DOUBLE) {
        LabText(kindHint(kind, en), en, p, LedgerTextRole.HELPER, color = p.muted)
    } else {
        val limit = if (kind == TwoDResearchCriterionKind.PAIR) 2 else 1
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                if (next.length <= limit && next.all { it in '0'..'9' }) onValue(next)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(kindHint(kind, en), color = p.muted) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold, color = p.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.accent,
                unfocusedBorderColor = p.outline,
                cursorColor = p.accent,
                focusedTextColor = p.ink,
                unfocusedTextColor = p.ink,
            )
        )
    }
}

@Composable
private fun LabHero(
    en: Boolean, p: ResearchLabPalette, verifiedCount: Int,
    onOpenGuide: () -> Unit, onOpenAudit: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(21.dp), color = p.hero) {
        Box(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(p.hero, p.heroLight)))) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RESEARCH ATLAS  /  2D",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold),
                        color = Color(0xFF9CE7D9))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(
                            onClick = onOpenAudit,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.FactCheck,
                                contentDescription = lab(en,
                                    "သမိုင်းမှတ်တမ်း အရည်အသွေးစစ်ရန်",
                                    "Audit history coverage"),
                                tint = Color(0xFF9CE7D9),
                                modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = onOpenGuide,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.HelpOutline,
                                contentDescription = lab(en,
                                    "Research အသုံးပြုနည်းလမ်းညွှန်",
                                    "Open Research Guide"),
                                tint = Color(0xFF9CE7D9),
                                modifier = Modifier.size(25.dp))
                        }
                    }
                }
                Text(lab(en,
                        "2D သမိုင်းဆက်စပ်မှု လေ့လာရန်",
                        "Explore 2D history"),
                    style = LedgerTypographyStandard.style(
                        LedgerTextRole.CARD_TITLE, if (en) "en" else "my"),
                    color = p.heroText)
                Text(lab(en, "အတည်ပြုထားသော Result ", "Verified results ") +
                    verifiedCount.toString() + lab(en, " ကြိမ်", " draws"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB5DBDD))
            }
        }
    }
}

@Composable
private fun LabPeriodPicker(
    en: Boolean, p: ResearchLabPalette, session: ResearchLabSessionState,
    rangeValid: Boolean, onPickDate: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "all" to lab(en, "ရှိသမျှမှတ်တမ်း", "All history"),
            "week" to lab(en, "၇ ရက်", "7 days"),
            "month" to lab(en, "၃၀ ရက်", "30 days"),
            "custom" to lab(en, "စိတ်ကြိုက်", "Custom"),
        ).forEach { (key, label) ->
            LabChoice(label, session.period == key, p) { session.period = key }
        }
    }
    if (session.period == "custom") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LabChoice(session.start, false, p, Modifier.weight(1f)) { onPickDate("start") }
            LabChoice(session.end, false, p, Modifier.weight(1f)) { onPickDate("end") }
        }
        if (!rangeValid) {
            LabText(lab(en, "အစ/အဆုံးရက်စွဲ မမှန်ပါ",
                "Invalid start/end dates"), en, p, LedgerTextRole.HELPER,
                color = Color(0xFFD35D48))
        }
    }
}

private fun sessionName(s: DrawSession, en: Boolean) =
    if (s == DrawSession.MORNING) lab(en, "မနက်", "AM") else lab(en, "ညနေ", "PM")

private fun statusName(s: TwoDResearchStatus, en: Boolean) = when (s) {
    TwoDResearchStatus.MATCH -> lab(en, "ကိုက်ညီ", "Match")
    TwoDResearchStatus.OTHER -> lab(en, "မကိုက်ညီ", "Different")
    TwoDResearchStatus.OBSERVED -> lab(en, "Result ရှိ", "Observed")
    TwoDResearchStatus.MISSING -> lab(en, "Result မတွေ့", "Missing")
    TwoDResearchStatus.PENDING -> lab(en, "မထွက်သေး", "Pending")
    TwoDResearchStatus.OUT_OF_RANGE -> lab(en, "ကာလအပြင်", "Outside range")
}

@Composable
private fun LabEvidence(
    e: TwoDResearchEvidence, en: Boolean, p: ResearchLabPalette,
) {
    val badgeColor = when (e.status) {
        TwoDResearchStatus.MATCH -> p.accent
        TwoDResearchStatus.MISSING, TwoDResearchStatus.PENDING,
        TwoDResearchStatus.OUT_OF_RANGE -> p.muted
        else -> p.ink
    }
    Surface(shape = RoundedCornerShape(14.dp), color = p.card,
        border = BorderStroke(1.dp, p.outline)) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                LabText(e.source.date.toString(), en, p, LedgerTextRole.HELPER,
                    color = p.muted)
                LabText(statusName(e.status, en), en, p,
                    LedgerTextRole.HELPER, color = badgeColor, weight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LabText(sessionName(e.source.session, en) + "  " + e.source.digit,
                    en, p, LedgerTextRole.BODY, modifier = Modifier.weight(1f),
                    weight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = p.accent,
                    modifier = Modifier.size(17.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    LabText(sessionName(e.expected.session, en) + "  " +
                        (e.following?.digit ?: "?"), en, p, LedgerTextRole.BODY,
                        weight = FontWeight.SemiBold)
                    if (e.source.date != e.expected.date) {
                        LabText(e.expected.date.toString(), en, p, LedgerTextRole.HELPER,
                            color = p.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabCompactEvidence(
    e: TwoDResearchEvidence, en: Boolean, p: ResearchLabPalette,
) {
    val stateColor = if (e.status == TwoDResearchStatus.MATCH) p.accent else p.muted
    Surface(shape = RoundedCornerShape(12.dp), color = p.card,
        border = BorderStroke(1.dp, p.outline)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                LabText(e.source.date.toString(), en, p, LedgerTextRole.HELPER, color = p.muted)
                LabText(statusName(e.status, en), en, p, LedgerTextRole.HELPER,
                    color = stateColor, weight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LabText(sessionName(e.source.session, en) + "  " + e.source.digit,
                    en, p, LedgerTextRole.BODY, modifier = Modifier.weight(1f),
                    weight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp), tint = p.accent)
                LabText(sessionName(e.expected.session, en) + "  " +
                    (e.following?.digit ?: "—"), en, p, LedgerTextRole.BODY,
                    modifier = Modifier.weight(1f), weight = FontWeight.SemiBold)
            }
            if (e.source.date != e.expected.date) {
                LabText(lab(en, "နောက်အကြိမ် ", "Target draw ") + e.expected.date,
                    en, p, LedgerTextRole.HELPER, color = p.muted)
            }
        }
    }
}

@Composable
private fun LabStat(
    p: ResearchLabPalette, en: Boolean, number: Int, label: String,
    modifier: Modifier = Modifier, highlight: Boolean = false,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp),
        color = if (highlight) p.accentWash else p.mutedSurface) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(number.toString(), style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold),
                color = if (highlight) p.accent else p.ink)
            LabText(label, en, p, LedgerTextRole.HELPER, color = p.muted, maxLines = 2)
        }
    }
}

@Composable
internal fun TwoDResearchLabScreen(
    draws: List<HistoricDraw>,
    closedDates: Set<LocalDate>,
    cutoff: DrawIdentity,
    en: Boolean,
    ready: Boolean,
    session: ResearchLabSessionState,
    modifier: Modifier = Modifier,
) {
    val p = researchLabPalette()
    var picking by remember { mutableStateOf<String?>(null) }
    val sourceRule = TwoDResearchCriterion(session.sourceKind,
        kindValue(session.sourceKind, session.sourceValue))
    val targetRule = TwoDResearchCriterion(session.targetKind,
        kindValue(session.targetKind, session.targetValue))
    val start = runCatching { LocalDate.parse(session.start) }.getOrNull()
    val end = runCatching { LocalDate.parse(session.end) }.getOrNull()
    val rangeValid = session.period != "custom" ||
        (start != null && end != null && !start.isAfter(end))
    val eligible = ready && sourceRule.valid() && (!session.compare || targetRule.valid()) &&
        rangeValid
    val relationship = if (session.relationship == "same")
        TwoDResearchRelationship.SAME_DAY_PM else TwoDResearchRelationship.NEXT_DRAW
    val scope = if (session.relationship == "same") TwoDResearchScope.AM else when (session.scope) {
        "am" -> TwoDResearchScope.AM
        "pm" -> TwoDResearchScope.PM
        else -> TwoDResearchScope.ALL
    }
    val range = when (session.period) {
        "week" -> TwoDHistoryRange(cutoff.date.minusDays(6), cutoff.date)
        "month" -> TwoDHistoryRange(cutoff.date.minusDays(29), cutoff.date)
        "custom" -> TwoDHistoryRange(start, end)
        else -> TwoDHistoryRange()
    }
    val audit = remember(draws, closedDates, cutoff) {
        TwoDResearchHistoryAuditor.inspect(draws, cutoff, closedDates)
    }
    val verifiedCount = audit.verifiedSlots

    val report by produceState<TwoDResearchReport?>(
        initialValue = null, session.submitted, draws, closedDates, cutoff, ready,
    ) {
        value = null
        val q = session.submitted
        if (ready && q != null) value = withContext(Dispatchers.Default) {
            TwoDResearchEngine.search(draws, q, cutoff, closedDates)
        }
    }
    val result = report?.takeIf { it.query == session.submitted }
    val resultVisible = session.resultVisible && session.submitted != null

    Column(modifier.fillMaxSize().background(p.background)) {
        if (session.auditVisible) {
            TwoDResearchHistoryAuditScreen(
                audit = audit, en = en, p = p,
                onClose = { session.auditVisible = false },
                modifier = Modifier.weight(1f),
            )
        } else if (session.guideVisible) {
            TwoDResearchGuideScreen(
                en = en,
                p = p,
                ready = ready,
                onClose = { session.guideVisible = false },
                onTryExample = { example -> if (ready) session.openPreset(example) },
                modifier = Modifier.weight(1f),
            )
        } else if (!resultVisible) {
            LazyColumn(Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    LabHero(en, p, verifiedCount,
                        onOpenGuide = { session.guideVisible = true },
                        onOpenAudit = { session.auditVisible = true })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            LabText(lab(en, "အမြန်စစ်ဆေးရန် · နှိပ်လိုက်ပါ",
                                "ONE-TAP RESEARCH  /  TAP TO RUN"), en, p,
                                LedgerTextRole.HELPER, color = p.muted,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { session.guideVisible = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = p.accent)) {
                                Icon(Icons.Default.HelpOutline, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(lab(en, "လမ်းညွှန်", "Guide"),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ResearchGuideCatalog.examples.filter {
                                it.id in setOf("reverse-12", "double-discovery",
                                    "odd-odd-discovery")
                            }.forEach { example ->
                                LabChoice(
                                    when (example.id) {
                                        "reverse-12" -> "12 → 21"
                                        "double-discovery" ->
                                            lab(en, "အပူးနောက်", "After doubles")
                                        else -> lab(en, "မမနောက်", "After odd/odd")
                                    },
                                    false, p, enabled = ready,
                                ) {
                                    session.openPreset(example)
                                }
                            }
                        }
                    }
                }
                item {
                    LabCard(p) {
                        LabText(lab(en, "၁  ·  ဆက်စပ်မှုရွေးချယ်ရန်", "01  /  RELATIONSHIP"),
                            en, p, LedgerTextRole.CARD_TITLE, weight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            LabChoice(lab(en, "မနက် → ညနေ", "AM → PM"),
                                session.relationship == "same", p, Modifier.weight(1f)) {
                                session.relationship = "same"
                            }
                            LabChoice(lab(en, "နောက်တစ်ကြိမ်", "Next draw"),
                                session.relationship == "next", p, Modifier.weight(1f)) {
                                session.relationship = "next"
                            }
                        }
                        if (session.relationship == "next") {
                            LabText(lab(en, "Source အကြိမ်", "Source occasion"), en, p,
                                LedgerTextRole.HELPER, color = p.muted)
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf("am" to "AM", "pm" to "PM", "all" to "ALL")
                                    .forEach { (key, label) ->
                                        LabChoice(label, session.scope == key, p,
                                            Modifier.weight(1f)) { session.scope = key }
                                    }
                            }
                        }
                    }
                }
                item {
                    LabCard(p) {
                        LabText(lab(en, "၂  ·  အခြေခံအခြေအနေ", "02  /  SOURCE CONDITION"),
                            en, p, LedgerTextRole.CARD_TITLE, weight = FontWeight.SemiBold)
                        LabCriterionPicker(en, p, session.sourceKind, session.sourceValue,
                            onKind = { session.sourceKind = it },
                            onValue = { session.sourceValue = it })
                        HorizontalDivider(color = p.outline)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            LabText(lab(en, "နောက်ရလဒ်ကို တိုက်စစ်မလား", "Compare a target?"),
                                en, p, LedgerTextRole.BODY, modifier = Modifier.weight(1f))
                            Switch(
                                checked = session.compare,
                                onCheckedChange = { session.compare = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = p.hero,
                                    checkedTrackColor = p.accent,
                                    checkedBorderColor = p.accent,
                                    uncheckedThumbColor = p.muted,
                                    uncheckedTrackColor = p.mutedSurface,
                                    uncheckedBorderColor = p.outline,
                                ),
                            )
                        }
                        if (session.compare) {
                            LabText(lab(en, "တိုက်စစ်မည့်အခြေအနေ", "TARGET CONDITION"),
                                en, p, LedgerTextRole.HELPER, color = p.muted)
                            LabCriterionPicker(en, p, session.targetKind, session.targetValue,
                                onKind = { session.targetKind = it },
                                onValue = { session.targetValue = it })
                            if (session.sourceKind == TwoDResearchCriterionKind.PAIR &&
                                session.sourceValue.length == 2) {
                                TextButton(
                                    onClick = {
                                        session.targetKind = TwoDResearchCriterionKind.PAIR
                                        session.targetValue = session.sourceValue.reversed()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = p.accent)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, null, Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(lab(en, "ပြောင်းပြန်ဂဏန်းသုံးမည်", "Use reversed pair"))
                                }
                            }
                        } else {
                            LabText(lab(en,
                                "သတ်မှတ်အခြေအနေနောက်မှာ ဘာတွေထွက်ခဲ့လဲဆိုတာ ရှာမယ်။",
                                "Discover every recorded following pair."),
                                en, p, LedgerTextRole.HELPER, color = p.muted)
                        }
                    }
                }
                item {
                    LabCard(p) {
                        LabText(lab(en, "၃  ·  မှတ်တမ်းကာလ", "03  /  HISTORY RANGE"),
                            en, p, LedgerTextRole.CARD_TITLE, weight = FontWeight.SemiBold)
                        LabPeriodPicker(en, p, session, rangeValid) { picking = it }
                        LabText(lab(en,
                            "Source နဲ့ နောက်အကြိမ် နှစ်ခုစလုံး ရွေးထားတဲ့ကာလအတွင်း ရှိရမယ်။",
                            "Both source and following occasion must be within the selected range."),
                            en, p, LedgerTextRole.HELPER, color = p.muted)
                    }
                }
                item {
                    LabText(lab(en,
                        "အတိတ်ကဖြစ်ခဲ့တဲ့ အချက်အလက်ကိုသာ ပြမယ်။ အနာဂတ်ခန့်မှန်းချက် မဟုတ်ပါ။",
                        "Historical evidence, not a forecast of future results."),
                        en, p, LedgerTextRole.HELPER, color = p.muted)
                }
            }
            Surface(color = p.card, shadowElevation = 9.dp,
                border = BorderStroke(1.dp, p.outline)) {
                Button(
                    onClick = {
                        val q = TwoDResearchQuery(
                            sourcePair = if (session.sourceKind == TwoDResearchCriterionKind.PAIR)
                                session.sourceValue else "",
                            scope = scope,
                            relationship = relationship,
                            range = range,
                            sourceCriterion = sourceRule,
                            targetCriterion = if (session.compare) targetRule else null,
                        )
                        session.submitted = q
                        session.resultVisible = true
                        session.evidenceFilter = "all"
                        session.evidenceLimit = 25
                        session.frequencyExpanded = false
                        session.frequencyMode = "matched"
                    },
                    enabled = eligible,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.accent,
                        contentColor = if (p.accent == Color(0xFF67E0CC)) p.hero else Color.White,
                        disabledContainerColor = p.outline, disabledContentColor = p.muted)
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            !ready -> lab(en, "မှတ်တမ်းဖတ်နေသည်", "Loading history")
                            !sourceRule.valid() -> lab(en, "အခြေခံဂဏန်း ရွေးပါ", "Choose a source")
                            session.compare && !targetRule.valid() ->
                                lab(en, "တိုက်စစ်မည့်ဂဏန်း ရွေးပါ", "Choose a target")
                            !rangeValid -> lab(en, "ရက်စွဲ ပြန်ရွေးပါ", "Fix date range")
                            else -> lab(en, "သမိုင်းလေ့လာရန်", "Run historical research")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            // Results are a destination, not a section hidden below a long form.
            val query = session.submitted!!
            val source = query.sourceCriterion ?:
                TwoDResearchCriterion(TwoDResearchCriterionKind.PAIR, query.sourcePair)
            val target = query.targetCriterion ?: query.targetPair?.let {
                TwoDResearchCriterion(TwoDResearchCriterionKind.PAIR, it)
            }
            val evidence = result?.evidence.orEmpty().asReversed()
            val filtered = evidence.filter { item ->
                when (session.evidenceFilter) {
                    "match" -> item.status == TwoDResearchStatus.MATCH
                    "other" -> item.status == TwoDResearchStatus.OTHER
                    "missing" -> item.following == null
                    else -> true
                }
            }
            LazyColumn(Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Surface(color = p.hero, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().background(
                            Brush.horizontalGradient(listOf(p.hero, p.heroLight)))
                            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("RESEARCH ATLAS  /  EVIDENCE",
                                    color = Color(0xFF9CE7D9),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold))
                                IconButton(
                                    onClick = { session.guideVisible = true },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.Default.HelpOutline,
                                        contentDescription = lab(en,
                                            "Research အသုံးပြုနည်းလမ်းညွှန်",
                                            "Open Research Guide"),
                                        tint = Color(0xFF9CE7D9),
                                        modifier = Modifier.size(24.dp))
                                }
                            }
                            Text(labelOf(source, en) + "   →   " +
                                (target?.let { labelOf(it, en) } ?: lab(en, "ဘာဆက်ထွက်လဲ", "What followed?")),
                                color = p.heroText,
                                style = LedgerTypographyStandard.style(
                                    LedgerTextRole.CARD_TITLE, if (en) "en" else "my"))
                            Text((if (query.relationship == TwoDResearchRelationship.SAME_DAY_PM)
                                    lab(en, "မနက် → ညနေ", "Same-day AM → PM")
                                else researchScopeName(query.scope) +
                                    lab(en, " · နောက်တစ်ကြိမ်", " · next scheduled draw")) +
                                "  ·  " +
                                (if (query.range.start == null && query.range.end == null)
                                    lab(en, "ရှိသမျှမှတ်တမ်း", "All history")
                                else (query.range.start?.toString() ?: "…") + " – " +
                                    (query.range.end?.toString() ?: "…")),
                                color = Color(0xFFB8E0E2),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (!ready || result == null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = p.accent)
                        }
                    }
                } else {
                    item {
                        LabCard(p) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                LabText(lab(en, "သမိုင်းစစ်ဆေးမှုရလဒ်", "Historical findings"),
                                    en, p, LedgerTextRole.CARD_TITLE, weight = FontWeight.SemiBold)
                                Icon(Icons.Default.FactCheck, null, tint = p.accent)
                            }
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                LabStat(p, en, result.sourceCount,
                                    lab(en, "Source တွေ့", "Source cases"),
                                    Modifier.weight(1f))
                                LabStat(p, en, result.observedCount,
                                    lab(en, "စစ်ဆေးနိုင်", "Known outcomes"),
                                    Modifier.weight(1f))
                                LabStat(p, en,
                                    if (target == null) result.followingFrequency.size
                                    else result.matchCount,
                                    if (target == null)
                                        lab(en, "ဂဏန်းမျိုး", "Distinct pairs")
                                    else lab(en, "ကိုက်ညီ", "Matches"),
                                    Modifier.weight(1f), highlight = true)
                            }
                            if (target != null && result.observedCount > 0) {
                                val percent = result.matchCount.toDouble() /
                                    result.observedCount.toDouble() * 100.0
                                LabText(result.matchCount.toString() + " / " +
                                    result.observedCount.toString() + " · " +
                                    String.format(Locale.US, "%.1f%%", percent) +
                                    lab(en, " (သမိုင်းတွင်တွေ့ခဲ့မှု)", " (historical observations)"),
                                    en, p, LedgerTextRole.BODY,
                                    weight = FontWeight.SemiBold)
                            }
                            if (result.sourceCount == 0) {
                                LabText(lab(en,
                                    "ရွေးထားတဲ့ Source အခြေအနေနဲ့ ကိုက်ညီတဲ့ သမိုင်းမှတ်တမ်း မတွေ့ပါ။",
                                    "No recorded source cases meet the selected condition."),
                                    en, p, LedgerTextRole.BODY, color = p.muted)
                            } else if (result.observedCount == 0) {
                                LabText(lab(en,
                                    "Source တွေ့ပေမယ့် ဆက်စပ် Result ကို အတည်မပြုနိုင်သေးပါ။",
                                    "Sources exist, but no eligible following outcomes are verified."),
                                    en, p, LedgerTextRole.BODY, color = p.muted)
                            }
                            HorizontalDivider(color = p.outline)
                            LabText(lab(en, "အခြား ", "Different ") + result.otherCount +
                                "  ·  " + lab(en, "Result မတွေ့ ", "Missing ") + result.missingCount +
                                "  ·  " + lab(en, "မထွက်သေး ", "Pending ") + result.pendingCount +
                                "  ·  " + lab(en, "ကာလအပြင် ", "Out of range ") + result.outsideCount,
                                en, p, LedgerTextRole.HELPER, color = p.muted)
                            LabText(lab(en,
                                "ရလဒ်တွေဟာ သမိုင်းအတွင်း တွေ့ခဲ့မှုတွေပါ။ အနာဂတ်ထွက်နိုင်ခြေ မဟုတ်ပါ။",
                                "Recorded history only. These counts are not future probabilities."),
                                en, p, LedgerTextRole.HELPER, color = p.muted)
                        }
                    }
                    if (result.followingFrequency.isNotEmpty()) {
                        item {
                            LabCard(p) {
                                LabText(lab(en, "ဆက်ထွက်ခဲ့သည့် ဂဏန်းတွဲများ",
                                    "What actually followed"), en, p,
                                    LedgerTextRole.CARD_TITLE, weight = FontWeight.SemiBold)
                                if (target != null) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        LabChoice(
                                            lab(en, "ကိုက်ညီသောဂဏန်းတွဲ", "Matches"),
                                            session.frequencyMode == "matched", p,
                                            Modifier.weight(1f),
                                        ) {
                                            session.frequencyMode = "matched"
                                            session.frequencyExpanded = false
                                        }
                                        LabChoice(
                                            lab(en, "နောက်ထွက်သမျှ", "All outcomes"),
                                            session.frequencyMode == "all", p,
                                            Modifier.weight(1f),
                                        ) {
                                            session.frequencyMode = "all"
                                            session.frequencyExpanded = false
                                        }
                                    }
                                }
                                val matchingView = target != null &&
                                    session.frequencyMode == "matched"
                                val shownFrequencies = if (matchingView)
                                    result.matchedFollowingFrequency
                                else result.followingFrequency
                                LabText(
                                    if (matchingView)
                                        lab(en, "Target ကိုက်ညီသော နောက်ထွက်ဂဏန်းတွဲ ",
                                            "Matching outcomes ") + result.matchCount +
                                            " / " + result.observedCount
                                    else
                                        lab(en, "နောက်ထွက်ခဲ့သမျှ ",
                                            "All recorded following results ") +
                                            result.observedCount +
                                            lab(en, " ကြိမ်", " occasions"),
                                    en, p, LedgerTextRole.HELPER, color = p.muted,
                                )
                                val compact = useCompactFrequencyGrid(shownFrequencies)
                                val limit = if (compact) 12 else 6
                                val frequencies = if (session.frequencyExpanded)
                                    shownFrequencies
                                else shownFrequencies.take(limit)
                                if (matchingView && shownFrequencies.isEmpty()) {
                                    LabText(
                                        lab(en,
                                            "ရွေးထားသော Target ကိုက်ညီသည့် နောက်ထွက်ဂဏန်းတွဲ မတွေ့ပါ။ နောက်ထွက်သမျှကို ပြောင်းကြည့်နိုင်သည်။",
                                            "No matching following pairs in this history. Select All outcomes to inspect other recorded results."),
                                        en, p, LedgerTextRole.BODY, color = p.muted,
                                    )
                                } else if (compact) {
                                    LabText(lab(en,
                                        "အကြိမ်ရေတူသော ဂဏန်းတွဲများ",
                                        "Pairs with equal observed frequency"),
                                        en, p, LedgerTextRole.HELPER, color = p.muted)
                                    frequencies.chunked(4).forEach { group ->
                                        Row(Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            group.forEach { entry ->
                                                Surface(Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = p.mutedSurface,
                                                    border = BorderStroke(1.dp, p.outline)) {
                                                    Column(
                                                        Modifier.padding(
                                                            horizontal = 5.dp, vertical = 9.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        LabText(entry.digit, en, p,
                                                            LedgerTextRole.BODY,
                                                            weight = FontWeight.SemiBold)
                                                        LabText(entry.count.toString() + "×",
                                                            en, p, LedgerTextRole.HELPER,
                                                            color = p.muted)
                                                    }
                                                }
                                            }
                                            repeat(4 - group.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    val most = shownFrequencies.first().count
                                        .coerceAtLeast(1)
                                    frequencies.forEach { entry ->
                                        Row(Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                            LabText(entry.digit, en, p, LedgerTextRole.BODY,
                                                weight = FontWeight.SemiBold)
                                            Box(Modifier.weight(1f).height(9.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(p.mutedSurface)) {
                                                Box(Modifier.fillMaxWidth(
                                                    entry.count.toFloat() / most.toFloat())
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(p.accent))
                                            }
                                            LabText(entry.count.toString() + "×",
                                                en, p, LedgerTextRole.HELPER, color = p.muted)
                                        }
                                    }
                                }
                                if (!session.frequencyExpanded &&
                                    shownFrequencies.size > limit) {
                                    TextButton(onClick = {
                                        session.frequencyExpanded = true
                                    }, colors = ButtonDefaults.textButtonColors(
                                        contentColor = p.accent)) {
                                        Text(lab(en, "ဂဏန်းတွဲအားလုံးကြည့်ရန်", "View all pairs") +
                                            " (" + shownFrequencies.size + ")")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            LabText(lab(en, "သမိုင်းအထောက်အထား", "SOURCE EVIDENCE"),
                                en, p, LedgerTextRole.CARD_TITLE,
                                modifier = Modifier.weight(1f),
                                weight = FontWeight.SemiBold)
                            TextButton(onClick = {
                                session.compactEvidence = !session.compactEvidence
                            }, colors = ButtonDefaults.textButtonColors(contentColor = p.accent)) {
                                Icon(if (session.compactEvidence) Icons.Default.ViewAgenda
                                    else Icons.Default.ViewList, null,
                                    Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (session.compactEvidence)
                                    lab(en, "ကတ်ပုံစံ", "Cards")
                                    else lab(en, "အကျဉ်း", "Compact"))
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val filters = if (target == null) listOf(
                                "all" to lab(en, "အားလုံး", "All"),
                                "missing" to lab(en, "မရရှိ", "Unavailable"),
                            ) else listOf(
                                "all" to lab(en, "အားလုံး", "All"),
                                "match" to lab(en, "ကိုက်ညီ", "Match"),
                                "other" to lab(en, "မကိုက်ညီ", "Different"),
                                "missing" to lab(en, "မရရှိ", "Unavailable"),
                            )
                            filters.forEach { (key, label) ->
                                LabChoice(label, session.evidenceFilter == key, p) {
                                    session.evidenceFilter = key
                                    session.evidenceLimit = 25
                                }
                            }
                        }
                        LabText(lab(en, "ပြသ ", "Showing ") +
                            minOf(session.evidenceLimit, filtered.size) + " / " +
                            filtered.size, en, p, LedgerTextRole.HELPER, color = p.muted)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            LabCard(p) {
                                LabText(lab(en, "ဒီအမျိုးအစားမှာ မှတ်တမ်းမရှိပါ။",
                                    "No evidence in this category."),
                                    en, p, LedgerTextRole.BODY, color = p.muted)
                            }
                        }
                    } else {
                        items(filtered.take(session.evidenceLimit),
                            key = { it.source.date.toString() + it.source.session.name }) {
                            if (session.compactEvidence) LabCompactEvidence(it, en, p)
                            else LabEvidence(it, en, p)
                        }
                        if (filtered.size > session.evidenceLimit) {
                            item {
                                OutlinedButton(onClick = {
                                    session.evidenceLimit += 25
                                }, modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, p.outline)) {
                                    Text(lab(en, "နောက်ထပ်မှတ်တမ်းများ", "Show more evidence"),
                                        color = p.accent)
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = p.card, border = BorderStroke(1.dp, p.outline),
                shadowElevation = 8.dp) {
                OutlinedButton(onClick = { session.resultVisible = false },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = p.accent),
                    border = BorderStroke(1.dp, p.accent),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(lab(en, "မေးခွန်းပြင်ပြီး ထပ်စစ်ရန်", "Edit question and research again"))
                }
            }
        }
    }

    picking?.let { choice ->
        val currentDate = if (choice == "start") session.start else session.end
        val millis = remember(choice, currentDate) {
            runCatching { LocalDate.parse(currentDate).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli() }.getOrNull()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { selectedMillis ->
                        val selected = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (choice == "start") session.start = selected
                        else session.end = selected
                    }
                    picking = null
                }) { Text(lab(en, "ရွေးမည်", "Select")) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) {
                    Text(lab(en, "ပိတ်မည်", "Cancel"))
                }
            },
        ) { DatePicker(state = picker) }
    }
}
