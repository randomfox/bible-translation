package io.github.randomfox.bible

import android.net.Uri

/** A passage another app asked to open. The chapter and verse are in KJV numbering. */
data class Link(val book: Int, val kjvChapter: Int, val kjvVerse: Int?)

/**
 * Links of the form `biblereader://BOOK/CHAPTER` or `biblereader://BOOK/CHAPTER/VERSE`, e.g. `biblereader://JOH/3/16`.
 * BOOK is a book's code (JOH), abbreviation (John), name (1 John or 1John) or number (43), in any case.
 *
 * Chapter and verse are always a KJV reference, whichever translation is open, and are mapped to the translation's own
 * numbering through the verse keys, so in the Claude Translation biblereader://MAL/4 opens Malachi 3:19. See "Opening a
 * passage from another app" in android/README.md for the rules and examples.
 */
object Links {
    const val SCHEME = "biblereader"

    fun parse(uri: Uri?, books: List<Book>): Link? {
        if (uri == null || !uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        val parts = uri.schemeSpecificPart.orEmpty().split('/', '.', ':').filter { it.isNotBlank() }
        if (parts.size !in 2..3) return null
        val book = findBook(parts[0], books) ?: return null
        val chapter = parts[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val verse = parts.getOrNull(2)?.let { it.toIntOrNull()?.takeIf { v -> v >= 1 } ?: return null }
        return Link(book.id, chapter, verse)
    }

    private fun findBook(key: String, books: List<Book>): Book? {
        val k = normalise(key)
        k.toIntOrNull()?.let { n -> return books.firstOrNull { it.id == n } }
        return books.firstOrNull { normalise(it.code) == k || normalise(it.abbrev) == k || normalise(it.name) == k }
    }

    private fun normalise(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
}
