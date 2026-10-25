package ma.workshop.invoice

import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.invoice.InvoiceDraft
import ma.workshop.invoice.domain.invoice.InvoiceItem
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingEngine
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.PricingResult
import java.math.BigDecimal

/** Builders for instrumented tests. They go through the real pricing engine, so snapshots are genuine. */
object TestData {

    fun m(value: String) = Length.ofDecimal(BigDecimal(value), LengthUnit.METER)!!
    fun dh(value: String) = Money.fromExact(BigDecimal(value))

    fun pricing(
        formula: PricingFormula,
        width: Length? = null,
        height: Length? = null,
        quantity: Int = 1,
        rate: String,
    ): ItemPricing =
        (PricingEngine.price(formula, width, height, quantity, dh(rate)) as PricingResult.Success).pricing

    fun door(id: String, width: String, height: String, rate: String, quantity: Int = 1, productId: Long? = null) = InvoiceItem(
        id = id,
        productId = productId,
        productName = "باب حديد",
        illustration = "door",
        pricing = pricing(PricingFormula.AREA, m(width), m(height), quantity, rate),
    )

    fun hinges(id: String, quantity: Int, rate: String, productId: Long? = null) = InvoiceItem(
        id = id,
        productId = productId,
        productName = "مفصلات",
        illustration = "hinges",
        pricing = pricing(PricingFormula.PER_ITEM, quantity = quantity, rate = rate),
    )

    fun draft(
        items: List<InvoiceItem>,
        deposit: String = "0",
        customer: CustomerInfo = CustomerInfo("أحمد", "0612345678"),
        replacing: String? = null,
    ) = InvoiceDraft(replacing, customer, items, dh(deposit))
}
