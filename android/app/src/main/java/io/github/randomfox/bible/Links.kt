package io.github.randomfox.bible

import android.net.Uri

/**
 * A passage another app asked to open. With [translation] the chapter and verse are in that translation's own
 * numbering; without it they are a KJV reference.
 */
data class Link(val book: Int, val chapter: Int, val verse: Int?, val translation: String?)

/** Shown when a link named a translation that isn't installed, or one Bible Reader doesn't know. */
data class LinkNotice(val code: String, val entry: CatalogEntry?, val openedIn: String)

/**
 * Links of the form `biblereader://BOOK/CHAPTER[/VERSE][?translation=CODE]`, e.g. `biblereader://JOH/3/16` or
 * `biblereader://PSA/51/3?translation=CT`.
 * BOOK is a book's code (JOH), abbreviation (John), name (1 John or 1John) or number (43), in any case.
 * CODE is a translation code from the Translations screen (CT, KJV, BSB, WEB, YLT, HEB-GRK, TR), in any case.
 *
 * Without a translation, the reference is read as KJV and opened in the translation being read, mapped through the
 * verse keys (in the Claude Translation biblereader://MAL/4 opens Malachi 3:19). With one, the app switches to that
 * translation and reads the reference in its own numbering. See "Opening a passage from another app" in
 * android/README.md for the rules and examples.
 */
object Links {
    const val SCHEME = "biblereader"

    fun parse(uri: Uri?, books: List<Book>): Link? {
        if (uri == null || !uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        // Parsed by hand: getQueryParameter() throws for opaque URIs such as biblereader:JOH/3.
        val ssp = uri.encodedSchemeSpecificPart.orEmpty()
        val path = ssp.substringBefore('?').substringBefore('#')
        val query = ssp.substringAfter('?', "").substringBefore('#')
        val params = query.split('&').filter { '=' in it }.associate {
            Uri.decode(it.substringBefore('=')).lowercase() to Uri.decode(it.substringAfter('=')).trim()
        }
        val parts = Uri.decode(path).split('/', '.', ':').filter { it.isNotBlank() }
        if (parts.size !in 2..3) return null
        val book = findBook(parts[0], books) ?: return null
        val chapter = parts[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val verse = parts.getOrNull(2)?.let { it.toIntOrNull()?.takeIf { v -> v >= 1 } ?: return null }
        return Link(book.id, chapter, verse, params["translation"]?.takeIf { it.isNotEmpty() })
    }

    /** Opens a link. Returns a notice to show if it named a translation that isn't available. */
    fun open(l: Link, db: BibleDb, state: AppState, catalog: List<CatalogEntry>?): LinkNotice? {
        val named = l.translation?.let { c -> db.translations.firstOrNull { it.code.equals(c, ignoreCase = true) } }
        if (named != null) {
            // The reference is in this translation's own numbering.
            state.translationId = named.id
            go(state, l, db.position(named.id, l.book, l.chapter, l.verse ?: 1))
            return null
        }
        // No translation, or one that isn't installed: open in the translation being read. Read the reference in the
        // named translation's numbering if the catalog says what it is, otherwise as KJV.
        val entry = l.translation?.let { c -> catalog?.firstOrNull { it.code.equals(c, ignoreCase = true) } }
        val tid = state.translationId
        val pos = if (entry?.numbering == "original") {
            // Every "original"-numbered translation shares the Claude Translation's numbering, which is always installed.
            db.position(BibleDb.CORE_ID, l.book, l.chapter, l.verse ?: 1)
                ?.let { (c, v) -> db.mapPosition(BibleDb.CORE_ID, l.book, c, v, tid) }
        } else db.fromKjv(tid, l.book, l.chapter, l.verse ?: 1)
        go(state, l, pos)
        return l.translation?.let { LinkNotice(it, entry, db.translation(tid).name) }
    }

    private fun go(state: AppState, l: Link, pos: Pair<Int, Int>?) {
        // A chapter link starts at the top, unless the chapter begins partway through one in this numbering.
        if (pos == null) state.goTo(l.book, 1)
        else state.goTo(l.book, pos.first, pos.second.takeIf { l.verse != null || it > 1 })
    }

    private fun findBook(key: String, books: List<Book>): Book? {
        val k = normalise(key)
        k.toIntOrNull()?.let { n -> return books.firstOrNull { it.id == n } }
        return books.firstOrNull { normalise(it.code) == k || normalise(it.abbrev) == k || normalise(it.name) == k }
    }

    private fun normalise(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
}
