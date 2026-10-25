package ma.workshop.invoice.domain.pricing

import ma.workshop.invoice.domain.input.DecimalParser
import ma.workshop.invoice.domain.input.MoneyParse
import ma.workshop.invoice.domain.input.MoneyParser
import ma.workshop.invoice.domain.input.ParsedNumber
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.util.canonical

data class FormEvaluation(
    val issues: List<FieldIssue>,
    /** Present only when the form is complete and valid; this is what the live total shows. */
    val pricing: ItemPricing?,
) {
    val isValid: Boolean get() = pricing != null

    fun issueFor(field: Field): Problem? = issues.firstOrNull { it.field == field }?.problem
}

/**
 * The raw, as-typed state of the "item details" screen. Dimensions and the unit rate are text so
 * that half-typed input can be represented; [evaluate] turns it into a priced snapshot or a list
 * of precise field issues. Quantity always starts at 1.
 */
data class ItemEntryForm(
    val formula: PricingFormula,
    val widthText: String = "",
    val widthUnit: LengthUnit = LengthUnit.METER,
    val heightText: String = "",
    val heightUnit: LengthUnit = LengthUnit.METER,
    val quantity: Int = DEFAULT_QUANTITY,
    val rateText: String = "",
) {

    fun evaluate(): FormEvaluation {
        val issues = mutableListOf<FieldIssue>()

        var width: Length? = null
        var height: Length? = null
        if (formula.needsDimensions) {
            width = parseLength(Field.WIDTH, widthText, widthUnit, issues)
            height = parseLength(Field.HEIGHT, heightText, heightUnit, issues)
        }

        var rate: Money? = null
        when (val parsed = MoneyParser.parse(rateText)) {
            MoneyParse.Empty -> issues += FieldIssue(Field.RATE, Problem.REQUIRED)
            is MoneyParse.Invalid -> issues += FieldIssue(Field.RATE, parsed.problem)
            is MoneyParse.Value -> rate = parsed.money
        }

        // Only run the engine on complete input, so every issue is reported once, precisely.
        if (issues.isNotEmpty()) return FormEvaluation(issues, null)

        return when (val result = PricingEngine.price(formula, width, height, quantity, rate)) {
            is PricingResult.Success -> FormEvaluation(emptyList(), result.pricing)
            is PricingResult.Failure -> FormEvaluation(result.issues, null)
        }
    }

    private fun parseLength(
        field: Field,
        text: String,
        unit: LengthUnit,
        issues: MutableList<FieldIssue>,
    ): Length? = when (val parsed = DecimalParser.parse(text, PricingLimits.MAX_DIMENSION_DECIMALS)) {
        ParsedNumber.Empty -> null.also { issues += FieldIssue(field, Problem.REQUIRED) }
        ParsedNumber.Malformed -> null.also { issues += FieldIssue(field, Problem.MALFORMED) }
        ParsedNumber.Negative -> null.also { issues += FieldIssue(field, Problem.NEGATIVE) }
        ParsedNumber.TooManyDecimals -> null.also { issues += FieldIssue(field, Problem.TOO_MANY_DECIMALS) }
        is ParsedNumber.Value -> Length.ofDecimal(parsed.value, unit)
            ?: null.also { issues += FieldIssue(field, Problem.TOO_MANY_DECIMALS) }
    }

    companion object {
        const val DEFAULT_QUANTITY = 1

        /** A blank form for a freshly selected catalog product: quantity 1, no price, no dimensions. */
        fun forProduct(formula: PricingFormula, defaultUnit: LengthUnit): ItemEntryForm =
            ItemEntryForm(formula = formula, widthUnit = defaultUnit, heightUnit = defaultUnit)

        /** Re-opens a confirmed item for editing, in the units the user originally typed. */
        fun fromPricing(pricing: ItemPricing): ItemEntryForm = ItemEntryForm(
            formula = pricing.formula,
            widthText = pricing.width?.let { editable(it) }.orEmpty(),
            widthUnit = pricing.width?.unit ?: LengthUnit.METER,
            heightText = pricing.height?.let { editable(it) }.orEmpty(),
            heightUnit = pricing.height?.unit ?: LengthUnit.METER,
            quantity = pricing.quantity,
            rateText = MoneyParser.toEditableText(pricing.unitRate),
        )

        private fun editable(length: Length): String = length.value.canonical().toPlainString()
    }
}
