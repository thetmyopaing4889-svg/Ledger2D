@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import android.content.Context
import com.myanmar.ledger2d.core.design.AppColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class RulesShelfCategory(val id: String, val mm: String, val en: String)
private val shelfCategories = listOf(
    RulesShelfCategory("digits", "0–9 ဂဏန်းမူ", "0–9 digit rules"),
    RulesShelfCategory("double", "အပူးမူ", "Doubles"),
    RulesShelfCategory("born", "မွေးဂဏန်း", "Born pairs"),
    RulesShelfCategory("groups", "အုပ်စု / ဘရိတ်", "Groups / BK"),
    RulesShelfCategory("days", "နေ့ / အချိန်", "Days / sessions"),
    RulesShelfCategory("special", "အသေ / အထူး", "Cut / special"),
)

private data class BookColors(
    val page: Color,
    val paper: Color,
    val panel: Color,
    val ink: Color,
    val softInk: Color,
    val accent: Color,
    val border: Color,
    val spine: Color,
)

@Composable
private fun readerColors(): BookColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) BookColors(
        page = Color(0xFF181114),
        paper = Color(0xFF24191E),
        panel = Color(0xFF3B252E),
        ink = Color(0xFFFFF5F7),
        softInk = Color(0xFFD8BFC7),
        accent = Color(0xFFFF9CB3),
        border = Color(0xFF6E394B),
        spine = AppColors.Primary,
    ) else BookColors(
        page = Color(0xFFFFF6F8),
        paper = Color.White,
        panel = AppColors.Blush,
        ink = AppColors.Ink,
        softInk = Color(0xFF6D5D64),
        accent = AppColors.Accent,
        border = Color(0xFFF3D2DE),
        spine = AppColors.Primary,
    )

/**
 * A source-accurate, book-first Rules library.
 *
 * Book mode: ORIGINAL titled source subsections -> book-cover shelf -> same-screen
 * expanded paper with the COMPLETE source text. No new navigation destination.
 *
 * Number mode: the original digit-first design. Select a digit and read ALL
 * five original table-column rules together; doubles / born pairs have their
 * own category and selectors. Never reinterpret the source percentages.
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
    val colors = readerColors()
    val source = remember(context) {
        runCatching {
            context.assets.open("two_d_rules_source.txt")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }
    // Source remains immutable and powers the existing number-first catalog.
    // The curated reader asset only controls book titles, tables and examples.
    val readerSource = remember(context) {
        runCatching {
            context.assets.open("two_d_rules_reader.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }
    val library = remember(source, readerSource) {
        if (source == null || readerSource == null) null else runCatching {
            Pair(TwoDReaderCatalog.booksFromJson(readerSource), TwoDRulesCatalog.fromSource(source))
        }.getOrNull()
    }
    val books = library?.first.orEmpty()
    val catalog = library?.second
    val prefs = remember(context) {
        context.getSharedPreferences("two_d_rules_favorites", Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet().orEmpty())
    }
    // Older builds saved the 16 source-topic cover IDs. Convert those IDs to
    // the nine category shelves rather than silently losing saved references.
    LaunchedEffect(books) {
        if (books.isNotEmpty()) {
            val upgraded = TwoDReaderCatalog.migrateSavedBookIds(favorites, books)
            if (upgraded != favorites) {
                favorites = upgraded
                prefs.edit().putStringSet("ids", upgraded).apply()
            }
        }
    }
    fun toggleFavorite(id: String) {
        val changed = if (id in favorites) favorites - id else favorites + id
        favorites = changed
        prefs.edit().putStringSet("ids", changed).apply()
    }

    var view by rememberSaveable { mutableStateOf("books") }
    var openedBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var numberCategory by rememberSaveable { mutableStateOf("digits") }
    var digit by rememberSaveable { mutableStateOf("0") }
    var doublePair by rememberSaveable { mutableStateOf("00") }
    var bornPair by rememberSaveable { mutableStateOf("01") }
    var bornInput by rememberSaveable { mutableStateOf("01") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Only an explicit search navigation highlights/scrolls the opened book.
    var focusedRuleIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var highlightedQuery by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<String?>(null) }
    val openedBook = books.firstOrNull { it.id == openedBookId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = sheetState,
            containerColor = colors.paper,
        ) {
            when (sheet) {
                "search" -> {
                    Text(
                        tr("မူဝါဒ ရှာဖွေရန်", "Search rules"),
                        color = colors.ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(80) },
                        label = { Text(tr("မူခေါင်းစဉ်၊ စာသား သို့မဟုတ် ဂဏန်း",
                            "Rule title, content, or number")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close,
                                        tr("ရှာဖွေမှုရှင်းရန်", "Clear search"))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    val matches = remember(books, searchQuery) {
                        TwoDReaderSearch.find(books, searchQuery)
                    }
                    if (searchQuery.isNotBlank()) {
                        Text(
                            tr("တွေ့ရှိသော မူ ${matches.size} ခု",
                                "${matches.size} matching rules"),
                            color = colors.softInk,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        if (searchQuery.isBlank()) {
                            item {
                                Text(
                                    tr("မူခေါင်းစဉ်၊ စာသား သို့မဟုတ် 77 ကဲ့သို့ ဂဏန်းတစ်ခု ရိုက်ရှာပါ။",
                                        "Type a rule title, phrase, or a number such as 77."),
                                    modifier = Modifier.padding(20.dp),
                                    color = colors.softInk,
                                )
                            }
                        } else {
                            listItems(matches, key = { "${it.bookId}:${it.entryIndex}" }) { hit ->
                                Surface(
                                    onClick = {
                                        view = "books"
                                        openedBookId = hit.bookId
                                        focusedRuleIndex = hit.entryIndex
                                        highlightedQuery = searchQuery.trim()
                                        sheet = null
                                        keyboard?.hide()
                                    },
                                    color = Color.Transparent,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(
                                            horizontal = 20.dp, vertical = 12.dp,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.MenuBook, null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(20.dp))
                                        Column(
                                            Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Text(
                                                hit.bookTitle,
                                                color = colors.accent,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            ReaderDigitsText(
                                                value = hit.ruleTitle,
                                                color = colors.ink,
                                                fontSize = 16.sp,
                                                lineHeight = 24.sp,
                                                highlightQuery = searchQuery,
                                            )
                                            if (hit.excerpt.isNotBlank()) {
                                                ReaderDigitsText(
                                                    value = hit.excerpt,
                                                    color = colors.softInk,
                                                    fontSize = 14.sp,
                                                    lineHeight = 22.sp,
                                                    highlightQuery = searchQuery,
                                                )
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, null,
                                            tint = colors.softInk)
                                    }
                                }
                                HorizontalDivider(color = colors.border)
                            }
                            if (matches.isEmpty()) {
                                item {
                                    Text(tr("သက်ဆိုင်သော မူ မတွေ့ပါ။", "No matching rule."),
                                        modifier = Modifier.padding(20.dp),
                                        color = colors.softInk)
                                }
                            }
                        }
                    }
                }
                "saved" -> {
                    Text(
                        tr("မှတ်သားထားသော မူဝါဒများ", "Saved rules"),
                        color = colors.ink,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    val saved = books.filter { "book:" + it.id in favorites }
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 450.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                    ) {
                        if (saved.isEmpty()) {
                            item {
                                Text(tr("မှတ်သားထားသော စာအုပ် မရှိသေးပါ။",
                                    "No saved books yet."),
                                    color = colors.softInk, modifier = Modifier.padding(20.dp))
                            }
                        }
                        listItems(saved, key = { it.id }) { book ->
                            Surface(
                                onClick = {
                                    view = "books"
                                    openedBookId = book.id
                                    focusedRuleIndex = null
                                    highlightedQuery = ""
                                    sheet = null
                                }, color = Color.Transparent,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Bookmark, null, tint = colors.accent)
                                    Text(book.title, modifier = Modifier.weight(1f),
                                        color = colors.ink)
                                }
                            }
                            HorizontalDivider(color = colors.border)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.background(colors.page)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // First visible layer: title plus the compact opt-in search action.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AutoStories, null, tint = colors.accent,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                tr("မူဝါဒ ရွေးချယ်ရန်", "Choose a rules reference"),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = { sheet = "search" },
                shape = RoundedCornerShape(10.dp),
                color = colors.panel,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(tr("ရှာဖွေရန်", "Search"), color = colors.accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Search,
                        tr("မူဝါဒ ရှာဖွေရန်", "Search"),
                        tint = colors.accent, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { sheet = "saved" }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.BookmarkBorder,
                    tr("မှတ်သားထားသော မူများ", "Saved rules"),
                    tint = colors.accent, modifier = Modifier.size(21.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(colors.panel, RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "books" to tr("မူဝါဒအလိုက်", "By rule"),
                "numbers" to tr("ဂဏန်းအလိုက်", "By number"),
            ).forEach { (mode, label) ->
                Surface(
                    onClick = { view = mode },
                    color = if (view == mode) colors.accent else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        Modifier.height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (view == mode) colors.paper else colors.ink,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (library == null) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = colors.paper,
            ) {
                Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                    Text(tr("မူရင်းစာတမ်းကို ဖတ်မရပါ။ မူအတု မပြပါ။",
                        "Original source unavailable. No invented rules displayed."),
                        color = colors.ink)
                }
            }
        } else if (view == "books") {
            if (openedBook == null) {
                // Shelves of ORIGINAL source subsection titles, not synthetic
                // mini-rules or five artificially split table columns.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 15.dp, top = 3.dp),
                ) {
                    gridItems(books, key = { it.id }) { book ->
                        RulesBookCover(
                            book = book, colors = colors,
                            onClick = {
                                openedBookId = book.id
                                focusedRuleIndex = null
                                highlightedQuery = ""
                            },
                        )
                    }
                }
            } else {
                // The cover opens INTO the same screen, replacing the shelf.
                RulesOpenedBook(
                    book = openedBook,
                    colors = colors,
                    en = en,
                    favorite = "book:" + openedBook.id in favorites,
                    onClose = {
                        openedBookId = null
                        focusedRuleIndex = null
                        highlightedQuery = ""
                    },
                    highlightedQuery = highlightedQuery,
                    focusedRuleIndex = focusedRuleIndex,
                    onFavorite = { toggleFavorite("book:" + openedBook.id) },
                    onCopy = { clipboard.setText(AnnotatedString(openedBook.originalText)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        } else {
            val catalog = library.second
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    shelfCategories.forEach { category ->
                        Surface(
                            onClick = { numberCategory = category.id },
                            color = if (numberCategory == category.id)
                                colors.accent else colors.panel,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                if (en) category.en else category.mm,
                                color = if (numberCategory == category.id)
                                    colors.paper else colors.ink,
                                modifier = Modifier.padding(
                                    horizontal = 13.dp, vertical = 10.dp),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                when (numberCategory) {
                    "digits", "double" -> {
                        val doubleMode = numberCategory == "double"
                        val selected = if (doubleMode) doublePair else digit
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            (0..1).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    (0..4).forEach { col ->
                                        val n = row * 5 + col
                                        val option = if (doubleMode) "$n$n" else "$n"
                                        Surface(
                                            onClick = {
                                                if (doubleMode) doublePair = option
                                                else digit = option
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected == option) colors.accent
                                                else colors.panel,
                                        ) {
                                            Box(
                                                Modifier.height(43.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    option,
                                                    color = if (selected == option)
                                                        colors.paper else colors.ink,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "born" -> {
                        val triggerPairs = remember(catalog) {
                            catalog.groupsIn("born").flatMap { it.items }
                                .flatMap { it.sourceNumbers }.distinct().sorted()
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = bornInput,
                                onValueChange = { s ->
                                    if (s.length <= 2 && s.all { it in '0'..'9' }) {
                                        bornInput = s
                                        if (s.length == 2) bornPair = s
                                    }
                                },
                                singleLine = true,
                                label = { Text(tr("ဂဏန်းတွဲ", "Two digits")) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (bornInput.length == 2) bornPair = bornInput
                                    keyboard?.hide()
                                }),
                                modifier = Modifier.width(150.dp),
                            )
                            Text(
                                tr("မွေးဂဏန်းမူ ရှာရန်", "Look up pair triggers"),
                                color = colors.softInk,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            triggerPairs.forEach { pair ->
                                Surface(
                                    onClick = { bornPair = pair; bornInput = pair },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (bornPair == pair) colors.accent
                                        else colors.panel,
                                ) {
                                    Text(
                                        pair,
                                        color = if (bornPair == pair)
                                            colors.paper else colors.ink,
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp, vertical = 9.dp),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                val categoryGroups = catalog.groupsIn(numberCategory)
                val digitRules = if (numberCategory == "digits") {
                    categoryGroups.mapNotNull { g ->
                        g.items.firstOrNull { digit in it.sourceNumbers }
                            ?.let { g.title to it }
                    }
                } else emptyList()
                val pairRules = if (numberCategory == "double" ||
                    numberCategory == "born") {
                    val chosen = if (numberCategory == "double")
                        doublePair else bornPair
                    categoryGroups.flatMap { group ->
                        group.items.filter { chosen in it.sourceNumbers }
                            .map { group.title to it }
                    }
                } else emptyList()

                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(17.dp),
                    color = colors.paper,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    Column {
                        Column(
                            modifier = Modifier.weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            Text(
                                when (numberCategory) {
                                    "digits" -> tr("ဂဏန်း ", "Digit ") + digit
                                    "double" -> tr("အပူး ", "Double ") + doublePair
                                    "born" -> tr("မွေးဂဏန်း ", "Pair ") + bornPair
                                    else -> {
                                        val category = shelfCategories.first {
                                            it.id == numberCategory
                                        }
                                        if (en) category.en else category.mm
                                    }
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = colors.accent,
                            )
                            HorizontalDivider(color = colors.border)

                            if (numberCategory in listOf("digits", "double", "born")) {
                                val rules = if (numberCategory == "digits")
                                    digitRules else pairRules
                                if (rules.isEmpty()) {
                                    Text(
                                        tr("သက်ဆိုင်သော မူရင်းမူ မတွေ့ပါ။",
                                            "No original source rule for this selection."),
                                        color = colors.softInk,
                                    )
                                } else {
                                    rules.forEachIndexed { index, (title, entry) ->
                                        SourceRuleCell(
                                            title = title, body = entry.original,
                                            colors = colors,
                                        )
                                        if (index != rules.lastIndex)
                                            HorizontalDivider(color = colors.border)
                                    }
                                }
                            } else {
                                categoryGroups.forEachIndexed { index, group ->
                                    Text(
                                        group.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp, lineHeight = 28.sp,
                                        color = colors.accent,
                                    )
                                    group.items.forEach { entry ->
                                        ReaderDigitsText(
                                            value = entry.original,
                                            fontSize = 17.sp, lineHeight = 29.sp,
                                            color = colors.ink,
                                        )
                                    }
                                    if (index != categoryGroups.lastIndex)
                                        HorizontalDivider(color = colors.border)
                                }
                            }
                        }
                        HorizontalDivider(color = colors.border)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tr("မူရင်းစာသား", "Original source"),
                                color = colors.softInk,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    val lines = if (numberCategory == "digits") digitRules
                                    else pairRules
                                    val copyText = if (numberCategory in
                                        listOf("digits", "double", "born")) {
                                        lines.joinToString("\n\n") {
                                            it.first + "\n" + it.second.original
                                        }
                                    } else categoryGroups.joinToString("\n\n") { group ->
                                        group.title + "\n" +
                                            group.items.joinToString("\n") { it.original }
                                    }
                                    clipboard.setText(AnnotatedString(copyText))
                                },
                            ) {
                                Icon(Icons.Default.ContentCopy,
                                    tr("မူရင်းကူးယူရန်", "Copy original"),
                                    tint = colors.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesBookCover(
    book: TwoDReaderBook,
    colors: BookColors,
    onClick: () -> Unit,
) {
    // The SOURCE rule title is always displayed on the cover. Never use the
    // section number as its stand-in. The earlier weighted title could collapse
    // to zero height inside the two-column grid on small-screen devices.
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = 5.dp, topEnd = 14.dp,
            bottomStart = 5.dp, bottomEnd = 14.dp,
        ),
        color = remember(book.pastel) {
            Color(android.graphics.Color.parseColor(book.pastel))
        },
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0x33E11D48)),
        modifier = Modifier.fillMaxWidth().height(158.dp),
    ) {
        Row {
            Box(
                modifier = Modifier.width(6.dp).fillMaxHeight()
                    .background(colors.spine),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .padding(horizontal = 9.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.MenuBook, contentDescription = null,
                    tint = AppColors.Accent,
                    modifier = Modifier.size(27.dp),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = book.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFF352B39),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RulesOpenedBook(
    book: TwoDReaderBook,
    colors: BookColors,
    en: Boolean,
    favorite: Boolean,
    onClose: () -> Unit,
    highlightedQuery: String,
    focusedRuleIndex: Int?,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerListState = rememberLazyListState()
    // One header item precedes all source formulas in the book.
    LaunchedEffect(book.id, focusedRuleIndex) {
        focusedRuleIndex?.takeIf { it in book.rules.indices }?.let { index ->
            readerListState.scrollToItem(index + 1)
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp,
            bottomStart = 5.dp, bottomEnd = 18.dp),
        color = colors.paper,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row {
            Box(
                modifier = Modifier.width(9.dp).fillMaxHeight()
                    .background(colors.spine),
            )
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(44.dp).padding(start = 12.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.MenuBook, null,
                        tint = colors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            if (en) "Close book" else "စာအုပ်ပိတ်ရန်",
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                HorizontalDivider(color = colors.border)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = readerListState,
                    contentPadding = PaddingValues(
                        horizontal = 15.dp, vertical = 17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "book-header") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                book.title,
                                fontSize = 19.sp, lineHeight = 29.sp,
                                fontWeight = FontWeight.Bold, color = colors.accent,
                            )
                            HorizontalDivider(color = colors.border)
                        }
                    }
                    listItemsIndexed(
                        book.rules,
                        key = { index, rule -> "${index}:${rule.id}" },
                    ) { index, rule ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    if (focusedRuleIndex == index)
                                        colors.accent.copy(alpha = 0.10f)
                                    else Color.Transparent,
                                    RoundedCornerShape(9.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Avoid repeating the same heading already in source text.
                            if (book.rules.size > 1 &&
                                !rule.text.startsWith(rule.title) &&
                                !rule.text.startsWith("* " + rule.title)
                            ) {
                                ReaderDigitsText(
                                    value = rule.title,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    color = colors.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    highlightQuery = highlightedQuery,
                                )
                            }
                            if (rule.text.isNotBlank()) {
                                ReaderDigitsText(
                                    value = rule.text,
                                    color = colors.ink,
                                    fontSize = 16.sp,
                                    lineHeight = 27.sp,
                                    highlightQuery = highlightedQuery,
                                )
                            }
                            rule.table?.let {
                                ReaderRuleTable(it, colors, highlightedQuery)
                            }
                            if (rule.exampleLines.isNotEmpty()) {
                                ReaderRuleExample(
                                    rule.exampleLines, colors, highlightedQuery)
                            }
                            if (index < book.rules.lastIndex) {
                                HorizontalDivider(
                                    color = colors.border,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                    item(key = "book-end") { Spacer(Modifier.height(9.dp)) }
                }
                HorizontalDivider(color = colors.border)
                Row(
                    modifier = Modifier.fillMaxWidth().height(49.dp)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onFavorite) {
                        Icon(
                            if (favorite) Icons.Default.Bookmark
                                else Icons.Default.BookmarkBorder,
                            if (en) "Save book" else "စာအုပ်မှတ်သားရန်",
                            tint = colors.accent,
                        )
                    }
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy,
                            if (en) "Copy full book" else "မူအပြည့်အစုံ ကူးယူရန်",
                            tint = colors.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginalDigitColumn(book: TwoDRulesBook, colors: BookColors) {
    // This book uses ONE original formula-column title and all ten cells
    // from the original 0–9 source table. The exact cell wording is unchanged.
    val lines = book.lines.map { it.trim() }
    if (lines.size != 21) {
        book.lines.forEach { line ->
            Text(line, color = colors.ink, fontSize = 17.sp, lineHeight = 28.sp)
        }
        return
    }
    val digitHeading = lines.first()
    val rows = lines.drop(1).chunked(2)
    rows.forEachIndexed { index, row ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.panel,
            ) {
                Text(
                    digitHeading + " " + row.first(),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.accent,
                )
            }
            Text(
                row[1],
                modifier = Modifier.weight(1f),
                fontSize = 19.sp, lineHeight = 31.sp,
                color = colors.ink,
            )
        }
        if (index != rows.lastIndex) {
            HorizontalDivider(color = colors.border)
        }
    }
}

@Composable
private fun OriginalDoubleTable(book: TwoDRulesBook, colors: BookColors) {
    // Each original table row is three consecutive source lines.
    val lines = book.lines.map { it.trim() }
    if (lines.size != 33) {
        book.lines.forEach { line ->
            Text(line, color = colors.ink, fontSize = 17.sp, lineHeight = 28.sp)
        }
        return
    }
    val headings = lines.take(3)
    val rows = lines.drop(3).chunked(3)
    rows.forEachIndexed { index, row ->
        Surface(color = colors.panel, shape = RoundedCornerShape(9.dp)) {
            Text(
                headings[0] + "  " + row[0],
                color = colors.accent, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
        (1..2).forEach { col ->
            Text(
                headings[col],
                color = colors.softInk,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                row[col],
                fontSize = 18.sp, lineHeight = 30.sp,
                color = colors.ink,
            )
        }
        if (index != rows.lastIndex) {
            HorizontalDivider(color = colors.border)
        }
    }
}

@Composable
private fun SourceRuleCell(title: String, body: String, colors: BookColors) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 24.sp,
        )
        ReaderDigitsText(
            value = body,
            color = colors.ink,
            fontSize = 19.sp,
            lineHeight = 31.sp,
        )
    }
}

/** Bold all source digits; search terms also receive a readable warm highlight. */
@Composable
private fun ReaderDigitsText(
    value: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    highlightQuery: String = "",
) {
    val styled = remember(value, highlightQuery) {
        buildAnnotatedString {
            append(value)
            Regex("""[0-9၀-၉]+""").findAll(value).forEach { match ->
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    match.range.first, match.range.last + 1,
                )
            }
            TwoDReaderSearch.pattern(highlightQuery)?.findAll(value)?.forEach { match ->
                addStyle(
                    SpanStyle(
                        background = Color(0xFFFFDA8A),
                        color = Color(0xFF30202A),
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    match.range.first, match.range.last + 1,
                )
            }
        }
    }
    Text(
        text = styled,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}

/**
 * True compact table: 0–9 is always a two-column, ten-row chart rather than
 * ten stacked cards. Wider original tables scroll sideways without truncation.
 */
@Composable
private fun ReaderRuleTable(table: TwoDReaderTable, colors: BookColors, highlightQuery: String = "") {
    val twoColumns = table.headings.size == 2
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.border),
        color = colors.paper,
    ) {
        if (twoColumns) {
            Column {
                Row(Modifier.fillMaxWidth().background(colors.panel)) {
                    table.headings.forEachIndexed { i, heading ->
                        ReaderTableCell(
                            heading, colors.ink,
                            true, Modifier.weight(if (i == 0) 0.34f else 0.66f), highlightQuery,
                        )
                    }
                }
                table.rows.forEachIndexed { rowIndex, row ->
                    HorizontalDivider(color = colors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth().background(
                            if (rowIndex % 2 == 0) colors.paper else colors.panel.copy(alpha = 0.55f),
                        ),
                    ) {
                        row.forEachIndexed { i, value ->
                            ReaderTableCell(
                                if (value == "—") "—" else value,
                                if (i == 0) colors.accent else colors.ink,
                                i == 0,
                                Modifier.weight(if (i == 0) 0.34f else 0.66f), highlightQuery,
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.background(colors.panel)) {
                    table.headings.forEachIndexed { i, heading ->
                        ReaderTableCell(
                            heading, colors.ink, true,
                            Modifier.width(if (i == 0) 105.dp else 205.dp), highlightQuery,
                        )
                    }
                }
                table.rows.forEachIndexed { rowIndex, row ->
                    HorizontalDivider(color = colors.border)
                    Row(
                        modifier = Modifier.background(
                            if (rowIndex % 2 == 0) colors.paper else colors.panel.copy(alpha = 0.55f),
                        ),
                    ) {
                        row.forEachIndexed { i, value ->
                            ReaderTableCell(
                                value, if (i == 0) colors.accent else colors.ink,
                                i == 0,
                                Modifier.width(if (i == 0) 105.dp else 205.dp), highlightQuery,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTableCell(
    text: String,
    color: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    highlightQuery: String = "",
) {
    // Layout weight belongs to the parent Row; no source value is dropped.
    ReaderDigitsText(
        value = text,
        color = color,
        fontSize = if (emphasized) 14.sp else 15.sp,
        lineHeight = 23.sp,
        modifier = modifier.padding(horizontal = 9.dp, vertical = 9.dp),
        highlightQuery = highlightQuery,
    )
}

/** Markers [[number]] highlight only the key digit in Cherry red. */
@Composable
private fun ReaderRuleExample(lines: List<String>, colors: BookColors, highlightQuery: String = "") {
    val marker = remember { Regex("""\[\[(.*?)\]\]""") }
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = colors.panel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "ဥပမာ",
                color = colors.softInk,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            lines.forEach { line ->
                val formatted = remember(line, colors.accent, highlightQuery) {
                    buildAnnotatedString {
                        var cursor = 0
                        marker.findAll(line).forEach { match ->
                            append(line.substring(cursor, match.range.first))
                            withStyle(SpanStyle(
                                color = colors.accent,
                                fontWeight = FontWeight.ExtraBold,
                            )) { append(match.groupValues[1]) }
                            cursor = match.range.last + 1
                        }
                        append(line.substring(cursor))
                        val plain = line.replace("[[", "").replace("]]", "")
                        TwoDReaderSearch.pattern(highlightQuery)?.findAll(plain)
                            ?.forEach { match ->
                                addStyle(
                                    SpanStyle(
                                        background = Color(0xFFFFDA8A),
                                        color = Color(0xFF30202A),
                                        fontWeight = FontWeight.ExtraBold,
                                    ),
                                    match.range.first, match.range.last + 1,
                                )
                            }
                    }
                }
                Text(
                    text = formatted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = colors.ink,
                    fontSize = 16.sp,
                    lineHeight = 27.sp,
                )
            }
        }
    }
}
