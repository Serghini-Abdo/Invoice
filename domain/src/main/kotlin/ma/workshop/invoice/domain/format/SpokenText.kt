package ma.workshop.invoice.domain.format

import ma.workshop.invoice.domain.invoice.PaymentSummary
import ma.workshop.invoice.domain.money.Money

/**
 * Arabic number words, so speech does not depend on how an engine happens to read digits.
 * Standard Arabic, nominative; precise enough to be understood clearly on any Arabic voice.
 */
object ArabicNumberWords {

    private val units = listOf(
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة",
        "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر",
    )
    private val tens = listOf("", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = listOf(
        "", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة",
    )

    private class Scale(val one: String, val two: String, val few: String, val many: String)

    private val scales = listOf(
        Scale("ألف", "ألفان", "آلاف", "ألف"),
        Scale("مليون", "مليونان", "ملايين", "مليون"),
        Scale("مليار", "ملياران", "مليارات", "مليار"),
    )

    fun integer(value: Long): String {
        require(value >= 0) { "negative numbers are not spoken" }
        if (value == 0L) return "صفر"

        val groups = ArrayList<Long>()
        var rest = value
        while (rest > 0) {
            groups += rest % 1000
            rest /= 1000
        }
        // The largest scale we name is billions; anything above is spoken as a multiple of billions.
        val parts = ArrayList<String>()
        for (index in groups.indices.reversed()) {
            val group = groups[index]
            if (group == 0L) continue
            parts += when {
                index == 0 -> below1000(group.toInt())
                else -> scaled(group.toInt(), scales[minOf(index, scales.size) - 1])
            }
        }
        return parts.joinToString(" و")
    }

    private fun scaled(group: Int, scale: Scale): String = when {
        group == 1 -> scale.one
        group == 2 -> scale.two
        group in 3..10 -> "${below1000(group)} ${scale.few}"
        else -> "${below1000(group)} ${scale.many}"
    }

    private fun below1000(value: Int): String {
        val parts = ArrayList<String>()
        val hundred = value / 100
        val rest = value % 100
        if (hundred > 0) parts += hundreds[hundred]
        if (rest > 0) {
            if (rest < 20) {
                parts += units[rest]
            } else {
                val unit = rest % 10
                if (unit > 0) parts += units[unit]
                parts += tens[rest / 10]
            }
        }
        return parts.joinToString(" و")
    }
}

/** The sentences the app can speak. Voice is an aid only: every figure is always on screen too. */
object SpokenText {

    /** "ألف ومائتان وستون درهم" / "درهم واحد" / "درهمان" / "خمسة دراهم وخمسون سنتيم" */
    fun amount(money: Money): String {
        val absolute = Math.abs(money.centimes)
        val dirhams = absolute / 100
        val centimes = (absolute % 100).toInt()

        val dirhamPart = when {
            dirhams == 0L && centimes > 0 -> ""
            dirhams == 1L -> "${ArabicTerms.DIRHAM} واحد"
            dirhams == 2L -> "درهمان"
            dirhams in 3..10 -> "${ArabicNumberWords.integer(dirhams)} دراهم"
            else -> "${ArabicNumberWords.integer(dirhams)} ${ArabicTerms.DIRHAM}"
        }
        if (centimes == 0) return dirhamPart
        val centimePart = "${ArabicNumberWords.integer(centimes.toLong())} ${ArabicTerms.CENTIME}"
        return if (dirhamPart.isEmpty()) centimePart else "$dirhamPart و$centimePart"
    }

    fun itemTotal(productName: String, total: Money): String =
        "$productName، ${ArabicTerms.TOTAL} ${amount(total)}"

    fun invoiceSummary(payment: PaymentSummary): String = buildString {
        append("${ArabicTerms.TOTAL} ${amount(payment.total)}")
        append("، ${ArabicTerms.DEPOSIT} ${if (payment.deposit.isZero) "لا شيء" else amount(payment.deposit)}")
        if (payment.isFullyPaid) {
            append("، الفاتورة ${ArabicTerms.FULLY_PAID}")
        } else {
            append("، ${ArabicTerms.REMAINING} ${amount(payment.remaining)}")
        }
    }
}
