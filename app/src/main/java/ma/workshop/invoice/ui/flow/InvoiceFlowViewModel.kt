package ma.workshop.invoice.ui.flow

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.domain.flow.FlowEffect
import ma.workshop.invoice.domain.flow.FlowEvent
import ma.workshop.invoice.domain.flow.FlowState
import ma.workshop.invoice.domain.flow.InvoiceFlow
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceDraft
import ma.workshop.invoice.domain.invoice.PaymentSummary
import ma.workshop.invoice.domain.invoice.ProductDefinition
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.speech.SpeechText
import ma.workshop.invoice.domain.speech.SpeechTexts
import ma.workshop.invoice.platform.SpeakResult
import ma.workshop.invoice.platform.SpeechStatus
import ma.workshop.invoice.domain.catalog.PhotoKey
import ma.workshop.invoice.platform.export.ExportFiles
import ma.workshop.invoice.platform.export.PdfPrintAdapter
import ma.workshop.invoice.platform.export.ShareHelper
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.screens.ExportStatus
import ma.workshop.invoice.ui.screens.ExportUi
import ma.workshop.invoice.ui.screens.ShareChoice
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

enum class LoadError { NOT_FOUND, UNREADABLE }

/**
 * Hosts the invoice workflow. All navigation and calculation rules live in the pure [InvoiceFlow]
 * reducer; this class only feeds it events, performs its effects (save, leave) and keeps the
 * unfinished invoice auto-saved so nothing is lost if the app is interrupted.
 */
@OptIn(FlowPreview::class)
class InvoiceFlowViewModel(
    private val container: AppContainer,
    private val editInvoiceId: String?,
) : ViewModel() {

    private val flow = InvoiceFlow { UUID.randomUUID().toString() }

    private val _state = MutableStateFlow<FlowState?>(null)
    val state: StateFlow<FlowState?> = _state.asStateFlow()

    private val _loadError = MutableStateFlow<LoadError?>(null)
    val loadError: StateFlow<LoadError?> = _loadError.asStateFlow()

    /** An unfinished draft found at start; the user chooses to continue it or start over. */
    private val _resumeOffer = MutableStateFlow<FlowState?>(null)
    val resumeOffer: StateFlow<FlowState?> = _resumeOffer.asStateFlow()

    private val _savedInvoice = MutableStateFlow<Invoice?>(null)
    val savedInvoice: StateFlow<Invoice?> = _savedInvoice.asStateFlow()

    private val _export = MutableStateFlow(ExportUi(ExportStatus.PREPARING))
    val export: StateFlow<ExportUi> = _export.asStateFlow()

    private val exitChannel = Channel<Unit>(Channel.BUFFERED)
    val exits = exitChannel.receiveAsFlow()

    val products: StateFlow<List<ProductDefinition>> =
        container.products.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** True when speaking is both switched on and the phone has a working Arabic voice. */
    val speechAvailable: StateFlow<Boolean> =
        combine(settings, container.speech.status) { s, status -> s.audioEnabled && status is SpeechStatus.Ready }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var exportJob: Job? = null
    private var exportFiles: ExportFiles? = null
    private var discarded = false

    init {
        container.speech.ensureStarted()
        viewModelScope.launch {
            if (editInvoiceId == null) {
                _resumeOffer.value = runCatching { container.drafts.load() }.getOrNull()
                _state.value = flow.startNew()
            } else {
                val invoice = try {
                    container.invoices.get(editInvoiceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _loadError.value = LoadError.UNREADABLE
                    return@launch
                }
                if (invoice == null) {
                    _loadError.value = LoadError.NOT_FOUND
                } else {
                    _state.value = flow.startEdit(invoice)
                }
            }
        }
        if (editInvoiceId == null) {
            // Auto-save the unfinished invoice shortly after every change.
            viewModelScope.launch {
                _state.filterNotNull().debounce(400).collect { state ->
                    if (!discarded && _resumeOffer.value == null) runCatching { container.drafts.save(state) }
                }
            }
        }
    }

    fun onEvent(event: FlowEvent) {
        var current = _state.value ?: return
        // A new item keeps its own literal copy of the product photo, so later edits never change it.
        val pending = current.pending
        if (event == FlowEvent.ItemConfirmed && pending != null && pending.editingItemId == null &&
            PhotoKey.isPhoto(pending.product.illustration)
        ) {
            val copy = container.photos.copyFor(pending.product.illustration)
            current = current.copy(pending = pending.copy(product = pending.product.copy(illustration = copy)))
        }
        val transition = flow.reduce(current, event)
        _state.value = transition.state
        when (val effect = transition.effect) {
            is FlowEffect.Save -> save(effect.draft)
            is FlowEffect.Exit -> exit(effect.keepDraft)
            null -> Unit
        }
    }

    fun resumeDraft() {
        val draft = _resumeOffer.value ?: return
        _state.value = draft
        _resumeOffer.value = null
    }

    fun discardDraftAndStartFresh() {
        _resumeOffer.value = null
        _state.value = flow.startNew()
        viewModelScope.launch { runCatching { container.drafts.clear() } }
    }

    private fun exit(keepDraft: Boolean) {
        if (!keepDraft) {
            discarded = true
            container.appScope.launch { runCatching { container.drafts.clear() } }
        } else {
            // Make sure the very latest state is stored even though the debounce may not have fired yet.
            _state.value?.let { latest -> container.appScope.launch { runCatching { container.drafts.save(latest) } } }
        }
        exitChannel.trySend(Unit)
    }

    private fun save(draft: InvoiceDraft) {
        viewModelScope.launch {
            try {
                // Never cancelled halfway: the transaction either completes or rolls back as a whole.
                val invoice = withContext(NonCancellable) {
                    val saved = container.invoices.save(draft)
                    runCatching { container.drafts.clear() }
                    saved
                }
                _savedInvoice.value = invoice
                onEvent(FlowEvent.SaveSucceeded(invoice.id))
                startExport(invoice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onEvent(FlowEvent.SaveFailed)
            }
        }
    }

    // ---- export (always after, and independent of, saving) ----

    private fun startExport(invoice: Invoice) {
        exportJob?.cancel()
        _export.value = ExportUi(ExportStatus.PREPARING)
        exportJob = viewModelScope.launch {
            try {
                val files = container.exporter.export(invoice, container.settings.get())
                exportFiles = files
                _export.value = ExportUi(ExportStatus.READY, preview = files.preview?.asImageBitmap(), pngTooLong = files.pngTooLong)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _export.value = ExportUi(ExportStatus.FAILED)
            }
        }
    }

    fun retryExport() {
        _savedInvoice.value?.let(::startExport)
    }

    fun share(context: Context, choice: ShareChoice) {
        val files = exportFiles ?: return
        val toShare = when (choice) {
            ShareChoice.PNG -> listOfNotNull(files.png)
            ShareChoice.PDF -> listOfNotNull(files.pdf)
            ShareChoice.BOTH -> listOfNotNull(files.png, files.pdf)
        }
        val opened = ShareHelper.share(context, toShare)
        _export.value = _export.value.copy(message = if (opened) null else Strings.SHARE_UNAVAILABLE)
    }

    /** Opens the system print dialog for the generated PDF. */
    fun print(context: Context) {
        val pdf = exportFiles?.pdf ?: return
        val opened = PdfPrintAdapter.print(context, pdf)
        if (!opened) _export.value = _export.value.copy(message = Strings.SHARE_UNAVAILABLE)
    }

    // ---- speech ----

    /** The speaker on a product card: speaks the name only; it never selects the product. */
    fun speakProductName(name: String): SpeakResult = container.speaker.say(name, settings.value)

    fun speakAddProduct(): SpeakResult = container.speaker.say(speechText().addProduct, settings.value)

    fun replayItemTotal(): SpeakResult {
        val text = itemTotalText() ?: return SpeakResult.UNAVAILABLE
        return container.speaker.say(text, settings.value)
    }

    fun autoSpeakItemTotal() {
        if (settings.value.speakItemTotals) itemTotalText()?.let { container.speaker.sayIfReady(it, settings.value) }
    }

    fun replayInvoiceSummary(): SpeakResult {
        val text = invoiceSummaryText() ?: return SpeakResult.UNAVAILABLE
        return container.speaker.say(text, settings.value)
    }

    fun autoSpeakInvoiceSummary() {
        if (settings.value.speakInvoiceTotals) invoiceSummaryText()?.let { container.speaker.sayIfReady(it, settings.value) }
    }

    private fun itemTotalText(): String? {
        val pending = _state.value?.pending
        val pricing = pending?.confirmedPricing ?: return null
        return speechText().itemTotal(pending.product.name, pricing.total)
    }

    private fun invoiceSummaryText(): String? {
        val state = _state.value ?: return null
        val deposit = state.deposit ?: Money.ZERO
        return speechText().paymentSummary(PaymentSummary(state.total, deposit, state.total - deposit))
    }

    /** Sentences in the language chosen in Settings (Darija by default). */
    private fun speechText(): SpeechText = SpeechTexts.of(settings.value.speechLanguage)

    /** After the user came back from installing a voice, look for it again. */
    fun recheckVoiceIfMissing() {
        if (container.speech.status.value is SpeechStatus.Unavailable) container.speech.recheck()
    }

    fun installVoiceIntent() = container.speech.installVoiceDataIntent()

    /** The quick mute switch: also silences anything currently being spoken. */
    fun toggleAudio() {
        container.speaker.stop()
        viewModelScope.launch { container.settings.update { it.copy(audioEnabled = !it.audioEnabled) } }
    }

    override fun onCleared() {
        container.speaker.stop()
        super.onCleared()
    }
}
