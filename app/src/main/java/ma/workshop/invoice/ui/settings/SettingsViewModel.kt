package ma.workshop.invoice.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.platform.SpeechStatus
import ma.workshop.invoice.domain.speech.DarijaTerms

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val speechStatus: StateFlow<SpeechStatus> = container.speech.status

    init {
        container.speech.ensureStarted()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun testVoice() {
        container.speech.speak(DarijaTerms.VOICE_TEST, settings.value.speechRate)
    }

    fun recheckVoice() {
        container.speech.recheck()
    }

    fun installVoiceIntent() = container.speech.installVoiceDataIntent()
}
