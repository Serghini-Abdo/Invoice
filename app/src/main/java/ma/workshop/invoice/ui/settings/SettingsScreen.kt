package ma.workshop.invoice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.ExportFormat
import ma.workshop.invoice.platform.SpeechProblem
import ma.workshop.invoice.platform.SpeechStatus
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.IconBadge
import ma.workshop.invoice.ui.components.ScreenHeader
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.ui.components.StatusBanner

@Composable
private fun NavCard(title: String, hint: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 64)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun FormatOption(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.RadioButton, onClickLabel = title, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    speechStatus: SpeechStatus,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenBackup: () -> Unit,
    onTestVoice: () -> Unit,
    onInstallVoice: () -> Unit,
    onBack: () -> Unit,
) {
    // Local copies keep the cursor stable while typing; every change is saved immediately.
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var phone by rememberSaveable { mutableStateOf<String?>(null) }
    var thanks by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(Strings.SETTINGS_TITLE, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NavCard(Strings.PRODUCT_CATALOG, Strings.PRODUCT_CATALOG_HINT, Icons.Filled.Category, onOpenCatalog)

            SectionCard {
                Text(Strings.WORKSHOP_INFO, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name ?: settings.workshopName,
                    onValueChange = {
                        name = it.take(60)
                        onUpdate { s -> s.copy(workshopName = it.take(60)) }
                    },
                    label = { Text(Strings.WORKSHOP_NAME) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                )
                OutlinedTextField(
                    value = phone ?: settings.workshopPhone,
                    onValueChange = { v ->
                        val clean = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20)
                        phone = clean
                        onUpdate { s -> s.copy(workshopPhone = clean) }
                    },
                    label = { Text(Strings.WORKSHOP_PHONE) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                )
                OutlinedTextField(
                    value = thanks ?: settings.thankYouMessage,
                    onValueChange = {
                        thanks = it.take(80)
                        onUpdate { s -> s.copy(thankYouMessage = it.take(80)) }
                    },
                    label = { Text(Strings.THANK_YOU_MESSAGE) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                )
            }

            SectionCard {
                Text(Strings.EXPORT_FORMAT, style = MaterialTheme.typography.titleLarge)
                FormatOption(Strings.FORMAT_PNG, Strings.FORMAT_PNG_HINT, settings.exportFormat == ExportFormat.PNG) {
                    onUpdate { it.copy(exportFormat = ExportFormat.PNG) }
                }
                FormatOption(Strings.FORMAT_PDF, Strings.FORMAT_PDF_HINT, settings.exportFormat == ExportFormat.PDF) {
                    onUpdate { it.copy(exportFormat = ExportFormat.PDF) }
                }
                FormatOption(Strings.FORMAT_BOTH, Strings.FORMAT_BOTH_HINT, settings.exportFormat == ExportFormat.BOTH) {
                    onUpdate { it.copy(exportFormat = ExportFormat.BOTH) }
                }
            }

            SectionCard {
                Text(Strings.AUDIO, style = MaterialTheme.typography.titleLarge)
                SwitchRow(Strings.AUDIO_ENABLED, settings.audioEnabled) { v -> onUpdate { it.copy(audioEnabled = v) } }
                SwitchRow(Strings.AUDIO_ITEM_TOTALS, settings.speakItemTotals, enabled = settings.audioEnabled) { v ->
                    onUpdate { it.copy(speakItemTotals = v) }
                }
                SwitchRow(Strings.AUDIO_INVOICE_TOTALS, settings.speakInvoiceTotals, enabled = settings.audioEnabled) { v ->
                    onUpdate { it.copy(speakInvoiceTotals = v) }
                }
                when {
                    !settings.audioEnabled -> StatusBanner(Strings.AUDIO_OFF, BannerKind.Info)
                    speechStatus is SpeechStatus.Checking -> StatusBanner(Strings.AUDIO_CHECKING, BannerKind.Info)
                    speechStatus is SpeechStatus.Ready -> {
                        StatusBanner(Strings.AUDIO_READY, BannerKind.Success)
                        BigButton(Strings.AUDIO_TEST, Icons.Filled.VolumeUp, onTestVoice, kind = ButtonKind.Secondary)
                    }
                    speechStatus is SpeechStatus.Unavailable -> {
                        StatusBanner(
                            when (speechStatus.problem) {
                                SpeechProblem.NO_ENGINE -> Strings.AUDIO_NO_ENGINE
                                SpeechProblem.NO_ARABIC -> Strings.AUDIO_NO_ARABIC
                                SpeechProblem.INIT_FAILED -> Strings.AUDIO_INIT_FAILED
                            },
                            BannerKind.Warning,
                        )
                        BigButton(Strings.AUDIO_INSTALL, Icons.Filled.RecordVoiceOver, onInstallVoice, kind = ButtonKind.Secondary)
                    }
                }
            }

            NavCard(Strings.BACKUP, Strings.BACKUP_HINT, Icons.Filled.Backup, onOpenBackup)
        }
    }
}
