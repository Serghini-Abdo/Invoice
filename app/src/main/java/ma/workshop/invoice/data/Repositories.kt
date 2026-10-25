package ma.workshop.invoice.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ma.workshop.invoice.data.db.AppDatabase
import ma.workshop.invoice.data.db.DraftEntity
import ma.workshop.invoice.data.db.InvoiceEntity
import ma.workshop.invoice.data.db.toDomain
import ma.workshop.invoice.data.db.toEntity
import ma.workshop.invoice.data.db.toSummary
import ma.workshop.invoice.domain.backup.BackupData
import ma.workshop.invoice.domain.backup.InvoiceRecord
import ma.workshop.invoice.domain.catalog.SeedCatalog
import ma.workshop.invoice.domain.flow.DraftCodec
import ma.workshop.invoice.domain.flow.FlowState
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceDraft
import ma.workshop.invoice.domain.invoice.InvoiceRevision
import ma.workshop.invoice.domain.invoice.InvoiceSummary
import ma.workshop.invoice.domain.invoice.InvoiceVersioning
import ma.workshop.invoice.domain.invoice.PaymentCalculator
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.pricing.PricingEngine
import ma.workshop.invoice.domain.pricing.PricingResult
import ma.workshop.invoice.domain.settings.AppSettings
import java.util.UUID

class InvoiceNotFoundException(id: String) : Exception("Invoice $id does not exist")

/** A stored invoice row that cannot be interpreted (unknown formula/unit). Never silently dropped. */
class CorruptInvoiceException(id: String) : Exception("Invoice $id has unreadable items")

class InvalidInvoiceException(message: String) : Exception(message)

/** Wall clock and id source, injectable so tests are deterministic. */
class Environment(
    val now: () -> Long = { System.currentTimeMillis() },
    val newId: () -> String = { UUID.randomUUID().toString() },
)

class SettingsRepository(private val db: AppDatabase) {

    val settings: Flow<AppSettings> = db.settings().observe().map { it?.toDomain() ?: AppSettings() }

    suspend fun get(): AppSettings = db.settings().get()?.toDomain() ?: AppSettings()

    /** Atomically applies [transform]. The invoice counter can never move backwards. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        db.withTransaction {
            val row = db.settings().get()
            val old = row?.toDomain() ?: AppSettings()
            val new = transform(old)
            val safe = new.copy(nextInvoiceNumber = maxOf(old.nextInvoiceNumber, new.nextInvoiceNumber))
            db.settings().upsert(safe.toEntity(catalogSeeded = row?.catalogSeeded ?: false))
        }
    }
}

class ProductRepository(private val db: AppDatabase, private val env: Environment = Environment()) {

    fun observeAll(): Flow<List<ProductDefinition>> = db.products().observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    fun observeActive(): Flow<List<ProductDefinition>> = db.products().observeActive().map { rows -> rows.mapNotNull { it.toDomain() } }

    suspend fun get(id: Long): ProductDefinition? = db.products().get(id)?.toDomain()

    /** Inserts the starter catalog exactly once per installation (and never after a restore). */
    suspend fun ensureSeeded() {
        db.withTransaction {
            val row = db.settings().get()
            if (row?.catalogSeeded == true) return@withTransaction
            if (db.products().count() == 0) {
                db.products().insertAll(SeedCatalog.products(env.now()).map { it.toEntity() })
            }
            val settings = row ?: AppSettings().toEntity(catalogSeeded = true)
            db.settings().upsert(settings.copy(catalogSeeded = true))
        }
    }

    /** Creates a product and returns its id. Edits never touch invoice items, which hold snapshots. */
    suspend fun save(product: ProductDefinition): Long = db.withTransaction {
        val now = env.now()
        if (product.id == 0L) {
            val order = db.products().maxSortOrder() + 1
            db.products().insert(product.copy(sortOrder = order, createdAt = now, updatedAt = now).toEntity())
        } else {
            val existing = db.products().get(product.id)
            db.products().update(product.copy(createdAt = existing?.createdAt ?: now, updatedAt = now).toEntity())
            product.id
        }
    }

    suspend fun setActive(id: Long, active: Boolean) {
        db.products().setActive(id, active, env.now())
    }

    /**
     * Removes a catalog entry. Invoice items keep their snapshots; only the optional link is cleared
     * (foreign key ON DELETE SET NULL), so no historical invoice is affected.
     */
    suspend fun delete(id: Long) {
        db.products().delete(id)
    }
}

class InvoiceRepository(private val db: AppDatabase, private val env: Environment = Environment()) {

    fun observeSummaries(): Flow<List<InvoiceSummary>> = db.invoices().observeAll().map { rows -> rows.map(InvoiceEntity::toSummary) }

    suspend fun get(id: String): Invoice? {
        val row = db.invoices().get(id) ?: return null
        return load(row)
    }

    suspend fun revisions(id: String): List<InvoiceRevision> =
        db.invoices().getRevisions(id).mapNotNull { it.toDomain() }

    suspend fun delete(id: String) {
        db.invoices().delete(id) // items and revisions go with it (ON DELETE CASCADE)
    }

    /**
     * Saves a new invoice or applies an edit, in one transaction: the invoice, all its items, the
     * allocated number and (for edits) the audit revision are written together or not at all.
     * Saving never depends on export or sharing, which happen afterwards.
     */
    suspend fun save(draft: InvoiceDraft): Invoice {
        validate(draft)
        return db.withTransaction {
            val replacing = draft.replacingInvoiceId
            if (replacing == null) createNew(draft) else applyEdit(replacing, draft)
        }
    }

    private suspend fun createNew(draft: InvoiceDraft): Invoice {
        val settingsRow = db.settings().get() ?: AppSettings().toEntity(catalogSeeded = false)
        // Numbers are never reused: take the larger of the counter and (highest existing + 1).
        val number = maxOf(settingsRow.nextInvoiceNumber, db.invoices().maxNumber() + 1)
        db.settings().upsert(settingsRow.copy(nextInvoiceNumber = number + 1))

        val invoice = InvoiceVersioning.create(draft, id = env.newId(), number = number, now = env.now())
        db.invoices().insert(invoice.toEntity())
        db.invoices().insertItems(invoice.items.mapIndexed { index, item -> item.toEntity(invoice.id, index) })
        return invoice
    }

    private suspend fun applyEdit(invoiceId: String, draft: InvoiceDraft): Invoice {
        val row = db.invoices().get(invoiceId) ?: throw InvoiceNotFoundException(invoiceId)
        val existing = load(row)
        val (updated, revision) = InvoiceVersioning.edit(existing, draft, revisionId = env.newId(), now = env.now())

        db.invoices().update(updated.toEntity())
        db.invoices().deleteItems(invoiceId)
        db.invoices().insertItems(updated.items.mapIndexed { index, item -> item.toEntity(invoiceId, index) })
        db.invoices().insertRevision(revision.toEntity())
        return updated
    }

    private suspend fun load(row: InvoiceEntity): Invoice {
        val items = db.invoices().getItems(row.id).map { it.toDomain() ?: throw CorruptInvoiceException(row.id) }
        return row.toDomain(items)
    }

    /** Last line of defence: never persist an invoice whose numbers do not add up. */
    private fun validate(draft: InvoiceDraft) {
        if (draft.items.isEmpty()) throw InvalidInvoiceException("an invoice needs at least one item")
        for (item in draft.items) {
            val recomputed = PricingEngine.recompute(item.pricing)
            if (recomputed !is PricingResult.Success || recomputed.pricing != item.pricing) {
                throw InvalidInvoiceException("item '${item.productName}' does not match its measurements")
            }
        }
        val total = PaymentCalculator.total(draft.items.map { it.pricing })
        if (PaymentCalculator.depositProblem(draft.deposit, total) != null) {
            throw InvalidInvoiceException("deposit must be between 0 and the invoice total")
        }
    }
}

/** Auto-saved unfinished invoice, so work survives phone calls, low-memory kills and accidental exits. */
class DraftRepository(private val db: AppDatabase, private val env: Environment = Environment()) {

    suspend fun load(): FlowState? = db.drafts().get(NEW_KEY)?.json?.let(DraftCodec::decode)

    /** Stores the draft, or removes it when there is nothing worth keeping. */
    suspend fun save(state: FlowState) {
        val json = DraftCodec.encode(state)
        if (json == null) clear() else db.drafts().upsert(DraftEntity(NEW_KEY, json, env.now()))
    }

    suspend fun clear() {
        db.drafts().delete(NEW_KEY)
    }

    private companion object {
        const val NEW_KEY = "new"
    }
}

/** Reads and replaces the whole database for backup and restore. */
class BackupRepository(private val db: AppDatabase) {

    /** A consistent snapshot of everything a backup contains. */
    suspend fun export(): BackupData = db.withTransaction {
        val settingsRow = db.settings().get()
        val items = db.invoices().getAllItems().groupBy { it.invoiceId }
        val revisions = db.invoices().getAllRevisions().groupBy { it.invoiceId }
        val invoices = db.invoices().getAll().map { row ->
            val domainItems = items[row.id].orEmpty().map { it.toDomain() ?: throw CorruptInvoiceException(row.id) }
            InvoiceRecord(
                invoice = row.toDomain(domainItems),
                revisions = revisions[row.id].orEmpty().mapNotNull { it.toDomain() },
            )
        }
        BackupData(
            settings = settingsRow?.toDomain() ?: AppSettings(),
            products = db.products().getAll().mapNotNull { it.toDomain() },
            invoices = invoices,
        )
    }

    /**
     * Replaces all local data with [data] in a single transaction. If anything fails the transaction
     * rolls back and the existing data is untouched. Callers must only pass validated data
     * (see BackupCodec.parse).
     */
    suspend fun restore(data: BackupData) {
        db.withTransaction {
            db.invoices().deleteAll() // cascades to items and revisions
            db.products().deleteAll()

            db.products().insertAll(data.products.map { it.toEntity() })
            val productIds = data.products.map { it.id }.toSet()

            // A restore is a full catalog: never re-seed over it.
            db.settings().upsert(data.settings.toEntity(catalogSeeded = true))

            for (record in data.invoices) {
                val invoice = record.invoice
                db.invoices().insert(invoice.toEntity())
                db.invoices().insertItems(
                    invoice.items.mapIndexed { index, item ->
                        // The product may have been deleted before the backup was made.
                        item.copy(productId = item.productId?.takeIf { it in productIds }).toEntity(invoice.id, index)
                    },
                )
                if (record.revisions.isNotEmpty()) {
                    db.invoices().insertRevisions(record.revisions.map { it.toEntity() })
                }
            }
        }
    }
}
