package ma.workshop.invoice.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The single local database. Everything the app needs works offline from here.
 *
 * Migration policy: [version] only ever goes up, every bump ships a real [Migration] in [MIGRATIONS]
 * and a migration test, and `fallbackToDestructiveMigration` is never used, so an app update can
 * never wipe saved invoices. See docs/data-and-migrations.md.
 */
@Database(
    entities = [
        ProductEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        RevisionEntity::class,
        SettingsEntity::class,
        DraftEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun invoices(): InvoiceDao
    abstract fun settings(): SettingsDao
    abstract fun drafts(): DraftDao

    companion object {
        const val NAME = "workshop-invoice.db"

        /** Add new migrations here, e.g. `MIGRATION_1_2`. Empty while the schema is at version 1. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
