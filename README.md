# The Claude Translation

A new English translation of the 66-book Bible, made directly from the Hebrew, Aramaic and Greek and compared verse by verse with the King James Version. To read it on Android beside the KJV, the BSB, the WEB, the YLT and the original-language texts, use [Bible Reader](https://github.com/randomfox/bible-reader), which has it built in.

> **Made with Claude.** The translation, the comparison notes and the judgements about which reading a passage most likely means were all produced by Claude, Anthropic's AI model, working under the direction of the repository's owner. No human biblical scholars or translation committee have reviewed them. They will contain mistakes, some of them significant. Use the translation for reading and comparison, not as an authority, and check any passage that matters against established translations and the original texts.

## What's here

| | |
|---|---|
| **Bible Reader (Android)** | In its own repository, [randomfox/bible-reader](https://github.com/randomfox/bible-reader), with the APK on its [releases page](https://github.com/randomfox/bible-reader/releases/latest). It reads the translation from this repository's releases; see [Releases](#releases). |
| **The translation** | `data/translation/` holds one tab-separated file per chapter, `ref` then text. |
| **Comparison with the KJV** | 2,316 recorded differences, each with a category, a severity, both readings and the evidence: `data/compare/`, `results/flags.tsv`, and the summary page `results/kjv-comparison.html` (open it in a browser). Capitalised pronouns: the judgement calls and places to review are in `results/capitalisation-review.md`. 65 ambiguous high-impact passages were decided from their context, each with a confidence level and the other possible meanings: `results/ambiguities.tsv`. |
| **Comparison with the BSB** | A measured comparison with the Berean Standard Bible, using the KJV as a reference point: how alike the wording is overall and book by book, which verses each leaves out, recurring word choices, and where the BSB stands on the 13 major KJV differences. See `results/bsb-comparison.md`, with per-verse scores in `results/bsb-scores.tsv`, made by `scripts/compare_bsb.py`. Unlike the KJV comparison, it has no verse-by-verse review. |
| **Database** | `bible.sqlite` has the translation, the KJV, the source texts and the differences, with the reading each passage most likely means. See [EXPORT.md](EXPORT.md). |
| **How it was made** | [INSTRUCTIONS.md](INSTRUCTIONS.md) gives the per-chapter method: translate from the source first, then compare with the KJV. `scripts/` holds the tools. |

## Capitalised pronouns

The translation capitalises pronouns that refer to God, Jesus or the Holy Spirit (He, Him, His, You, Your, My, Who), as the NKJV and NASB do. In Old Testament prophecy it does so only in passages the New Testament applies to Jesus. The original languages have no capital letters, so this is a reading aid, not part of the text.

## Releases

The translation is released for Bible Reader as GitHub releases tagged `ct-<version>`. Each holds `CT.db.gz`, the translation and its notes as one SQLite pack, and `ct.json`, with its version, size, SHA-256 and download URL. The version is a hash of the pack's contents. Bible Reader bundles the [latest release](https://github.com/randomfox/bible-translation/releases/latest) when it is built, and installed apps offer it as an update on their Translations screen.

To release a change to the translation:

1. Rebuild `bible.sqlite` with `scripts/export.py`, then commit and push.
2. Run `scripts/release.sh`, optionally with release notes as its argument. It builds the pack with `scripts/pack.py`, tags the current commit and publishes the release as the latest. If the translation hasn't changed since the last release, it does nothing.

Keep the latest release a translation release: Bible Reader reads `ct.json` from whichever release GitHub marks as latest.

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
