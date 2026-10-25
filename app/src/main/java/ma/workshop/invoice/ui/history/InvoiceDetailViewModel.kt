package ma.workshop.invoice.ui.history

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceRevision
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.platform.export.ExportFiles
import ma.workshop.invoice.platform.export.ShareHelper
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.screens.ExportStatus
import ma.workshop.invoice.ui.screens.ExportUi
import ma.workshop.invoice.ui.screens.ShareChoice
import kotlin.coroutines.cancellation.CancellationException

sealed interface DetailState {
    data object Loading : DetailState
    data object NotFound : DetailState
    data object Unreadable : DetailState
    data class Ready(val invoice: Invoice, val revisions: List<InvoiceRevision>) : DetailState
}

class InvoiceDetailViewModel(private val container: AppContainer, private val invoiceId: String) : ViewModel() {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    /** Null until the user asks to share; generating a file on every open would be wasted work. */
    private val _export = MutableStateFlow<ExportUi?>(null)
    val export: StateFlow<ExportUi?> = _export.asStateFlow()

    private val _deleteFailed = MutableStateFlow(false)
    val deleteFailed: StateFlow<Boolean> = _deleteFailed.asStateFlow()

    private val exitChannel = Channel<Unit>(Channel.BUFFERED)
    val exits = exitChannel.receiveAsFlow()

    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private var files: ExportFiles? = null

    /**
     * (Re)loads the invoice. Called whenever the screen resumes, so coming back from editing shows the
     * edited invoice. An already shown invoice stays visible while the reload runs.
     */
    fun refresh() {
        viewModelScope.launch {
            val loaded = try {
                val invoice = container.invoices.get(invoiceId)
                if (invoice == null) DetailState.NotFound else DetailState.Ready(invoice, container.invoices.revisions(invoiceId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailState.Unreadable
            }
            val previous = _state.value
            if (previous is DetailState.Ready && loaded is DetailState.Ready && previous.invoice != loaded.invoice) {
                // The files generated for the old content are out of date.
                files = null
                _export.value = null
            }
            _state.value = loaded
        }
    }

    fun prepareShare() {
        val ready = _state.value as? DetailState.Ready ?: return
        _export.value = ExportUi(ExportStatus.PREPARING)
        viewModelScope.launch {
            _export.value = try {
                val generated = container.exporter.export(ready.invoice, container.settings.get())
                files = generated
                ExportUi(ExportStatus.READY, preview = generated.preview?.asImageBitmap())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportUi(ExportStatus.FAILED)
            }
        }
    }

    fun share(context: Context, choice: ShareChoice) {
        val generated = files ?: return
        val list = when (choice) {
            ShareChoice.PNG -> generated.png
            ShareChoice.PDF -> listOfNotNull(generated.pdf)
            ShareChoice.BOTH -> generated.png + listOfNotNull(generated.pdf)
        }
        val opened = ShareHelper.share(context, list)
        _export.value = _export.value?.copy(message = if (opened) null else Strings.SHARE_UNAVAILABLE)
    }

    fun delete() {
        viewModelScope.launch {
            try {
                container.invoices.delete(invoiceId)
                exitChannel.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteFailed.value = true
            }
        }
    }
}
