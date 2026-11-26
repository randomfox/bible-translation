# The Claude Translation

A new English translation of the 66-book Bible, made directly from the Hebrew, Aramaic and Greek and compared verse by verse with the King James Version. The repository also has an Android app, **Bible Reader**, that puts the translation beside the KJV, the BSB, the WEB, the YLT and the original-language texts.

> **Made with Claude.** The translation, the comparison notes, the judgements about which reading a passage most likely means, and the Android app were all produced by Claude, Anthropic's AI model, working under the direction of the repository's owner. No human biblical scholars or translation committee have reviewed them. They will contain mistakes, some of them significant. Use the translation for reading and comparison, not as an authority, and check any passage that matters against established translations and the original texts.

## What's here

| | |
|---|---|
| **Bible Reader (Android)** | [Download the APK](https://github.com/randomfox/bible-translation/releases/latest). The Claude Translation is built in. More translations can be downloaded in the app. See [android/README.md](android/README.md). |
| **The translation** | `data/translation/` holds one tab-separated file per chapter, `ref` then text. |
| **Comparison with the KJV** | 2,316 recorded differences, each with a category, a severity, both readings and the evidence: `data/compare/`, `results/flags.tsv`, and the summary page `results/kjv-comparison.html` (open it in a browser). 65 ambiguous high-impact passages were decided from their context, each with a confidence level and the other possible meanings: `results/ambiguities.tsv`. |
| **Database** | `bible.sqlite` has the translation, the KJV, the source texts and the differences, with the reading each passage most likely means. See [EXPORT.md](EXPORT.md). |
| **How it was made** | [INSTRUCTIONS.md](INSTRUCTIONS.md) gives the per-chapter method: translate from the source first, then compare with the KJV. `scripts/` holds the tools. |

## Sources

- **Old Testament:** the Westminster Leningrad Codex, through the Open Scriptures Hebrew Bible.
- **New Testament:** the SBL Greek New Testament.
- **Comparison texts:** the KJV, from eBible.org, and Scrivener's Textus Receptus.

## Numbers

| | |
|---|---|
| Verses translated | 31,155, in Hebrew and SBLGNT numbering |
| Recorded differences from the KJV | 2,316, in 7.3% of verses |
| By severity | 1,970 nuances, 333 notable changes, 13 changes in what a passage says |
| Most common causes | interpretation 610, word sense 579, manuscript differences 477 |

## Licence

The translation, the notes, the scripts and the app are dedicated to the public domain under [CC0 1.0](LICENSES/CC0-1.0.txt). The New Testament is translated from the SBLGNT, © 2010 Society of Biblical Literature and Logos Bible Software, CC BY 4.0, so anyone republishing it should keep that credit. See [LICENSE.md](LICENSE.md) for every source's terms.
