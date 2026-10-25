# Day 2 progress

Decisions: [DECISIONS.md](DECISIONS.md). Mock-ups: [design-review.html](design-review.html).

## Sprint plan

There were no Sprint 1 or Sprint 2 branches when Sprint 3 started (2026-10-25); `day2/sprint-3` was
branched from `main`. The owner chose to build the **speech core first** and to place speakers and mics
on the screens after the layout sprint.

| Sprint | Scope | State |
|---|---|---|
| Design | Four review rounds, approved 2026-10-25 | Done |
| 3 (speech core) | TTS wrapper, voice check and prompt, Darija speech, speech rate, speech recognizer core | Done, see below |
| 3 (add-on) | Spoken-language setting: Darija (default) or standard Arabic | Done, see below |
| 4 (foundations) | Theme tokens, forced LTR, Arabic text and field components, top bar, speaker tile, Add button | Done, see below |
| 5 (invoice output) | Table option A on paper and on screen, PDF headers and footer, totals colours, one PNG or point to PDF, paid badge, stacked Saved buttons | Not started (moved before units) |
| 6 (units, fields, photos) | cm/m and unit-label settings, invoice unit, field icons, photo import, backup with images | Not started |
| 7 (placement) | Speakers on every row/card/field/CTA; mic on customer name and product name; dictation status line | Not started (needs 4 to 6) |

The step-by-step plan for sprints 4 to 7 and a starter prompt for the next session are in
[Next sprints, step by step](#next-sprints-step-by-step) at the end of this file.

## Sprint 3: speech core

### Delivered

* **Darija speech, offline** (`domain/speech`):
  * `DarijaTerms`: every spoken label, in Darija spelling, kept apart from screen and invoice text.
  * `DarijaNumbers`: numbers as Darija words (جوج, تلاتة, حضاش, عشرين, ميتين, تلت آلاف, مليون …),
    decimals ("جوج فاصلة تنين و خمسين"), metres ("متر و عشرين سانتي") and centimetres.
  * `DarijaSpeech`: prices (درهم / دراهم / سنتيم), quantities, measurements, and one natural sentence per
    row, per calculation card and per total + deposit + balance card. Method names come from one table,
    so no new `when (formula)` branch was added.
* **TextToSpeech wrapper** (`platform/SpeechController`, `platform/Speaker`):
  * offline voices only (a voice that needs the network counts as missing); ar-MA first, then any Arabic;
  * every speech uses `QUEUE_FLUSH`, so tapping another speaker stops the current one;
  * speech-rate setting applied on every utterance;
  * voice on/off is the existing audio switch; `Speaker` (through the pure `SpeechGate`) never speaks
    with audio off and never shows a prompt then;
  * automatic speech (totals on arrival) only plays when a voice is ready; it never prompts.
* **Missing Arabic voice**: the first speaker tap without an offline Arabic voice opens a clear dialog
  (`VoiceMissingDialog`) with "تثبيت صوت عربي" (opens the system voice-data screen) and "لاحقاً"; once per
  app run, later taps stay silent with a short toast. The flow re-checks the voice when it resumes.
  The Settings banner and install button were already there.
* **Existing speech switched to Darija**: product name on catalogue cards, item total on the confirmation
  screen, invoice summary on the review screen, "تجربة الصوت" in Settings.
* **Speech rate**: `AppSettings.speechRate` (slow 0.8 / normal 1.0 / fast 1.25), three large choices in the
  Settings audio section. Room schema **version 2** (`MIGRATION_1_2`, `app_settings.speechRate` default
  `normal`) with a migration test; backups carry the rate, and older backups restore with `normal`.
* **Speak-to-fill core** (`platform/Dictation`, `ui/components/MicButton`):
  * `SpeechRecognizer`, ar-MA, `EXTRA_PREFER_OFFLINE`; recognised text is tidied and limited to the
    field's length, then put in the field as ordinary editable text;
  * microphone permission (`RECORD_AUDIO`) requested on the first mic tap only, after a short
    explanation; later refusals only show a hint;
  * hint when the offline Arabic pack is missing (checked up front on Android 13+, otherwise from the
    recognizer's error), plus hints for no recogniser, nothing understood and busy;
  * `MicButton` is built but **not placed on any field yet** (placement sprint).

### Tests

| Test | Where | Result |
|---|---|---|
| Darija number words for prices, quantities, measurements; row, calculation and totals sentences | `domain` `SpeechTest` (13 tests) | Pass (JVM) |
| Voice off never speaks and never prompts; missing voice prompts once | `domain` `SpeechTest` | Pass (JVM) |
| Recognizer error mapping, recognised-text tidying | `domain` `SpeechTest` | Pass (JVM) |
| Speech rate in backups, older backups restore at normal | `domain` `SpeechTest` | Pass (JVM) |
| All existing domain tests | `./gradlew -p domain test` | Pass |
| Speaker tap on a product card never selects | `SpeakerTapTest` (instrumented) | Pass (phone) |
| Replay speaker on the confirmation never confirms or leaves | `SpeakerTapTest` (instrumented) | Pass (phone) |
| Voice off: speakers silent, no prompt, flow continues | `VoiceOffFlowTest` (instrumented) | Pass (phone) |
| Migration 1 → 2 keeps settings and adds the normal rate | `MigrationTest` (instrumented) | Pass (phone) |
| All instrumented tests | Samsung SM-S931B, Android 16 | **OK (49 tests)**: 48 pass, 1 skipped (`AppFolderStoreTest.a_…`, Android 7–9 only) |
| Build, lint | `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | Pass |
| Mic button only on text fields | (placement sprint) | Not yet testable: the mic is not placed |

### Verified on the emulator vs. must be tested on a real phone

The instrumented suite ran on the owner's phone (Samsung SM-S931B, Android 16) with the owner's approval,
after the API 37 emulator could not start the app in time on this PC (7.7 GB RAM; the app process hit an
"app not responding" timeout at startup and Gradle ran out of memory). The app on that phone holds only test
data. The suite was run with `adb shell am instrument` on the APKs built by `assembleDebug` /
`assembleDebugAndroidTest`.

**Verified automatically (JVM tests and on the phone):**

* Darija words for prices, quantities and measurements; the row, calculation-card and totals sentences.
* Speaker taps never select, confirm or navigate (product card, confirmation replay).
* With voice off, speakers are silent, no voice prompt appears, and the invoice flow continues.
* The missing-voice prompt logic (once per run, never with audio off); recognizer error to hint mapping.
* Schema 1 → 2 migration and backup compatibility of the speech rate.
* Build, lint and every existing test.

**Not verified yet; must be tested by hand on a real phone (ideally the owner's):**

* Whether an **offline Arabic voice** is installed, and what Settings → الصوت shows (ready / no Arabic voice).
* **Voice quality** and how the Darija spelling and the Darija **number words** actually sound
  ("ألفين و خمس مية و عشرين درهم", "متر و عشرين سانتي"); adjust `DarijaTerms` / `DarijaNumbers` if a voice
  mispronounces them.
* **Speech rate**: whether slow / normal / fast (0.8 / 1.0 / 1.25) feel right; "تجربة الصوت" at each.
* The **missing-voice dialog** on the first speaker tap, and the **voice download** flow through
  "تثبيت صوت عربي", then that the app picks up the new voice when it comes back.
* Tapping another speaker while one is talking stops the first.
* **Speak-to-fill** is not on any screen yet (placement sprint). When it is: the microphone permission
  explanation and system dialog on the first mic tap only, recognition of Moroccan Arabic names with the
  **offline ar-MA pack**, the hint when the pack is missing, and that the text stays editable.

## Sprint 3 add-on: spoken language (Darija or standard Arabic)

Requested by the owner after the speech core.

### Delivered

* `SpeechLanguage` setting (`darija` default, `standard`); Settings → الصوت → "لغة الكلام" with two choices
  in the same style as the speech rate (no speakers in Settings). "تجربة الصوت" speaks its sample in the
  chosen language.
* One interface, `SpeechText` (amount, quantity, length, rate, row, item total, calculation card, method,
  totals card, voice test), picked with `SpeechTexts.of(language)`:
  * Darija = `DarijaSpeech` (unchanged sentences);
  * standard Arabic = `StandardSpeech`, built on the existing `ArabicNumberWords` and `SpokenText`, plus
    the missing sentences (rows, calculation card, rates, metres/centimetres with "متر واحد / متران /
    أمتار", "سنتيمتر / سنتيمتران / سنتيمترات"). Method names come from one table per language.
* Room schema **version 3** (`MIGRATION_2_3`, `app_settings.speechLanguage` default `darija`); the v2
  schema is unchanged. Backups carry the language; older backups and unknown values restore as Darija.

### Tests

| Test | Where | Result |
|---|---|---|
| Standard Arabic prices, quantities, measurements, row, calculation and totals sentences; language switch; backup default | `domain` `StandardSpeechTest` (8 tests) | Pass (JVM) |
| All domain tests | `./gradlew -p domain test` | Pass (188 tests) |
| Build, lint | `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | Pass |
| Migration 2 → 3 keeps settings and adds Darija | `MigrationTest` (instrumented) | Pass (phone) |
| All instrumented tests | Samsung SM-S931B, Android 16 | 49 pass, 1 skipped (Android 7–9 only) of 50. The first run failed the 2 migration tests only because the test APK was packaged before `3.json` existed; after rebuilding it, `MigrationTest` is OK (3 tests). |

### Must be heard on a real phone

* The **standard-Arabic reading** with the installed Arabic voice: numbers ("ألفان وخمسمائة وعشرون درهم"),
  measurements ("متر واحد وعشرون سنتيمتر"), the calculation and totals sentences.
* Known simplification inherited from `SpokenText`: the number before a noun is not put in construct state
  ("مائتان درهم" rather than the formal "مائتا درهم"; "عشرون سنتيمتر" rather than "سنتيمتراً"). It is
  understandable when spoken; fix in `SpokenText` / `StandardSpeech` if the owner wants formal grammar.
* Switching the language in Settings and pressing "تجربة الصوت" in each language.

### Notes for the next sprints

* `Strings.AUDIO_TEST_SENTENCE` is no longer used (the voice test speaks `SpeechText.voiceTest` in the chosen language).
* Placement must use `Speaker.say(...)` for taps and `Speaker.sayIfReady(...)` for automatic speech, and
  `SpeechTexts.of(settings.speechLanguage)` for every sentence (never `DarijaSpeech` directly).
* Instrumented tests clear the app database: always target one device explicitly (`adb -s` /
  `ANDROID_SERIAL`), and never a phone with real invoices.
* On this PC the emulator and Gradle do not fit in memory together: build first, stop Gradle
  (`./gradlew --stop`), then start the emulator; or install the APKs and run `adb shell am instrument`.

## Sprint 4: foundations

Branch `day2/sprint-4` (from `day2/sprint-3`).

### Delivered

* **One palette** (`domain/palette/Palette`): every colour of DECISIONS.md as ARGB. The screen theme
  (`ui/theme/Theme.kt`) builds the Material `ColorScheme` and a small `AppPalette` (`MaterialTheme.appPalette`:
  paid green, balance yellow + pressed + edge, error, gray, gray-strong, surface, ink, banner colours) from it,
  and the printed invoice's `InvoiceColors` now points at the same table. `AppColors` is gone; no screen uses
  `AppColors.*` or `Color.White` any more.
  * Green means paid only: unpaid balances, item totals and invoice totals on screens are ink; the paid
    balance and the "خالص" badge stay green. On the **printed invoice** the unpaid balance is ink too, and the
    workshop name, muted text, dividers and panels use the new palette (rust is gone). The rest of the invoice
    layout is unchanged (sprint 5, invoice output).
  * Buttons: primary/success green with white, danger red with white, secondary white with a gray edge and
    ink text, accent yellow with ink text and a `#A67C00` edge. Home cards: green, yellow (dark text, dark
    edge), white (gray edge), as in the mock-up.
* **Forced LTR**: `MainActivity` and `InvoiceTheme` use `LayoutDirection.Ltr`. `ArabicText` (in
  `ui/components/Foundations.kt`) is a right-to-left paragraph, right-aligned; every screen text uses it.
  Every row was re-ordered so labels stay on the right and amounts on the left; icons sit on the right of
  their words. Choices read from the right (speech language, speech rate, metre/cm, picture grid). The renderer
  of the printed invoice is unchanged (still RTL).
  * One RTL island on purpose: the product-catalogue grid keeps today's order (first product top-right,
    decided); each card inside is LTR again. The category chips use `reverseLayout`.
* **`AppTextField`**: label above on the right (optional small hint such as "(اختياري)"), box below with
  right-aligned text (a phone number keeps its digit order), optional illustration beside it on the right,
  optional `trailing` slot inside the box on the left (for the mic in sprint 7), error line with icon. It
  replaces all 9 `OutlinedTextField`s (customer name/phone, product name, category, workshop name/phone/
  thank-you, history search). No illustrations are passed yet (field icons are sprint 6).
* **Arrows**: forward arrows point right, on the right of the label ("→ متابعة"); back arrow on the left.
* **`AppTopBar`** replaces `ScreenHeader` on every screen: back left (56 dp), title right with an optional
  speaker slot, optional centred 44 dp picture (item details and item confirmation) with the title limited
  to the right half and ending with "…". The mute chip of confirmation and review moved to `AppSubBar`, a
  slim line under the bar. On the confirmation screen the large picture moved into the bar (as in the mock-up).
* **`SpokenButton` / `SpeakerTile`**: `| speaker | button |`, the tile copies the button's colours, border,
  radius and height, 4 dp gap, at least 48 × 48 dp, own target that only speaks; stays usable (and speaks)
  when its button is disabled. Used only by the add button for now (placement is sprint 7).
* **"+ إضافة منتوج"** (`AddProductButton`): yellow `#F2B705`, ink text, `#A67C00` edge, 64 dp, plus in a dark
  circle on the right of the label, pressed state darker (`#D9A404`) and shrinking slightly. Shown in both the
  empty and the filled item list. Its tile speaks the new `SpeechText.addProduct` ("زيد منتوج" in Darija,
  "إضافة منتوج" in standard Arabic) through `Speaker.say`.
* `strictClickable` / `StrictButton` accept an interaction source (for the pressed state) and a content
  description (for icon-only buttons such as the tile).

### Tests

| Test | Where | Result |
|---|---|---|
| Add-product sentence in both languages | `domain` `StandardSpeechTest` | Pass (JVM) |
| All domain tests | `./gradlew -p domain test` | Pass (189 tests) |
| Build, lint | `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | Pass (lint: 0 errors, 62 warnings, none in changed code: Gradle versions, KTX, launcher icon) |
| Back arrow left, title right, home cards and Continue large | `LayoutAndBackUiTest` (rewritten for LTR) | Pass (phone) |
| Field labels and typed text right-aligned (Arabic name, phone number) | `LayoutAndBackUiTest` (new) | Pass (phone) |
| Yellow add button ≥ 64 dp; tile left of it, 4 dp gap, same height, ≥ 48 dp; a tile tap never opens the catalogue | `LayoutAndBackUiTest` (new) | Pass (phone) |
| Centred picture in the item-details bar, title in the right half; mute chip in the sub-bar on the left on confirmation | `LayoutAndBackUiTest` (new) | Pass (phone) |
| Catalogue first product top-right, speakers ≥ 48 dp, speaker tap and finger slide never select, keypad, system Back | `LayoutAndBackUiTest` (kept) | Pass (phone) |
| Export: payment summary found on PNG / last PNG / last PDF page | `ExportInstrumentedTest` | Pass (phone), see note |
| All instrumented tests | Samsung SM-S931B, Android 16 | **OK (53 tests)**: 52 pass, 1 skipped (`AppFolderStoreTest.a_…`, Android 7–9 only) |

Test changes besides `LayoutAndBackUiTest`:

* The add button's visible label is now "إضافة منتوج" (the plus is an icon), so the locator in
  `InvoiceFlowUiTest`, `ScreenshotTourTest` and `SpeechUiTest` changed from `"+ إضافة منتوج"` to
  `Strings.ADD_PRODUCT`. The flows themselves are unchanged.
* `ExportInstrumentedTest` found the payment summary by the old rust balance colour, which no longer exists
  (unpaid balance = ink). It now looks for the summary box itself: the tallest block in the invoice panel
  colour (the column-title strip has the same fill but is one line high), and also checks that page 1 of the
  long PDF has no summary. The first run of the suite failed only these 2 tests for that reason.

### Verified on a device vs. must be checked by hand

The suite ran on the owner's Samsung SM-S931B (Android 16, test data only) with the owner's approval,
targeted with `adb -s`, after building and stopping Gradle.

**Verified automatically:** LTR placement of the back arrow, titles, field labels, typed text, the add
button and its tile, the centred picture and the sub-bar; touch-target sizes; speaker taps never act;
every existing flow, export, backup, migration and speech test.

**Must be checked by eye on the phone (not covered by tests):**

* Colours on every screen against the mock-ups: the yellow add button and the yellow history card (dark text,
  dark edge), the white settings card, white secondary buttons with a gray edge, banners (info gray, warning
  pale yellow, error pale red, success pale green).
* The add button's **pressed state** (darker yellow, slight shrink) is visible under a real finger.
* Arrow directions and icon sides: "→ متابعة", icons on the right of every button label and banner, the
  forward arrow at the left of the Settings cards pointing right.
* Arabic text that starts with digits or Latin (amounts "2 520,00 DH", "DH / م²", the customer line
  "أحمد • 0612 …", invoice numbers, dates) reads in the right order and sits on the right.
* Long product names in the item-details bar end with "…" before the centred picture.
* Dialogs (save, delete, leave, restore, voice missing, mic listening): title and text on the right,
  cancel on the left and confirm on the right; the Material date picker in history.
* The printed invoice (PNG and PDF) with the new colours: workshop name green, unpaid balance ink.
* The add button's speaker tile with the voice on: it says "زيد منتوج" (Darija) / "إضافة منتوج" (standard).

### Notes for the next sprints

* Use `ArabicText` for every new screen text, `AppTextField` for text input, `AppTopBar` for headers,
  `SpokenButton` for labelled buttons and colours only from `MaterialTheme.colorScheme` / `appPalette`.
* In LTR a `Column` aligns children left: give content columns `horizontalAlignment = Alignment.End`
  (`SectionCard` already does) and put the right-hand element last in a `Row`.
* The empty item list card is still a button (it opens the catalogue); the mock-up makes it a message with
  its own speaker: change it with the speaker placement in sprint 7.

## Next sprints, step by step

Rules for every sprint below:

* Branch `day2/sprint-N` from the previous sprint's branch (sprint 4 from `day2/sprint-3`). Do not push
  unless the owner asks.
* Read `CLAUDE.md`, [DECISIONS.md](DECISIONS.md) and the mock-ups in [design-review.html](design-review.html)
  first. Build only what the sprint lists.
* Definition of done: `./gradlew -p domain test`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`,
  `:app:lintDebug`, the instrumented suite on **one** explicitly targeted device (ask the owner before
  using the phone), this file updated (delivered, tests, verified vs. still to check by hand), one commit.
* Every Room version bump ships a migration, a `MigrationTest` case and the committed schema JSON.
  The schema is at version 3 now, so the next bump is 4.
* No new `when (formula)` branches: calculation stays pluggable.

### Sprint 4: foundations (theme, LTR, base components)

1. **Theme tokens.** Replace `AppColors` with the palette in DECISIONS.md as `ColorScheme` tokens plus a
   small `AppPalette` (paid green, balance yellow, error, gray, surface). Remove every direct
   `AppColors.*` and hard-coded `Color.White` use in the 8 UI files (Basics, Inputs, Home,
   CustomerAndItems, ReviewAndSaved, History, InvoiceDetail, Catalog). Put the printed-invoice colours
   (`InvoiceColors` in the domain) in one shared palette table so screens and invoice cannot drift.
   Unpaid balance becomes ink; green means paid only.
2. **Forced LTR.** Switch `MainActivity` and `InvoiceTheme` to `LayoutDirection.Ltr`. Add one
   `ArabicText` composable (right-aligned, RTL text direction) and use it for screen text, so strings
   that start with digits or Latin ("DH / م²", phone numbers) stay right-aligned. Labels stay on the
   right, amounts on the left. The printed invoice stays RTL (renderer unchanged).
3. **App text field.** Replace the 9 Material `OutlinedTextField`s (customer name/phone, product name,
   category, workshop name/phone/thank-you, history search) with one app field: label above on the
   right, box below, optional illustration beside it, optional trailing slot (for the mic in sprint 7).
4. **Arrows.** Forward arrows point right and sit on the right of the label ("→ متابعة"); back arrow on
   the left.
5. **`AppTopBar`** (replaces `ScreenHeader`): back on the left, title on the right with its speaker
   slot, optional centred icon (item details, item confirmation), title ellipsis before the icon. Move the
   mute chip on confirm/review to a slim sub-bar under the header.
6. **`SpokenButton`**: a speaker tile on the button's left, styled like its button (colour, border,
   radius, height), 4 dp gap, its own target of at least 48 dp. Build it and use it for the
   "+ إضافة منتوج" button now; full placement waits for sprint 7.
7. **"+ إضافة منتوج"**: yellow (`#F2B705`, dark text, `#A67C00` edge), 64 dp, plus in a dark circle,
   visible pressed state.
8. **Tests.** Rewrite `LayoutAndBackUiTest` for LTR (back arrow left, title right); keep the touch-target
   checks; update `ScreenshotTourTest` if it asserts positions. Existing flow tests must pass unchanged.

### Sprint 5: invoice output (screens and paper)

Moved before units and photos on the owner's request (2026-10-25): it needs no database change and works
with today's data. The Latin unit labels and imported photos reach the output in sprint 6.

1. **Table option A** in `InvoiceLayoutEngine`: columns left to right Price | Measurement / Formula |
   Quantity | Name/Icon; unit price inside the formula cell ("2,52 م² × 500 DH"), quantity dropped from
   that cell; "DH" in the column header; quantity column with its own margins, centred, no dividers.
2. **Thumbnails** 34 units on the PNG (26 pt ≈ 9 mm on A4); change `InvoiceLayoutTest`'s 60–80 check to
   the new 32–40 range.
3. **PDF.** Full header on page 1 only; pages 2+ start with one slim line (workshop · invoice number ·
   date) above the column titles; footer "صفحة 2 / 3" on every page; total, deposit and balance only on
   the last page with at least the last row. Deposit in green, slightly smaller than the total; balance
   in a yellow band (dark text), same size as the total.
4. **PNG.** One image. If it would be taller than 8,000 px (about 55 items), the saved screen shows
   "هذه الفاتورة طويلة على صورة واحدة. شاركها كملف PDF" and the PDF buttons instead of the image button,
   even when the format setting is "image". Remove the multi-PNG split.
5. **The same compact table on screen** (in the mock-ups, in no sprint before): one shared composable,
   right to left: product with its picture, quantity in its own column, measurement and calculation (unit
   price in the cell), amount in DH; column titles above; no dividers; a long formula wraps inside its cell.
   * Item list: 36 dp pictures; each row keeps its actions below it, | حذف | تعديل | (delete left, edit
     right; their speaker tiles come in sprint 7).
   * Review: 32 dp pictures.
   * Invoice detail: the same table as the paper.
6. **Paid state on Review**: when the deposit equals the total, the balance line shows "0 DH" in green with
   the "خالص" badge (check icon + word) on the same line, instead of the separate banner. An open balance
   stays bold ink.
7. **Saved screen**: the two bottom buttons (الرئيسية, فاتورة جديدة) stacked, full width.
8. **Tests.** Pagination (rows per page, slim header, footer, totals on the last page only), the PNG limit
   switch; on-screen table column order (amount left, product right) on the item list, review and detail;
   the paid badge only when fully paid; the stacked Saved buttons.

### Sprint 6: units, fields, photos

1. **Settings.** `lengthUnit` (cm / m) and `unitLabelScript` (Arabic سم / م, Latin cm / m) in
   `AppSettings`, Room v4 migration, backup fields with defaults (older backups: metres, Arabic labels).
   Both shown in Settings as in the mock-up, without speakers.
2. **Invoice unit.** New nullable `invoices.lengthUnit`: set on create; old invoices that mix m and cm
   take their first item's unit; existing lines stay as typed. Editing uses the invoice's unit, not the
   current setting. `ItemEntryForm` gets one unit for both dimensions.
3. **Remove the per-item unit switch.** `UnitToggle` goes from item entry and product edit; the box shows
   only the unit, where the switch was. `ProductDefinition.defaultUnit` stays in the database, unused.
4. **Unit labels.** Pass the label script into `ItemDescriber` and `InvoiceDocumentBuilder` (screens,
   printed invoices and re-exports of old invoices). Wrap Latin labels as LTR isolates (see the bidi demo
   in the mock-ups).
5. **Field icons.** ↔ width, ↕ height, DH coin for the unit price, stacked boxes for quantity, person and
   phone for the customer. Labels on the right, directly above their icons.
6. **Photo import (product edit only).** System photo picker, downscale to about 512 px, store in app
   storage; `products.imageFile` (nullable). On item confirmation copy the file for that item
   (`invoice_items.imageFile`, a literal copy per item); delete it with its item or invoice.
   `IllustrationImage` and the renderer show the photo when present, otherwise the built-in picture.
   The choose-product screen has no image change.
7. **Backup with images.** Backup format v2: a ZIP with `backup.json` plus `images/`. Restore accepts
   v1 JSON and v2 ZIP; images are staged and swapped in with the DB transaction; the validator checks
   every referenced file; the safety copy includes images; update file names, MIME types and the Drive
   upload. Show the backup size line in Backup.
8. **Tests.** Migration 3 → 4, invoice unit on create / edit / old mixed invoices, label script in
   descriptions (both scripts, bidi isolation), image copy per item, backup v1 and v2 round trips; both
   label scripts in the printed output and the on-screen table from sprint 5; a photo shown as the
   thumbnail in the table, the PNG and the PDF.

### Sprint 7: placement (speakers and mic)

1. **Speakers**, following DECISIONS.md: `SpokenButton` tiles on every labelled button in the invoice
   flow, history and product catalogue; speaker inside the Home and catalogue cards; one round speaker
   per title, field label, item row and invoice line in previews and history; one speaker for the
   calculation card and one for the total + deposit + balance card. None on icon-only controls, none in
   Settings or Backup except the المنتوجات card and its screens (product list, product edit, including
   the calculation-method options).
2. **Speech calls.** Taps use `Speaker.say(...)`, automatic speech `Speaker.sayIfReady(...)`, and every
   sentence comes from `SpeechTexts.of(settings.speechLanguage)` (never `DarijaSpeech` directly). Add any
   missing sentence (history card, product list row) to both `DarijaSpeech` and `StandardSpeech`.
3. **Mic.** `MicButton` in the trailing slot of the customer-name and product-name fields only.
   Settings → الصوت gets one status line for dictation (ready / offline Arabic pack missing / no recogniser),
   next to the voice status, as in the mock-up (in no sprint before), without a speaker.
4. **Tests.** A speaker tap never selects, presses or navigates (one per screen type); the mic appears
   only on the two text fields; voice off keeps the whole flow usable; the missing-voice dialog appears
   once.
5. **By hand on the phone.** Voice quality in both languages, speech rate, the voice download flow,
   dictation with and without the offline ar-MA pack, the microphone permission flow.

### Open items outside the sprints

* Listen on the phone to the Darija and standard-Arabic readings (see the checklists above).
* Decide whether to keep the test build installed on the Samsung phone (it holds only test data).
* Existing bug: the product "not found" state of `ProductEditScreen` shows "الفاتورة غير موجودة".
* Cleanup: `Strings.AUDIO_TEST_SENTENCE` is unused.
* Optional, not decided: recolour the rust launcher icon.

### Starter prompt for the next session

```text
Day 2, Sprint 5 (invoice output) of the Workshop Invoice app in C:\dev\projects\Kotlin\Invoice.
Read CLAUDE.md, docs/day2/DECISIONS.md, docs/day2/PROGRESS.md (sections "Sprint 4: foundations" and
"Next sprints, step by step") and the approved mock-ups in docs/day2/design-review.html first
(sections "Unit-price column", "Long invoice", item list, review, saved, history).
Create branch day2/sprint-5 from day2/sprint-4 and build exactly the Sprint 5 steps listed in
PROGRESS.md: table option A on the printed invoice, 34-unit thumbnails, PDF page header/footer and
totals on the last page, one PNG or point to PDF, the same compact table on the item list, review and
invoice detail screens, the paid badge on review, stacked Saved buttons, and their tests. Use the Sprint 4
components (ArabicText, AppTopBar, SpokenButton, appPalette). No features beyond that list; units,
photos and speakers come later.
Safety: instrumented tests wipe the app database. Target one device explicitly (adb -s / ANDROID_SERIAL)
and ask me before using my phone. This PC cannot run the emulator and Gradle together (7.7 GB RAM):
build first, stop Gradle, then run the APKs with adb shell am instrument.
Before ending: build, run all tests, update docs/day2/PROGRESS.md, commit on day2/sprint-5 (do not
push), and tell me what was verified on a device and what I must check by hand.
```
