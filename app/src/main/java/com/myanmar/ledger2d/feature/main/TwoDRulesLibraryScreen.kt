@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

private data class RuleColors(
    val page: Color, val panel: Color, val paper: Color, val ink: Color,
    val subtle: Color, val border: Color, val accent: Color, val selectedText: Color,
)

@Composable
private fun rulesColors(): RuleColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) RuleColors(
        Color(0xFF14191F), Color(0xFF242A33), Color(0xFF303641),
        Color(0xFFF7F1EC), Color(0xFFC9BBB6), Color(0xFF484F5B),
        Color(0xFFE2BE88), Color(0xFF29221C),
    ) else RuleColors(
        Color(0xFFF4F1ED), Color(0xFFEAE4DD), Color.White,
        Color(0xFF30262B), Color(0xFF74676B), Color(0xFFDED1CB),
        Color(0xFF70364C), Color.White,
    )
}

/**
 * Two genuinely different ways of reading the SAME unchanged source document:
 *
 * By rule: the WHOLE selected rule, including all ten digits of a source
 * table column, is shown together on one vertically scrolling paper.
 * By number: all related rules across every section are individual swipe pages.
 *
 * Search and rule/number pickers are opt-in sheets. The reader dominates the
 * screen instead of requiring the user to juggle three persistent filters.
 */
@Composable
internal fun TwoDRulesLibraryScreen(
    en: Boolean,
    modifier: Modifier = Modifier,
) {
    fun tr(mm: String, english: String) = if (en) english else mm
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val colors = rulesColors()
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
    fun toggleFavorite(id: String) {
        val updated = if (id in favorites) favorites - id else favorites + id
        favorites = updated
        prefs.edit().putStringSet("ids", updated).apply()
    }

    var view by rememberSaveable { mutableStateOf("rule") }
    var groupId by rememberSaveable { mutableStateOf("s2-column-0") }
    var number by rememberSaveable { mutableStateOf("27") }
    var numberInput by rememberSaveable { mutableStateOf("27") }
    var searchTerm by rememberSaveable { mutableStateOf("") }
    var focusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSheet by remember { mutableStateOf<String?>(null) }
    var expandedCategory by remember { mutableStateOf("digits") }
    val group = catalog?.groups?.firstOrNull { it.id == groupId }
        ?: catalog?.groups?.firstOrNull()
    val focused = catalog?.allItems?.firstOrNull { it.id == focusedItemId }
    val numberEntries = remember(catalog, number) { catalog?.byNumber(number).orEmpty() }
    val found = remember(catalog, searchTerm) {
        if (searchTerm.isBlank()) emptyList() else catalog?.search(searchTerm).orEmpty()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (activeSheet != null && catalog != null) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = colors.paper,
        ) {
            when (activeSheet) {
                "rules" -> {
                    Text(
                        tr("မူစာရင်း ရွေးရန်", "Choose a rule"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = colors.ink,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 510.dp),
                        contentPadding = PaddingValues(bottom = 25.dp),
                    ) {
                        ruleCategories.forEach { category ->
                            val groups = catalog.groupsIn(category.key)
                            item(key = "category-" + category.key) {
                                val opened = expandedCategory == category.key
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            expandedCategory =
                                                if (opened) "" else category.key
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (en) category.en else category.mm,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.ink,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        groups.size.toString(),
                                        color = colors.subtle,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Icon(
                                        if (opened) Icons.Default.ExpandLess
                                        else Icons.Default.ExpandMore,
                                        contentDescription = null, tint = colors.accent,
                                    )
                                }
                                HorizontalDivider(color = colors.border)
                            }
                            if (expandedCategory == category.key) {
                                items(groups, key = { it.id }) { candidate ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                groupId = candidate.id
                                                focusedItemId = null
                                                activeSheet = null
                                            }
                                            .background(
                                                if (candidate.id == groupId)
                                                    colors.panel else Color.Transparent
                                            )
                                            .padding(
                                                start = 32.dp, end = 18.dp,
                                                top = 12.dp, bottom = 12.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            candidate.title,
                                            color = colors.ink,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (candidate.id == groupId)
                                                FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (candidate.id == groupId)
                                            Icon(Icons.Default.Check, null, tint = colors.accent)
                                    }
                                }
                            }
                        }
                    }
                }
                "number" -> {
                    Text(
                        tr("ဂဏန်း ရွေးရန်", "Choose a number"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = colors.ink,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        OutlinedTextField(
                            value = numberInput,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it in '0'..'9' })
                                    numberInput = input
                            },
                            label = { Text(tr("0–9 သို့မဟုတ် 00–99", "0–9 or 00–99")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (numberInput.isNotBlank()) {
                                    number = numberInput
                                    focusedItemId = null
                                    activeSheet = null
                                    keyboard?.hide()
                                }
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        (0..1).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (0..4).forEach { col ->
                                    val d = row * 5 + col
                                    Surface(
                                        onClick = {
                                            numberInput = d.toString()
                                            number = numberInput
                                            focusedItemId = null
                                            activeSheet = null
                                            keyboard?.hide()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (numberInput == d.toString())
                                            colors.accent else colors.panel,
                                    ) {
                                        Box(contentAlignment = Alignment.Center,
                                            modifier = Modifier.height(44.dp)) {
                                            Text(d.toString(),
                                                color = if (numberInput == d.toString())
                                                    colors.selectedText else colors.ink,
                                                fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = {
                                if (numberInput.isNotBlank()) {
                                    number = numberInput
                                    focusedItemId = null
                                    activeSheet = null
                                    keyboard?.hide()
                                }
                            },
                            enabled = numberInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tr("ဒီဂဏန်း၏ မူများကို ကြည့်ရန်", "Read rules for this number"))
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
                "search" -> {
                    Text(
                        tr("မူဝါဒ ရှာဖွေရန်", "Find a rule"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = colors.ink,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { searchTerm = it },
                        label = { Text(tr("ဂဏန်း၊ မူအမည်၊ BK", "Number, rule name, BK")) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    ) {
                        if (searchTerm.isBlank()) {
                            item {
                                Text(
                                    tr("ဂဏန်း သို့မဟုတ် မူအမည် ရိုက်ရှာပါ။",
                                        "Enter a number or rule name."),
                                    color = colors.subtle,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        } else if (found.isEmpty()) {
                            item {
                                Text(
                                    tr("သက်ဆိုင်သော မူ မတွေ့ပါ။", "No matching rules."),
                                    color = colors.subtle,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        } else {
                            items(found, key = { it.id }) { result ->
                                val sourceGroup = catalog.groups.firstOrNull {
                                    candidate -> candidate.items.any { it.id == result.id }
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            focusedItemId = result.id
                                            activeSheet = null
                                            keyboard?.hide()
                                        }
                                        .padding(horizontal = 20.dp, vertical = 11.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        sourceGroup?.title ?: result.label,
                                        color = colors.accent,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        if (result.category == "digits")
                                            result.label + "  •  " + result.original
                                        else result.original,
                                        color = colors.ink,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                HorizontalDivider(color = colors.border)
                            }
                        }
                    }
                }
                "saved" -> {
                    Text(
                        tr("မှတ်သားထားသော မူများ", "Saved rules"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = colors.ink,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                    val savedGroups = catalog.groups.filter { "group:" + it.id in favorites }
                    val savedEntries = catalog.allItems.filter { it.id in favorites }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                        contentPadding = PaddingValues(bottom = 30.dp),
                    ) {
                        if (savedGroups.isEmpty() && savedEntries.isEmpty()) {
                            item {
                                Text(
                                    tr("မှတ်သားထားသော မူ မရှိသေးပါ။", "Nothing saved yet."),
                                    color = colors.subtle,
                                    modifier = Modifier.padding(20.dp),
                                )
                            }
                        }
                        items(savedGroups, key = { "g:" + it.id }) { saved ->
                            Text(
                                saved.title,
                                color = colors.ink,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        view = "rule"
                                        groupId = saved.id
                                        focusedItemId = null
                                        activeSheet = null
                                    }
                                    .padding(18.dp),
                            )
                            HorizontalDivider(color = colors.border)
                        }
                        items(savedEntries, key = { "i:" + it.id }) { saved ->
                            Text(
                                saved.original,
                                color = colors.ink,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        focusedItemId = saved.id
                                        activeSheet = null
                                    }
                                    .padding(18.dp),
                            )
                            HorizontalDivider(color = colors.border)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.background(colors.page)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // One compact search control, not an always-visible text input.
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = colors.panel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(43.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .clickable { activeSheet = "search" }
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(Icons.Default.Search,
                        tr("မူဝါဒ ရှာဖွေရန်", "Search rules"),
                        tint = colors.accent, modifier = Modifier.size(19.dp))
                    Text(
                        tr("မူဝါဒ ရှာဖွေရန်", "Search rules"),
                        color = colors.ink, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = { activeSheet = "saved" }) {
                    Icon(Icons.Default.StarBorder,
                        tr("မှတ်သားထားသော မူများ", "Saved rules"),
                        tint = colors.accent, modifier = Modifier.size(21.dp))
                }
            }
        }

        // Two actual reading models. Never show category tabs and a separate
        // group dropdown at the same time.
        Row(
            Modifier.fillMaxWidth().background(colors.panel, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("rule" to tr("မူအလိုက်", "By rule"),
                "number" to tr("ဂဏန်းအလိုက်", "By number")).forEach { (kind, text) ->
                Surface(
                    onClick = { view = kind; focusedItemId = null },
                    color = if (view == kind) colors.accent else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier.height(39.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text,
                            color = if (view == kind) colors.selectedText else colors.ink,
                            fontWeight = if (view == kind) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (catalog == null) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(17.dp), color = colors.paper,
            ) {
                Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        tr("မူရင်းစာတမ်းကို ဖတ်မရပါ။ မူအတု မပြပါ။",
                            "Original document unavailable. No invented rules shown."),
                        color = colors.ink,
                    )
                }
            }
        } else {
            Surface(
                onClick = {
                    activeSheet = if (view == "rule") "rules" else "number"
                    numberInput = number
                    expandedCategory = group?.category ?: "digits"
                },
                color = colors.panel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (view == "rule") Icons.Default.MenuBook else Icons.Default.Tag,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (view == "rule")
                            tr("မူစာရင်း ရွေးရန်", "Choose a rule")
                        else tr("ဂဏန်း ရွေးရန်", "Choose a number"),
                        color = colors.ink,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (view == "number")
                        Text(number, color = colors.accent, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 7.dp))
                    Icon(Icons.Default.ExpandMore, null, tint = colors.accent)
                }
            }

            if (focused != null) {
                // A search hit may come from any category, independent of the active view.
                val sourceGroup = catalog.groups.firstOrNull { g ->
                    g.items.any { it.id == focused.id }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    color = colors.paper,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { focusedItemId = null },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(Icons.Default.ArrowBack, tr("ပြန်သွားရန်", "Back"),
                                    tint = colors.accent)
                            }
                            Text(
                                tr("ရှာဖွေတွေ့ရှိသော မူ", "Selected search result"),
                                fontWeight = FontWeight.Bold,
                                color = colors.ink,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HorizontalDivider(color = colors.border)
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState())
                                .padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                sourceGroup?.title ?: focused.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = colors.accent,
                            )
                            if (focused.category == "digits") {
                                Text(
                                    tr("ဂဏန်း ", "Digit ") + focused.label,
                                    fontWeight = FontWeight.Bold, color = colors.subtle,
                                )
                            }
                            Text(
                                focused.original,
                                fontSize = 21.sp, lineHeight = 34.sp, color = colors.ink,
                            )
                            Text(
                                tr("မူရင်းစာတမ်း · အပိုင်း (", "Source · Section ") +
                                    focused.section + if (en) "" else ")",
                                color = colors.subtle,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        HorizontalDivider(color = colors.border)
                        RuleActionRow(
                            colors = colors,
                            favorite = focused.id in favorites,
                            onFavorite = { toggleFavorite(focused.id) },
                            onCopy = { clipboard.setText(AnnotatedString(focused.original)) },
                            en = en,
                        )
                    }
                }
            } else if (view == "rule") {
                val selected = group
                if (selected != null) {
                    // Entire original rule in ONE vertical reading scroll.
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        color = colors.paper,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Column {
                            Column(
                                modifier = Modifier.weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 17.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(15.dp),
                            ) {
                                Text(
                                    tr("မူရင်းစာတမ်း · အပိုင်း (", "Source · Section ") +
                                        selected.section + if (en) "" else ")",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.subtle,
                                )
                                Text(
                                    selected.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                )
                                HorizontalDivider(color = colors.border)
                                selected.items.forEachIndexed { idx, entry ->
                                    if (selected.category == "digits") {
                                        // A source table cell belongs to this original digit row.
                                        // No invented "if digit comes out" explanations.
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            Surface(
                                                color = colors.panel,
                                                shape = RoundedCornerShape(10.dp),
                                            ) {
                                                Text(
                                                    entry.label,
                                                    modifier = Modifier.padding(
                                                        horizontal = 15.dp, vertical = 9.dp),
                                                    fontSize = 23.sp, fontWeight = FontWeight.Bold,
                                                    color = colors.accent,
                                                )
                                            }
                                            Text(
                                                entry.original,
                                                modifier = Modifier.weight(1f)
                                                    .padding(top = 6.dp),
                                                fontSize = 20.sp, lineHeight = 31.sp,
                                                color = colors.ink,
                                            )
                                        }
                                    } else {
                                        Text(
                                            entry.original,
                                            color = colors.ink,
                                            fontSize = 20.sp, lineHeight = 33.sp,
                                        )
                                    }
                                    if (idx != selected.items.lastIndex)
                                        HorizontalDivider(color = colors.border.copy(alpha = .68f))
                                }
                            }
                            HorizontalDivider(color = colors.border)
                            RuleActionRow(
                                colors = colors,
                                favorite = ("group:" + selected.id) in favorites,
                                onFavorite = { toggleFavorite("group:" + selected.id) },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(
                                        selected.title + "\n\n" +
                                            selected.items.joinToString("\n") { entry ->
                                                if (selected.category == "digits")
                                                    entry.label + "\n" + entry.original
                                                else entry.original
                                            }
                                    ))
                                },
                                en = en,
                            )
                        }
                    }
                }
            } else {
                // By-number remains a swipeable SINGLE-RULE reader.
                if (numberEntries.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        color = colors.paper,
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                            Text(
                                tr("ဒီဂဏန်းနှင့် ဆက်စပ်မူ မတွေ့ပါ။",
                                    "No indexed rules for this number."),
                                color = colors.subtle,
                            )
                        }
                    }
                } else {
                    key(number) {
                        val pager = rememberPagerState(pageCount = { numberEntries.size })
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            pageSpacing = 8.dp,
                        ) { page ->
                            val entry = numberEntries[page]
                            val sourceGroup = catalog.groups.firstOrNull { g ->
                                g.items.any { it.id == entry.id }
                            }
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = colors.paper,
                                shape = RoundedCornerShape(18.dp),
                                shadowElevation = 3.dp,
                                border = BorderStroke(1.dp, colors.border),
                            ) {
                                Column {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 17.dp, vertical = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(15.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                tr("မူရင်း · အပိုင်း (", "Source · Section ") +
                                                    entry.section + if (en) "" else ")",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.subtle,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                (page + 1).toString() + " / " + numberEntries.size,
                                                color = colors.accent, fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Text(
                                            sourceGroup?.title ?: entry.label,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accent,
                                        )
                                        if (entry.category == "digits") {
                                            Text(
                                                tr("ဂဏန်း ", "Digit ") + entry.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold, color = colors.ink,
                                            )
                                        }
                                        HorizontalDivider(color = colors.border)
                                        Text(
                                            entry.original,
                                            fontSize = 22.sp, lineHeight = 35.sp,
                                            color = colors.ink,
                                        )
                                        Text(
                                            if (number in entry.sourceNumbers)
                                                tr("တိုက်ရိုက်ဆိုင်သော မူ", "Direct trigger")
                                            else tr("ဆက်စပ်မူ", "Related rule"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.subtle,
                                        )
                                    }
                                    HorizontalDivider(color = colors.border)
                                    RuleActionRow(
                                        colors = colors,
                                        favorite = entry.id in favorites,
                                        onFavorite = { toggleFavorite(entry.id) },
                                        onCopy = {
                                            clipboard.setText(AnnotatedString(entry.original))
                                        },
                                        en = en,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().height(43.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                enabled = pager.currentPage > 0,
                                onClick = {
                                    scope.launch {
                                        pager.animateScrollToPage(pager.currentPage - 1)
                                    }
                                },
                            ) {
                                Icon(Icons.Default.ChevronLeft,
                                    tr("ယခင်မူ", "Previous rule"), tint = colors.accent)
                            }
                            Text(
                                tr("ဘယ် / ညာ ဆွဲဖတ်ပါ", "Swipe left or right"),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.subtle,
                            )
                            IconButton(
                                enabled = pager.currentPage < numberEntries.lastIndex,
                                onClick = {
                                    scope.launch {
                                        pager.animateScrollToPage(pager.currentPage + 1)
                                    }
                                },
                            ) {
                                Icon(Icons.Default.ChevronRight,
                                    tr("နောက်မူ", "Next rule"), tint = colors.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleActionRow(
    colors: RuleColors,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    en: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (en) "Original wording" else "မူရင်းစာသား",
            style = MaterialTheme.typography.labelSmall,
            color = colors.subtle,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                if (en) "Save rule" else "မှတ်သားရန်",
                tint = colors.accent, modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy,
                if (en) "Copy original" else "မူရင်းကူးယူရန်",
                tint = colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}
