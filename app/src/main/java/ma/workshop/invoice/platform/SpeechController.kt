package ma.workshop.invoice.platform

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ma.workshop.invoice.domain.settings.SpeechRate
import ma.workshop.invoice.domain.speech.VoiceState
import java.util.Locale

enum class SpeechProblem { NO_ENGINE, NO_ARABIC, INIT_FAILED }

sealed interface SpeechStatus {
    data object Checking : SpeechStatus

    /** An Arabic voice was found and selected. [voiceName] is shown in settings. */
    data class Ready(val voiceName: String) : SpeechStatus

    data class Unavailable(val problem: SpeechProblem) : SpeechStatus
}

/**
 * Arabic speech through the phone's own text-to-speech engine, with no network needed when an
 * offline voice is installed.
 *
 * It looks for an installed offline Arabic voice (preferring Moroccan Arabic, `ar-MA`, then any other
 * Arabic voice); voices that need the network are never used. It does not promise Darija pronunciation: that depends entirely on
 * which voice the phone has. When no usable voice exists the status says so, the UI shows a clear
 * message, and the app carries on silently, since speech is only ever an aid.
 */
class SpeechController(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow<SpeechStatus>(SpeechStatus.Checking)
    val status: StateFlow<SpeechStatus> = _status.asStateFlow()

    private var engine: TextToSpeech? = null
    private var utterance = 0

    val isReady: Boolean get() = _status.value is SpeechStatus.Ready

    val voiceState: VoiceState
        get() = when (_status.value) {
            SpeechStatus.Checking -> VoiceState.CHECKING
            is SpeechStatus.Ready -> VoiceState.READY
            is SpeechStatus.Unavailable -> VoiceState.MISSING
        }

    /** Starts the engine on first use. Safe to call repeatedly. */
    fun ensureStarted() {
        if (engine != null) return
        _status.value = SpeechStatus.Checking
        engine = TextToSpeech(context.applicationContext) { code ->
            // Hop to the main thread so `engine` is assigned before it is used.
            main.post { onInit(code) }
        }
    }

    /** Re-checks the installed voices, e.g. after the user installed Arabic voice data. */
    fun recheck() {
        engine?.shutdown()
        engine = null
        ensureStarted()
    }

    private fun onInit(code: Int) {
        val tts = engine
        if (code != TextToSpeech.SUCCESS || tts == null) {
            _status.value = SpeechStatus.Unavailable(if (hasEngine()) SpeechProblem.INIT_FAILED else SpeechProblem.NO_ENGINE)
            return
        }
        _status.value = try {
            selectArabicVoice(tts)
        } catch (e: Exception) {
            SpeechStatus.Unavailable(SpeechProblem.INIT_FAILED)
        }
    }

    private fun hasEngine(): Boolean =
        context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()

    /**
     * Offline voices only: the app must speak with no internet. Moroccan Arabic (ar-MA) is preferred,
     * then any other Arabic voice. A voice that needs the network counts as missing.
     */
    private fun selectArabicVoice(tts: TextToSpeech): SpeechStatus {
        val installed: List<Voice> = tts.voices.orEmpty().filter { voice ->
            voice.locale.language == "ar" &&
                !voice.isNetworkConnectionRequired &&
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        val best = installed.minWithOrNull(compareBy<Voice>({ it.locale.country != "MA" }, { -it.quality }))
        if (best != null && tts.setVoice(best) == TextToSpeech.SUCCESS) {
            return SpeechStatus.Ready(best.name)
        }
        // Engines that do not list voices: fall back to the language, but still refuse a network voice.
        for (tag in listOf("ar-MA", "ar")) {
            val locale = Locale.forLanguageTag(tag)
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE &&
                tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE &&
                tts.voice?.isNetworkConnectionRequired != true
            ) {
                return SpeechStatus.Ready(tts.voice?.name ?: tag)
            }
        }
        return SpeechStatus.Unavailable(SpeechProblem.NO_ARABIC)
    }

    /**
     * Speaks [text] at [rate]. QUEUE_FLUSH cuts off whatever is still being spoken, so tapping another
     * speaker always stops the previous one. Does nothing if no offline Arabic voice works.
     */
    fun speak(text: String, rate: SpeechRate = SpeechRate.NORMAL) {
        ensureStarted()
        val tts = engine ?: return
        if (!isReady || text.isBlank()) return
        utterance += 1
        tts.setSpeechRate(rate.multiplier)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "invoice-$utterance")
    }

    fun stop() {
        engine?.stop()
    }

    /** Opens the system screen where an Arabic voice can be installed, if the phone has one. */
    fun installVoiceDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun release() {
        engine?.shutdown()
        engine = null
    }
}
