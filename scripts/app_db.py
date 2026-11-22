"""Build the Android app's bundled database from bible.sqlite.

Usage: python3 -I scripts/app_db.py [OUT]   (default android/app/src/main/assets/bible.db)

The app database holds any number of translations. Each translation keeps its own verse
numbering; every verse also carries one or more KJV keys (verse_keys), which is how the app
lines up the same verse across translations. To add a translation later, write a loader that
returns a Translation (see load_kjv for the simplest case) and add it to TRANSLATIONS.

Tables
  meta          key/value
  translations  id, code, name, language, description, license, attribution, markup, sort
                markup: 'italic' = [words] are supplied words, shown in italics without brackets
                        'keep'   = brackets are part of the text and shown as is
  books         id 1..66, code, abbrev, name, testament, chapters (KJV count)
  verses        id, translation_id, book_id, chapter, verse, text
  verse_keys    verse_id, book_id, kjv_chapter, kjv_verse
  notes         differences from the KJV, keyed by KJV reference (so every translation can show them)
  book_notes    per-translation notes on a book (markdown)
  verses_fts    contentless FTS4 index over English verses, docid = verses.id
"""
import datetime
import pathlib
import re
import sqlite3
import sys
from dataclasses import dataclass, field

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "bible.sqlite"
SCHEMA_VERSION = 1

SCHEMA = """
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE translations (
  id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, language TEXT NOT NULL,
  description TEXT NOT NULL, license TEXT NOT NULL, attribution TEXT NOT NULL,
  markup TEXT NOT NULL CHECK (markup IN ('italic','keep')), searchable INTEGER NOT NULL, sort INTEGER NOT NULL);
CREATE TABLE books (
  id INTEGER PRIMARY KEY, code TEXT NOT NULL, abbrev TEXT NOT NULL, name TEXT NOT NULL,
  testament TEXT NOT NULL, chapters INTEGER NOT NULL);
CREATE TABLE verses (
  id INTEGER PRIMARY KEY, translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL);
CREATE UNIQUE INDEX verses_ref ON verses (translation_id, book_id, chapter, verse);
CREATE TABLE verse_keys (verse_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL);
CREATE INDEX verse_keys_verse ON verse_keys (verse_id);
CREATE INDEX verse_keys_kjv ON verse_keys (book_id, kjv_chapter, kjv_verse);
CREATE TABLE notes (
  id INTEGER PRIMARY KEY, translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
  severity INTEGER NOT NULL, category TEXT NOT NULL, kjv_reading TEXT NOT NULL,
  translation_reading TEXT NOT NULL, preferred TEXT NOT NULL, preferred_reading TEXT NOT NULL,
  confidence TEXT, context TEXT, note TEXT NOT NULL);
CREATE INDEX notes_kjv ON notes (book_id, kjv_chapter, kjv_verse);
CREATE TABLE book_notes (translation_id INTEGER NOT NULL, book_id INTEGER NOT NULL,
  first_chapter INTEGER NOT NULL, last_chapter INTEGER NOT NULL, text TEXT NOT NULL);
CREATE VIRTUAL TABLE verses_fts USING fts4(content="", text, tokenize=unicode61);
"""


@dataclass
class Translation:
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


def _keys(src, table_filter=""):
    keys = {}
    for b, c, v, kc, kv in src.execute("SELECT book_id, chapter, verse, kjv_chapter, kjv_verse FROM verse_map "
                                       "ORDER BY book_id, chapter, verse, kjv_chapter, kjv_verse"):
        keys.setdefault((b, c, v), []).append((kc, kv))
    return keys


def load_ft(src, meta):
    keys = _keys(src)
    t = Translation(
        code="FT", name="Fresh Translation", language="en",
        description="A new translation from the Hebrew, Aramaic and Greek, made with attention to the original "
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
    return t


def load_kjv(src, meta):
    t = Translation(
        code="KJV", name="King James Version", language="en",
        description="The Authorized Version of 1611, in the standard 1769 text. Words in italics were supplied by "
                    "the translators.",
        license="Public domain (Crown patent in the UK)", attribution=meta["attribution_kjv"],
        markup="italic", searchable=True)
    for b, c, v, text in src.execute("SELECT book_id, chapter, verse, text FROM kjv_verses ORDER BY book_id, chapter, verse"):
        t.verses.append((b, c, v, text, [(c, v)]))
    return t


SIGLA = re.compile(r"[⸀⸁⸂⸃⸄⸅]")


def load_orig(src, meta):
    keys = _keys(src)
    kv0 = {(b, c, v): (kc, kv) for b, c, v, kc, kv in
           src.execute("SELECT book_id, chapter, verse, kjv_chapter, kjv_verse FROM verses")}
    t = Translation(
        code="HEB-GRK", name="Hebrew & Greek", language="hbo,grc",
        description="The texts the Fresh Translation was made from: the Westminster Leningrad Codex for the Old "
                    "Testament and the SBL Greek New Testament for the New. (K) marks the written text (ketiv) and "
                    "[Q: ] the reading tradition (qere).",
        license="Old Testament: public domain. New Testament: CC BY 4.0",
        attribution=meta["attribution_nt"].replace("Changes: translated into English; verse references mapped to "
                                                   "the KJV;", "Changes: variant signs removed; verse references "
                                                   "mapped to the KJV;") + "\n\n" + meta["attribution_ot"],
        markup="keep", searchable=False)
    for b, c, v, text in src.execute("SELECT book_id, chapter, verse, text FROM source_verses ORDER BY book_id, chapter, verse"):
        k = keys.get((b, c, v)) or ([kv0[(b, c, v)]] if kv0.get((b, c, v), (None,))[0] else [])
        t.verses.append((b, c, v, SIGLA.sub("", text), k))
    return t


TRANSLATIONS = [load_ft, load_kjv, load_orig]


def main():
    out = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "android/app/src/main/assets/bible.db"
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)
    src = sqlite3.connect(SRC)
    meta = dict(src.execute("SELECT key, value FROM meta"))
    db = sqlite3.connect(tmp)
    db.executescript(SCHEMA)
    for bid, code, osis, name, test, ch in src.execute("SELECT id, code, osis, name, testament, chapters FROM books"):
        kjv_ch = src.execute("SELECT max(chapter) FROM kjv_verses WHERE book_id=?", (bid,)).fetchone()[0]
        db.execute("INSERT INTO books VALUES (?,?,?,?,?,?)", (bid, code, osis, name, test, kjv_ch))
    vid = 0
    counts = {}
    for sort, loader in enumerate(TRANSLATIONS):
        t = loader(src, meta)
        tid = sort + 1
        db.execute("INSERT INTO translations VALUES (?,?,?,?,?,?,?,?,?,?)",
                   (tid, t.code, t.name, t.language, t.description, t.license, t.attribution, t.markup,
                    int(t.searchable), sort))
        for b, c, v, text, keys in t.verses:
            vid += 1
            db.execute("INSERT INTO verses VALUES (?,?,?,?,?,?)", (vid, tid, b, c, v, text))
            db.executemany("INSERT INTO verse_keys VALUES (?,?,?,?)", [(vid, b, kc, kv) for kc, kv in keys])
            if t.searchable:
                plain = text.replace("[", "").replace("]", "")
                db.execute("INSERT INTO verses_fts (docid, text) VALUES (?,?)", (vid, plain))
        for d in t.notes:
            db.execute("INSERT INTO notes (translation_id, book_id, kjv_chapter, kjv_verse, chapter, verse, severity, "
                       "category, kjv_reading, translation_reading, preferred, preferred_reading, confidence, context, "
                       "note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                       (tid, d["book_id"], d["kjv_chapter"], d["kjv_verse"], d["chapter"], d["verse"], d["severity"],
                        d["category"], d["kjv_reading"], d["translation_reading"], d["preferred"],
                        d["preferred_reading"], d["confidence"], d["context"], d["note"]))
        db.executemany("INSERT INTO book_notes VALUES (?,?,?,?,?)", [(tid, *bn) for bn in t.book_notes])
        counts[t.code] = len(t.verses)
    db.executemany("INSERT INTO meta VALUES (?,?)", {
        "schema_version": str(SCHEMA_VERSION),
        "built": datetime.date.today().isoformat(),
        "translations": ",".join(counts),
    }.items())
    db.execute("INSERT INTO verses_fts(verses_fts) VALUES ('optimize')")
    db.commit()
    db.execute("VACUUM")
    db.close()
    tmp.replace(out)
    print(f"{out}: {counts}, {out.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
