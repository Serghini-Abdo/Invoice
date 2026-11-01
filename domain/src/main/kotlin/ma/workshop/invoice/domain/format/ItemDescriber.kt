package ma.workshop.invoice.domain.format

import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.settings.UnitLabelScript
import ma.workshop.invoice.domain.pricing.LengthUnit
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
    fun measures(pricing: ItemPricing, script: UnitLabelScript = UnitLabelScript.ARABIC): String? {
        val width = pricing.width
        val height = pricing.height
        if (width == null || height == null) {
            val dimension = width ?: height ?: return null
            val label = if (width != null) ArabicTerms.WIDTH else ArabicTerms.HEIGHT
            return "$label ${ArabicFormat.isolate("${ArabicFormat.length(dimension)} ${ArabicFormat.unit(dimension.unit, script)}", script)}"
        }
        val text = if (width.unit == height.unit) {
            "${ArabicFormat.length(width)} × ${ArabicFormat.length(height)} ${ArabicFormat.unit(width.unit, script)}"
        } else {
            "${ArabicFormat.length(width)} ${ArabicFormat.unit(width.unit, script)} × " +
                "${ArabicFormat.length(height)} ${ArabicFormat.unit(height.unit, script)}"
        }
        return ArabicFormat.isolate(text, script)
    }

    /** "500 DH / م²" */
    fun rate(pricing: ItemPricing, script: UnitLabelScript = UnitLabelScript.ARABIC): String {
        if (!pricing.formula.usesQuantity) return ArabicFormat.amount(pricing.unitRate)
        val text = "${ArabicFormat.amount(pricing.unitRate)} / ${rateUnit(pricing.rateBasis, script)}"
        return if (pricing.rateBasis == RateBasis.PER_PIECE) text else ArabicFormat.isolate(text, script)
    }

    fun rateUnit(basis: RateBasis, script: UnitLabelScript = UnitLabelScript.ARABIC): String = when (basis) {
        RateBasis.PER_SQUARE_METER -> ArabicFormat.squareMeter(script)
        RateBasis.PER_LINEAR_METER -> ArabicFormat.unit(LengthUnit.METER, script)
        RateBasis.PER_PIECE -> ArabicTerms.PIECE
        RateBasis.FIXED -> ""
    }

    /** "2,52 م²" or "7 م"; null for per-piece items. */
    fun baseAmount(pricing: ItemPricing, script: UnitLabelScript = UnitLabelScript.ARABIC): String? {
        val base = pricing.baseAmount ?: return null
        val unit = if (pricing.formula == PricingFormula.AREA) ArabicFormat.squareMeter(script) else ArabicFormat.unit(LengthUnit.METER, script)
        return "${ArabicFormat.decimal(base, minDecimals = if (pricing.formula == PricingFormula.AREA) 2 else 0)} $unit"
    }

    /**
     * One compact line: "2,52 م² × 500 DH × 2" or "4 × 30 DH".
     *
     * An invisible right-to-left mark follows the rate. Without it the Latin "DH" makes the bidi algorithm
     * treat the quantity after it as part of the same left-to-right run, and a rate of 401 with a quantity
     * of 1 would be displayed as "1 401", which reads like one thousand four hundred and one.
     */
    fun calculation(pricing: ItemPricing, script: UnitLabelScript = UnitLabelScript.ARABIC): String {
        val rate = ArabicFormat.amount(pricing.unitRate)
        if (!pricing.formula.usesQuantity) return rate
        return when (pricing.baseAmount) {
            null -> "${pricing.quantity} × $rate"
            else -> ArabicFormat.isolate("${baseAmount(pricing, script)} × $rate", script).let {
                if (script == UnitLabelScript.LATIN) "$it × ${pricing.quantity}" else "${baseAmount(pricing, script)} × $rate${ArabicFormat.RLM} × ${pricing.quantity}"
            }
        }
    }

    /**
     * The calculation cell of the invoice table: the unit price sits inside it and the quantity does not
     * (it has its own column). "2,52 م² × 500 DH" or, for a per-piece product, "35 DH / قطعة".
     */
    fun formula(pricing: ItemPricing, script: UnitLabelScript = UnitLabelScript.ARABIC): String = when (pricing.baseAmount) {
        null -> rate(pricing, script)
        else -> ArabicFormat.isolate("${baseAmount(pricing, script)} × ${ArabicFormat.amount(pricing.unitRate)}", script)
    }

    /** The full step-by-step calculation for the confirmation screen. */
    fun breakdown(pricing: ItemPricing, script: UnitLabelScript = UnitLabelScript.ARABIC): List<BreakdownLine> {
        val lines = mutableListOf<BreakdownLine>()
        val width = pricing.width
        val height = pricing.height
        if (width != null) {
            lines += BreakdownLine(ArabicTerms.WIDTH, ArabicFormat.isolate("${ArabicFormat.length(width)} ${ArabicFormat.unit(width.unit, script)}", script))
        }
        if (height != null) {
            lines += BreakdownLine(ArabicTerms.HEIGHT, ArabicFormat.isolate("${ArabicFormat.length(height)} ${ArabicFormat.unit(height.unit, script)}", script))
        }
        if (width != null && height != null && pricing.baseAmount != null) {
            val wMeters = ArabicFormat.decimal(width.meters, minDecimals = 2, maxDecimals = 6)
            val hMeters = ArabicFormat.decimal(height.meters, minDecimals = 2, maxDecimals = 6)
            val formulaLine = mapOf(
                PricingFormula.AREA to "$wMeters × $hMeters = ${baseAmount(pricing, script)}",
                PricingFormula.PERIMETER to "${ArabicFormat.decimal(TWO)} × ($wMeters + $hMeters) = ${baseAmount(pricing, script)}",
            )[pricing.formula]
            val label = if (pricing.formula == PricingFormula.AREA) ArabicTerms.AREA else ArabicTerms.PERIMETER
            if (formulaLine != null) lines += BreakdownLine(label, formulaLine)
        }
        lines += BreakdownLine(ArabicTerms.RATE, rate(pricing, script))
        if (pricing.formula.usesQuantity) lines += BreakdownLine(ArabicTerms.QUANTITY, pricing.quantity.toString())
        lines += BreakdownLine(ArabicTerms.TOTAL, "${calculation(pricing, script)} = ${ArabicFormat.amount(pricing.total)}")
        return lines
    }
}
