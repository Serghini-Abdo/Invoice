package ma.workshop.invoice.domain.backup

import kotlinx.serialization.Serializable
import ma.workshop.invoice.domain.invoice.Change
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.invoice.InvoiceRevision
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.RateBasis
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.ExportFormat
import ma.workshop.invoice.domain.settings.SpeechLanguage
import ma.workshop.invoice.domain.settings.SpeechRate
import java.math.BigDecimal

/*
 * Serializable mirrors of the domain model, shared by the Drive/file backup, the auto-saved draft
 * and the revision history. Enums are stored as stable string ids, money as integer centimes and
 * lengths as integer thousandths, so the format is exact and independent of Kotlin class names.
 */

@Serializable
data class SettingsDto(
    val workshopName: String,
    val workshopPhone: String,
    val exportFormat: String,
    val audioEnabled: Boolean,
    val speakItemTotals: Boolean,
    val speakInvoiceTotals: Boolean,
    val thankYouMessage: String,
    val nextInvoiceNumber: Long,
    /** Added in Day 2; backups made before it restore with the normal rate. */
    val speechRate: String = SpeechRate.NORMAL.id,
    /** Added in Day 2; backups made before it restore in Darija. */
    val speechLanguage: String = SpeechLanguage.DARIJA.id,
    /** Added in Day 2; older backups restore with the speaker icons hidden. */
    val showSpeakers: Boolean = false,
)

@Serializable
data class ProductDto(
    val id: Long,
    val name: String,
    val illustration: String,
    val formula: String,
    val defaultUnit: String,
    val category: String? = null,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ItemDto(
    val id: String,
    val productId: Long? = null,
    val productName: String,
    val illustration: String,
    val formula: String,
    val widthThousandths: Long? = null,
    val widthUnit: String? = null,
    val heightThousandths: Long? = null,
    val heightUnit: String? = null,
    val quantity: Int,
    val unitRateCentimes: Long,
    val rateBasis: String,
    val baseAmount: String? = null,
    val totalCentimes: Long,
)

/** An invoice without its audit trail; also the "previous version" stored in a revision. */
@Serializable
data class InvoiceSnapshotDto(
    val id: String,
    val number: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Int,
    val customerName: String,
    val customerPhone: String,
    val depositCentimes: Long,
    val totalCentimes: Long,
    val remainingCentimes: Long,
    val items: List<ItemDto>,
)

@Serializable
data class RevisionDto(
    val id: String,
    val revision: Int,
    val changedAt: Long,
    val changes: List<Change>,
    val previousTotalCentimes: Long,
    val newTotalCentimes: Long,
    val previousDepositCentimes: Long,
    val newDepositCentimes: Long,
    val previous: InvoiceSnapshotDto,
)

@Serializable
data class InvoiceDto(
    val invoice: InvoiceSnapshotDto,
    val revisions: List<RevisionDto> = emptyList(),
)

// ---------- mapping: domain -> dto ----------

fun AppSettings.toDto() = SettingsDto(
    workshopName, workshopPhone, exportFormat.id, audioEnabled, speakItemTotals, speakInvoiceTotals,
    thankYouMessage, nextInvoiceNumber, speechRate.id, speechLanguage.id, showSpeakers,
)

fun ProductDefinition.toDto() = ProductDto(
    id, name, illustration, formula.id, defaultUnit.id, category, isActive, sortOrder, createdAt, updatedAt,
)

fun InvoiceItem.toDto() = ItemDto(
    id = id,
    productId = productId,
    productName = productName,
    illustration = illustration,
    formula = pricing.formula.id,
    widthThousandths = pricing.width?.thousandths,
    widthUnit = pricing.width?.unit?.id,
    heightThousandths = pricing.height?.thousandths,
    heightUnit = pricing.height?.unit?.id,
    quantity = pricing.quantity,
    unitRateCentimes = pricing.unitRate.centimes,
    rateBasis = pricing.rateBasis.id,
    baseAmount = pricing.baseAmount?.toPlainString(),
    totalCentimes = pricing.total.centimes,
)

fun Invoice.toSnapshotDto() = InvoiceSnapshotDto(
    id = id,
    number = number,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    customerName = customer.name,
    customerPhone = customer.phone,
    depositCentimes = deposit.centimes,
    totalCentimes = total.centimes,
    remainingCentimes = remaining.centimes,
    items = items.map { it.toDto() },
)

fun InvoiceRevision.toDto() = RevisionDto(
    id = id,
    revision = revision,
    changedAt = changedAt,
    changes = changes,
    previousTotalCentimes = previousTotal.centimes,
    newTotalCentimes = newTotal.centimes,
    previousDepositCentimes = previousDeposit.centimes,
    newDepositCentimes = newDeposit.centimes,
    previous = previous.toSnapshotDto(),
)

// ---------- mapping: dto -> domain (null when a value is not a known/valid one) ----------

fun SettingsDto.toDomain(): AppSettings? {
    val format = ExportFormat.fromId(exportFormat) ?: return null
    return AppSettings(
        workshopName, workshopPhone, format, audioEnabled, speakItemTotals, speakInvoiceTotals,
        thankYouMessage, nextInvoiceNumber,
        // A rate written by a newer app is not a reason to refuse the whole backup.
        speechRate = SpeechRate.fromId(speechRate) ?: SpeechRate.NORMAL,
        speechLanguage = SpeechLanguage.fromId(speechLanguage) ?: SpeechLanguage.DARIJA,
        showSpeakers = showSpeakers,
    )
}

fun ProductDto.toDomain(): ProductDefinition? {
    return ProductDefinition(
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
}

fun ItemDto.toDomain(): InvoiceItem? {
    val formula = PricingFormula.fromId(formula) ?: return null
    if (RateBasis.fromId(rateBasis) != formula.rateBasis) return null
    val width = widthThousandths?.let { Length(it, LengthUnit.fromId(widthUnit ?: return null) ?: return null) }
    val height = heightThousandths?.let { Length(it, LengthUnit.fromId(heightUnit ?: return null) ?: return null) }
    val base = baseAmount?.let { text -> runCatching { BigDecimal(text) }.getOrNull() ?: return null }
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
            baseAmount = base,
            total = Money(totalCentimes),
        ),
    )
}

fun InvoiceSnapshotDto.toDomain(): Invoice? {
    val domainItems = items.map { it.toDomain() ?: return null }
    return Invoice(
        id = id,
        number = number,
        createdAt = createdAt,
        updatedAt = updatedAt,
        revision = revision,
        customer = CustomerInfo(customerName, customerPhone),
        items = domainItems,
        deposit = Money(depositCentimes),
    )
}

fun RevisionDto.toDomain(invoiceId: String): InvoiceRevision? {
    return InvoiceRevision(
        id = id,
        invoiceId = invoiceId,
        revision = revision,
        changedAt = changedAt,
        changes = changes,
        previousTotal = Money(previousTotalCentimes),
        newTotal = Money(newTotalCentimes),
        previousDeposit = Money(previousDepositCentimes),
        newDeposit = Money(newDepositCentimes),
        previous = previous.toDomain() ?: return null,
    )
}
