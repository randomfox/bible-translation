"""Validate translation + comparison files.

usage: python3 -I scripts/check.py [BOOKDIR [FIRST_CH LAST_CH]]   e.g. 01_GEN 1 25
With no args, checks everything and prints totals.
"""
import sys, re, json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
CATS = {"TEXT", "MEANING", "INTERP", "LEXICAL", "ARCHAIC", "NAME"}


def refs(path):
    out = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if "\t" in line:
            out.append(line.split("\t")[0].split(" ")[0])
    return out


def check_chapter(bdir, ch):
    errs = []
    src = DATA / "chapters" / bdir / f"{ch:03d}.src.txt"
    tr = DATA / "translation" / bdir / f"{ch:03d}.txt"
    cmp_ = DATA / "compare" / bdir / f"{ch:03d}.tsv"
    if not tr.exists():
        return [f"{bdir} {ch}: missing translation"], 0, 0
    want = refs(src)
    got = []
    for i, line in enumerate(tr.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 2 or not re.fullmatch(r"\d+:\d+", parts[0]) or not parts[1].strip():
            errs.append(f"{tr.name}:{i} bad line: {line[:60]!r}")
            continue
        got.append(parts[0])
    if got != want:
        miss = [r for r in want if r not in got]
        extra = [r for r in got if r not in want]
        errs.append(f"{bdir} {ch}: verse mismatch missing={miss[:8]} extra={extra[:8]} order_ok={sorted(got)==sorted(want)}")
    nflags = 0
    if not cmp_.exists():
        errs.append(f"{bdir} {ch}: missing compare tsv")
    else:
        lines = cmp_.read_text(encoding="utf-8").splitlines()
        if not lines or lines[0].split("\t")[:3] != ["ref", "category", "severity"]:
            errs.append(f"{cmp_.name}: bad/missing header")
        for i, line in enumerate(lines[1:], 2):
            if not line.strip():
                continue
            p = line.split("\t")
            if len(p) != 6 or p[1] not in CATS or p[2] not in {"1", "2", "3"}:
                errs.append(f"{bdir}/{cmp_.name}:{i} bad row ({len(p)} cols): {line[:80]!r}")
            else:
                nflags += 1
    return errs, len(got), nflags


def main():
    books = json.loads((DATA / "books.json").read_text())
    a = sys.argv[1:]
    todo = []
    for b in books:
        if a and b["dir"] != a[0]:
            continue
        lo, hi = (int(a[1]), int(a[2])) if len(a) == 3 else (1, b["chapters"])
        todo += [(b, c) for c in range(lo, hi + 1)]
    allerr, nv, nf = [], 0, 0
    for b, c in todo:
        e, v, f = check_chapter(b["dir"], c)
        allerr += e; nv += v; nf += f
    for b in {b["dir"] for b, _ in todo}:
        if not list((DATA / "compare" / b).glob("summary*.md")) and (DATA / "translation" / b).exists():
            allerr.append(f"{b}: missing summary.md")
    print("\n".join(allerr[:60]) or "OK")
    print(f"chapters={len(todo)} verses_translated={nv} flags={nf} errors={len(allerr)}")


if __name__ == "__main__":
    main()
