# Testing: what is covered, and what is not

Last counts: **210 domain tests** pass; the instrumented suite was **OK (73 tests)** on the owner's Samsung
(Android 16, test data only) before the 2026-10-27 entry update, which has not run on a device yet. The
current state of every check is in [day2/PROGRESS.md](day2/PROGRESS.md).

The item entry confirms several size lines on one page and uses the system numeric keyboard.
`VariationEntryTest` covers batch entry, copied rates, draft recovery, the new pricing methods and backup
validation.


Every statement below was produced by the CI workflow in `.github/workflows/ci.yml`, not by assumption.
The workflow has four jobs. A fresh checkout reproduces all of them with the commands at the end.

| Job | What runs | Needs |
| --- | --- | --- |
| Domain unit tests | the pure Kotlin tests (`./gradlew -p domain test`) | a JDK only |
| Android build, unit tests, lint | `:app:testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`, and a check that the Room schema JSON in git equals the one generated from the entities | Android SDK |
| Emulator tests, Android 7.0 (API 24) | the instrumented tests below, on the oldest Android the app supports | emulator |
| Emulator tests, Android 14 (API 34) | the same tests plus the screenshot tour | emulator |

The emulator job is what found problems the JVM tests could not see (see "Defects found by the emulator"
below), so it runs on the oldest and a current Android version.

## The 20 acceptance scenarios

"Domain" means a JVM unit test in `domain/src/test`. "Emulator" means an instrumented test in
`app/src/androidTest`, run on a real Android system image.

| # | Scenario | Where it is tested |
| --- | --- | --- |
| 1 | Door 1.20 m x 2.10 m, 500 DH/m2, quantity 1 gives 1,260 DH | Domain `PricingEngineTest`; emulator `InvoiceFlowUiTest` (typed with the numeric keyboard, the entry total shows 1 260 DH) |
| 2 | Quantity 2 gives 2,520 DH | Domain `PricingEngineTest` |
| 3 | Perimeter product: perimeter x rate x quantity | Domain `PricingEngineTest`; emulator `ScreenshotTourTest` (frame 150 x 200 cm, 80 DH/m, quantity 3 shows 1 680 DH) |
| 4 | Per-item product: quantity x unit price | Domain `PricingEngineTest`, `FormatAndDocumentTest` |
| 5 | Centimetre input is converted correctly | Domain `PricingEngineTest`, `ItemEntryFormTest` |
| 6 | Invalid or negative measurements, rates, quantities, deposits are rejected | Domain `PricingEngineTest`, `ItemEntryFormTest`, `PaymentTest` |
| 7 | Deposit cannot exceed the invoice total | Domain `PaymentTest`, `InvoiceFlowTest`; emulator `DatabaseTest` (an inconsistent invoice is rejected before anything is written) |
| 8 | Remaining balance is always correct | Domain `PaymentTest` |
| 9 | The same product twice gives two independent items | Domain `InvoiceFlowTest`; emulator `DatabaseTest` (persisted), `InvoiceFlowUiTest` (through the real screens) |
| 10 | Confirming an item returns to the item list, not the catalog or payment | Domain `InvoiceFlowTest`; emulator `InvoiceFlowUiTest` |
| 11 | "+ Add product" allows further products | Domain `InvoiceFlowTest`; emulator `InvoiceFlowUiTest` |
| 12 | Quantity starts at 1 for every new item | Domain `ItemEntryFormTest`, `InvoiceFlowTest`; emulator `InvoiceFlowUiTest` |
| 13 | Editing one repeated item does not change the other | Domain `InvoiceFlowTest`; emulator `DatabaseTest` |
| 14 | Editing a catalog definition does not change historical invoices | Domain `VersioningAndDraftTest`; emulator `DatabaseTest` (edit, deactivate and delete a catalog product, saved invoices unchanged) |
| 15 | Invoices remain after closing and reopening the app and after restart | Emulator `DatabaseTest` (database closed and reopened). **Device restart itself was not tested**: Room writes to disk in a committed transaction, which is why a reopen is the available proxy |
| 16 | PDF and PNG contain correct Arabic text, measurements, totals, deposits, balances | Domain `FormatAndDocumentTest` (document content), `InvoiceLayoutTest`; emulator `ExportInstrumentedTest` (real PNG and PDF files are decoded; size and the payment summary are checked; the shared files carry no pictures). **The Arabic text pixels were checked by eye in the published screenshots, not by OCR** |
| 17 | Long invoices export without clipping or unreadable text | Domain `InvoiceLayoutTest` (pagination rules); emulator `ExportInstrumentedTest` (45 items: one PNG and a paged PDF; 90 items: no PNG, the PDF is made instead) |
| 18 | Failed sharing does not lose a saved invoice | Domain `InvoiceFlowTest` (saving completes before and independently of export and sharing); by construction the share step runs after the save transaction. A failing share intent is not simulated on the emulator |
| 19 | Invalid or interrupted backup restore does not corrupt existing data | Domain `BackupTest` (truncated, wrong version, tampered totals); emulator `DatabaseTest` (a restore that fails leaves existing data untouched) |
| 20 | The main workflow works with audio disabled and with no internet | Emulator `InvoiceFlowUiTest` runs the full workflow with audio disabled. **No internet use exists in that path** (Drive is only reached from the backup screen), but the emulator was not put in airplane mode, so "works offline" is established by design and by the absence of network calls, not by a test that cuts the network |

## Other emulator checks

* `LayoutAndBackUiTest`: placement in the current left-to-right layout (back arrow on the left, titles, labels
  and typed text on the right, first catalogue product top-right), touch targets of at least 48 dp, a speaker tap
  or a finger sliding off a product never selecting it, the system Back button walking back through the steps,
  and leaving with unsaved work asking first.
* `SpeakerToggleUiTest`, `SpeakerTapTest`, `SpeakerLayoutSweepTest`: no speakers with the switch off; with it on,
  speakers only speak, and no screen breaks a word in the middle.
* `InvoiceTableUiTest`, `UnitsUiTest`, `PhotoStoreTest`: table column order, paid badge, unit settings and labels,
  photo downscale, private storage, per-item copy and sweep.
* `BackupWorkerTest`: the real `BackupWorker` run through work-testing with in-memory fakes for the phone folder
  and for Google Drive: a local backup is written, validated and recorded; retention keeps the newest 10 and never
  touches foreign files; a write failure retries and is then recorded; a disabled schedule does nothing; Drive
  creates the new copy before deleting any old one and keeps 5; a failed upload deletes nothing; gone consent is
  recorded with no UI and becomes a home alert; a developer error is not retried and keeps its raw code.
* `AppFolderStoreTest`: the real storage path. MediaStore into `Documents/Fawatir Warsha` on API 34 (list, read back,
  delete, two backups in one minute do not overwrite each other: **passes**), and on API 24 the app-private fallback
  when the permission is missing (**passes**). The API 24 public-folder write with the permission granted is
  checked by `scripts/legacy-storage-test.sh`, which runs the tests twice in a fresh process:
  * Run 1, after `pm grant` of `WRITE_EXTERNAL_STORAGE` only: **fails on the CI emulator**. The app could not write
    inside `/sdcard/Documents` although the permission was listed as granted. The diagnostics show the test process
    ran with Android 7's "default" storage view (`/mnt/runtime/default`, no shared-storage access), and `dumpsys`
    listed `READ_EXTERNAL_STORAGE` as declared but not granted.
  * Run 2, after `am force-stop` and `pm grant` of **both** `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE`:
    **passes** (`OK (3 tests)`): the backup is written to `Documents/Fawatir Warsha`, listed and deleted, and two
    backups in one minute do not overwrite each other.

  So the public-folder write is **verified on the API 24 emulator when READ and WRITE are granted after a
  force-stop**. The two runs differ in two ways at once (the force-stop and the READ grant), so which of them
  matters is not established; the likely cause is the missing READ grant, but that is a guess. The script reports
  VERIFIED if either run passes and a CI warning (not a failure) if neither does. The app asks for
  `WRITE_EXTERNAL_STORAGE` and the system dialog normally grants the whole storage group, so a real phone should
  behave like run 2, but that was **not observed on a physical Android 7 to 9 phone**. If the write is refused,
  the app falls back to its private folder and says so on screen.
* Domain `AutoBackupTest`: file names (ASCII, sortable, no collisions), retention rules (never the new copy, always
  at least one, foreign files untouched), Play Services and HTTP error mapping, the "more than twice the interval"
  warning, catch-up decisions.
* `MigrationTest`: every committed Room schema from version 1 to 6 migrates with data in it and matches the
  entities. (The backup schedule and history are kept in SharedPreferences, outside Room.)
* `DatabaseTest`: invoice numbers are sequential and never reused, deleting an invoice removes its items and
  revisions, the starter catalog is seeded once, settings persist, drafts round trip, backup round trips
  including the audit trail.

## Defects found by the emulator (not visible to the JVM tests)

These were real bugs in code that all the JVM tests of the time passed:

1. **Every price calculation crashed on Android.** `BigInteger.longValueExact` does not exist on Android
   before API 31, and the money rounding used it. 19 of the first 31 emulator tests failed with it.
2. **`java.time` is not available on Android 7** (API 24, 25) without library desugaring, which lint caught.
3. **A rate of 401 with quantity 1 was drawn as "1 401"** on the invoice, because of how the Latin letters
   "DH" interact with the right-to-left text direction. An invisible direction mark after the rate fixed it.
4. **A four-item invoice spilled onto a second PDF page** because the Cairo font has very tall line boxes.
   Line height is now fixed at 1.4 em and the quantity shares the measurements line.
5. **The item total was below the fold** and the share buttons after saving were below a
   large preview. Both screens now put the important thing first.

## What is deliberately not verified

* **Google Drive** against a real Google account (needs your own OAuth client, see `google-drive-setup.md`).
  What is tested is everything around it (error classification, retention, the worker with a scripted Drive).
* **WorkManager actually firing on schedule** on a phone (Doze, reboot, battery optimisation). The worker is run
  directly; the periodic and catch-up scheduling calls are exercised only by the app starting.
* **The file picker opening inside `Documents/Fawatir Warsha`** (it is a hint to the system; not observable here).
* **Arabic speech quality.** The CI emulators have no Arabic voice. Only the "voice unavailable" path is exercised.
* **Physical phones beyond one.** CI uses emulators (API 24 and API 34, Pixel 5 profile, 1080 x 2340); the
  instrumented suite also ran on the owner's Samsung (Android 16). Smaller screens and large system font sizes
  were not run.
* **Device restart, airplane mode, low storage, and a failing share target** (see scenarios 15, 18, 20).
* **Text rendering pixel-perfection of the exported files.** The files are decoded and measured by tests;
  legibility was reviewed by looking at the generated images.

## Reproducing the results locally

```bash
./gradlew -p domain test                  # JDK 17 only
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest -PdeviceTests  # OPTIONAL: emulator or phone attached; skipped without -PdeviceTests
```

Screenshots of the real screens and of the exported invoice are published by CI to the throwaway
`ci-screenshots` branch on every push (overwritten each time).
