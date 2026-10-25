package ma.workshop.invoice.domain.settings

/** How a finished invoice is exported. */
enum class ExportFormat(val id: String) {
    /** Image, best for WhatsApp. */
    PNG("png"),

    /** Document, best for printing and archiving. */
    PDF("pdf"),

    /** Let the user choose (or generate both) every time an invoice is shared. */
    BOTH("both");

    companion object {
        fun fromId(id: String): ExportFormat? = entries.firstOrNull { it.id == id }
    }
}

/** The few settings the workshop owner can change. Persisted in the AppSettings table and backed up. */
data class AppSettings(
    val workshopName: String = "",
    val workshopPhone: String = "",
    val exportFormat: ExportFormat = ExportFormat.PNG,
    /** Master switch for any speech. Everything must work with this off. */
    val audioEnabled: Boolean = true,
    /** Speak an item's total on the confirmation screen. */
    val speakItemTotals: Boolean = true,
    /** Speak the invoice total, deposit and remaining balance on the review screen. */
    val speakInvoiceTotals: Boolean = true,
    val thankYouMessage: String = DEFAULT_THANK_YOU,
    /** Next invoice number to hand out. Never decreases, so numbers are not reused after deletions. */
    val nextInvoiceNumber: Long = 1L,
) {
    companion object {
        const val DEFAULT_THANK_YOU = "شكراً لثقتكم"
    }
}
