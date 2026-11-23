package io.github.randomfox.bible

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

/** A note the reader wrote on a verse. Keyed by KJV reference, so it shows in every translation. */
data class UserNote(
    val id: Long, val book: Int, val kjvChapter: Int, val kjvVerse: Int,
    /** Where it was written: translation and that translation's own reference, for display. */
    val tid: Int, val chapter: Int, val verse: Int,
    val text: String, val updated: Long,
)

/**
 * The reader's own notes, in user.db. Kept apart from the translation library (library.db), which the app
 * may rebuild, so translation updates never touch them. Included in Android's backup (allowBackup).
 */
class UserNotes(context: Context) {
    private val db: SQLiteDatabase = object : SQLiteOpenHelper(context, "user.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL, " +
                "kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL, translation_id INTEGER NOT NULL, " +
                "chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL, " +
                "updated INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX notes_key ON notes (book_id, kjv_chapter, kjv_verse)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
    }.writableDatabase

    /** Bumped on every change so screens reload. */
    var revision by mutableIntStateOf(0)
        private set

    private val cols = "id, book_id, kjv_chapter, kjv_verse, translation_id, chapter, verse, text, updated"
    private fun query(where: String, vararg args: Any): List<UserNote> =
        db.rawQuery("SELECT $cols FROM notes $where", args.map { it.toString() }.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(UserNote(c.getLong(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4),
                    c.getInt(5), c.getInt(6), c.getString(7), c.getLong(8)))
            }
        }

    fun get(id: Long) = query("WHERE id=?", id).firstOrNull()
    fun all() = query("ORDER BY book_id, kjv_chapter, kjv_verse, updated")

    /** Notes on any of the given KJV references in a book. */
    fun forKeys(book: Int, keys: List<Pair<Int, Int>>): List<UserNote> =
        if (keys.isEmpty()) emptyList()
        else query("WHERE book_id=? AND (" + keys.joinToString(" OR ") { "(kjv_chapter=${it.first} AND kjv_verse=${it.second})" } +
            ") ORDER BY updated", book)

    fun keysInBook(book: Int): Set<Pair<Int, Int>> =
        db.rawQuery("SELECT DISTINCT kjv_chapter, kjv_verse FROM notes WHERE book_id=?", arrayOf(book.toString())).use { c ->
            buildSet { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) }
        }

    /** Inserts or updates; a note saved empty is deleted. */
    fun save(n: UserNote) {
        if (n.text.isBlank()) { if (n.id != 0L) delete(n.id); return }
        val now = System.currentTimeMillis()
        val v = ContentValues().apply {
            put("book_id", n.book); put("kjv_chapter", n.kjvChapter); put("kjv_verse", n.kjvVerse)
            put("translation_id", n.tid); put("chapter", n.chapter); put("verse", n.verse)
            put("text", n.text.trimEnd()); put("updated", now)
        }
        if (n.id == 0L) { v.put("created", now); db.insert("notes", null, v) }
        else db.update("notes", v, "id=?", arrayOf(n.id.toString()))
        revision++
    }

    fun delete(id: Long) {
        db.delete("notes", "id=?", arrayOf(id.toString()))
        revision++
    }
}

// ---------------------------------------------------------------------------------------------------------
// Markdown

/**
 * Renders the Markdown people write in notes (and the book notes): # headings, **bold**, *italic*, `code`,
 * ~~strike~~, - bullets, 1. numbered lists, > quotes, --- rules, and tables as plain rows.
 */
@Composable
fun Markdown(md: String, modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp, maxLines: Int = Int.MAX_VALUE) {
    val c = LocalBibleColors.current
    val lh = fontSize * 1.4f
    Column(modifier) {
        for (raw in md.lines().take(maxLines)) {
            val line = raw.trimEnd()
            val indent = (line.length - line.trimStart().length) / 2 * 14
            val t = line.trimStart()
            val heading = Regex("^(#{1,6})\\s+(.*)").find(t)
            val numbered = Regex("^(\\d+[.)])\\s+(.*)").find(t)
            when {
                t.isEmpty() -> Spacer(Modifier.height(6.dp))
                heading != null -> Text(inlineMarkdown(heading.groupValues[2]), Modifier.padding(top = 8.dp, bottom = 2.dp),
                    style = when (heading.groupValues[1].length) {
                        1 -> MaterialTheme.typography.titleLarge; 2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }, color = c.verseNumber)
                t.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) ->
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = c.muted.copy(alpha = 0.4f))
                t.matches(Regex("^\\|[-:| ]+\\|$")) -> {}
                t.startsWith("|") -> Text(inlineMarkdown(t.trim('|').split("|").joinToString("  ·  ") { it.trim() }),
                    Modifier.padding(vertical = 2.dp), fontSize = fontSize * 0.93f, lineHeight = lh)
                t.startsWith(">") -> Row(Modifier.padding(vertical = 2.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(c.verseNumber.copy(alpha = 0.5f)))
                    Text(inlineMarkdown(t.removePrefix(">").trimStart()), Modifier.padding(start = 10.dp),
                        fontSize = fontSize, lineHeight = lh, fontStyle = FontStyle.Italic, color = c.muted)
                }
                Regex("^[-*+]\\s+").containsMatchIn(t) -> Row(Modifier.padding(start = indent.dp, top = 1.dp, bottom = 1.dp)) {
                    Text("•  ", color = c.verseNumber, fontSize = fontSize, lineHeight = lh)
                    Text(inlineMarkdown(t.replaceFirst(Regex("^[-*+]\\s+"), "")), fontSize = fontSize, lineHeight = lh)
                }
                numbered != null -> Row(Modifier.padding(start = indent.dp, top = 1.dp, bottom = 1.dp)) {
                    Text(numbered.groupValues[1] + " ", color = c.verseNumber, fontSize = fontSize, lineHeight = lh)
                    Text(inlineMarkdown(numbered.groupValues[2]), fontSize = fontSize, lineHeight = lh)
                }
                else -> Text(inlineMarkdown(t), Modifier.padding(start = indent.dp, top = 1.dp, bottom = 1.dp),
                    fontSize = fontSize, lineHeight = lh)
            }
        }
    }
}

/** Inline Markdown: **bold** / __bold__, *italic* / _italic_, `code`, ~~strike~~. */
fun inlineMarkdown(s: String): AnnotatedString {
    val b = AnnotatedString.Builder()
    var bold = false; var italic = false; var code = false; var strike = false
    fun wordChar(i: Int) = i in s.indices && s[i].isLetterOrDigit()
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        if (ch == '\\' && i + 1 < s.length) { i++ } // escaped character: emit next char as is
        else if (ch == '`') { code = !code; i++; continue }
        else if (!code) {
            if (s.startsWith("**", i) || s.startsWith("__", i)) { bold = !bold; i += 2; continue }
            if (s.startsWith("~~", i)) { strike = !strike; i += 2; continue }
            if (ch == '*' && (italic || (i + 1 < s.length && s[i + 1] != ' '))) { italic = !italic; i++; continue }
            // Underscores inside words (snake_case) are left alone.
            if (ch == '_' && ((!italic && !wordChar(i - 1) && wordChar(i + 1)) || (italic && !wordChar(i + 1)))) {
                italic = !italic; i++; continue
            }
        }
        val start = b.length
        b.append(s[i])
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            fontFamily = if (code) FontFamily.Monospace else null,
            background = if (code) Color(0x22888888) else Color.Unspecified,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        )
        if (bold || italic || code || strike) b.addStyle(style, start, b.length)
        i++
    }
    return b.toAnnotatedString()
}

// ---------------------------------------------------------------------------------------------------------
// Editor

@Composable
fun NoteEditorScreen(db: BibleDb, state: AppState, target: Screen.EditNote) {
    val c = LocalBibleColors.current
    val notes = state.notes
    val existing = remember(target) { target.noteId?.let { notes.get(it) } }
    val original = existing?.text ?: ""
    var value by remember { mutableStateOf(TextFieldValue(original, TextRange(original.length))) }
    var preview by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val changed = value.text != original
    val ref = "${db.book(target.book).name} ${target.chapter}:${target.verse}"
    fun close() = state.goTo(target.book, target.chapter, target.verse)
    fun save() {
        notes.save(UserNote(existing?.id ?: 0L, target.book, target.kjvChapter, target.kjvVerse, target.tid,
            target.chapter, target.verse, value.text, 0))
        close()
    }
    // The editor owns Back while open: unsaved changes ask first, otherwise it returns to the verse.
    BackHandler { if (changed) confirmDiscard = true else close() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(preview) { if (!preview) runCatching { focus.requestFocus() } }

    Scaffold(topBar = {
        BlueBar({ Text(if (existing == null) "New note" else "Edit note") },
            onBack = { if (changed) confirmDiscard = true else close() }) {
            if (existing != null) TextButton({ confirmDelete = true }) { Text("Delete", color = c.onBar) }
            TextButton(::save) { Text("Save", color = c.onBar, fontWeight = FontWeight.SemiBold) }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            Text("$ref · ${db.translation(target.tid).code}", Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                color = c.verseNumber, fontWeight = FontWeight.SemiBold)
            TabRow(if (preview) 1 else 0, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab(!preview, { preview = false }, text = { Text("Write") })
                Tab(preview, { preview = true }, text = { Text("Preview") })
            }
            if (preview) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    if (value.text.isBlank()) Text("Nothing to preview yet.", color = c.muted)
                    else Markdown(value.text, fontSize = 16.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    FormatButton("B", FontWeight.Bold) { value = wrap(value, "**") }
                    FormatButton("I", style = FontStyle.Italic) { value = wrap(value, "*") }
                    FormatButton("H") { value = prefixLines(value, "## ") }
                    FormatButton("•") { value = prefixLines(value, "- ") }
                    FormatButton("1.") { value = prefixLines(value, "1. ") }
                    FormatButton("❝") { value = prefixLines(value, "> ") }
                    FormatButton("</>") { value = wrap(value, "`") }
                }
                OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)
                    .focusRequester(focus),
                    placeholder = { Text("Write your note. Markdown works: **bold**, *italic*, # heading, - list, > quote") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (confirmDiscard) AlertDialog({ confirmDiscard = false },
        confirmButton = { TextButton({ confirmDiscard = false; close() }) { Text("Discard") } },
        dismissButton = { TextButton({ confirmDiscard = false; save() }) { Text("Save") } },
        title = { Text("Discard changes?") }, text = { Text("Your changes to this note haven't been saved.") })
    if (confirmDelete) AlertDialog({ confirmDelete = false },
        confirmButton = { TextButton({ confirmDelete = false; existing?.let { notes.delete(it.id) }; close() }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        title = { Text("Delete this note?") }, text = { Text("It will be removed from this phone. This can't be undone.") })
}

@Composable
private fun FormatButton(label: String, weight: FontWeight? = null, style: FontStyle? = null, onClick: () -> Unit) =
    TextButton(onClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(label, fontWeight = weight ?: FontWeight.Medium, fontStyle = style, fontSize = 17.sp)
    }

/** Wraps the selection in a marker (or inserts a pair and puts the cursor between them). */
private fun wrap(v: TextFieldValue, mark: String): TextFieldValue {
    val s = v.selection.min; val e = v.selection.max
    val text = v.text.substring(0, s) + mark + v.text.substring(s, e) + mark + v.text.substring(e)
    return TextFieldValue(text, if (s == e) TextRange(s + mark.length) else TextRange(s + mark.length, e + mark.length))
}

/** Adds a prefix to every line the selection touches, or removes it if all of them already have it. */
private fun prefixLines(v: TextFieldValue, prefix: String): TextFieldValue {
    val text = v.text
    val start = text.lastIndexOf('\n', (v.selection.min - 1).coerceAtLeast(-1)).let { if (it < 0) 0 else it + 1 }
    val endNl = text.indexOf('\n', v.selection.max).let { if (it < 0) text.length else it }
    val lines = text.substring(start, endNl).split('\n')
    val remove = lines.all { it.startsWith(prefix) }
    val changed = lines.joinToString("\n") { if (remove) it.removePrefix(prefix) else prefix + it }
    val delta = changed.length - (endNl - start)
    return TextFieldValue(text.substring(0, start) + changed + text.substring(endNl), TextRange(endNl + delta))
}

// ---------------------------------------------------------------------------------------------------------
// The panel section and the list of all notes

/** The reader's notes on a verse, with Add/Edit, shown at the top of the verse panel's Notes tab. */
@Composable
fun UserNotesSection(db: BibleDb, state: AppState, verse: Verse, tid: Int, list: List<UserNote>, onOpen: () -> Unit) {
    val c = LocalBibleColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("MY NOTES", Modifier.weight(1f), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp)
        TextButton({ onOpen(); state.screen = editTarget(db, verse, tid, null) }) { Text("+ Add note") }
    }
    if (list.isEmpty()) Text("Tap Add note to write your own note on this verse. Markdown formatting works.",
        Modifier.padding(bottom = 12.dp), color = c.muted, fontSize = 14.sp)
    list.forEach { n ->
        Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(); state.screen = editTarget(db, verse, tid, n) },
            shape = RoundedCornerShape(10.dp), color = c.tile) {
            Column(Modifier.padding(14.dp)) {
                Markdown(n.text, fontSize = 15.sp)
                Text(noteFooter(db, n), Modifier.padding(top = 8.dp), color = c.muted, fontSize = 12.sp)
            }
        }
    }
}

private fun noteFooter(db: BibleDb, n: UserNote): String {
    val where = "${db.book(n.book).name} ${n.chapter}:${n.verse}" +
        (db.translations.firstOrNull { it.id == n.tid }?.let { " (${it.code})" } ?: "")
    return "$where · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(n.updated))} · tap to edit"
}

private fun editTarget(db: BibleDb, verse: Verse, tid: Int, n: UserNote?): Screen.EditNote {
    if (n != null) return Screen.EditNote(n.id, n.book, n.kjvChapter, n.kjvVerse, tid, verse.chapter, verse.verse)
    val key = db.keys(verse).firstOrNull() ?: (verse.chapter to verse.verse)
    return Screen.EditNote(null, verse.book, key.first, key.second, tid, verse.chapter, verse.verse)
}

@Composable
fun MyNotesScreen(db: BibleDb, state: AppState) {
    val c = LocalBibleColors.current
    val context = LocalContext.current
    val all = remember(state.notes.revision) { state.notes.all() }
    Scaffold(topBar = {
        BlueBar({ Text("My notes") }, onBack = { state.screen = Screen.Reader }) {
            if (all.isNotEmpty()) TextButton({
                val md = buildString {
                    append("# My Bible notes\n\n")
                    all.forEach { n ->
                        append("## ${db.book(n.book).name} ${n.chapter}:${n.verse}")
                        db.translations.firstOrNull { it.id == n.tid }?.let { append(" (${it.code})") }
                        append("\n\n${n.text}\n\n")
                    }
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "My Bible notes").putExtra(Intent.EXTRA_TEXT, md), "Share notes"))
            }) { Text("Share all", color = c.onBar) }
        }
    }) { pad ->
        if (all.isEmpty()) Text("No notes yet. Tap a verse, open Notes, and choose Add note.",
            Modifier.padding(pad).padding(18.dp), color = c.muted)
        else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(all, key = { it.id }) { n ->
                Column(Modifier.fillMaxWidth().clickable {
                    // Open in the translation it was written in if installed, otherwise find it in the current one.
                    if (db.isInstalled(n.tid)) { state.translationId = n.tid; state.goTo(n.book, n.chapter, n.verse) }
                    else {
                        val pos = db.findByKey(state.translationId, n.book, n.kjvChapter, n.kjvVerse)
                        state.goTo(n.book, pos?.first ?: n.kjvChapter, pos?.second ?: n.kjvVerse)
                    }
                }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("${db.book(n.book).name} ${n.chapter}:${n.verse}" +
                        (db.translations.firstOrNull { it.id == n.tid }?.let { " · ${it.code}" } ?: ""),
                        color = c.verseNumber, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Markdown(n.text, Modifier.padding(top = 4.dp), fontSize = 15.sp, maxLines = 6)
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(n.updated)), Modifier.padding(top = 4.dp),
                        color = c.muted, fontSize = 12.sp)
                }
                HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = c.muted.copy(alpha = 0.15f))
            }
        }
    }
}
