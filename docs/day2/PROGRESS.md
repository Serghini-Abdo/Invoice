# Progress

What is built, what is verified, what is open, and what comes next. Decisions live in
[DECISIONS.md](DECISIONS.md). The detailed logs of sprints 3 to 5 and the 2026-10-27 entry update are in
git history: `git show 7926659:docs/day2/PROGRESS.md`.

## Current state (2026-11-01)

* Branch `main`, commit `7926659` (multi-size entry, new pricing methods, fixed charges, Room v6).
  **Not pushed.** The last patch ("another size" only for products with a width or height) has not been
  compiled.
* Room schema **version 6**. Every version has a migration, a `MigrationTest` case and its committed JSON.
* Tests at the last run: **210 domain tests pass**; build and lint pass. The instrumented suite last ran
  before the 2026-10-27 entry update: **OK (73 tests)** on the owner's Samsung SM-S931B, Android 16
  (test data only). The entry update has **not** run on a device yet.

## What is built

* **Receipt flow:** optional customer, item list, picture catalogue, multi-size entry on one page with
  rate carry-over, custom charges, review with deposit, balance and paid badge, save, then share.
  Drafts auto-save; leaving with unsaved work asks first.
* **Pricing:** per piece, area, perimeter, width, height, fixed. Integer money, exact cm / m conversion.
* **History:** search by name, phone or number, date filter, receipt detail, edit with a revision log, delete.
* **Printed receipt:** PNG and PDF from one layout engine (table option A, pagination, footer, totals on the
  last page, PDF fallback for long receipts).
* **Settings:** product catalogue (with photo import), workshop name / phone / thank-you line, output
  format, units and unit labels, audio (on / off, rate, spoken language, speaker icons switch), backup.
* **Backup:** automatic (off / daily / weekly) to the phone and / or Google Drive, back up now, staged and
  validated restore.
* **Speech:** offline TextToSpeech in Darija or standard Arabic, speakers behind a switch (off by default),
  dictation on customer name and product name.
* **Layout:** forced left-to-right with right-aligned Arabic (to be replaced, see the roadmap), green palette.

## Still to check by hand on the phone

* Voice quality in Darija and standard Arabic, speech rates, the voice download flow, a speaker stopping the
  previous one.
* Dictation with and without the offline ar-MA pack, and the microphone permission flow.
* The new entry flow end to end: several sizes, the yellow copied rate, custom charge, editing a line.
* PNG and PDF of a real receipt: divider weight, Latin unit labels ("120 × 85 cm"), bidi order of
  "2,52 م² × 500 DH" and "صفحة 2 / 3", balance colour.
* Photo import with a real photo (picker, a portrait photo's rotation).
* Product list and product edit, voice dialogs, and screens with several items or a long customer name,
  with the speaker icons on (the layout sweep does not cover them).

## Not proven at all

* Google Drive backup and restore against a real Google account (needs the OAuth setup in
  [google-drive-setup.md](../google-drive-setup.md)).
* Automatic backups firing on schedule on a real phone (Doze, reboot).
* A physical Android 7 to 9 phone, device restart, airplane mode, a failing share target.

## Known issues

* Product edit's "not found" state shows "الفاتورة غير موجودة" (the receipt message).
* `Strings.AUDIO_TEST_SENTENCE` is unused.
* Backups do not include photo files: a restore on another phone shows the generic picture.
* A receipt's unit is derived from its first measured item, not stored; a per-piece-only receipt follows the
  current Settings unit.
* Standard-Arabic speech does not put numbers before nouns in construct state ("مائتان درهم").

## Roadmap (Day 3, from the 2026-11-01 review)

Each sprint: branch `day3/sprint-N` from `main`, build only what is listed, then the definition of done below.

### Sprint 0: safety (before anything else)

1. Compile, run the domain tests, lint, and the instrumented suite on one targeted device for the entry update.
2. One real backup and one restore on the owner's phone (phone folder, and Google Drive after its setup),
   before the owner keeps any real receipts.
3. Push `main`.

### Sprint 1: layout, palette, name

1. Right-to-left app layout (replace forced LTR); update the layout tests.
2. Blue palette tokens and a blue launcher icon; green only for paid.
3. Rename invoice to receipt (وصل) in screens, printed output and speech (no migration).
4. Fix the known issues: the product "not found" message, the unused string.

### Sprint 2: fewer taps

1. Remove the save confirmation dialog.
2. Recent customers as one-tap choices; saved-customer suggestions matched by phone (derive from saved
   receipts if that is enough, before adding a table).
3. Optional "send to the customer's WhatsApp" next to the share sheet.
4. Version mark "نسخة N" on re-shared receipts from version 2 on.

### Sprint 3: speakers and large fonts

1. `SpeakableText` / `SpeakableButton` and the speaker column, with the two exceptions (title, grid cards).
2. Extend the layout sweep: every visible Arabic text has a speaker or is exempt; run it at font scale 1.0,
   1.3 and 1.5.
3. Fix every screen that breaks at large font scales.
4. Local speaker-tap counter on a hidden Settings screen.

### Later

Cancel instead of delete, dated payments, unpaid balances on Home and in history, quotes, discount, photos
in backups.

## Definition of done and device safety

* `./gradlew -p domain test`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug`, and
  the instrumented suite (`-PdeviceTests`) on **one** explicitly targeted device; this file updated; one commit.
* Instrumented tests **wipe the app database**: target one device (`adb -s` / `ANDROID_SERIAL`), ask the owner
  before using the phone, never run them on a phone with real receipts.
* This PC cannot run the emulator and Gradle together (7.7 GB RAM): build first, `./gradlew --stop`, then
  install the APKs and run `adb shell am instrument`.
* Every Room version bump ships a migration, a `MigrationTest` case and the committed schema JSON. The next
  version is 7.
