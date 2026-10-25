package ma.workshop.invoice.ui

import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
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

fun PricingFormula.rateLabel(): String = when (this) {
    PricingFormula.AREA -> Strings.RATE_AREA
    PricingFormula.PERIMETER -> Strings.RATE_PERIMETER
    PricingFormula.PER_ITEM -> Strings.RATE_PIECE
}

fun PricingFormula.rateUnit(script: UnitLabelScript = UnitLabelScript.ARABIC): String = when (this) {
    PricingFormula.AREA -> "DH / ${ArabicFormat.squareMeter(script)}"
    PricingFormula.PERIMETER -> "DH / ${ArabicFormat.unit(LengthUnit.METER, script)}"
    PricingFormula.PER_ITEM -> Strings.RATE_PIECE_UNIT
}

fun PricingFormula.methodName(): String = when (this) {
    PricingFormula.AREA -> Strings.FORMULA_AREA
    PricingFormula.PERIMETER -> Strings.FORMULA_PERIMETER
    PricingFormula.PER_ITEM -> Strings.FORMULA_PIECE
}
