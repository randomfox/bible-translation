package io.github.randomfox.bible

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Translation(
    val id: Int, val code: String, val name: String, val language: String, val description: String,
    val license: String, val attribution: String, val italicBrackets: Boolean, val searchable: Boolean,
    val version: String,
)

data class Book(val id: Int, val code: String, val abbrev: String, val name: String, val nt: Boolean)

data class ChapterRef(val book: Int, val chapter: Int)

/** One row in a chapter. [id] is 0 for a verse the KJV has but this translation's source lacks. */
data class Verse(
    val id: Long, val book: Int, val chapter: Int, val verse: Int, val text: String,
    val noteCount: Int = 0, val maxSeverity: Int = 0, val hasUserNote: Boolean = false,
)

data class Note(
    val kjvChapter: Int, val kjvVerse: Int, val severity: Int, val category: String,
    val kjvReading: String, val translationReading: String, val preferred: String,
    val preferredReading: String, val confidence: String?, val context: String?, val note: String,
)

data class Parallel(val translation: Translation, val verses: List<Verse>)

data class SearchHit(val translationId: Int, val verse: Verse)

/**
 * The app's library: one writable SQLite database holding every installed translation. Each translation
 * arrives as a "pack" (see scripts/app_db.py): the Claude Translation is bundled in the APK, the rest are
 * downloaded (see Library.kt). Installing a pack copies its rows in; removing deletes them.
 */
class BibleDb private constructor(private val db: SQLiteDatabase) {
    /** Installed translations, in display order. Compose state, so screens update after an install. */
    var translations by mutableStateOf(loadTranslations())
        private set

    private fun loadTranslations() = db.list(
        "SELECT id, code, name, language, description, license, attribution, markup, searchable, version " +
            "FROM translations ORDER BY sort"
    ) {
        Translation(it.getInt(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4),
            it.getString(5), it.getString(6), it.getString(7) == "italic", it.getInt(8) == 1, it.getString(9))
    }

    fun isInstalled(id: Int) = translations.any { it.id == id }

    /** Copies a pack's rows into the library, replacing any older copy of the same translation. */
    fun install(pack: File) {
        db.execSQL("ATTACH DATABASE ? AS p", arrayOf(pack.path))
        try {
            val format = db.list("SELECT value FROM p.meta WHERE key='pack_format'") { it.getString(0) }.firstOrNull()
            require(format == PACK_FORMAT.toString()) { "This translation needs a newer version of the app." }
            val tid = db.list("SELECT id FROM p.translations") { it.getInt(0) }.single()
            db.beginTransaction()
            try {
                deleteRows(tid)
                db.execSQL("INSERT OR REPLACE INTO books SELECT * FROM p.books")
                for (t in listOf("translations", "verses", "verse_keys", "notes", "book_notes"))
                    db.execSQL("INSERT INTO $t SELECT * FROM p.$t")
                if (db.list("SELECT searchable FROM p.translations") { it.getInt(0) }.single() == 1) {
                    db.execSQL("CREATE VIRTUAL TABLE fts_$tid USING fts4(content=\"\", text, tokenize=unicode61)")
                    db.execSQL("INSERT INTO fts_$tid (docid, text) SELECT id, text FROM verses WHERE translation_id=$tid")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.execSQL("DETACH DATABASE p")
        }
        refresh()
    }

    fun remove(tid: Int) {
        db.beginTransaction()
        try { deleteRows(tid); db.setTransactionSuccessful() } finally { db.endTransaction() }
        refresh()
    }

    private fun deleteRows(tid: Int) {
        db.execSQL("DELETE FROM verse_keys WHERE verse_id IN (SELECT id FROM verses WHERE translation_id=$tid)")
        for (t in listOf("verses", "notes", "book_notes")) db.execSQL("DELETE FROM $t WHERE translation_id=$tid")
        db.execSQL("DELETE FROM translations WHERE id=$tid")
        db.execSQL("DROP TABLE IF EXISTS fts_$tid")
    }

    private fun refresh() {
        synchronized(chapterCache) { chapterCache.clear() }
        books = loadBooks()
        translations = loadTranslations()
    }
    var books: List<Book> = loadBooks()
        private set

    private fun loadBooks() = db.list("SELECT id, code, abbrev, name, testament FROM books ORDER BY id") {
        Book(it.getInt(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4) == "NT")
    }
    private val chapterCache = HashMap<Int, List<ChapterRef>>()

    fun translation(id: Int) = translations.firstOrNull { it.id == id } ?: translations.first()
    fun installedVersion(id: Int) = translations.firstOrNull { it.id == id }?.version
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

    /**
     * For every verse of a chapter, the matching verses in [others], matched through the KJV keys.
     * Keyed by verse id (or -verse for a gap row). Each matching verse is listed once, under the first
     * verse it matches, so a verse split differently in another numbering is not repeated.
     */
    fun parallelChapter(tid: Int, verses: List<Verse>, others: List<Int>): Map<Long, List<Pair<Translation, Verse>>> {
        if (others.isEmpty() || verses.isEmpty()) return emptyMap()
        val v0 = verses.first()
        val rows = db.list(
            "SELECT DISTINCT v1.id, v1.verse, v2.translation_id, v2.id, v2.chapter, v2.verse, v2.text FROM verses v1 " +
                "JOIN verse_keys k1 ON k1.verse_id=v1.id JOIN verse_keys k2 ON k2.book_id=k1.book_id " +
                "AND k2.kjv_chapter=k1.kjv_chapter AND k2.kjv_verse=k1.kjv_verse JOIN verses v2 ON v2.id=k2.verse_id " +
                "AND v2.translation_id IN (${others.joinToString(",")}) WHERE v1.translation_id=? AND v1.book_id=? " +
                "AND v1.chapter=? ORDER BY v1.verse, v2.translation_id, v2.chapter, v2.verse", tid, v0.book, v0.chapter
        ) { Triple(it.getLong(0) to it.getInt(1), it.getInt(2), Verse(it.getLong(3), v0.book, it.getInt(4), it.getInt(5), it.getString(6))) }
        // A verse goes under the verse with the same reference if there is one (same numbering),
        // otherwise under the first verse it matches.
        val home = HashMap<Long, Long>()
        for ((p, _, v) in rows) {
            val (pid, pverse) = p
            if (v.id !in home || (v.chapter == v0.chapter && v.verse == pverse)) home[v.id] = pid
        }
        val seen = HashSet<Long>()
        val out = LinkedHashMap<Long, MutableList<Pair<Translation, Verse>>>()
        for ((p, t, v) in rows) if (home[v.id] == p.first && seen.add(v.id))
            out.getOrPut(p.first) { mutableListOf() }.add(translation(t) to v)
        for (list in out.values) list.sortBy { (t, v) -> translations.indexOf(t) * 100000 + v.chapter * 1000 + v.verse }
        for (gap in verses.filter { it.id == 0L }) {
            val list = parallel(gap).filter { it.translation.id in others }
                .flatMap { p -> p.verses.filter { seen.add(it.id) }.map { p.translation to it } }
            if (list.isNotEmpty()) out[-gap.verse.toLong()] = list.toMutableList()
        }
        return out
    }

    /** The KJV references a verse is tied to (for a gap row, its own reference). */
    fun keys(verse: Verse): List<Pair<Int, Int>> = if (verse.id == 0L) listOf(verse.chapter to verse.verse)
    else db.list("SELECT kjv_chapter, kjv_verse FROM verse_keys WHERE verse_id=? ORDER BY kjv_chapter, kjv_verse",
        verse.id) { it.getInt(0) to it.getInt(1) }

    /** KJV references for every verse of a chapter, by verse id. */
    fun chapterKeys(tid: Int, book: Int, chapter: Int): Map<Long, List<Pair<Int, Int>>> =
        db.list("SELECT v.id, k.kjv_chapter, k.kjv_verse FROM verses v JOIN verse_keys k ON k.verse_id=v.id " +
            "WHERE v.translation_id=? AND v.book_id=? AND v.chapter=?", tid, book, chapter) {
            it.getLong(0) to (it.getInt(1) to it.getInt(2))
        }.groupBy({ it.first }, { it.second })

    /** The first verse of a translation tied to a KJV reference. */
    fun findByKey(tid: Int, book: Int, kc: Int, kv: Int): Pair<Int, Int>? =
        db.list("SELECT v.chapter, v.verse FROM verse_keys k JOIN verses v ON v.id=k.verse_id WHERE k.book_id=? " +
            "AND k.kjv_chapter=? AND k.kjv_verse=? AND v.translation_id=? ORDER BY v.chapter, v.verse LIMIT 1",
            book, kc, kv, tid) { it.getInt(0) to it.getInt(1) }.firstOrNull()

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

    /** Where a KJV reference is in a translation, or the first verse after it that the translation has. */
    fun fromKjv(tid: Int, book: Int, kjvChapter: Int, kjvVerse: Int): Pair<Int, Int>? = db.list(
        "SELECT v.chapter, v.verse FROM verse_keys k JOIN verses v ON v.id=k.verse_id AND v.translation_id=? " +
            "WHERE k.book_id=? AND (k.kjv_chapter>? OR (k.kjv_chapter=? AND k.kjv_verse>=?)) " +
            "ORDER BY k.kjv_chapter, k.kjv_verse, v.chapter, v.verse LIMIT 1", tid, book, kjvChapter, kjvChapter, kjvVerse
    ) { it.getInt(0) to it.getInt(1) }.firstOrNull()

    /**
     * A reference in a translation's own numbering: the verse itself, or the next one it has (a verse it leaves
     * out, or one past the chapter's end). Null if the chapter is past the end of the book.
     */
    fun position(tid: Int, book: Int, chapter: Int, verse: Int): Pair<Int, Int>? = db.list(
        "SELECT chapter, verse FROM verses WHERE translation_id=? AND book_id=? AND (chapter>? OR (chapter=? AND verse>=?)) " +
            "ORDER BY chapter, verse LIMIT 1", tid, book, chapter, chapter, verse
    ) { it.getInt(0) to it.getInt(1) }.firstOrNull()

    fun search(query: String, tid: Int, limit: Int = 500): List<SearchHit> {
        val match = ftsQuery(query) ?: return emptyList()
        return db.list(
            "SELECT v.id, v.book_id, v.chapter, v.verse, v.text FROM fts_$tid f JOIN verses v ON v.id=f.docid " +
                "WHERE fts_$tid MATCH ? ORDER BY v.id LIMIT ?", match, limit
        ) { SearchHit(tid, Verse(it.getLong(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getString(4))) }
    }

    fun bookNotes(tid: Int, book: Int): List<String> =
        db.list("SELECT text FROM book_notes WHERE translation_id=? AND book_id=? ORDER BY first_chapter", tid, book) {
            it.getString(0)
        }

    companion object {
        private const val NAME = "library.db"
        private const val SCHEMA_VERSION = 2
        const val PACK_FORMAT = 1
        const val CORE_ID = 1

        private val SCHEMA = listOf(
            "CREATE TABLE translations (id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, " +
                "language TEXT NOT NULL, description TEXT NOT NULL, license TEXT NOT NULL, attribution TEXT NOT NULL, " +
                "markup TEXT NOT NULL, searchable INTEGER NOT NULL, sort INTEGER NOT NULL, version TEXT NOT NULL)",
            "CREATE TABLE books (id INTEGER PRIMARY KEY, code TEXT NOT NULL, abbrev TEXT NOT NULL, name TEXT NOT NULL, " +
                "testament TEXT NOT NULL, chapters INTEGER NOT NULL)",
            "CREATE TABLE verses (id INTEGER PRIMARY KEY, translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL, " +
                "chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL)",
            "CREATE UNIQUE INDEX verses_ref ON verses (translation_id, book_id, chapter, verse)",
            "CREATE TABLE verse_keys (verse_id INTEGER NOT NULL, book_id INTEGER NOT NULL, " +
                "kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL)",
            "CREATE INDEX verse_keys_verse ON verse_keys (verse_id)",
            "CREATE INDEX verse_keys_kjv ON verse_keys (book_id, kjv_chapter, kjv_verse)",
            "CREATE TABLE notes (translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL, kjv_chapter INTEGER NOT NULL, " +
                "kjv_verse INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, severity INTEGER NOT NULL, " +
                "category TEXT NOT NULL, kjv_reading TEXT NOT NULL, translation_reading TEXT NOT NULL, " +
                "preferred TEXT NOT NULL, preferred_reading TEXT NOT NULL, confidence TEXT, context TEXT, note TEXT NOT NULL)",
            "CREATE INDEX notes_kjv ON notes (book_id, kjv_chapter, kjv_verse)",
            "CREATE TABLE book_notes (translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL, " +
                "first_chapter INTEGER NOT NULL, last_chapter INTEGER NOT NULL, text TEXT NOT NULL)",
        )

        /**
         * Opens the library, creating it on first run (or after a schema change) and installing the
         * bundled Claude Translation when none is installed or when the APK brings a new version of it.
         * A newer CT downloaded from the catalog is kept until the next APK update.
         */
        suspend fun open(context: Context): BibleDb = withContext(Dispatchers.IO) {
            context.getDatabasePath("bible.db").let { if (it.exists()) context.deleteDatabase("bible.db") }  // 0.1 layout
            var sql = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME).also { it.parentFile?.mkdirs() }, null)
            if (sql.version != SCHEMA_VERSION) {
                sql.close()
                context.deleteDatabase(NAME)
                sql = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(NAME), null)
                sql.beginTransaction()
                try {
                    SCHEMA.forEach(sql::execSQL)
                    sql.version = SCHEMA_VERSION
                    sql.setTransactionSuccessful()
                } finally { sql.endTransaction() }
            }
            val lib = BibleDb(sql)
            val bundled = context.assets.open("core.version").use { it.readBytes().decodeToString().trim() }
            // The bundled version this app last saw. If it hasn't changed, an installed CT that differs from
            // it was downloaded from the catalog (Translations → Update) and must not be replaced.
            val seenFile = File(context.filesDir, "core.bundled")
            val seen = runCatching { seenFile.readText().trim() }.getOrNull()
            val installed = lib.installedVersion(CORE_ID)
            if (installed == null || (installed != bundled && seen != bundled)) {
                val tmp = File(context.cacheDir, "core.db")
                context.assets.open("core.db").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                try { lib.install(tmp) } finally { tmp.delete() }
            }
            if (seen != bundled) runCatching { seenFile.writeText(bundled) }
            lib
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
