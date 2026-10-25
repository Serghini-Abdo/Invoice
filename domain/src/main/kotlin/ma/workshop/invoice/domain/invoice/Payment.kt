package ma.workshop.invoice.domain.invoice

import ma.workshop.invoice.domain.input.MoneyParse
import ma.workshop.invoice.domain.input.MoneyParser
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Problem

/** Invoice total, the deposit already received and what is still owed. No taxes of any kind. */
data class PaymentSummary(val total: Money, val deposit: Money, val remaining: Money) {
    /** True when everything is paid (and there is something to pay). */
    val isFullyPaid: Boolean get() = total.isPositive && remaining.isZero
}

sealed interface DepositParse {
    data class Valid(val deposit: Money) : DepositParse
    data class Invalid(val problem: Problem) : DepositParse
}

object PaymentCalculator {

    /** Invoice total = sum of all (already rounded) item totals. */
    fun total(items: List<ItemPricing>): Money = Money.sum(items.map { it.total })

    /** Null when the deposit is acceptable for [total]: it must be within 0..total. */
    fun depositProblem(deposit: Money, total: Money): Problem? = when {
        deposit.isNegative -> Problem.NEGATIVE
        deposit > total -> Problem.EXCEEDS_TOTAL
        else -> null
    }

    /** Remaining balance = total − deposit, or null if the deposit is invalid for this total. */
    fun summarize(total: Money, deposit: Money): PaymentSummary? =
        if (depositProblem(deposit, total) != null) null
        else PaymentSummary(total, deposit, total - deposit)

    /** Parses the deposit field. Empty means "no deposit" (zero), which is the default. */
    fun parseDeposit(text: String, total: Money): DepositParse = when (val parsed = MoneyParser.parse(text)) {
        MoneyParse.Empty -> DepositParse.Valid(Money.ZERO)
        is MoneyParse.Invalid -> DepositParse.Invalid(parsed.problem)
        is MoneyParse.Value -> depositProblem(parsed.money, total)
            ?.let { DepositParse.Invalid(it) }
            ?: DepositParse.Valid(parsed.money)
    }
}
