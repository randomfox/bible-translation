# Translation agent instructions

You are translating part of the Bible directly from the original languages into English, then
comparing your translation with the King James Version (KJV). All paths below are relative to the
repository root.

## Inputs (read-only)

For every chapter, in `data/chapters/NN_CODE/`:

- `CCC.src.txt` — original-language text, one verse per line: `chapter:verse<TAB>text`.
  - OT: Westminster Leningrad Codex (Masoretic Text, Hebrew; Aramaic in parts of Daniel/Ezra and Jer 10:11, Gen 31:47).
    `word(K)` marks a ketiv (written form) and `[Q: ...]` the qere (read form) that follows it.
    A tag like `3:2 [=KJV 3:1]` means the KJV numbers that verse differently (e.g. Psalm titles are verse 1 in Hebrew).
  - NT: SBL Greek New Testament (modern critical text).
- `CCC.kjv.txt` — the KJV for the same passage, in KJV numbering. `[brackets]` = words the KJV printed in italics (supplied, not in its source).
- `CCC.tr.txt` (NT only) — Scrivener's 1894 Textus Receptus, the Greek text underlying the KJV NT, unaccented.

## Outputs (write these)

1. `data/translation/NN_CODE/CCC.txt` — your translation, exactly one line per source verse, same refs, same order:
   `chapter:verse<TAB>English text`. Drop any `[=KJV …]` tag from the ref (just `3:2`). No blank lines, no headings, no commentary.
2. `data/compare/NN_CODE/CCC.tsv` — differences from the KJV, header line exactly:
   `ref<TAB>category<TAB>severity<TAB>kjv<TAB>mine<TAB>note`
   then one row per significant difference (zero rows is fine). Six tab-separated fields, no tabs or newlines inside a field.
   `kjv` and `mine` are the short phrases that differ (not whole verses). `note` is one or two sentences explaining what the
   original says and why the renderings differ; give the key Hebrew/Greek word in transliteration.
3. `data/compare/NN_CODE/summary_FFF-LLL.md` — one per book segment you cover (FFF/LLL = first/last chapter, 3 digits).
   See "Segment summary" below.

## Procedure — per chapter, in order

1. Read `CCC.src.txt` (NT: the SBLGNT `src` only). Do **not** look at the KJV yet.
2. Translate the whole chapter yourself from the original and write `data/translation/.../CCC.txt` in one Write call.
   Translate fresh from the text in front of you; do not recite any existing English version from memory.
3. Now read `CCC.kjv.txt` (NT: also `CCC.tr.txt`) and compare verse by verse. Write `CCC.tsv`.
   Do not revise your translation to match the KJV. You may fix a genuine error of your own you notice
   (wrong verse, skipped clause), but not adopt KJV wording.
4. Next chapter.

When your whole assignment is done, run `python3 -I scripts/check.py NN_CODE FIRST LAST` for each book segment,
fix every error it reports, then write the segment summaries.

## Translation standard

Aim for the original meaning in clear, contemporary English: accurate and fairly close to the source structure,
but idiomatic where a literal rendering would mislead. Translate every clause; add nothing that is not implied.
Where the text is genuinely ambiguous or obscure, pick the best-supported reading and (if it matters) note the
ambiguity in the compare file. Translate the qere unless the ketiv clearly gives better sense.

Shared conventions (so all agents are consistent):

| Source | Render as |
|---|---|
| יהוה YHWH | Yahweh (Adonai YHWH → "the Lord Yahweh") |
| אֲדֹנָי Adonai | Lord · אֱלֹהִים Elohim → God (or gods/divine beings by context) |
| אֵל שַׁדַּי El Shaddai | God Almighty · יהוה צְבָאוֹת → Yahweh of Armies |
| שְׁאוֹל Sheol | Sheol · חֶסֶד hesed → steadfast love / loyalty by context |
| נֶפֶשׁ nephesh, רוּחַ ruach | by context (life, self, person, throat / spirit, wind, breath) |
| κύριος | Lord · Χριστός → Christ (Messiah where clearly a title, e.g. "the Christ" in the Gospels) |
| πνεῦμα ἅγιον | Holy Spirit · ᾅδης Hades · γέεννα Gehenna · ἐκκλησία church (assembly for non-Christian gatherings) |
| δοῦλος | slave · ἀδελφοί → brothers (or brothers and sisters when clearly mixed) |
| ὁ υἱὸς τοῦ ἀνθρώπου | the Son of Man |
| Pronouns for God, Jesus, the Holy Spirit | Capitalised: He, Him, His, Himself, You, Your, Me, My, Mine, Who, Whom, Whose. OT messianic prophecy only where the NT applies the passage to Jesus |

## What to flag in the compare file

Flag only differences of **meaning**, not style. Do NOT flag: thee/thou/-eth, word order, synonyms
("said" vs "spoke"), "and it came to pass", conjunction handling, KJV italics that merely smooth English,
or capitalisation. If you would not mention it to a serious reader, leave it out.

Categories:

- `TEXT` — the difference comes from the underlying text, not translation: NT where the KJV follows the
  Textus Receptus against the critical text (quote the TR reading in the note); OT where the KJV followed
  the Septuagint/Vulgate/an emendation or a different Masoretic reading. Includes whole verses/clauses the KJV
  has that the source lacks (put the ref where it would sit; `mine` = "(absent)").
- `MEANING` — the KJV conveys a different sense than the source supports (a mistranslation by current
  knowledge of the language, wrong tense/subject, misread grammar).
- `INTERP` — the source is genuinely open and the KJV picks a different (defensible) reading than you, often
  theologically significant (e.g. "virgin" vs "young woman").
- `LEXICAL` — the meaning of a rare word is now better understood (animals, plants, objects, hapax legomena:
  "unicorn", "satyr", "dragon", "cockatrice").
- `ARCHAIC` — the KJV was accurate in 1611 but its English now misleads a modern reader ("prevent" = go before,
  "let" = hinder, "conversation" = conduct, "suffer" = allow, "meat" = food). Flag only where a reader would
  actually get the wrong idea.
- `NAME` — one-off name/title issues ("Lucifer", "Easter", "Jesus" for Joshua, "Jehovah" in a few verses).
  Systematic ones (LORD for YHWH, Holy Ghost, Christ/Messiah) go in the summary only, never per verse.

Severity: `1` nuance — a careful reader gets the gist from either; `2` notable — the clause means something
noticeably different; `3` major — substantially different meaning, or a difference in a historically /
doctrinally significant passage.

## Segment summary (`summary_FFF-LLL.md`)

Short markdown (under ~400 words):

- Book, chapters, verse count, number of flagged verses, and counts per category and severity.
- Systematic differences for this book (divine names, recurring terms, textual base).
- The most important differences (up to 8), each with ref, KJV vs yours, and a one-line explanation.
- One-paragraph overall assessment of how faithful the KJV is to the original in this section.

## Rules

- Work only inside this project's `data/translation` and `data/compare` folders; touch no other files.
- Do not use the web, do not spawn sub-agents, do not edit scripts or source data.
- Keep going until every chapter in your assignment is translated, compared, checked and summarised.
- Final reply to the coordinator (under 150 words): chapters and verses done, flag counts, check.py result,
  the 3 most significant findings, and any problem you hit.
