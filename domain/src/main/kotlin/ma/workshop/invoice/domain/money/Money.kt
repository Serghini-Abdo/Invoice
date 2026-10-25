package ma.workshop.invoice.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A Moroccan Dirham amount stored as an integer number of centimes (1 DH = 100 centimes).
 *
 * No binary floating point is used anywhere in the money path.
 *
 * Rounding policy (single place, documented in docs/calculation-rules.md):
 * every intermediate value is kept exact ([BigDecimal]); an item total is rounded exactly once,
 * to whole centimes, using [RoundingMode.HALF_UP]. An invoice total is the plain sum of the
 * already-rounded item totals, so the lines printed on an invoice always add up to its total.
 */
@JvmInline
value class Money(val centimes: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(centimes, other.centimes))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(centimes, other.centimes))

    override fun compareTo(other: Money): Int = centimes.compareTo(other.centimes)

    val isZero: Boolean get() = centimes == 0L
    val isNegative: Boolean get() = centimes < 0L
    val isPositive: Boolean get() = centimes > 0L

    /** Exact decimal value in dirhams, always with scale 2. */
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(centimes, 2)

    companion object {
        val ZERO = Money(0L)

        /**
         * Rounds an exact dirham amount to whole centimes (HALF_UP). Goes through BigDecimal.longValueExact
         * because BigInteger.longValueExact is missing on Android before API 31.
         */
        fun fromExact(dirhams: BigDecimal): Money =
            Money(dirhams.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())

        fun sum(values: Iterable<Money>): Money = values.fold(ZERO) { acc, m -> acc + m }
    }
}
