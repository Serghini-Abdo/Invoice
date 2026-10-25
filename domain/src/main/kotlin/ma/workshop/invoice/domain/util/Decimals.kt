package ma.workshop.invoice.domain.util

import java.math.BigDecimal

/**
 * Canonical form of a decimal: no trailing zeros and never exponent notation, so numerically equal
 * values are also `equals` and always print with [BigDecimal.toPlainString] as "1200", "1.2", "0.05".
 */
fun BigDecimal.canonical(): BigDecimal {
    val stripped = stripTrailingZeros()
    return if (stripped.scale() < 0) stripped.setScale(0) else stripped
}
