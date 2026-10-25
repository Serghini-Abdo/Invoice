package ma.workshop.invoice.data.db

import ma.workshop.invoice.domain.backup.SnapshotCodec
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.invoice.InvoiceRevision
import ma.workshop.invoice.domain.invoice.InvoiceSummary
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.ExportFormat
import java.math.BigDecimal

// ---------- products ----------

/** Rows with an unknown formula/unit (written by a future version) are skipped rather than crashing. */
fun ProductEntity.toDomain(): ProductDefinition? = ProductDefinition(
    id = id,
    name = name,
    illustration = illustration,
    formula = PricingFormula.fromId(formula) ?: return null,
    defaultUnit = LengthUnit.fromId(defaultUnit) ?: return null,
    category = category,
    isActive = isActive,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProductDefinition.toEntity() = ProductEntity(
    id = id,
    name = name,
    illustration = illustration,
    formula = formula.id,
    defaultUnit = defaultUnit.id,
    category = category,
    isActive = isActive,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ---------- invoices ----------

fun Invoice.toEntity() = InvoiceEntity(
    id = id,
    number = number,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    customerName = customer.name,
    customerPhone = customer.phone,
    totalCentimes = total.centimes,
    depositCentimes = deposit.centimes,
    remainingCentimes = remaining.centimes,
    itemCount = items.size,
)

fun InvoiceItem.toEntity(invoiceId: String, position: Int) = InvoiceItemEntity(
    id = id,
    invoiceId = invoiceId,
    productId = productId,
    position = position,
    productName = productName,
    illustration = illustration,
    formula = pricing.formula.id,
    widthThousandths = pricing.width?.thousandths,
    widthUnit = pricing.width?.unit?.id,
    widthMicrometers = pricing.widthMicrometers,
    heightThousandths = pricing.height?.thousandths,
    heightUnit = pricing.height?.unit?.id,
    heightMicrometers = pricing.heightMicrometers,
    quantity = pricing.quantity,
    unitRateCentimes = pricing.unitRate.centimes,
    rateBasis = pricing.rateBasis.id,
    baseAmount = pricing.baseAmount?.toPlainString(),
    totalCentimes = pricing.total.centimes,
)

/** Null when the row cannot be interpreted (unknown formula or unit). Callers treat that as corruption. */
fun InvoiceItemEntity.toDomain(): InvoiceItem? {
    val formula = PricingFormula.fromId(formula) ?: return null
    val width = widthThousandths?.let { Length(it, LengthUnit.fromId(widthUnit ?: return null) ?: return null) }
    val height = heightThousandths?.let { Length(it, LengthUnit.fromId(heightUnit ?: return null) ?: return null) }
    return InvoiceItem(
        id = id,
        productId = productId,
        productName = productName,
        illustration = illustration,
        pricing = ItemPricing(
            formula = formula,
            width = width,
            height = height,
            quantity = quantity,
            unitRate = Money(unitRateCentimes),
            baseAmount = baseAmount?.let { BigDecimal(it) },
            total = Money(totalCentimes),
        ),
    )
}

fun InvoiceEntity.toDomain(items: List<InvoiceItem>) = Invoice(
    id = id,
    number = number,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    customer = CustomerInfo(customerName, customerPhone),
    items = items,
    deposit = Money(depositCentimes),
)

fun InvoiceEntity.toSummary() = InvoiceSummary(
    id = id,
    number = number,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    customerName = customerName,
    customerPhone = customerPhone,
    itemCount = itemCount,
    total = Money(totalCentimes),
    deposit = Money(depositCentimes),
    remaining = Money(remainingCentimes),
)

// ---------- revisions ----------

fun InvoiceRevision.toEntity() = RevisionEntity(
    id = id,
    invoiceId = invoiceId,
    revision = revision,
    changedAt = changedAt,
    changesJson = SnapshotCodec.encodeChanges(changes),
    previousTotalCentimes = previousTotal.centimes,
    newTotalCentimes = newTotal.centimes,
    previousDepositCentimes = previousDeposit.centimes,
    newDepositCentimes = newDeposit.centimes,
    previousSnapshotJson = SnapshotCodec.encodeInvoice(previous),
)

/** Null if the stored previous version is unreadable; the audit entry is then skipped, never fatal. */
fun RevisionEntity.toDomain(): InvoiceRevision? {
    val previous = SnapshotCodec.decodeInvoice(previousSnapshotJson) ?: return null
    return InvoiceRevision(
        id = id,
        invoiceId = invoiceId,
        revision = revision,
        changedAt = changedAt,
        changes = SnapshotCodec.decodeChanges(changesJson),
        previousTotal = Money(previousTotalCentimes),
        newTotal = Money(newTotalCentimes),
        previousDeposit = Money(previousDepositCentimes),
        newDeposit = Money(newDepositCentimes),
        previous = previous,
    )
}

// ---------- settings ----------

fun SettingsEntity.toDomain() = AppSettings(
    workshopName = workshopName,
    workshopPhone = workshopPhone,
    exportFormat = ExportFormat.fromId(exportFormat) ?: ExportFormat.PNG,
    audioEnabled = audioEnabled,
    speakItemTotals = speakItemTotals,
    speakInvoiceTotals = speakInvoiceTotals,
    thankYouMessage = thankYouMessage,
    nextInvoiceNumber = nextInvoiceNumber,
)

fun AppSettings.toEntity(catalogSeeded: Boolean) = SettingsEntity(
    id = SettingsEntity.SINGLETON_ID,
    workshopName = workshopName,
    workshopPhone = workshopPhone,
    exportFormat = exportFormat.id,
    audioEnabled = audioEnabled,
    speakItemTotals = speakItemTotals,
    speakInvoiceTotals = speakInvoiceTotals,
    thankYouMessage = thankYouMessage,
    nextInvoiceNumber = nextInvoiceNumber,
    catalogSeeded = catalogSeeded,
)
