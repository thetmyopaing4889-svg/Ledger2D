package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Visual-only experiment for the 2D Summary tab.
 * All figures below are illustrative demo fixtures, NEVER verified history,
 * ledger results, calculated odds or a prediction. No database/network access.
 *
 * One scrollable report displays all eight checks without an extra group selector.
 * The rows are content-sized: long evidence stays in the existing real-data
 * analysis detail pages, while all six A-02 and A-06 output families and the
 * independent A-08 conditions remain visible in this first-level demo.
 */
private val SummaryWine = Color(0xFF751B3C)
private val SummaryRose = Color(0xFFFFEBF1)
private val SummaryLine = Color(0xFFE9CBD5)

private data class SummaryFact(
    val mmLabel: String,
    val enLabel: String,
    val mmValue: String,
    val enValue: String,
)

private fun fact(
    mmLabel: String,
    enLabel: String,
    mmValue: String,
    enValue: String,
) = SummaryFact(mmLabel, enLabel, mmValue, enValue)

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
    Column(
        modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SummaryCompactDateBar(date, session, en, onPickDate, onChangeSession)

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = SummaryRose,
            ) {
                Text("DEMO", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SummaryWine)
            }
            Text(
                if (en) "Sample findings only · date and session do not alter demo numbers"
                else "နမူနာအဖြေများသာ · ရက်စွဲ/အကြိမ်ပြောင်းလည်း နမူနာဂဏန်းမပြောင်းပါ",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }

        SummarySectionTitle(if (en) "CURRENT OCCASION" else "လက်ရှိအကြိမ်", "04")

        SummaryFinding("02", if (en) "Highest observed frequencies" else "အထွက်အများဆုံး",
            onOpenDetail) {
            SummaryFacts(en, listOf(
                fact("ဂဏန်းတွဲ", "Pair", "21·73  ×6", "21·73  ×6"),
                fact("တစ်လုံးဂဏန်း", "Digit", "1·7  ×45", "1·7  ×45"),
                fact("စုံ/မ", "Parity", "စုံမ  ×13", "Even/odd  ×13"),
            ))
            SummaryFacts(en, listOf(
                fact("အထူးအုပ်စု", "Special", "ညီအကို  ×43", "Siblings  ×43"),
                fact("ထိပ်", "Tens", "2  ×12", "2  ×12"),
                fact("ပိတ်", "Ones", "1  ×9", "1  ×9"),
            ))
        }

        SummaryFinding("03", if (en) "Matching parity pattern" else "စုံ/မ ပုံစံတူ",
            onOpenDetail) {
            SummaryFacts(en, listOf(
                fact("ပုံစံတူအပတ်", "Matched weeks", "3 ပတ်", "3 weeks"),
                fact("နောက်ထွက်အများဆုံး", "Top next class", "စုံမ  ×2", "Even/odd  ×2"),
            ))
        }

        SummaryFinding("06", if (en) "Longest ongoing absences" else "ပျောက်ဂဏန်း / ဂဏန်းတွဲ",
            onOpenDetail) {
            SummaryFacts(en, listOf(
                fact("မနက် · ဂဏန်း / တွဲ", "AM · digit / pair", "3 ×16 · 93 ×45", "3 ×16 · 93 ×45"),
                fact("ညနေ · ဂဏန်း / တွဲ", "PM · digit / pair", "1 ×8 · 56 ×37", "1 ×8 · 56 ×37"),
                fact("နှစ်ကြိမ် · ဂဏန်း / တွဲ", "ALL · digit / pair", "0 ×6 · 47 ×22", "0 ×6 · 47 ×22"),
            ))
        }

        SummaryFinding("08", if (en) "Previous-result comparison" else "ရှေ့ထွက်ဂဏန်းတိုက်စစ်",
            onOpenDetail) {
            Text(
                if (en) "References: 21 + 66" else "ရှေ့ထွက်ဂဏန်း 21 + 66",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryFacts(en, listOf(
                fact("BOTH", "BOTH", "35  ×1", "35  ×1"),
                fact("21 တစ်ခုတည်း", "First · 21", "12  ×2", "12  ×2"),
                fact("66 တစ်ခုတည်း", "Second · 66", "57  ×1", "57  ×1"),
            ))
        }

        SummarySectionTitle(if (en) "THIS WEEK" else "ယခုအပတ်", "02")

        SummaryFinding("05", if (en) "New / old digit flow" else "အသစ် / အဟောင်း စီးဆင်းမှု",
            onOpenDetail) {
            SummaryFacts(en, listOf(
                fact("နောက်ဆုံး · 66", "Latest · 66", "6 အဟောင်း", "6 old"),
                fact("ကျန်ဂဏန်း", "Unseen digits", "8·9", "8·9"),
                fact("ပြီးစီးအကြိမ်", "Recorded", "9 / 10", "9 / 10"),
            ))
            // Tiny occupancy rail: one bar per Mon AM through Fri PM slot.
            // This is not a second data formula and does not encode odds.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(10) { index ->
                    Surface(
                        modifier = Modifier.weight(1f).height(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = if (index < 9) SummaryWine else SummaryLine,
                    ) {}
                }
            }
        }

        SummaryFinding("07", if (en) "Special groups this week" else "ယခုအပတ် အထူးအုပ်စု",
            onOpenDetail) {
            SummaryFacts(en, listOf(
                fact("အပူး", "Doubles", "2", "2"),
                fact("ညီအကို", "Siblings", "3", "3"),
                fact("ပါဝါ", "Power", "0", "0"),
                fact("နက္ခတ်", "Astrology", "2", "2"),
                fact("ပြန်ကျ", "Repeat", "0", "0"),
            ))
        }

        SummarySectionTitle(if (en) "PREVIOUS WEEK" else "ပြီးခဲ့တဲ့အပတ်", "02")

        SummaryFinding("04", if (en) "Previous-week overview" else "ပြီးခဲ့တဲ့အပတ်စာရင်း",
            onOpenDetail) {
            SummaryFacts(en, listOf(
                fact("အများဆုံးဂဏန်း", "Top digit", "7  ×5", "7  ×5"),
                fact("ပျောက်ဂဏန်း", "Unseen", "0·9", "0·9"),
                fact("စုံ/မ အများဆုံး", "Top parity", "မမ  ×4", "Odd/odd  ×4"),
            ))
            SummaryFacts(en, listOf(
                fact("အထူးအများဆုံး", "Top special", "ညီအကို  ×3", "Siblings  ×3"),
                fact("ပြန်ကျ", "Repeat events", "1 ကြိမ်", "1 time"),
            ))
        }

        SummaryFinding("01", if (en) "Missing-digit returns" else "ပျောက်ဂဏန်း ပြန်ပေါ်မှု",
            onOpenDetail) {
            SummaryFacts(en, listOf(
                fact("ယခင်ပျောက်", "Prior unseen", "0·9", "0·9"),
                fact("ယခုအပတ်", "This week", "0 ပြန်ပေါ် · 9 မပေါ်", "0 returned · 9 unseen"),
            ))
            SummaryFacts(en, listOf(
                fact("0 ပထမပြန်ပေါ်", "First return of 0", "03 · တနင်္လာညနေ", "03 · Mon PM"),
                fact("သမိုင်းတွဲထွက်", "Historical companion", "3  ×3", "3  ×3"),
            ))
        }

        Text(
            if (en) "Example values, not a forecast. Tap a row for its existing Analysis detail."
            else "နမူနာဂဏန်းများသာဖြစ်ပြီး ခန့်မှန်းချက်မဟုတ်ပါ။ အသေးစိတ်ကို စစ်ဆေးမှုတွင် ကြည့်နိုင်သည်။",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 7.dp),
        )
    }
}

@Composable
private fun SummaryCompactDateBar(
    date: LocalDate,
    session: DrawSession,
    en: Boolean,
    onPickDate: () -> Unit,
    onChangeSession: (DrawSession) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SummaryWine,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                date.toString(),
                modifier = Modifier.weight(1f).clickable(onClick = onPickDate)
                    .padding(vertical = 5.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White,
            )
            listOf(
                DrawSession.MORNING to (if (en) "AM" else "မနက်"),
                DrawSession.EVENING to (if (en) "PM" else "ညနေ"),
            ).forEach { (key, label) ->
                val selected = session == key
                Surface(
                    onClick = { onChangeSession(key) },
                    color = if (selected) Color(0xFFFFE1EB) else Color.White.copy(alpha = .14f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (selected) SummaryWine else Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySectionTitle(label: String, count: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SummaryWine)
        HorizontalDivider(modifier = Modifier.weight(1f), color = SummaryLine)
        Text(count, fontSize = 10.sp, color = SummaryWine)
    }
}

@Composable
private fun SummaryFinding(
    id: String,
    title: String,
    onOpenDetail: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenDetail(id) },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, SummaryLine),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = SummaryWine, shape = RoundedCornerShape(5.dp)) {
                    Text(
                        "A-" + id, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                }
                Text(
                    title, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("›", fontSize = 17.sp, color = SummaryWine)
            }
            content()
        }
    }
}

@Composable
private fun SummaryFacts(en: Boolean, items: List<SummaryFact>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Surface(
                modifier = Modifier.weight(1f),
                color = SummaryRose,
                shape = RoundedCornerShape(6.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        if (en) item.enLabel else item.mmLabel,
                        fontSize = 10.sp,
                        color = SummaryWine.copy(alpha = .85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (en) item.enValue else item.mmValue,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = SummaryWine, lineHeight = 15.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
