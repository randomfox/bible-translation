package io.github.randomfox.bible

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    /** A biblereader:// link waiting to be opened, see [Links]. */
    private var link by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) link = intent?.data // not again when the activity is recreated, e.g. on rotation
        enableEdgeToEdge()
        setContent {
            BibleTheme {
                Surface(Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val db by produceState<BibleDb?>(null) { value = BibleDb.open(context.applicationContext) }
                    val loaded = db
                    if (loaded == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    else App(loaded, link) { link = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        link = intent.data
    }
}

sealed interface Screen {
    data object Reader : Screen
    data class Books(val nt: Boolean) : Screen
    data class Chapters(val book: Int) : Screen
    data class BookInfo(val book: Int) : Screen
    data object Search : Screen
    data object About : Screen
    data object Library : Screen
    data object MyNotes : Screen
    /** Editing a note ([noteId] null for a new one) on a verse given by KJV key and by the reading translation's ref. */
    data class EditNote(val noteId: Long?, val book: Int, val kjvChapter: Int, val kjvVerse: Int,
                        val tid: Int, val chapter: Int, val verse: Int) : Screen
}

/** Reading position and settings, saved between launches. */
class AppState(private val prefs: android.content.SharedPreferences, db: BibleDb, val notes: UserNotes) {
    var translationId by mutableIntStateOf(prefs.getInt("tid", BibleDb.CORE_ID).takeIf { db.isInstalled(it) } ?: BibleDb.CORE_ID)
    var book by mutableIntStateOf(prefs.getInt("book", 43))
    var chapter by mutableIntStateOf(prefs.getInt("chapter", 1))
    var fontSize by mutableIntStateOf(prefs.getInt("font", 19))
    /** Translations shown under each verse for comparison (the reading translation is skipped). */
    var compare by mutableStateOf(prefs.getString("compare", "")!!.split(",").mapNotNull { it.toIntOrNull() })
    /** Verse to scroll to and highlight once, after a jump. */
    var target by mutableStateOf<Int?>(null)
    /** The verse at the top of the screen in the current chapter. */
    var readingVerse by mutableIntStateOf(1)
    var screen by mutableStateOf<Screen>(Screen.Reader)
    var searchQuery by mutableStateOf("")

    fun save() = prefs.edit().putInt("tid", translationId).putInt("book", book).putInt("chapter", chapter)
        .putInt("font", fontSize).putString("compare", compare.joinToString(",")).apply()

    /** Switches translation, staying on the verse being read (mapped through the KJV keys). */
    fun switchTranslation(db: BibleDb, newTid: Int) {
        if (newTid == translationId) return
        val pos = db.mapPosition(translationId, book, chapter, readingVerse, newTid)
        translationId = newTid
        if (pos != null) { chapter = pos.first; target = pos.second.takeIf { it > 1 } }
        save()
    }

    fun goTo(book: Int, chapter: Int, verse: Int? = null) {
        this.book = book; this.chapter = chapter; target = verse; screen = Screen.Reader; save()
    }
}

@Composable
fun App(db: BibleDb, link: Uri? = null, onLinkOpened: () -> Unit = {}) {
    val context = LocalContext.current
    val state = remember {
        AppState(context.getSharedPreferences("app", Context.MODE_PRIVATE), db, UserNotes(context.applicationContext))
    }
    val library = remember { Library(context.applicationContext, db) }
    var notice by remember { mutableStateOf<LinkNotice?>(null) }
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        Links.parse(link, db.books)?.let { notice = Links.open(it, db, state, library.catalog) }
        onLinkOpened()
    }
    notice?.let { n ->
        androidx.compose.material3.AlertDialog({ notice = null },
            confirmButton = {
                if (n.entry?.file != null) androidx.compose.material3.TextButton({
                    library.download(n.entry); notice = null; state.screen = Screen.Library
                }) { androidx.compose.material3.Text("Download") }
                else androidx.compose.material3.TextButton({ notice = null }) { androidx.compose.material3.Text("OK") }
            },
            dismissButton = if (n.entry?.file != null) ({
                androidx.compose.material3.TextButton({ notice = null }) { androidx.compose.material3.Text("Not now") }
            }) else null,
            title = { androidx.compose.material3.Text(if (n.entry != null) "${n.entry.name} isn't downloaded" else "Unknown translation") },
            text = {
                androidx.compose.material3.Text(if (n.entry != null)
                    "This link asked for the ${n.entry.name} (${n.entry.code}). It's open in the ${n.openedIn} instead, " +
                        "at the same passage. Download the ${n.entry.code} to read it there."
                else "This link asked for a translation called \"${n.code}\", which Bible Reader doesn't have. The " +
                    "reference was read as a KJV reference and opened in the ${n.openedIn}.")
            })
    }
    BackHandler(state.screen != Screen.Reader && state.screen !is Screen.EditNote) {
        state.screen = when (val s = state.screen) {
            is Screen.Chapters -> Screen.Books(db.book(s.book).nt)
            is Screen.BookInfo -> Screen.Chapters(s.book)
            else -> Screen.Reader
        }
    }
    when (val s = state.screen) {
        Screen.Reader -> androidx.compose.runtime.key(state.translationId) { ReaderScreen(db, state) }
        is Screen.Books -> BooksScreen(db, state, s.nt)
        is Screen.Chapters -> ChaptersScreen(db, state, s.book)
        is Screen.BookInfo -> BookInfoScreen(db, state, s.book)
        Screen.Search -> SearchScreen(db, state)
        Screen.About -> AboutScreen(db, state)
        Screen.Library -> LibraryScreen(db, state, library)
        Screen.MyNotes -> MyNotesScreen(db, state)
        is Screen.EditNote -> NoteEditorScreen(db, state, s)
    }
}
