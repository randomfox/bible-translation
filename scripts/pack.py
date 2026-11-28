"""Build the Claude Translation pack for Bible Reader (https://github.com/randomfox/bible-reader) from bible.sqlite.

Usage: python3 -I scripts/pack.py

Writes dist/CT.db.gz, the gzipped pack, and dist/ct.json: its version (a hash of everything in it), size, SHA-256,
pack format and download URL. scripts/release.sh publishes both as a GitHub release tagged ct-<version>. Bible Reader
bundles the latest release when it is built, and installed apps offer it as an update on their Translations screen.

Pack tables (the app's library database has the same ones, plus an FTS4 table per searchable translation)
  meta          key/value: pack_format, translation id, version
  translations  one row: id, code, name, language, description, license, attribution, markup, searchable,
                sort, version.  markup: 'italic' = [words] are supplied words; 'keep' = brackets are text
  books         id 1..66, code, abbrev, name, testament, chapters (KJV count)
  verses        id, translation_id, book_id, chapter, verse, text.  id = translation_id * 10,000,000 + n
  verse_keys    verse_id, book_id, kjv_chapter, kjv_verse.  How the same verse is matched across translations
  notes         differences from the KJV, keyed by KJV reference
  book_notes    notes on a book
  note_spans    notes that cover a passage of several verses: the note sits at the first KJV verse and applies
                to every verse up to end_chapter:end_verse. A separate table, so apps older than 0.5.0, which
                copy only the tables above, still install the pack and simply ignore it.

The translation keeps its own (Hebrew Bible and SBL Greek New Testament) verse numbering; verse_keys tie every
verse to one or more KJV references.
"""
import gzip
import hashlib
import json
import pathlib
import shutil
import sqlite3
import sys
from dataclasses import dataclass, field

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "bible.sqlite"
REPO = "randomfox/bible-translation"
PACK_FORMAT = 1
VERSE_ID_BASE = 10_000_000

SCHEMA = """
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE translations (
  id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, language TEXT NOT NULL,
  description TEXT NOT NULL, license TEXT NOT NULL, attribution TEXT NOT NULL,
  markup TEXT NOT NULL, searchable INTEGER NOT NULL, sort INTEGER NOT NULL, version TEXT NOT NULL);
CREATE TABLE books (
  id INTEGER PRIMARY KEY, code TEXT NOT NULL, abbrev TEXT NOT NULL, name TEXT NOT NULL,
  testament TEXT NOT NULL, chapters INTEGER NOT NULL);
CREATE TABLE verses (
  id INTEGER PRIMARY KEY, translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL);
CREATE TABLE verse_keys (verse_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL);
CREATE TABLE notes (
  translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
  severity INTEGER NOT NULL, category TEXT NOT NULL, kjv_reading TEXT NOT NULL,
  translation_reading TEXT NOT NULL, preferred TEXT NOT NULL, preferred_reading TEXT NOT NULL,
  confidence TEXT, context TEXT, note TEXT NOT NULL);
CREATE TABLE book_notes (translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  first_chapter INTEGER NOT NULL, last_chapter INTEGER NOT NULL, text TEXT NOT NULL);
CREATE TABLE note_spans (translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL, end_chapter INTEGER NOT NULL, end_verse INTEGER NOT NULL);
"""


@dataclass
class Translation:
    id: int            # stable: saved in the app's settings, so never change or reuse one
    code: str
    name: str
    language: str
    description: str
    license: str
    attribution: str
    markup: str
    searchable: bool
    # (book_id, chapter, verse, text, [(kjv_chapter, kjv_verse), ...])
    verses: list = field(default_factory=list)
    notes: list = field(default_factory=list)       # dict rows, keyed by KJV reference
    book_notes: list = field(default_factory=list)  # (book_id, first, last, markdown)
    spans: list = field(default_factory=list)       # (book_id, kjv_chapter, kjv_verse, end_chapter, end_verse)


def _keys(src, table_filter=""):
    keys = {}
    for b, c, v, kc, kv in src.execute("SELECT book_id, chapter, verse, kjv_chapter, kjv_verse FROM verse_map "
                                       "ORDER BY book_id, chapter, verse, kjv_chapter, kjv_verse"):
        keys.setdefault((b, c, v), []).append((kc, kv))
    return keys


def load_ft(src, meta):
    keys = _keys(src)
    t = Translation(
        id=1, code="CT", name="Claude Translation", language="en",
        description="Made by Claude, Anthropic's AI model, and not reviewed by human scholars, so check important "
                    "passages against other translations. A new translation from the Hebrew, Aramaic and Greek, made "
                    "with attention to the original "
                    "meaning and compared verse by verse with the KJV. [[ ]] marks passages judged to be later "
                    "additions; [ ] marks doubtful words.",
        license="CC0 1.0 (public domain dedication)",
        attribution=meta["attribution_nt"] + "\n\n" + meta["attribution_ot"],
        markup="keep", searchable=True)
    for b, c, v, kc, kv, text in src.execute("SELECT book_id, chapter, verse, kjv_chapter, kjv_verse, text FROM verses "
                                             "ORDER BY book_id, chapter, verse"):
        t.verses.append((b, c, v, text, keys.get((b, c, v)) or ([(kc, kv)] if kc else [])))
    cols = ["book_id", "kjv_chapter", "kjv_verse", "chapter", "verse", "severity", "category", "kjv_reading",
            "translation_reading", "preferred", "preferred_reading", "confidence", "context", "note"]
    for row in src.execute(f"SELECT {','.join(cols)} FROM differences ORDER BY book_id, chapter, verse, severity DESC"):
        d = dict(zip(cols, row))
        if d["kjv_chapter"] is None:
            d["kjv_chapter"], d["kjv_verse"] = d["chapter"], d["verse"]
        t.notes.append(d)
    t.book_notes = list(src.execute("SELECT book_id, first_chapter, last_chapter, markdown FROM book_notes "
                                    "ORDER BY book_id, first_chapter"))
    t.spans = list(src.execute("SELECT book_id, kjv_chapter, kjv_verse, end_kjv_chapter, end_kjv_verse FROM spans "
                               "ORDER BY book_id, kjv_chapter, kjv_verse"))
    return t


def write_pack(t, sort, books, out):
    """Writes one translation as a pack and returns its content version (a hash of everything in it)."""
    out.unlink(missing_ok=True)
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)
    db.executemany("INSERT INTO books VALUES (?,?,?,?,?,?)", books)
    h = hashlib.sha256(repr((t.code, t.name, t.description, t.license, t.attribution, t.markup)).encode())
    for n, (b, c, v, text, keys) in enumerate(t.verses, 1):
        vid = t.id * VERSE_ID_BASE + n
        db.execute("INSERT INTO verses VALUES (?,?,?,?,?,?)", (vid, t.id, b, c, v, text))
        db.executemany("INSERT INTO verse_keys VALUES (?,?,?,?)", [(vid, b, kc, kv) for kc, kv in keys])
        h.update(repr((b, c, v, text, keys)).encode())
    cols = ["book_id", "kjv_chapter", "kjv_verse", "chapter", "verse", "severity", "category", "kjv_reading",
            "translation_reading", "preferred", "preferred_reading", "confidence", "context", "note"]
    for d in t.notes:
        db.execute(f"INSERT INTO notes (translation_id, {','.join(cols)}) VALUES (?{',?' * len(cols)})",
                   (t.id, *[d[k] for k in cols]))
        h.update(repr(sorted(d.items())).encode())
    db.executemany("INSERT INTO book_notes VALUES (?,?,?,?,?)", [(t.id, *bn) for bn in t.book_notes])
    h.update(repr(t.book_notes).encode())
    db.executemany("INSERT INTO note_spans VALUES (?,?,?,?,?,?)", [(t.id, *s) for s in t.spans])
    if t.spans:  # hashed only when present, so packs without spans keep their version
        h.update(repr(t.spans).encode())
    version = h.hexdigest()[:12]
    db.execute("INSERT INTO translations VALUES (?,?,?,?,?,?,?,?,?,?,?)",
               (t.id, t.code, t.name, t.language, t.description, t.license, t.attribution, t.markup,
                int(t.searchable), sort, version))
    db.executemany("INSERT INTO meta VALUES (?,?)", [("pack_format", str(PACK_FORMAT)), ("translation", str(t.id)),
                                                     ("version", version)])
    db.commit()
    db.execute("VACUUM")
    db.close()
    return version


def main():
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    src = sqlite3.connect(SRC)
    meta = dict(src.execute("SELECT key, value FROM meta"))
    books = []
    for bid, code, osis, name, test, ch in src.execute("SELECT id, code, osis, name, testament, chapters FROM books"):
        kjv_ch = src.execute("SELECT max(chapter) FROM kjv_verses WHERE book_id=?", (bid,)).fetchone()[0]
        books.append((bid, code, osis, name, test, kjv_ch))
    t = load_ft(src, meta)
    raw = dist / "CT.db"
    version = write_pack(t, 0, books, raw)
    gz = dist / "CT.db.gz"
    with open(raw, "rb") as f, gzip.GzipFile(gz, "wb", mtime=0) as g:
        shutil.copyfileobj(f, g)
    raw.unlink()
    data = gz.read_bytes()
    (dist / "ct.json").write_text(json.dumps({
        "version": version, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
        "pack_format": PACK_FORMAT, "verses": len(t.verses),
        "url": f"https://github.com/{REPO}/releases/download/ct-{version}/CT.db.gz",
    }, indent=1) + "\n")
    print(f"CT {len(t.verses)} verses, version {version}, {gz.relative_to(ROOT)} {len(data) / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
