"""Compare the translation with the Berean Standard Bible (BSB), with the KJV as a reference point.

Reads bible.sqlite and the BSB from eBible.org (downloaded to cache/sources/engbsb if missing), compares every verse
the three texts share in KJV numbering, and writes:
  results/bsb-comparison.md   the summary
  results/bsb-scores.tsv      per-verse scores (references and numbers only; no text)

Two measures, each from 0 (nothing shared) to 1 (identical):
  wording   word-sequence similarity of the raw words: order, word choice, "Yahweh" against "the LORD" all count
  content   report.py's measure: bag-of-words F1 after dropping common words and mapping archaic forms and
            Yahweh/LORD together, so it shows whether verses say the same things rather than use the same words
"""
import collections, difflib, io, re, sqlite3, sys, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "scripts"))
from report import norm, f1  # noqa: E402

BSB = ROOT / "cache/sources/engbsb/engbsb_vpl.txt"
BSB_URL = "https://ebible.org/Scriptures/engbsb_vpl.zip"
OUT = ROOT / "results"

# Who the BSB sides with on each of the 13 severity-3 KJV differences. Judged by reading the three texts; see the table.
MAJOR = {("GEN", 49, 10): "KJV", ("DEU", 6, 4): "CT", ("2SA", 21, 19): "KJV", ("JOB", 19, 26): "CT",
         ("JOB", 42, 6): "CT", ("ISA", 7, 14): "KJV", ("ISA", 14, 12): "CT", ("DAN", 9, 25): "KJV",
         ("MAR", 16, 9): "KJV", ("JOH", 7, 53): "KJV", ("1JO", 5, 7): "CT", ("REV", 22, 14): "CT",
         ("REV", 22, 19): "CT"}
MAJOR_WHY = {("MAR", 16, 9): "prints 16:9–20 with no brackets", ("JOH", 7, 53): "prints 7:53–8:11 with no brackets"}

TERMS = [("Yahweh", r"\bYahweh\b", 0), ("LORD (small capitals in print)", r"\bLORD\b", 0),
         ("Holy Spirit", r"\bHoly Spirit\b", 0), ("Holy Ghost", r"\bHoly Ghost\b", 0),
         ("Christ", r"\bChrist\b", 0), ("Messiah", r"\bMessiah\b", 0),
         ("Sheol", r"\bSheol\b", 0), ("Hades", r"\bHades\b", 0), ("hell", r"\bhell\b", re.I),
         ("steadfast love", r"\bsteadfast love\b", re.I), ("loving devotion", r"\bloving devotion\b", re.I),
         ("lovingkindness / mercy", r"\b(lovingkindness|mercy)\b", re.I),
         ("slave(s)", r"\bslaves?\b", re.I), ("servant(s)", r"\bservants?\b", re.I),
         ("saints", r"\bsaints\b", re.I), ("holy ones", r"\bholy ones\b", re.I),
         ("behold", r"\bbehold\b", re.I), ("truly / verily", r"\b(truly|verily)\b", re.I),
         ("thee / thou / thy / thine", r"\b(thee|thou|thy|thine)\b", re.I),
         ("He / Him / His mid-sentence", r"(?<=[a-z,;] )(He|Him|His)\b", 0)]


def load_bsb():
    if not BSB.exists():
        BSB.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(BSB_URL) as r, zipfile.ZipFile(io.BytesIO(r.read())) as z:
            BSB.write_bytes(z.read("engbsb_vpl.txt"))
    out = {}
    for line in BSB.read_text(encoding="utf-8-sig").splitlines():
        m = re.match(r"(\S+) (\d+):(\d+) (.*)", line)
        if m:
            out[(m[1], int(m[2]), int(m[3]))] = m[4]
    return out


def words(s):  # raw words; drops the translation's short bracketed clarifications such as "(Jerusalem)"
    s = re.sub(r"\((\S+\s?){1,3}\)", "", s.replace("’", "'"))
    return re.findall(r"[a-z]+", s.lower())


def wording(a, b):
    return difflib.SequenceMatcher(None, words(a), words(b), autojunk=False).ratio()


def content(a, b):
    return f1(norm(a), norm(b))


def main():
    db = sqlite3.connect(ROOT / "bible.sqlite")
    books = db.execute("select id, code, name, testament from books order by id").fetchall()
    code = {i: c for i, c, _, _ in books}
    ctv = {(b, c, v): t for b, c, v, t in db.execute("select book_id, chapter, verse, text from verses")}
    ct = collections.defaultdict(list)  # CT text in KJV numbering
    for b, c, v, kc, kv in db.execute("select * from verse_map order by book_id, chapter, verse"):
        if ctv[(b, c, v)] not in ct[(code[b], kc, kv)]:
            ct[(code[b], kc, kv)].append(ctv[(b, c, v)])
    ct = {k: " ".join(v) for k, v in ct.items()}
    kjv = {(code[b], c, v): t for b, c, v, t in db.execute("select book_id, chapter, verse, text from kjv_verses")}
    bsb = load_bsb()

    order = {c: i for i, c, _, _ in books}
    shared = sorted(set(ct) & set(kjv) & set(bsb), key=lambda k: (order[k[0]], k[1], k[2]))
    testament = {c: t for _, c, _, t in books}
    pairs = (("ct_bsb", "ct", "bsb"), ("ct_kjv", "ct", "kjv"), ("bsb_kjv", "bsb", "kjv"))
    text = {"ct": ct, "bsb": bsb, "kjv": kjv}
    rows = []
    for k in shared:
        r = {"ref": f"{k[0]} {k[1]}:{k[2]}", "key": k}
        for name, a, b in pairs:
            r["w_" + name] = wording(text[a][k], text[b][k])
            r["c_" + name] = content(text[a][k], text[b][k])
        rows.append(r)

    def mean(rs, f):
        return sum(r[f] for r in rs) / len(rs)

    groups = [("Whole Bible", rows), ("Old Testament", [r for r in rows if testament[r["key"][0]] == "OT"]),
              ("New Testament", [r for r in rows if testament[r["key"][0]] == "NT"])]
    absent = {n: [k for k in kjv if k not in t] for n, t in (("ct", ct), ("bsb", bsb))}
    both = set(absent["ct"]) & set(absent["bsb"])
    ref = lambda k: f"{k[0]} {k[1]}:{k[2]}"
    srt = lambda ks: sorted(ks, key=lambda k: (order[k[0]], k[1], k[2]))

    md = ["# The Claude Translation compared with the BSB", "",
          "Made by `scripts/compare_bsb.py` from `bible.sqlite` and the Berean Standard Bible text from eBible.org, "
          "with the KJV as a reference point. Per-verse scores are in `bsb-scores.tsv`. The KJV differences themselves "
          "are recorded verse by verse in `flags.tsv` and `kjv-comparison.html`; no such review has been done against "
          "the BSB, so this page measures rather than explains.", "",
          f"Verses compared: {len(rows):,}, every verse the three texts share in KJV numbering.", "",
          "## Similarity", "",
          "Each score runs from 0 (nothing shared) to 1 (identical). *Wording* compares the words in order, so "
          "word choice, word order and \"Yahweh\" against \"the LORD\" all lower it. *Content* is the measure "
          "`report.py` uses: it ignores common words, word order and archaic forms, and treats Yahweh and LORD as "
          "the same, so it shows whether verses say the same things.", "",
          "| | CT ~ BSB | CT ~ KJV | BSB ~ KJV |", "|---|---|---|---|"]
    for name, rs in groups:
        md.append(f"| {name}, wording | **{mean(rs, 'w_ct_bsb'):.3f}** | {mean(rs, 'w_ct_kjv'):.3f} | {mean(rs, 'w_bsb_kjv'):.3f} |")
    for name, rs in groups:
        md.append(f"| {name}, content | **{mean(rs, 'c_ct_bsb'):.3f}** | {mean(rs, 'c_ct_kjv'):.3f} | {mean(rs, 'c_bsb_kjv'):.3f} |")
    md += ["", "Share of verses where the CT and the BSB reach each wording score:", "",
           "| at least | 0.9 | 0.7 | 0.5 | 0.3 |", "|---|---|---|---|---|",
           "| verses | " + " | ".join(f"{sum(r['w_ct_bsb'] >= t for r in rows) / len(rows):.1%}" for t in (0.9, 0.7, 0.5, 0.3)) + " |", "",
           "## Which verses each leaves out", "",
           f"Of the KJV's {len(kjv):,} verses, the CT has no text for {len(absent['ct'])} and the BSB for {len(absent['bsb'])}. "
           f"Both leave out the same {len(both)}: " + ", ".join(ref(k) for k in srt(both)) + ".", ""]
    if set(absent["ct"]) - both:
        md.append("Only the CT leaves out " + ", ".join(ref(k) for k in srt(set(absent["ct"]) - both))
                  + "; its Hebrew or Greek source has no such verse.")
    if set(absent["bsb"]) - both:
        md.append("Only the BSB leaves out " + ", ".join(ref(k) for k in srt(set(absent["bsb"]) - both)) + ".")
    md += ["", "## Word choices", "", "How often each term appears in each text.", "",
           "| Term | CT | BSB | KJV |", "|---|---|---|---|"]
    for name, pat, fl in TERMS:
        n = [sum(len(re.findall(pat, t, fl)) for t in text[x].values()) for x in ("ct", "bsb", "kjv")]
        md.append(f"| {name} | {n[0]:,} | {n[1]:,} | {n[2]:,} |")
    md += ["", "## The 13 major KJV differences", "",
           "The comparison with the KJV marked 13 differences as changing what a passage says. This is where the BSB "
           "stands on each, judged by reading the three texts.", "",
           "| Passage | KJV | CT | BSB | BSB sides with |", "|---|---|---|---|---|"]
    side = collections.Counter()
    for b, c, v, kc, kv, kr, tr in db.execute(
            "select book_id, chapter, verse, kjv_chapter, kjv_verse, kjv_reading, translation_reading "
            "from differences where severity = 3 order by book_id, chapter, verse"):
        k = (code[b], kc or c, kv or v)
        who = MAJOR[k]
        side[who] += 1
        cell = lambda s: s.replace("|", "\\|")
        md.append(f"| {ref(k)} | {cell(kr)} | {cell(tr)} | {cell(MAJOR_WHY.get(k, bsb.get(k, '(absent)')))} | {who} |")
    md += ["", f"The BSB sides with the CT on {side['CT']} and with the KJV on {side['KJV']}.", "",
           "## By book", "", "Wording scores.", "",
           "| Book | Verses | CT ~ BSB | CT ~ KJV | BSB ~ KJV |", "|---|---|---|---|---|"]
    for _, c, name, _ in books:
        rs = [r for r in rows if r["key"][0] == c]
        md.append(f"| {name} | {len(rs):,} | {mean(rs, 'w_ct_bsb'):.3f} | {mean(rs, 'w_ct_kjv'):.3f} | {mean(rs, 'w_bsb_kjv'):.3f} |")
    md += ["", "## Least alike", "",
           "The 20 verses where the CT and the BSB share the least content, leaving out verses with figures (one may "
           "write 8,580 where the other spells it out) and verses under ten words. Most are the same sense in "
           "different words, or a different reading of a hard Hebrew verse.", ""]
    plain = [r for r in rows if not re.search(r"\d", ct[r["key"]] + bsb[r["key"]])
             and min(len(words(ct[r["key"]])), len(words(bsb[r["key"]]))) >= 10]
    for r in sorted(plain, key=lambda r: r["c_ct_bsb"])[:20]:
        k = r["key"]
        md += [f"- **{r['ref']}** (content {r['c_ct_bsb']:.2f})", f"  - CT: {ct[k]}", f"  - BSB: {bsb[k]}"]
    md += ["", "The BSB text is from eBible.org's edition, which has no footnotes or brackets.", ""]
    (OUT / "bsb-comparison.md").write_text("\n".join(md))

    with open(OUT / "bsb-scores.tsv", "w") as fh:
        cols = [p + n for n, _, _ in pairs for p in ("w_", "c_")]
        fh.write("ref\t" + "\t".join(c.replace("w_", "wording_").replace("c_", "content_") for c in cols) + "\n")
        for r in rows:
            fh.write(r["ref"] + "\t" + "\t".join(f"{r[c]:.3f}" for c in cols) + "\n")
    print("\n".join(md[:md.index("## Which verses each leaves out")]))


if __name__ == "__main__":
    main()
