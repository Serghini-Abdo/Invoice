package ma.workshop.invoice.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import ma.workshop.invoice.domain.speech.DictationErrors
import ma.workshop.invoice.domain.speech.DictationProblem

sealed interface DictationOutcome {
    data class Text(val text: String) : DictationOutcome
    /** [code] is the recognizer's own error number, shown to help find out why it failed. */
    data class Problem(val problem: DictationProblem, val code: Int? = null) : DictationOutcome
}

/**
 * Speak-to-fill through the phone's own speech recognizer, in Moroccan Arabic (ar-MA), offline
 * preferred. Nothing is recorded or stored by the app: the recognizer only hands back text, which the
 * field shows as ordinary editable text. Typing always keeps working.
 */
class Dictation(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("speech", Context.MODE_PRIVATE)
    private var recognizer: SpeechRecognizer? = null

    /** False on phones without any speech recognition service. */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(app)

    /** The microphone permission is asked once, on the first mic tap; afterwards a refusal only shows a hint. */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    /**
     * Listens once and reports through [onOutcome] (on the main thread). [onListening] fires when the
     * recognizer is ready for speech. Call from the main thread.
     */
    fun listen(onListening: () -> Unit, onOutcome: (DictationOutcome) -> Unit) {
        cancel()
        if (!isAvailable()) {
            onOutcome(DictationOutcome.Problem(DictationProblem.NOT_AVAILABLE))
            return
        }
        // One recognizer at a time: first learn whether the offline pack exists, then listen. If the check
        // never answers, listen anyway after a moment. Offline is preferred only when the pack is there, so
        // a phone without it simply uses the internet.
        var started = false
        val mine = ++session
        fun go(installed: Boolean?) {
            if (started || mine != session) return
            started = true
            startEngine(LANGUAGE, preferOffline = installed == true, retried = false, onListening, onOutcome)
        }
        checkOfflinePack { go(it) }
        main.postDelayed({ go(null) }, 1500)
    }

    private fun startEngine(
        language: String,
        preferOffline: Boolean,
        retried: Boolean,
        onListening: () -> Unit,
        onOutcome: (DictationOutcome) -> Unit,
    ) {
        val engine = SpeechRecognizer.createSpeechRecognizer(app)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening()

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish()
                onOutcome(
                    if (text.isNullOrBlank()) DictationOutcome.Problem(DictationProblem.NO_MATCH) else DictationOutcome.Text(text),
                )
            }

            override fun onError(error: Int) {
                finish()
                // Moroccan Arabic is not offered everywhere: once, retry with plain Arabic over the internet.
                if (!retried && error in RETRY_WITH_PLAIN_ARABIC) {
                    main.post { startEngine(FALLBACK_LANGUAGE, preferOffline = false, retried = true, onListening, onOutcome) }
                    return
                }
                onOutcome(DictationOutcome.Problem(DictationErrors.problemFor(error), error))
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        engine.startListening(intent(language, preferOffline))
    }

    /**
     * Android 13+: reports whether the offline Arabic pack is installed (true / false), so the hint can be
     * shown before the user speaks. Older phones report null: there the hint comes from the error instead.
     */
    fun checkOfflinePack(onResult: (Boolean?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isAvailable()) {
            onResult(null)
            return
        }
        checkOfflinePack33(onResult)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkOfflinePack33(onResult: (Boolean?) -> Unit) {
        val engine = SpeechRecognizer.createSpeechRecognizer(app)
        engine.checkRecognitionSupport(intent(), { main.post(it) }, object : RecognitionSupportCallback {
            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                engine.destroy()
                onResult(recognitionSupport.installedOnDeviceLanguages.any { it.startsWith("ar") })
            }

            override fun onError(error: Int) {
                engine.destroy()
                onResult(null)
            }
        })
    }

    private var session = 0

    fun cancel() {
        session++
        recognizer?.cancel()
        finish()
    }

    private fun finish() {
        val engine = recognizer ?: return
        recognizer = null
        // Destroying from inside its own callback is not safe on every engine.
        main.post { engine.destroy() }
    }

    private fun intent(language: String = LANGUAGE, preferOffline: Boolean = true): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

    private companion object {
        const val LANGUAGE = "ar-MA"
        const val FALLBACK_LANGUAGE = "ar"
        /** Network, server and language errors: worth one retry with plain Arabic. */
        val RETRY_WITH_PLAIN_ARABIC = setOf(1, 2, 4, 11, 12, 13)
        const val KEY_PERMISSION_ASKED = "micPermissionAsked"
    }
}
