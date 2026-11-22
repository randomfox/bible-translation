"""Export the translation, the KJV, the source texts and the differences to one SQLite file
for use in a Bible reading app.

Usage:  python3 -I scripts/export.py [OUT]      (default: ./bible.sqlite)

Tables (see EXPORT.md for the full description):
  meta            key/value: title, sources, licences, counts, build date
  books           66 books in canonical order
  verses          this translation, one row per source verse, with its KJV reference
  verse_map       source verse <-> KJV verse (many-to-many where the numbering differs)
  kjv_verses      the King James Version, KJV numbering
  source_verses   Hebrew/Aramaic (WLC) and Greek (SBLGNT) text, source numbering
  differences     every flagged difference from the KJV, with the reading the text most likely means
  book_notes      the per-book comparison summaries (Markdown)
"""
import csv, datetime, json, re, sqlite3, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"

# Flags whose note says the KJV reading is as valid as this translation's: preferred = "either".
EITHER = {
    ("Numbers", "6:4"), ("Numbers", "12:7"), ("Deuteronomy", "33:7"), ("Joshua", "22:10"),
    ("1 Chronicles", "28:12"), ("2 Chronicles", "6:42"), ("2 Chronicles", "14:10"),
    ("2 Chronicles", "20:21"), ("Esther", "10:1"), ("Hosea", "1:6"), ("Luke", "23:47"),
    ("John", "21:17"), ("Romans", "3:22"), ("Romans", "6:7"), ("Romans", "16:24"),
    ("Galatians", "2:16"), ("Ephesians", "1:15"), ("Hebrews", "1:2"), ("Hebrews", "11:1"),
    ("Revelation", "19:15"), ("Revelation", "20:4"),
}

# NT verses where the SBLGNT numbering differs from the KJV (source ref -> KJV refs).
NT_MAP = {
    ("ACT", 19, 40): [(19, 40), (19, 41)],
    ("2CO", 13, 12): [(13, 12), (13, 13)],
    ("2CO", 13, 13): [(13, 14)],
    ("3JO", 1, 15): [(1, 14)],
    ("REV", 12, 18): [(13, 1)],
}
# OT splits the OSHB verse map does not record (Hebrew 13:6 = KJV 13:5-6).
OT_MAP = {("PSA", 13, 6): [(13, 5), (13, 6)]}

SCHEMA = """
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE books (
  id INTEGER PRIMARY KEY,            -- 1..66, canonical Protestant order
  code TEXT NOT NULL UNIQUE,         -- GEN .. REV
  osis TEXT NOT NULL,                -- OSIS abbreviation (Gen, 1Cor, ...)
  name TEXT NOT NULL,
  testament TEXT NOT NULL CHECK (testament IN ('OT','NT')),
  chapters INTEGER NOT NULL,
  verses INTEGER NOT NULL,
  source TEXT NOT NULL               -- text translated from
);
CREATE TABLE verses (
  book_id INTEGER NOT NULL REFERENCES books(id),
  chapter INTEGER NOT NULL,
  verse INTEGER NOT NULL,            -- source numbering (Hebrew for the OT, SBLGNT for the NT)
  kjv_chapter INTEGER,               -- first KJV verse this corresponds to
  kjv_verse INTEGER,
  text TEXT NOT NULL,                -- this translation; [[ ]] = later addition, [ ] = doubtful
  PRIMARY KEY (book_id, chapter, verse)
);
CREATE TABLE verse_map (
  book_id INTEGER NOT NULL REFERENCES books(id),
  chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
  kjv_chapter INTEGER NOT NULL, kjv_verse INTEGER NOT NULL,
  PRIMARY KEY (book_id, chapter, verse, kjv_chapter, kjv_verse)
);
CREATE TABLE kjv_verses (
  book_id INTEGER NOT NULL REFERENCES books(id),
  chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
  text TEXT NOT NULL,                -- [brackets] = words the KJV printed in italics
  PRIMARY KEY (book_id, chapter, verse)
);
CREATE TABLE source_verses (
  book_id INTEGER NOT NULL REFERENCES books(id),
  chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
  language TEXT NOT NULL,            -- hbo (Hebrew/Aramaic) or grc
  text TEXT NOT NULL,                -- OT: (K) = ketiv, [Q: ] = qere. NT: SBLGNT variant sigla kept
  PRIMARY KEY (book_id, chapter, verse)
);
CREATE TABLE differences (
  id INTEGER PRIMARY KEY,
  book_id INTEGER NOT NULL REFERENCES books(id),
  chapter INTEGER NOT NULL, verse INTEGER NOT NULL,   -- source numbering; for verses the KJV has
                                                       -- and the source lacks, the KJV reference
  kjv_chapter INTEGER, kjv_verse INTEGER,
  category TEXT NOT NULL,            -- TEXT, MEANING, INTERP, LEXICAL, ARCHAIC, NAME
  severity INTEGER NOT NULL CHECK (severity IN (1,2,3)),  -- 1 nuance, 2 notable, 3 major
  kjv_reading TEXT NOT NULL,         -- the KJV wording in question
  translation_reading TEXT NOT NULL, -- this translation's wording; "(absent)" = not in the source text
  preferred TEXT NOT NULL CHECK (preferred IN ('translation','kjv','either')),
  preferred_reading TEXT NOT NULL,   -- what the text is most likely saying
  confidence TEXT CHECK (confidence IN ('high','medium','low')),  -- set where a context verdict was made
  context TEXT,                      -- the contextual reasoning behind that verdict
  note TEXT NOT NULL                 -- evidence and explanation
);
CREATE INDEX differences_ref ON differences (book_id, chapter, verse);
CREATE INDEX differences_kjv ON differences (book_id, kjv_chapter, kjv_verse);
CREATE TABLE book_notes (
  book_id INTEGER NOT NULL REFERENCES books(id),
  first_chapter INTEGER NOT NULL, last_chapter INTEGER NOT NULL,
  markdown TEXT NOT NULL,
  PRIMARY KEY (book_id, first_chapter)
);
"""


def read_lines(p):
    if not p.exists():
        return []
    return [l.split("\t", 1) for l in p.read_text(encoding="utf-8").splitlines() if "\t" in l]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "bible.sqlite"
    if out.exists():
        out.unlink()
    db = sqlite3.connect(out)
    db.executescript(SCHEMA)
    books = json.loads((DATA / "books.json").read_text())
    ids = {b["name"]: b["n"] for b in books}
    verdicts = {(r["book"], r["ref"]): r for r in csv.DictReader(open(ROOT / "results" / "ambiguities.tsv", encoding="utf-8"), delimiter="\t")}
    used_verdicts = set()
    n_vs = n_diff = 0
    for b in books:
        bid, code, d = b["n"], b["code"], b["dir"]
        ot = b["testament"] == "OT"
        src_name = "Westminster Leningrad Codex (OSHB)" if ot else "SBL Greek New Testament"
        db.execute("INSERT INTO books VALUES (?,?,?,?,?,?,?,?)",
                   (bid, code, b["osis"], b["name"], b["testament"], b["chapters"], 0, src_name))
        kjvmap, partial, kjv_refs = {}, [], set()
        for srcf in sorted((DATA / "chapters" / d).glob("*.src.txt")):
            for ref, text in read_lines(srcf):
                m = re.match(r"(\d+):(\d+)(?: \[=KJV (\d+):(\d+)( partial)?\])?", ref)
                c, v = int(m[1]), int(m[2])
                if (code, c, v) in OT_MAP or (code, c, v) in NT_MAP:
                    kj = OT_MAP.get((code, c, v)) or NT_MAP[(code, c, v)]
                elif m[3]:
                    kj = [(int(m[3]), int(m[4]))]
                    if m[5]:
                        partial.append((c, v))
                else:
                    kj = [(c, v)]
                kjvmap[(c, v)] = kj
                db.execute("INSERT INTO source_verses VALUES (?,?,?,?,?)", (bid, c, v, "hbo" if ot else "grc", text))
            for ref, text in read_lines(srcf.with_name(srcf.name.replace(".src.", ".kjv."))):
                c, v = map(int, ref.split(":"))
                kjv_refs.add((c, v))
                db.execute("INSERT OR IGNORE INTO kjv_verses VALUES (?,?,?,?)", (bid, c, v, text))
        # A Hebrew verse marked "partial" also covers the KJV verse of its own number when no
        # other source verse claims that KJV verse (e.g. Isa 63:19 = KJV 63:19 + 64:1).
        claimed = {k for ks in kjvmap.values() for k in ks}
        for c, v in partial:
            if (c, v) in kjv_refs and (c, v) not in claimed:
                kjvmap[(c, v)] = [(c, v)] + kjvmap[(c, v)]
        for (c, v), kj in kjvmap.items():
            for kc, kv in kj:
                db.execute("INSERT OR IGNORE INTO verse_map VALUES (?,?,?,?,?)", (bid, c, v, kc, kv))
        count = 0
        for tf in sorted((DATA / "translation" / d).glob("*.txt")):
            for ref, text in read_lines(tf):
                c, v = map(int, ref.split(":"))
                kc, kv = kjvmap.get((c, v), [(c, v)])[0]
                db.execute("INSERT INTO verses VALUES (?,?,?,?,?,?)", (bid, c, v, kc, kv, text))
                count += 1
        db.execute("UPDATE books SET verses=? WHERE id=?", (count, bid))
        n_vs += count
        for cf in sorted((DATA / "compare" / d).glob("*.tsv")):
            for row in csv.reader(open(cf, encoding="utf-8"), delimiter="\t"):
                if not row or row[0] == "ref":
                    continue
                ref, cat, sev, kjv, mine, note = row
                c, v = map(int, ref.split(":"))
                kc, kv = kjvmap.get((c, v), [(c, v)])[0]
                note = re.sub(r" Context: .*$", "", note)
                vd = verdicts.get((b["name"], ref))
                if vd:
                    used_verdicts.add((b["name"], ref))
                    preferred, reading, conf, ctx = "translation", vd["verdict"], vd["confidence"], vd["context"]
                elif (b["name"], ref) in EITHER:
                    preferred, reading, conf, ctx = "either", mine, None, None
                else:
                    preferred, reading, conf, ctx = "translation", mine, None, None
                db.execute("INSERT INTO differences (book_id,chapter,verse,kjv_chapter,kjv_verse,category,severity,"
                           "kjv_reading,translation_reading,preferred,preferred_reading,confidence,context,note) "
                           "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                           (bid, c, v, kc, kv, cat, int(sev), kjv, mine, preferred, reading, conf, ctx, note))
                n_diff += 1
        for sf in sorted((DATA / "compare" / d).glob("summary*.md")):
            m = re.search(r"(\d{3})-(\d{3})", sf.name)
            first, last = (int(m[1]), int(m[2])) if m else (1, b["chapters"])
            db.execute("INSERT INTO book_notes VALUES (?,?,?,?)", (bid, first, last, sf.read_text(encoding="utf-8")))
    # Context verdicts with no matching difference (the translation now agrees with the KJV)
    for key, vd in verdicts.items():
        if key in used_verdicts:
            continue
        bid = ids[vd["book"]]
        c, v = map(int, vd["ref"].split(":"))
        kt = db.execute("SELECT text FROM kjv_verses WHERE book_id=? AND chapter=? AND verse=?", (bid, c, v)).fetchone()
        tt = db.execute("SELECT text FROM verses WHERE book_id=? AND chapter=? AND verse=?", (bid, c, v)).fetchone()
        db.execute("INSERT INTO differences (book_id,chapter,verse,kjv_chapter,kjv_verse,category,severity,"
                   "kjv_reading,translation_reading,preferred,preferred_reading,confidence,context,note) "
                   "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                   (bid, c, v, c, v, "INTERP", 1, kt[0] if kt else "", tt[0] if tt else "", "either",
                    vd["verdict"], vd["confidence"], vd["context"],
                    f"Ambiguous: {vd['question']}. After weighing the context this translation agrees with the KJV, so it is not counted as a difference."))
    meta = {
        "title": "A fresh translation of the 66-book Bible, compared with the King James Version",
        "built": datetime.date.today().isoformat(),
        "books": "66", "verses": str(n_vs), "differences": str(n_diff),
        "source_ot": "Westminster Leningrad Codex via Open Scriptures Hebrew Bible (OSHB); public domain text",
        "source_nt": "SBL Greek New Testament (Logos Bible Software / Society of Biblical Literature), CC BY 4.0; "
                     "Romans 16:25-27 supplied from its apparatus (WH, Tregelles, NA28)",
        "source_kjv": "King James Version (1611/1769) from eBible.org; public domain outside the UK "
                      "(Crown patent applies in the UK)",
        "source_tr": "Scrivener 1894 Textus Receptus, used for comparison only",
        "numbering": "verses use source numbering (Hebrew/SBLGNT); kjv_chapter/kjv_verse and verse_map give the KJV equivalent",
        "translation_marks": "[[ ]] passage judged a later addition; [ ] doubtful words",
        "preferred": "translation = this translation's reading is what the text most likely means; "
                     "either = the KJV reading is equally valid; confidence is set where a context verdict was made",
    }
    db.executemany("INSERT INTO meta VALUES (?,?)", meta.items())
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"{out.name}: {n_vs} verses, {n_diff} differences, {out.stat().st_size/1e6:.1f} MB")


if __name__ == "__main__":
    main()
