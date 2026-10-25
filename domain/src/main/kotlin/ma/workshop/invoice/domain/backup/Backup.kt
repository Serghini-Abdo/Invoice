package ma.workshop.invoice.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceRevision
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.settings.AppSettings

object BackupFormat {
    const val FORMAT = "workshop-invoice-backup"

    /** Bump when the document layout changes incompatibly; older versions must stay restorable. */
    const val VERSION = 1

    /** Hard cap so a wrong file cannot exhaust memory while being parsed. */
    const val MAX_BYTES = 64 * 1024 * 1024
}

@Serializable
data class BackupDocument(
    val format: String = BackupFormat.FORMAT,
    val version: Int = BackupFormat.VERSION,
    val createdAt: Long,
    val appVersion: String = "",
    val settings: SettingsDto,
    val products: List<ProductDto>,
    val invoices: List<InvoiceDto>,
)

/** An invoice together with its audit trail. */
data class InvoiceRecord(val invoice: Invoice, val revisions: List<InvoiceRevision>)

/** Everything a backup contains, as domain objects. This is what the repository exports and restores. */
data class BackupData(
    val settings: AppSettings,
    val products: List<ProductDefinition>,
    val invoices: List<InvoiceRecord>,
)

enum class BackupProblemCode {
    /** Not JSON, not ours, or empty/too large. */
    NOT_A_BACKUP,

    /** Written by a newer version of the app than this one. */
    UNSUPPORTED_VERSION,

    /** Right kind of file but missing or malformed fields (typically truncated or hand-edited). */
    CORRUPT,

    /** A structurally valid file whose contents are inconsistent (bad totals, duplicate ids, ...). */
    INCONSISTENT,
}

data class BackupProblem(val code: BackupProblemCode, val detail: String)

sealed interface BackupParseResult {
    data class Valid(val document: BackupDocument, val data: BackupData) : BackupParseResult
    data class Invalid(val problems: List<BackupProblem>) : BackupParseResult
}

object BackupCodec {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        // Newer minor versions may add fields; unknown ones are ignored, missing required ones are not.
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Header(val format: String? = null, val version: Int? = null)

    fun toDocument(data: BackupData, now: Long, appVersion: String): BackupDocument = BackupDocument(
        createdAt = now,
        appVersion = appVersion,
        settings = data.settings.toDto(),
        products = data.products.map { it.toDto() },
        invoices = data.invoices.map { record ->
            InvoiceDto(record.invoice.toSnapshotDto(), record.revisions.map { it.toDto() })
        },
    )

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    /**
     * Parses and fully validates a backup. Nothing here touches the database: the caller restores
     * only when the result is [BackupParseResult.Valid].
     */
    fun parse(text: String): BackupParseResult {
        if (text.isBlank()) {
            return invalid(BackupProblemCode.NOT_A_BACKUP, "empty file")
        }
        if (text.length > BackupFormat.MAX_BYTES) {
            return invalid(BackupProblemCode.NOT_A_BACKUP, "file too large")
        }

        val header = try {
            json.decodeFromString(Header.serializer(), text)
        } catch (e: Exception) {
            return invalid(BackupProblemCode.NOT_A_BACKUP, "not valid JSON: ${e.message.orEmpty().take(120)}")
        }
        if (header.format != BackupFormat.FORMAT) {
            return invalid(BackupProblemCode.NOT_A_BACKUP, "unknown format '${header.format}'")
        }
        val version = header.version ?: return invalid(BackupProblemCode.CORRUPT, "missing version")
        if (version > BackupFormat.VERSION) {
            return invalid(BackupProblemCode.UNSUPPORTED_VERSION, "version $version is newer than ${BackupFormat.VERSION}")
        }
        if (version < 1) {
            return invalid(BackupProblemCode.CORRUPT, "invalid version $version")
        }

        val document = try {
            json.decodeFromString(BackupDocument.serializer(), text)
        } catch (e: Exception) {
            return invalid(BackupProblemCode.CORRUPT, e.message.orEmpty().take(160))
        }

        val problems = BackupValidator.validate(document)
        if (problems.isNotEmpty()) return BackupParseResult.Invalid(problems)

        // validate() guarantees every mapping below succeeds.
        val data = checkNotNull(toData(document)) { "validated backup failed to map" }
        return BackupParseResult.Valid(document, data)
    }

    fun toData(document: BackupDocument): BackupData? {
        val settings = document.settings.toDomain() ?: return null
        val products = document.products.map { it.toDomain() ?: return null }
        val invoices = document.invoices.map { dto ->
            val invoice = dto.invoice.toDomain() ?: return null
            InvoiceRecord(invoice, dto.revisions.map { it.toDomain(invoice.id) ?: return null })
        }
        return BackupData(settings, products, invoices)
    }

    private fun invalid(code: BackupProblemCode, detail: String) =
        BackupParseResult.Invalid(listOf(BackupProblem(code, detail)))
}
