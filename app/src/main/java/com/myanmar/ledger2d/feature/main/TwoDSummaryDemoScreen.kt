package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Visual-only prototype for the new 2D Summary tab.
 *
 * Every frequency, digit and pattern below is invented SAMPLE DATA. The panel
 * does not read, overwrite or disguise the verified analysis as a forecast.
 * Real summaries will be built from TwoDAnalysisReport in a subsequent pass.
 */
private data class SummarySampleChip(
    val mmValue: String, val enValue: String,
    val mmLabel: String, val enLabel: String,
)

private data class SummarySampleEntry(
    val id: String,
    val mmTitle: String, val enTitle: String,
    val chips: List<SummarySampleChip>,
    val mmSentence: String, val enSentence: String,
)

private data class SummarySampleGroup(
    val mmTitle: String, val enTitle: String,
    val entries: List<SummarySampleEntry>,
)

private fun demoSummaryGroups(): List<SummarySampleGroup> {
    fun c(mm: String, en: String, ml: String, el: String) =
        SummarySampleChip(mm, en, ml, el)
    fun e(id: String, mm: String, en: String, chips: List<SummarySampleChip>,
          mmSentence: String, enSentence: String) =
        SummarySampleEntry(id, mm, en, chips, mmSentence, enSentence)

    return listOf(
        SummarySampleGroup("လက်ရှိအကြိမ်", "Current draw", listOf(
            e("02", "အထွက်အများဆုံး", "Most frequent",
                listOf(
                    c("21·73 · ၆ ကြိမ်", "21·73 · 6x", "ဂဏန်းတွဲ", "2D pairs"),
                    c("1·7 · ၄၅ ကြိမ်", "1·7 · 45x", "ဂဏန်း", "Digits"),
                    c("ညီအကို · ၄၃ ကြိမ်", "Siblings · 43x", "အထူးအုပ်စု", "Special group"),
                    c("စုံမ · ၁၃ ကြိမ်", "Even–odd · 13x", "စုံ/မ အုပ်စု", "Parity"),
                ),
                "တို့သည် အမျိုးအစားအလိုက် အထွက်အများဆုံးစာရင်း ဖြစ်ပါသည်။",
                "These are the most frequently observed values in each category."),
            e("03", "စုံ/မ ပုံစံတူ", "Matching parity",
                listOf(
                    c("စုံမ→မမ", "EO→OO", "လက်ရှိ ပုံစံ", "Current sequence"),
                    c("၃ ပတ်", "3 weeks", "သမိုင်းပုံစံတူ", "Matching weeks"),
                    c("စုံမ · ၂ ကြိမ်", "EO · 2x", "နောက်ထွက်အများဆုံး", "Top following class"),
                ),
                "လက်ရှိ ပုံစံနှင့် တူခဲ့သော အပတ်များ၏ နောက်ထွက်အခြေအနေ ဖြစ်ပါသည်။",
                "Following classes in historical weeks with the same parity sequence."),
            e("06", "ပျောက်ဂဏန်း", "Longest missing",
                listOf(
                    c("3 · ၁၆ ကြိမ်", "3 · 16 draws", "ပျောက်ဂဏန်း", "Absent digit"),
                    c("93 · ၄၅ ကြိမ်", "93 · 45 draws", "ပျောက်ဂဏန်းတွဲ", "Absent pair"),
                ),
                "တို့သည် ညနေအကြိမ် မှတ်တမ်းအရ အကြာဆုံး မထွက်ရှိသေးသော ဂဏန်းများ ဖြစ်ပါသည်။",
                "Longest recorded absence streaks for evening draws."),
            e("08", "ရှေ့ထွက်ဂဏန်း တိုက်စစ်", "Previous-results match",
                listOf(
                    c("67 + 74", "67 + 74", "ရှေ့နှစ်ကြိမ်", "Two references"),
                    c("ALL · ၃ ကြိမ်", "ALL · 3x", "နှစ်ခုလုံးတူ", "Both matched"),
                    c("21 · ၂ ကြိမ်", "21 · 2x", "နောက်ထွက်အများဆုံး", "Top next result"),
                ),
                "ရှေ့နှစ်ကြိမ်လုံး တူညီခဲ့သော သမိုင်းမှတ်တမ်းများ၏ နောက်ထွက်ရလဒ် ဖြစ်ပါသည်။",
                "Recorded outcomes following matches of both preceding results."),
        )),
        SummarySampleGroup("ယခုအပတ်", "This week", listOf(
            e("05", "အသစ်/အဟောင်း စီးဆင်းမှု", "New / old flow",
                listOf(
                    c("အသစ်+အသစ် · ၁", "New+new · 1", "ပုံစံ", "Class"),
                    c("အသစ်+အဟောင်း · ၁", "New+old · 1", "ပုံစံ", "Class"),
                    c("ကျန် · 0·1·2·3·5·8·9", "Unseen · 0·1·2·3·5·8·9", "မထွက်သေး", "Unseen digits"),
                ),
                "နောက်ဆုံးအကြိမ်မှာ အသစ်+အဟောင်း ပုံစံ ဖြစ်ပါသည်။",
                "The latest verified pattern is new+old."),
            e("07", "အထူးဂဏန်းအုပ်စု", "Special groups",
                listOf(
                    c("ညီအကို · ၁ ကြိမ်", "Siblings · 1x", "အများဆုံးအုပ်စု", "Top group"),
                ),
                "ယခုအပတ် အထူးအုပ်စုများတွင် အများဆုံးတွေ့ရသည့် အမျိုးအစား ဖြစ်ပါသည်။",
                "The most observed special group so far this week."),
        )),
        SummarySampleGroup("ယခင်အပတ်", "Previous week", listOf(
            e("04", "ပြီးခဲ့တဲ့အပတ်စာရင်း", "Last week's results",
                listOf(
                    c("7 · ၅ ကြိမ်", "7 · 5x", "အများဆုံးဂဏန်း", "Most seen digit"),
                    c("0·9", "0·9", "မထွက်ခဲ့သောဂဏန်း", "Unseen digits"),
                    c("မမ · ၄ ကြိမ်", "OO · 4x", "အများဆုံးစုံ/မ", "Top parity"),
                ),
                "တို့သည် ပြီးခဲ့တဲ့အပတ်၏ အဓိကထွက်ရှိမှုများ ဖြစ်ပါသည်။",
                "Key observed statistics from the previous week."),
            e("01", "ပျောက်ဂဏန်း ပြန်ဆင်းမှု", "Missing-digit returns",
                listOf(
                    c("0·9", "0·9", "ယခင်အပတ် ပျောက်", "Missing last week"),
                    c("၀ လုံး", "0 digits", "ယခုအပတ် ပြန်တွေ့", "Returned this week"),
                ),
                "ယခင်အပတ် ပျောက်ဂဏန်းများ ယခုအပတ် ပြန်မတွေ့ရသေးပါ။",
                "None of last week's missing digits have returned yet."),
        )),
    )
}

@Composable
internal fun TwoDSummaryDemoScreen(
    date: LocalDate,
    session: DrawSession,
    en: Boolean,
    modifier: Modifier = Modifier,
    onPickDate: () -> Unit,
    onChangeSession: (DrawSession) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val groups = remember { demoSummaryGroups() }
    var expandedChip by remember { mutableStateOf<String?>(null) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(19.dp),
            color = scheme.primaryContainer,
            shadowElevation = 5.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.horizontalGradient(listOf(
                        Color(0xFF191C4B), Color(0xFF513B8E), Color(0xFF865DA6),
                    ))
                ).padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    listOf("?", "?").forEach { digit ->
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = Color.White.copy(alpha = 0.13f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            shadowElevation = 3.dp,
                        ) {
                            Box(Modifier.size(width = 46.dp, height = 48.dp),
                                contentAlignment = Alignment.Center) {
                                Text(digit, style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        date.toString() + " · " +
                            if (session == DrawSession.MORNING) {
                                if (en) "Morning" else "မနက်အကြိမ်"
                            } else {
                                if (en) "Evening" else "ညနေအကြိမ်"
                            },
                        modifier = Modifier.clickable(onClick = onPickDate)
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                    IconButton(
                        modifier = Modifier.size(23.dp),
                        onClick = {
                            onChangeSession(if (session == DrawSession.MORNING)
                                DrawSession.EVENING else DrawSession.MORNING)
                        },
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(15.dp),
                            tint = Color.White.copy(alpha = 0.88f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(shape = RoundedCornerShape(6.dp),
                color = scheme.tertiaryContainer) {
                Text("DEMO", style = MaterialTheme.typography.labelSmall,
                    color = scheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Text(if (en) "Sample values only · Not live data · Date does not filter samples"
                else "နမူနာဒေတာသာ · အစစ်မဟုတ် · ရက်စွဲပြောင်းလည်း နမူနာမပြောင်းပါ",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        SummarySampleGroupCard(
            title = if (en) groups[0].enTitle else groups[0].mmTitle,
            entries = groups[0].entries,
            en = en,
            expandedChip = expandedChip,
            onToggleChip = { expandedChip = if (expandedChip == it) null else it },
            onOpenDetail = onOpenDetail,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top) {
            groups.drop(1).forEach { group ->
                SummarySampleGroupCard(
                    title = if (en) group.enTitle else group.mmTitle,
                    entries = group.entries,
                    en = en,
                    expandedChip = expandedChip,
                    onToggleChip = { expandedChip = if (expandedChip == it) null else it },
                    onOpenDetail = onOpenDetail,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.heightIn(min = 5.dp))
    }
}

@Composable
private fun SummarySampleGroupCard(
    title: String,
    entries: List<SummarySampleEntry>,
    en: Boolean,
    expandedChip: String?,
    onToggleChip: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = scheme.primary)
            entries.forEachIndexed { index, entry ->
                if (entry.chips.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("A-" + entry.id + " · " +
                                if (en) entry.enTitle else entry.mmTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = scheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            entry.chips.forEachIndexed { chipIndex, chip ->
                                val key = entry.id + "-" + chipIndex
                                val expanded = expandedChip == key
                                Surface(
                                    onClick = { onToggleChip(key) },
                                    modifier = Modifier.animateContentSize(),
                                    shape = RoundedCornerShape(if (expanded) 11.dp else 8.dp),
                                    color = if (expanded) scheme.primaryContainer
                                        else scheme.surfaceVariant.copy(alpha = .72f),
                                    border = BorderStroke(1.dp, scheme.primary.copy(
                                        alpha = if (expanded) .45f else .10f)),
                                    shadowElevation = if (expanded) 3.dp else 0.dp,
                                ) {
                                    Column(Modifier.padding(
                                        horizontal = if (expanded) 10.dp else 5.dp,
                                        vertical = if (expanded) 6.dp else 3.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        if (expanded) {
                                            Text(if (en) chip.enLabel else chip.mmLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onSurfaceVariant)
                                        }
                                        Text(if (en) chip.enValue else chip.mmValue,
                                            style = if (expanded)
                                                MaterialTheme.typography.titleSmall
                                            else MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (expanded) scheme.onPrimaryContainer
                                                else scheme.onSurface,
                                            maxLines = if (expanded) 3 else 2)
                                    }
                                }
                            }
                        }
                        Text(if (en) entry.enSentence else entry.mmSentence,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End) {
                            Row(
                                Modifier.clickable { onOpenDetail(entry.id) }
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(if (en) "Explore details" else "ပိုမိုလေ့လာရန်",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold, color = scheme.primary)
                                Icon(Icons.Default.ArrowOutward, null,
                                    modifier = Modifier.size(12.dp), tint = scheme.primary)
                            }
                        }
                    }
                }
                if (index < entries.lastIndex) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .65f))
                }
            }
        }
    }
}
