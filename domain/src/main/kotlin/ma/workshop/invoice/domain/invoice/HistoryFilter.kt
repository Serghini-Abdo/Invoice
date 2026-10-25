package ma.workshop.invoice.domain.invoice

import ma.workshop.invoice.domain.format.ArabicFormat
import java.time.LocalDate
import java.time.ZoneId

/** Search/filter for the invoice history: free text (customer name, phone, number) and a date range. */
data class HistoryQuery(
    val text: String = "",
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
) {
    val isEmpty: Boolean get() = text.isBlank() && fromDate == null && toDate == null
}

object HistoryFilter {

    /** Newest first. Both dates are inclusive and interpreted in [zone]. */
    fun apply(invoices: List<InvoiceSummary>, query: HistoryQuery, zone: ZoneId): List<InvoiceSummary> {
        val needle = normalize(query.text)
        val fromMillis = query.fromDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val toMillisExclusive = query.toDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

        return invoices
            .filter { invoice ->
                (fromMillis == null || invoice.createdAt >= fromMillis) &&
                    (toMillisExclusive == null || invoice.createdAt < toMillisExclusive) &&
                    (needle.isEmpty() || matches(invoice, needle))
            }
            .sortedWith(compareByDescending<InvoiceSummary> { it.createdAt }.thenByDescending { it.number })
    }

    private fun matches(invoice: InvoiceSummary, needle: String): Boolean =
        normalize(invoice.customerName).contains(needle) ||
            normalize(invoice.customerPhone).contains(needle) ||
            ArabicFormat.invoiceNumber(invoice.number).contains(needle)

    /**
     * Makes Arabic search forgiving: removes diacritics and tatweel, unifies alef/yaa/taa-marbuta
     * variants, maps Arabic-Indic digits to Western ones and ignores case and extra spaces.
     */
    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text.trim()) {
            when (c) {
                in 'ً'..'ٟ', 'ٰ', 'ـ' -> Unit
                'أ', 'إ', 'آ', 'ٱ' -> out.append('ا')
                'ى' -> out.append('ي')
                'ة' -> out.append('ه')
                in '٠'..'٩' -> out.append('0' + (c - '٠'))
                in '۰'..'۹' -> out.append('0' + (c - '۰'))
                ' ', ' ' -> if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                else -> out.append(c.lowercaseChar())
            }
        }
        return out.toString().trimEnd()
    }
}
