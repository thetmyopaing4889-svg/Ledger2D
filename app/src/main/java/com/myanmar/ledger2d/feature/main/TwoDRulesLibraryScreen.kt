@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class RulesCategory(val key: String, val mm: String, val en: String)

private val ruleCategories = listOf(
    RulesCategory("digits", "0–9 မူ", "0–9 rules"),
    RulesCategory("born", "မွေးဂဏန်း", "Pair triggers"),
    RulesCategory("double", "အပူးမူ", "Doubles"),
    RulesCategory("groups", "အုပ်စု / ဘရိတ်", "Groups / BK"),
    RulesCategory("days", "နေ့ / အချိန်", "Day / session"),
    RulesCategory("special", "အသေ / အထူး", "Cut / special"),
)

private data class RulesReaderPalette(
    val page: Color, val panel: Color, val paper: Color, val ink: Color,
    val subtle: Color, val outline: Color, val accent: Color, val activeText: Color,
)

@Composable
private fun rememberRulesPalette(): RulesReaderPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) RulesReaderPalette(
        page = Color(0xFF151A21), panel = Color(0xFF212730),
        paper = Color(0xFF2E343F), ink = Color(0xFFF6F1EA),
        subtle = Color(0xFFC5BAB7), outline = Color(0xFF444A54),
        accent = Color(0xFFE2BE88), activeText = Color(0xFF29221C),
    ) else RulesReaderPalette(
        page = Color(0xFFF4F1ED), panel = Color(0xFFEAE3DC),
        paper = Color(0xFFFFFFFF), ink = Color(0xFF30272B),
        subtle = Color(0xFF75686A), outline = Color(0xFFDDD0CB),
        accent = Color(0xFF70364C), activeText = Color.White,
    )
}

/**
 * A calm, text-first reader. The surrounding controls are physically grouped
 * into a search panel and a contextual browse panel; the original rule has the
 * only prominent reading card. Neither view modifies the source document.
 */
@Composable
internal fun TwoDRulesLibraryScreen(
    en: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val palette = rememberRulesPalette()
    fun tr(mm: String, english: String) = if (en) english else mm

    val catalog = remember(context) {
        runCatching {
            context.assets.open("two_d_rules_source.txt")
                .bufferedReader(Charsets.UTF_8).use { TwoDRulesCatalog.fromSource(it.readText()) }
        }.getOrNull()
    }
    val prefs = remember(context) {
        context.getSharedPreferences("two_d_rules_favorites", Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet().orEmpty())
    }

    var view by rememberSaveable { mutableStateOf("rule") }
    var category by rememberSaveable { mutableStateOf("digits") }
    var groupId by rememberSaveable { mutableStateOf("s2-column-0") }
    var number by rememberSaveable { mutableStateOf("27") }
    var query by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var allCategories by remember { mutableStateOf(false) }
    var allGroups by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val categoryGroups = catalog?.groupsIn(category).orEmpty()
    val group = categoryGroups.firstOrNull { it.id == groupId }
        ?: categoryGroups.firstOrNull()
    val entries = when {
        catalog == null -> emptyList()
        savedOnly -> catalog.allItems.filter { it.id in favorites }
        query.isNotBlank() -> catalog.search(query)
        view == "number" -> catalog.byNumber(number)
        else -> group?.items.orEmpty()
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(tr("မူရင်းစာသားအကြောင်း", "About the source")) },
            text = {
                Text(tr(
                    "မူဝါဒများကို မူရင်း 2D Document စာသားအတိုင်း ဖော်ပြထားသည်။ အပိုင်း (၇) ကို Better Data အတွက် ချန်ထားသည်။ စာတမ်းပါ ရာခိုင်နှုန်းများသည် အတည်ပြုထားသော တိကျမှုနှုန်း မဟုတ်ပါ။",
                    "The original 2D wording is preserved. Source section 7 is reserved for Better Data. Claimed percentages are not verified accuracy rates.",
                ))
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(tr("ပိတ်ရန်", "Close"))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .background(palette.page)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // SEARCH + VIEW. One shared control panel; no duplicate page title,
        // since the host toolbar already says "2D မူဝါဒ".
        Surface(
            shape = RoundedCornerShape(18.dp), color = palette.panel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("မူဝါဒ ရှာဖွေရန်", "Find a rule"),
                        color = palette.ink,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        modifier = Modifier.size(31.dp),
                        onClick = {
                            savedOnly = !savedOnly
                            query = ""
                            keyboard?.hide()
                        },
                    ) {
                        Icon(
                            if (savedOnly) Icons.Default.Star
                            else Icons.Default.StarBorder,
                            contentDescription = tr("မှတ်သားထားသော မူများ", "Saved rules"),
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(31.dp),
                        onClick = { showInfo = true },
                    ) {
                        Icon(
                            Icons.Default.InfoOutline,
                            contentDescription = tr("မူရင်းအကြောင်း", "Source information"),
                            tint = palette.subtle, modifier = Modifier.size(19.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; savedOnly = false },
                    placeholder = {
                        Text(tr("ဂဏန်း၊ မူအမည်၊ BK ရှာရန်", "Number, rule name, BK"),
                            fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null,
                            modifier = Modifier.size(20.dp), tint = palette.subtle)
                    },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close,
                                    tr("ရှာဖွေမှုရှင်းရန်", "Clear search"),
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    } else null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.ink),
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.paper,
                        unfocusedContainerColor = palette.paper,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth()
                        .background(palette.paper, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        "rule" to tr("မူအလိုက်", "By rule"),
                        "number" to tr("ဂဏန်းအလိုက်", "By number"),
                    ).forEach { (value, label) ->
                        Surface(
                            onClick = {
                                view = value
                                query = ""
                                savedOnly = false
                                keyboard?.hide()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (view == value) palette.accent else Color.Transparent,
                        ) {
                            Box(contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp)) {
                                Text(
                                    label,
                                    color = if (view == value) palette.activeText else palette.ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (view == value) FontWeight.Bold
                                        else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (catalog == null) {
            Surface(
                color = palette.paper,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        tr("မူရင်း Document ကို မဖတ်နိုင်သဖြင့် မူအတု မပြထားပါ။",
                            "Original source unavailable; no invented rules are displayed."),
                        color = palette.ink,
                    )
                }
            }
        } else {
            // CONTEXT. Only controls relevant to the current browsing mode
            // are visible. Search and Saved use the same reader, without
            // showing irrelevant group/number controls.
            if (query.isBlank() && !savedOnly) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = palette.panel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        if (view == "rule") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr("မူအုပ်စု ရွေးရန်", "Browse category"),
                                    color = palette.subtle,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Box {
                                    IconButton(
                                        onClick = { allCategories = true },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.GridView,
                                            tr("အုပ်စု ၆ ခုလုံး", "All six categories"),
                                            tint = palette.accent,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = allCategories,
                                        onDismissRequest = { allCategories = false },
                                    ) {
                                        ruleCategories.forEach { c ->
                                            DropdownMenuItem(
                                                text = { Text(if (en) c.en else c.mm) },
                                                onClick = {
                                                    category = c.key
                                                    groupId = catalog.groupsIn(c.key)
                                                        .firstOrNull()?.id.orEmpty()
                                                    allCategories = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(ruleCategories, key = { it.key }) { c ->
                                    val selected = category == c.key
                                    Surface(
                                        onClick = {
                                            category = c.key
                                            groupId = catalog.groupsIn(c.key)
                                                .firstOrNull()?.id.orEmpty()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) palette.accent else palette.paper,
                                        border = if (selected) null
                                            else BorderStroke(1.dp, palette.outline),
                                    ) {
                                        Text(
                                            if (en) c.en else c.mm,
                                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (selected) FontWeight.Bold
                                                else FontWeight.Medium,
                                            color = if (selected) palette.activeText else palette.ink,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            Box {
                                Surface(
                                    onClick = { allGroups = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = palette.paper,
                                    border = BorderStroke(1.dp, palette.outline),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                tr("မူခေါင်းစဉ်", "Selected rule"),
                                                color = palette.subtle,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Text(
                                                group?.title.orEmpty(),
                                                color = palette.ink,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Icon(Icons.Default.ExpandMore, null, tint = palette.accent)
                                    }
                                }
                                DropdownMenu(
                                    expanded = allGroups,
                                    onDismissRequest = { allGroups = false },
                                ) {
                                    categoryGroups.forEach { candidate ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(candidate.title,
                                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            },
                                            onClick = {
                                                groupId = candidate.id
                                                allGroups = false
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        tr("ဂဏန်း ရွေးရန်", "Choose a number"),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.ink,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        tr("0–9 သို့မဟုတ် 00–99", "Single digit or pair, 00–99"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.subtle,
                                    )
                                }
                                OutlinedTextField(
                                    value = number,
                                    onValueChange = { input ->
                                        if (input.length <= 2 && input.all { it in '0'..'9' })
                                            number = input
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold, color = palette.ink),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = palette.paper,
                                        unfocusedContainerColor = palette.paper,
                                        focusedBorderColor = palette.accent,
                                        unfocusedBorderColor = palette.outline,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.width(110.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ONE READING AREA. No duplicate hero, noisy actions, or loose
            // captions between the controls and source.
            val contextTitle = when {
                savedOnly -> tr("မှတ်သားထားသော မူများ", "Saved rules")
                query.isNotBlank() -> tr("ရှာဖွေမှုရလဒ်", "Search results")
                view == "number" -> tr("ဂဏန်း ", "Number ") + number
                else -> if (en) ruleCategories.first { it.key == category }.en
                    else ruleCategories.first { it.key == category }.mm
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    contextTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = palette.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotBlank() || savedOnly) {
                    TextButton(onClick = { query = ""; savedOnly = false }) {
                        Text(tr("ပြန်ကြည့်ရန်", "Browse"), color = palette.accent)
                    }
                }
            }

            if (entries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = palette.paper,
                    border = BorderStroke(1.dp, palette.outline),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                savedOnly -> tr("မှတ်သားထားသော မူ မရှိသေးပါ။", "No saved rules yet.")
                                query.isNotBlank() -> tr("မူ မတွေ့ပါ။", "No matching rules.")
                                else -> tr("ဒီဂဏန်းနှင့် ဆက်စပ်မူ မတွေ့ပါ။",
                                    "No indexed rules for this number.")
                            },
                            color = palette.subtle,
                        )
                    }
                }
            } else {
                key(view, category, group?.id, number, query, savedOnly) {
                    val pager = rememberPagerState(pageCount = { entries.size })
                    val item = entries[pager.currentPage.coerceIn(0, entries.lastIndex)]

                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        pageSpacing = 10.dp,
                    ) { pageIndex ->
                        val rule = entries[pageIndex]
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(20.dp),
                            color = palette.paper,
                            border = BorderStroke(1.dp, palette.outline),
                            shadowElevation = 6.dp,
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(palette.accent.copy(alpha = 0.07f))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            tr("မူရင်းစာတမ်း • အပိုင်း (", "Source · Section ") +
                                                rule.section + if (en) "" else ")",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.subtle,
                                        )
                                        Text(
                                            if (rule.category == "digits")
                                                tr("ဂဏန်း ", "Digit ") + rule.label
                                            else rule.label,
                                            color = palette.accent,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(9.dp),
                                        color = palette.panel,
                                    ) {
                                        Text(
                                            (pageIndex + 1).toString() + " / " + entries.size,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold, color = palette.accent,
                                        )
                                    }
                                }
                                HorizontalDivider(color = palette.outline)
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 19.dp, vertical = 18.dp),
                                    contentAlignment = if (rule.original.length < 75)
                                        Alignment.CenterStart else Alignment.TopStart,
                                ) {
                                    Text(
                                        rule.original,
                                        fontSize = 22.sp,
                                        lineHeight = 36.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.ink,
                                    )
                                }
                                HorizontalDivider(color = palette.outline)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 15.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
                                ) {
                                    Text(
                                        if (view == "number" && query.isBlank() && !savedOnly)
                                            if (number in rule.sourceNumbers)
                                                tr("တိုက်ရိုက်ဆိုင်သော မူ", "Direct trigger")
                                            else tr("ဆက်စပ်မူ", "Related rule")
                                        else tr("မူရင်းစာသား", "Original text"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.subtle,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        val updated = if (rule.id in favorites)
                                            favorites - rule.id else favorites + rule.id
                                        favorites = updated
                                        prefs.edit().putStringSet("ids", updated).apply()
                                    }) {
                                        Icon(
                                            if (rule.id in favorites) Icons.Default.Star
                                            else Icons.Default.StarBorder,
                                            tr("မှတ်သားရန်", "Save"),
                                            tint = palette.accent,
                                        )
                                    }
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(rule.original))
                                    }) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            tr("မူရင်းစာသား ကူးယူရန်", "Copy original text"),
                                            tint = palette.accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            onClick = {
                                scope.launch {
                                    pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                                }
                            },
                            enabled = pager.currentPage > 0,
                            color = palette.paper,
                            border = BorderStroke(1.dp, palette.outline),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                tr("ရှေ့မူ", "Previous rule"),
                                modifier = Modifier.padding(9.dp),
                                tint = if (pager.currentPage > 0) palette.accent else palette.subtle,
                            )
                        }
                        Text(
                            tr("ဘယ် / ညာ ဆွဲဖတ်ပါ", "Swipe left / right"),
                            color = palette.subtle,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Surface(
                            onClick = {
                                scope.launch {
                                    pager.animateScrollToPage(
                                        (pager.currentPage + 1).coerceAtMost(entries.lastIndex)
                                    )
                                }
                            },
                            enabled = pager.currentPage < entries.lastIndex,
                            color = palette.paper,
                            border = BorderStroke(1.dp, palette.outline),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                tr("နောက်မူ", "Next rule"),
                                modifier = Modifier.padding(9.dp),
                                tint = if (pager.currentPage < entries.lastIndex)
                                    palette.accent else palette.subtle,
                            )
                        }
                    }
                }
            }
        }
    }
}
