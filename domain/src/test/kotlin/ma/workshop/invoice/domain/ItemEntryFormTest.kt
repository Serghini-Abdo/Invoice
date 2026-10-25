package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.Field
import ma.workshop.invoice.domain.pricing.FieldIssue
import ma.workshop.invoice.domain.pricing.ItemEntryForm
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.Problem
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemEntryFormTest {

    private fun area(width: String, height: String, rate: String, qty: Int = 1) = ItemEntryForm(
        formula = PricingFormula.AREA,
        widthText = width,
        heightText = height,
        rateText = rate,
        quantity = qty,
    )

    // Scenario 12
    @Test
    fun `quantity starts at 1 for every new item and for every formula`() {
        for (formula in PricingFormula.entries) {
            assertEquals(1, ItemEntryForm.forProduct(formula, LengthUnit.METER).quantity)
        }
        assertEquals(1, ItemEntryForm.DEFAULT_QUANTITY)
    }

    @Test
    fun `new form uses the product's default unit and is not valid until filled in`() {
        val form = ItemEntryForm.forProduct(PricingFormula.AREA, LengthUnit.CENTIMETER)
        assertEquals(LengthUnit.CENTIMETER, form.widthUnit)
        assertEquals(LengthUnit.CENTIMETER, form.heightUnit)
        val evaluation = form.evaluate()
        assertFalse(evaluation.isValid)
        assertEquals(
            listOf(
                FieldIssue(Field.WIDTH, Problem.REQUIRED),
                FieldIssue(Field.HEIGHT, Problem.REQUIRED),
                FieldIssue(Field.RATE, Problem.REQUIRED),
            ),
            evaluation.issues,
        )
    }

    @Test
    fun `typed values produce the spec example`() {
        val evaluation = area("1.20", "2.10", "500").evaluate()
        assertTrue(evaluation.isValid)
        assertEquals(Money(126_000), evaluation.pricing!!.total)
    }

    @Test
    fun `comma and Arabic-Indic digits are accepted`() {
        val evaluation = area("1,20", "٢٫١٠", "٥٠٠").evaluate()
        assertEquals(Money(126_000), evaluation.pricing!!.total)
    }

    @Test
    fun `centimetres and metres can be mixed`() {
        val form = area("120", "2.1", "500").copy(widthUnit = LengthUnit.CENTIMETER)
        assertEquals(Money(126_000), form.evaluate().pricing!!.total)
    }

    @Test
    fun `per item form ignores dimension fields entirely`() {
        val form = ItemEntryForm(PricingFormula.PER_ITEM, quantity = 4, rateText = "25")
        val evaluation = form.evaluate()
        assertEquals(Money(10_000), evaluation.pricing!!.total)
        assertNull(evaluation.pricing!!.width)
    }

    // Scenario 6
    @Test
    fun `malformed negative and over-precise numbers are reported precisely`() {
        assertEquals(Problem.MALFORMED, area("1.2.3", "2", "500").evaluate().issueFor(Field.WIDTH))
        assertEquals(Problem.MALFORMED, area("abc", "2", "500").evaluate().issueFor(Field.WIDTH))
        assertEquals(Problem.NEGATIVE, area("-1", "2", "500").evaluate().issueFor(Field.WIDTH))
        assertEquals(Problem.NOT_POSITIVE, area("0", "2", "500").evaluate().issueFor(Field.WIDTH))
        assertEquals(Problem.TOO_MANY_DECIMALS, area("1.2345", "2", "500").evaluate().issueFor(Field.WIDTH))
        assertEquals(Problem.NEGATIVE, area("1", "2", "-5").evaluate().issueFor(Field.RATE))
        assertEquals(Problem.NOT_POSITIVE, area("1", "2", "0").evaluate().issueFor(Field.RATE))
        assertEquals(Problem.TOO_MANY_DECIMALS, area("1", "2", "5.001").evaluate().issueFor(Field.RATE))
        assertEquals(Problem.REQUIRED, area("1", "2", "").evaluate().issueFor(Field.RATE))
        assertEquals(Problem.TOO_LARGE, area("99999", "2", "5").evaluate().issueFor(Field.WIDTH))
    }

    @Test
    fun `quantity limits come from the engine`() {
        assertEquals(Problem.NOT_POSITIVE, area("1", "2", "5", qty = 0).evaluate().issueFor(Field.QUANTITY))
        assertEquals(Problem.TOO_LARGE, area("1", "2", "5", qty = 10_000).evaluate().issueFor(Field.QUANTITY))
    }

    @Test
    fun `fromPricing restores the exact typed values and units for editing`() {
        val original = ItemEntryForm(
            formula = PricingFormula.AREA,
            widthText = "120",
            widthUnit = LengthUnit.CENTIMETER,
            heightText = "2.1",
            heightUnit = LengthUnit.METER,
            quantity = 3,
            rateText = "450.5",
        )
        val pricing = assertNotNull(original.evaluate().pricing)
        val restored = ItemEntryForm.fromPricing(pricing)
        assertEquals(original, restored)
        assertEquals(pricing, restored.evaluate().pricing)
    }

    @Test
    fun `whole numbers are restored without trailing zeros or exponent notation`() {
        val form = area("100", "200", "1000").copy(widthUnit = LengthUnit.CENTIMETER, heightUnit = LengthUnit.CENTIMETER)
        val pricing = assertNotNull(form.evaluate().pricing)
        assertEquals(BigDecimal("2"), pricing.baseAmount)
        assertEquals(Money(200_000), pricing.total)
        val restored = ItemEntryForm.fromPricing(pricing)
        assertEquals("100", restored.widthText)
        assertEquals("200", restored.heightText)
        assertEquals("1000", restored.rateText)
        assertEquals(form, restored)
    }
}
