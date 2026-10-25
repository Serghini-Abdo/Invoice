package ma.workshop.invoice.domain.pricing

import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.util.canonical
import java.math.BigDecimal

/** The three ways a catalog product can be priced. The price itself is always entered per item. */
enum class PricingFormula(
    val id: String,
    /** True when the formula needs width and height. */
    val needsDimensions: Boolean,
    val rateBasis: RateBasis,
) {
    AREA("area", true, RateBasis.PER_SQUARE_METER),
    PERIMETER("perimeter", true, RateBasis.PER_LINEAR_METER),
    PER_ITEM("per_item", false, RateBasis.PER_PIECE);

    companion object {
        fun fromId(id: String): PricingFormula? = entries.firstOrNull { it.id == id }
    }
}

enum class RateBasis(val id: String) {
    PER_SQUARE_METER("m2"),
    PER_LINEAR_METER("m"),
    PER_PIECE("piece");

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
     * Dimensions are ignored (and dropped from the snapshot) for PER_ITEM.
     */
    fun price(
        formula: PricingFormula,
        width: Length?,
        height: Length?,
        quantity: Int,
        unitRate: Money?,
    ): PricingResult {
        val issues = mutableListOf<FieldIssue>()

        if (formula.needsDimensions) {
            checkDimension(Field.WIDTH, width, issues)
            checkDimension(Field.HEIGHT, height, issues)
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

        val base: BigDecimal?
        val exactTotal: BigDecimal
        when (formula) {
            PricingFormula.AREA -> {
                base = width!!.meters.multiply(height!!.meters)
                exactTotal = base.multiply(rate).multiply(quantityDecimal)
            }
            PricingFormula.PERIMETER -> {
                base = width!!.meters.add(height!!.meters).multiply(BigDecimal(2))
                exactTotal = base.multiply(rate).multiply(quantityDecimal)
            }
            PricingFormula.PER_ITEM -> {
                base = null
                exactTotal = quantityDecimal.multiply(rate)
            }
        }

        return PricingResult.Success(
            ItemPricing(
                formula = formula,
                width = if (formula.needsDimensions) width else null,
                height = if (formula.needsDimensions) height else null,
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
