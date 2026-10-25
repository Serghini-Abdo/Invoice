package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.invoice.DepositParse
import ma.workshop.invoice.domain.invoice.PaymentCalculator
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentTest {

    private fun dh(value: Long) = Money(value * 100)

    // Scenario 8
    @Test
    fun `remaining balance is total minus deposit`() {
        val summary = assertNotNull(PaymentCalculator.summarize(dh(2520), dh(1000)))
        assertEquals(dh(2520), summary.total)
        assertEquals(dh(1000), summary.deposit)
        assertEquals(dh(1520), summary.remaining)
        assertFalse(summary.isFullyPaid)
    }

    @Test
    fun `deposit defaults to zero so the remaining balance equals the total`() {
        val parsed = PaymentCalculator.parseDeposit("", dh(300))
        assertEquals(DepositParse.Valid(Money.ZERO), parsed)
        assertEquals(dh(300), PaymentCalculator.summarize(dh(300), Money.ZERO)!!.remaining)
    }

    @Test
    fun `a deposit equal to the total means fully paid`() {
        val summary = assertNotNull(PaymentCalculator.summarize(dh(300), dh(300)))
        assertEquals(Money.ZERO, summary.remaining)
        assertTrue(summary.isFullyPaid)
    }

    @Test
    fun `an empty invoice is never reported as fully paid`() {
        assertFalse(assertNotNull(PaymentCalculator.summarize(Money.ZERO, Money.ZERO)).isFullyPaid)
    }

    // Scenario 7
    @Test
    fun `deposit cannot exceed the invoice total`() {
        assertEquals(Problem.EXCEEDS_TOTAL, PaymentCalculator.depositProblem(Money(30_001), Money(30_000)))
        assertNull(PaymentCalculator.summarize(Money(30_000), Money(30_001)))
        assertEquals(
            DepositParse.Invalid(Problem.EXCEEDS_TOTAL),
            PaymentCalculator.parseDeposit("300.01", Money(30_000)),
        )
    }

    // Scenario 6
    @Test
    fun `negative and malformed deposits are rejected`() {
        assertEquals(Problem.NEGATIVE, PaymentCalculator.depositProblem(Money(-1), Money(30_000)))
        assertEquals(DepositParse.Invalid(Problem.NEGATIVE), PaymentCalculator.parseDeposit("-5", dh(100)))
        assertEquals(DepositParse.Invalid(Problem.MALFORMED), PaymentCalculator.parseDeposit("5x", dh(100)))
        assertEquals(DepositParse.Invalid(Problem.TOO_MANY_DECIMALS), PaymentCalculator.parseDeposit("5.123", dh(100)))
    }

    @Test
    fun `deposit may be typed with a comma or Arabic digits`() {
        assertEquals(DepositParse.Valid(Money(25_050)), PaymentCalculator.parseDeposit("250,5", dh(1000)))
        assertEquals(DepositParse.Valid(Money(50_000)), PaymentCalculator.parseDeposit("٥٠٠", dh(1000)))
    }

    @Test
    fun `no tax is ever added`() {
        val total = PaymentCalculator.total(emptyList())
        assertEquals(Money.ZERO, total)
        val summary = assertNotNull(PaymentCalculator.summarize(Money(126_000), Money.ZERO))
        assertEquals(Money(126_000), summary.remaining)
    }
}
