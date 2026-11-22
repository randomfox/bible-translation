# Licence

This project combines its own work with third-party texts under their own terms. The table shows what applies to each part. Full legal texts are in [LICENSES/](LICENSES/).

| Part | Licence |
|---|---|
| The English translation, the comparison flags and notes, the context verdicts, the book summaries, the reports in `results/`, the scripts, and the structure of `bible.sqlite` | **CC0 1.0 Universal**: public domain dedication |
| Greek New Testament text (`source_verses` with `language = 'grc'`, `data/chapters/40_MAT` … `66_REV` `*.src.txt`) | SBLGNT, **CC BY 4.0**. Attribution is required, as given below |
| Hebrew and Aramaic Old Testament text (`source_verses` with `language = 'hbo'`, the OT `*.src.txt` files) | Westminster Leningrad Codex, **public domain**. Credited to the Open Scriptures Hebrew Bible |
| King James Version (`kjv_verses`, the `kjv` column of the chapter files) | **Public domain**, except under Crown patent in the United Kingdom |
| Scrivener 1894 Textus Receptus and World English Bible (used for comparison only) | **Public domain** |

## The translation: CC0 1.0

To the extent possible under law, the authors of this project have waived all copyright and related or neighbouring rights to the English translation and the other parts marked CC0 above. They are dedicated to the public domain under [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/) (full text: [LICENSES/CC0-1.0.txt](LICENSES/CC0-1.0.txt)). You may copy, modify, distribute, sell and adapt them without asking permission and without credit.

CC0 covers only this project's own contribution. It does not cover the third-party texts in the table.

### The New Testament translation and the SBLGNT

The English New Testament is translated from the SBL Greek New Testament and follows the readings its editor chose. The SBLGNT is licensed under CC BY 4.0, which requires credit to it wherever adapted material is shared. CC0 adds no conditions of its own, but anyone republishing the New Testament translation should include the SBLGNT credit below, to be safe. A short form is enough, for example in an app's About screen:

> New Testament translated from the SBL Greek New Testament, © 2010 Society of Biblical Literature and Logos Bible Software, CC BY 4.0.

The Old Testament translation has no such condition, because the Westminster Leningrad Codex is in the public domain.

## Attribution

**SBL Greek New Testament (SBLGNT)**
- Copyright 2010 by the [Society of Biblical Literature](https://sbl-site.org/) and [Logos Bible Software](https://www.logos.com/).
- Licensed under [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/) (full text: [LICENSES/CC-BY-4.0.txt](LICENSES/CC-BY-4.0.txt)).
- Source: <https://github.com/LogosBible/SBLGNT>, version 1.2.
- Changes made here:
  - The text was translated into English.
  - Verse references were mapped to KJV numbering.
  - Romans 16:25–27, which the SBLGNT omits from its main text, was added from the SBLGNT apparatus, in the wording shared by Westcott–Hort, Tregelles and NA28. It is marked `{WH Treg NA28}` in the source text.
- The Society of Biblical Literature and Logos Bible Software do not endorse this project.

**Open Scriptures Hebrew Bible (OSHB)**
- Original work of the Open Scriptures Hebrew Bible available at <https://github.com/openscriptures/morphhb>.
- The Hebrew text is the Westminster Leningrad Codex, which is in the public domain. The OSHB lemma and morphology data (CC BY 4.0) are not included here.
- Changes made here: the text was translated into English. Ketiv and qere are written inline as `(K)` and `[Q: ]`, and verse references were mapped to KJV numbering.

**King James Version**
- Text of 1769, from [eBible.org](https://ebible.org/), courtesy of the CrossWire Bible Society and eBible.org.
- It is public domain outside the United Kingdom. In the UK the right to print it is held under Crown letters patent by Cambridge University Press, Oxford University Press and Collins. Printing or importing printed copies there needs their permission.

**Scrivener's Textus Receptus (1894)**
- Edited by Dr Maurice A. Robinson, from <https://github.com/byztxt/greektext-scrivener>. Public domain.

**World English Bible**
- From eBible.org. Public domain. Used only as a baseline for the similarity scores in `results/`.
