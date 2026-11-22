# Bible: Android app

A Bible reader for the translations in this repository. It is built with Kotlin and Jetpack Compose, has no network access, and keeps all its text in one bundled SQLite database.

- **Reader:** a blue top bar and a clean page, with blue verse numbers and serif text. Swipe left or right to change chapter. It follows the phone's light or dark theme.
- **Book and chapter picker:** tap the title. The books are in Old and New Testament tabs, then a grid of chapter numbers.
- **Tap a verse** to open a panel with two tabs:
  - **Compare** shows the verse in every translation, the Hebrew or Greek included. Verses are matched through KJV numbering, so Malachi 3:19 in the Hebrew numbering lines up with KJV Malachi 4:1.
  - **Notes** shows every recorded difference from the KJV: its severity and category, both readings, the most likely meaning with its confidence, and the reasoning behind it.
- **Dots after verses** mark recorded differences. A light dot is a nuance, amber a notable change, and red a major one.
- **Search:** full text search of the English translations. Use quotes for an exact phrase.
- **Other:** text size, notes on each book, and an About screen with each translation's licence and attribution.

Verses the KJV has but the critical text lacks, such as Matthew 17:21, appear in the Fresh Translation as marked gaps that open their note.

## Building

You need JDK 17 or later, the Android SDK (platform 36), and Python 3. The build runs `scripts/app_db.py` first, which regenerates `app/src/main/assets/bible.db` from `../bible.sqlite`.

```bash
cd android
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` makes a minified build. It is signed with the debug key for now, so before publishing to a store, set up a real signing key.

## Adding a translation

The app database holds any number of translations. Each translation keeps its own verse numbering, and each verse is tied to one or more KJV references (`verse_keys`). The Compare tab, translation switching and notes all work through those keys.

To add one:

1. In `scripts/app_db.py`, write a loader that returns a `Translation`, using `load_kjv` as the simplest example. For each verse, give `(book_id, chapter, verse, text, [(kjv_chapter, kjv_verse), ...])`. If the translation uses KJV numbering, the key is just its own reference.
2. Set `markup`. Use `italic` if `[words]` mark supplied words, or `keep` if brackets are part of the text.
3. Set `searchable`, and fill in `license` and `attribution`, which the About screen shows.
4. Add the loader to `TRANSLATIONS` and rebuild. The app needs no code changes.

Notes (`notes`) and book notes (`book_notes`) belong to the translation that made them. They are keyed by KJV reference, so they show up under the same verse in every translation.
