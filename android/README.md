# Bible: Android app

A Bible reader for the translations in this repository. The Claude Translation (CT) is built in. The King James Version, Berean Standard Bible, World English Bible, Young's Literal Translation, the Hebrew and Greek source texts (WLC and SBLGNT) and the Greek Textus Receptus can be downloaded in the app (⋮ → Download translations), at 0.6–2.5 MB each. It is built with Kotlin and Jetpack Compose. The release APK is about 3.5 MB.

- **Reader:** a blue top bar with the reference and translation in the centre between previous and next chapter arrows, over a clean page with blue verse numbers and serif text. Swipe left or right to change chapter. It follows the phone's light or dark theme.
- **Quick Nav:** tap the reference or the list icon. Book names sit in three columns under swipeable Old and New Testament tabs, then a five-column grid of chapters. The translation button and Home are in its bar.
- **Compare translations** (the two-page button at the top left, or the ⋮ menu): tick any translations to show them under every verse of the one you're reading, lined up through the KJV keys. Where the numbering differs, the other reference is shown, for example `KJV 51:2` under Psalm 51:4. The bar lists every translation shown.
- **Switching translation** keeps the verse at the top of the screen, even where the two numberings differ.
- **Tap a verse** to open a panel with two tabs:
  - **Compare** shows the verse in every translation, the Hebrew or Greek included. Verses are matched through KJV numbering, so Malachi 3:19 in the Hebrew numbering lines up with KJV Malachi 4:1.
  - **Notes** shows every recorded difference from the KJV: its severity and category, both readings, the most likely meaning with its confidence, and the reasoning behind it.
- **Dots after verses** mark recorded differences. A light dot is a nuance, amber a notable change, and red a major one.
- **Search:** full text search of the English translations. Use quotes for an exact phrase.
- **Other:** text size, notes on each book, and an About screen with each translation's licence and attribution.

Verses the KJV has but the critical text lacks, such as Matthew 17:21, appear in the Claude Translation as marked gaps that open their note.

## How translations are stored and downloaded

Each translation is a **pack**: a small SQLite file built by `scripts/app_db.py`, with the layout described at the top of that script.

- The CT pack is bundled in the APK as `assets/core.db`. Its content hash is in `assets/core.version`.
- The other packs are gzipped into `dist/packs/<CODE>.db.gz`, listed in `dist/packs/catalog.json` with their size, SHA-256 and content version, and published as assets of the GitHub release [`translations`](https://github.com/randomfox/bible-translation/releases/tag/translations) by `scripts/publish_packs.sh`.
- The app keeps one writable library database (`library.db`). On first run, and whenever the APK carries a new CT version, it installs `core.db` into it. The Translations screen reads `catalog.json`, downloads a pack, checks its SHA-256, and copies the rows in, building a search index for English texts. **Update** appears when the catalog's version differs from the installed one. **Remove** deletes the rows.
- A verse's id is `translation_id × 10,000,000 + n`, so packs never collide. Translation ids are fixed and saved in the app's settings, so never change or reuse one.

## Building

You need JDK 17 or later, the Android SDK (platform 36), and Python 3. The build runs `scripts/app_db.py` first, which regenerates `app/src/main/assets/bible.db` from `../bible.sqlite`.

To rebuild and publish the downloadable packs (after changing a translation or adding one):

```bash
scripts/publish_packs.sh
```

To build the app:

```bash
cd android
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` makes a minified build of about 3.5 MB. It is signed with the debug key for now, so before publishing to a store, set up a real signing key.

## Adding a translation

The app database holds any number of translations. Each translation keeps its own verse numbering, and each verse is tied to one or more KJV references (`verse_keys`). The Compare tab, translation switching and notes all work through those keys.

To add one:

1. In `scripts/app_db.py`, write a loader that returns a `Translation`. For an eBible.org verse-per-line text, `vpl_loader` does it in one call, as for the BSB, WEB, YLT and TR. Give it a new `id`. Ids are saved in the app's settings, so never change or reuse one. For each verse, give `(book_id, chapter, verse, text, [(kjv_chapter, kjv_verse), ...])`. If the translation uses KJV numbering, the key is just its own reference.
2. Set `markup`. Use `italic` if `[words]` mark supplied words, or `keep` if brackets are part of the text.
3. Set `searchable`, and fill in `license` and `attribution`, which the About screen shows.
4. Add the loader to `TRANSLATIONS` and run `scripts/publish_packs.sh`. Installed apps see it on the Translations screen with no app update.

Notes (`notes`) and book notes (`book_notes`) belong to the translation that made them. They are keyed by KJV reference, so they show up under the same verse in every translation.
