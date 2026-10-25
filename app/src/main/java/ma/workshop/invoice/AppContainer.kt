package ma.workshop.invoice

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ma.workshop.invoice.data.BackupRepository
import ma.workshop.invoice.data.DraftRepository
import ma.workshop.invoice.data.Environment
import ma.workshop.invoice.data.InvoiceRepository
import ma.workshop.invoice.data.ProductRepository
import ma.workshop.invoice.data.SettingsRepository
import ma.workshop.invoice.data.db.AppDatabase

/**
 * Hand-rolled dependency container: small enough that a DI framework would only add build risk.
 * Everything here works offline; nothing depends on Google Play Services or the network.
 */
class AppContainer(val application: Application) {
    val environment = Environment()

    val database: AppDatabase by lazy { AppDatabase.build(application) }

    val settings: SettingsRepository by lazy { SettingsRepository(database) }
    val products: ProductRepository by lazy { ProductRepository(database, environment) }
    val invoices: InvoiceRepository by lazy { InvoiceRepository(database, environment) }
    val drafts: DraftRepository by lazy { DraftRepository(database, environment) }
    val backups: BackupRepository by lazy { BackupRepository(database) }

    /** For work that must outlive a screen, such as saving a draft while the user leaves. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
