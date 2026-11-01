package ma.workshop.invoice.domain.pricing

import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.util.canonical
import java.math.BigDecimal

enum class Measurements(val id: String, val width: Boolean, val height: Boolean) {
    NONE("none", false, false), WIDTH("width", true, false), HEIGHT("height", false, true), BOTH("both", true, true);

    companion object {
        fun fromId(id: String): Measurements? = entries.firstOrNull { it.id == id }
        fun of(width: Boolean, height: Boolean): Measurements = entries.first { it.width == width && it.height == height }
    }
}

/** Each method owns its required inputs and exact base calculation. */
enum class PricingFormula(
    val id: String,
    val measurements: Measurements,
    val rateBasis: RateBasis,
    val usesQuantity: Boolean = true,
    val base: (Length?, Length?) -> BigDecimal?,
) {
    AREA("area", Measurements.BOTH, RateBasis.PER_SQUARE_METER, base = { w, h -> w!!.meters.multiply(h!!.meters) }),
    PERIMETER("perimeter", Measurements.BOTH, RateBasis.PER_LINEAR_METER, base = { w, h -> w!!.meters.add(h!!.meters).multiply(BigDecimal(2)) }),
    PER_ITEM("per_item", Measurements.NONE, RateBasis.PER_PIECE, base = { _, _ -> null }),
    WIDTH("width", Measurements.WIDTH, RateBasis.PER_LINEAR_METER, base = { w, _ -> w!!.meters }),
    HEIGHT("height", Measurements.HEIGHT, RateBasis.PER_LINEAR_METER, base = { _, h -> h!!.meters }),
    FIXED("fixed", Measurements.NONE, RateBasis.FIXED, usesQuantity = false, base = { _, _ -> null });

    val needsDimensions: Boolean get() = measurements != Measurements.NONE

    fun visibleMeasurements(optional: Measurements): Measurements =
        if (this == PER_ITEM) optional else measurements

    companion object {
        fun fromId(id: String): PricingFormula? = entries.firstOrNull { it.id == id }
    }
}

enum class RateBasis(val id: String) {
    PER_SQUARE_METER("m2"),
    PER_LINEAR_METER("m"),
    PER_PIECE("piece"),
    FIXED("fixed");

    companion object {
        fun fromId(id: String): RateBasis? = entries.firstOrNull { it.id == id }
    }
}

enum class Field { WIDTH, HEIGHT, QUANTITY, RATE, DEPOSIT }

enum class Problem {
    REQUIRED,
    MALFORMED,
    NOT_POSITIVE,
    NEGATIVE,
    TOO_LARGE,
    TOO_MANY_DECIMALS,
    EXCEEDS_TOTAL,
}

data class FieldIssue(val field: Field, val problem: Problem)

/** Hard limits that protect the arithmetic (and the owner) from typing mistakes. */
object PricingLimits {
    /** 50 m in micrometres. */
    const val MAX_DIMENSION_MICROMETERS: Long = 50_000_000L
    const val MAX_QUANTITY: Int = 9_999
    val MAX_RATE: Money = Money(100_000_000L) // 1 000 000 DH
    const val MAX_DIMENSION_DECIMALS: Int = 3
    const val MAX_RATE_DECIMALS: Int = 2
}

/**
 * The immutable pricing snapshot stored on an invoice item when it is confirmed.
 *
 * It holds everything needed to reproduce the total (formula, measurements with their original
 * units, quantity, unit rate), plus the exact [baseAmount] (area in m² or perimeter in m for one
 * piece). Later edits of the catalog can never change it.
 */
data class ItemPricing(
    val formula: PricingFormula,
    val width: Length?,
    val height: Length?,
    val quantity: Int,
    val unitRate: Money,
    /** Exact area (m²) for AREA, exact perimeter (m) for PERIMETER, null for PER_ITEM. Canonical scale. */
    val baseAmount: BigDecimal?,
    val total: Money,
) {
    val rateBasis: RateBasis get() = formula.rateBasis

    /** Normalised measurements, in micrometres. */
    val widthMicrometers: Long? get() = width?.micrometers
    val heightMicrometers: Long? get() = height?.micrometers
}

sealed interface PricingResult {
    data class Success(val pricing: ItemPricing) : PricingResult
    data class Failure(val issues: List<FieldIssue>) : PricingResult
}

object PricingEngine {

    /**
     * Prices one invoice item.
     *
     * - AREA:      (width m × height m) × rate per m² × quantity
     * - PERIMETER: 2 × (width m + height m) × rate per linear metre × quantity
     * - PER_ITEM:  quantity × rate per piece
     *
     * Centimetres are converted to metres exactly before any multiplication; the product is exact
     * and rounded once (HALF_UP, to centimes) at the very end.
     * Per-piece measurements are informational; fixed charges have no quantity or measurements.
     */
    fun price(
        formula: PricingFormula,
        width: Length?,
        height: Length?,
        quantity: Int,
        unitRate: Money?,
    ): PricingResult {
        val issues = mutableListOf<FieldIssue>()

        if (formula.measurements.width || width != null) checkDimension(Field.WIDTH, width, issues)
        if (formula.measurements.height || height != null) checkDimension(Field.HEIGHT, height, issues)
        if (!formula.usesQuantity && quantity != 1) issues += FieldIssue(Field.QUANTITY, Problem.MALFORMED)
        if (formula == PricingFormula.FIXED && (width != null || height != null)) {
            issues += FieldIssue(if (width != null) Field.WIDTH else Field.HEIGHT, Problem.MALFORMED)
        }
        when {
            quantity < 1 -> issues += FieldIssue(Field.QUANTITY, Problem.NOT_POSITIVE)
            quantity > PricingLimits.MAX_QUANTITY -> issues += FieldIssue(Field.QUANTITY, Problem.TOO_LARGE)
        }
        when {
            unitRate == null -> issues += FieldIssue(Field.RATE, Problem.REQUIRED)
            unitRate.isNegative -> issues += FieldIssue(Field.RATE, Problem.NEGATIVE)
            unitRate.isZero -> issues += FieldIssue(Field.RATE, Problem.NOT_POSITIVE)
            unitRate > PricingLimits.MAX_RATE -> issues += FieldIssue(Field.RATE, Problem.TOO_LARGE)
        }
        if (issues.isNotEmpty() || unitRate == null) return PricingResult.Failure(issues)

        val rate = unitRate.toBigDecimal()
        val quantityDecimal = BigDecimal(quantity)

        val base = formula.base(width, height)
        val exactTotal = (base ?: BigDecimal.ONE).multiply(rate).multiply(quantityDecimal)

        return PricingResult.Success(
            ItemPricing(
                formula = formula,
                width = width,
                height = height,
                quantity = quantity,
                unitRate = unitRate,
                baseAmount = base?.canonical(),
                total = Money.fromExact(exactTotal),
            ),
        )
    }

    /** Recomputes the total of a stored snapshot, to detect corrupted or tampered data. */
    fun recompute(snapshot: ItemPricing): PricingResult =
        price(snapshot.formula, snapshot.width, snapshot.height, snapshot.quantity, snapshot.unitRate)

    private fun checkDimension(field: Field, length: Length?, issues: MutableList<FieldIssue>) {
        when {
            length == null -> issues += FieldIssue(field, Problem.REQUIRED)
            length.thousandths < 0 -> issues += FieldIssue(field, Problem.NEGATIVE)
            length.thousandths == 0L -> issues += FieldIssue(field, Problem.NOT_POSITIVE)
            !length.isAtMost(PricingLimits.MAX_DIMENSION_MICROMETERS) ->
                issues += FieldIssue(field, Problem.TOO_LARGE)
        }
    }
}
