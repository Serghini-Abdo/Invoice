# فواتير الورشة: Workshop Invoice

An offline-first Android invoicing app for a small Moroccan metalworking workshop (doors, windows,
garage gates, frames, accessories). Arabic first, right-to-left, illustration first, built for an owner
who reads little: large buttons, large numbers, one decision per screen, optional spoken Arabic.

* Kotlin, Jetpack Compose + Material 3, Room (SQLite), Android TextToSpeech, Android Sharesheet.
* **Everything for normal invoicing works with no internet and no account.** Google Drive backup is an
  optional, explicit action in Settings.
* No taxes, no inventory, no accounting: invoices only.

> **Status.** The app is built and tested by the GitHub Actions workflow in this repository
> (`.github/workflows/ci.yml`): domain unit tests, Android build, lint and an emulator run of the
> instrumented tests. See [Verification](#verification-what-has-and-has-not-been-proven) for exactly what is
> proven and what is not (for example, Google Drive has not been exercised against a real Google account).

## Using the app

Home has exactly three buttons: **فاتورة جديدة** (new invoice), **الفواتير السابقة** (previous invoices),
**الإعدادات** (settings).

New invoice:

1. **Customer** (optional): name and phone. Continue immediately if you like.
2. **Items**: the list of items so far, each with a small picture, measurements and total; a big
   **+ إضافة منتوج** button; **متابعة** to go on. Items can be edited or removed (removal asks first).
3. **Choose a product** from the picture grid (two columns). Tapping the picture selects it; the small
   speaker under each card only speaks the name and never selects.
4. **Details**: only the fields the product needs. Width/height in metres or centimetres (switch beside each
   number), the rate typed for *this* item, quantity (starts at 1, big - / + buttons). A big keypad with
   decimal, backspace and clear. The total updates on every keypress.
5. **Confirm**: picture, name, measurements, quantity, rate and the full calculation, then **تأكيد** or **إلغاء**.
   After confirming you are always back on the item list, never in the catalog or at payment.
6. **Review**: all items, the total, an editable deposit (default 0, cannot exceed the total), the remaining
   balance and an explicit "خالص" (fully paid) state. A final confirmation precedes saving.
7. **Saved**: the invoice is stored first; then the PNG/PDF is generated on the phone, previewed, and
   shared through Android's share sheet (WhatsApp appears there when installed). The app never claims a
   message was sent, only that the share sheet was opened.

The same product can be added any number of times; every addition is an independent item with its own size,
rate and quantity. Nothing is merged.

Settings contains only: the product catalog, workshop name/phone (and an optional thank-you line),
the invoice format (PNG / PDF / both and choose when sharing), audio, and backup/restore.

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
Without it, the Drive buttons show a clear message and the file backup still works.

## Tests

```bash
./gradlew -p domain test                  # pure-Kotlin unit tests, needs only a JDK (no Android SDK)
./gradlew :app:testDebugUnitTest          # (no app-level JVM tests; the logic lives in :domain)
./gradlew :app:connectedDebugAndroidTest  # emulator/device: Room, migrations, PDF/PNG export, full UI workflow
```

## Architecture

```
domain/   pure Kotlin/JVM (no Android): everything that must be correct
  money/ pricing/ input/        exact money + measurements, pricing engine, validation, keypad rules
  flow/                         InvoiceFlow: the workflow as a pure state machine; draft auto-save codec
  invoice/                      models, payment math, versioning/audit, history search
  format/ layout/               Arabic formatting, invoice document model, page layout + pagination
  backup/                       backup format, strict validator, snapshot codec
  catalog/                      built-in illustrations (SVG path data) and the starter catalog
app/      Android: Compose UI, Room, TextToSpeech, PDF/PNG renderer, FileProvider, Google Drive client
  data/        Room entities, DAOs, repositories (transactions)
  platform/    speech, export (renderer/exporter/share), Drive client, restore safety
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

