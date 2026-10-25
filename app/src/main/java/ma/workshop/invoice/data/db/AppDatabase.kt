package ma.workshop.invoice.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun invoices(): InvoiceDao
    abstract fun settings(): SettingsDao
    abstract fun drafts(): DraftDao

    companion object {
        const val NAME = "workshop-invoice.db"

        /** Day 2: the speech-rate setting. Existing installs keep the normal rate. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN speechRate TEXT NOT NULL DEFAULT 'normal'")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
