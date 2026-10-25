package ma.workshop.invoice.domain.format

import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.RateBasis
import java.math.BigDecimal

data class BreakdownLine(val label: String, val value: String)

/**
 * Turns a priced item into the Arabic text shown on the item list, the confirmation screen and the
 * invoice. All numbers displayed here are derived from the stored snapshot; nothing is recomputed
 * with different rules.
 */
object ItemDescriber {

    private val TWO = BigDecimal(2)

    /** "1,20 × 2,10 م" (or with a unit after each number when units differ). Null for per-piece items. */
    fun measures(pricing: ItemPricing): String? {
        val width = pricing.width ?: return null
        val height = pricing.height ?: return null
        return if (width.unit == height.unit) {
            "${ArabicFormat.length(width)} × ${ArabicFormat.length(height)} ${ArabicFormat.unit(width.unit)}"
        } else {
            "${ArabicFormat.length(width)} ${ArabicFormat.unit(width.unit)} × " +
                "${ArabicFormat.length(height)} ${ArabicFormat.unit(height.unit)}"
        }
    }

    /** "500 DH / م²" */
    fun rate(pricing: ItemPricing): String =
        "${ArabicFormat.amount(pricing.unitRate)} / ${rateUnit(pricing.rateBasis)}"

    fun rateUnit(basis: RateBasis): String = when (basis) {
        RateBasis.PER_SQUARE_METER -> ArabicTerms.SQUARE_METER
        RateBasis.PER_LINEAR_METER -> ArabicTerms.METER
        RateBasis.PER_PIECE -> ArabicTerms.PIECE
    }

    /** "2,52 م²" or "7 م"; null for per-piece items. */
    fun baseAmount(pricing: ItemPricing): String? {
        val base = pricing.baseAmount ?: return null
        val unit = if (pricing.formula == PricingFormula.AREA) ArabicTerms.SQUARE_METER else ArabicTerms.METER
        return "${ArabicFormat.decimal(base, minDecimals = if (pricing.formula == PricingFormula.AREA) 2 else 0)} $unit"
    }

    /** One compact line: "2,52 م² × 500 DH × 2" or "4 × 30 DH". */
    fun calculation(pricing: ItemPricing): String {
        val rate = ArabicFormat.amount(pricing.unitRate)
        return when (pricing.formula) {
            PricingFormula.PER_ITEM -> "${pricing.quantity} × $rate"
            else -> "${baseAmount(pricing)} × $rate × ${pricing.quantity}"
        }
    }

    /** The full step-by-step calculation for the confirmation screen. */
    fun breakdown(pricing: ItemPricing): List<BreakdownLine> {
        val lines = mutableListOf<BreakdownLine>()
        val width = pricing.width
        val height = pricing.height
        if (width != null && height != null) {
            lines += BreakdownLine(ArabicTerms.WIDTH, "${ArabicFormat.length(width)} ${ArabicFormat.unit(width.unit)}")
            lines += BreakdownLine(ArabicTerms.HEIGHT, "${ArabicFormat.length(height)} ${ArabicFormat.unit(height.unit)}")

            val wMeters = ArabicFormat.decimal(width.meters, minDecimals = 2, maxDecimals = 6)
            val hMeters = ArabicFormat.decimal(height.meters, minDecimals = 2, maxDecimals = 6)
            val formulaLine = when (pricing.formula) {
                PricingFormula.AREA -> "$wMeters × $hMeters = ${baseAmount(pricing)}"
                PricingFormula.PERIMETER ->
                    "${ArabicFormat.decimal(TWO)} × ($wMeters + $hMeters) = ${baseAmount(pricing)}"
                PricingFormula.PER_ITEM -> ""
            }
            val label = if (pricing.formula == PricingFormula.AREA) ArabicTerms.AREA else ArabicTerms.PERIMETER
            lines += BreakdownLine(label, formulaLine)
        }
        lines += BreakdownLine(ArabicTerms.RATE, rate(pricing))
        lines += BreakdownLine(ArabicTerms.QUANTITY, pricing.quantity.toString())
        lines += BreakdownLine(ArabicTerms.TOTAL, "${calculation(pricing)} = ${ArabicFormat.amount(pricing.total)}")
        return lines
    }
}
