package ma.workshop.invoice.domain.speech

import ma.workshop.invoice.domain.format.ArabicNumberWords
import ma.workshop.invoice.domain.format.ArabicTerms
import ma.workshop.invoice.domain.format.SpokenText
import ma.workshop.invoice.domain.invoice.PaymentSummary
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.ItemPricing
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.pricing.PricingFormula
import ma.workshop.invoice.domain.pricing.RateBasis
import ma.workshop.invoice.domain.util.canonical
import java.math.BigDecimal
import java.math.RoundingMode

/** Spoken words in standard Arabic, for owners who prefer it to Darija. Kept apart from screen text. */
object StandardTerms {
    const val METER = "متر"
    const val TWO_METERS = "متران"
    const val METERS = "أمتار"
    const val CENTIMETER = "سنتيمتر"
    const val TWO_CENTIMETERS = "سنتيمتران"
    const val CENTIMETERS = "سنتيمترات"
    const val SQUARE = "مربع"
    const val POINT = "فاصلة"
    const val BY = "في"
    const val CALCULATION = "الحساب"
    const val PER_SQUARE_METER = "للمتر المربع"
    const val PER_METER = "للمتر"
    const val PER_PIECE = "للقطعة"
    const val METHOD_AREA = "بالمتر المربع"
    const val METHOD_PERIMETER = "بالمتر الطولي"
    const val METHOD_PIECE = "بالقطعة"
    const val VOICE_TEST = "هذا هو صوت التطبيق. ألف ومائتان وستون درهم"
    const val ADD_PRODUCT = "إضافة منتوج"
}

/**
 * The same sentences as [DarijaSpeech], in standard Arabic. Amounts and the totals card reuse the
 * long-standing [SpokenText]; numbers come from [ArabicNumberWords].
 */
object StandardSpeech : SpeechText {

    private const val SEP = "، "
    private const val AND = " و"

    /** The one place to extend when a calculation method is added. */
    private val methodNames = mapOf(
        PricingFormula.AREA to StandardTerms.METHOD_AREA,
        PricingFormula.PERIMETER to StandardTerms.METHOD_PERIMETER,
        PricingFormula.PER_ITEM to StandardTerms.METHOD_PIECE,
    )

    private val rateBases = mapOf(
        RateBasis.PER_SQUARE_METER to StandardTerms.PER_SQUARE_METER,
        RateBasis.PER_LINEAR_METER to StandardTerms.PER_METER,
        RateBasis.PER_PIECE to StandardTerms.PER_PIECE,
    )

    override fun amount(money: Money): String = SpokenText.amount(money)

    override fun quantity(quantity: Int): String = ArabicNumberWords.integer(quantity.toLong())

    override fun length(length: Length): String = when (length.unit) {
        LengthUnit.METER -> meters(length.value)
        LengthUnit.CENTIMETER -> centimeters(length.value)
    }

    override fun rate(pricing: ItemPricing): String = "${amount(pricing.unitRate)} ${rateBases.getValue(pricing.rateBasis)}"

    override fun itemLine(productName: String, pricing: ItemPricing): String = listOfNotNull(
        productName,
        dimensions(pricing),
        "${ArabicTerms.QUANTITY} ${quantity(pricing.quantity)}",
        "${ArabicTerms.TOTAL} ${amount(pricing.total)}",
    ).joinToString(SEP)

    override fun itemTotal(productName: String, total: Money): String = SpokenText.itemTotal(productName, total)

    override fun calculation(pricing: ItemPricing): String {
        val parts = ArrayList<String>()
        parts += "${StandardTerms.CALCULATION} ${methodNames.getValue(pricing.formula)}"
        pricing.width?.let { parts += "${ArabicTerms.WIDTH} ${length(it)}" }
        pricing.height?.let { parts += "${ArabicTerms.HEIGHT} ${length(it)}" }
        base(pricing)?.let { parts += it }
        parts += "${ArabicTerms.RATE} ${rate(pricing)}"
        parts += "${ArabicTerms.QUANTITY} ${quantity(pricing.quantity)}"
        parts += "${ArabicTerms.TOTAL} ${amount(pricing.total)}"
        return parts.joinToString(SEP)
    }

    override fun method(formula: PricingFormula): String = methodNames.getValue(formula)

    override fun paymentSummary(payment: PaymentSummary): String = SpokenText.invoiceSummary(payment)

    override val voiceTest: String = StandardTerms.VOICE_TEST

    override val addProduct: String = StandardTerms.ADD_PRODUCT

    // ---- numbers and measurements ----

    /** "اثنان فاصلة اثنان وخمسون", "اثنان فاصلة صفر خمسة": leading zeros of the fraction are read one by one. */
    fun decimal(value: BigDecimal): String {
        val plain = value.canonical().toPlainString()
        val dot = plain.indexOf('.')
        if (dot < 0) return ArabicNumberWords.integer(plain.toLong())
        val whole = ArabicNumberWords.integer(plain.substring(0, dot).toLong())
        val fraction = plain.substring(dot + 1)
        val zeros = fraction.takeWhile { it == '0' }.length
        val spoken = (List(zeros) { "صفر" } + ArabicNumberWords.integer(fraction.substring(zeros).toLong())).joinToString(" ")
        return "$whole ${StandardTerms.POINT} $spoken"
    }

    /** "متر واحد وعشرون سنتيمتر", "متران", "ثلاثة أمتار"; finer than a centimetre: "... فاصلة ... متر". */
    fun meters(value: BigDecimal): String {
        val exact = value.canonical()
        if (exact.scale() > 2) return "${decimal(exact)} ${StandardTerms.METER}"
        val whole = exact.toBigInteger().toLong()
        val centimeters = exact.subtract(BigDecimal(whole)).movePointRight(2).toLong()
        val parts = listOfNotNull(
            if (whole > 0) counted(whole, StandardTerms.METER, StandardTerms.TWO_METERS, StandardTerms.METERS) else null,
            if (centimeters > 0) counted(centimeters, StandardTerms.CENTIMETER, StandardTerms.TWO_CENTIMETERS, StandardTerms.CENTIMETERS) else null,
        )
        return if (parts.isEmpty()) "صفر ${StandardTerms.METER}" else parts.joinToString(AND)
    }

    /** "مائة وعشرون سنتيمتر", "مائة وعشرون فاصلة خمسة سنتيمتر". */
    fun centimeters(value: BigDecimal): String {
        val exact = value.canonical()
        return if (exact.scale() == 0 && exact.signum() > 0) {
            counted(exact.toLong(), StandardTerms.CENTIMETER, StandardTerms.TWO_CENTIMETERS, StandardTerms.CENTIMETERS)
        } else {
            "${decimal(exact)} ${StandardTerms.CENTIMETER}"
        }
    }

    /** Same counting rule as the spoken amounts: "متر واحد", "متران", "ثلاثة أمتار", "عشرون متر". */
    private fun counted(count: Long, one: String, two: String, few: String): String = when (count) {
        1L -> "$one واحد"
        2L -> two
        in 3..10 -> "${ArabicNumberWords.integer(count)} $few"
        else -> "${ArabicNumberWords.integer(count)} $one"
    }

    /** Spoken with the four decimals the screen shows. */
    private fun base(pricing: ItemPricing): String? {
        val base = pricing.baseAmount?.setScale(4, RoundingMode.HALF_UP) ?: return null
        return when (pricing.rateBasis) {
            RateBasis.PER_SQUARE_METER -> "${ArabicTerms.AREA} ${decimal(base)} ${StandardTerms.METER} ${StandardTerms.SQUARE}"
            else -> "${ArabicTerms.PERIMETER} ${meters(base)}"
        }
    }

    private fun dimensions(pricing: ItemPricing): String? {
        val width = pricing.width ?: return null
        val height = pricing.height ?: return null
        return "${length(width)} ${StandardTerms.BY} ${length(height)}"
    }
}
