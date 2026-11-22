"""Aggregate translations + comparison flags into measured results.

Writes results/stats.json and results/flags.tsv, prints a summary.

Similarity (per chapter, bag-of-words over normalised English):
  mine~KJV, mine~WEB, WEB~KJV  (WEB = World English Bible, an independent modern translation from the originals).
If mine~KJV were far above WEB~KJV that would suggest KJV wording leaked into the translation.
"""
import json, re, collections, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
OUT = ROOT / "results"

ARCH = {"thee": "you", "thou": "you", "thy": "your", "thine": "your", "ye": "you", "hath": "has", "doth": "does",
        "art": "are", "shalt": "shall", "wilt": "will", "hast": "have", "dost": "do", "unto": "to", "saith": "says",
        "yahweh": "lord", "jehovah": "lord", "ghost": "spirit", "spake": "spoke", "begat": "fathered"}
STOP = set("the a an and of to in that is was be it for on with as by he she they them his her their i you we "
           "me my our us him its this which who not are were from at so but or have has had will shall all".split())


def norm(text):
    text = re.sub(r"[\[\]]", "", text.lower())
    toks = re.findall(r"[a-z]+", text)
    out = []
    for t in toks:
        t = ARCH.get(t, t)
        if len(t) > 4 and t.endswith("eth"):
            t = t[:-3]
        elif len(t) > 4 and t.endswith("est"):
            t = t[:-3]
        if t not in STOP:
            out.append(t)
    return out


def f1(a, b):
    ca, cb = collections.Counter(a), collections.Counter(b)
    ov = sum((ca & cb).values())
    if not ov:
        return 0.0
    p, r = ov / sum(ca.values()), ov / sum(cb.values())
    return 2 * p * r / (p + r)


def load_web():
    web = {}
    for line in (ROOT / "cache/sources/web/eng-web_vpl.txt").read_text(encoding="utf-8-sig").splitlines():
        m = re.match(r"(\S+) (\d+):(\d+) (.*)", line)
        if m:
            web[(m[1], int(m[2]), int(m[3]))] = m[4]
    return web


def main():
    books = json.loads((DATA / "books.json").read_text())
    web = load_web()
    OUT.mkdir(exist_ok=True)
    flags, per_book = [], []
    sims = {"mine_kjv": [], "mine_web": [], "web_kjv": []}
    for b in books:
        tdir = DATA / "translation" / b["dir"]
        cdir = DATA / "compare" / b["dir"]
        done_ch, nverses, flagged_refs, bsims = 0, 0, set(), collections.defaultdict(list)
        bflags = collections.Counter()
        for c in range(1, b["chapters"] + 1):
            tf = tdir / f"{c:03d}.txt"
            if not tf.exists():
                continue
            done_ch += 1
            mine = [l.split("\t", 1)[1] for l in tf.read_text().splitlines() if "\t" in l]
            nverses += len(mine)
            kjv_lines = (DATA / "chapters" / b["dir"] / f"{c:03d}.kjv.txt").read_text().splitlines()
            kjv_refs = [tuple(map(int, l.split("\t")[0].split(":"))) for l in kjv_lines if "\t" in l]
            kjv_txt = " ".join(l.split("\t", 1)[1] for l in kjv_lines if "\t" in l)
            web_txt = " ".join(web.get((b["code"], cc, vv), "") for cc, vv in kjv_refs)
            m, k, w = norm(" ".join(mine)), norm(kjv_txt), norm(web_txt)
            if w:
                for key, val in (("mine_kjv", f1(m, k)), ("mine_web", f1(m, w)), ("web_kjv", f1(w, k))):
                    sims[key].append((val, len(m)))
                    bsims[key].append((val, len(m)))
            cf = cdir / f"{c:03d}.tsv"
            if cf.exists():
                for line in cf.read_text().splitlines()[1:]:
                    p = line.split("\t")
                    if len(p) == 6 and p[2] in "123":
                        flags.append([b["name"], b["testament"], *p])
                        flagged_refs.add(p[0])
                        bflags[p[1]] += 1
                        bflags[f"sev{p[2]}"] += 1
        if done_ch:
            wavg = lambda xs: round(sum(v * n for v, n in xs) / max(1, sum(n for _, n in xs)), 3)
            per_book.append(dict(book=b["name"], testament=b["testament"], chapters_done=done_ch,
                                 chapters=b["chapters"], verses=nverses, flagged_verses=len(flagged_refs),
                                 flags=sum(v for k, v in bflags.items() if not k.startswith("sev")),
                                 by_category={k: v for k, v in bflags.items() if not k.startswith("sev")},
                                 by_severity={k: v for k, v in bflags.items() if k.startswith("sev")},
                                 sim={k: wavg(v) for k, v in bsims.items()}))
    wavg = lambda xs: round(sum(v * n for v, n in xs) / max(1, sum(n for _, n in xs)), 3)
    cat = collections.Counter(f[3] for f in flags)
    sev = collections.Counter(f[4] for f in flags)
    tot_v = sum(p["verses"] for p in per_book)
    tot_fv = sum(p["flagged_verses"] for p in per_book)
    stats = dict(books_touched=len(per_book), verses_translated=tot_v, flags=len(flags),
                 flagged_verses=tot_fv, flagged_verse_pct=round(100 * tot_fv / max(1, tot_v), 2),
                 by_category=dict(cat.most_common()), by_severity=dict(sorted(sev.items())),
                 by_testament={t: dict(collections.Counter(f[3] for f in flags if f[1] == t)) for t in ("OT", "NT")},
                 similarity={k: wavg(v) for k, v in sims.items()}, books=per_book)
    (OUT / "stats.json").write_text(json.dumps(stats, indent=1, ensure_ascii=False))
    with open(OUT / "flags.tsv", "w") as fh:
        fh.write("book\ttestament\tref\tcategory\tseverity\tkjv\tmine\tnote\n")
        for f in flags:
            fh.write("\t".join(f) + "\n")
    print(json.dumps({k: v for k, v in stats.items() if k != "books"}, indent=1))


if __name__ == "__main__":
    main()
