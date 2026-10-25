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
        container.appScope.launch { container.products.ensureSeeded() }
        container.appScope.launch {
            // Re-assert the schedule (idempotent) and take a missed backup now, e.g. after the phone was off.
            val plan = container.backupPrefs.plan.value
            container.backupScheduler.apply(plan)
            container.backupScheduler.catchUp(plan, container.backupPrefs.status.value, System.currentTimeMillis())
        }
    }
}
