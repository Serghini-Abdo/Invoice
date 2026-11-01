# Progress

What is built, what is verified, what is open, and what comes next. Decisions live in
[DECISIONS.md](DECISIONS.md). The detailed logs of sprints 3 to 5 and the 2026-10-27 entry update are in
git history: `git show 7926659:docs/day2/PROGRESS.md`.

## Current state (2026-11-01)

* Branch `day3/sprint-0` (from `main` at `4a7dd70`). **Not pushed.** Chapter 0 of the Day 3 plan is in place
  except the owner tasks below.
* Room schema **version 6**. Every version has a migration, a `MigrationTest` case and its committed JSON.
* Tests at the last run (2026-11-01): **211 domain tests pass**; `:app:assembleDebug`,
  `:app:assembleDebugAndroidTest` and `:app:lintDebug` pass, so the multi-size entry patch compiles.
  The instrumented suite last ran before the 2026-10-27 entry update: **OK (73 tests)** on the owner's
  Samsung SM-S931B, Android 16 (test data only). **Device suite not run** for the entry update or for the
  Google consent change below.

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
* **Backup:** automatic (off / daily / weekly) to the phone and / or Google Drive (setup done; one backup and
  one restore verified by the owner on 2026-11-01), back up now, staged and
  validated restore. When Google's consent screen fails, the real reason is shown: a closed screen says so
  (no home banner); anything else shows Google's code, for example `api 10`.
* **Speech:** offline TextToSpeech in Darija or standard Arabic, speakers behind a switch (off by default),
  dictation on customer name and product name.
* **Layout:** forced right-to-left on every phone (back arrow on the right, forward arrows point left, labels
  on the right, amounts on the left); green palette (to be replaced, see the roadmap).
* **Settings:** six category cards with their own screens (see above).

## Still to check by hand on the phone

* **Sprint 1, steps 1.0 and 1.1 (phone set to French):** Settings list and its five child screens, then every
  screen of the receipt flow, history, catalogue and backup read from the right (list in the hand-off report).
  Nothing from these two steps has been compiled or run yet.

* Voice quality in Darija and standard Arabic, speech rates, the voice download flow, a speaker stopping the
  previous one.
* Dictation with and without the offline ar-MA pack, and the microphone permission flow.
* Google sign-in messages: on a phone without the Google Cloud setup, choosing an account shows "setup
  incomplete" with `api 10`; pressing Back on Google's screen shows "you closed the Google screen" and no banner
  appears on the home screen.
* The new entry flow end to end: several sizes, the yellow copied rate, custom charge, editing a line.
* PNG and PDF of a real receipt: divider weight, Latin unit labels ("120 × 85 cm"), bidi order of
  "2,52 م² × 500 DH" and "صفحة 2 / 3", balance colour.
* Photo import with a real photo (picker, a portrait photo's rotation).
* Product list and product edit, voice dialogs, and screens with several items or a long customer name,
  with the speaker icons on (the layout sweep does not cover them).

## Not proven at all

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
**Step-by-step instructions for every sprint are in [../day3/PLAN.md](../day3/PLAN.md)** (files, steps,
tests, stop conditions, hand-off format). The list below is the summary.

### Sprint 0: safety (before anything else)

Done: compile, domain tests and lint for the entry update; the real reason for a failed Google consent.
Still open (owner tasks):

1. The instrumented suite on one targeted device (entry update and consent change).
2. One real backup and one restore to the **phone folder** (Google Drive: done 2026-11-01).
3. Push `main`.

### Sprint 1: layout, palette, name

1. ~~Settings as categories with parent / child options~~ (plan step 1.0): built; compiles, lint clean, domain tests (211) pass; device suite run on a Samsung SM-S931B (Android 16): 82 of 83 pass, 1 skipped (old-Android test), 1 fails (see below). Hand check still to do.
2. ~~Right-to-left app layout~~ (plan step 1.1): built; same device run as above, the layout tests pass. Hand check still to do.
   * Known failure, **not caused by Sprint 1** (fails the same way on a5bffd2): `LayoutAndBackUiTest.leavingWithUnsavedWorkAsksFirst`. After typing a width on the item entry screen, a system Back leaves the screen on item entry instead of returning to the catalogue. Cause not yet found (a Back-handler on the entry screen, or the soft keyboard on a real phone, are the first suspects).
3. Blue palette tokens and a blue launcher icon; green only for paid.
4. Rename invoice to receipt (وصل) in screens, printed output and speech (no migration).
5. Fix the known issues: the product "not found" message, the unused string.
6. No arrows on buttons and cards (plan step 1.6).
7. No automatic speech; remove the two automatic-totals switches (plan step 1.7).

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
