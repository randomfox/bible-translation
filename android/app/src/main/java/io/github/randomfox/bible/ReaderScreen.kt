package io.github.randomfox.bible

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueBar(title: @Composable () -> Unit, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val c = LocalBibleColors.current
    TopAppBar(
        title = title,
        navigationIcon = {
            if (onBack != null) IconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = c.bar, titleContentColor = c.onBar, actionIconContentColor = c.onBar,
            navigationIconContentColor = c.onBar,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(db: BibleDb, state: AppState) {
    val c = LocalBibleColors.current
    val tid = state.translationId
    val translation = db.translation(tid)
    val pages = remember(tid) { db.chapters(tid) }
    fun indexOf(book: Int, chapter: Int) = pages.indexOf(ChapterRef(book, chapter)).takeIf { it >= 0 }
        ?: pages.indexOfFirst { it.book == book }.coerceAtLeast(0)
    val pager = rememberPagerState(indexOf(state.book, state.chapter)) { pages.size }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Verse?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pickTranslation by remember { mutableStateOf(false) }
    var textSize by remember { mutableStateOf(false) }
    var pickCompare by remember { mutableStateOf(false) }
    val others = state.compare.filter { it != tid && db.translations.any { t -> t.id == it } }

    LaunchedEffect(pager, pages) {
        snapshotFlow { pager.settledPage }.collect { p ->
            val ref = pages[p]
            if (ref.book != state.book || ref.chapter != state.chapter) {
                state.book = ref.book; state.chapter = ref.chapter; state.save()
            }
        }
    }
    LaunchedEffect(state.book, state.chapter) {
        val i = indexOf(state.book, state.chapter)
        if (i != pager.currentPage) pager.scrollToPage(i)
    }

    val current = pages.getOrNull(pager.currentPage) ?: pages.first()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous chapter")
                        }
                        Column(
                            Modifier.clip(RoundedCornerShape(50)).background(c.onBar.copy(alpha = 0.12f))
                                .clickable { state.screen = Screen.Books(db.book(current.book).nt) }
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${db.book(current.book).name} ${current.chapter}", maxLines = 1, fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                            Text((listOf(tid) + others).joinToString(" · ") { db.translation(it).code }, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.08.em,
                                color = c.onBar.copy(alpha = 0.8f))
                        }
                        IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                            enabled = pager.currentPage < pages.lastIndex) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next chapter")
                        }
                    }
                },
                navigationIcon = {
                    Row {
                        IconButton({ state.screen = Screen.Books(db.book(current.book).nt) }) {
                            Icon(Icons.AutoMirrored.Filled.List, "Quick Nav")
                        }
                        // Chapter-wide comparison; shaded while other translations are shown.
                        IconButton({ pickCompare = true },
                            Modifier.padding(4.dp).clip(CircleShape)
                                .background(if (others.isEmpty()) Color.Transparent else c.onBar.copy(alpha = 0.2f))) {
                            Icon(CompareIcon, "Compare translations")
                        }
                    }
                },
                actions = {
                    IconButton({ state.screen = Screen.Search }) { Icon(Icons.Filled.Search, "Search") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Translation") }, { menu = false; pickTranslation = true })
                            DropdownMenuItem({ Text(if (others.isEmpty()) "Compare translations" else "Compare translations ✓") },
                                { menu = false; pickCompare = true })
                            DropdownMenuItem({ Text("Download translations") }, { menu = false; state.screen = Screen.Library })
                            DropdownMenuItem({ Text("Text size") }, { menu = false; textSize = true })
                            DropdownMenuItem({ Text("About ${db.book(current.book).name}") },
                                { menu = false; state.screen = Screen.BookInfo(current.book) })
                            DropdownMenuItem({ Text("About this app") }, { menu = false; state.screen = Screen.About })
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = c.bar, titleContentColor = c.onBar, actionIconContentColor = c.onBar,
                    navigationIconContentColor = c.onBar,
                ),
            )
        },
    ) { pad ->
        HorizontalPager(pager, Modifier.padding(pad).fillMaxSize(), beyondViewportPageCount = 1,
            key = { pages[it].book * 1000 + pages[it].chapter }) { p ->
            ChapterPage(db, state, translation, others, pages[p], isCurrent = p == pager.currentPage,
                selected = selected) { selected = it }
        }
    }

    selected?.let { VerseSheet(db, state, translation, it) { selected = null } }
    if (pickTranslation) TranslationDialog(db, tid, { pickTranslation = false }, { state.screen = Screen.Library }) { newTid ->
        pickTranslation = false
        state.switchTranslation(db, newTid)
    }
    if (textSize) TextSizeDialog(state) { textSize = false }
    if (pickCompare) CompareDialog(db, state, tid) { pickCompare = false }
}

@Composable
private fun CompareDialog(db: BibleDb, state: AppState, tid: Int, onDismiss: () -> Unit) {
    AlertDialog(onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
        dismissButton = { TextButton({ state.compare = emptyList(); state.save(); onDismiss() }) { Text("Turn off") } },
        title = { Text("Compare translations") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Show these under each verse of ${db.translation(tid).name}:", fontSize = 14.sp,
                    color = LocalBibleColors.current.muted)
                db.translations.filter { it.id != tid }.forEach { t ->
                    val on = t.id in state.compare
                    fun toggle() {
                        state.compare = if (on) state.compare - t.id else (state.compare + t.id).sortedBy { db.translations.indexOf(db.translation(it)) }
                        state.save()
                    }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { toggle() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { toggle() })
                        Text("${t.name} (${t.code})")
                    }
                }
                TextButton({ onDismiss(); state.screen = Screen.Library }) { Text("Get more translations") }
            }
        })
}

@Composable
private fun ChapterPage(
    db: BibleDb, state: AppState, translation: Translation, others: List<Int>, ref: ChapterRef, isCurrent: Boolean,
    selected: Verse?, onTap: (Verse) -> Unit,
) {
    val c = LocalBibleColors.current
    val data by produceState<Pair<List<Verse>, Map<Long, List<Pair<Translation, Verse>>>>?>(null, translation.id, ref, others) {
        value = withContext(Dispatchers.IO) {
            val v = db.chapter(translation.id, ref.book, ref.chapter)
            v to db.parallelChapter(translation.id, v, others)
        }
    }
    val list = rememberLazyListState()
    var highlight by remember { mutableStateOf<Int?>(null) }
    val vs = data?.first ?: return
    val parallels = data?.second.orEmpty()
    // Keep track of the verse at the top of the screen, so switching translation can keep it.
    LaunchedEffect(vs, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        snapshotFlow {
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull()
            val i = list.firstVisibleItemIndex +
                if (first != null && list.firstVisibleItemScrollOffset > first.size / 2) 1 else 0
            vs[(i - 1).coerceIn(0, vs.lastIndex)].verse   // item 0 is the chapter heading
        }.collect { state.readingVerse = it }
    }
    // A jumped-to verse stays highlighted for a moment, then fades back.
    LaunchedEffect(highlight) {
        if (highlight != null) { delay(2500); highlight = null }
    }
    LaunchedEffect(vs, isCurrent, state.target, state.book, state.chapter) {
        val t = state.target
        if (isCurrent && t != null && ref.book == state.book && ref.chapter == state.chapter) {
            val i = vs.indexOfFirst { it.verse >= t }
            if (i >= 0) list.scrollToItem(i + 1)
            highlight = t
            state.target = null
        }
    }
    val bodyStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = state.fontSize.sp,
        lineHeight = (state.fontSize * 1.55f).sp, color = MaterialTheme.colorScheme.onBackground)
    LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(db.book(ref.book).name.uppercase(), color = c.muted, fontSize = 13.sp, letterSpacing = 0.15.em)
                Text("${ref.chapter}", color = c.verseNumber, fontSize = 40.sp, fontFamily = FontFamily.Serif)
            }
        }
        itemsIndexed(vs, key = { _, v -> v.verse * 10 + if (v.id == 0L) 1 else 0 }) { _, v ->
            val isSel = selected?.let { it.verse == v.verse && it.chapter == v.chapter && it.book == v.book } == true
            val bg = when {
                isSel -> c.selected
                highlight == v.verse -> c.selected.copy(alpha = 0.6f)
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val comparing = others.isNotEmpty()
            Column(Modifier.fillMaxWidth().background(bg).clickable { onTap(v) }
                .padding(horizontal = 18.dp, vertical = if (comparing) 9.dp else 5.dp)) {
                Text(verseLine(v, translation, c), Modifier.fillMaxWidth(), style = verseStyle(bodyStyle, v.text, state.fontSize))
                parallels[if (v.id == 0L) -v.verse.toLong() else v.id]?.forEach { (t, pv) ->
                    val small = bodyStyle.copy(fontSize = (state.fontSize - 2).sp, lineHeight = (state.fontSize * 1.4f).sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = c.muted, fontWeight = FontWeight.Bold, fontSize = 0.7.em,
                            fontFamily = FontFamily.SansSerif, letterSpacing = 0.05.em)) {
                            append(t.code)
                            if (pv.chapter != v.chapter || pv.verse != v.verse) append(" ${pv.chapter}:${pv.verse}")
                            append("  ")
                        }
                        append(renderText(pv.text, t.italicBrackets))
                    }, Modifier.fillMaxWidth().padding(top = 4.dp), style = verseStyle(small, pv.text, state.fontSize - 2))
                }
            }
            if (comparing) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = c.muted.copy(alpha = 0.15f))
        }
    }
}

private fun verseLine(v: Verse, t: Translation, c: BibleColors): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = c.verseNumber, fontWeight = FontWeight.Bold, fontSize = 0.68.em,
        baselineShift = BaselineShift(0.25f), fontFamily = FontFamily.SansSerif)) { append("${v.verse} ") }
    if (v.id == 0L) withStyle(SpanStyle(color = c.muted, fontStyle = FontStyle.Italic, fontSize = 0.85.em)) {
        append("Not in the source text. The KJV has a verse here; tap for the note.")
    } else append(renderText(v.text, t.italicBrackets))
    if (v.noteCount > 0) withStyle(SpanStyle(color = c.severity[(v.maxSeverity - 1).coerceIn(0, 2)],
        fontSize = 0.6.em, baselineShift = BaselineShift(0.35f))) { append(" ●") }
}

private fun verseStyle(base: TextStyle, text: String, size: Int) = if (isHebrew(text))
    base.copy(textDirection = TextDirection.Rtl, fontSize = (size + 3).sp, lineHeight = (size * 1.9f).sp) else base

/** Hebrew verses read right to left, with the verse number on the right. */
fun isHebrew(text: String) = text.firstOrNull { it.isLetter() }?.let { it in '\u0590'..'\u05FF' } == true

/** KJV brackets mark supplied words, which print in italics; other translations keep brackets as text. */
fun renderText(text: String, italicBrackets: Boolean): AnnotatedString = buildAnnotatedString {
    if (!italicBrackets) { append(text); return@buildAnnotatedString }
    var italic = false
    for (ch in text) when (ch) {
        '[' -> italic = true
        ']' -> italic = false
        else -> if (italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(ch) } else append(ch)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerseSheet(db: BibleDb, state: AppState, t: Translation, verse: Verse, onDismiss: () -> Unit) {
    val c = LocalBibleColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val notes = remember(verse) { db.notes(verse, t.id) }
    val parallel = remember(verse) { db.parallel(verse) }
    var tab by remember { mutableIntStateOf(if (notes.isNotEmpty()) 1 else 0) }
    val ref = "${db.book(verse.book).name} ${verse.chapter}:${verse.verse}"
    val plain = verse.text.let { if (t.italicBrackets) it.replace("[", "").replace("]", "") else it }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ref, style = MaterialTheme.typography.titleLarge, color = c.verseNumber)
                    Text(t.name, color = c.muted, fontSize = 13.sp)
                }
                if (verse.id != 0L) {
                    TextButton({ clipboard.setText(AnnotatedString("$plain\n— $ref (${t.code})")); onDismiss() }) { Text("Copy") }
                    TextButton({
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "$plain\n— $ref (${t.code})")
                        context.startActivity(Intent.createChooser(send, ref))
                    }) { Text("Share") }
                }
            }
            TabRow(tab, Modifier.padding(top = 8.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Compare") })
                Tab(tab == 1, { tab = 1 }, text = { Text(if (notes.isEmpty()) "Notes" else "Notes (${notes.size})") })
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                if (tab == 0) parallel.forEach { p -> ParallelBlock(db, p, verse, state.fontSize) }
                else if (notes.isEmpty()) Text("No difference from the KJV is recorded for this verse.", color = c.muted)
                else notes.forEach { NoteCard(it) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ParallelBlock(db: BibleDb, p: Parallel, from: Verse, fontSize: Int) {
    val c = LocalBibleColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.translation.code, Modifier.clip(RoundedCornerShape(4.dp)).background(c.tile)
                .padding(horizontal = 6.dp, vertical = 2.dp), color = c.onTile, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(p.translation.name, color = c.muted, fontSize = 13.sp)
        }
        if (p.verses.isEmpty()) Text("Not in this text.", Modifier.padding(top = 4.dp), color = c.muted,
            fontStyle = FontStyle.Italic)
        p.verses.forEach { v ->
            val differentRef = v.chapter != from.chapter || v.verse != from.verse || p.verses.size > 1
            Text(buildAnnotatedString {
                if (differentRef) withStyle(SpanStyle(color = c.verseNumber, fontWeight = FontWeight.Bold, fontSize = 0.75.em)) {
                    append("${v.chapter}:${v.verse} ")
                }
                append(renderText(v.text, p.translation.italicBrackets))
            }, Modifier.fillMaxWidth().padding(top = 4.dp), fontFamily = FontFamily.Serif,
                fontSize = (fontSize + if (isHebrew(v.text)) 2 else -1).sp, lineHeight = (fontSize * 1.6f).sp,
                style = TextStyle(textDirection = if (isHebrew(v.text)) TextDirection.Rtl else TextDirection.Content))
        }
    }
}

@Composable
private fun NoteCard(n: Note) {
    val c = LocalBibleColors.current
    val sev = (n.severity - 1).coerceIn(0, 2)
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.severity[sev]))
                Spacer(Modifier.width(8.dp))
                Text("${SEVERITY_LABEL[sev]} · ${CATEGORY_LABEL[n.category] ?: n.category}",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Label("KJV"); Body(n.kjvReading)
            Label("This translation"); Body(n.translationReading)
            if (n.preferred == "either") {
                Label("Either reading"); Body("The KJV reading is equally valid here.")
            }
            if (n.confidence != null) {
                Label("Most likely meaning · ${n.confidence} confidence")
                Body(n.preferredReading, bold = true)
                n.context?.let { Body(it) }
            } else if (n.preferred == "translation" && n.preferredReading != n.translationReading) {
                Label("Most likely meaning"); Body(n.preferredReading, bold = true)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = c.muted.copy(alpha = 0.25f))
            Text(n.note, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun Label(text: String) = Text(text.uppercase(), Modifier.padding(top = 10.dp),
    color = LocalBibleColors.current.muted, fontSize = 11.sp, letterSpacing = 0.08.em, fontWeight = FontWeight.SemiBold)

@Composable
private fun Body(text: String, bold: Boolean = false) = Text(text, Modifier.padding(top = 2.dp),
    fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 22.sp,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)

@Composable
fun TranslationDialog(db: BibleDb, current: Int, onDismiss: () -> Unit, onMore: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(onDismiss, confirmButton = { TextButton(onDismiss) { Text("Close") } },
        dismissButton = { TextButton({ onDismiss(); onMore() }) { Text("Get more") } },
        title = { Text("Translation") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                db.translations.forEach { t ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(t.id) }
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        RadioButton(t.id == current, { onPick(t.id) })
                        Column(Modifier.padding(start = 4.dp, top = 10.dp)) {
                            Text("${t.name} (${t.code})", fontWeight = FontWeight.SemiBold)
                            Text(t.description, fontSize = 13.sp, color = LocalBibleColors.current.muted)
                        }
                    }
                }
            }
        })
}

@Composable
private fun TextSizeDialog(state: AppState, onDismiss: () -> Unit) {
    AlertDialog(onDismiss, confirmButton = { TextButton({ state.save(); onDismiss() }) { Text("Done") } },
        title = { Text("Text size") },
        text = {
            Column {
                Text("In the beginning God created the heavens and the earth.", fontFamily = FontFamily.Serif,
                    fontSize = state.fontSize.sp, lineHeight = (state.fontSize * 1.4f).sp)
                Slider(state.fontSize.toFloat(), { state.fontSize = it.toInt() }, valueRange = 14f..30f, steps = 15)
            }
        })
}

/** Two pages side by side: one solid (the text being read), one outlined (the one compared with it). */
val CompareIcon: ImageVector = ImageVector.Builder("Compare", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 5f); horizontalLineTo(10.5f); verticalLineTo(19f); horizontalLineTo(3f); close()
    }
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(13.5f, 5f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(13.5f); close()
        moveTo(15.3f, 6.8f); horizontalLineTo(19.2f); verticalLineTo(17.2f); horizontalLineTo(15.3f); close()
    }
}.build()
