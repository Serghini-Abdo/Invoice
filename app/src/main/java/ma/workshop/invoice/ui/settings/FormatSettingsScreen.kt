package ma.workshop.invoice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.AppTopBar
import ma.workshop.invoice.ui.components.SectionCard
import ma.workshop.invoice.domain.settings.ExportFormat


/** Settings > receipt format: PNG, PDF or both. */
@Composable
fun FormatSettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.EXPORT_FORMAT, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
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
        }
    }
}
