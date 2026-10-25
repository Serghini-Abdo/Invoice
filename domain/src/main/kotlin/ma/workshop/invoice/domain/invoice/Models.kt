package ma.workshop.invoice.domain.invoice

import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula

/**
 * A catalog entry: a *definition* of something the workshop makes. It deliberately has no price;
 * the rate is entered for every invoice item.
 */
data class ProductDefinition(
    val id: Long,
    val name: String,
    /** Key into the built-in illustration set, e.g. "door". */
    val illustration: String,
    val formula: PricingFormula,
    val defaultUnit: LengthUnit,
    val category: String? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    fun toRef(): ProductRef = ProductRef(id, name, illustration, formula, defaultUnit)
}

/**
 * The part of a product the invoice flow needs. Carried inside the flow state so entering an item
 * never depends on the catalog still being loadable (and so it survives process death in a draft).
 */
data class ProductRef(
    /** Null when the item no longer references a catalog entry (it was deleted). */
    val id: Long?,
    val name: String,
    val illustration: String,
    val formula: PricingFormula,
    val defaultUnit: LengthUnit,
)

data class CustomerInfo(val name: String = "", val phone: String = "") {
    val isBlank: Boolean get() = name.isBlank() && phone.isBlank()
    fun normalized(): CustomerInfo = CustomerInfo(name.trim(), phone.trim())
}

/**
 * One physical item on an invoice. Everything here is a snapshot taken when the item was
 * confirmed: renaming, re-pricing or deactivating the catalog product later changes nothing.
 */
data class InvoiceItem(
    val id: String,
    /** Link to the catalog for convenience only; the snapshot fields below are authoritative. */
    val productId: Long?,
    val productName: String,
    val illustration: String,
    val pricing: ItemPricing,
)

/** What the user is about to save (new invoice) or has changed (edit of [replacingInvoiceId]). */
data class InvoiceDraft(
    val replacingInvoiceId: String?,
    val customer: CustomerInfo,
    val items: List<InvoiceItem>,
    val deposit: Money,
)

data class Invoice(
    /** Stable for the life of the invoice, including across edits. */
    val id: String,
    val number: Long,
    val createdAt: Long,
    val updatedAt: Long,
    /** 1 for a never-edited invoice; incremented on every saved edit. */
    val revision: Int,
    val customer: CustomerInfo,
    val items: List<InvoiceItem>,
    val deposit: Money,
) {
    val total: Money get() = PaymentCalculator.total(items.map { it.pricing })
    val remaining: Money get() = total - deposit
    val isEdited: Boolean get() = revision > 1
    val payment: PaymentSummary get() = PaymentSummary(total, deposit, remaining)
}

/** Lightweight row for the history list. */
data class InvoiceSummary(
    val id: String,
    val number: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Int,
    val customerName: String,
    val customerPhone: String,
    val itemCount: Int,
    val total: Money,
    val deposit: Money,
    val remaining: Money,
)
