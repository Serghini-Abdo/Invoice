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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import ma.workshop.invoice.ui.components.AppTextField


/** Settings > workshop information: name, phone and thank-you line. Every change is saved at once. */
@Composable
fun WorkshopSettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    // Local copies keep the cursor stable while typing; every change is saved immediately.
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var phone by rememberSaveable { mutableStateOf<String?>(null) }
    var thanks by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.WORKSHOP_INFO, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
                AppTextField(
                    value = name ?: settings.workshopName,
                    onValueChange = {
                        name = it.take(60)
                        onUpdate { s -> s.copy(workshopName = it.take(60)) }
                    },
                    label = Strings.WORKSHOP_NAME,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    tag = "workshop-name",
                )
                AppTextField(
                    value = phone ?: settings.workshopPhone,
                    onValueChange = { v ->
                        val clean = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20)
                        phone = clean
                        onUpdate { s -> s.copy(workshopPhone = clean) }
                    },
                    label = Strings.WORKSHOP_PHONE,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    tag = "workshop-phone",
                )
                AppTextField(
                    value = thanks ?: settings.thankYouMessage,
                    onValueChange = {
                        thanks = it.take(80)
                        onUpdate { s -> s.copy(thankYouMessage = it.take(80)) }
                    },
                    label = Strings.THANK_YOU_MESSAGE,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    tag = "workshop-thanks",
                )
            }
        }
    }
}
