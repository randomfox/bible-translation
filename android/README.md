# Bible Reader (Android)

Bible Reader is an Android app for the translations in this repository. The Claude Translation (CT) is built in. The King James Version, Berean Standard Bible, World English Bible, Young's Literal Translation, the Hebrew and Greek source texts (WLC and SBLGNT) and the Greek Textus Receptus can be downloaded in the app (⋮ → Download translations), at 0.6–2.5 MB each. It is built with Kotlin and Jetpack Compose. The release APK is about 3.5 MB.

- **Reader:** a blue top bar with the reference and translation in the centre between previous and next chapter arrows, over a clean page with blue verse numbers and serif text. Swipe left or right to change chapter. It follows the phone's light or dark theme.
- **Quick Nav:** tap the reference or the list icon. Book names sit in three columns under swipeable Old and New Testament tabs, then a five-column grid of chapters. The translation button and Home are in its bar.
- **Compare translations** (the two-page button at the top left, or the ⋮ menu): tick any translations to show them under every verse of the one you're reading, lined up through the KJV keys. Where the numbering differs, the other reference is shown, for example `KJV 51:2` under Psalm 51:4. The bar lists every translation shown.
- **Switching translation** keeps the verse at the top of the screen, even where the two numberings differ.
- **Tap a verse** to open a panel with two tabs:
  - **Compare** shows the verse in every translation, the Hebrew or Greek included. Verses are matched through KJV numbering, so Malachi 3:19 in the Hebrew numbering lines up with KJV Malachi 4:1.
  - **Notes** shows every recorded difference from the KJV: its severity and category, both readings, the most likely meaning with its confidence, and the reasoning behind it.
- **Dots after verses** mark recorded differences. A light dot is a nuance, amber a notable change, and red a major one.
- **Your own notes:** in a verse's Notes tab, tap **+ Add note**. Notes use Markdown: `# headings`, `**bold**`, `*italic*`, `- lists`, `1. numbered lists`, `> quotes`, `` `code` ``, `~~strike~~`, and `---` rules. The editor has a toolbar for these and a **Preview** tab. A ✎ after a verse marks your note. Notes are tied to the KJV verse key, so a note shows under the same verse in every translation. **⋮ → My notes** lists them all, jumps to any one, and **Share all** exports them as one Markdown document. They're stored in their own database (`user.db`), separate from the translations, so updating or removing a translation never touches them, and Android's backup includes them.
- **Search:** full text search of the English translations. Use quotes for an exact phrase.
- **Other:** text size, notes on each book, and an About screen with each translation's licence and attribution.

Verses the KJV has but the critical text lacks, such as Matthew 17:21, appear in the Claude Translation as marked gaps that open their note.

## Opening a passage from another app

Other apps can open a passage with a link. Bible Year uses one to open each day's readings.

```
biblereader://BOOK/CHAPTER
biblereader://BOOK/CHAPTER/VERSE
```

For example `biblereader://JOH/3` or `biblereader://JOH/3/16`. BOOK can be the book's code (`JOH`), abbreviation (`John`), name (`1 John` or `1John`) or number from 1 to 66 (`43`), in any case. A chapter link opens the chapter at the top. A verse link scrolls to the verse and highlights it.

### References are in KJV numbering, whichever translation is open

The chapter and verse in a link are always read as a KJV reference, and Bible Reader opens the same text in the translation you are reading. It finds it through the KJV key that every verse in every translation carries, the same way it lines up translations for comparison.

For the KJV, BSB, YLT and TR, a link opens exactly the reference it names. The WEB differs in only 3 verses. But the Claude Translation and the Hebrew & Greek texts follow the numbering of the Hebrew Bible and the SBL Greek New Testament, which differs from the KJV in about 2,000 verses. There, a link can open a different chapter or verse number from the one it names:

| Link | Opens in the Claude Translation | Why |
|:--|:--|:--|
| `biblereader://MAL/4` | Malachi 3:19 | The Hebrew Bible has no Malachi 4. KJV 4:1–6 is Hebrew 3:19–24. |
| `biblereader://JOE/2/28` | Joel 3:1 | KJV Joel 2:28–32 is Hebrew Joel 3, and KJV Joel 3 is Hebrew Joel 4. |
| `biblereader://DAN/4/1` | Daniel 3:31 | KJV Daniel 4:1–3 is Hebrew 3:31–33. |
| `biblereader://GEN/32` | Genesis 32, at verse 2 | KJV Genesis 31:55 is Hebrew 32:1, so KJV chapter 32 begins at Hebrew verse 2. |
| `biblereader://PSA/51` | Psalm 51, at the top | Hebrew verses 1–2 are the psalm's title and verse 3 is KJV verse 1. All three share KJV 51:1, and the link opens at the first. |
| `biblereader://PSA/51/2` | Psalm 51:4 | Two verses ahead, because of the title. |
| `biblereader://2CO/13/14` | 2 Corinthians 13:13 | KJV 13:14 is verse 13 in the SBL Greek New Testament. |
| `biblereader://REV/13/1` | Revelation 12:18 | KJV 13:1 starts at 12:18 in the SBL Greek New Testament. |
| `biblereader://MAT/17/21` | Matthew 17:22 | The SBL Greek New Testament has no 17:21, so the link opens the next verse it has. |

In detail:

- **A chapter link** opens at the top of the chapter that holds the KJV chapter's first verse. If that chapter starts partway through a chapter in this numbering, it scrolls to the verse where it starts and highlights it, as for Malachi 4.
- **A verse link** highlights the verse that holds the KJV verse. Where several verses share one KJV verse, as in a psalm's title, it is the first of them.
- **A KJV verse the translation leaves out** opens the next verse the translation has.
- **A reference past the end of the book in KJV numbering** opens the book at chapter 1. For example, `biblereader://JOE/4`: Joel 4 exists only in the Hebrew numbering.
- **Don't send a Hebrew- or Greek-numbered reference.** `biblereader://MAL/3/24` is read as KJV Malachi 3:24. KJV Malachi 3 has 18 verses, so the link opens the next KJV verse, 4:1, which is Malachi 3:19 in the Claude Translation, not 3:24.

Switching to another translation afterwards keeps the verse, as usual.

### Checking for Bible Reader from another app

On Android 11 and later, an app can only see whether Bible Reader is installed if it lists the package in its manifest:

```xml
<queries>
    <package android:name="io.github.randomfox.bible" />
</queries>
```

## Install

Download the APK from the [latest release](https://github.com/randomfox/bible-translation/releases/latest) on your phone, open it, and allow installs from your browser or file manager when Android asks. The APK is signed with a development key, not a store key.

## Internet use and privacy

The app has the `INTERNET` permission and uses it only for translations:

- **When:** only when you open the Translations screen (⋮ → Download translations, or **Get more**), which fetches `catalog.json`, and when you tap **Download** or **Update**, which fetches that translation's pack. Nothing runs in the background or at startup.
- **Where:** only the GitHub release [`translations`](https://github.com/randomfox/bible-translation/releases/tag/translations) on github.com, which redirects downloads to githubusercontent.com.
- **What is sent:** ordinary HTTPS requests for those files. There is no account, analytics, advertising, crash reporting or tracking, and nothing you read, write in your notes or search leaves the phone. GitHub sees your IP address, as with any download.
- **Offline:** reading, comparing, notes and search need no connection. The Claude Translation is built in, and downloaded translations stay on the phone.

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
