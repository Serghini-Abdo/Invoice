package ma.workshop.invoice.domain.speech

import ma.workshop.invoice.domain.invoice.PaymentSummary
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.RateBasis
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The sentences the app speaks, in Darija. Related fields are joined into one natural sentence
 * ("باب حديد، متر و عشرين سانتي على جوج متر و عشرة سانتي، العدد جوج، المجموع ...") so one speaker can
 * read a whole row or card. Voice is only an aid: every figure is always on screen too.
 */
object DarijaSpeech {

    private const val SEP = "، "

    /**
     * Spoken name of each calculation method. The one place to extend when a method is added, so the
     * sentences below never branch on the formula themselves.
     */
    private val methodNames = mapOf(
        PricingFormula.AREA to DarijaTerms.METHOD_AREA,
        PricingFormula.PERIMETER to DarijaTerms.METHOD_PERIMETER,
        PricingFormula.PER_ITEM to DarijaTerms.METHOD_PIECE,
    )

    private val rateBases = mapOf(
        RateBasis.PER_SQUARE_METER to DarijaTerms.PER_SQUARE_METER,
        RateBasis.PER_LINEAR_METER to DarijaTerms.PER_METER,
        RateBasis.PER_PIECE to DarijaTerms.PER_PIECE,
    )

    /** Short counting forms used before a plural noun: "تلت دراهم", "خمس دراهم", "عشر دراهم". */
    private val fewCount = listOf("", "", "", "تلت", "ربع", "خمس", "ست", "سبع", "تمن", "تسع", "عشر")

    /** "ألفين و خمس مية و عشرين درهم", "درهم واحد", "جوج دراهم", "خمس دراهم و خمسين سنتيم". */
    fun amount(money: Money): String {
        val absolute = Math.abs(money.centimes)
        val dirhams = absolute / 100
        val centimes = (absolute % 100).toInt()
        val dirhamPart = when {
            dirhams == 0L && centimes > 0 -> null
            dirhams == 1L -> "${DarijaTerms.DIRHAM} واحد"
            dirhams == 2L -> DarijaTerms.TWO_DIRHAMS
            dirhams in 3..10 -> "${fewCount[dirhams.toInt()]} ${DarijaTerms.DIRHAMS}"
            else -> "${DarijaNumbers.integer(dirhams)} ${DarijaTerms.DIRHAM}"
        }
        if (centimes == 0) return dirhamPart ?: "صفر ${DarijaTerms.DIRHAM}"
        val centimePart = "${DarijaNumbers.integer(centimes.toLong())} ${DarijaTerms.CENTIME}"
        return if (dirhamPart == null) centimePart else "$dirhamPart ${DarijaTerms.AND} $centimePart"
    }

    fun quantity(quantity: Int): String = DarijaNumbers.integer(quantity.toLong())

    /** A measurement in the unit it was typed in. */
    fun length(length: Length): String = when (length.unit) {
        LengthUnit.METER -> DarijaNumbers.meters(length.value)
        LengthUnit.CENTIMETER -> DarijaNumbers.centimeters(length.value)
    }

    /** "خمس مية درهم للمتر مربع". */
    fun rate(pricing: ItemPricing): String = "${amount(pricing.unitRate)} ${rateBases.getValue(pricing.rateBasis)}"

    /**
     * The area or perimeter of one piece; null for per-piece items. It is spoken with the same four
     * decimals the screen shows (see `ItemDescriber.baseAmount`), never with more digits than are visible.
     */
    private fun base(pricing: ItemPricing): String? {
        val base = pricing.baseAmount?.setScale(4, RoundingMode.HALF_UP) ?: return null
        return when (pricing.rateBasis) {
            RateBasis.PER_SQUARE_METER -> "${DarijaTerms.AREA} ${DarijaNumbers.decimal(base)} ${DarijaTerms.METER} ${DarijaTerms.SQUARE}"
            else -> "${DarijaTerms.PERIMETER} ${DarijaNumbers.meters(base)}"
        }
    }

    private fun dimensions(pricing: ItemPricing): String? {
        val width = pricing.width ?: return null
        val height = pricing.height ?: return null
        return "${length(width)} ${DarijaTerms.BY} ${length(height)}"
    }

    /** One invoice row: name, size, quantity and line total. */
    fun itemLine(productName: String, pricing: ItemPricing): String = listOfNotNull(
        productName,
        dimensions(pricing),
        "${DarijaTerms.QUANTITY} ${quantity(pricing.quantity)}",
        "${DarijaTerms.TOTAL} ${amount(pricing.total)}",
    ).joinToString(SEP)

    /** Spoken on the confirmation screen: "باب حديد، المجموع ...". */
    fun itemTotal(productName: String, total: Money): String = "$productName$SEP${DarijaTerms.TOTAL} ${amount(total)}"

    /** The whole calculation card as one sentence, method first. */
    fun calculation(pricing: ItemPricing): String {
        val parts = ArrayList<String>()
        parts += "${DarijaTerms.CALCULATION} ${methodNames.getValue(pricing.formula)}"
        pricing.width?.let { parts += "${DarijaTerms.WIDTH} ${length(it)}" }
        pricing.height?.let { parts += "${DarijaTerms.HEIGHT} ${length(it)}" }
        base(pricing)?.let { parts += it }
        parts += "${DarijaTerms.PRICE} ${rate(pricing)}"
        parts += "${DarijaTerms.QUANTITY} ${quantity(pricing.quantity)}"
        parts += "${DarijaTerms.TOTAL} ${amount(pricing.total)}"
        return parts.joinToString(SEP)
    }

    /** Name of a calculation method, for the options in product edit. */
    fun method(formula: PricingFormula): String = methodNames.getValue(formula)

    /** The total + deposit + balance card as one sentence. */
    fun paymentSummary(payment: PaymentSummary): String = buildString {
        append("${DarijaTerms.INVOICE_TOTAL} ${amount(payment.total)}")
        append(SEP)
        append(if (payment.deposit.isZero) DarijaTerms.NO_DEPOSIT else "${DarijaTerms.DEPOSIT} ${amount(payment.deposit)}")
        append(SEP)
        append(if (payment.isFullyPaid) DarijaTerms.FULLY_PAID else "${DarijaTerms.REMAINING} ${amount(payment.remaining)}")
    }

    /** A decimal read as words, for places that show a bare number. */
    fun number(value: BigDecimal): String = DarijaNumbers.decimal(value)
}
