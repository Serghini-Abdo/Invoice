package ma.workshop.invoice.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: Long): ProductEntity?

    @Query("SELECT * FROM products ORDER BY sortOrder, id")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT illustration FROM products")
    suspend fun illustrations(): List<String>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM products")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE products SET isActive = :active, updatedAt = :now WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, now: Long)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC, number DESC")
    fun observeAll(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY number")
    suspend fun getAll(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun get(id: String): InvoiceEntity?

    @Query("SELECT COALESCE(MAX(number), 0) FROM invoices")
    suspend fun maxNumber(): Long

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId ORDER BY position")
    suspend fun getItems(invoiceId: String): List<InvoiceItemEntity>

    @Query("SELECT illustration FROM invoice_items")
    suspend fun itemIllustrations(): List<String>

    @Query("SELECT previousSnapshotJson FROM invoice_revisions")
    suspend fun revisionSnapshots(): List<String>

    @Query("SELECT * FROM invoice_items ORDER BY invoiceId, position")
    suspend fun getAllItems(): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoice_revisions WHERE invoiceId = :invoiceId ORDER BY revision DESC")
    suspend fun getRevisions(invoiceId: String): List<RevisionEntity>

    @Query("SELECT * FROM invoice_revisions ORDER BY invoiceId, revision")
    suspend fun getAllRevisions(): List<RevisionEntity>

    @Insert
    suspend fun insert(invoice: InvoiceEntity)

    @Insert
    suspend fun insertItems(items: List<InvoiceItemEntity>)

    @Insert
    suspend fun insertRevision(revision: RevisionEntity)

    @Insert
    suspend fun insertRevisions(revisions: List<RevisionEntity>)

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteItems(invoiceId: String)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM invoices")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE draftKey = :key")
    suspend fun get(key: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("SELECT json FROM drafts")
    suspend fun allJson(): List<String>

    @Query("DELETE FROM drafts WHERE draftKey = :key")
    suspend fun delete(key: String)
}
