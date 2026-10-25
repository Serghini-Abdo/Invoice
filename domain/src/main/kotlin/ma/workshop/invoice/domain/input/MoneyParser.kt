package ma.workshop.invoice.domain.input

import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.Problem
import ma.workshop.invoice.domain.util.canonical
import java.math.BigDecimal

sealed interface MoneyParse {
    data object Empty : MoneyParse
    data class Invalid(val problem: Problem) : MoneyParse
    data class Value(val money: Money) : MoneyParse
}

object MoneyParser {

    /** Parses dirhams typed by the user (at most two decimals) into [Money]. */
    fun parse(text: String): MoneyParse = when (val parsed = DecimalParser.parse(text, maxDecimals = 2)) {
        ParsedNumber.Empty -> MoneyParse.Empty
        ParsedNumber.Malformed -> MoneyParse.Invalid(Problem.MALFORMED)
        ParsedNumber.Negative -> MoneyParse.Invalid(Problem.NEGATIVE)
        ParsedNumber.TooManyDecimals -> MoneyParse.Invalid(Problem.TOO_MANY_DECIMALS)
        is ParsedNumber.Value -> {
            if (parsed.value > MAX_PARSEABLE) MoneyParse.Invalid(Problem.TOO_LARGE)
            else MoneyParse.Value(Money.fromExact(parsed.value))
        }
    }

    /** Formats money as editable text for a field: "500", "450.5", "450.25". */
    fun toEditableText(money: Money): String {
        return money.toBigDecimal().canonical().toPlainString()
    }

    private val MAX_PARSEABLE = BigDecimal("100000000000")
}
