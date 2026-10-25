package ma.workshop.invoice

import android.app.Application
import kotlinx.coroutines.launch

class InvoiceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.products.ensureSeeded() }
    }
}
