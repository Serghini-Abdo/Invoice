package ma.workshop.invoice.ui.flow

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.domain.flow.FlowEvent
import ma.workshop.invoice.domain.flow.FlowMode
import ma.workshop.invoice.domain.flow.FlowStep
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.VmFactory
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ConfirmDialog
import ma.workshop.invoice.ui.components.ScreenHeader
import ma.workshop.invoice.ui.screens.CatalogPickerScreen
import ma.workshop.invoice.ui.screens.CustomerScreen
import ma.workshop.invoice.ui.screens.ItemConfirmScreen
import ma.workshop.invoice.ui.screens.ItemEntryScreen
import ma.workshop.invoice.ui.screens.ItemListScreen
import ma.workshop.invoice.ui.screens.ReviewScreen
import ma.workshop.invoice.ui.screens.SavedScreen

/**
 * Renders the invoice workflow. Which screen shows is decided only by the reducer's [FlowStep];
 * nothing here navigates on its own, so the required order is guaranteed by the tested state machine.
 */
@Composable
fun InvoiceFlowRoute(
    container: AppContainer,
    editInvoiceId: String?,
    onExit: () -> Unit,
    onNewInvoice: () -> Unit,
    onHome: () -> Unit,
) {
    val vm: InvoiceFlowViewModel = viewModel(
        key = "flow-${editInvoiceId ?: "new"}",
        factory = VmFactory { InvoiceFlowViewModel(container, editInvoiceId) },
    )
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val resumeOffer by vm.resumeOffer.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val speechAvailable by vm.speechAvailable.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val saved by vm.savedInvoice.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val currentOnExit by rememberUpdatedState(onExit)
    LaunchedEffect(vm) { vm.exits.collect { currentOnExit() } }

    fun speakFeedback(result: SpeakResult) {
        val message = when (result) {
            SpeakResult.SPOKEN -> null
            SpeakResult.AUDIO_OFF -> Strings.AUDIO_OFF
            SpeakResult.UNAVAILABLE -> Strings.AUDIO_UNAVAILABLE_TOAST
        }
        if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    BackHandler { vm.onEvent(FlowEvent.BackPressed) }

    val current = state
    when {
        loadError != null -> LoadErrorScreen(
            message = if (loadError == LoadError.NOT_FOUND) Strings.INVOICE_NOT_FOUND else Strings.INVOICE_UNREADABLE,
            onBack = onExit,
        )
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> when (val step = current.step) {
            FlowStep.Customer -> CustomerScreen(
                customer = current.customer,
                onChange = { name, phone -> vm.onEvent(FlowEvent.CustomerEdited(name, phone)) },
                onContinue = { vm.onEvent(FlowEvent.CustomerContinue) },
                onBack = { vm.onEvent(FlowEvent.BackPressed) },
            )
            FlowStep.ItemList -> ItemListScreen(
                state = current,
                isEditing = current.mode is FlowMode.Edit,
                onAdd = { vm.onEvent(FlowEvent.AddProductPressed) },
                onEditItem = { vm.onEvent(FlowEvent.EditItemPressed(it)) },
                onRemoveItem = { vm.onEvent(FlowEvent.RemoveItemPressed(it)) },
                onConfirmRemove = { vm.onEvent(FlowEvent.RemoveConfirmed) },
                onCancelRemove = { vm.onEvent(FlowEvent.RemoveCancelled) },
                onEditCustomer = { vm.onEvent(FlowEvent.EditCustomerPressed) },
                onContinue = { vm.onEvent(FlowEvent.ContinueToReview) },
                onBack = { vm.onEvent(FlowEvent.BackPressed) },
            )
            FlowStep.Catalog -> CatalogPickerScreen(
                products = products,
                onPick = { vm.onEvent(FlowEvent.ProductChosen(it.toRef())) },
                onSpeak = { speakFeedback(vm.speakProductName(it)) },
                speechAvailable = speechAvailable,
                onBack = { vm.onEvent(FlowEvent.BackPressed) },
            )
            FlowStep.ItemEntry -> current.pending?.let { pending ->
                ItemEntryScreen(
                    pending = pending,
                    onFormChange = { vm.onEvent(FlowEvent.FormEdited(it)) },
                    onSubmit = { vm.onEvent(FlowEvent.EntrySubmitted) },
                    onBack = { vm.onEvent(FlowEvent.BackPressed) },
                )
            }
            FlowStep.ItemConfirm -> current.pending?.let { pending ->
                ItemConfirmScreen(
                    pending = pending,
                    speechAvailable = speechAvailable,
                    onSpeakTotal = { vm.autoSpeakItemTotal() },
                    onReplay = { speakFeedback(vm.replayItemTotal()) },
                    onConfirm = { vm.onEvent(FlowEvent.ItemConfirmed) },
                    onCancel = { vm.onEvent(FlowEvent.ItemCancelled) },
                    onBack = { vm.onEvent(FlowEvent.BackPressed) },
                )
            }
            FlowStep.Review -> ReviewScreen(
                state = current,
                speechAvailable = speechAvailable,
                onSpeakSummary = { vm.autoSpeakInvoiceSummary() },
                onReplay = { speakFeedback(vm.replayInvoiceSummary()) },
                onDepositChange = { vm.onEvent(FlowEvent.DepositEdited(it)) },
                onSave = { vm.onEvent(FlowEvent.SavePressed) },
                onConfirmSave = { vm.onEvent(FlowEvent.SaveConfirmed) },
                onDismissSave = { vm.onEvent(FlowEvent.SaveDismissed) },
                onEditItems = { vm.onEvent(FlowEvent.GoToItems) },
                onBack = { vm.onEvent(FlowEvent.BackPressed) },
            )
            is FlowStep.Saved -> SavedScreen(
                invoiceNumber = saved?.let { ArabicFormat.invoiceNumber(it.number) }.orEmpty(),
                format = settings.exportFormat,
                export = export,
                onShare = { vm.share(context, it) },
                onRetry = vm::retryExport,
                onNewInvoice = onNewInvoice,
                onHome = onHome,
            )
        }
    }

    if (resumeOffer != null) {
        ConfirmDialog(
            title = Strings.RESUME_DRAFT_TITLE,
            message = Strings.RESUME_DRAFT_MESSAGE,
            onConfirm = vm::resumeDraft,
            onDismiss = vm::discardDraftAndStartFresh,
            confirmText = Strings.RESUME,
            dismissText = Strings.START_NEW,
        )
    }

    if (current?.confirmingExit == true) {
        val isNew = current.mode == FlowMode.New
        AlertDialog(
            onDismissRequest = { vm.onEvent(FlowEvent.ExitCancelled) },
            title = { Text(Strings.LEAVE_TITLE, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isNew) Strings.LEAVE_NEW_MESSAGE else Strings.LEAVE_EDIT_MESSAGE, style = MaterialTheme.typography.bodyLarge)
                    if (isNew) {
                        BigButton(Strings.LEAVE_KEEP_DRAFT, Icons.Filled.Save, { vm.onEvent(FlowEvent.ExitKeepDraft) })
                    }
                    BigButton(Strings.LEAVE_DISCARD, Icons.Filled.Delete, { vm.onEvent(FlowEvent.ExitDiscard) }, kind = ButtonKind.Danger)
                    BigButton(Strings.KEEP_EDITING, Icons.Filled.Edit, { vm.onEvent(FlowEvent.ExitCancelled) }, kind = ButtonKind.Secondary)
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun LoadErrorScreen(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Strings.INVOICE_DETAILS, onBack)
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.titleLarge)
        }
    }
}
