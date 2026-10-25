package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.Field
import ma.workshop.invoice.domain.pricing.FieldIssue
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingEngine
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.PricingResult
import ma.workshop.invoice.domain.pricing.Problem
import ma.workshop.invoice.domain.pricing.RateBasis
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PricingEngineTest {

    private fun m(value: String) = Length.ofDecimal(BigDecimal(value), LengthUnit.METER)!!
    private fun cm(value: String) = Length.ofDecimal(BigDecimal(value), LengthUnit.CENTIMETER)!!
    private fun dh(value: String) = Money.fromExact(BigDecimal(value))

    private fun success(result: PricingResult): ItemPricing {
        assertIs<PricingResult.Success>(result)
        return result.pricing
    }

    private fun issues(result: PricingResult): List<FieldIssue> {
        assertIs<PricingResult.Failure>(result)
        return result.issues
    }

    // Scenario 1
    @Test
    fun `area door 1_20 x 2_10 m at 500 DH per m2 quantity 1 is 1260 DH`() {
        val pricing = success(PricingEngine.price(PricingFormula.AREA, m("1.20"), m("2.10"), 1, dh("500")))
        assertEquals(dh("1260"), pricing.total)
        assertEquals(126_000L, pricing.total.centimes)
        assertEquals(BigDecimal("2.52"), pricing.baseAmount)
        assertEquals(RateBasis.PER_SQUARE_METER, pricing.rateBasis)
    }

    // Scenario 2
    @Test
    fun `quantity 2 doubles the total to 2520 DH`() {
        val pricing = success(PricingEngine.price(PricingFormula.AREA, m("1.20"), m("2.10"), 2, dh("500")))
        assertEquals(dh("2520"), pricing.total)
    }

    // Scenario 3
    @Test
    fun `perimeter frame multiplies perimeter by rate and quantity`() {
        // perimeter = 2 * (1.5 + 2.0) = 7.0 m ; 7.0 * 80 * 3 = 1680
        val pricing = success(PricingEngine.price(PricingFormula.PERIMETER, m("1.5"), m("2"), 3, dh("80")))
        assertEquals(BigDecimal("7"), pricing.baseAmount)
        assertEquals(dh("1680"), pricing.total)
        assertEquals(RateBasis.PER_LINEAR_METER, pricing.rateBasis)
    }

    // Scenario 4
    @Test
    fun `per item is quantity times the entered unit price and drops dimensions`() {
        val pricing = success(PricingEngine.price(PricingFormula.PER_ITEM, m("9"), m("9"), 6, dh("35.50")))
        assertEquals(dh("213"), pricing.total)
        assertNull(pricing.baseAmount)
        assertNull(pricing.width)
        assertNull(pricing.height)
        assertEquals(RateBasis.PER_PIECE, pricing.rateBasis)
    }

    // Scenario 5
    @Test
    fun `centimetres convert exactly to the same result as metres`() {
        val fromCm = success(PricingEngine.price(PricingFormula.AREA, cm("120"), cm("210"), 1, dh("500")))
        val fromM = success(PricingEngine.price(PricingFormula.AREA, m("1.2"), m("2.1"), 1, dh("500")))
        assertEquals(dh("1260"), fromCm.total)
        assertEquals(fromM.total, fromCm.total)
        assertEquals(fromM.baseAmount, fromCm.baseAmount)
        assertEquals(1_200_000L, fromCm.widthMicrometers)
        assertEquals(2_100_000L, fromCm.heightMicrometers)
    }

    @Test
    fun `mixed units work per dimension`() {
        val pricing = success(PricingEngine.price(PricingFormula.AREA, cm("100"), m("2"), 1, dh("600")))
        assertEquals(dh("1200"), pricing.total)
    }

    @Test
    fun `the rounding policy is half up to the centime applied once at the end`() {
        // 0.333 m * 0.333 m = 0.110889 m2 ; * 1 DH = 0.110889 DH -> 0.11
        assertEquals(dh("0.11"), success(PricingEngine.price(PricingFormula.AREA, m("0.333"), m("0.333"), 1, dh("1"))).total)
        // 0.005 DH exactly -> rounds half up to 0.01
        // area 0.001 m * 0.005 ... use per-item style: width 0.5 m, height 0.001 m = 0.0005 m2 ; * 10 DH = 0.005 DH
        assertEquals(dh("0.01"), success(PricingEngine.price(PricingFormula.AREA, m("0.5"), m("0.001"), 1, dh("10"))).total)
        // quantity multiplies before rounding: 0.0005 m2 * 10 DH * 3 = 0.015 -> 0.02 (rounding each piece first would give 0.03)
        assertEquals(dh("0.02"), success(PricingEngine.price(PricingFormula.AREA, m("0.5"), m("0.001"), 3, dh("10"))).total)
    }

    @Test
    fun `no binary floating point error on classic decimal traps`() {
        // 0.1 + 0.2 style traps: 1.1 * 1.1 * 100 = 121 exactly
        assertEquals(dh("121"), success(PricingEngine.price(PricingFormula.AREA, m("1.1"), m("1.1"), 1, dh("100"))).total)
        // 3 * 0.10 is 0.30000000000000004 in doubles
        assertEquals(dh("0.30"), success(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 3, dh("0.10"))).total)
        assertEquals(dh("19.99"), success(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 1, dh("19.99"))).total)
        assertEquals(dh("59.97"), success(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 3, dh("19.99"))).total)
    }

    // Scenario 6
    @Test
    fun `zero or missing measurements are rejected for dimension formulas`() {
        val zeroWidth = issues(PricingEngine.price(PricingFormula.AREA, m("0"), m("2"), 1, dh("500")))
        assertEquals(listOf(FieldIssue(Field.WIDTH, Problem.NOT_POSITIVE)), zeroWidth)

        val missingHeight = issues(PricingEngine.price(PricingFormula.PERIMETER, m("1"), null, 1, dh("500")))
        assertEquals(listOf(FieldIssue(Field.HEIGHT, Problem.REQUIRED)), missingHeight)
    }

    @Test
    fun `negative measurement is rejected`() {
        val result = issues(PricingEngine.price(PricingFormula.AREA, Length(-1200, LengthUnit.METER), m("2"), 1, dh("500")))
        assertEquals(listOf(FieldIssue(Field.WIDTH, Problem.NEGATIVE)), result)
    }

    @Test
    fun `absurdly large measurement is rejected without overflow`() {
        val huge = Length(999_999_999_999_999L, LengthUnit.METER)
        val result = issues(PricingEngine.price(PricingFormula.AREA, huge, m("2"), 1, dh("500")))
        assertEquals(listOf(FieldIssue(Field.WIDTH, Problem.TOO_LARGE)), result)
        assertTrue(PricingEngine.price(PricingFormula.AREA, m("50"), m("50"), 1, dh("500")) is PricingResult.Success)
        assertTrue(PricingEngine.price(PricingFormula.AREA, m("50.001"), m("2"), 1, dh("500")) is PricingResult.Failure)
        assertTrue(PricingEngine.price(PricingFormula.AREA, cm("5000"), m("2"), 1, dh("500")) is PricingResult.Success)
        assertTrue(PricingEngine.price(PricingFormula.AREA, cm("5000.001"), m("2"), 1, dh("500")) is PricingResult.Failure)
    }

    @Test
    fun `invalid quantities are rejected`() {
        assertEquals(
            listOf(FieldIssue(Field.QUANTITY, Problem.NOT_POSITIVE)),
            issues(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 0, dh("10"))),
        )
        assertEquals(
            listOf(FieldIssue(Field.QUANTITY, Problem.NOT_POSITIVE)),
            issues(PricingEngine.price(PricingFormula.PER_ITEM, null, null, -3, dh("10"))),
        )
        assertEquals(
            listOf(FieldIssue(Field.QUANTITY, Problem.TOO_LARGE)),
            issues(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 10_000, dh("10"))),
        )
    }

    @Test
    fun `invalid rates are rejected`() {
        assertEquals(
            listOf(FieldIssue(Field.RATE, Problem.REQUIRED)),
            issues(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 1, null)),
        )
        assertEquals(
            listOf(FieldIssue(Field.RATE, Problem.NEGATIVE)),
            issues(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 1, Money(-100))),
        )
        assertEquals(
            listOf(FieldIssue(Field.RATE, Problem.NOT_POSITIVE)),
            issues(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 1, Money.ZERO)),
        )
        assertEquals(
            listOf(FieldIssue(Field.RATE, Problem.TOO_LARGE)),
            issues(PricingEngine.price(PricingFormula.PER_ITEM, null, null, 1, dh("1000000.01"))),
        )
    }

    @Test
    fun `all issues are reported together`() {
        val result = issues(PricingEngine.price(PricingFormula.AREA, null, null, 0, null))
        assertEquals(
            listOf(
                FieldIssue(Field.WIDTH, Problem.REQUIRED),
                FieldIssue(Field.HEIGHT, Problem.REQUIRED),
                FieldIssue(Field.QUANTITY, Problem.NOT_POSITIVE),
                FieldIssue(Field.RATE, Problem.REQUIRED),
            ),
            result,
        )
    }

    @Test
    fun `recompute reproduces the stored snapshot`() {
        val pricing = success(PricingEngine.price(PricingFormula.AREA, cm("120"), cm("210"), 2, dh("500")))
        assertEquals(pricing, success(PricingEngine.recompute(pricing)))
    }

    @Test
    fun `largest allowed item does not overflow`() {
        val pricing = success(PricingEngine.price(PricingFormula.AREA, m("50"), m("50"), 9_999, dh("1000000")))
        assertEquals(dh("24997500000000"), pricing.total) // 2500 * 1,000,000 * 9,999
    }

    @Test
    fun `length conversion helpers are exact`() {
        assertEquals(BigDecimal("1.200000"), m("1.2").meters)
        assertEquals(BigDecimal("1.200000"), cm("120").meters)
        assertEquals(BigDecimal("0.001000"), cm("0.1").meters)
        assertNull(Length.ofDecimal(BigDecimal("1.2345"), LengthUnit.METER))
        assertEquals(1200L, m("1.20").thousandths)
    }
}
