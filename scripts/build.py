"""Build per-chapter source / KJV / TR text files for translation agents.

Inputs (cache/sources):
  openscriptures_morphhb/wlc/*.xml   Westminster Leningrad Codex (OSHB), Hebrew + Aramaic
  LogosBible_SBLGNT/data/sblgnt/text SBL Greek New Testament (critical text)
  tr/scrivener/textonly/*.SCV        Scrivener 1894 Textus Receptus (KJV's Greek base), ASCII transliteration
  kjv/eng-kjv_vpl.txt                King James Version (eBible.org), [brackets] = KJV italics

Output (data/):
  books.json                         canonical order, codes, verse counts
  chapters/NN_CODE/CCC.src.txt       original-language text, one verse per line
  chapters/NN_CODE/CCC.kjv.txt       matching KJV verses (KJV numbering)
  chapters/NN_CODE/CCC.tr.txt        Textus Receptus (NT only)
"""
import json, re, sys, unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "cache" / "sources"
OUT = ROOT / "data"

# (OSHB/SBLGNT name, KJV code, display name, TR file)
OT = [("Gen","GEN","Genesis"),("Exod","EXO","Exodus"),("Lev","LEV","Leviticus"),("Num","NUM","Numbers"),
      ("Deut","DEU","Deuteronomy"),("Josh","JOS","Joshua"),("Judg","JDG","Judges"),("Ruth","RUT","Ruth"),
      ("1Sam","1SA","1 Samuel"),("2Sam","2SA","2 Samuel"),("1Kgs","1KI","1 Kings"),("2Kgs","2KI","2 Kings"),
      ("1Chr","1CH","1 Chronicles"),("2Chr","2CH","2 Chronicles"),("Ezra","EZR","Ezra"),("Neh","NEH","Nehemiah"),
      ("Esth","EST","Esther"),("Job","JOB","Job"),("Ps","PSA","Psalms"),("Prov","PRO","Proverbs"),
      ("Eccl","ECC","Ecclesiastes"),("Song","SOL","Song of Songs"),("Isa","ISA","Isaiah"),("Jer","JER","Jeremiah"),
      ("Lam","LAM","Lamentations"),("Ezek","EZE","Ezekiel"),("Dan","DAN","Daniel"),("Hos","HOS","Hosea"),
      ("Joel","JOE","Joel"),("Amos","AMO","Amos"),("Obad","OBA","Obadiah"),("Jonah","JON","Jonah"),
      ("Mic","MIC","Micah"),("Nah","NAH","Nahum"),("Hab","HAB","Habakkuk"),("Zeph","ZEP","Zephaniah"),
      ("Hag","HAG","Haggai"),("Zech","ZEC","Zechariah"),("Mal","MAL","Malachi")]
NT = [("Matt","MAT","Matthew","MT"),("Mark","MAR","Mark","MR"),("Luke","LUK","Luke","LU"),("John","JOH","John","JOH"),
      ("Acts","ACT","Acts","AC"),("Rom","ROM","Romans","RO"),("1Cor","1CO","1 Corinthians","1CO"),
      ("2Cor","2CO","2 Corinthians","2CO"),("Gal","GAL","Galatians","GA"),("Eph","EPH","Ephesians","EPH"),
      ("Phil","PHI","Philippians","PHP"),("Col","COL","Colossians","COL"),("1Thess","1TH","1 Thessalonians","1TH"),
      ("2Thess","2TH","2 Thessalonians","2TH"),("1Tim","1TI","1 Timothy","1TI"),("2Tim","2TI","2 Timothy","2TI"),
      ("Titus","TIT","Titus","TIT"),("Phlm","PHM","Philemon","PHM"),("Heb","HEB","Hebrews","HEB"),
      ("Jas","JAM","James","JAS"),("1Pet","1PE","1 Peter","1PE"),("2Pet","2PE","2 Peter","2PE"),
      ("1John","1JO","1 John","1JO"),("2John","2JO","2 John","2JO"),("3John","3JO","3 John","3JO"),
      ("Jude","JUD","Jude","JUDE"),("Rev","REV","Revelation","RE")]

NS = "{http://www.bibletechnologies.net/2003/OSIS/namespace}"


def load_kjv():
    kjv = {}
    for line in (SRC / "kjv" / "eng-kjv_vpl.txt").read_text(encoding="utf-8-sig").splitlines():
        m = re.match(r"(\S+) (\d+):(\d+) (.*)", line)
        if m:
            kjv[(m[1], int(m[2]), int(m[3]))] = m[4].strip()
    return kjv


def load_versemap():
    """WLC ref -> (KJV chapter, verse, type)."""
    vm = {}
    root = ET.parse(SRC / "openscriptures_morphhb" / "wlc" / "VerseMap.xml").getroot()
    for v in root.iter():
        if v.tag.endswith("verse"):
            w = v.get("wlc").split(".")
            k = v.get("kjv").split(".")
            vm[(w[0], int(w[1]), int(re.match(r"\d+", w[2])[0]))] = (int(k[1]), int(re.match(r"\d+", k[2])[0]), v.get("type"))
    return vm


def heb_verse_text(verse):
    """Flatten an OSHB <verse>: words joined, morpheme slashes removed, ketiv shown with qere."""
    parts = []
    for el in verse:
        tag = el.tag.replace(NS, "")
        if tag == "w":
            t = "".join(el.itertext()).replace("/", "")
            if el.get("type") == "x-ketiv":
                t = f"{t}(K)"
            parts.append((" ", t))
        elif tag == "seg":
            t = el.text or ""
            if el.get("type") == "x-maqqef":
                parts.append(("", t))
            else:
                parts.append(("", t))
        elif tag == "note":
            rdg = el.find(f"{NS}rdg")
            if rdg is not None and rdg.get("type") == "x-qere":
                q = " ".join("".join(w.itertext()).replace("/", "") for w in rdg.iter(f"{NS}w"))
                parts.append((" ", f"[Q: {q}]"))
    s = ""
    for i, (sep, t) in enumerate(parts):
        glue = sep if s and not s.endswith("־") else ""
        s += glue + t
    return re.sub(r"\s+", " ", s).strip()


BETA = dict(zip("abgdezhyiklmnxoprsvtufcqw", "αβγδεζηθικλμνξοπρσςτυφχψω"))


def tr_to_greek(s):
    out = "".join(BETA.get(c, c) for c in s)
    return re.sub(r"σ(?=\s|$|[.,;])", "ς", out)


def load_tr(fname):
    raw = (SRC / "tr" / "scrivener" / "textonly" / f"{fname}.SCV").read_text(encoding="latin-1")
    raw = re.sub(r"\[[^\]]*\]", " ", raw)  # book titles
    verses, cur = {}, None
    for line in raw.splitlines():
        m = re.match(r"\s*(\d+):(\d+)\s+(.*)", line)
        if m:
            cur = (int(m[1]), int(m[2]))
            verses[cur] = m[3]
        elif cur and line.strip():
            verses[cur] += " " + line.strip()
    return {k: tr_to_greek(re.sub(r"\s+", " ", v).strip()) for k, v in verses.items()}


def write_chapter(d, ch, src_lines, kjv_lines, tr_lines=None):
    d.mkdir(parents=True, exist_ok=True)
    (d / f"{ch:03d}.src.txt").write_text("\n".join(src_lines) + "\n", encoding="utf-8")
    (d / f"{ch:03d}.kjv.txt").write_text("\n".join(kjv_lines) + "\n", encoding="utf-8")
    if tr_lines is not None:
        (d / f"{ch:03d}.tr.txt").write_text("\n".join(tr_lines) + "\n", encoding="utf-8")


def main():
    kjv = load_kjv()
    vm = load_versemap()
    books = []
    n = 0
    for name, code, disp in OT:
        n += 1
        root = ET.parse(SRC / "openscriptures_morphhb" / "wlc" / f"{name}.xml").getroot()
        bdir = OUT / "chapters" / f"{n:02d}_{code}"
        chapters = {}
        for v in root.iter(f"{NS}verse"):
            _, c, vs = v.get("osisID").split(".")
            chapters.setdefault(int(c), []).append((int(vs), heb_verse_text(v)))
        total = 0
        for c, vv in chapters.items():
            src_lines, kjv_refs = [], []
            for vs, text in vv:
                kc, kv, typ = vm.get((name, c, vs), (c, vs, "full"))
                tag = "" if (kc, kv) == (c, vs) else f" [=KJV {kc}:{kv}{'' if typ == 'full' else ' partial'}]"
                src_lines.append(f"{c}:{vs}{tag}\t{text}")
                if (kc, kv) not in kjv_refs:
                    kjv_refs.append((kc, kv))
            # Include every KJV verse touched, filling gaps (split verses, Neh 7:68 absent from WLC)
            for kc in sorted({k for k, _ in kjv_refs}):
                vs_ = [v for k, v in kjv_refs if k == kc]
                for v in range(min(vs_), max(vs_) + 1):
                    if (kc, v) not in kjv_refs and (code, kc, v) in kjv:
                        kjv_refs.append((kc, v))
                # a source verse split into two KJV verses at the end of a chapter (e.g. Ps 13:6 = KJV 13:5-6)
                nxt = (kc, max(vs_) + 1)
                mapped_elsewhere = any(vm.get((name, cc, vv_), (cc, vv_, ""))[:2] == nxt
                                       for cc, lst in chapters.items() for vv_, _ in lst)
                if (code, *nxt) in kjv and not mapped_elsewhere:
                    kjv_refs.append(nxt)
            kjv_refs.sort()
            kjv_lines = [f"{kc}:{kv}\t{kjv[(code, kc, kv)]}" for kc, kv in kjv_refs if (code, kc, kv) in kjv]
            write_chapter(bdir, c, src_lines, kjv_lines)
            total += len(vv)
        books.append(dict(n=n, code=code, name=disp, osis=name, testament="OT",
                          chapters=len(chapters), verses=total, dir=bdir.name))
    for name, code, disp, trf in NT:
        n += 1
        bdir = OUT / "chapters" / f"{n:02d}_{code}"
        tr = load_tr(trf)
        chapters = {}
        for line in (SRC / "LogosBible_SBLGNT" / "data" / "sblgnt" / "text" / f"{name}.txt").read_text(encoding="utf-8-sig").splitlines():
            m = re.match(r".+? (\d+):(\d+)\t(.*)", line)
            if m:
                chapters.setdefault(int(m[1]), []).append((int(m[2]), unicodedata.normalize("NFC", m[3].strip())))
        total = 0
        allc = sorted(set(chapters) | {c for c, _ in tr})
        for c in allc:
            vv = chapters.get(c, [])
            src_lines = [f"{c}:{vs}\t{t}" for vs, t in vv]
            kv = sorted(v for (cc, v) in [(k[1], k[2]) for k in kjv if k[0] == code and k[1] == c])
            kjv_lines = [f"{c}:{v}\t{kjv[(code, c, v)]}" for v in kv]
            tr_lines = [f"{c}:{v}\t{t}" for (cc, v), t in sorted(tr.items()) if cc == c]
            write_chapter(bdir, c, src_lines, kjv_lines, tr_lines)
            total += len(vv)
        books.append(dict(n=n, code=code, name=disp, osis=name, testament="NT",
                          chapters=len(allc), verses=total, dir=bdir.name))
    (OUT / "books.json").write_text(json.dumps(books, indent=1), encoding="utf-8")
    print(len(books), "books,", sum(b["verses"] for b in books), "source verses,",
          sum(b["chapters"] for b in books), "chapters")


if __name__ == "__main__":
    main()
