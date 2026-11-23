"""Build the Android app's translation packs from bible.sqlite and the eBible.org texts.

Usage: python3 -I scripts/app_db.py

Each translation becomes one SQLite "pack". The app installs packs into its own library database:
  - the Claude Translation (CT) pack is bundled in the APK: android/app/src/main/assets/core.db
  - every other pack is gzipped into dist/packs/<CODE>.db.gz and listed in dist/packs/catalog.json,
    which are published as assets of the GitHub release "translations" (scripts/publish_packs.sh).

Pack tables (the app's library database has the same ones, plus an FTS4 table per searchable translation)
  meta          key/value: pack_format, translation id, version
  translations  one row: id, code, name, language, description, license, attribution, markup, searchable,
                sort, version.  markup: 'italic' = [words] are supplied words; 'keep' = brackets are text
  books         id 1..66, code, abbrev, name, testament, chapters (KJV count); the same in every pack
  verses        id, translation_id, book_id, chapter, verse, text.  id = translation_id * 10,000,000 + n
  verse_keys    verse_id, book_id, kjv_chapter, kjv_verse.  How the same verse is matched across translations
  notes         differences from the KJV, keyed by KJV reference (CT only)
  book_notes    per-translation notes on a book (CT only)

Each translation keeps its own verse numbering; verse_keys tie every verse to one or more KJV references.
To add a translation, write a loader that returns a Translation (vpl_loader does it for eBible.org texts),
give it a new id, and add it to TRANSLATIONS.
"""
import gzip
import hashlib
import json
import pathlib
import shutil
import re
import sqlite3
import sys
from dataclasses import dataclass, field

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "bible.sqlite"
PACK_FORMAT = 1
VERSE_ID_BASE = 10_000_000
CATALOG_URL = "https://github.com/randomfox/bible-translation/releases/download/translations/"

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
    # Verse numbering, for links naming a translation that isn't installed: "kjv", or "original" for the numbering
    # of the Hebrew Bible and the SBL Greek New Testament (the Claude Translation and Hebrew & Greek)
    numbering: str = "kjv"
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
        id=1, code="CT", name="Claude Translation", language="en", numbering="original",
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
    return t


def load_kjv(src, meta):
    t = Translation(
        id=2, code="KJV", name="King James Version", language="en",
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
        id=3, code="HEB-GRK", name="Hebrew & Greek", language="hbo,grc", numbering="original",
        description="The texts the Claude Translation was made from: the Westminster Leningrad Codex for the Old "
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


SOURCES = ROOT / "cache/sources"
EBIBLE = "Text from eBible.org (https://ebible.org/), which distributes it as public domain."


def load_vpl(path, book_ids, remap=None):
    """eBible.org verse-per-line text: 'GEN 1:1 text'. Books outside the 66 and empty verses are skipped.
    These translations use KJV verse numbering, so each verse's key is its own reference unless remapped."""
    out = []
    for line in open(path, encoding="utf-8"):
        m = re.match(r"(\S+) (\d+):(\d+) ?(.*)", line.rstrip("\n"))
        if not m or m.group(1) not in book_ids or not m.group(4).strip():
            continue
        b, c, v = book_ids[m.group(1)], int(m.group(2)), int(m.group(3))
        out.append((b, c, v, m.group(4).strip(), [(remap or {}).get((b, c, v), (c, v))]))
    return out


def vpl_loader(tid, code, name, file, description, markup, searchable, attribution, remap=None,
               language="en", license="Public domain"):
    def load(src, meta):
        ids = {code_: bid for bid, code_ in src.execute("SELECT id, code FROM books")}
        t = Translation(id=tid, code=code, name=name, language=language, description=description,
                        license=license, attribution=attribution, markup=markup, searchable=searchable)
        t.verses = load_vpl(SOURCES / file, ids, remap)
        return t
    return load


# The WEB prints the Romans doxology at 14:24-26 (as the Majority Text does); the KJV has it at 16:25-27.
WEB_REMAP = {(45, 14, 24): (16, 25), (45, 14, 25): (16, 26), (45, 14, 26): (16, 27)}

load_bsb = vpl_loader(
    4, "BSB", "Berean Standard Bible", "engbsb/engbsb_vpl.txt",
    "A modern, readable translation from the Hebrew, Aramaic and Greek (2016, revised 2023). Verses the oldest "
    "manuscripts lack are left out.", "keep", True,
    "Berean Standard Bible. Dedicated to the public domain by Bible Hub and the Berean Bible translation committee "
    "(2023). " + EBIBLE)
load_web = vpl_loader(
    5, "WEB", "World English Bible", "web/eng-web_vpl.txt",
    "A modern update of the American Standard Version of 1901, based on the Majority Text in the New Testament.",
    "keep", True,
    "World English Bible. Public domain; \"World English Bible\" is a trademark of eBible.org. " + EBIBLE,
    remap=WEB_REMAP)
load_ylt = vpl_loader(
    6, "YLT", "Young's Literal Translation", "engylt/engylt_vpl.txt",
    "Robert Young's very literal translation (1862, revised 1898), which keeps the Hebrew and Greek tenses and word "
    "order where it can. Words in italics were supplied by the translator.", "italic", True,
    "Young's Literal Translation (1898). Public domain. " + EBIBLE)
load_tr = vpl_loader(
    7, "TR", "Textus Receptus", "grctr/grctr_vpl.txt",
    "The Greek \"Received Text\" of the New Testament, the kind of text the KJV was translated from. Compare it "
    "with the SBL Greek New Testament (HEB-GRK) to see where the KJV's Greek differs.", "keep", False,
    "Greek Textus Receptus New Testament. Public domain due to its age. " + EBIBLE, language="grc")

# Display order. Ids are fixed per translation (see Translation.id).
TRANSLATIONS = [load_ft, load_kjv, load_bsb, load_web, load_ylt, load_orig, load_tr]


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
    dist = ROOT / "dist/packs"
    dist.mkdir(parents=True, exist_ok=True)
    assets = ROOT / "android/app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    for old in ("bible.db", "bible.tmp"):
        (assets / old).unlink(missing_ok=True)
    src = sqlite3.connect(SRC)
    meta = dict(src.execute("SELECT key, value FROM meta"))
    books = []
    for bid, code, osis, name, test, ch in src.execute("SELECT id, code, osis, name, testament, chapters FROM books"):
        kjv_ch = src.execute("SELECT max(chapter) FROM kjv_verses WHERE book_id=?", (bid,)).fetchone()[0]
        books.append((bid, code, osis, name, test, kjv_ch))
    catalog = []
    for sort, loader in enumerate(TRANSLATIONS):
        t = loader(src, meta)
        bundled = t.id == 1
        raw = (assets / "core.db") if bundled else dist / f"{t.code}.db"
        version = write_pack(t, sort, books, raw)
        entry = {"id": t.id, "code": t.code, "name": t.name, "language": t.language, "description": t.description,
                 "license": t.license, "version": version, "sort": sort, "verses": len(t.verses),
                 "numbering": t.numbering}
        if bundled:
            (assets / "core.version").write_text(version)
            entry["bundled"] = True
        else:
            gz = dist / f"{t.code}.db.gz"
            with open(raw, "rb") as f, gzip.GzipFile(gz, "wb", mtime=0) as g:
                shutil.copyfileobj(f, g)
            raw.unlink()
            data = gz.read_bytes()
            entry.update(file=gz.name, size=len(data), sha256=hashlib.sha256(data).hexdigest())
        catalog.append(entry)
        size = "bundled" if bundled else f"{entry['size'] / 1e6:.1f} MB"
        print(f"{t.code:8} {len(t.verses):6} verses  version {version}  {size}")
    (dist / "catalog.json").write_text(json.dumps({"format": PACK_FORMAT, "base_url": CATALOG_URL,
                                                   "translations": catalog}, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    main()
