package ma.workshop.invoice.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.flow.FlowIssue
import ma.workshop.invoice.domain.flow.FlowState
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.format.InvoiceDocumentBuilder
import ma.workshop.invoice.domain.input.KeypadEditor
import ma.workshop.invoice.domain.input.KeypadKey
import ma.workshop.invoice.domain.money.Money
import ma.workshop.invoice.domain.pricing.PricingLimits
import ma.workshop.invoice.domain.settings.ExportFormat
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.arabic
import ma.workshop.invoice.ui.components.AmountRow
import ma.workshop.invoice.ui.components.AudioToggle
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ConfirmDialog
import ma.workshop.invoice.ui.components.FieldBox
import ma.workshop.invoice.ui.components.InvoiceTable
import ma.workshop.invoice.ui.components.PaidBadge
import ma.workshop.invoice.ui.components.TablePicture
import ma.workshop.invoice.ui.components.NumericKeypad
import ma.workshop.invoice.ui.components.AppSubBar
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.SpeakerButton
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.theme.appPalette

/** Step 5: all items, invoice total, editable deposit, remaining balance, and the final save confirmation. */
@Composable
fun ReviewScreen(
    state: FlowState,
    speechAvailable: Boolean,
    audioEnabled: Boolean,
    onToggleAudio: () -> Unit,
    onSpeakSummary: () -> Unit,
    onReplay: () -> Unit,
    onDepositChange: (String) -> Unit,
    onSave: () -> Unit,
    onConfirmSave: () -> Unit,
    onDismissSave: () -> Unit,
    onEditItems: () -> Unit,
    onBack: () -> Unit,
) {
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    var fresh by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { onSpeakSummary() }

    val total = state.total
    val deposit = state.deposit
    val remaining: Money? = if (deposit != null && deposit <= total && !deposit.isNegative) total - deposit else null
    val fullyPaid = remaining != null && remaining.isZero && total.isPositive

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.REVIEW_TITLE, onBack)
        AppSubBar { AudioToggle(audioEnabled, onToggleAudio) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.issue == FlowIssue.SAVE_FAILED) StatusBanner(Strings.SAVE_FAILED, BannerKind.Error)

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                InvoiceTable(
                    rows = state.items.mapIndexed { index, item -> InvoiceDocumentBuilder.row(index + 1, item) },
                    picture = TablePicture.Review,
                    modifier = Modifier.padding(6.dp),
                )
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeakerButton(onClick = onReplay, available = speechAvailable, description = Strings.REPLAY)
                    Column(Modifier.weight(1f)) {
                        AmountRow(Strings.INVOICE_TOTAL, ArabicFormat.amount(total), emphasized = true)
                    }
                }
                FieldBox(
                    label = Strings.DEPOSIT,
                    tag = "deposit-field",
                    value = state.depositText,
                    selected = keypadOpen,
                    onClick = {
                        keypadOpen = true
                        fresh = true
                    },
                    error = state.depositProblem?.arabic(),
                    trailing = {
                        Text("DH", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
                if (fullyPaid) {
                    // Paid: "0 DH" in green with the "خالص" badge on the same line. An open balance is bold ink.
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PaidBadge()
                        Spacer(Modifier.width(10.dp))
                        ArabicText(
                            ArabicFormat.amount(remaining!!),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.appPalette.paid,
                            modifier = Modifier.testTag("balance-value"),
                        )
                        Spacer(Modifier.width(8.dp))
                        ArabicText(Strings.REMAINING, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }
                } else {
                    AmountRow(
                        label = Strings.REMAINING,
                        value = remaining?.let { ArabicFormat.amount(it) } ?: "-",
                        emphasized = true,
                    )
                }
            }
        }

        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            if (keypadOpen) {
                NumericKeypad(
                    onKey = { key ->
                        val base = if (fresh && (key is KeypadKey.Digit || key == KeypadKey.Decimal)) "" else state.depositText
                        onDepositChange(KeypadEditor.apply(base, key, PricingLimits.MAX_RATE_DECIMALS))
                        fresh = false
                    },
                    modifier = Modifier.padding(12.dp),
                    onNext = { keypadOpen = false },
                    nextLabel = Strings.CONFIRM,
                )
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.saving) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        ) {
                            ArabicText(Strings.SAVING, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                    BigButton(Strings.SAVE_INVOICE, Icons.Filled.Save, onSave, enabled = !state.saving, kind = ButtonKind.Success)
                    BigButton(Strings.EDIT_ITEMS, Icons.Filled.Edit, onEditItems, enabled = !state.saving, kind = ButtonKind.Secondary)
                }
            }
        }
    }

    if (state.confirmingSave) {
        ConfirmDialog(
            title = Strings.CONFIRM_SAVE_TITLE,
            message = "",
            onConfirm = onConfirmSave,
            onDismiss = onDismissSave,
            confirmText = Strings.SAVE,
            extra = {
                AmountRow(Strings.INVOICE_TOTAL, ArabicFormat.amount(total))
                AmountRow(Strings.DEPOSIT_SHORT, ArabicFormat.amount(deposit ?: Money.ZERO))
                AmountRow(Strings.REMAINING, remaining?.let { ArabicFormat.amount(it) } ?: "-", emphasized = true)
            },
        )
    }
}

enum class ShareChoice { PNG, PDF, BOTH }

enum class ExportStatus { PREPARING, READY, FAILED }

/** What the export panel shows. The preview is the first page of the same file that gets shared. */
data class ExportUi(
    val status: ExportStatus,
    val preview: ImageBitmap? = null,
    /** Set after the share sheet was opened or when no app can handle it. Never says "sent". */
    val message: String? = null,
    /** One image would be taller than 8,000 px: the image button gives way to a message and the PDF buttons. */
    val pngTooLong: Boolean = false,
)

/** Preview of the generated invoice and the share buttons for the chosen export format. */
@Composable
fun ExportPanel(
    format: ExportFormat,
    export: ExportUi,
    onShare: (ShareChoice) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPrint: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
        when (export.status) {
            ExportStatus.PREPARING -> Row(verticalAlignment = Alignment.CenterVertically) {
                ArabicText(Strings.EXPORT_PREPARING, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            ExportStatus.FAILED -> {
                StatusBanner(Strings.EXPORT_FAILED, BannerKind.Warning)
                BigButton(Strings.RETRY, Icons.Filled.Refresh, onRetry, kind = ButtonKind.Secondary)
            }
            ExportStatus.READY -> {
                // The share buttons come first so they are on screen the moment the invoice is saved;
                // the preview follows and can be scrolled in full.
                when {
                    export.pngTooLong -> {
                        StatusBanner(Strings.INVOICE_TOO_LONG_FOR_IMAGE, BannerKind.Warning)
                        BigButton(Strings.SHARE_PDF, Icons.Filled.PictureAsPdf, { onShare(ShareChoice.PDF) }, kind = ButtonKind.Success)
                    }
                    else -> when (format) {
                    ExportFormat.PNG -> BigButton(Strings.SHARE_IMAGE, Icons.Filled.Share, { onShare(ShareChoice.PNG) }, kind = ButtonKind.Success)
                    ExportFormat.PDF -> BigButton(Strings.SHARE_PDF, Icons.Filled.Share, { onShare(ShareChoice.PDF) }, kind = ButtonKind.Success)
                    ExportFormat.BOTH -> {
                        BigButton(Strings.SHARE_IMAGE, Icons.Filled.Image, { onShare(ShareChoice.PNG) }, kind = ButtonKind.Success)
                        BigButton(Strings.SHARE_PDF, Icons.Filled.PictureAsPdf, { onShare(ShareChoice.PDF) }, kind = ButtonKind.Success)
                        BigButton(Strings.SHARE_BOTH, Icons.Filled.Share, { onShare(ShareChoice.BOTH) }, kind = ButtonKind.Secondary)
                    }
                    }
                }
                if (onPrint != null && (format != ExportFormat.PNG || export.pngTooLong)) {
                    BigButton(Strings.PRINT, Icons.Filled.Print, onPrint, kind = ButtonKind.Secondary)
                }
                ArabicText(Strings.SHARE_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                export.preview?.let { bitmap ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = Strings.PREVIEW,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        export.message?.let { StatusBanner(it, BannerKind.Info) }
    }
}

/** Step 6: the invoice is already saved; export and sharing are extras that cannot undo that. */
@Composable
fun SavedScreen(
    invoiceNumber: String,
    format: ExportFormat,
    export: ExportUi,
    onShare: (ShareChoice) -> Unit,
    onRetry: () -> Unit,
    onPrint: () -> Unit,
    onNewInvoice: () -> Unit,
    onHome: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.SAVED_TITLE, onBack = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
            ArabicText(
                "${Strings.INVOICE_NUMBER} $invoiceNumber",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            ExportPanel(format, export, onShare, onRetry, onPrint = onPrint)
        }
        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            // Stacked, each full width: the new invoice first, the home screen under it.
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(Strings.NEW_INVOICE, Icons.Filled.Add, onNewInvoice, Modifier.fillMaxWidth().testTag("saved-new-invoice"))
                BigButton(Strings.HOME, Icons.Filled.Home, onHome, Modifier.fillMaxWidth().testTag("saved-home"), kind = ButtonKind.Secondary)
            }
        }
    }
}
