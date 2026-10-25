package ma.workshop.invoice.domain.invoice

import kotlinx.serialization.Serializable
import ma.workshop.invoice.domain.money.Money

enum class ChangeType {
    ITEM_ADDED,
    ITEM_REMOVED,
    ITEM_CHANGED,
    CUSTOMER_CHANGED,
    DEPOSIT_CHANGED,
}

/** One human-meaningful difference between two versions of an invoice. */
@Serializable
data class Change(val type: ChangeType, val label: String = "")

/** Audit record written every time an existing invoice is edited. */
data class InvoiceRevision(
    val id: String,
    val invoiceId: String,
    /** The revision number the invoice moved to with this edit. */
    val revision: Int,
    val changedAt: Long,
    val changes: List<Change>,
    val previousTotal: Money,
    val newTotal: Money,
    val previousDeposit: Money,
    val newDeposit: Money,
    /** The complete invoice as it was before this edit, so every old version can be reconstructed. */
    val previous: Invoice,
)

object InvoiceVersioning {

    /** Builds the first version of an invoice. The number is allocated by the repository, atomically. */
    fun create(draft: InvoiceDraft, id: String, number: Long, now: Long): Invoice = Invoice(
        id = id,
        number = number,
        createdAt = now,
        updatedAt = now,
        revision = 1,
        customer = draft.customer.normalized(),
        items = draft.items,
        deposit = draft.deposit,
    )

    /**
     * Applies an edit: the invoice keeps its id, number and creation time; the revision counter goes
     * up; and a [InvoiceRevision] describing exactly what changed is produced for the audit trail.
     * Totals and the remaining balance are always recomputed from the items.
     */
    fun edit(existing: Invoice, draft: InvoiceDraft, revisionId: String, now: Long): Pair<Invoice, InvoiceRevision> {
        val updated = existing.copy(
            updatedAt = now,
            revision = existing.revision + 1,
            customer = draft.customer.normalized(),
            items = draft.items,
            deposit = draft.deposit,
        )
        val revision = InvoiceRevision(
            id = revisionId,
            invoiceId = existing.id,
            revision = updated.revision,
            changedAt = now,
            changes = diff(existing, updated),
            previousTotal = existing.total,
            newTotal = updated.total,
            previousDeposit = existing.deposit,
            newDeposit = updated.deposit,
            previous = existing,
        )
        return updated to revision
    }

    fun diff(old: Invoice, new: Invoice): List<Change> {
        val changes = mutableListOf<Change>()
        val oldById = old.items.associateBy { it.id }
        val newById = new.items.associateBy { it.id }

        for (item in new.items) {
            val before = oldById[item.id]
            when {
                before == null -> changes += Change(ChangeType.ITEM_ADDED, item.productName)
                before.pricing != item.pricing || before.productName != item.productName ->
                    changes += Change(ChangeType.ITEM_CHANGED, item.productName)
            }
        }
        for (item in old.items) {
            if (item.id !in newById) changes += Change(ChangeType.ITEM_REMOVED, item.productName)
        }
        if (old.customer.normalized() != new.customer.normalized()) {
            changes += Change(ChangeType.CUSTOMER_CHANGED)
        }
        if (old.deposit != new.deposit) {
            changes += Change(ChangeType.DEPOSIT_CHANGED)
        }
        return changes
    }
}
