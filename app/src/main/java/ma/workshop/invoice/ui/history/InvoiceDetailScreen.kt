package ma.workshop.invoice.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.domain.format.ArabicTerms
import ma.workshop.invoice.domain.format.InvoiceDocumentBuilder
import ma.workshop.invoice.domain.invoice.Change
import ma.workshop.invoice.domain.invoice.ChangeType
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceRevision
import ma.workshop.invoice.domain.settings.ExportFormat
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.AmountRow
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ConfirmDialog
import ma.workshop.invoice.ui.components.InvoiceTable
import ma.workshop.invoice.ui.components.TablePicture
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.components.StrictIconButton
import ma.workshop.invoice.ui.screens.ExportPanel
import ma.workshop.invoice.ui.screens.ExportUi
import ma.workshop.invoice.ui.screens.ShareChoice
import ma.workshop.invoice.ui.theme.appPalette
import java.time.ZoneId

fun Change.arabic(): String = when (type) {
    ChangeType.ITEM_ADDED -> "${Strings.CHANGE_ITEM_ADDED}: $label"
    ChangeType.ITEM_REMOVED -> "${Strings.CHANGE_ITEM_REMOVED}: $label"
    ChangeType.ITEM_CHANGED -> "${Strings.CHANGE_ITEM_CHANGED}: $label"
    ChangeType.CUSTOMER_CHANGED -> Strings.CHANGE_CUSTOMER
    ChangeType.DEPOSIT_CHANGED -> Strings.CHANGE_DEPOSIT
}

@Composable
fun InvoiceDetailScreen(
    state: DetailState,
    export: ExportUi?,
    exportFormat: ExportFormat,
    deleteFailed: Boolean,
    onEdit: () -> Unit,
    onPrepareShare: () -> Unit,
    onShare: (ShareChoice) -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.INVOICE_DETAILS, onBack)
        when (state) {
            DetailState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            DetailState.NotFound -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                ArabicText(Strings.INVOICE_NOT_FOUND, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            DetailState.Unreadable -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                ArabicText(Strings.INVOICE_UNREADABLE, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            is DetailState.Ready -> {
                val invoice = state.invoice
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (deleteFailed) StatusBanner(Strings.DELETE_FAILED, BannerKind.Error)
                    InvoiceHeaderCard(invoice, zone)
                    // The same compact table as the paper.
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        InvoiceTable(
                            rows = invoice.items.mapIndexed { index, item -> InvoiceDocumentBuilder.row(index + 1, item) },
                            picture = TablePicture.Detail,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                    PaymentCard(invoice)
                    if (state.revisions.isNotEmpty()) RevisionLog(state.revisions, zone)

                    if (export == null) {
                        BigButton(Strings.PREPARE_SHARE, Icons.Filled.Share, onPrepareShare, kind = ButtonKind.Success)
                    } else {
                        ExportPanel(exportFormat, export, onShare, onRetry = onPrepareShare, onPrint = onPrint)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BigButton(Strings.EDIT, Icons.Filled.Edit, onEdit)
                    BigButton(Strings.DELETE, Icons.Filled.Delete, { confirmingDelete = true }, kind = ButtonKind.Danger)
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = Strings.DELETE_INVOICE_TITLE,
            message = Strings.DELETE_INVOICE_MESSAGE,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
            confirmText = Strings.DELETE,
            danger = true,
        )
    }
}

@Composable
private fun InvoiceHeaderCard(invoice: Invoice, zone: ZoneId) {
    SectionCard {
        ArabicText("${Strings.INVOICE_NUMBER} ${ArabicFormat.invoiceNumber(invoice.number)}", style = MaterialTheme.typography.headlineSmall)
        ArabicText(ArabicFormat.date(invoice.createdAt, zone), style = MaterialTheme.typography.bodyLarge)
        if (invoice.customer.name.isNotBlank()) ArabicText("${ArabicTerms.CUSTOMER}: ${invoice.customer.name}", style = MaterialTheme.typography.bodyLarge)
        if (invoice.customer.phone.isNotBlank()) ArabicText("${ArabicTerms.PHONE}: ${invoice.customer.phone}", style = MaterialTheme.typography.bodyLarge)
        if (invoice.isEdited) {
            ArabicText(
                "${ArabicTerms.UPDATED} ${ArabicFormat.date(invoice.updatedAt, zone)}  (${Strings.REVISION} ${invoice.revision - 1})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.appPalette.onWarningContainer,
            )
        }
    }
}

@Composable
private fun PaymentCard(invoice: Invoice) {
    val payment = invoice.payment
    SectionCard {
        AmountRow(Strings.INVOICE_TOTAL, ArabicFormat.amount(payment.total), emphasized = true)
        AmountRow(Strings.DEPOSIT_SHORT, ArabicFormat.amount(payment.deposit))
        AmountRow(
            Strings.REMAINING,
            ArabicFormat.amount(payment.remaining),
            emphasized = true,
            valueColor = if (payment.isFullyPaid) MaterialTheme.appPalette.paid else MaterialTheme.colorScheme.onSurface,
        )
        if (payment.isFullyPaid) StatusBanner(Strings.FULLY_PAID_LONG, BannerKind.Success)
    }
}

@Composable
private fun RevisionLog(revisions: List<InvoiceRevision>, zone: ZoneId) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrictIconButton(
                onClick = { expanded = !expanded },
                contentDescription = Strings.REVISION_LOG,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = Strings.REVISION_LOG,
                    modifier = Modifier.size(32.dp),
                )
            }
            ArabicText(Strings.REVISION_LOG, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (expanded) {
            for (revision in revisions) {
                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.End) {
                    ArabicText(
                        "${Strings.REVISION} ${revision.revision - 1}  •  ${ArabicFormat.date(revision.changedAt, zone)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    for (change in revision.changes) {
                        ArabicText("• ${change.arabic()}", style = MaterialTheme.typography.bodyMedium)
                    }
                    ArabicText(
                        "${ArabicTerms.TOTAL}: ${ArabicFormat.amount(revision.previousTotal)} ← ${ArabicFormat.amount(revision.newTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
