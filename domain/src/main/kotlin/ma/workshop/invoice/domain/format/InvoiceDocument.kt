package ma.workshop.invoice.domain.format

import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.settings.UnitLabelScript
import ma.workshop.invoice.domain.settings.AppSettings
import java.time.ZoneId

/** One line of the printed invoice. */
data class DocumentRow(
    val position: Int,
    /** Key of the built-in illustration to draw as a small thumbnail. */
    val illustration: String,
    val name: String,
    /** "1,20 × 2,10 م", null for per-piece items. */
    val measures: String?,
    val quantity: Int,
    /** "500 DH / م²" */
    val rate: String,
    /** "2,52 م² × 500 DH × 2" */
    val calculation: String,
    /** The calculation cell of the table: unit price inside, quantity left out ("2,52 م² × 500 DH"). */
    val formula: String,
    val total: String,
    /** [total] without the currency: the table writes "DH" once, in its column title. */
    val totalNumber: String,
)

/**
 * Everything printed on an invoice, as final strings. The PDF renderer, the PNG renderer and the
 * on-screen preview all draw this one model, which is how the three outputs are guaranteed to carry
 * the same content, calculations and totals.
 */
data class InvoiceDocument(
    val title: String,
    val workshopName: String,
    val workshopPhone: String,
    val number: String,
    val date: String,
    /** Set when the invoice was edited after creation, e.g. "فاتورة معدلة 12/03/2026". */
    val updatedNote: String?,
    val customerName: String?,
    val customerPhone: String?,
    val rows: List<DocumentRow>,
    val total: String,
    val deposit: String,
    val remaining: String,
    /** False when no deposit was entered: the invoice then shows the total only. */
    val hasDeposit: Boolean,
    val isFullyPaid: Boolean,
    val thankYou: String?,
)

object InvoiceDocumentBuilder {

    fun build(invoice: Invoice, settings: AppSettings, zone: ZoneId): InvoiceDocument {
        val rows = invoice.items.mapIndexed { index, item -> row(index + 1, item, settings.unitLabelScript) }
        val payment = invoice.payment
        return InvoiceDocument(
            title = ArabicTerms.INVOICE,
            workshopName = settings.workshopName.trim(),
            workshopPhone = settings.workshopPhone.trim(),
            number = ArabicFormat.invoiceNumber(invoice.number),
            date = ArabicFormat.date(invoice.createdAt, zone),
            updatedNote = if (invoice.isEdited) "${ArabicTerms.UPDATED} ${ArabicFormat.date(invoice.updatedAt, zone)}" else null,
            customerName = invoice.customer.name.trim().ifEmpty { null },
            customerPhone = invoice.customer.phone.trim().ifEmpty { null },
            rows = rows,
            total = ArabicFormat.amount(payment.total),
            deposit = ArabicFormat.amount(payment.deposit),
            remaining = ArabicFormat.amount(payment.remaining),
            hasDeposit = payment.deposit.isPositive,
            isFullyPaid = payment.isFullyPaid,
            thankYou = settings.thankYouMessage.trim().ifEmpty { null },
        )
    }

    /** One table row. The paper and the on-screen table (item list, review, detail) both draw this. */
    fun row(position: Int, item: InvoiceItem, script: UnitLabelScript = UnitLabelScript.ARABIC): DocumentRow {
        val pricing = item.pricing
        return DocumentRow(
            position = position,
            illustration = item.illustration,
            name = item.productName,
            measures = ItemDescriber.measures(pricing, script),
            quantity = pricing.quantity,
            rate = ItemDescriber.rate(pricing, script),
            calculation = ItemDescriber.calculation(pricing, script),
            formula = ItemDescriber.formula(pricing, script),
            total = ArabicFormat.amount(pricing.total),
            totalNumber = ArabicFormat.amountNumber(pricing.total),
        )
    }

    /** ASCII-only, sortable and descriptive base name: invoice-0007-2026-10-25 */
    fun baseFileName(invoice: Invoice, zone: ZoneId): String =
        "invoice-${ArabicFormat.invoiceNumber(invoice.number)}-${ArabicFormat.fileDate(invoice.createdAt, zone)}"

    /** invoice-0007-2026-10-25.pdf */
    fun fileName(invoice: Invoice, extension: String, zone: ZoneId): String =
        "${baseFileName(invoice, zone)}.$extension"
}
