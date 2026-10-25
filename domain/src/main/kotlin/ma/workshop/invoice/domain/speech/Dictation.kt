package ma.workshop.invoice.domain.speech

/** Why speak-to-fill produced no text. Every case leaves the field as it was and keyboard typing works. */
enum class DictationProblem {
    /** The phone has no speech recognizer at all. */
    NOT_AVAILABLE,

    /** The offline Arabic speech pack is not installed (and there is no internet to fall back on). */
    OFFLINE_PACK_MISSING,

    /** Nothing was understood, or nobody spoke. */
    NO_MATCH,

    /** The microphone permission was refused. */
    PERMISSION_DENIED,

    /** The recognizer is busy with another request. */
    BUSY,

    /** Any other failure. */
    FAILED,
}

/**
 * Maps the recognizer's numeric error codes to [DictationProblem]. The values are the public constants
 * of `android.speech.SpeechRecognizer.ERROR_*`, copied here so the mapping is testable without Android.
 * With "prefer offline" set, a network or language error means the offline Arabic pack is missing.
 */
object DictationErrors {
    const val NETWORK_TIMEOUT = 1
    const val NETWORK = 2
    const val AUDIO = 3
    const val SERVER = 4
    const val CLIENT = 5
    const val SPEECH_TIMEOUT = 6
    const val NO_MATCH = 7
    const val RECOGNIZER_BUSY = 8
    const val INSUFFICIENT_PERMISSIONS = 9
    const val TOO_MANY_REQUESTS = 10
    const val SERVER_DISCONNECTED = 11
    const val LANGUAGE_NOT_SUPPORTED = 12
    const val LANGUAGE_UNAVAILABLE = 13

    fun problemFor(code: Int): DictationProblem = when (code) {
        NETWORK_TIMEOUT, NETWORK, SERVER, SERVER_DISCONNECTED, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE ->
            DictationProblem.OFFLINE_PACK_MISSING
        SPEECH_TIMEOUT, NO_MATCH -> DictationProblem.NO_MATCH
        RECOGNIZER_BUSY, TOO_MANY_REQUESTS -> DictationProblem.BUSY
        INSUFFICIENT_PERMISSIONS -> DictationProblem.PERMISSION_DENIED
        else -> DictationProblem.FAILED
    }
}

object DictationText {
    /**
     * Prepares recognised text for a field: trims, collapses runs of spaces and keeps at most [maxLength]
     * characters (the field's own limit). The result is put into the field as normal, editable text.
     */
    fun forField(recognized: String, maxLength: Int): String =
        recognized.trim().replace(Regex("\\s+"), " ").take(maxLength).trimEnd()
}
