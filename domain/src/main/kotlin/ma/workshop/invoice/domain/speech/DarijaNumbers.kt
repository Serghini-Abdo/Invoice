package ma.workshop.invoice.domain.speech

import ma.workshop.invoice.domain.util.canonical
import java.math.BigDecimal

/**
 * Numbers as Moroccan Darija words, generated offline, so speech never depends on how a voice reads
 * digits: 2 520 -> "ألفين و خمس مية و عشرين", 1,20 m -> "متر و عشرين سانتي".
 */
object DarijaNumbers {

    private val units = listOf(
        "", "واحد", "جوج", "تلاتة", "ربعة", "خمسة", "ستة", "سبعة", "تمنية", "تسعة", "عشرة",
        "حضاش", "طناش", "تلطاش", "ربعطاش", "خمسطاش", "سطاش", "سبعطاش", "تمنطاش", "تسعطاش",
    )
    private val tens = listOf("", "", "عشرين", "تلاتين", "ربعين", "خمسين", "ستين", "سبعين", "تمانين", "تسعين")
    private val hundreds = listOf(
        "", "مية", "ميتين", "تلت مية", "ربع مية", "خمس مية", "ست مية", "سبع مية", "تمن مية", "تسع مية",
    )

    /** The short forms used before "آلاف": تلت آلاف, ربع آلاف ... عشر آلاف. */
    private val fewThousands = listOf("", "", "", "تلت", "ربع", "خمس", "ست", "سبع", "تمن", "تسع", "عشر")

    private const val JOIN = " و "

    fun integer(value: Long): String {
        require(value >= 0) { "negative numbers are not spoken" }
        if (value == 0L) return "صفر"
        val billions = value / 1_000_000_000
        val millions = (value / 1_000_000) % 1000
        val thousands = (value / 1000) % 1000
        val rest = (value % 1000).toInt()
        val parts = ArrayList<String>()
        if (billions > 0) parts += scaled(billions, "مليار")
        if (millions > 0) parts += scaled(millions, "مليون")
        if (thousands > 0) parts += thousandsPart(thousands.toInt())
        if (rest > 0) parts += below1000(rest)
        return parts.joinToString(JOIN)
    }

    /** "مليون", "جوج مليون", "تلاتة مليون"; [count] above 999 is read as a plain number. */
    private fun scaled(count: Long, word: String): String = when (count) {
        1L -> word
        else -> "${integer(count)} $word"
    }

    private fun thousandsPart(count: Int): String = when (count) {
        1 -> "ألف"
        2 -> "ألفين"
        in 3..10 -> "${fewThousands[count]} آلاف"
        else -> "${below1000(count)} ألف"
    }

    private fun below1000(value: Int): String {
        val parts = ArrayList<String>()
        val hundred = value / 100
        val rest = value % 100
        if (hundred > 0) parts += hundreds[hundred]
        if (rest > 0) parts += below100(rest)
        return parts.joinToString(JOIN)
    }

    private fun below100(value: Int): String {
        if (value < 20) return units[value]
        val unit = value % 10
        val ten = tens[value / 10]
        if (unit == 0) return ten
        // Inside a compound number two is "تنين": "تنين و عشرين".
        val unitWord = if (unit == 2) "تنين" else units[unit]
        return "$unitWord$JOIN$ten"
    }

    /**
     * A decimal such as an area: "2,52" -> "جوج فاصلة تنين و خمسين", "2,05" -> "جوج فاصلة صفر خمسة".
     * Leading zeros of the fraction are read one by one so the value is never misheard.
     */
    fun decimal(value: BigDecimal): String {
        require(value.signum() >= 0) { "negative numbers are not spoken" }
        val plain = value.canonical().toPlainString()
        val dot = plain.indexOf('.')
        if (dot < 0) return integer(plain.toLong())
        val whole = integer(plain.substring(0, dot).toLong())
        val fraction = plain.substring(dot + 1)
        val zeros = fraction.takeWhile { it == '0' }.length
        val digits = fraction.substring(zeros)
        val spoken = (List(zeros) { "صفر" } + integer(digits.toLong())).joinToString(" ")
        return "$whole ${DarijaTerms.POINT} $spoken"
    }

    /**
     * A length in metres, the way it is said in the workshop: "متر و عشرين سانتي", "جوج متر",
     * "تمانين سانتي". Anything finer than a centimetre falls back to "واحد فاصلة ... متر".
     */
    fun meters(value: BigDecimal): String {
        val exact = value.canonical()
        if (exact.scale() > 2) return "${decimal(exact)} ${DarijaTerms.METER}"
        val whole = exact.toBigInteger().toLong()
        val centimeters = exact.subtract(BigDecimal(whole)).movePointRight(2).toInt()
        val meterPart = when (whole) {
            0L -> null
            1L -> DarijaTerms.METER
            else -> "${integer(whole)} ${DarijaTerms.METER}"
        }
        val centimeterPart = if (centimeters > 0) "${integer(centimeters.toLong())} ${DarijaTerms.CENTIMETER}" else null
        return when {
            meterPart == null && centimeterPart == null -> "صفر ${DarijaTerms.METER}"
            meterPart == null -> centimeterPart!!
            centimeterPart == null -> meterPart
            else -> "$meterPart$JOIN$centimeterPart"
        }
    }

    /** A length typed in centimetres: "مية و عشرين سانتي", "مية و عشرين فاصلة خمسة سانتي". */
    fun centimeters(value: BigDecimal): String = "${decimal(value)} ${DarijaTerms.CENTIMETER}"
}
