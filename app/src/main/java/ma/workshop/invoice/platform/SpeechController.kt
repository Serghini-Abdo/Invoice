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
 * It looks for an installed Arabic voice (preferring Moroccan Arabic, `ar-MA`, then any other Arabic
 * voice, offline before online). It does not promise Darija pronunciation: that depends entirely on
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

    private fun selectArabicVoice(tts: TextToSpeech): SpeechStatus {
        val installed: List<Voice> = tts.voices.orEmpty().filter { voice ->
            voice.locale.language == "ar" &&
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        val best = installed.minWithOrNull(
            compareBy<Voice>({ it.locale.country != "MA" }, { it.isNetworkConnectionRequired }, { -it.quality }),
        )
        if (best != null && tts.setVoice(best) == TextToSpeech.SUCCESS) {
            return SpeechStatus.Ready(best.name)
        }
        for (tag in listOf("ar-MA", "ar-DZ", "ar-TN", "ar")) {
            val locale = Locale.forLanguageTag(tag)
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE &&
                tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
            ) {
                return SpeechStatus.Ready(tts.defaultVoice?.name ?: tag)
            }
        }
        return SpeechStatus.Unavailable(SpeechProblem.NO_ARABIC)
    }

    /** Speaks [text], replacing anything still being spoken. Does nothing if no Arabic voice works. */
    fun speak(text: String) {
        ensureStarted()
        val tts = engine ?: return
        if (!isReady || text.isBlank()) return
        utterance += 1
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
