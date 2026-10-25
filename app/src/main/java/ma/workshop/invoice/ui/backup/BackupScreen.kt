package ma.workshop.invoice.ui.backup

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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.backup.BackupAlert
import ma.workshop.invoice.domain.backup.BackupDestination
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFrequency
import ma.workshop.invoice.domain.backup.BackupPlan
import ma.workshop.invoice.domain.backup.BackupStatus
import ma.workshop.invoice.domain.backup.DestinationStatus
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.AmountRow
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ConfirmDialog
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.StatusBanner
import ma.workshop.invoice.ui.components.strictClickable
import ma.workshop.invoice.ui.settings.FormatOption
import java.time.ZoneId

@Composable
private fun DestinationRow(
    title: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .strictClickable(enabled = enabled, role = Role.Switch) { onChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                ArabicText(title, style = MaterialTheme.typography.titleMedium)
                ArabicText(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

/** One destination's last good backup and last problem, with the small technical code for whoever helps. */
@Composable
private fun StatusRow(destination: BackupDestination, status: DestinationStatus) {
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
        AmountRow(
            destination.label(),
            status.lastSuccessAt?.let { ArabicFormat.dateTime(it, zone) } ?: Strings.STATUS_NEVER,
        )
        val failure = status.lastFailure
        if (failure != null) {
            StatusBanner("${Strings.STATUS_ERROR}: ${failure.message()}. ${failure.advice()}", if (failure.needsUser) BannerKind.Error else BannerKind.Warning)
            if (status.lastDetail.isNotEmpty()) {
                ArabicText(
                    "${Strings.STATUS_CODE}: ${status.lastDetail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    plan: BackupPlan,
    status: BackupStatus,
    alerts: List<BackupAlert>,
    driveAccount: String?,
    onSetFrequency: (BackupFrequency) -> Unit,
    onSetLocal: (Boolean) -> Unit,
    onSetDrive: (Boolean) -> Unit,
    onBackUpNow: () -> Unit,
    onReconnectDrive: () -> Unit,
    onDriveRestore: () -> Unit,
    onFileExport: () -> Unit,
    onFileImport: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.BACKUP_TITLE, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            state.messages.forEach { message ->
                StatusBanner(message.text, message.kind)
                if (message.detail.isNotEmpty()) {
                    ArabicText("${Strings.STATUS_CODE}: ${message.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            alerts.forEach { StatusBanner(it.text(), if (it.kind == ma.workshop.invoice.domain.backup.AlertKind.NEEDS_ACTION) BannerKind.Error else BannerKind.Warning) }
            val driveNeedsReconnect = alerts.any { it.destination == BackupDestination.DRIVE && it.failure != null && it.failure != BackupFailure.NO_NETWORK }
            if (driveNeedsReconnect) {
                BigButton(Strings.RECONNECT_DRIVE, Icons.Filled.Sync, onReconnectDrive, enabled = !state.working)
            }

            if (state.working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArabicText(Strings.WORKING, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }

            SectionCard {
                ArabicText(Strings.AUTO_BACKUP_TITLE, style = MaterialTheme.typography.titleLarge)
                FormatOption(Strings.FREQ_OFF, Strings.FREQ_OFF_HINT, plan.frequency == BackupFrequency.OFF) { onSetFrequency(BackupFrequency.OFF) }
                FormatOption(Strings.FREQ_DAILY, "", plan.frequency == BackupFrequency.DAILY) { onSetFrequency(BackupFrequency.DAILY) }
                FormatOption(Strings.FREQ_WEEKLY, "", plan.frequency == BackupFrequency.WEEKLY) { onSetFrequency(BackupFrequency.WEEKLY) }
            }

            SectionCard {
                ArabicText(Strings.DESTINATIONS_TITLE, style = MaterialTheme.typography.titleLarge)
                DestinationRow(
                    Strings.DEST_PHONE,
                    Strings.DEST_PHONE_HINT,
                    Icons.Filled.PhoneAndroid,
                    checked = plan.local,
                    enabled = !state.working && !(plan.local && !plan.drive),
                    onChange = { onSetLocal(it) },
                )
                DestinationRow(
                    Strings.DEST_DRIVE,
                    if (plan.drive && driveAccount != null) "${Strings.DRIVE_ACCOUNT}: $driveAccount" else Strings.DEST_DRIVE_HINT,
                    Icons.Filled.Cloud,
                    checked = plan.drive,
                    enabled = !state.working && !(plan.drive && !plan.local),
                    onChange = { onSetDrive(it) },
                )
                if (plan.local != plan.drive) ArabicText(Strings.DEST_KEEP_ONE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigButton(Strings.BACKUP_NOW, Icons.Filled.Backup, onBackUpNow, enabled = !state.working)
            }

            SectionCard {
                ArabicText(Strings.STATUS_TITLE, style = MaterialTheme.typography.titleLarge)
                StatusRow(BackupDestination.LOCAL, status.local)
                if (plan.drive || status.drive.lastSuccessAt != null || status.drive.lastFailure != null) {
                    StatusRow(BackupDestination.DRIVE, status.drive)
                }
                if (status.local.usedPrivateFolder) StatusBanner(Strings.PRIVATE_FOLDER_NOTE, BannerKind.Warning)
            }

            SectionCard {
                ArabicText(Strings.BACKUP_FILE_TITLE, style = MaterialTheme.typography.titleLarge)
                BigButton(Strings.FILE_IMPORT, Icons.Filled.FileUpload, onFileImport, enabled = !state.working, kind = ButtonKind.Secondary)
                ArabicText(Strings.FILE_IMPORT_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigButton(Strings.DRIVE_RESTORE, Icons.Filled.CloudDownload, onDriveRestore, enabled = !state.working, kind = ButtonKind.Secondary)
                BigButton(Strings.FILE_EXPORT, Icons.Filled.FileDownload, onFileExport, enabled = !state.working, kind = ButtonKind.Secondary)
            }
        }
    }

    state.pendingRestore?.let { pending ->
        ConfirmDialog(
            title = Strings.RESTORE_CONFIRM_TITLE,
            message = Strings.RESTORE_CONFIRM_MESSAGE,
            onConfirm = onConfirmRestore,
            onDismiss = onCancelRestore,
            confirmText = Strings.RESTORE,
            danger = true,
            extra = {
                AmountRow(Strings.BACKUP_SUMMARY, pending.invoiceCount.toString())
                AmountRow(Strings.PRODUCT_CATALOG, pending.productCount.toString())
                AmountRow(Strings.BACKUP_DATE, ArabicFormat.date(pending.createdAt, ZoneId.systemDefault()))
            },
        )
    }
}
