package ma.workshop.invoice.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ma.workshop.invoice.domain.settings.SpeechRate

/** Catalog definition. Deliberately has no price column: rates are typed per invoice item. */
@Entity(tableName = "products", indices = [Index(value = ["isActive", "sortOrder"])])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val illustration: String,
    val formula: String,
    val defaultUnit: String,
    val category: String?,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["number"], unique = true),
        Index(value = ["createdAt"]),
        Index(value = ["customerName"]),
    ],
)
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val number: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Int,
    /** Customer details are a snapshot on the invoice; there is no customer table. */
    val customerName: String,
    val customerPhone: String,
    val totalCentimes: Long,
    val depositCentimes: Long,
    val remainingCentimes: Long,
    val itemCount: Int,
)

/**
 * One physical item. Every column except [productId] is a snapshot, so the row stays valid and
 * unchanged when the catalog product is edited, deactivated or deleted ([productId] becomes null).
 */
@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["invoiceId", "position"]), Index(value = ["productId"])],
)
data class InvoiceItemEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val productId: Long?,
    val position: Int,
    val productName: String,
    val illustration: String,
    val formula: String,
    val widthThousandths: Long?,
    val widthUnit: String?,
    /** Normalised width in micrometres, as used for the calculation. */
    val widthMicrometers: Long?,
    val heightThousandths: Long?,
    val heightUnit: String?,
    val heightMicrometers: Long?,
    val quantity: Int,
    val unitRateCentimes: Long,
    val rateBasis: String,
    /** Exact area (m²) or perimeter (m) of one piece as plain decimal text; null for per-piece items. */
    val baseAmount: String?,
    val totalCentimes: Long,
)

/** Audit trail: one row per saved edit of an existing invoice. */
@Entity(
    tableName = "invoice_revisions",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["invoiceId", "revision"])],
)
data class RevisionEntity(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val revision: Int,
    val changedAt: Long,
    val changesJson: String,
    val previousTotalCentimes: Long,
    val newTotalCentimes: Long,
    val previousDepositCentimes: Long,
    val newDepositCentimes: Long,
    val previousSnapshotJson: String,
)

/** Single-row table (id = 1) holding the workshop details and preferences. */
@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val workshopName: String,
    val workshopPhone: String,
    val exportFormat: String,
    val audioEnabled: Boolean,
    val speakItemTotals: Boolean,
    val speakInvoiceTotals: Boolean,
    val thankYouMessage: String,
    val nextInvoiceNumber: Long,
    /** True once the starter catalog has been inserted, so deleting every product never re-seeds. */
    val catalogSeeded: Boolean,
    /** Added in schema version 2 (Day 2). */
    @ColumnInfo(defaultValue = "normal")
    val speechRate: String = SpeechRate.NORMAL.id,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** Auto-saved unfinished invoice (key "new"), restored after an interruption. */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val draftKey: String,
    val json: String,
    val updatedAt: Long,
)
