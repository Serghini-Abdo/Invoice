package ma.workshop.invoice.ui

import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.RateBasis
import ma.workshop.invoice.domain.settings.UnitLabelScript
import ma.workshop.invoice.domain.pricing.Problem

/** Arabic wording for validation problems (shown next to the field, with a warning icon). */
fun Problem.arabic(): String = when (this) {
    Problem.REQUIRED -> "مطلوب"
    Problem.MALFORMED -> "رقم غير صحيح"
    Problem.NOT_POSITIVE -> "يجب أن يكون أكبر من صفر"
    Problem.NEGATIVE -> "لا يمكن أن يكون سالباً"
    Problem.TOO_LARGE -> "الرقم كبير جداً"
    Problem.TOO_MANY_DECIMALS -> "أرقام كثيرة بعد الفاصلة"
    Problem.EXCEEDS_TOTAL -> "أكبر من مجموع الفاتورة"
}

private val methodNames = mapOf(
    PricingFormula.AREA to Strings.FORMULA_AREA,
    PricingFormula.PERIMETER to Strings.FORMULA_PERIMETER,
    PricingFormula.PER_ITEM to Strings.FORMULA_PIECE,
    PricingFormula.WIDTH to Strings.FORMULA_WIDTH,
    PricingFormula.HEIGHT to Strings.FORMULA_HEIGHT,
    PricingFormula.FIXED to Strings.FORMULA_FIXED,
)
private val methodHints = mapOf(
    PricingFormula.AREA to Strings.FORMULA_AREA_HINT,
    PricingFormula.PERIMETER to Strings.FORMULA_PERIMETER_HINT,
    PricingFormula.PER_ITEM to Strings.FORMULA_PIECE_HINT,
    PricingFormula.WIDTH to Strings.FORMULA_WIDTH_HINT,
    PricingFormula.HEIGHT to Strings.FORMULA_HEIGHT_HINT,
    PricingFormula.FIXED to Strings.FORMULA_FIXED_HINT,
)
fun PricingFormula.rateLabel(): String = when (rateBasis) {
    RateBasis.PER_SQUARE_METER -> Strings.RATE_AREA
    RateBasis.PER_LINEAR_METER -> Strings.RATE_PERIMETER
    RateBasis.PER_PIECE -> Strings.RATE_PIECE
    RateBasis.FIXED -> Strings.CHARGE_AMOUNT
}
fun PricingFormula.rateUnit(script: UnitLabelScript = UnitLabelScript.ARABIC): String = when (rateBasis) {
    RateBasis.PER_SQUARE_METER -> "DH / ${ArabicFormat.squareMeter(script)}"
    RateBasis.PER_LINEAR_METER -> "DH / ${ArabicFormat.unit(LengthUnit.METER, script)}"
    RateBasis.PER_PIECE -> Strings.RATE_PIECE_UNIT
    RateBasis.FIXED -> "DH"
}
fun PricingFormula.methodName(): String = methodNames.getValue(this)
fun PricingFormula.methodHint(): String = methodHints.getValue(this)
