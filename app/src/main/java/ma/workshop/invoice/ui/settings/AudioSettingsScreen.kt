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
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import ma.workshop.invoice.domain.settings.SpeechLanguage
import ma.workshop.invoice.domain.settings.SpeechRate
import ma.workshop.invoice.platform.SpeechProblem
import ma.workshop.invoice.platform.SpeechStatus
import ma.workshop.invoice.ui.components.ArabicText
import ma.workshop.invoice.ui.components.BannerKind
import ma.workshop.invoice.ui.components.BigButton
import ma.workshop.invoice.ui.components.ButtonKind
import ma.workshop.invoice.ui.components.ChildOptions
import ma.workshop.invoice.ui.components.LocalDictation
import ma.workshop.invoice.ui.components.StatusBanner


/**
 * Settings > audio. "تشغيل الصوت" is the parent: while it is off only it and the reason are shown, and every
 * other option is hidden (their saved values are kept). While it is on, the options appear in groups.
 */
@Composable
fun AudioSettingsScreen(
    settings: AppSettings,
    speechStatus: SpeechStatus,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onTestVoice: () -> Unit,
    onInstallVoice: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(Strings.AUDIO, onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
                SwitchRow(Strings.AUDIO_ENABLED, settings.audioEnabled) { v -> onUpdate { it.copy(audioEnabled = v) } }
                if (!settings.audioEnabled) StatusBanner(Strings.AUDIO_OFF, BannerKind.Info)
            }

            ChildOptions(visible = settings.audioEnabled) {
                SectionCard {
                    SwitchRow(Strings.SHOW_SPEAKERS, settings.showSpeakers) { v -> onUpdate { it.copy(showSpeakers = v) } }
                }

                SectionCard {
                    ArabicText(Strings.SPEECH_LANGUAGE, style = MaterialTheme.typography.titleMedium)
                    // Choices read from the right: the first (default) one is on the right (first child in a right-to-left row).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (language in listOf(SpeechLanguage.DARIJA, SpeechLanguage.STANDARD)) {
                            ChoiceOption(speechLanguageLabel(language), selected = settings.speechLanguage == language, enabled = true, modifier = Modifier.weight(1f)) {
                                onUpdate { it.copy(speechLanguage = language) }
                            }
                        }
                    }
                }

                SectionCard {
                    ArabicText(Strings.SPEECH_RATE, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (rate in listOf(SpeechRate.SLOW, SpeechRate.NORMAL, SpeechRate.FAST)) {
                            ChoiceOption(speechRateLabel(rate), selected = settings.speechRate == rate, enabled = true, modifier = Modifier.weight(1f)) {
                                onUpdate { it.copy(speechRate = rate) }
                            }
                        }
                    }
                }

                SectionCard {
                    SwitchRow(Strings.AUDIO_ITEM_TOTALS, settings.speakItemTotals) { v -> onUpdate { it.copy(speakItemTotals = v) } }
                    SwitchRow(Strings.AUDIO_INVOICE_TOTALS, settings.speakInvoiceTotals) { v -> onUpdate { it.copy(speakInvoiceTotals = v) } }
                }

                SectionCard {
                    when (speechStatus) {
                        is SpeechStatus.Checking -> StatusBanner(Strings.AUDIO_CHECKING, BannerKind.Info)
                        is SpeechStatus.Ready -> {
                            StatusBanner(Strings.AUDIO_READY, BannerKind.Success)
                            BigButton(Strings.AUDIO_TEST, Icons.Filled.VolumeUp, onTestVoice, kind = ButtonKind.Secondary)
                        }
                        is SpeechStatus.Unavailable -> {
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

                MicStatusLine()
            }
        }
    }
}

/** Status line for speak-to-fill (the mic on customer name and product name). */
@Composable
private fun MicStatusLine() {
    val dictation = LocalDictation.current ?: return
    val context = LocalContext.current
    var offlinePack by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { dictation.checkOfflinePack { offlinePack = it } }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    SectionCard {
        ArabicText(Strings.DICTATE, style = MaterialTheme.typography.titleMedium)
        when {
            !dictation.isAvailable() -> StatusBanner(Strings.DICTATE_NOT_AVAILABLE, BannerKind.Warning)
            offlinePack == false -> StatusBanner(Strings.DICTATE_OFFLINE_MISSING, BannerKind.Warning)
            !granted -> StatusBanner(Strings.DICTATE_NEEDS_PERMISSION, BannerKind.Info)
            else -> StatusBanner(Strings.DICTATE_READY, BannerKind.Success)
        }
    }
}
