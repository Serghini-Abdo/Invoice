package ma.workshop.invoice.domain.pricing

import java.math.BigDecimal

enum class LengthUnit(
    /** Stable identifier used in the database and in backups. */
    val id: String,
    /** How many micrometres one thousandth of this unit is. */
    internal val micrometersPerThousandth: Long,
) {
    METER("m", 1_000L),
    CENTIMETER("cm", 10L);

    companion object {
        fun fromId(id: String): LengthUnit? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A measurement with at most three decimals, stored exactly as an integer count of thousandths of
 * [unit] (so 1.2 m is 1200 thousandths of a metre and 120 cm is 120000 thousandths of a centimetre).
 *
 * [micrometers] is the normalised value used for all calculations: both 1.2 m and 120 cm are
 * exactly 1 200 000 micrometres, so unit conversion can never introduce rounding error.
 */
data class Length(val thousandths: Long, val unit: LengthUnit) {

    val micrometers: Long get() = Math.multiplyExact(thousandths, unit.micrometersPerThousandth)

    /** Exact value in metres (scale 6). */
    val meters: BigDecimal get() = BigDecimal.valueOf(micrometers, 6)

    /** Exact value in the original [unit]. */
    val value: BigDecimal get() = BigDecimal.valueOf(thousandths, 3)

    /** Overflow-safe check that this length is at most [maxMicrometers]. */
    fun isAtMost(maxMicrometers: Long): Boolean = thousandths <= maxMicrometers / unit.micrometersPerThousandth

    companion object {
        /** Builds a length from an exact decimal that has at most 3 decimals, or null if it has more. */
        fun ofDecimal(value: BigDecimal, unit: LengthUnit): Length? {
            val stripped = value.stripTrailingZeros()
            if (stripped.scale() > 3) return null
            return Length(stripped.movePointRight(3).setScale(0).longValueExact(), unit)
        }
    }
}
