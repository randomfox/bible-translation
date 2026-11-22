package io.github.randomfox.bible

import android.content.Context
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BibleTheme {
                Surface(Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val db by produceState<BibleDb?>(null) { value = BibleDb.open(context.applicationContext) }
                    val loaded = db
                    if (loaded == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    else App(loaded)
                }
            }
        }
    }
}

sealed interface Screen {
    data object Reader : Screen
    data class Books(val nt: Boolean) : Screen
    data class Chapters(val book: Int) : Screen
    data class BookInfo(val book: Int) : Screen
    data object Search : Screen
    data object About : Screen
}

/** Reading position and settings, saved between launches. */
class AppState(private val prefs: android.content.SharedPreferences, db: BibleDb) {
    var translationId by mutableIntStateOf(prefs.getInt("tid", db.translations.first().id))
    var book by mutableIntStateOf(prefs.getInt("book", 43))
    var chapter by mutableIntStateOf(prefs.getInt("chapter", 1))
    var fontSize by mutableIntStateOf(prefs.getInt("font", 19))
    /** Verse to scroll to and highlight once, after a jump. */
    var target by mutableStateOf<Int?>(null)
    /** The verse at the top of the screen in the current chapter. */
    var readingVerse by mutableIntStateOf(1)
    var screen by mutableStateOf<Screen>(Screen.Reader)
    var searchQuery by mutableStateOf("")

    fun save() = prefs.edit().putInt("tid", translationId).putInt("book", book).putInt("chapter", chapter)
        .putInt("font", fontSize).apply()

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
fun App(db: BibleDb) {
    val context = LocalContext.current
    val state = remember { AppState(context.getSharedPreferences("app", Context.MODE_PRIVATE), db) }
    BackHandler(state.screen != Screen.Reader) {
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
    }
}
