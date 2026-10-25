package ma.workshop.invoice.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.format.ArabicFormat
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.AmountRow
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ConfirmDialog
import ma.workshop.invoice.ui.components.ScreenHeader
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.StatusBanner
import java.time.ZoneId

@Composable
fun BackupScreen(
    state: BackupUiState,
    onDriveBackup: () -> Unit,
    onDriveRestore: () -> Unit,
    onFileExport: () -> Unit,
    onFileImport: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(Strings.BACKUP_TITLE, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.message?.let { StatusBanner(it.text, it.kind) }
            StatusBanner(Strings.BACKUP_ONLINE_NOTE, BannerKind.Info)

            if (state.working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(Strings.WORKING, style = MaterialTheme.typography.titleMedium)
                }
            }

            SectionCard {
                Text("Google Drive", style = MaterialTheme.typography.titleLarge)
                BigButton(Strings.DRIVE_BACKUP, Icons.Filled.CloudUpload, onDriveBackup, enabled = !state.working)
                BigButton(Strings.DRIVE_RESTORE, Icons.Filled.CloudDownload, onDriveRestore, enabled = !state.working, kind = ButtonKind.Secondary)
            }
            SectionCard {
                Text(Strings.BACKUP_FILE_TITLE, style = MaterialTheme.typography.titleLarge)
                BigButton(Strings.FILE_EXPORT, Icons.Filled.FileDownload, onFileExport, enabled = !state.working, kind = ButtonKind.Secondary)
                BigButton(Strings.FILE_IMPORT, Icons.Filled.FileUpload, onFileImport, enabled = !state.working, kind = ButtonKind.Secondary)
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
