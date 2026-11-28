# The Claude Translation

A new English translation of the 66-book Bible, made directly from the Hebrew, Aramaic and Greek and compared verse by verse with the King James Version. To read it on Android beside the KJV, the BSB, the WEB, the YLT and the original-language texts, use [Bible Reader](https://github.com/randomfox/bible-reader), which has it built in.

> **Made with Claude.** The translation, the comparison notes and the judgements about which reading a passage most likely means were all produced by Claude, Anthropic's AI model, working under the direction of the repository's owner. No human biblical scholars or translation committee have reviewed them. They will contain mistakes, some of them significant. Use the translation for reading and comparison, not as an authority, and check any passage that matters against established translations and the original texts.

## What's here

| | |
|---|---|
| **Bible Reader (Android)** | Now in its own repository, [randomfox/bible-reader](https://github.com/randomfox/bible-reader), with the APK on its [releases page](https://github.com/randomfox/bible-reader/releases/latest). It builds its copy of the translation from `bible.sqlite` here. |
| **The translation** | `data/translation/` holds one tab-separated file per chapter, `ref` then text. |
| **Comparison with the KJV** | 2,316 recorded differences, each with a category, a severity, both readings and the evidence: `data/compare/`, `results/flags.tsv`, and the summary page `results/kjv-comparison.html` (open it in a browser). Capitalised pronouns: the judgement calls and places to review are in `results/capitalisation-review.md`. 65 ambiguous high-impact passages were decided from their context, each with a confidence level and the other possible meanings: `results/ambiguities.tsv`. |
| **Database** | `bible.sqlite` has the translation, the KJV, the source texts and the differences, with the reading each passage most likely means. See [EXPORT.md](EXPORT.md). |
| **How it was made** | [INSTRUCTIONS.md](INSTRUCTIONS.md) gives the per-chapter method: translate from the source first, then compare with the KJV. `scripts/` holds the tools. |

## Capitalised pronouns

The translation capitalises pronouns that refer to God, Jesus or the Holy Spirit (He, Him, His, You, Your, My, Who), as the NKJV and NASB do. In Old Testament prophecy it does so only in passages the New Testament applies to Jesus. The original languages have no capital letters, so this is a reading aid, not part of the text.

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

The translation, the notes and the scripts are dedicated to the public domain under [CC0 1.0](LICENSES/CC0-1.0.txt). The New Testament is translated from the SBLGNT, © 2010 Society of Biblical Literature and Logos Bible Software, CC BY 4.0, so anyone republishing it should keep that credit. See [LICENSE.md](LICENSE.md) for every source's terms.
