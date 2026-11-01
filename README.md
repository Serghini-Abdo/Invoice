# فواتير الورشة: Workshop Invoice

An offline-first Android invoicing app for a small Moroccan metalworking workshop (doors, windows,
garage gates, frames, accessories). Arabic first, right-to-left, illustration first, built for an owner
who reads little: large buttons, large numbers, one decision per screen, optional spoken Arabic.

* Kotlin, Jetpack Compose + Material 3, Room (SQLite), Android TextToSpeech, Android Sharesheet.
* **Everything for normal invoicing works with no internet and no account.** Backups (automatic or manual, on
  the phone and optionally on Google Drive) are configured in Settings.
* No taxes, no inventory, no accounting: invoices only.

> **Where it is going.** Product decisions, including the planned changes from the 2026-11-01 review
> (receipts instead of invoices, right-to-left layout, blue palette), are in
> [docs/day2/DECISIONS.md](docs/day2/DECISIONS.md); current state and roadmap in
> [docs/day2/PROGRESS.md](docs/day2/PROGRESS.md). This README describes the app as built.

> **Status.** The app is built and tested by the GitHub Actions workflow in this repository
> (`.github/workflows/ci.yml`): domain unit tests, Android build, lint and an emulator run of the
> instrumented tests. See [Verification](#verification-what-has-and-has-not-been-proven) for exactly what is
> proven and what is not (for example, automatic backups firing on schedule have not been observed on a phone).

## Using the app

Home has exactly three buttons: **فاتورة جديدة** (new invoice), **الفواتير السابقة** (previous invoices),
**الإعدادات** (settings).

New invoice:

1. **Customer** (optional): name and phone. Continue immediately if you like.
2. **Items**: the list of items so far, each with a small picture, measurements and total; a big
   **+ إضافة منتوج** button; **متابعة** to go on. Items can be edited or removed (removal asks first).
3. **Choose a product** from the picture grid (two columns). Tapping the picture selects it. With the speaker
   icons switched on in Settings, each card has its own speaker that only speaks the name and never selects.
4. **Details and confirmation**: enter the size, rate and quantity with the phone's numeric keyboard.
   Dismiss it with Done, Back or the hide-keyboard button. Use **إضافة قياس آخر** (products with a width or height) to add another size of
   the same product. Its rate carries over in yellow with a text cue until changed, confirmed or accepted
   by adding another line. Review the calculations and total below the fields, then confirm all lines together.
5. **Review**: all items, the total, an editable deposit (default 0, cannot exceed the total), the remaining
   balance and an explicit "خالص" (fully paid) state. A final confirmation precedes saving.
6. **Saved**: the invoice is stored first; then the PNG/PDF is generated on the phone, previewed, and
   shared through Android's share sheet (WhatsApp appears there when installed).

The product picker also offers **إضافة مبلغ خاص** for labour or another fixed charge: description and amount,
without a catalogue product, quantity or measurements. Product settings offer per-piece, area, perimeter,
width-only, height-only and fixed pricing. Per-piece products may show width, height or both for information;
required measurements for calculated prices cannot be hidden.

The same product can be added any number of times; every addition is an independent item with its own size,
rate and quantity. Nothing is merged.

Settings contains only: the product catalog (with photo import in product edit), workshop name/phone (and
an optional thank-you line), the invoice format (PNG / PDF / both and choose when sharing), units (cm or m,
Arabic or Latin labels), audio (on/off, speech rate, spoken language, speaker icons), and backup/restore.

### Backups

Settings > Backup has: automatic backup **off / every day / every week**; where to save (**this phone**,
**Google Drive**, or both); **انسخ الآن** (back up now); the time of the last successful backup per destination
and the last error with what to do about it; and restore.

* **On the phone**: `Documents/Fawatir Warsha/backup-YYYY-MM-DD-HHmm.json`, created automatically (Android 10+
  needs no permission; Android 7 to 9 ask once, and if refused the copy is kept inside the app and the screen
  says so). The newest 10 are kept. Files the app did not name are never touched.
* **On Google Drive** (hidden app folder): the newest 5 are kept. Needs the one-time Google Cloud setup in
  [docs/google-drive-setup.md](docs/google-drive-setup.md); after the first consent it runs silently.
* Automatic backups use WorkManager (survives reboot): one job for the phone with no network requirement, one
  for Drive that waits for a connection. If a backup is missed because the phone was off, the next app start
  takes it. The home screen shows a banner when Drive needs the owner to reconnect, or when no backup has
  succeeded for more than twice the chosen interval.
* A new copy is written and verified first and only then are old ones removed, so the last good copy is never
  deleted. Every backup is parsed back with the full validator before it is stored.
* Restore: **استرجاع من الهاتف** opens the file picker in the backup folder; **استرجاع من Google Drive** takes the
  newest copy. Both go through the same staged, validated, atomic restore.

## Build and run

Requirements: **JDK 17** and the Android SDK (platform 35). Android Studio (Ladybug or newer) handles both.

```bash
./gradlew :app:assembleDebug            # debug APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug             # install on a connected phone/emulator
```

Release build (sign it with your own key; nothing secret is in the repository):

```bash
export INVOICE_KEYSTORE_PATH=/path/to/release.jks
export INVOICE_KEYSTORE_PASSWORD=...  INVOICE_KEY_ALIAS=...  INVOICE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

Google Drive backup needs a one-time Google Cloud OAuth setup: see [docs/google-drive-setup.md](docs/google-drive-setup.md).
Without it, switching Drive on shows the exact reason in Arabic and backups to the phone keep working.
If the owner closes Google's screen before tapping Allow, the app says so (a separate message, no banner on
the home screen); any other Google failure is shown with its real code, for example `api 10`.

**Debug signing key.** The debug build is signed with the committed `app/debug.keystore` (standard public debug
credentials), so every build, local or CI, has the same certificate. That is what lets the Google OAuth client
match, and lets a newer APK install over an older one without losing data. Builds made before this change were
signed with a different, throw-away key: moving to this build needs one uninstall and a restore from a file, see
[docs/google-drive-setup.md](docs/google-drive-setup.md#important-the-first-install-of-the-pinned-key-cannot-update-the-old-app).

## Tests

```bash
./gradlew -p domain test                  # pure-Kotlin unit tests, needs only a JDK (no Android SDK)
./gradlew :app:testDebugUnitTest          # (no app-level JVM tests; the logic lives in :domain)
./gradlew :app:connectedDebugAndroidTest -PdeviceTests  # OPTIONAL, emulator/phone: Room, migrations, PDF/PNG export, full UI workflow
```

## Architecture

```
domain/   pure Kotlin/JVM (no Android): everything that must be correct
  money/ pricing/ input/        exact money + measurements, pricing engine, input parsing and validation
  flow/                         InvoiceFlow: the workflow as a pure state machine; draft auto-save codec
  invoice/                      models, payment math, versioning/audit, history search
  format/ layout/               Arabic formatting, invoice document model, page layout + pagination
  backup/                       backup format, strict validator, snapshot codec
  catalog/                      built-in illustrations (SVG path data) and the starter catalog
app/      Android: Compose UI, Room, TextToSpeech, PDF/PNG renderer, FileProvider, Google Drive client, WorkManager
  data/        Room entities, DAOs, repositories (transactions)
  platform/    speech, export (renderer/exporter/share), Drive client, backup (service, scheduler/worker, folder store), restore safety
  ui/          theme, components, screens, ViewModels, navigation
```

Design decisions worth knowing:

* **Money is integer centimes, lengths are integer thousandths, no floating point.** One documented rounding
  rule: [docs/calculation-rules.md](docs/calculation-rules.md).
* **The workflow is a state machine** (`InvoiceFlow`). Screens only render its current step and send events,
  so rules like "confirming an item returns to the item list" are tested without Android.
* **Invoice items are immutable snapshots.** Editing/deactivating/deleting a catalog product never changes
  an invoice. Product deletion only clears an optional link.
* **Saving is separate from exporting and sharing.** If export fails or WhatsApp is missing, the invoice is
  already safe; the screen says so and offers a retry.
* **Edits are traceable:** an invoice keeps its id and number, its revision counter increases, and every
  edit stores what changed plus the complete previous version.
* **Nothing is silently lost:** an unfinished new invoice is auto-saved as a draft and offered on the next
  start; leaving a screen with unsaved work asks first.
* **PDF and PNG come from one layout engine** ([layout](domain/src/main/kotlin/ma/workshop/invoice/domain/layout)),
  which is unit-tested for pagination (rows never split, summary never alone, table header repeats) and for
  keeping thumbnails small and consistent. Text is shaped by Android's text engine with the bundled Cairo font.
* **Voice is only an aid.** It looks for an installed Arabic voice (preferring Moroccan Arabic), shows clear
  status in Settings, and everything remains visible on screen. Spoken amounts are generated as Arabic number
  words, so they do not depend on how an engine reads digits.
* **Backup/restore is staged:** read, fully validate (every item is recomputed from its measurements), confirm,
  write a safety copy of the current data, then one atomic database transaction.

Data model and migration strategy: [docs/data-and-migrations.md](docs/data-and-migrations.md).

## Verification: what has and has not been proven

Proven by the CI workflow (`.github/workflows/ci.yml`), on every push:

* The domain unit tests pass on a plain JDK (210 at the last run).
* The Android app builds (debug and unsigned release APK), passes lint, and the committed Room schema equals
  the one generated from the entities.
* The instrumented tests (73 at the last run) run on real Android system images: **Android 14 (API 34)** and **Android 7.0 (API 24,
  the oldest supported; the 2 screenshot tests need API 26 and are skipped there)**. They cover the Room
  database (reopening, repeated items, edits with audit revisions, catalog changes not touching invoices,
  backup round trip, a failed restore leaving data intact), PNG and PDF export (files decoded and measured),
  and the complete invoice workflow through the real screens with audio disabled.

Not proven, stated plainly:

* Google Drive is verified by hand only: the owner ran one backup and one restore against his Google account
  (2026-11-01). Automated tests use a scripted fake Drive.
* A background backup actually firing on schedule on a real phone (the worker is tested by running it
  directly; WorkManager's own timing, Doze and reboot behaviour were not observed).
* Saving to `Documents/Fawatir Warsha` is verified on the Android 14 (API 34) emulator and on the Android 7.0
  (API 24) emulator when `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` are both granted and the app process is
  restarted after the grant. With only `WRITE_EXTERNAL_STORAGE` granted through `pm grant`, the Android 7 emulator
  refused the write (the app falls back to its private folder and tells the owner). Not observed on a physical
  Android 7 to 9 phone with the real permission dialog, and the file picker opening in that folder was not observed.
  Details: `docs/testing.md`.
* Spoken Arabic quality (the CI emulators have no Arabic voice).
* Any physical phone other than the owner's Samsung (Android 16, where the instrumented suite ran with test
  data only), any screen smaller than the Pixel 5 profile, large system font sizes, device restart, airplane mode, and a
  failing share target.

The mapping of the 20 acceptance scenarios to tests, with the exact gaps, is in
[docs/testing.md](docs/testing.md), together with the defects the emulator found that the JVM tests could not.

To get the APK without installing anything, open the latest green run of the **CI** workflow in the
repository's Actions tab and download the `app-debug-apk` artifact. Screenshots of the real screens and of
the exported invoice are published to the `ci-screenshots` branch by every run.

## Known limitations

These are real, current limits, not future plans:

* **Google Drive needs the one-time OAuth setup** ([docs/google-drive-setup.md](docs/google-drive-setup.md)),
  Google Play Services and internet. With that done, one backup and one restore worked on the owner's phone
  (2026-11-01). The parts that make a restore *safe* (strict validation, staged confirmation, safety copy,
  atomic transaction) are tested automatically through the file backup, which uses the same restore path.
* **Arabic speech depends on the phone.** The app uses whatever Arabic text-to-speech voice is installed
  (Moroccan Arabic if present). Darija pronunciation is *not* promised or verified; spoken amounts use
  standard Arabic number words. CI emulators have no Arabic voice, so speech output itself is only verified
  to degrade correctly (clear "unavailable" state, everything still on screen), not to sound right.
* **Tested on emulators and one phone**: Android 7.0 (API 24) and Android 14 (API 34) emulators, Pixel 5
  profile (1080 x 2340), and the owner's Samsung (Android 16); not on smaller screens or large font sizes. Touch-target sizes and right-to-left placement are
  asserted in density-independent pixels by automated UI tests rather than checked by hand on hardware. On a
  small phone the item-entry form scrolls and keeps the field being typed into in view.
* **Product pictures** are the 16 built-in illustrations or a photo imported in product edit. **Backups do not
  include the photo files yet**: a restore on another phone shows the generic picture.
* **Phones are locked to portrait.** Tablets may rotate; on a tablet in landscape the item-entry screen is
  usable but compact (it scrolls).
* **The app is Arabic-only** by design; there is no language switch.
* **Android Auto Backup is switched off** (`allowBackup=false`) so invoice data only leaves the phone
  through the explicit backup actions.
* **Release builds are not code-shrunk** (`isMinifyEnabled=false`) and are unsigned unless you provide a
  keystore through the environment variables above.
