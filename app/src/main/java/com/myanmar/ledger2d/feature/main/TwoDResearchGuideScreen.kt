package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.domain.*

/**
 * Official one-tap questions. Only query conditions are samples; outcomes are
 * ALWAYS read from the same reconciled historical stream as normal Research.
 */
internal data class ResearchGuideExample(
    val id: String,
    val titleMy: String,
    val titleEn: String,
    val descriptionMy: String,
    val descriptionEn: String,
    val source: TwoDResearchCriterion,
    val target: TwoDResearchCriterion?,
    val relationship: TwoDResearchRelationship,
    val scope: TwoDResearchScope,
) {
    fun toQuery(): TwoDResearchQuery {
        require(source.valid()) { "Invalid guide source" }
        require(target == null || target.valid()) { "Invalid guide target" }
        return TwoDResearchQuery(
            sourcePair = if (source.kind == TwoDResearchCriterionKind.PAIR)
                source.value else "",
            scope = if (relationship == TwoDResearchRelationship.SAME_DAY_PM)
                TwoDResearchScope.AM else scope,
            relationship = relationship,
            sourceCriterion = source,
            targetCriterion = target,
        )
    }
}

internal object ResearchGuideCatalog {
    private fun c(kind: TwoDResearchCriterionKind, value: String = "") =
        TwoDResearchCriterion(kind, value)

    val examples: List<ResearchGuideExample> = listOf(
        ResearchGuideExample(
            id = "reverse-12",
            titleMy = "မနက် 12 → ညနေ 21",
            titleEn = "AM 12 → PM 21",
            descriptionMy = "တစ်နေ့တည်း မနက် 12 ထွက်ပြီး ညနေ 21 ထွက်ခဲ့ဖူးသလား။",
            descriptionEn = "Did 21 follow an AM 12 on the same day?",
            source = c(TwoDResearchCriterionKind.PAIR, "12"),
            target = c(TwoDResearchCriterionKind.PAIR, "21"),
            relationship = TwoDResearchRelationship.SAME_DAY_PM,
            scope = TwoDResearchScope.AM,
        ),
        ResearchGuideExample(
            id = "digit-7-3",
            titleMy = "တစ်လုံး 7 → တစ်လုံး 3",
            titleEn = "Any digit 7 → any digit 3",
            descriptionMy = "7 ပါတဲ့ Source နောက်အကြိမ်မှာ 3 ပါခဲ့သလား (AM/PM အားလုံး)။",
            descriptionEn = "After any pair containing 7, did the next draw contain 3?",
            source = c(TwoDResearchCriterionKind.DIGIT, "7"),
            target = c(TwoDResearchCriterionKind.DIGIT, "3"),
            relationship = TwoDResearchRelationship.NEXT_DRAW,
            scope = TwoDResearchScope.ALL,
        ),
        ResearchGuideExample(
            id = "double-discovery",
            titleMy = "အပူးနောက် ဘာဆက်ထွက်ခဲ့လဲ",
            titleEn = "What followed a double?",
            descriptionMy = "00၊ 11 … 99 ထွက်ပြီးနောက် ဘယ်ဂဏန်းတွဲတွေ ထွက်ခဲ့သလဲ။",
            descriptionEn = "Discover recorded pairs following 00, 11 … 99.",
            source = c(TwoDResearchCriterionKind.DOUBLE),
            target = null,
            relationship = TwoDResearchRelationship.NEXT_DRAW,
            scope = TwoDResearchScope.ALL,
        ),
        ResearchGuideExample(
            id = "odd-odd-discovery",
            titleMy = "မမနောက် ဘာဆက်ထွက်ခဲ့လဲ",
            titleEn = "What followed odd/odd?",
            descriptionMy = "ထိပ်ရော ပိတ်ပါ မဂဏန်းဖြစ်တဲ့အကြိမ်တွေရဲ့ နောက်ရလဒ်များ။",
            descriptionEn = "Discover outcomes after odd-tens and odd-ones pairs.",
            source = c(TwoDResearchCriterionKind.PARITY, "OO"),
            target = null,
            relationship = TwoDResearchRelationship.NEXT_DRAW,
            scope = TwoDResearchScope.ALL,
        ),
        ResearchGuideExample(
            id = "tens-7-ones-3",
            titleMy = "ထိပ် 7 → ပိတ် 3",
            titleEn = "Tens 7 → ones 3",
            descriptionMy = "Source ထိပ် 7 ဖြစ်ပြီး နောက်အကြိမ်မှာ ပိတ် 3 ဖြစ်ခဲ့သလား။",
            descriptionEn = "Did an outcome ending in 3 follow a pair starting with 7?",
            source = c(TwoDResearchCriterionKind.TENS, "7"),
            target = c(TwoDResearchCriterionKind.ONES, "3"),
            relationship = TwoDResearchRelationship.NEXT_DRAW,
            scope = TwoDResearchScope.ALL,
        ),
        ResearchGuideExample(
            id = "parity-odd-to-even",
            titleMy = "မမ → စုံစုံ",
            titleEn = "Odd/odd → even/even",
            descriptionMy = "မမထွက်ပြီးနောက် စုံစုံထွက်ခဲ့ဖူးသလား။",
            descriptionEn = "Did even/even follow an odd/odd pair?",
            source = c(TwoDResearchCriterionKind.PARITY, "OO"),
            target = c(TwoDResearchCriterionKind.PARITY, "EE"),
            relationship = TwoDResearchRelationship.NEXT_DRAW,
            scope = TwoDResearchScope.ALL,
        ),
    )
}

private fun gt(en: Boolean, mm: String, english: String) = if (en) english else mm

@Composable
private fun GuideText(
    value: String,
    en: Boolean,
    p: ResearchLabPalette,
    role: LedgerTextRole = LedgerTextRole.BODY,
    color: Color = p.ink,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Text(value, modifier = modifier, color = color,
        style = LedgerTypographyStandard.style(role, if (en) "en" else "my")
            .let { if (bold) it.copy(fontWeight = FontWeight.SemiBold) else it })
}

@Composable
private fun GuideHero(en: Boolean, p: ResearchLabPalette) {
    Surface(shape = RoundedCornerShape(21.dp), color = p.hero) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(p.hero, p.heroLight)))
                .padding(horizontal = 17.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RESEARCH ATLAS  /  ACADEMY",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF9CE7D9))
                Icon(Icons.Default.School, contentDescription = null, tint = Color.White)
            }
            Text(gt(en, "Research အသုံးပြုနည်းလမ်းညွှန်", "Learn Research Atlas"),
                style = LedgerTypographyStandard.style(
                    LedgerTextRole.CARD_TITLE, if (en) "en" else "my"),
                color = p.heroText)
            Text(gt(en,
                "မေးခွန်းရွေးပါ၊ သမိုင်းကိုစစ်ပါ၊ အထောက်အထားကို နားလည်ပါ။",
                "Build a question, examine history, understand the evidence."),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB8E0E2))
        }
    }
}

@Composable
private fun GuideCard(
    en: Boolean,
    p: ResearchLabPalette,
    id: String,
    number: String,
    titleMy: String,
    titleEn: String,
    expanded: Boolean,
    onToggle: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = p.card, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, p.outline),
        shadowElevation = if (expanded) 2.dp else 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Surface(
                color = Color.Transparent,
                onClick = { onToggle(id) },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(color = p.accentWash, shape = RoundedCornerShape(9.dp)) {
                        Text(number, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            color = p.accent,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold))
                    }
                    GuideText(gt(en, titleMy, titleEn), en, p, LedgerTextRole.BODY,
                        modifier = Modifier.weight(1f), bold = true)
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded)
                            gt(en, "ပိတ်ရန်", "Collapse") else gt(en, "ဖွင့်ရန်", "Expand"),
                        tint = p.muted,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = p.outline)
                Column(Modifier.padding(13.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content)
            }
        }
    }
}

@Composable
private fun GuideLine(
    en: Boolean,
    p: ResearchLabPalette,
    headingMy: String,
    headingEn: String,
    contentMy: String,
    contentEn: String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        GuideText(gt(en, headingMy, headingEn), en, p, LedgerTextRole.BODY, bold = true)
        GuideText(gt(en, contentMy, contentEn), en, p, LedgerTextRole.HELPER,
            color = p.muted)
    }
}

@Composable
private fun GuideExampleCard(
    ex: ResearchGuideExample,
    en: Boolean,
    p: ResearchLabPalette,
    ready: Boolean,
    onTry: (ResearchGuideExample) -> Unit,
) {
    Surface(color = p.card, shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, p.outline)) {
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            GuideText(if (en) ex.titleEn else ex.titleMy, en, p,
                LedgerTextRole.CARD_TITLE, bold = true)
            GuideText(if (en) ex.descriptionEn else ex.descriptionMy, en, p,
                LedgerTextRole.HELPER, color = p.muted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                GuideText(
                    if (ex.relationship == TwoDResearchRelationship.SAME_DAY_PM)
                        gt(en, "AM → PM", "Same-day AM → PM")
                    else ex.scope.name + gt(en, " · နောက်တစ်ကြိမ်", " · next draw"),
                    en, p, LedgerTextRole.HELPER, color = p.muted,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onTry(ex) }, enabled = ready,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.accent,
                        contentColor = if (p.accent == Color(0xFF67E0CC)) p.hero else Color.White,
                        disabledContainerColor = p.mutedSurface,
                        disabledContentColor = p.muted,
                    )) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(gt(en, "စမ်းမယ်", "Try it"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Optional, read-only, discoverable guide. Opening it never edits research. */
@Composable
internal fun TwoDResearchGuideScreen(
    en: Boolean,
    p: ResearchLabPalette,
    ready: Boolean,
    onClose: () -> Unit,
    onTryExample: (ResearchGuideExample) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openLesson by remember { mutableStateOf("start") }

    Column(modifier.fillMaxSize().background(p.background)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { GuideHero(en, p) }
            item {
                GuideText(gt(en, "အခြေခံသင်ခန်းစာများ", "EXPLORE THE GUIDE"), en, p,
                    LedgerTextRole.CARD_TITLE, bold = true)
            }
            item {
                GuideCard(en, p, "start", "01",
                    "အမြန်စတင်အသုံးပြုနည်း", "Get started in four steps",
                    openLesson == "start", { openLesson = if (openLesson == it) "" else it }) {
                    GuideLine(en, p, "၁။ ဆက်စပ်မှုရွေးပါ", "1. Choose a relationship",
                        "တစ်နေ့တည်း မနက် → ညနေ သို့မဟုတ် နောက်တစ်ကြိမ်ကို ရွေးပါ။",
                        "Select same-day AM → PM or the next scheduled draw.")
                    GuideLine(en, p, "၂။ Source ရွေးပါ", "2. Define the source",
                        "ဂဏန်းတွဲ၊ တစ်လုံး၊ ထိပ်၊ ပိတ်၊ အပူး၊ စုံ/မ ထဲက ရွေးပါ။",
                        "Choose an exact pair, any digit, tens, ones, double or parity.")
                    GuideLine(en, p, "၃။ Target ရွေးချယ်နိုင်ပါသည်", "3. Optionally compare a target",
                        "Switch ပိတ်လျှင် ဘာဆက်ထွက်ခဲ့လဲ ရှာမယ်။ ဖွင့်လျှင် Target ကိုက်မကိုက် စစ်မယ်။",
                        "Switch off discovers all following results; on checks a chosen target.")
                    GuideLine(en, p, "၄။ ကာလရွေးပြီး စစ်ပါ", "4. Choose a period and run",
                        "ရှိသမျှမှတ်တမ်း၊ ၇ ရက်၊ ၃၀ ရက် သို့မဟုတ် စိတ်ကြိုက်ရက်စွဲကို ရွေးပြီး စစ်ပါ။",
                        "Use all history, 7 days, 30 days or custom dates, then run research.")
                }
            }
            item {
                GuideCard(en, p, "criteria", "02",
                    "စစ်ဆေးနိုင်သော အမျိုးအစား ၆ မျိုး", "Six ways to define a condition",
                    openLesson == "criteria", { openLesson = if (openLesson == it) "" else it }) {
                    GuideLine(en, p, "ဂဏန်းတွဲ · 00–99", "Exact pair · 00–99",
                        "ဥပမာ 07 ကို ရွေးလျှင် 07 ကိုသာ စစ်မယ်။ 70 မပါ။",
                        "07 matches only 07, not 70. Leading zeros matter.")
                    GuideLine(en, p, "တစ်လုံး · 0–9", "Any digit · 0–9",
                        "7 ပါတဲ့ 17၊ 70၊ 47၊ 77 တို့ကို စစ်မယ်။ 77 ကို တစ်ကြိမ်ပဲတွက်မယ်။",
                        "7 matches 17, 70, 47 and 77; 77 counts as one draw.")
                    GuideLine(en, p, "ထိပ်", "Tens",
                        "ရှေ့ဂဏန်း။ ထိပ် 7 ဆိုလျှင် 70 မှ 79 အထိ။",
                        "First digit: tens 7 includes 70 through 79.")
                    GuideLine(en, p, "ပိတ်", "Ones",
                        "နောက်ဂဏန်း။ ပိတ် 3 ဆိုလျှင် 03၊ 13 … 93။",
                        "Last digit: ones 3 includes 03, 13 … 93.")
                    GuideLine(en, p, "အပူး", "Double",
                        "00၊ 11၊ 22 … 99 ကဲ့သို့ နှစ်လုံးတူသောဂဏန်းတွဲ။",
                        "Equal digits: 00, 11, 22 … 99.")
                    GuideLine(en, p, "စုံ/မ", "Tens/ones parity",
                        "ထိပ် → ပိတ် အစဉ်ဖြင့် စုံစုံ (24)၊ စုံမ (23)၊ မစုံ (32)၊ မမ (31)။",
                        "Tens then ones: even/even (24), even/odd (23), odd/even (32), odd/odd (31).")
                }
            }
            item {
                GuideCard(en, p, "timeline", "03",
                    "မနက်၊ ညနေ၊ ALL နဲ့ အကြိမ်အစဉ်", "AM, PM, ALL and draw order",
                    openLesson == "timeline", { openLesson = if (openLesson == it) "" else it }) {
                    GuideLine(en, p, "မနက် → ညနေ", "Same-day AM → PM",
                        "တစ်နေ့တည်း မနက် Source နဲ့ အဲဒီနေ့ ညနေ Result ကိုပဲ စစ်မယ်။",
                        "Only compare the morning source to the evening on that same date.")
                    GuideLine(en, p, "နောက်တစ်ကြိမ်", "Next scheduled draw",
                        "မနက်နောက် ညနေ၊ ညနေနောက် အလုပ်ဖွင့်ရက် မနက်ကို စစ်မယ်။",
                        "AM advances to PM; PM advances to the next open morning.")
                    GuideLine(en, p, "AM / PM / ALL", "AM / PM / ALL",
                        "Source ကို မနက်မှသာ၊ ညနေမှသာ၊ သို့မဟုတ် နှစ်မျိုးလုံးမှ ရှာမလားဆိုတာ ရွေးတာပါ။",
                        "Filter source draws to morning, evening, or both; it does not change the target rule.")
                    GuideLine(en, p, "ရက်ပိတ်နှင့် Result မရှိမှု", "Closures and missing results",
                        "အတည်ပြုပိတ်ရက်ကို ကျော်မယ်။ ဖွင့်ရက်မှာ Result ပျောက်နေရင် နောက်သိထားတဲ့အကြိမ်ဆီ ခုန်မသွားဘူး။",
                        "Skip verified closures, but never silently jump over a missing scheduled draw.")
                }
            }
            item {
                GuideCard(en, p, "target", "04",
                    "Target Switch နဲ့ မှတ်တမ်းကာလ", "Target switch and history range",
                    openLesson == "target", { openLesson = if (openLesson == it) "" else it }) {
                    GuideLine(en, p, "Switch ပိတ်", "Switch off · discover",
                        "Source နောက်မှာ ထွက်ခဲ့သမျှ ဂဏန်းတွဲတွေရဲ့ အကြိမ်ရေကို ရှာမယ်။",
                        "Discover all recorded following pairs, without checking a target.")
                    GuideLine(en, p, "Switch ဖွင့်", "Switch on · compare",
                        "Source နောက်မှာ Target အခြေအနေ ကိုက်ညီခဲ့သလားဆိုတာ စစ်မယ်။",
                        "Test whether the following pair satisfies a specified target condition.")
                    GuideLine(en, p, "ကာလသတ်မှတ်ချက်", "Evidence window",
                        "ရှိသမျှမှတ်တမ်း၊ နောက်ဆုံး ၇ ရက်၊ ၃၀ ရက်၊ စိတ်ကြိုက်ရက်စွဲ။ Source နဲ့ Target နှစ်ခုစလုံးက ကာလအတွင်း ရှိရမယ်။",
                        "All history, last 7/30 inclusive calendar days, or custom dates. Both source and next occasion must be inside the window.")
                    GuideLine(en, p, "ပြောင်းပြန် Shortcut", "Reverse-pair shortcut",
                        "ဂဏန်းတွဲ 12 ရွေးထားရင် Target 21 ကို အမြန်ဖြည့်ပေးတာသာ ဖြစ်တယ်။",
                        "For exact source 12, the shortcut fills target 21. It is not a prediction rule.")
                }
            }
            item {
                GuideCard(en, p, "results", "05",
                    "ရလဒ်အရေအတွက် ဖတ်နည်း", "Understand the result counts",
                    openLesson == "results", { openLesson = if (openLesson == it) "" else it }) {
                    GuideLine(en, p, "Source တွေ့", "Source cases",
                        "ရွေးထားတဲ့ Source အခြေအနေ ပြည့်ခဲ့တဲ့ သမိုင်းအကြိမ်။",
                        "Historical draws matching your source condition.")
                    GuideLine(en, p, "စစ်ဆေးနိုင်", "Known outcomes",
                        "နောက်တစ်ကြိမ် Result ကို တကယ်အတည်ပြုထားတဲ့အကြိမ်။",
                        "Sources with a verified eligible following result.")
                    GuideLine(en, p, "ကိုက်ညီ / မကိုက်ညီ", "Match / different",
                        "Target ဖွင့်ထားရင် စစ်နိုင်တဲ့ Result တွေကို ကိုက်ညီ/မကိုက်ညီ ခွဲမယ်။",
                        "With a target, split known outcomes into matches and different outcomes.")
                    GuideLine(en, p, "Result မတွေ့ / မထွက်သေး / ကာလအပြင်",
                        "Missing / pending / outside range",
                        "နောက်ရလဒ်မရှိတာ၊ မထွက်သေးတာ၊ ကာလအပြင်ဖြစ်တာကို မကိုက်ညီမှုထဲ မတွက်ဘူး။",
                        "Unavailable, not-yet-due and out-of-range results are not treated as nonmatches.")
                    GuideLine(en, p, "ရာခိုင်နှုန်း", "Historical percentage",
                        "ကိုက်ညီမှု ÷ စစ်နိုင်တဲ့အကြိမ် × 100 ပါ။ အနာဂတ်ဖြစ်နိုင်ခြေ မဟုတ်ပါ။",
                        "Matches ÷ known outcomes × 100. This is not the probability of a future draw.")
                }
            }
            item {
                GuideCard(en, p, "evidence", "06",
                    "Frequency နဲ့ သမိုင်းအထောက်အထား", "Frequency and evidence",
                    openLesson == "evidence", { openLesson = if (openLesson == it) "" else it }) {
                    GuideLine(en, p, "ကိုက်ညီသောဂဏန်းတွဲ", "Matching pairs",
                        "Target ဖွင့်ထားရင် Target နဲ့ကိုက်တဲ့ နောက်ထွက်ဂဏန်းတွဲတွေကို အရင်ပြမယ်။",
                        "With a target, show only matching following pairs by default.")
                    GuideLine(en, p, "နောက်ထွက်သမျှ", "All following outcomes",
                        "Target ကိုက်တာရော မကိုက်တာပါ အတည်ပြုထားတဲ့ နောက်ရလဒ်အားလုံးကို ကြည့်နိုင်တယ်။",
                        "Inspect every known following pair, including matches and other outcomes.")
                    GuideLine(en, p, "Number Grid / Bar Chart", "Number tiles / bars",
                        "အကြိမ်ရေတူရင် ဂဏန်းကွက်၊ မတူရင် အစိမ်းရောင်နှိုင်းယှဉ် Bar ပြမယ်။",
                        "Equal counts use compact tiles; unequal counts use comparative bars.")
                    GuideLine(en, p, "Evidence · ကတ်/အကျဉ်း", "Evidence · cards/compact",
                        "ရက်စွဲ၊ Source၊ နောက်ရလဒ်၊ ကိုက်ညီမှုကို ပြန်ကြည့်ပါ။ ကတ်ပုံစံ/အကျဉ်း ပြောင်းနိုင်တယ်။",
                        "Audit dates, source, next result and status; switch between card and compact views.")
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                GuideText(gt(en, "လက်တွေ့စမ်းကြည့်ရန်", "TRY A REAL HISTORICAL QUERY"), en, p,
                    LedgerTextRole.CARD_TITLE, bold = true)
                GuideText(gt(en,
                    "ဥပမာတစ်ခုကို နှိပ်လျှင် လက်ရှိသမိုင်းမှတ်တမ်းနဲ့ တန်းစစ်မယ်။ Demo Result မပါပါ။",
                    "Each example runs against real eligible history, never a demo result."),
                    en, p, LedgerTextRole.HELPER, color = p.muted)
            }
            items(ResearchGuideCatalog.examples, key = { it.id }) { example ->
                GuideExampleCard(example, en, p, ready, onTryExample)
            }
            item {
                GuideText(
                    gt(en,
                        "သတိပြုရန် — သမိုင်းမှာ ဖြစ်ခဲ့သည့်အကြိမ်ရေက အနာဂတ်ဂဏန်းအတွက် ခန့်မှန်းချက်၊ အာမခံချက် မဟုတ်ပါ။",
                        "Remember: historical frequencies are observations, not predictions or guarantees."),
                    en, p, LedgerTextRole.HELPER, color = p.muted,
                    modifier = Modifier.padding(vertical = 7.dp),
                )
            }
        }
        Surface(color = p.card, border = BorderStroke(1.dp, p.outline),
            shadowElevation = 6.dp) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp)
                    .heightIn(min = 48.dp),
                border = BorderStroke(1.dp, p.accent),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.accent),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null,
                    modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(gt(en, "Research သို့ပြန်ရန်", "Back to Research"),
                    style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
