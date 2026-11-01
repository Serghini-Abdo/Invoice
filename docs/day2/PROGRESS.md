# Progress

What is built, what is verified, what is open, and what comes next. Decisions live in
[DECISIONS.md](DECISIONS.md). The detailed logs of sprints 3 to 5 and the 2026-10-27 entry update are in
git history: `git show 7926659:docs/day2/PROGRESS.md`.

## Current state (2026-11-01)

* Branch `day3/sprint-1`. **Not pushed.** Chapter 1 of the Day 3 plan (steps 1.0 to 1.11) is built; the
  chapter 0 owner tasks below are still open.
* Room schema **version 6** (no change in chapter 1). Every version has a migration, a `MigrationTest` case
  and its committed JSON.
* Tests at the last run (2026-11-01): **214 domain tests pass**; `:app:assembleDebug`,
  `:app:compileDebugAndroidTestKotlin` and `:app:lintDebug` pass after step 1.11. The instrumented suite last ran
  after step 1.7, **before steps 1.8 to 1.11 (not run on a device since)**, on the owner's Samsung SM-S931B (Android 16, test data only): **84 tests, 0 failed,
  1 skipped** (the old-Android storage test). That includes the new `NoAutomaticSpeechTest`, which ran with a
  real Arabic voice (it skips on a phone without one).

## What is built

* **Invoice flow:** optional customer, item list, picture catalogue, multi-size entry on one page with
  rate carry-over, custom charges, review with deposit, balance and paid badge, save, then share.
  Drafts auto-save; leaving with unsaved work asks first.
* **Pricing:** per piece, area, perimeter, width, height, fixed. Integer money, exact cm / m conversion.
* **History:** search by name, phone or number, date filter, invoice detail, edit with a revision log, delete.
* **Printed invoice:** PNG and PDF from one layout engine (table option A, pagination, footer, totals on the
  last page, PDF fallback for long invoices).
* **Settings:** product catalogue (with photo import), workshop name / phone / thank-you line, output
  format, units and unit labels, audio (on / off, rate, spoken language, speaker icons switch, voice test),
  backup.
* **Backup:** automatic (off / daily / weekly) to the phone and / or Google Drive (setup done; one backup and
  one restore verified by the owner on 2026-11-01), back up now, staged and
  validated restore. When Google's consent screen fails, the real reason is shown: a closed screen says so
  (no home banner); anything else shows Google's code, for example `api 10`.
* **Speech:** offline TextToSpeech in Darija or standard Arabic, speakers behind a switch (off by default),
  dictation on customer name and product name. **Nothing is spoken automatically**; speech happens only on a
  speaker tap or "تجربة الصوت".
* **Layout:** forced right-to-left on every phone (back arrow on the right, labels on the right, amounts on
  the left). Buttons and cards have no arrows; the only arrow is the back arrow in the top bar.
* **Palette and name:** blue for actions and the launcher icon; green only for paid (badge, paid balance,
  "خالص" banner) and on the printed invoice, which keeps its colours. The document is called فاتورة on screen, on paper
  and in speech (shared files `invoice-0007-...`).
* **Settings:** six category cards with their own screens (see above).

## Still to check by hand on the phone

* **Sprint 1, steps 1.0 to 1.11 (phone set to French):** Settings list and its child screens, every screen of
  the invoice flow, history, catalogue and backup read from the right, buttons and cards without arrows, the
  blue palette (green only on paid states), the blue launcher icon, the wording (after step 1.8: فاتورة جديدة, الفواتير السابقة, "الفاتورة خالصة" in Darija), and silence until a speaker is tapped (list in the hand-off report).

* Voice quality in Darija and standard Arabic, speech rates, the voice download flow, a speaker stopping the
  previous one.
* Dictation with and without the offline ar-MA pack, and the microphone permission flow.
* Google sign-in messages: on a phone without the Google Cloud setup, choosing an account shows "setup
  incomplete" with `api 10`; pressing Back on Google's screen shows "you closed the Google screen" and no banner
  appears on the home screen.
* The new entry flow end to end: several sizes, the copied rate in yellow, custom charge, editing a line.
* PNG and PDF of a real invoice: divider weight, Latin unit labels ("120 × 85 cm"), bidi order of
  "2,52 م² × 500 DH" and "صفحة 2 / 3", balance colour.
* Photo import with a real photo (picker, a portrait photo's rotation).
* Product list and product edit, voice dialogs, and screens with several items or a long customer name,
  with the speaker icons on (the layout sweep does not cover them).

## Not proven at all

* Automatic backups firing on schedule on a real phone (Doze, reboot).
* A physical Android 7 to 9 phone, device restart, airplane mode, a failing share target.

## Known issues

* Backups do not include photo files: a restore on another phone shows the generic picture.
* A invoice's unit is derived from its first measured item, not stored; a per-piece-only invoice follows the
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

1. ~~Settings as categories with parent / child options~~ (plan step 1.0): built; compiles, lint clean, domain tests pass; the device suite passes on a Samsung SM-S931B (Android 16, 84 tests, 0 failed). Hand check still to do.
2. ~~Right-to-left app layout~~ (plan step 1.1): built; same device run as above, the layout tests pass. Hand check still to do.
   * `LayoutAndBackUiTest.leavingWithUnsavedWorkAsksFirst` used to fail on the phone (and on a5bffd2, so not caused by Sprint 1). The app was right: on the entry screens a Back press while the keyboard is open only closes the keyboard, and a real phone keeps its keyboard up after a width is typed. The test's system Back now closes the keyboard first when it is open. Passes on the phone.
3. ~~Blue palette tokens and a blue launcher icon; green only for paid~~ (steps 1.2, 1.3): built; compiles and
   lint passes; the invoice colours are pinned by a domain test; the device suite passes. Hand check still to do.
4. ~~Back to فاتورة~~ (step 1.8): built (the owner reversed the وصل rename of step 1.4).
5. ~~Fix the known issues: the product "not found" message, the unused string~~ (step 1.5): built.
6. ~~No arrows on buttons and cards~~ (step 1.6): built; the device suite passes. Hand check still to do.
7. ~~No automatic speech; remove the two automatic-totals switches~~ (step 1.7): built; the new
   `NoAutomaticSpeechTest` passes on the phone.
8. ~~Compact item list~~ (step 1.9): built, compiles; its new UI test has not run on a device. Hand check still to do.
9. ~~Dialogs: app buttons, right-to-left, meaningful icons, speakers~~ (step 1.10): built, compiles; its new UI
   test has not run on a device. Hand check still to do.
10. ~~Yellow only for attention; tonal blue add button~~ (step 1.11): built, compiles. Hand check still to do.

### Sprint 2: fewer taps

1. Remove the save confirmation dialog.
2. Recent customers as one-tap choices; saved-customer suggestions matched by phone (derive from saved
   invoices if that is enough, before adding a table).
3. Optional "send to the customer's WhatsApp" next to the share sheet.
4. Version mark "نسخة N" on re-shared invoices from version 2 on.

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
  before using the phone, never run them on a phone with real invoices.
* This PC cannot run the emulator and Gradle together (7.7 GB RAM): build first, `./gradlew --stop`, then
  install the APKs and run `adb shell am instrument`.
* Every Room version bump ships a migration, a `MigrationTest` case and the committed schema JSON. The next
  version is 7.
