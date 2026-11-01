# Data model and migration strategy

Local storage is Room over SQLite (`ma.workshop.invoice.data.db`). Everything works offline.

## Tables (schema version 6)

| Table | Purpose | Notes |
|---|---|---|
| `products` | Catalog definitions | Arabic name, picture (`illustration`: a built-in key or `photo:<file>.jpg`), pricing formula, `measurements` shown (`none` / `width` / `height` / `both`), default unit (legacy, the unit is now global), category, `isActive`, sort order, timestamps. **No price column, by design.** |
| `invoices` | One row per invoice | Stable text id (UUID), unique sequential `number`, created/updated timestamps, `revision`, customer name/phone **snapshot**, total / deposit / remaining (centimes), item count. Indexed on number, createdAt, customerName. |
| `invoice_items` | Items of an invoice | FK to `invoices` (`CASCADE`), optional FK to `products` (`SET NULL`). Product name, picture (its own photo copy), formula, original dimensions + units, normalised micrometres, quantity, unit rate, rate basis, exact base amount, total: all snapshots. |
| `invoice_revisions` | Audit trail | One row per saved edit: what changed, old/new totals and deposits, and the **complete previous version** of the invoice as JSON. |
| `app_settings` | Single row | Workshop name/phone, thank-you text, export format, audio switches, speech rate, spoken language, speaker icons switch, length unit, unit label script, next invoice number, "starter catalog seeded" flag. |
| `drafts` | Auto-saved unfinished invoice | JSON of the in-progress workflow state (including unfinished size lines); removed when the invoice is saved or discarded. |

Photos live in the app's private `filesDir/photos`, not in Room. Backups do not include them yet.

## Transactions

* Saving an invoice (allocating its number, writing the invoice, items and, for edits, the revision)
  is a single transaction.
* Restoring a backup is a single transaction: delete everything, insert the backup. Any failure rolls
  the whole thing back, so local data is either completely replaced or completely untouched.
* Invoice numbers come from a counter that only moves forward and are also checked against the highest
  existing number, so a number is never reused, even after deleting the newest invoice.

## Migration strategy

* `AppDatabase.version` only ever increases. `exportSchema = true`: the generated schema JSON of every
  version lives in `app/schemas/` and **must be committed**.
* Every version bump ships a `Migration` added to `AppDatabase.MIGRATIONS`. `fallbackToDestructiveMigration`
  is **never** used, so an app update cannot wipe saved invoices.
* `MigrationTest` (instrumented) creates the previous schema from its JSON, inserts data, runs the
  migrations and validates the result against the current entities. When you add version N+1, add a test
  that opens version N with data in it.
* Backups carry their own `version` (see `BackupFormat`). Older backups must remain restorable; a backup
  from a *newer* app is refused with a clear message instead of being half-understood.

## Version history

| Version | Change | Migration |
|---|---|---|
| 1 | Initial schema. | - |
| 2 | `app_settings.speechRate` (default `normal`). | `MIGRATION_1_2` |
| 3 | `app_settings.speechLanguage` (default `darija`). | `MIGRATION_2_3` |
| 4 | `app_settings.showSpeakers` (default off). | `MIGRATION_3_4` |
| 5 | `app_settings.lengthUnit` (default `m`) and `unitLabelScript` (default `arabic`). | `MIGRATION_4_5` |
| 6 | `products.measurements`: existing area and perimeter products become `both`, per-piece products `none`. Invoices untouched. Width, height and fixed pricing use new formula ids in the existing columns. | `MIGRATION_5_6` |

Every new setting is also carried by backups; older backups restore with the default. Fixed charges store
quantity 1 internally but never show a quantity. Old backups without `measurements` derive it from the formula.

## Adding a column

1. Change the entity and bump `version` to N+1 (the next is 7).
2. Add `MIGRATION_N_N+1` (`ALTER TABLE ...`) to `AppDatabase.MIGRATIONS`.
3. Build once so Room writes `app/schemas/.../N+1.json`; commit it.
4. Extend `MigrationTest` to open version N with data in it.
5. If the backup format changes, keep older backups restorable with a default.
