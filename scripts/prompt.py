"""Print the agent prompt for a chunk id: python3 -I scripts/prompt.py 20"""
import json, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
o = {c["id"]: c for c in json.loads((ROOT / "data/chunks.json").read_text())}[int(sys.argv[1])]
segs = o["segments"]
a = "; then ".join(f"{d} chapters {x}-{y}" for d, x, y in segs)
s = ", ".join(f"data/compare/{d}/summary_{x:03d}-{y:03d}.md" for d, x, y in segs)
c = " and ".join(f"`python3 -I scripts/check.py {d} {x} {y}`" for d, x, y in segs)
print(f"Read {ROOT}/INSTRUCTIONS.md first and follow it exactly. Write each chapter's files as soon as that chapter is done.\n\n"
      f"Your assignment (chunk {o['id']}): {a} ({o['verses']} verses).\nSegment summaries to write: {s}.\n"
      f"Checks to run at the end (from the project root): {c}.")
