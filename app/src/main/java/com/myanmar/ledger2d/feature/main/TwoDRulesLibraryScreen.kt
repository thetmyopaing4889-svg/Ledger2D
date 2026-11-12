@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Read-only, offline-first Rules library. Its data source is the user's
 * verbatim original document. Search, categories and numeric indexing never
 * replace or paraphrase source instructions.
 */
@Composable
internal fun TwoDRulesLibraryScreen(
    en: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    fun tr(mm: String, english: String) = if (en) english else mm
    val catalog = remember(context) {
        runCatching {
            val original = context.assets.open("two_d_rules_source.txt")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            TwoDRulesCatalog.fromSource(original)
        }.getOrNull()
    }
    val prefs = remember(context) {
        context.getSharedPreferences("two_d_rules_favorites", Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet().orEmpty())
    }

    var mode by rememberSaveable { mutableStateOf("rule") }
    var category by rememberSaveable { mutableStateOf("digits") }
    var groupId by rememberSaveable { mutableStateOf("s2-column-0") }
    var number by rememberSaveable { mutableStateOf("27") }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (dark) Color(0xFFD8B785) else Color(0xFF6B3349)
    val card = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh
        else Color(0xFFF8F5EF)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val categoryGroups = catalog?.groupsIn(category).orEmpty()
    val chosenGroup = categoryGroups.firstOrNull { it.id == groupId }
        ?: categoryGroups.firstOrNull()
    val entries = when {
        catalog == null -> emptyList()
        onlyFavorites -> catalog.allItems.filter { it.id in favorites }
        query.isNotBlank() -> catalog.search(query)
        mode == "number" -> catalog.byNumber(number)
        else -> chosenGroup?.items.orEmpty()
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr("မူဝါဒ စာကြည့်တိုက်", "Rules library"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr("မူရင်းစာသားအတိုင်း · ရှာဖွေ · Swipe ဖတ်ရှု", "Original wording · Find · Swipe"),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
            Icon(Icons.Default.AutoStories, null, tint = accent)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onlyFavorites = false },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = if (query.isNotBlank()) {
                { IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, tr("ရှာဖွေမှုရှင်းရန်", "Clear search"))
                } }
            } else null,
            placeholder = { Text(tr("ဂဏန်း၊ မူအမည်၊ BK ရှာရန်", "Search number, rule, BK")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("rule" to tr("မူအလိုက်", "By rule"),
                "number" to tr("ဂဏန်းအလိုက်", "By number")).forEach { (value, label) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { mode = value; query = ""; onlyFavorites = false },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = if (mode == value) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (catalog == null) {
            Text(
                tr("မူရင်း Document ကို ဖတ်မရပါ။ မူအတု မပြပါ။",
                    "Unable to load the original document. No substitute rules are shown."),
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            if (query.isBlank() && !onlyFavorites) {
                if (mode == "rule") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("မူအုပ်စု", "Category"),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            TextButton(onClick = { categoryMenu = true }) {
                                Text(tr("အုပ်စုအားလုံး", "All groups"))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = categoryMenu,
                                onDismissRequest = { categoryMenu = false },
                            ) {
                                ruleCategories.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(if (en) c.en else c.mm) },
                                        onClick = {
                                            category = c.key
                                            groupId = catalog.groupsIn(c.key).firstOrNull()?.id.orEmpty()
                                            categoryMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(ruleCategories, key = { it.key }) { c ->
                            FilterChip(
                                selected = category == c.key,
                                onClick = {
                                    category = c.key
                                    groupId = catalog.groupsIn(c.key).firstOrNull()?.id.orEmpty()
                                },
                                label = { Text(if (en) c.en else c.mm, maxLines = 1) },
                            )
                        }
                    }
                    if (categoryGroups.size > 1) {
                        Box {
                            OutlinedButton(
                                onClick = { groupMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    chosenGroup?.title.orEmpty(),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                            DropdownMenu(
                                expanded = groupMenu,
                                onDismissRequest = { groupMenu = false },
                            ) {
                                categoryGroups.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group.title, maxLines = 3) },
                                        onClick = {
                                            groupId = group.id
                                            groupMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            chosenGroup?.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all { it in '0'..'9' })
                                number = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("0–9 သို့မဟုတ် 00–99 ရိုက်ရွေးပါ", "Enter 0–9 or 00–99")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("8", "22", "27").forEach { suggestion ->
                            FilterChip(
                                selected = number == suggestion,
                                onClick = { number = suggestion },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                    Text(
                        tr("မူအုပ်စုအားလုံးမှ တိုက်ရိုက်ဆိုင်သောမူနှင့် ဆက်စပ်မူများကို ရှာပြမည်။",
                            "Shows direct triggers and associated rules across all categories."),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        onlyFavorites -> tr("မှတ်သားထားသော မူများ", "Saved rules")
                        query.isNotBlank() -> tr("ရှာဖွေမှု ရလဒ်", "Search results")
                        mode == "number" -> tr("ဂဏန်း " + number + " နှင့်ဆိုင်သော မူများ",
                            "Rules related to " + number)
                        else -> tr("မူတစ်ချက်ချင်း", "One rule at a time")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onlyFavorites = !onlyFavorites }) {
                    Icon(
                        if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                        null, Modifier.size(18.dp), tint = accent,
                    )
                    Text(tr("သိမ်းထား", "Saved"))
                }
            }

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (onlyFavorites) tr("မှတ်သားထားသော မူ မရှိသေးပါ။", "No saved rules yet.")
                        else if (mode == "number" && query.isBlank())
                            tr("ဒီဂဏန်းနှင့် တိုက်ရိုက်ဆိုင်သော မူ မတွေ့ပါ။", "No indexed rule for this number.")
                        else tr("မူ မတွေ့ပါ။", "No matching rules."),
                        color = muted,
                    )
                }
            } else {
                // This key recreates the pager when the underlying reading
                // collection changes, preventing a stale page index.
                key(mode, category, chosenGroup?.id, number, query, onlyFavorites) {
                    val pager = rememberPagerState(pageCount = { entries.size })
                    val index = pager.currentPage.coerceIn(0, entries.lastIndex)
                    val item = entries[index]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            tr("ဘယ် / ညာ ဆွဲဖတ်ပါ", "Swipe left or right"),
                            color = muted, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            (index + 1).toString() + " / " + entries.size,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        pageSpacing = 10.dp,
                    ) { page ->
                        val rule = entries[page]
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(19.dp),
                            color = card,
                            tonalElevation = 1.dp,
                        ) {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                    .padding(horizontal = 17.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(15.dp),
                            ) {
                                Text(
                                    rule.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                )
                                HorizontalDivider(color = accent.copy(alpha = 0.22f))
                                Text(
                                    rule.original,
                                    fontSize = 20.sp,
                                    lineHeight = 34.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    tr("ရင်းမြစ် • အပိုင်း (", "Original document • Section ") +
                                        rule.section + if (en) "" else ")",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = muted,
                                )
                                if (mode == "number" && query.isBlank()) {
                                    Text(
                                        if (number in rule.sourceNumbers)
                                            tr("မူစတင်ဂဏန်း", "Direct trigger")
                                        else tr("ဆက်စပ်ဂဏန်း", "Related number"),
                                        color = muted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(
                            enabled = pager.currentPage > 0,
                            onClick = {
                                scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                            },
                        ) {
                            Icon(Icons.Default.ChevronLeft,
                                tr("ယခင်မူ", "Previous rule"))
                        }
                        Text(
                            tr("မူရင်းစာသား • မပြင်ထား", "Unmodified source text"),
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                        )
                        IconButton(
                            enabled = pager.currentPage < entries.lastIndex,
                            onClick = {
                                scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            },
                        ) {
                            Icon(Icons.Default.ChevronRight,
                                tr("နောက်မူ", "Next rule"))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = {
                                val updated = if (item.id in favorites) favorites - item.id
                                    else favorites + item.id
                                favorites = updated
                                prefs.edit().putStringSet("ids", updated).apply()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                if (item.id in favorites) Icons.Default.Star
                                else Icons.Default.StarBorder,
                                null, Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(tr("မှတ်သား", "Save"))
                        }
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(item.original)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("ကူးယူ", "Copy"))
                        }
                    }
                }
            }
            Text(
                tr("စာတမ်းပါ မူများသည် အတည်ပြုထားသော ရလဒ် သို့မဟုတ် တိကျမှုနှုန်း မဟုတ်ပါ။",
                    "Source claims are not verified results or accuracy guarantees."),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 2,
            )
        }
    }
}
