package ma.workshop.invoice

import android.app.Application
import kotlinx.coroutines.launch

class InvoiceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (container.restoreSafety.consumeInterrupted()) container.startupNotice = ma.workshop.invoice.ui.Strings.RESTORE_INTERRUPTED
        container.appScope.launch {
            container.products.ensureSeeded()
            // Once per installation: the owner's own products (drawn after his sketches, 2026-11-01).
            val prefs = getSharedPreferences("catalog", MODE_PRIVATE)
            if (!prefs.getBoolean(WORKSHOP_PRODUCTS_ADDED, false)) {
                runCatching { container.products.addWorkshopProducts() }
                    .onSuccess { prefs.edit().putBoolean(WORKSHOP_PRODUCTS_ADDED, true).apply() }
            }
        }
        // Photos nothing refers to any more (an abandoned product edit, a deleted invoice) are removed.
        container.appScope.launch { runCatching { container.sweepPhotos() } }
        container.appScope.launch {
            // Re-assert the schedule (idempotent) and take a missed backup now, e.g. after the phone was off.
            val plan = container.backupPrefs.plan.value
            container.backupScheduler.apply(plan)
            container.backupScheduler.catchUp(plan, container.backupPrefs.status.value, System.currentTimeMillis())
        }
    }

    private companion object {
        const val WORKSHOP_PRODUCTS_ADDED = "workshop_products_v1"
    }
}
