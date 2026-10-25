package ma.workshop.invoice.domain.backup

import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.invoice.PaymentCalculator
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.PricingEngine
import ma.workshop.invoice.domain.pricing.PricingResult

/**
 * Structural and arithmetic validation of a decoded backup. A restore must never apply a backup
 * that fails here, so every check errs on the side of rejecting.
 *
 * Besides ids and enums, the important check is that every item's stored total, base amount and the
 * invoice totals can be reproduced by the pricing engine from the stored measurements: a backup
 * that was truncated, hand-edited or produced by a buggy build is rejected instead of silently
 * overwriting good local data.
 */
object BackupValidator {

    private const val MAX_TEXT = 1_000
    private const val MAX_ITEMS_PER_INVOICE = 2_000

    fun validate(document: BackupDocument): List<BackupProblem> {
        val problems = mutableListOf<BackupProblem>()
        fun bad(detail: String) {
            problems += BackupProblem(BackupProblemCode.INCONSISTENT, detail)
        }

        // settings
        val settings = document.settings.toDomain()
        if (settings == null) bad("settings: unknown export format '${document.settings.exportFormat}'")
        if (document.settings.nextInvoiceNumber < 1) bad("settings: nextInvoiceNumber < 1")
        if (listOf(
                document.settings.workshopName,
                document.settings.workshopPhone,
                document.settings.thankYouMessage,
            ).any { it.length > MAX_TEXT }
        ) {
            bad("settings: text too long")
        }

        // products
        val productIds = HashSet<Long>()
        for (product in document.products) {
            if (!productIds.add(product.id)) bad("product ${product.id}: duplicate id")
            if (product.toDomain() == null) bad("product ${product.id}: unknown formula or unit")
            if (product.name.isBlank() || product.name.length > MAX_TEXT) bad("product ${product.id}: invalid name")
            if (product.illustration.isBlank()) bad("product ${product.id}: missing illustration")
        }

        // invoices
        val invoiceIds = HashSet<String>()
        val numbers = HashSet<Long>()
        val itemIds = HashSet<String>()
        var maxNumber = 0L

        for (dto in document.invoices) {
            val snapshot = dto.invoice
            val label = "invoice ${snapshot.number}"
            if (!invoiceIds.add(snapshot.id)) bad("$label: duplicate id")
            if (snapshot.number < 1 || !numbers.add(snapshot.number)) bad("$label: invalid or duplicate number")
            maxNumber = maxOf(maxNumber, snapshot.number)

            val invoice = snapshot.toDomain()
            if (invoice == null) {
                bad("$label: unknown value in an item")
                continue
            }
            checkInvoice(invoice, snapshot.totalCentimes, snapshot.remainingCentimes, label, ::bad)
            for (item in invoice.items) {
                if (!itemIds.add(item.id)) bad("$label: duplicate item id ${item.id}")
            }

            val revisionIds = HashSet<String>()
            for (revisionDto in dto.revisions) {
                if (!revisionIds.add(revisionDto.id)) bad("$label: duplicate revision id")
                if (revisionDto.revision !in 2..invoice.revision) bad("$label: revision ${revisionDto.revision} out of range")
                val previous = revisionDto.previous.toDomain()
                if (previous == null) {
                    bad("$label: revision ${revisionDto.revision} has an invalid snapshot")
                } else {
                    checkInvoice(
                        previous,
                        revisionDto.previous.totalCentimes,
                        revisionDto.previous.remainingCentimes,
                        "$label rev ${revisionDto.revision}",
                        ::bad,
                    )
                }
            }
        }

        if (settings != null && document.settings.nextInvoiceNumber <= maxNumber) {
            bad("settings: nextInvoiceNumber must be greater than the highest invoice number")
        }
        return problems
    }

    private fun checkInvoice(
        invoice: Invoice,
        storedTotal: Long,
        storedRemaining: Long,
        label: String,
        bad: (String) -> Unit,
    ) {
        if (invoice.revision < 1) bad("$label: revision < 1")
        if (invoice.items.isEmpty()) bad("$label: no items")
        if (invoice.items.size > MAX_ITEMS_PER_INVOICE) bad("$label: too many items")
        if (invoice.customer.name.length > MAX_TEXT || invoice.customer.phone.length > MAX_TEXT) bad("$label: customer text too long")

        for (item in invoice.items) checkItem(item, label, bad)

        // Totals are always recomputed from the items; stored values must agree.
        val computedTotal = invoice.items.fold(Money.ZERO) { acc, item -> acc + item.pricing.total }
        if (computedTotal.centimes != storedTotal) bad("$label: stored total does not match items")
        if (PaymentCalculator.depositProblem(invoice.deposit, computedTotal) != null) {
            bad("$label: deposit is negative or exceeds the total")
        } else if ((computedTotal - invoice.deposit).centimes != storedRemaining) {
            bad("$label: stored remaining balance is wrong")
        }
    }

    private fun checkItem(item: InvoiceItem, label: String, bad: (String) -> Unit) {
        if (item.productName.isBlank() || item.productName.length > MAX_TEXT) bad("$label: item has an invalid name")
        when (val recomputed = PricingEngine.recompute(item.pricing)) {
            is PricingResult.Failure -> bad("$label: item '${item.productName}' has invalid measurements or rate")
            is PricingResult.Success -> {
                if (recomputed.pricing != item.pricing) {
                    bad("$label: item '${item.productName}' total does not match its measurements")
                }
            }
        }
    }
}
