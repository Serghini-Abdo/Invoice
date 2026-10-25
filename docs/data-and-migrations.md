# Data model and migration strategy

Local storage is Room over SQLite (`ma.workshop.invoice.data.db`). Everything works offline.

## Tables (schema version 3)

| Table | Purpose | Notes |
|---|---|---|
| `products` | Catalog definitions | Arabic name, illustration key, pricing formula, default unit, category, `isActive`, sort order, timestamps. **No price column.** |
| `invoices` | One row per invoice | Stable text id (UUID), unique sequential `number`, created/updated timestamps, `revision`, customer name/phone **snapshot**, total / deposit / remaining (centimes), item count. Indexed on number, createdAt, customerName. |
| `invoice_items` | Items of an invoice | FK to `invoices` (`CASCADE`), optional FK to `products` (`SET NULL`). Product name, illustration, formula, original dimensions + units, normalised micrometres, quantity, unit rate, rate basis, exact base amount, total: all snapshots. |
| `invoice_revisions` | Audit trail | One row per saved edit: what changed, old/new totals and deposits, and the **complete previous version** of the invoice as JSON. |
| `app_settings` | Single row | Workshop name/phone, export format, audio switches, speech rate (`slow` / `normal` / `fast`, added in version 2), spoken language (`darija` / `standard`, added in version 3), thank-you text, next invoice number, "starter catalog seeded" flag. |
| `drafts` | Auto-saved unfinished invoice | JSON of the in-progress workflow state; removed when the invoice is saved or discarded. |

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
| 2 | `app_settings.speechRate TEXT NOT NULL DEFAULT 'normal'` (Day 2, sprint 3). Backups gained an optional `speechRate`; older backups restore with `normal`. | `MIGRATION_1_2` |
| 3 | `app_settings.speechLanguage TEXT NOT NULL DEFAULT 'darija'` (Day 2, sprint 3 add-on). Backups gained an optional `speechLanguage`; older backups restore as Darija. | `MIGRATION_2_3` |

## Adding a column (example)

1. Change the entity, bump `version = 2`.
2. Add `val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE ...") } }`
   and put it in `MIGRATIONS`.
3. Build once so Room writes `app/schemas/.../2.json`; commit it.
4. Extend `MigrationTest` for 1 -> 2.
