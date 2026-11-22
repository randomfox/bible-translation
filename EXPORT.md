# bible.sqlite

This is one SQLite file holding the complete translation, ready to load into a Bible reading app. It also holds the King James Version, the Hebrew, Aramaic and Greek source texts, and every recorded difference from the KJV.

To rebuild it from the project data, run:

```bash
python3 -I scripts/export.py
```

The script writes `bible.sqlite` in the project root. You can pass a different output path as an argument.

## Tables

| Table | One row per | Purpose |
|---|---|---|
| `meta` | setting | Title, build date, counts, sources and licences, and the meaning of the markers and codes |
| `books` | book (66) | Canonical order (`id` 1–66), code (`GEN`…`REV`), OSIS name, testament, chapter and verse counts |
| `verses` | source verse (31,155) | **The translation.** It uses the source numbering and also gives the first matching KJV reference |
| `verse_map` | source verse ↔ KJV verse | Full mapping between the two numberings, many-to-many where they differ |
| `kjv_verses` | KJV verse (31,102) | KJV text in KJV numbering. `[brackets]` mark the words the KJV printed in italics |
| `source_verses` | source verse | Hebrew/Aramaic (WLC, `hbo`) or Greek (SBLGNT, `grc`). OT: `(K)` = ketiv, `[Q: ]` = qere |
| `differences` | flagged difference (2,320) | The KJV reading, this translation's reading, **the reading the text most likely means**, and the evidence |
| `book_notes` | book summary | Markdown summary of each book's comparison |

### Verse numbering

The `verses` table follows the source texts: Hebrew numbering for the Old Testament and SBLGNT numbering for the New. In 1,976 verses this differs from the KJV:

- Psalm titles count as verse 1 in Hebrew.
- Malachi 3:19–24 in Hebrew is KJV 4:1–6.
- Joel 3–4 in Hebrew is KJV 2:28–3:21.
- In the NT a few verses are split or joined, for example 2 Cor 13:12–14 and 3 John 14–15.

`kjv_chapter`/`kjv_verse` on each verse gives the first matching KJV verse, which is enough for "jump to" navigation. For a side-by-side view, use `verse_map`, which lists every KJV verse that a source verse covers.

Sixteen KJV verses have no source verse because the source texts do not contain them. Fifteen are NT verses absent from the critical text (Matt 17:21, Acts 8:37 and others). The sixteenth is Neh 7:68. Each of these verses still has a row in `differences`, keyed by its KJV reference, with `translation_reading = '(absent)'`. The same marker is also used for shorter words and phrases the source lacks.

### Markers in the translation text

- `[[ … ]]` marks a passage judged to be a later addition: Mark 16:9–20, John 7:53–8:11, Luke 22:43–44 and 23:34a.
- `[ … ]` marks doubtful words that the source edition prints in brackets.

### The `differences` table

| Column | Meaning |
|---|---|
| `category` | What causes the difference: `TEXT` (different manuscript), `MEANING`, `INTERP`, `LEXICAL` (word sense), `ARCHAIC` (KJV English has shifted), `NAME` |
| `severity` | 1 = a nuance; 2 = a notable change in sense; 3 = a change in what the passage says |
| `kjv_reading` / `translation_reading` | The two wordings being compared |
| `preferred` | `translation`: this translation's reading is what the text most likely means. `either`: the KJV reading is equally valid (21 cases where the evidence is balanced). `kjv`: reserved; no current difference prefers the KJV |
| `preferred_reading` | The reading judged most likely. For ambiguous high-impact passages it is the context verdict, which may be fuller than the translation's own wording |
| `confidence` | Set only where a context verdict was made (65 passages). `high` means the context points clearly one way; `medium` means it favours one reading; `low` means it does not settle the question. NULL elsewhere |
| `context` | The contextual argument behind the verdict |
| `note` | Manuscript evidence, grammar and other explanation |

1 Chronicles 21:1 has a row with `preferred = 'either'`. After the context review, this translation agrees with the KJV there, so the row records the verdict but is not counted as a difference.

## Example queries

A chapter of the translation:

```sql
SELECT verse, text FROM verses
WHERE book_id = (SELECT id FROM books WHERE code = 'JOH') AND chapter = 3
ORDER BY verse;
```

The translation and the KJV side by side, in KJV numbering:

```sql
SELECT m.kjv_verse, v.text AS translation, k.text AS kjv
FROM verse_map m
JOIN verses v     ON v.book_id = m.book_id AND v.chapter = m.chapter AND v.verse = m.verse
JOIN kjv_verses k ON k.book_id = m.book_id AND k.chapter = m.kjv_chapter AND k.verse = m.kjv_verse
WHERE m.book_id = (SELECT id FROM books WHERE code = 'MAL') AND m.kjv_chapter = 4
ORDER BY m.kjv_verse;
```

Differences to show under a verse, the most serious first:

```sql
SELECT severity, category, kjv_reading, preferred_reading, confidence, note
FROM differences
WHERE book_id = ? AND chapter = ? AND verse = ?
ORDER BY severity DESC;
```

The major differences:

```sql
SELECT b.name, d.chapter, d.verse, d.kjv_reading, d.preferred_reading
FROM differences d JOIN books b ON b.id = d.book_id
WHERE d.severity = 3 ORDER BY b.id, d.chapter, d.verse;
```

## Sources and licences

The `meta` table lists these as well.

- **Old Testament:** Westminster Leningrad Codex, through the Open Scriptures Hebrew Bible. The text is in the public domain.
- **New Testament:** SBL Greek New Testament, CC BY 4.0. Attribution is required if you redistribute `source_verses`. Romans 16:25–27, which the SBLGNT leaves out, comes from its apparatus.
- **KJV:** from eBible.org. It is public domain except in the UK, where it is under Crown patent.
- **Translation and comparison notes:** produced in this project. No licence has been set yet.
