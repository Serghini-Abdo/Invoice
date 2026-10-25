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
import ma.workshop.invoice.platform.RestoreSafety
import ma.workshop.invoice.domain.catalog.PhotoKey
import ma.workshop.invoice.platform.Dictation
import ma.workshop.invoice.platform.PhotoStore
import ma.workshop.invoice.platform.Speaker
import ma.workshop.invoice.platform.SpeechController
import ma.workshop.invoice.platform.backup.AppFolderBackupStore
import ma.workshop.invoice.platform.backup.BackupPrefs
import ma.workshop.invoice.platform.backup.BackupScheduler
import ma.workshop.invoice.platform.backup.BackupService
import ma.workshop.invoice.platform.backup.LocalBackupStore
import ma.workshop.invoice.platform.drive.DriveBackupApi
import ma.workshop.invoice.platform.drive.DriveBackupClient
import java.io.File
import ma.workshop.invoice.platform.export.InvoiceExporter

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

    /** Replaceable so instrumented tests can start from a clean schedule and history. */
    @Volatile
    var backupPrefs: BackupPrefs = BackupPrefs(application.getSharedPreferences("backup", android.content.Context.MODE_PRIVATE))

    /** The two backup destinations. Replaceable so instrumented tests can run the worker without Google or real storage. */
    @Volatile
    var localStore: LocalBackupStore = AppFolderBackupStore(application)

    @Volatile
    var drive: DriveBackupApi = DriveBackupClient(
        application,
        savedAccount = { backupPrefs.driveAccount },
        saveAccount = { backupPrefs.driveAccount = it },
    )

    @Volatile
    var backupService: BackupService = newBackupService(backupPrefs)

    fun newBackupService(prefs: BackupPrefs, now: () -> Long = { System.currentTimeMillis() }) = BackupService(
        export = { backups.export() },
        prefs = prefs,
        local = { localStore },
        drive = { drive },
        appVersion = BuildConfig.VERSION_NAME,
        now = now,
    )
    val backupScheduler: BackupScheduler by lazy { BackupScheduler(application) }
    val restoreSafety: RestoreSafety by lazy { RestoreSafety(File(application.filesDir, "backups")) }

    /** Set at startup if the previous restore was cut off; shown once on the home screen. */
    @Volatile
    var startupNotice: String? = null

    val speech: SpeechController by lazy { SpeechController(application) }
    val speaker: Speaker by lazy { Speaker(speech) }
    val dictation: Dictation by lazy { Dictation(application) }
    val exporter: InvoiceExporter by lazy { InvoiceExporter(application) }
    val photos: PhotoStore by lazy { PhotoStore(application) }

    /** Deletes photo files that no product, invoice item, revision snapshot or draft refers to. */
    suspend fun sweepPhotos() {
        val texts = database.products().illustrations() + database.invoices().itemIllustrations() +
            database.invoices().revisionSnapshots() + database.drafts().allJson()
        photos.sweep(PhotoKey.referencedIn(texts))
    }

    /** For work that must outlive a screen, such as saving a draft while the user leaves. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
