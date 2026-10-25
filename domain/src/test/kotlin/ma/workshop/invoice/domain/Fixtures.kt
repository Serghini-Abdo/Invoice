package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.backup.BackupData
import ma.workshop.invoice.domain.backup.InvoiceRecord
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceDraft
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.invoice.InvoiceVersioning
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingEngine
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.PricingResult
import ma.workshop.invoice.domain.settings.AppSettings
import java.math.BigDecimal

object Fixtures {

    fun m(value: String) = Length.ofDecimal(BigDecimal(value), LengthUnit.METER)!!
    fun cm(value: String) = Length.ofDecimal(BigDecimal(value), LengthUnit.CENTIMETER)!!
    fun dh(value: String) = Money.fromExact(BigDecimal(value))

    fun pricing(
        formula: PricingFormula,
        width: Length? = null,
        height: Length? = null,
        quantity: Int = 1,
        rate: String,
    ): ItemPricing {
        val result = PricingEngine.price(formula, width, height, quantity, dh(rate))
        return (result as PricingResult.Success).pricing
    }

    fun item(id: String, name: String, illustration: String, pricing: ItemPricing, productId: Long? = 1) =
        InvoiceItem(id, productId, name, illustration, pricing)

    val door = ProductDefinition(1, "باب حديد", "door", PricingFormula.AREA, LengthUnit.METER, createdAt = 1, updatedAt = 1)
    val hinges = ProductDefinition(5, "مفصلات", "hinges", PricingFormula.PER_ITEM, LengthUnit.METER, createdAt = 1, updatedAt = 1)

    fun doorItem(id: String, width: String, height: String, rate: String, qty: Int = 1) = item(
        id, "باب حديد", "door", pricing(PricingFormula.AREA, m(width), m(height), qty, rate),
    )

    fun hingesItem(id: String, qty: Int, rate: String) = item(
        id, "مفصلات", "hinges", pricing(PricingFormula.PER_ITEM, quantity = qty, rate = rate), productId = 5,
    )

    fun invoice(
        id: String = "inv-1",
        number: Long = 1,
        items: List<InvoiceItem> = listOf(doorItem("i1", "1.20", "2.10", "500"), hingesItem("i2", 4, "30")),
        deposit: Money = dh("500"),
        customer: CustomerInfo = CustomerInfo("أحمد", "0612345678"),
        now: Long = 1_700_000_000_000,
    ): Invoice = InvoiceVersioning.create(InvoiceDraft(null, customer, items, deposit), id, number, now)

    fun settings(nextNumber: Long = 3) = AppSettings(
        workshopName = "ورشة الأمل للحدادة",
        workshopPhone = "0522000000",
        nextInvoiceNumber = nextNumber,
    )

    /** A backup with two invoices; the second one was edited once so it has a revision. */
    fun backupData(): BackupData {
        val first = invoice("inv-1", 1)
        val second = invoice("inv-2", 2, items = listOf(hingesItem("i3", 2, "40")), deposit = dh("20"))
        val edit = InvoiceDraft(
            replacingInvoiceId = second.id,
            customer = second.customer,
            items = listOf(hingesItem("i3", 5, "40")),
            deposit = dh("50"),
        )
        val (edited, revision) = InvoiceVersioning.edit(second, edit, "rev-1", now = 1_700_000_100_000)
        return BackupData(
            settings = settings(),
            products = listOf(door, hinges),
            invoices = listOf(InvoiceRecord(first, emptyList()), InvoiceRecord(edited, listOf(revision))),
        )
    }
}
