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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.flow.FlowIssue
import ma.workshop.invoice.domain.flow.FlowState
import ma.workshop.invoice.domain.format.ArabicFormat
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
import ma.workshop.invoice.ui.components.IllustrationImage
import ma.workshop.invoice.ui.components.NumericKeypad
import ma.workshop.invoice.ui.components.ScreenHeader
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.SpeakerButton
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.theme.AppColors

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
        ScreenHeader(Strings.REVIEW_TITLE, onBack, actions = { AudioToggle(audioEnabled, onToggleAudio) })

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.issue == FlowIssue.SAVE_FAILED) StatusBanner(Strings.SAVE_FAILED, BannerKind.Error)

            for (item in state.items) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IllustrationImage(item.illustration, Modifier.size(48.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.productName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${ma.workshop.invoice.domain.format.ArabicTerms.QUANTITY}: ${item.pricing.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(ArabicFormat.amount(item.pricing.total), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AmountRow(Strings.INVOICE_TOTAL, ArabicFormat.amount(total), emphasized = true)
                    }
                    SpeakerButton(onClick = onReplay, available = speechAvailable, description = Strings.REPLAY)
                }
                FieldBox(
                    label = Strings.DEPOSIT,
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
                AmountRow(
                    label = Strings.REMAINING,
                    value = remaining?.let { ArabicFormat.amount(it) } ?: "-",
                    emphasized = true,
                    valueColor = if (fullyPaid) AppColors.Green else MaterialTheme.colorScheme.primary,
                )
            }

            if (fullyPaid) StatusBanner(Strings.FULLY_PAID_LONG, BannerKind.Success)
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(Strings.SAVING, style = MaterialTheme.typography.titleMedium)
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (export.status) {
            ExportStatus.PREPARING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(Strings.EXPORT_PREPARING, style = MaterialTheme.typography.titleMedium)
            }
            ExportStatus.FAILED -> {
                StatusBanner(Strings.EXPORT_FAILED, BannerKind.Warning)
                BigButton(Strings.RETRY, Icons.Filled.Refresh, onRetry, kind = ButtonKind.Secondary)
            }
            ExportStatus.READY -> {
                // The share buttons come first so they are on screen the moment the invoice is saved;
                // the preview follows and can be scrolled in full.
                when (format) {
                    ExportFormat.PNG -> BigButton(Strings.SHARE_IMAGE, Icons.Filled.Share, { onShare(ShareChoice.PNG) }, kind = ButtonKind.Success)
                    ExportFormat.PDF -> BigButton(Strings.SHARE_PDF, Icons.Filled.Share, { onShare(ShareChoice.PDF) }, kind = ButtonKind.Success)
                    ExportFormat.BOTH -> {
                        BigButton(Strings.SHARE_IMAGE, Icons.Filled.Image, { onShare(ShareChoice.PNG) }, kind = ButtonKind.Success)
                        BigButton(Strings.SHARE_PDF, Icons.Filled.PictureAsPdf, { onShare(ShareChoice.PDF) }, kind = ButtonKind.Success)
                        BigButton(Strings.SHARE_BOTH, Icons.Filled.Share, { onShare(ShareChoice.BOTH) }, kind = ButtonKind.Secondary)
                    }
                }
                if (onPrint != null && format != ExportFormat.PNG) {
                    BigButton(Strings.PRINT, Icons.Filled.Print, onPrint, kind = ButtonKind.Secondary)
                }
                Text(Strings.SHARE_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        ScreenHeader(Strings.SAVED_TITLE, onBack = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Green, modifier = Modifier.size(72.dp))
            Text(
                "${Strings.INVOICE_NUMBER} $invoiceNumber",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            ExportPanel(format, export, onShare, onRetry, onPrint = onPrint)
        }
        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // "New invoice" is the longer label, so it gets the larger share and stays on one line.
                BigButton(Strings.HOME, Icons.Filled.Home, onHome, Modifier.weight(0.85f), kind = ButtonKind.Secondary)
                BigButton(Strings.NEW_INVOICE, Icons.Filled.Add, onNewInvoice, Modifier.weight(1.15f))
            }
        }
    }
}
