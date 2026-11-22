package io.github.randomfox.bible

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Translation(
    val id: Int, val code: String, val name: String, val language: String, val description: String,
    val license: String, val attribution: String, val italicBrackets: Boolean, val searchable: Boolean,
)

data class Book(val id: Int, val code: String, val abbrev: String, val name: String, val nt: Boolean)

data class ChapterRef(val book: Int, val chapter: Int)

/** One row in a chapter. [id] is 0 for a verse the KJV has but this translation's source lacks. */
data class Verse(
    val id: Long, val book: Int, val chapter: Int, val verse: Int, val text: String,
    val noteCount: Int = 0, val maxSeverity: Int = 0,
)

data class Note(
    val kjvChapter: Int, val kjvVerse: Int, val severity: Int, val category: String,
    val kjvReading: String, val translationReading: String, val preferred: String,
    val preferredReading: String, val confidence: String?, val context: String?, val note: String,
)

data class Parallel(val translation: Translation, val verses: List<Verse>)

data class SearchHit(val translationId: Int, val verse: Verse)

class BibleDb private constructor(private val db: SQLiteDatabase) {
    val translations: List<Translation> = db.list(
        "SELECT id, code, name, language, description, license, attribution, markup, searchable " +
            "FROM translations ORDER BY sort"
    ) {
        Translation(it.getInt(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4),
            it.getString(5), it.getString(6), it.getString(7) == "italic", it.getInt(8) == 1)
    }
    val books: List<Book> = db.list("SELECT id, code, abbrev, name, testament FROM books ORDER BY id") {
        Book(it.getInt(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4) == "NT")
    }
    private val chapterCache = HashMap<Int, List<ChapterRef>>()

    fun translation(id: Int) = translations.firstOrNull { it.id == id } ?: translations.first()
    fun book(id: Int) = books[id - 1]

    /** Every chapter of a translation, in canonical order. Chapter counts differ between numberings. */
    fun chapters(tid: Int): List<ChapterRef> = synchronized(chapterCache) {
        chapterCache.getOrPut(tid) {
            db.list("SELECT DISTINCT book_id, chapter FROM verses WHERE translation_id=? ORDER BY book_id, chapter",
                tid) { ChapterRef(it.getInt(0), it.getInt(1)) }
        }
    }

    fun chapter(tid: Int, book: Int, chapter: Int): List<Verse> {
        val sub = "FROM notes n JOIN verse_keys k ON n.book_id=k.book_id AND n.kjv_chapter=k.kjv_chapter " +
            "AND n.kjv_verse=k.kjv_verse WHERE k.verse_id=v.id"
        val verses = db.list(
            "SELECT v.id, v.verse, v.text, (SELECT count(*) $sub), (SELECT max(n.severity) $sub) FROM verses v " +
                "WHERE v.translation_id=? AND v.book_id=? AND v.chapter=? ORDER BY v.verse", tid, book, chapter
        ) { Verse(it.getLong(0), book, chapter, it.getInt(1), it.getString(2), it.getInt(3), it.getInt(4)) }
        // Verses the KJV has and this translation's source text lacks (e.g. Matt 17:21), shown as gaps.
        val have = verses.map { it.verse }.toSet()
        val gaps = db.list(
            "SELECT verse, count(*), max(severity) FROM notes WHERE translation_id=? AND book_id=? AND chapter=? " +
                "AND translation_reading='(absent)' GROUP BY verse", tid, book, chapter
        ) { Verse(0, book, chapter, it.getInt(0), "", it.getInt(1), it.getInt(2)) }
            .filter { it.verse !in have && it.verse <= (have.maxOrNull() ?: 0) + 1 }
        return (verses + gaps).sortedBy { it.verse }
    }

    fun notes(verse: Verse, tid: Int): List<Note> {
        val cols = "n.kjv_chapter, n.kjv_verse, n.severity, n.category, n.kjv_reading, n.translation_reading, " +
            "n.preferred, n.preferred_reading, n.confidence, n.context, n.note"
        val map: (Cursor) -> Note = {
            Note(it.getInt(0), it.getInt(1), it.getInt(2), it.getString(3), it.getString(4), it.getString(5),
                it.getString(6), it.getString(7), it.getStringOrNull(8), it.getStringOrNull(9), it.getString(10))
        }
        return if (verse.id == 0L) db.list("SELECT $cols FROM notes n WHERE n.translation_id=? AND n.book_id=? " +
            "AND n.chapter=? AND n.verse=? ORDER BY n.severity DESC", tid, verse.book, verse.chapter, verse.verse, map = map)
        else db.list("SELECT DISTINCT $cols FROM notes n JOIN verse_keys k ON n.book_id=k.book_id " +
            "AND n.kjv_chapter=k.kjv_chapter AND n.kjv_verse=k.kjv_verse WHERE k.verse_id=? ORDER BY n.severity DESC",
            verse.id, map = map)
    }

    /** The same verse in every translation, matched through the shared KJV keys. */
    fun parallel(verse: Verse): List<Parallel> {
        val keys = if (verse.id == 0L) "SELECT ? AS b, ? AS c, ? AS v"
        else "SELECT book_id AS b, kjv_chapter AS c, kjv_verse AS v FROM verse_keys WHERE verse_id=?"
        val args: Array<Any> = if (verse.id == 0L) arrayOf(verse.book, verse.chapter, verse.verse) else arrayOf(verse.id)
        val rows = db.list(
            "SELECT DISTINCT v.translation_id, v.id, v.book_id, v.chapter, v.verse, v.text FROM ($keys) x " +
                "JOIN verse_keys k ON k.book_id=x.b AND k.kjv_chapter=x.c AND k.kjv_verse=x.v " +
                "JOIN verses v ON v.id=k.verse_id ORDER BY v.translation_id, v.chapter, v.verse", *args
        ) { it.getInt(0) to Verse(it.getLong(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getString(5)) }
        return translations.map { t -> Parallel(t, rows.filter { it.first == t.id }.map { it.second }) }
    }

    /** Where a chapter/verse in one translation falls in another translation's numbering. */
    fun mapPosition(fromTid: Int, book: Int, chapter: Int, verse: Int, toTid: Int): Pair<Int, Int>? {
        if (fromTid == toTid) return chapter to verse
        return db.list(
            "SELECT v2.chapter, v2.verse FROM verses v1 JOIN verse_keys k1 ON k1.verse_id=v1.id " +
                "JOIN verse_keys k2 ON k2.book_id=k1.book_id AND k2.kjv_chapter=k1.kjv_chapter " +
                "AND k2.kjv_verse=k1.kjv_verse JOIN verses v2 ON v2.id=k2.verse_id AND v2.translation_id=? " +
                "WHERE v1.translation_id=? AND v1.book_id=? AND v1.chapter=? AND v1.verse>=? " +
                "ORDER BY v1.verse, v2.chapter, v2.verse LIMIT 1", toTid, fromTid, book, chapter, verse
        ) { it.getInt(0) to it.getInt(1) }.firstOrNull()
    }

    fun search(query: String, tid: Int, limit: Int = 500): List<SearchHit> {
        val match = ftsQuery(query) ?: return emptyList()
        return db.list(
            "SELECT v.id, v.book_id, v.chapter, v.verse, v.text FROM verses_fts f JOIN verses v ON v.id=f.docid " +
                "WHERE verses_fts MATCH ? AND v.translation_id=? ORDER BY v.id LIMIT ?", match, tid, limit
        ) { SearchHit(tid, Verse(it.getLong(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getString(4))) }
    }

    fun bookNotes(tid: Int, book: Int): List<String> =
        db.list("SELECT text FROM book_notes WHERE translation_id=? AND book_id=? ORDER BY first_chapter", tid, book) {
            it.getString(0)
        }

    companion object {
        private const val NAME = "bible.db"

        /** Copies the bundled database out of the APK on first run and after every app update. */
        suspend fun open(context: Context): BibleDb = withContext(Dispatchers.IO) {
            val file = context.getDatabasePath(NAME)
            val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            val prefs = context.getSharedPreferences("db", Context.MODE_PRIVATE)
            if (!file.exists() || prefs.getLong("stamp", -1) != stamp) {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                context.assets.open(NAME).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                tmp.renameTo(file)
                prefs.edit().putLong("stamp", stamp).apply()
            }
            BibleDb(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
        }

        /** Turns typed words into an FTS4 query: all words must appear, the last may be a prefix. */
        fun ftsQuery(text: String): String? {
            val phrase = text.trim().let { it.length > 2 && it.startsWith('"') && it.endsWith('"') }
            val words = Regex("[\\p{L}\\p{N}']+").findAll(text).map { it.value.replace("'", "") }
                .filter { it.isNotEmpty() }.toList()
            if (words.isEmpty()) return null
            return if (phrase) "\"" + words.joinToString(" ") + "\""
            else words.mapIndexed { i, w -> if (i == words.lastIndex) "$w*" else w }.joinToString(" ")
        }
    }
}

private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

private fun <T> SQLiteDatabase.list(sql: String, vararg args: Any, map: (Cursor) -> T): List<T> =
    rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { c ->
        buildList { while (c.moveToNext()) add(map(c)) }
    }
