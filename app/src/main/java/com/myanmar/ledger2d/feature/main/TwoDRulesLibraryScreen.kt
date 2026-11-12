@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
        page = Color(0xFF171A20),
        paper = Color(0xFF29262B),
        panel = Color(0xFF343139),
        ink = Color(0xFFF8F0E8),
        softInk = Color(0xFFCDC0B9),
        accent = Color(0xFFE6B98B),
        border = Color(0xFF514850),
        spine = Color(0xFF825C58),
    ) else BookColors(
        page = Color(0xFFF5F1EA),
        paper = Color(0xFFFFFCF6),
        panel = Color(0xFFECE3D9),
        ink = Color(0xFF30282A),
        softInk = Color(0xFF786A6B),
        accent = Color(0xFF773B4C),
        border = Color(0xFFDDD0C3),
        spine = Color(0xFF995D62),
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
    val library = remember(source) {
        source?.let {
            runCatching {
                Pair(TwoDRulesBookLibrary.fromSource(it), TwoDRulesCatalog.fromSource(it))
            }.getOrNull()
        }
    }
    val books = library?.first.orEmpty()
    val catalog = library?.second
    val prefs = remember(context) {
        context.getSharedPreferences("two_d_rules_favorites", Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet().orEmpty())
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
                        onValueChange = { searchQuery = it },
                        label = { Text(tr("မူခေါင်းစဉ်၊ စာသား သို့မဟုတ် ဂဏန်း",
                            "Title, source text, or number")) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    val matches = if (searchQuery.isBlank()) books else books.filter {
                        it.originalText.contains(searchQuery.trim(), ignoreCase = true)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        listItems(matches, key = { it.id }) { book ->
                            Surface(
                                onClick = {
                                    view = "books"
                                    openedBookId = book.id
                                    sheet = null
                                    keyboard?.hide()
                                },
                                color = Color.Transparent,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Default.MenuBook, null, tint = colors.accent)
                                    Column(Modifier.weight(1f)) {
                                        Text(book.title, color = colors.ink,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium)
                                        Text(book.sectionTitle,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = colors.softInk,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.border)
                        }
                        if (matches.isEmpty()) {
                            item {
                                Text(tr("မူမတွေ့ပါ။", "No matching book."),
                                    modifier = Modifier.padding(20.dp),
                                    color = colors.softInk)
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
                            onClick = { openedBookId = book.id },
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
                    onClose = { openedBookId = null },
                    onFavorite = { toggleFavorite("book:" + openedBook.id) },
                    onCopy = { clipboard.setText(AnnotatedString(openedBook.originalText)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        } else {
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
                                        Text(
                                            entry.original,
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
    book: TwoDRulesBook,
    colors: BookColors,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 5.dp, topEnd = 15.dp,
            bottomStart = 5.dp, bottomEnd = 15.dp),
        color = colors.paper,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth().heightIn(min = 158.dp),
    ) {
        Row {
            Box(
                modifier = Modifier.width(8.dp).fillMaxHeight()
                    .background(colors.spine),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.MenuBook, null, tint = colors.accent,
                    modifier = Modifier.size(35.dp),
                )
                Text(
                    book.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "အပိုင်း (" + "၁၂၃၄၅၆၇၈၉".getOrNull(book.section - 1) + ")",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.softInk,
                )
            }
        }
    }
}

@Composable
private fun RulesOpenedBook(
    book: TwoDRulesBook,
    colors: BookColors,
    en: Boolean,
    favorite: Boolean,
    onClose: () -> Unit,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        .padding(start = 11.dp, end = 8.dp, top = 5.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AutoStories, null,
                        tint = colors.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (en) "Open book" else "ဖွင့်ထားသော စာအုပ်",
                        color = colors.softInk,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) {
                        Icon(Icons.Default.Close, null,
                            modifier = Modifier.size(18.dp), tint = colors.accent)
                        Spacer(Modifier.width(4.dp))
                        Text(if (en) "Close" else "ပိတ်ရန်",
                            color = colors.accent)
                    }
                }
                HorizontalDivider(color = colors.border)
                Column(
                    modifier = Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 15.dp, vertical = 17.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        book.sectionTitle,
                        color = colors.softInk,
                        style = MaterialTheme.typography.labelMedium,
                        lineHeight = 21.sp,
                    )
                    if (book.title != book.sectionTitle) {
                        Text(book.title,
                            fontSize = 21.sp, lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold, color = colors.accent)
                    }
                    HorizontalDivider(color = colors.border)
                    when (book.kind) {
                        "digit-table" -> OriginalDigitTable(book, colors)
                        "double-table" -> OriginalDoubleTable(book, colors)
                        else -> {
                            book.lines.forEach { sourceLine ->
                                if (sourceLine.isNotBlank()) {
                                    Text(
                                        text = sourceLine.trimStart(),
                                        modifier = Modifier.padding(
                                            start = if (sourceLine.startsWith("   ")) 14.dp
                                                else 0.dp),
                                        fontSize = 17.sp,
                                        lineHeight = 29.sp,
                                        color = colors.ink,
                                    )
                                } else {
                                    Spacer(Modifier.height(5.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                }
                HorizontalDivider(color = colors.border)
                Row(
                    modifier = Modifier.fillMaxWidth().height(49.dp)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (en) "Original document" else "မူရင်း DOC အတိုင်း",
                        color = colors.softInk,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
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
private fun OriginalDigitTable(book: TwoDRulesBook, colors: BookColors) {
    // The supplied file is a plain-text export of one six-column source table.
    // Transpose for narrow mobile paper WITHOUT changing the five source titles
    // or any of its fifty cells.
    val lines = book.lines.map { it.trim() }
    if (lines.size != 66) {
        book.lines.forEach { line ->
            Text(line, color = colors.ink, fontSize = 17.sp, lineHeight = 28.sp)
        }
        return
    }
    val columnHeaders = lines.drop(1).take(5)
    val rows = lines.drop(6).chunked(6)
    rows.forEachIndexed { index, row ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = colors.panel,
            ) {
                Text(
                    lines.first() + "  " + row.first(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp, color = colors.accent,
                )
            }
            columnHeaders.forEachIndexed { col, heading ->
                Text(
                    heading,
                    color = colors.softInk,
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = 23.sp,
                )
                Text(
                    row[col + 1],
                    color = colors.ink, fontSize = 19.sp, lineHeight = 30.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
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
        Text(
            body,
            color = colors.ink,
            fontSize = 19.sp,
            lineHeight = 31.sp,
        )
    }
}
