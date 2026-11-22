package io.github.randomfox.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.em
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Quick Nav bar: centred title, back on the left, translations and home on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNavBar(db: BibleDb, state: AppState, onBack: (() -> Unit)?) {
    val c = LocalBibleColors.current
    var pick by remember { mutableStateOf(false) }
    CenterAlignedTopAppBar(
        title = { Text("Quick Nav", fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = {
            Text(db.translation(state.translationId).code,
                Modifier.clip(RoundedCornerShape(50)).background(c.onBar.copy(alpha = 0.12f))
                    .clickable { pick = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            IconButton({ state.screen = Screen.Reader }) { Icon(Icons.Filled.Home, "Back to reading") }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = c.bar, titleContentColor = c.onBar, actionIconContentColor = c.onBar,
            navigationIconContentColor = c.onBar,
        ),
    )
    if (pick) TranslationDialog(db, state.translationId, { pick = false }, { state.screen = Screen.Library }) {
        pick = false; state.switchTranslation(db, it)
    }
}

/** The grey strip under the bar: testament tabs, or the chosen book's name. */
@Composable
private fun NavStrip(content: @Composable () -> Unit) {
    Surface(color = LocalBibleColors.current.strip, modifier = Modifier.fillMaxWidth().height(44.dp)) { content() }
}

@Composable
private fun StripLabel(text: String, modifier: Modifier = Modifier, selected: Boolean = true) =
    Text(text.uppercase(), modifier, fontSize = 12.sp, letterSpacing = 0.1.em, fontWeight = FontWeight.SemiBold,
        color = if (selected) MaterialTheme.colorScheme.onSurface else LocalBibleColors.current.muted,
        textAlign = TextAlign.Center)

@Composable
fun BooksScreen(db: BibleDb, state: AppState, nt: Boolean) {
    val c = LocalBibleColors.current
    val pager = rememberPagerState(if (nt) 1 else 0) { 2 }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { QuickNavBar(db, state, onBack = null) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            NavStrip {
                Row(Modifier.fillMaxSize()) {
                    listOf("Old Testament", "New Testament").forEachIndexed { i, label ->
                        val sel = pager.currentPage == i
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { scope.launch { pager.animateScrollToPage(i) } }) {
                            StripLabel(label, Modifier.align(Alignment.Center), sel)
                            if (sel) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(c.indicator))
                        }
                    }
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    items(db.books.filter { it.nt == (page == 1) }, key = { it.id }) { b ->
                        val here = b.id == state.book
                        Text(b.name,
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                .clickable { state.screen = Screen.Chapters(b.id) }
                                .padding(horizontal = 8.dp, vertical = 13.dp),
                            fontSize = 16.sp, maxLines = 1, softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = if (here) c.verseNumber else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (here) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun ChaptersScreen(db: BibleDb, state: AppState, bookId: Int) {
    val c = LocalBibleColors.current
    val book = db.book(bookId)
    val chapters = remember(state.translationId, bookId) {
        db.chapters(state.translationId).filter { it.book == bookId }.map { it.chapter }
    }
    val hasNotes = remember(state.translationId, bookId) { db.bookNotes(state.translationId, bookId).isNotEmpty() }
    Scaffold(topBar = { QuickNavBar(db, state, onBack = { state.screen = Screen.Books(book.nt) }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            NavStrip {
                Box(Modifier.fillMaxSize()) {
                    StripLabel(book.name, Modifier.align(Alignment.Center))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(c.indicator))
                }
            }
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(chapters) { ch ->
                    val here = bookId == state.book && ch == state.chapter
                    Box(Modifier.height(52.dp).clickable { state.goTo(bookId, ch) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).clip(CircleShape)
                            .background(if (here) c.bar else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Text("$ch", fontSize = 18.sp, color = if (here) c.onBar else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (hasNotes) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("About the translation of ${book.name} →",
                        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { state.screen = Screen.BookInfo(bookId) }.padding(12.dp),
                        color = c.verseNumber, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun BookInfoScreen(db: BibleDb, state: AppState, bookId: Int) {
    val book = db.book(bookId)
    val notes = remember(state.translationId, bookId) { db.bookNotes(state.translationId, bookId) }
    Scaffold(topBar = { BlueBar({ Text(book.name) }, onBack = { state.screen = Screen.Chapters(bookId) }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            if (notes.isEmpty()) Text("No notes on this book for ${db.translation(state.translationId).name}.",
                color = LocalBibleColors.current.muted)
            notes.forEach { Markdown(it) }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/** Enough Markdown for the book notes: headings, bullets, **bold**, `code`, tables as plain rows. */
@Composable
private fun Markdown(md: String) {
    val c = LocalBibleColors.current
    for (raw in md.lines()) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> Spacer(Modifier.height(8.dp))
            line.startsWith("#") -> Text(inline(line.trimStart('#').trim()), Modifier.padding(top = 10.dp, bottom = 4.dp),
                style = if (line.startsWith("# ")) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = c.verseNumber)
            line.matches(Regex("^\\|[-:| ]+\\|$")) -> {}
            line.startsWith("|") -> Text(inline(line.trim('|').split("|").joinToString("  ·  ") { it.trim() }),
                Modifier.padding(vertical = 2.dp), fontSize = 14.sp)
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row(Modifier.padding(vertical = 2.dp)) {
                Text("•  ", color = c.verseNumber)
                Text(inline(line.trimStart().drop(2)), fontSize = 15.sp, lineHeight = 21.sp)
            }
            else -> Text(inline(line), Modifier.padding(vertical = 2.dp), fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var bold = false
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) { bold = !bold; i += 2; continue }
        if (s[i] == '`') { i++; continue }
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s[i]) } else append(s[i])
        i++
    }
}

@Composable
fun SearchScreen(db: BibleDb, state: AppState) {
    val c = LocalBibleColors.current
    val searchable = db.translations.filter { it.searchable }
    var tid by remember { mutableIntStateOf(state.translationId.takeIf { id -> searchable.any { it.id == id } } ?: searchable.first().id) }
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.searchQuery, tid) {
        val q = state.searchQuery
        if (q.trim().length < 2) { hits = null; return@LaunchedEffect }
        delay(250)
        hits = withContext(Dispatchers.IO) { runCatching { db.search(q, tid) }.getOrDefault(emptyList()) }
    }
    val words = Regex("[\\p{L}\\p{N}']+").findAll(state.searchQuery).map { it.value.lowercase() }.toList()
    Scaffold(topBar = { BlueBar({ Text("Search") }, onBack = { state.screen = Screen.Reader }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(state.searchQuery, { state.searchQuery = it },
                Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus), singleLine = true,
                placeholder = { Text("Words, or \"an exact phrase\"") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions())
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                searchable.forEach { t -> FilterChip(t.id == tid, { tid = t.id }, { Text(t.code) }) }
            }
            hits?.let { h ->
                Text(if (h.size >= 500) "First 500 verses" else "${h.size} verses", Modifier.padding(12.dp),
                    color = c.muted, fontSize = 13.sp)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(hits.orEmpty(), key = { it.verse.id }) { hit ->
                    val v = hit.verse
                    Column(Modifier.fillMaxWidth().clickable {
                        state.translationId = hit.translationId
                        state.goTo(v.book, v.chapter, v.verse)
                    }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("${db.book(v.book).name} ${v.chapter}:${v.verse}", color = c.verseNumber,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(highlight(v.text.replace("[", "").replace("]", ""), words, c), fontFamily = FontFamily.Serif,
                            fontSize = 16.sp, lineHeight = 22.sp)
                    }
                    HorizontalDivider(color = c.muted.copy(alpha = 0.15f))
                }
            }
        }
    }
}

private fun highlight(text: String, words: List<String>, c: BibleColors): AnnotatedString = buildAnnotatedString {
    append(text)
    val lower = text.lowercase()
    for (w in words) {
        var i = lower.indexOf(w)
        while (i >= 0) {
            val wordStart = i == 0 || !lower[i - 1].isLetterOrDigit()
            if (wordStart) addStyle(SpanStyle(background = c.selected, fontWeight = FontWeight.Bold), i, i + w.length)
            i = lower.indexOf(w, i + w.length)
        }
    }
}

@Composable
fun AboutScreen(db: BibleDb, state: AppState) {
    val c = LocalBibleColors.current
    Scaffold(topBar = { BlueBar({ Text("About") }, onBack = { state.screen = Screen.Reader }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Bible Reader", style = MaterialTheme.typography.headlineSmall, color = c.verseNumber)
            Surface(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(10.dp), color = c.tile) {
                Text("Made with Claude. The Claude Translation, its notes on the KJV, the judgements about what a " +
                    "passage most likely means, and this app were produced by Claude, Anthropic's AI model. No human " +
                    "scholars have reviewed them, and they will contain mistakes. Use them for reading and comparison, " +
                    "and check any passage that matters against established translations and the original texts.",
                    Modifier.padding(12.dp), color = c.onTile, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Text("Download more translations from ⋮ → Download translations. Read several side by side. Tap a verse to compare it across translations and to see " +
                "where the Claude Translation differs from the KJV, which reading the text most likely means, and why.",
                Modifier.padding(top = 6.dp), lineHeight = 21.sp)
            Text("Internet use", Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("The app goes online only when you open Download translations or download a translation. It then " +
                "fetches the list of translations and the files you choose from GitHub (github.com, which serves them " +
                "from githubusercontent.com). Nothing else is sent: no account, analytics, ads or tracking. GitHub sees " +
                "your IP address, as with any download. Reading, comparing, notes and search work offline.",
                Modifier.padding(top = 4.dp), lineHeight = 21.sp)
            Text("A dot after a verse marks a recorded difference: light for a nuance, amber for a notable change, " +
                "red for a major one.", Modifier.padding(top = 8.dp), lineHeight = 21.sp, color = c.muted)
            db.translations.forEach { t ->
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = c.muted.copy(alpha = 0.25f))
                Text("${t.name} (${t.code})", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(t.description, Modifier.padding(top = 4.dp), fontSize = 14.sp, lineHeight = 20.sp)
                Text("Licence: ${t.license}", Modifier.padding(top = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(t.attribution, Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 19.sp, color = c.muted)
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}
