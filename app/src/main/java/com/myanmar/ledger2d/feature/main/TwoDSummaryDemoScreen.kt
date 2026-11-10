package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Separate bold data from its compact secondary frequency or status. */
private fun summaryPrimaryAndMeta(raw: String): Pair<String, String> {
    val divider = " · "
    val at = raw.lastIndexOf(divider)
    if (at < 0) return raw to ""
    val front = raw.substring(0, at)
    val back = raw.substring(at + divider.length)
    // Unseen-digit lists are the main fact; the short prefix becomes the caption.
    return if (front == "ကျန်" || front == "Unseen") back to front
        else front to back
}

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
                "တို့သည် သက်ဆိုင်ရာ အမျိုးအစားအလိုက် အထွက်အများဆုံးစာရင်းဖြစ်ပါသည်။",
                "These are the most frequently observed values in each category."),
            e("03", "စုံ/မ ပုံစံတူ", "Matching parity",
                listOf(
                    c("စုံမ→မမ", "EO→OO", "လက်ရှိ ပုံစံ", "Current sequence"),
                    c("၃ ပတ်", "3 weeks", "သမိုင်းပုံစံတူ", "Matching weeks"),
                    c("စုံမ · ၂ ကြိမ်", "EO · 2x", "နောက်ထွက်အများဆုံး", "Top following class"),
                ),
                "တို့သည် လက်ရှိ စုံ/မ ပုံစံနှင့် တူခဲ့သော သမိုင်းအပတ်များ၏ နောက်ထွက်အခြေအနေဖြစ်ပါသည်။",
                "Following classes in historical weeks with the same parity sequence."),
            e("06", "ပျောက်ဂဏန်း", "Longest missing",
                listOf(
                    c("3 · ၁၆ ကြိမ်", "3 · 16 draws", "ပျောက်ဂဏန်း", "Absent digit"),
                    c("93 · ၄၅ ကြိမ်", "93 · 45 draws", "ပျောက်ဂဏန်းတွဲ", "Absent pair"),
                ),
                "တို့သည် ညနေအကြိမ် မှတ်တမ်းအရ အကြာဆုံးပျောက်နေသော ဂဏန်းနှင့် ဂဏန်းတွဲဖြစ်ပါသည်။",
                "Longest recorded absence streaks for evening draws."),
            e("08", "ရှေ့ထွက်ဂဏန်း တိုက်စစ်", "Previous-results match",
                listOf(
                    c("67 + 74", "67 + 74", "ရှေ့နှစ်ကြိမ်", "Two references"),
                    c("ALL · ၃ ကြိမ်", "ALL · 3x", "နှစ်ခုလုံးတူ", "Both matched"),
                    c("21 · ၂ ကြိမ်", "21 · 2x", "နောက်ထွက်အများဆုံး", "Top next result"),
                ),
                "တို့သည် ရှေ့နှစ်ကြိမ်လုံး တူညီသော သမိုင်းမှတ်တမ်းများ၏ နောက်ထွက်အခြေအနေဖြစ်ပါသည်။",
                "Recorded outcomes following matches of both preceding results."),
        )),
        SummarySampleGroup("ယခုအပတ်", "This week", listOf(
            e("05", "အသစ်/အဟောင်း စီးဆင်းမှု", "New / old flow",
                listOf(
                    c("အသစ်+အသစ် · ၁", "New+new · 1", "ပုံစံ", "Class"),
                    c("အသစ်+အဟောင်း · ၁", "New+old · 1", "ပုံစံ", "Class"),
                    c("ကျန် · 0·1·2·3·5·8·9", "Unseen · 0·1·2·3·5·8·9", "မထွက်သေး", "Unseen digits"),
                ),
                "ယခုအပတ် နောက်ဆုံးအကြိမ်တွင် အသစ်+အဟောင်း ပုံစံဖြစ်ပြီး မထွက်သေးသော ဂဏန်းများလည်း ကျန်ရှိပါသည်။",
                "The latest verified pattern is new+old."),
            e("07", "အထူးဂဏန်းအုပ်စု", "Special groups",
                listOf(
                    c("ညီအကို · ၁ ကြိမ်", "Siblings · 1x", "အများဆုံးအုပ်စု", "Top group"),
                ),
                "သည် ယခုအပတ် အထူးဂဏန်းအုပ်စုများတွင် အများဆုံးတွေ့ရသော အမျိုးအစားဖြစ်ပါသည်။",
                "The most observed special group so far this week."),
        )),
        SummarySampleGroup("ယခင်အပတ်", "Previous week", listOf(
            e("04", "ပြီးခဲ့တဲ့အပတ်စာရင်း", "Last week's results",
                listOf(
                    c("7 · ၅ ကြိမ်", "7 · 5x", "အများဆုံးဂဏန်း", "Most seen digit"),
                    c("0·9", "0·9", "မထွက်ခဲ့သောဂဏန်း", "Unseen digits"),
                    c("မမ · ၄ ကြိမ်", "OO · 4x", "အများဆုံးစုံ/မ", "Top parity"),
                ),
                "တို့သည် ပြီးခဲ့တဲ့အပတ်၏ အဓိက ထွက်ရှိမှုနှင့် ပျောက်ဂဏန်း အခြေအနေများဖြစ်ပါသည်။",
                "Key observed statistics from the previous week."),
            e("01", "ပျောက်ဂဏန်း ပြန်ဆင်းမှု", "Missing-digit returns",
                listOf(
                    c("0·9", "0·9", "ယခင်အပတ် ပျောက်", "Missing last week"),
                    c("၀ လုံး", "0 digits", "ယခုအပတ် ပြန်တွေ့", "Returned this week"),
                ),
                "ယခင်အပတ်က မထွက်ခဲ့သော 0 နှင့် 9 တို့ကို ယခုအပတ်တွင် ပြန်မတွေ့ရသေးပါ။",
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

    // One uninterrupted report: section dividers are labels, not large cards
    // or separate mini-dashboards. All eight sample findings share one reading flow.
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 11.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        CherryMysteryHeader(date, session, en, onPickDate, onChangeSession)

        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = scheme.tertiaryContainer,
            ) {
                Text(
                    "DEMO",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                if (en) "Sample only · Not live · Date does not change examples"
                else "နမူနာသာ · ရလဒ်အစစ်မဟုတ် · ရက်စွဲနှင့် မပြောင်းပါ",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        groups.forEach { group ->
            val visibleEntries = group.entries.filter { it.chips.isNotEmpty() }
            if (visibleEntries.isNotEmpty()) {
                SummaryReportSection(
                    title = if (en) group.enTitle else group.mmTitle,
                )
                visibleEntries.forEach { entry ->
                    SummaryReportParagraph(
                        entry = entry,
                        en = en,
                        expandedChip = expandedChip,
                        onToggleChip = { expandedChip = if (expandedChip == it) null else it },
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Compact brand banner; seven-segment silhouettes are deliberately undecipherable. */
@Composable
private fun CherryMysteryHeader(
    date: LocalDate,
    session: DrawSession,
    en: Boolean,
    onPickDate: () -> Unit,
    onChangeSession: (DrawSession) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF681031),
        shadowElevation = 5.dp,
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(listOf(
                    Color(0xFF420C29),
                    Color(0xFF971A48),
                    Color(0xFFE46485),
                ))
            )
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color(0xFFFFB6CA).copy(alpha = .09f),
                    radius = size.height * .72f,
                    center = Offset(size.width * .94f, size.height * .12f))
                drawCircle(Color(0xFF35051F).copy(alpha = .19f),
                    radius = size.height * .58f,
                    center = Offset(size.width * .08f, size.height * 1.07f))
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    CherryFrostedDigit(0)
                    CherryFrostedDigit(1)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        date.toString() + " · " + when (session) {
                            DrawSession.MORNING -> if (en) "Morning" else "မနက်အကြိမ်"
                            else -> if (en) "Evening" else "ညနေအကြိမ်"
                        },
                        modifier = Modifier.clickable(onClick = onPickDate)
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = .94f),
                    )
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        onClick = {
                            onChangeSession(if (session == DrawSession.MORNING)
                                DrawSession.EVENING else DrawSession.MORNING)
                        },
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = if (en) "Change session" else "အကြိမ်ပြောင်းရန်",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = .88f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Frosted fragments of two different digital glyphs: the segment outlines
 * are deliberately incomplete, so the hero cannot read as "88" or any
 * specific result. This is brand decoration, never a prediction.
 */
@Composable
private fun CherryFrostedDigit(variant: Int) {
    Surface(
        modifier = Modifier.size(width = 49.dp, height = 46.dp),
        shape = RoundedCornerShape(11.dp),
        color = Color.White.copy(alpha = .12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .40f)),
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                Color.White.copy(alpha = .13f),
                Color.White.copy(alpha = .025f),
                Color(0xFFFFB8C9).copy(alpha = .11f),
            ))
        )) {
            Canvas(
                Modifier.fillMaxSize()
                    .padding(horizontal = 13.dp, vertical = 8.dp)
                    .blur(4.dp),
            ) {
                val w = size.width
                val h = size.height
                // A different set of OPEN fragments per cell, with no
                // top/middle/bottom loops and no complete seven-segment "8".
                val fragments = if (variant == 0) listOf(
                    Pair(Offset(w*.22f,h*.11f), Offset(w*.71f,h*.11f)),
                    Pair(Offset(w*.80f,h*.19f), Offset(w*.80f,h*.43f)),
                    Pair(Offset(w*.26f,h*.52f), Offset(w*.57f,h*.52f)),
                    Pair(Offset(w*.18f,h*.64f), Offset(w*.18f,h*.81f)),
                ) else listOf(
                    Pair(Offset(w*.20f,h*.18f), Offset(w*.20f,h*.42f)),
                    Pair(Offset(w*.37f,h*.49f), Offset(w*.78f,h*.49f)),
                    Pair(Offset(w*.81f,h*.60f), Offset(w*.81f,h*.80f)),
                    Pair(Offset(w*.27f,h*.90f), Offset(w*.62f,h*.90f)),
                )
                fragments.forEachIndexed { idx, (from, to) ->
                    drawLine(
                        color = Color.White.copy(alpha = if (idx % 2 == 0) .70f else .46f),
                        start = from, end = to,
                        strokeWidth = 5.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawLine(
                    color = Color(0xFFFFDEEA).copy(alpha = .36f),
                    start = if (variant == 0) Offset(w*.35f,h*.80f) else Offset(w*.10f,h*.75f),
                    end = if (variant == 0) Offset(w*.67f,h*.28f) else Offset(w*.46f,h*.18f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = .16f),
                    Color.White.copy(alpha = .14f),
                    Color.White.copy(alpha = .05f),
                    Color.White.copy(alpha = .21f),
                ))
            ))
        }
    }
}

/** Hairline section headings keep the chronology without splitting the report into cards. */
@Composable
private fun SummaryReportSection(title: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f),
            color = scheme.primary.copy(alpha = .20f))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
        HorizontalDivider(Modifier.weight(1f),
            color = scheme.primary.copy(alpha = .20f))
    }
}

@Composable
private fun SummaryReportParagraph(
    entry: SummarySampleEntry,
    en: Boolean,
    expandedChip: String?,
    onToggleChip: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sentence = if (en) entry.enSentence else entry.mmSentence
    val more = if (en) "More details ↗" else "ပိုမိုလေ့လာရန် ↗"

    // No per-check card or heading. Make the key data more legible while
    // shrinking only the wasted inter-paragraph whitespace.
    Column(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entry.chips.forEachIndexed { chipIndex, chip ->
                val key = entry.id + "-" + chipIndex
                val expanded = expandedChip == key
                val (primary, meta) = summaryPrimaryAndMeta(
                    if (en) chip.enValue else chip.mmValue)
                val isRemaining = entry.id == "05" && chipIndex == 2
                Surface(
                    onClick = { onToggleChip(key) },
                    modifier = Modifier.animateContentSize(),
                    shape = RoundedCornerShape(if (expanded) 11.dp else 7.dp),
                    color = if (expanded) scheme.primaryContainer
                        else if (isRemaining) scheme.primaryContainer.copy(alpha = .55f)
                        else scheme.primary.copy(alpha = .055f),
                    border = BorderStroke(1.dp, scheme.primary.copy(
                        alpha = if (expanded) .37f else if (isRemaining) .28f else .12f)),
                    shadowElevation = if (expanded) 2.dp else 0.dp,
                ) {
                    Column(
                        Modifier.padding(
                            horizontal = if (expanded) 10.dp else 4.dp,
                            vertical = if (expanded) 5.dp else 1.dp,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        if (expanded) {
                            Text(
                                if (en) chip.enLabel else chip.mmLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(
                                    fontSize = if (expanded) 21.sp else if (isRemaining) 16.sp else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (expanded) scheme.onPrimaryContainer else scheme.onSurface,
                                )) { append(primary) }
                                if (meta.isNotBlank()) {
                                    append(" ")
                                    withStyle(SpanStyle(
                                        fontSize = if (expanded) 13.sp else 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (expanded) scheme.onPrimaryContainer.copy(alpha = .82f)
                                            else scheme.onSurfaceVariant,
                                    )) { append(meta) }
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = if (expanded) 3 else 2,
                            softWrap = true,
                        )
                    }
                }
            }
        }
        val annotated = buildAnnotatedString {
            append(sentence)
            append("  ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "detail-" + entry.id,
                    linkInteractionListener = { onOpenDetail(entry.id) },
                )
            ) {
                pushStyle(SpanStyle(
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold,
                ))
                append(more)
                pop()
            }
        }
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
