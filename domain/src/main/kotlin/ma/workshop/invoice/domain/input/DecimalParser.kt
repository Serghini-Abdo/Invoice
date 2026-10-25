package ma.workshop.invoice.domain.input

import ma.workshop.invoice.domain.util.canonical
import java.math.BigDecimal

/** Result of parsing the text of a numeric field. */
sealed interface ParsedNumber {
    data object Empty : ParsedNumber
    data object Malformed : ParsedNumber
    data object Negative : ParsedNumber
    data object TooManyDecimals : ParsedNumber
    data class Value(val value: BigDecimal) : ParsedNumber
}

/**
 * Strict parser for typed numbers.
 *
 * Accepts Western digits, Arabic-Indic digits (٠-٩) and Eastern Arabic-Indic digits (۰-۹), and
 * `.`, `,` or `٫` as the decimal separator. Spaces are ignored. Anything else is [ParsedNumber.Malformed];
 * a leading minus is reported as [ParsedNumber.Negative] so the UI can give a precise message.
 * It never silently rounds: more decimals than allowed is an error.
 */
object DecimalParser {

    private const val MAX_LENGTH = 15

    fun parse(text: String, maxDecimals: Int): ParsedNumber {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return ParsedNumber.Empty

        val negative = normalized.startsWith("-")
        val body = if (negative) normalized.substring(1) else normalized
        if (body.isEmpty() || body.length > MAX_LENGTH) return ParsedNumber.Malformed

        var separators = 0
        var digits = 0
        for (c in body) {
            when {
                c in '0'..'9' -> digits++
                c == '.' -> separators++
                else -> return ParsedNumber.Malformed
            }
        }
        if (digits == 0 || separators > 1) return ParsedNumber.Malformed

        val value = BigDecimal(body)
        if (negative) {
            return if (value.signum() == 0) ParsedNumber.Malformed else ParsedNumber.Negative
        }
        if (value.stripTrailingZeros().scale() > maxDecimals) return ParsedNumber.TooManyDecimals
        return ParsedNumber.Value(value.canonical())
    }

    private fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                in '٠'..'٩' -> out.append('0' + (c - '٠'))
                in '۰'..'۹' -> out.append('0' + (c - '۰'))
                ',', '٫', '.' -> out.append('.')
                '-', '−' -> out.append('-')
                ' ', ' ', ' ', '‎', '‏', '⁦', '⁧', '⁩' -> Unit
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
