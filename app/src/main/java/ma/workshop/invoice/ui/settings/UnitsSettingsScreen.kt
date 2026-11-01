package ma.workshop.invoice.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.testTag
import ma.workshop.invoice.domain.format.ArabicTerms
import ma.workshop.invoice.domain.pricing.LengthUnit
import ma.workshop.invoice.domain.settings.UnitLabelScript
import ma.workshop.invoice.ui.components.ArabicText


/** Settings > units: the length unit and how its label is written. */
@Composable
fun UnitsSettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.UNITS, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
                ArabicText(Strings.LENGTH_UNIT, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((unit, label) in listOf(
                        LengthUnit.METER to ArabicTerms.METER,
                        LengthUnit.CENTIMETER to ArabicTerms.CENTIMETER,
                    )) {
                        ChoiceOption(label, selected = settings.lengthUnit == unit, enabled = true, modifier = Modifier.weight(1f).testTag("unit-${unit.id}")) {
                            onUpdate { it.copy(lengthUnit = unit) }
                        }
                    }
                }
                ArabicText(Strings.UNIT_LABELS, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((script, label) in listOf(
                        UnitLabelScript.ARABIC to Strings.UNIT_LABELS_ARABIC,
                        UnitLabelScript.LATIN to Strings.UNIT_LABELS_LATIN,
                    )) {
                        ChoiceOption(label, selected = settings.unitLabelScript == script, enabled = true, modifier = Modifier.weight(1f).testTag("script-${script.id}")) {
                            onUpdate { it.copy(unitLabelScript = script) }
                        }
                    }
                }
            }
        }
    }
}
