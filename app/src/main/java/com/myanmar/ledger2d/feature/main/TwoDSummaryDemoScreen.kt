package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
                    // Five mornings, five evenings: a FULL-WEEK layout stress test.
                    // Sequence is display order Monday–Friday for each session.
                    c("သစ်/သစ်", "New/new", "တနင်္လာ · မနက်", "Monday · AM"),
                    c("သစ်/ဟောင်း", "New/old", "အင်္ဂါ · မနက်", "Tuesday · AM"),
                    c("သစ်/သစ်", "New/new", "ဗုဒ္ဓဟူး · မနက်", "Wednesday · AM"),
                    c("ဟောင်း/ဟောင်း", "Old/old", "ကြာသပတေး · မနက်", "Thursday · AM"),
                    c("သစ်/ဟောင်း", "New/old", "သောကြာ · မနက်", "Friday · AM"),
                    c("သစ်/ဟောင်း", "New/old", "တနင်္လာ · ညနေ", "Monday · PM"),
                    c("ဟောင်း/ဟောင်း", "Old/old", "အင်္ဂါ · ညနေ", "Tuesday · PM"),
                    c("သစ်/ဟောင်း", "New/old", "ဗုဒ္ဓဟူး · ညနေ", "Wednesday · PM"),
                    c("ဟောင်း/ဟောင်း", "Old/old", "ကြာသပတေး · ညနေ", "Thursday · PM"),
                    c("ဟောင်း/ဟောင်း", "Old/old", "သောကြာ · ညနေ", "Friday · PM"),
                ),
                "မနက် ၅ ကြိမ်၊ ညနေ ၅ ကြိမ်၏ သစ်/ဟောင်း ပုံစံနှင့် မထွက်သေးသော ဂဏန်းများကို စုပြထားပါသည်။",
                "Shows new/old classes across five AM and five PM results, plus unseen digits."),
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
    var selectedGroup by rememberSaveable { mutableStateOf(0) }
    var enlargedChip by remember { mutableStateOf<Pair<String, SummarySampleChip>?>(null) }

    // Equal-height PARENT findings are scoped to the selected tag only.
    // The 4-check tag and each 2-check tag intentionally use different heights.
    Column(
        modifier.fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        CherryMysteryHeader(date, session, en, onPickDate, onChangeSession)

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = scheme.tertiaryContainer,
            ) {
                Text(
                    "DEMO", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                if (en) "Illustrative only · Not actual results · Date does not change samples"
                else "နမူနာဒေတာသာ · အစစ်မဟုတ် · ရက်စွဲပြောင်းလည်း မပြောင်းပါ",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            groups.forEachIndexed { index, group ->
                TwoDAnalysisGroupTag(
                    text = if (en) group.enTitle else group.mmTitle,
                    selected = selectedGroup == index,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedGroup = index; enlargedChip = null },
                )
            }
        }

        val visible = groups[selectedGroup].entries.filter { it.chips.isNotEmpty() }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (en) "Information not yet available"
                        else "အချက်အလက် မရရှိနိုင်သေးပါ",
                        color = scheme.onSurfaceVariant,
                    )
                }
            } else {
                val gap = 6.dp
                // Fill the first view evenly; scroll ONLY if a smaller device
                // cannot fit the minimum readable card height.
                val usable = maxHeight - gap * (visible.size - 1)
                val equalHeight = maxOf(
                    usable / visible.size.toFloat(),
                    if (selectedGroup == 0) 116.dp
                    else if (selectedGroup == 1) 225.dp
                    else 155.dp,
                )
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    visible.forEach { entry ->
                        SummarySideBySideFinding(
                            entry = entry,
                            en = en,
                            modifier = Modifier.fillMaxWidth().height(equalHeight),
                            onEnlarge = { chip -> enlargedChip = entry.id to chip },
                            onOpenDetail = onOpenDetail,
                        )
                    }
                }
            }
        }
    }

    // Compact transient zoom, not a page-sized dialog; the underlying 2-column
    // layout is unaffected by expanding a miniature data cell.
    val chosen = enlargedChip
    if (chosen != null) {
        val chip = chosen.second
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { enlargedChip = null },
            properties = PopupProperties(focusable = true),
        ) {
            Surface(
                onClick = { enlargedChip = null },
                modifier = Modifier.widthIn(min = 130.dp, max = 205.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF691832),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color(0xFFFFC8D6).copy(alpha = .48f)),
            ) {
                Column(
                    Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        if (en) chip.enLabel else chip.mmLabel,
                        color = Color(0xFFFFD7E2),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                    val (primary, meta) = summaryPrimaryAndMeta(
                        if (en) chip.enValue else chip.mmValue)
                    Text(primary, style = MaterialTheme.typography.titleLarge,
                        color = Color.White, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD7E2), textAlign = TextAlign.Center)
                    }
                }
            }
        }
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
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
        modifier = Modifier.size(width = 62.dp, height = 57.dp),
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

/**
 * One equal-height parent for each check in a selected tag. The left half is
 * a neutral parent holding dark, uniformly sized mini cards. The right half
 * keeps the already-approved explanatory copy on an unobtrusive background.
 */
@Composable
private fun SummarySideBySideFinding(
    entry: SummarySampleEntry,
    en: Boolean,
    modifier: Modifier = Modifier,
    onEnlarge: (SummarySampleChip) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sentence = if (en) entry.enSentence else entry.mmSentence
    val more = if (en) "More details ↗" else "ပိုမိုလေ့လာရန် ↗"

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .50f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(.52f).fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                color = c.surfaceVariant.copy(alpha = .28f),
                border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .28f)),
            ) {
                if (entry.id == "05") {
                    SummaryTenSlotGrid(entry, en, onEnlarge)
                } else {
                    SummarySmallDataGrid(entry, en, onEnlarge)
                }
            }

            Column(
                modifier = Modifier.weight(.48f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    sentence,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurface,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val annotated = buildAnnotatedString {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "summary-detail-" + entry.id,
                            linkInteractionListener = { onOpenDetail(entry.id) },
                        )
                    ) {
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            color = c.primary,
                        )) { append(more) }
                    }
                }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * Equal mini-card width AND height inside each parent. Other parents may use
 * different dimensions; rows are weighted so single/three/four values stay
 * aligned without mixing large and small tiles.
 */
@Composable
private fun SummarySmallDataGrid(
    entry: SummarySampleEntry,
    en: Boolean,
    onEnlarge: (SummarySampleChip) -> Unit,
) {
    val rows = entry.chips.chunked(2)
    Column(
        Modifier.fillMaxSize().padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { pair ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pair.forEach { chip ->
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        SummaryInnerDataChip(
                            chip = chip, en = en,
                            compact = false,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { onEnlarge(chip) },
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A-05 max capacity = 2 columns x 5 rows. Each row is one Mon–Fri day,
 * with its morning on the left and evening on the right. All ten demo
 * values are present; no additional rows are introduced as draws accumulate.
 */
@Composable
private fun SummaryTenSlotGrid(
    entry: SummarySampleEntry,
    en: Boolean,
    onEnlarge: (SummarySampleChip) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val days = listOf("M", "T", "W", "T", "F")
    Column(
        Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(11.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(if (en) "AM" else "မနက်",
                    fontSize = 9.sp, color = c.onSurfaceVariant)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(if (en) "PM" else "ညနေ",
                    fontSize = 9.sp, color = c.onSurfaceVariant)
            }
        }
        days.forEachIndexed { day, label ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(11.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 9.sp,
                        color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                listOf(day, day + 5).forEach { index ->
                    val chip = entry.chips.getOrNull(index)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (chip != null) {
                            SummaryInnerDataChip(
                                chip = chip,
                                en = en,
                                compact = true,
                                modifier = Modifier.fillMaxSize(),
                                onClick = { onEnlarge(chip) },
                            )
                        } else {
                            // Unconfirmed slot: never fabricate a result.
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(7.dp),
                                color = c.surface,
                                border = BorderStroke(
                                    1.dp, c.outlineVariant.copy(alpha = .45f)),
                            ) {}
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (en) "Unseen: 0·1·2·3·5·8·9"
                else "ကျန်ဂဏန်း 0·1·2·3·5·8·9",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF751B3C),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Deep-cherry data has visual priority; explanatory copy remains neutral. */
@Composable
private fun SummaryInnerDataChip(
    chip: SummarySampleChip,
    en: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (primary, meta) = summaryPrimaryAndMeta(
        if (en) chip.enValue else chip.mmValue)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 6.dp else 8.dp),
        color = Color(0xFF691832),
        border = BorderStroke(1.dp, Color(0xFFC87893).copy(alpha = .48f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                horizontal = if (compact) 2.dp else 3.dp,
                vertical = 2.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            ) {
            val readable = if (compact) primary.replace("/", "/\n") else primary
            Text(
                readable,
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (compact) 13.sp else 16.sp,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = Color(0xFFFFD7E2),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
