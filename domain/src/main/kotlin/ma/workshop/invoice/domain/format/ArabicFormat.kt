package ma.workshop.invoice.domain.format

import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.Length
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.util.canonical
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Number, money and date formatting for the Arabic UI and invoices.
 *
 * Morocco writes Western digits (0-9) with a comma decimal separator and a space between thousands
 * ("1 260,50 DH"). The grouping space is a no-break space (U+00A0), whose bidi class is "common
 * separator": between two digits it stays inside the number, so a figure like "1 260" is never split
 * or reordered when it sits inside right-to-left text.
 */
object ArabicFormat {

    const val NBSP = ' '
    const val CURRENCY = "DH"

    /** Invisible right-to-left mark (U+200F); see [ItemDescriber.calculation] for why it is needed. */
    const val RLM = '‏'

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** "1 260,5" style number: [minDecimals]..[maxDecimals] decimals, rounded half-up for display only. */
    fun decimal(value: BigDecimal, minDecimals: Int = 0, maxDecimals: Int = 4): String {
        val negative = value.signum() < 0
        var shown = value.abs().setScale(maxDecimals, RoundingMode.HALF_UP).canonical()
        if (shown.scale() < minDecimals) shown = shown.setScale(minDecimals)
        val plain = shown.toPlainString()
        val dot = plain.indexOf('.')
        val integerPart = if (dot < 0) plain else plain.substring(0, dot)
        val fraction = if (dot < 0) "" else plain.substring(dot + 1)
        return buildString {
            if (negative) append('-')
            append(group(integerPart))
            if (fraction.isNotEmpty()) append(',').append(fraction)
        }
    }

    /** "1 260 DH" or "1 260,50 DH": centimes are shown only when non-zero. */
    fun amount(money: Money): String = "${amountNumber(money)}$NBSP$CURRENCY"

    /** The number part of [amount], for layouts that print the currency separately. */
    fun amountNumber(money: Money): String {
        val absolute = Math.abs(money.centimes)
        val dirhams = absolute / 100
        val centimes = (absolute % 100).toInt()
        return buildString {
            if (money.isNegative) append('-')
            append(group(dirhams.toString()))
            if (centimes != 0) append(',').append(centimes.toString().padStart(2, '0'))
        }
    }

    fun date(epochMillis: Long, zone: ZoneId): String =
        dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** The same dd/MM/yyyy with Western digits, for a calendar date picked by the user. */
    fun date(date: java.time.LocalDate): String = dateFormatter.format(date)

    fun fileDate(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun invoiceNumber(number: Long): String = number.toString().padStart(4, '0')

    /** Metres always show two decimals ("1,20"); centimetres only the decimals that matter ("120", "120,5"). */
    fun length(length: Length): String = when (length.unit) {
        LengthUnit.METER -> decimal(length.value, minDecimals = 2, maxDecimals = 3)
        LengthUnit.CENTIMETER -> decimal(length.value, minDecimals = 0, maxDecimals = 3)
    }

    fun unit(unit: LengthUnit): String = when (unit) {
        LengthUnit.METER -> ArabicTerms.METER
        LengthUnit.CENTIMETER -> ArabicTerms.CENTIMETER
    }

    private fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val out = StringBuilder()
        val first = digits.length % 3
        if (first > 0) out.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (out.isNotEmpty()) out.append(NBSP)
            out.append(digits, i, i + 3)
            i += 3
        }
        return out.toString()
    }
}

/** Fixed Arabic vocabulary used on invoices and in spoken text. (App screens use string resources.) */
object ArabicTerms {
    const val INVOICE = "فاتورة"
    const val NUMBER = "رقم"
    const val DATE = "التاريخ"
    const val CUSTOMER = "الزبون"
    const val PHONE = "الهاتف"
    const val PRODUCT = "المنتوج"
    const val QUANTITY = "الكمية"
    const val RATE = "الثمن"
    const val TOTAL = "المجموع"
    const val DEPOSIT = "التسبيق"
    const val REMAINING = "الباقي"
    const val FULLY_PAID = "خالص"
    const val UPDATED = "فاتورة معدلة"
    const val WIDTH = "العرض"
    const val HEIGHT = "الارتفاع"
    const val AREA = "المساحة"
    const val PERIMETER = "المحيط"
    const val PIECE = "قطعة"
    const val METER = "م"
    const val CENTIMETER = "سم"
    const val SQUARE_METER = "م²"
    const val DIRHAM = "درهم"
    const val CENTIME = "سنتيم"
}
