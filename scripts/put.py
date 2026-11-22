"""Helper for inline translation.

  put.py src BOOKDIR CH [CH2]   print source chapter(s), cantillation stripped (NT: SBLGNT)
  put.py kjv BOOKDIR CH         print KJV (and TR for NT) for chapter
  put.py tr  BOOKDIR CH         stdin "c:v text" lines -> data/translation/BOOKDIR/CCC.txt
  put.py cmp BOOKDIR CH         stdin "ref|CAT|sev|kjv|mine|note" lines -> data/compare/BOOKDIR/CCC.tsv
  put.py todo [N]               list next N chapters lacking translation or compare
"""
import sys, re, json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
D = ROOT / "data"
HDR = "ref\tcategory\tseverity\tkjv\tmine\tnote"


def strip(s):
    return re.sub(r"[֑-ֽ֯׀׃-׆]", "", s).replace("׃", "")


def main():
    m, *a = sys.argv[1:]
    if m == "src":
        for c in range(int(a[1]), int(a[-1]) + 1):
            print(f"## {a[0]} {c}")
            print(strip((D / "chapters" / a[0] / f"{c:03d}.src.txt").read_text()), end="")
    elif m == "kjv":
        c = int(a[1])
        print((D / "chapters" / a[0] / f"{c:03d}.kjv.txt").read_text().replace("¶ ", ""), end="")
        tr = D / "chapters" / a[0] / f"{c:03d}.tr.txt"
        if tr.exists():
            print("## TR")
            print(tr.read_text(), end="")
    elif m == "tr":
        c = int(a[1])
        want = [l.split("\t")[0].split(" ")[0] for l in (D / "chapters" / a[0] / f"{c:03d}.src.txt").read_text().splitlines()]
        out = []
        for line in sys.stdin.read().splitlines():
            line = line.strip()
            if line:
                ref, txt = line.split(" ", 1)
                out.append(f"{ref}\t{txt.strip()}")
        got = [l.split("\t")[0] for l in out]
        if got != want:
            print("REF MISMATCH", [r for r in want if r not in got], [r for r in got if r not in want])
        p = D / "translation" / a[0] / f"{c:03d}.txt"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("\n".join(out) + "\n")
        print(f"wrote {p.name} {len(out)}/{len(want)} verses")
    elif m == "cmp":
        c = int(a[1])
        rows = [HDR]
        for line in sys.stdin.read().splitlines():
            if line.strip():
                p = [x.strip() for x in line.split("|")]
                if len(p) != 6:
                    print("BAD ROW", line)
                    continue
                rows.append("\t".join(p))
        p = D / "compare" / a[0] / f"{c:03d}.tsv"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("\n".join(rows) + "\n")
        print(f"wrote {p.parent.name}/{p.name} {len(rows) - 1} flags")
    elif m == "todo":
        n = int(a[0]) if a else 10
        for b in json.loads((D / "books.json").read_text()):
            for c in range(1, b["chapters"] + 1):
                t = (D / "translation" / b["dir"] / f"{c:03d}.txt").exists()
                k = (D / "compare" / b["dir"] / f"{c:03d}.tsv").exists()
                if not (t and k):
                    print(b["dir"], c, "T" if t else "-", "K" if k else "-")
                    n -= 1
                    if not n:
                        return


if __name__ == "__main__":
    main()
