package ma.workshop.invoice.domain.backup

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ma.workshop.invoice.domain.invoice.Change
import ma.workshop.invoice.domain.invoice.Invoice

/**
 * JSON text for the two structured values the database keeps inside a revision row: the list of
 * human-readable changes and the complete previous version of the invoice. Using the same DTOs as the
 * backup keeps one definition of "what an invoice looks like on disk".
 */
object SnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val changesSerializer = ListSerializer(Change.serializer())

    fun encodeChanges(changes: List<Change>): String = json.encodeToString(changesSerializer, changes)

    /** An unreadable value yields an empty list: an audit entry must never make an invoice unopenable. */
    fun decodeChanges(text: String): List<Change> =
        try {
            json.decodeFromString(changesSerializer, text)
        } catch (e: Exception) {
            emptyList()
        }

    fun encodeInvoice(invoice: Invoice): String = json.encodeToString(InvoiceSnapshotDto.serializer(), invoice.toSnapshotDto())

    fun decodeInvoice(text: String): Invoice? =
        try {
            json.decodeFromString(InvoiceSnapshotDto.serializer(), text).toDomain()
        } catch (e: Exception) {
            null
        }
}
